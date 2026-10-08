package com.xiaomieu.toolkit.ui.features

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaomieu.toolkit.data.SpoofController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface SpoofUiState {
    data object Loading : SpoofUiState

    data class Ready(val enabled: Set<String>, val moduleInstalled: Boolean) : SpoofUiState

    data class Failed(val message: String) : SpoofUiState
}

/**
 * Which packages the Zygisk module currently spoofs, and how to change that.
 *
 * Hoisted in [com.xiaomieu.toolkit.ui.AppRoot] so the 功能 page and every app detail page share one
 * source of truth (a switch flipped in the detail page is immediately reflected in the list).
 *
 * The module reads the config on each app start, so a change needs the target app restarted —
 * not a reboot.
 */
class FeaturesViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<SpoofUiState>(SpoofUiState.Loading)
    val state: StateFlow<SpoofUiState> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** 配置是整文件覆写，写入必须串行，否则连点开关时后写的可能被先写的盖掉。 */
    private val writeMutex = Mutex()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = withContext(Dispatchers.IO) {
                try {
                    SpoofUiState.Ready(
                        enabled = SpoofController.readEnabled(),
                        moduleInstalled = SpoofController.isModuleInstalled(),
                    )
                } catch (t: Throwable) {
                    SpoofUiState.Failed(t.message ?: t.javaClass.simpleName)
                }
            }
        }
    }

    fun setEnabled(packageName: String, enabled: Boolean) {
        val current = _state.value as? SpoofUiState.Ready ?: return
        // Update the UI first so the switch responds immediately.
        _state.value = current.copy(
            enabled = current.enabled.toMutableSet().apply {
                if (enabled) add(packageName) else remove(packageName)
            },
        )

        viewModelScope.launch {
            val ok = writeMutex.withLock {
                // 排队等到真正写入时，以当时的最新状态为准。
                val latest = (_state.value as? SpoofUiState.Ready)?.enabled ?: return@withLock false
                withContext(Dispatchers.IO) { SpoofController.writeEnabled(latest) }
            }
            if (ok) {
                _message.value = "$packageName ${if (enabled) "已开启" else "已关闭"}：重启该应用后生效"
            } else {
                _message.value = "写入失败：需要 root 权限"
                refresh() // 回到磁盘上的真实状态
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
