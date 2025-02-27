package com.sanmer.mrepo.viewmodel

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanmer.mrepo.model.module.LocalModule
import com.sanmer.mrepo.model.module.State
import com.sanmer.mrepo.repository.LocalRepository
import com.sanmer.mrepo.repository.ModulesRepository
import com.sanmer.mrepo.repository.SuRepository
import com.sanmer.mrepo.utils.ModuleUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ModulesViewModel @Inject constructor(
    private val localRepository: LocalRepository,
    private val modulesRepository: ModulesRepository,
    private val suRepository: SuRepository
) : ViewModel() {
    val localValue get() = (if (isSearch) _local else local)
        .sortedBy { it.name }

    private val local get() = localRepository.local

    var isSearch by mutableStateOf(false)
    var key by mutableStateOf("")
    private val _local by derivedStateOf {
        local.filter {
            if (key.isBlank()) return@filter true
            key.uppercase() in "${it.name}${it.author}".uppercase()
        }
    }

    val suState get() = suRepository.state

    var isRefreshing by mutableStateOf(false)
        private set
    private inline fun <T> T.refreshing(callback: T.() -> Unit) {
        isRefreshing = true
        callback()
        isRefreshing = false
    }

    init {
        Timber.d("ModulesViewModel init")
    }

    fun closeSearch() {
        isSearch = false
        key = ""
    }

    fun getLocalAll() = viewModelScope.launch {
        refreshing {
            modulesRepository.getLocalAll()
        }
    }

    @Stable
    data class LocalModuleState(
        val alpha: Float = 1f,
        val decoration: TextDecoration = TextDecoration.None,
        val toggle: (Boolean) -> Unit = {},
        val change: () -> Unit = {},
        val manager: (() -> Unit)? = null
    )

    private fun createLocalModuleState(
        context: Context,
        module: LocalModule
    ): LocalModuleState = when (module.state) {
        State.ENABLE -> LocalModuleState(
            alpha = 1f,
            decoration = TextDecoration.None,
            toggle = { suRepository.disable(module) },
            change = { suRepository.remove(module) },
            manager = ModuleUtils.launchManger(context, module)
        )

        State.DISABLE -> LocalModuleState(
            alpha = 0.5f,
            toggle = { suRepository.enable(module) },
            change = { suRepository.remove(module) },
            manager = ModuleUtils.launchManger(context, module)
        )

        State.REMOVE -> LocalModuleState(
            alpha = 0.5f,
            decoration = TextDecoration.LineThrough,
            change = { suRepository.enable(module) }
        )
        State.ZYGISK_UNLOADED,
        State.RIRU_DISABLE,
        State.ZYGISK_DISABLE -> LocalModuleState(
            alpha = 0.5f
        )
        State.UPDATE -> LocalModuleState()
    }

    @Composable
    fun rememberLocalModuleState(module: LocalModule): LocalModuleState {
        val context = LocalContext.current

        return remember(key1 = module.state, key2 = isRefreshing) {
            createLocalModuleState(context, module)
        }
    }
}