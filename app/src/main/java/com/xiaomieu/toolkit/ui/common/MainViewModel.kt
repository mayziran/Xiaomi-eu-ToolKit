package com.xiaomieu.toolkit.ui.common

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaomieu.toolkit.data.AppInfoResolver
import com.xiaomieu.toolkit.data.MiPushScanner
import com.xiaomieu.toolkit.data.XmsfController
import com.xiaomieu.toolkit.data.XmsfPaths
import com.xiaomieu.toolkit.data.XmsfRegistryRepository
import com.xiaomieu.toolkit.data.model.XmsfSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SnapshotUiState {
    data object Loading : SnapshotUiState

    /** [refreshing] is true while a background refresh keeps the current data on screen. */
    data class Ready(val snapshot: XmsfSnapshot, val refreshing: Boolean = false) : SnapshotUiState

    data class Failed(val message: String) : SnapshotUiState
}

/**
 * Shared state and XMSF operations. One instance is hoisted in [com.xiaomieu.toolkit.ui.AppRoot]
 * so the overview and diagnostics screens never show stale data relative to each other.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val resolver = AppInfoResolver.shared(app)
    private val repository = XmsfRegistryRepository(resolver, MiPushScanner(app))

    private val _state = MutableStateFlow<SnapshotUiState>(SnapshotUiState.Loading)
    val state: StateFlow<SnapshotUiState> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val current = _state.value
            _state.value = if (current is SnapshotUiState.Ready) {
                current.copy(refreshing = true)
            } else {
                SnapshotUiState.Loading
            }
            _state.value = try {
                SnapshotUiState.Ready(repository.loadSnapshot())
            } catch (t: Throwable) {
                SnapshotUiState.Failed(t.message ?: t.javaClass.simpleName)
            }
        }
    }

    /** Writes `mipush_region=China` and `mipush_country_code=CN` into the XMSF data dir. */
    fun applyChinaRegion() {
        viewModelScope.launch {
            val dataDir = currentDataDir()
            if (dataDir == null) {
                _message.value = "找不到 XMSF 数据目录（需要 root 权限）"
                return@launch
            }
            val uid = resolver.uid(XmsfPaths.XMSF_PACKAGE)
            val ok = withContext(Dispatchers.IO) { XmsfController.applyChinaRegion(dataDir, uid) }
            _message.value = if (ok) "已写入 China / CN，重启设备或框架后生效" else "写入失败：需要 root 权限"
            refresh()
        }
    }

    fun forceStop() {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { XmsfController.forceStop() }
            _message.value = if (ok) "已强行停止服务框架" else "操作失败：需要 root 权限"
        }
    }

    fun clearDataAndCache() {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { XmsfController.clearDataAndCache() }
            _message.value = if (ok) "已清除服务框架的数据与缓存" else "操作失败：需要 root 权限"
            refresh()
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun currentDataDir(): String? =
        (_state.value as? SnapshotUiState.Ready)?.snapshot?.diagnostics?.dataDir
}
