package com.sanmer.mrepo.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanmer.mrepo.app.Const
import com.sanmer.mrepo.app.event.Event
import com.sanmer.mrepo.app.event.State
import com.sanmer.mrepo.model.json.UpdateJson
import com.sanmer.mrepo.model.json.VersionItem
import com.sanmer.mrepo.model.json.versionDisplay
import com.sanmer.mrepo.model.module.LocalModule
import com.sanmer.mrepo.model.module.OnlineModule
import com.sanmer.mrepo.repository.LocalRepository
import com.sanmer.mrepo.repository.ModulesRepository
import com.sanmer.mrepo.repository.SuRepository
import com.sanmer.mrepo.repository.UserDataRepository
import com.sanmer.mrepo.service.DownloadService
import com.sanmer.mrepo.utils.expansion.toDateTime
import com.sanmer.mrepo.utils.expansion.totalSize
import com.sanmer.mrepo.utils.expansion.update
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.log10
import kotlin.math.pow

@HiltViewModel
class ModuleViewModel @Inject constructor(
    private val localRepository: LocalRepository,
    private val modulesRepository: ModulesRepository,
    private val userDataRepository: UserDataRepository,
    private val suRepository: SuRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val moduleId: String = checkNotNull(savedStateHandle["moduleId"])
    var online by mutableStateOf(OnlineModule())
        private set
    val versions = mutableListOf<VersionItem>()

    var local by mutableStateOf(LocalModule())
        private set
    val installed get() = local.id != "unknown"
    val modulePath get() = "${Const.MODULE_PATH}/${moduleId}"

    val userData get() = userDataRepository.userData
    val suState get() = suRepository.state

    init {
        Timber.d("ModuleViewModel init")
        getModule(moduleId)
    }

    private fun getModule(moduleId: String) = viewModelScope.launch {
        Timber.d("getModule: $moduleId")

        runCatching {
            localRepository.local.find { it.id == moduleId }?.let { local = it }
            localRepository.online.first { it.id == moduleId }.apply { online = this }
        }.onSuccess {
            getUpdates(it)
        }.onFailure {
            Timber.e(it, "getModule")
        }
    }

    suspend fun getRepoByUrl(url: String) = localRepository.getRepoByUrl(url)

    // TODO: TODO: Waiting for version 2.0 of util
    val state = State(initial = Event.LOADING)
    private suspend fun getUpdates(module: OnlineModule) {
        val update: (UpdateJson) -> Unit = { update ->
            update.versions.forEach { item ->
                val versionCodes = versions.map { it.versionCode }
                if (item.versionCode !in versionCodes) {
                    val new = item.copy(repoUrl = update.repoUrl)
                    versions.update(new)
                }
            }
        }

        val result = module.repoUrls.map { url ->
            modulesRepository.getUpdate(url, module.id)
                .onSuccess {
                    return@map Result.success(it.copy(repoUrl = url))
                }.onFailure {
                    Timber.e(it, "getUpdates")
                }
        }

        if (result.all { it.isFailure }) {
            state.setFailed()
            return
        }

        result.mapNotNull { it.getOrNull() }.let { list ->
            list.sortedByDescending { it.timestamp }
                .forEach(update)

            if (versions.isNotEmpty()) {
                versions.sortedByDescending { it.versionCode }
            }
        }

        state.setSucceeded()
    }

    fun downloader(
        context: Context,
        item: VersionItem,
        install: Boolean = false
    ) {
        val path = userDataRepository.value.downloadPath.resolve(
            "${online.name}_${item.versionDisplay}.zip"
                .replace("[\\s+|/]".toRegex(), "_")
                .replace("[^a-zA-Z0-9\\-._]".toRegex(), "")
        )

        DownloadService.start(
            context = context,
            name = online.name,
            path = path.absolutePath,
            url = item.zipUrl,
            install = install
        )
    }

    fun getLastModified(): String? = try {
        val moduleProp = suRepository.fs
            .getFile("$modulePath/module.prop")

        if (moduleProp.exists()) {
            moduleProp.lastModified().toDateTime()
        } else {
            null
        }

    } catch (e: Exception) {
        Timber.e(e, "getLastModified")
        null
    }

    fun getDirSize(): String? = try {
        val modulePath = suRepository.fs.getFile(modulePath)

        if (modulePath.exists()) {
            modulePath.totalSize.formatFileSize()
        } else {
            null
        }

    } catch (e: Exception) {
        Timber.e(e, "getDirSize")
        null
    }

    private fun Long.formatFileSize() = if (this < 0){
        "0 B"
    } else {
        val units = listOf("B", "KB", "MB")
        val group = (log10(toDouble()) / log10(1024.0)).toInt()
        String.format("%.2f %s", this / 1024.0.pow(group.toDouble()), units[group])
    }
}