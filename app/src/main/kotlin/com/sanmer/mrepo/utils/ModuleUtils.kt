package com.sanmer.mrepo.utils

import android.content.Context
import com.sanmer.mrepo.model.module.LocalModule
import com.sanmer.mrepo.model.module.State
import com.sanmer.mrepo.utils.expansion.output
import com.sanmer.mrepo.utils.expansion.unzip
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import timber.log.Timber
import java.io.File

object ModuleUtils {
    fun install(
        context: Context,
        console: (console: String) -> Unit = {},
        onSuccess: (LocalModule) -> Unit = {},
        onFailure: () -> Unit = {},
        zipFile: File,
        cmd: String
    ) = Shell.cmd(cmd)
        .to(object : CallbackList<String?>() {
            override fun onAddElement(str: String?) {
                str?.let(console)
            }
        })
        .submit {
            if (it.isSuccess) {
                val tmp = context.cacheDir.resolve("tmp").apply {
                    if (!exists()) mkdirs()
                }

                zipFile.unzip(tmp, "module.prop", true)

                getModule(
                    prop = tmp.resolve("module.prop")
                ).onSuccess { value ->
                    value.state = State.UPDATE
                    onSuccess(value)
                }.onFailure {
                    onFailure()
                }

                Shell.cmd("rm -rf ${tmp.absolutePath}").submit()
            } else {
                onFailure()
            }
        }

    fun getModule(prop: File) = runCatching {
        val props = Shell.cmd("dos2unix < ${prop.absolutePath}").exec().out

        LocalModule().apply {
            for (line in props) {
                val text = line.split("=".toRegex(), 2).map { it.trim() }
                if (text.size != 2) {
                    continue
                }

                val key = text[0]
                val value = text[1]
                if (key.isEmpty() || key[0] == '#') {
                    continue
                }

                when (key) {
                    "id" -> id = value
                    "name" -> name = value
                    "version" -> version = value
                    "versionCode" -> versionCode = value.toInt()
                    "author" -> author = value
                    "description" -> description = value
                }
            }
        }
    }.onFailure {
        Timber.e(it, "parseProps")
    }

    fun launchManger(
        context: Context,
        module: LocalModule
    ): (() -> Unit)? = if (module.isLsposed) {
        { launchLSPManger(context) }
    } else {
        null
    }

    private val  LocalModule.isLsposed get() =
        id == "zygisk_lsposed" || id == "riru_lsposed"

    private fun launchLSPManger(
        context: Context,
        onFailure: (String) -> Unit = {},
    ) {
        context.packageManager
            .getLaunchIntentForPackage("org.lsposed.manager")
            ?.let {
                context.startActivity(it)
                return
            }

        Shell.cmd("am start " +
                "-a android.intent.action.MAIN " +
                "-c org.lsposed.manager.LAUNCH_MANAGER com.android.shell/.BugreportWarningActivity")
            .submit {
                if (!it.isSuccess) {
                    Timber.e("launchLSPManger failed: ${it.output}")
                    onFailure(it.output)
                }
            }
    }
}