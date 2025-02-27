package com.sanmer.mrepo.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanmer.mrepo.model.module.OnlineModule
import com.sanmer.mrepo.repository.LocalRepository
import com.sanmer.mrepo.repository.ModulesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class RepositoryViewModel @Inject constructor(
    private val localRepository: LocalRepository,
    private val modulesRepository: ModulesRepository
) : ViewModel() {

    val onlineValue get() = (if (isSearch) _online else online)
        .sortedBy { it.name }

    private val online get() = localRepository.online

    var isSearch by mutableStateOf(false)
    var key by mutableStateOf("")
    private val _online by derivedStateOf {
        online.filter {
            if (key.isBlank()) return@filter true
            key.uppercase() in "${it.name}${it.author}".uppercase()
        }
    }

    var isRefreshing by mutableStateOf(false)
        private set
    private inline fun <T> T.refreshing(callback: T.() -> Unit) {
        isRefreshing  = true
        callback()
        isRefreshing = false
    }

    init {
        Timber.d("RepositoryViewModel init")
    }

    fun closeSearch() {
        isSearch = false
        key = ""
    }

    fun getOnlineAll() = viewModelScope.launch {
        refreshing {
            modulesRepository.getRepoAll()
        }
    }

    @Stable
    data class OnlineModuleState(
        val installed: Boolean,
        val hasLicense: Boolean
    ) {
        val hasLabel get() = installed or hasLicense
    }

    private fun createOnlineModuleState(module: OnlineModule): OnlineModuleState {
        val installed = localRepository.local.any { it.id == module.id }
        return OnlineModuleState(
            installed = installed,
            hasLicense = module.license.isNotBlank()
        )
    }

    @Composable
    fun rememberOnlineModuleState(module: OnlineModule): OnlineModuleState {
        return remember(key1 = module, key2 = isRefreshing) {
            createOnlineModuleState(module)
        }
    }
}