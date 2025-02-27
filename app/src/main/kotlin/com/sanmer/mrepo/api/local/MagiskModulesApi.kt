package com.sanmer.mrepo.api.local

import android.content.Context
import com.sanmer.mrepo.api.ApiInitializerListener
import com.sanmer.mrepo.app.Const
import com.sanmer.mrepo.model.module.LocalModule
import com.sanmer.mrepo.model.module.State
import com.sanmer.mrepo.utils.ModuleUtils
import com.sanmer.mrepo.utils.expansion.output
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import com.topjohnwu.superuser.nio.FileSystemManager
import timber.log.Timber
import java.io.File

class MagiskModulesApi(
    private val context: Context,
    private val fs: FileSystemManager
) : ModulesLocalApi {

    override var version = "magisk"
    private var isZygiskEnabled = false

    fun build(listener: ApiInitializerListener): ModulesLocalApi {
        Timber.d("initMagisk")

        Shell.cmd("su -v").submit {
            if (it.isSuccess) {
                val versionCode = ShellUtils.fastCmd("su -V")
                version = "${it.output} ($versionCode)"
                isZygisk()
                listener.onSuccess()

            } else {
                Timber.e("initMagisk: ${it.output}")
                listener.onFailure()
            }
        }

        return this
    }

    private fun isZygisk(): Boolean {
        val query = "SELECT value FROM settings WHERE key LIKE \"zygisk\" LIMIT 1"

        isZygiskEnabled = ShellUtils.fastCmd("magisk --sqlite '$query'").let {
            if (it.isNotBlank()) {
                it.split("=", limit = 2)[1] == "1"
            } else {
                false
            }
        }

        Timber.d("isZygiskEnabled: $isZygiskEnabled")
        return isZygiskEnabled
    }

    private fun getState(path: File): State {
        val removeFile = fs.getFile(path, "remove")
        val disableFile = fs.getFile(path, "disable")
        val updateFile = fs.getFile(path, "update")
        val riruFolder = fs.getFile(path, "riru")
        val zygiskFolder = fs.getFile(path, "zygisk")
        val unloaded = fs.getFile(zygiskFolder, "unloaded")

        if (riruFolder.exists() || path.name == "riru-core" ) {
            if (isZygiskEnabled) {
                return State.RIRU_DISABLE
            }
        }

        if (zygiskFolder.exists()) {
            if (unloaded.exists()) {
                return State.ZYGISK_UNLOADED
            }
            if (!isZygiskEnabled) {
                return State.ZYGISK_DISABLE
            }
        }

        if (removeFile.exists()) return State.REMOVE
        if (updateFile.exists()) return State.UPDATE

        return if (disableFile.exists()) {
            State.DISABLE
        } else {
            State.ENABLE
        }
    }

    override suspend fun getModules() = runCatching {
        Timber.i("getLocal: ${Const.MODULE_PATH}")

        val modules = mutableListOf<LocalModule>()
        fs.getFile(Const.MODULE_PATH).listFiles().orEmpty()
            .filter { !it.isFile && !it.isHidden }
            .forEach { path ->
                ModuleUtils.getModule(
                    prop = path.resolve("module.prop")
                ).onSuccess { module ->
                    module.state = getState(path)
                    modules.add(module)
                }
            }

        return@runCatching modules.toList()
    }

    private val LocalModule.path get() = "${Const.MODULE_PATH}/${id}"

    override fun enable(module: LocalModule) {
        when (module.state) {
            State.REMOVE -> {
                fs.getFile(module.path, "remove").delete()
            }
            State.DISABLE -> {
                fs.getFile(module.path, "disable").delete()
            }
            else -> {}
        }
        module.state = State.ENABLE
    }

    override fun disable(module: LocalModule) {
        fs.getFile(module.path, "disable").createNewFile()
        module.state = State.DISABLE
    }

    override fun remove(module: LocalModule) {
        when (module.state) {
            State.ENABLE -> {
                fs.getFile(module.path, "remove").createNewFile()
            }
            State.DISABLE -> {
                fs.getFile(module.path, "disable").delete()
                fs.getFile(module.path, "remove").createNewFile()
            }
            else -> {}
        }
        module.state = State.REMOVE
    }

    override fun install(
        console: (String) -> Unit,
        onSuccess: (LocalModule) -> Unit,
        onFailure: () -> Unit,
        zipFile: File
    ) = ModuleUtils.install(
        context = context,
        console = console,
        onSuccess = onSuccess,
        onFailure = onFailure,
        zipFile = zipFile,
        cmd = "magisk --install-module ${zipFile.absolutePath}"
    )
}