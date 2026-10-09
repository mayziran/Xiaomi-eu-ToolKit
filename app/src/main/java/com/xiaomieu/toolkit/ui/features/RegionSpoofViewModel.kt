package com.xiaomieu.toolkit.ui.features

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaomieu.toolkit.data.RegionSpoofController
import com.xiaomieu.toolkit.data.RegionSpoofPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * [connected] 为 false 表示未连上 LSPosed 服务，开关不可用；
 * [missingScope] 是还没加入模块作用域的目标应用（没勾的话开关看起来"没反应"）。
 */
data class RegionSpoofUiState(
    val connected: Boolean = false,
    val weather: Boolean = false,
    val download: Boolean = false,
    val missingScope: List<String> = emptyList(),
)

/** 「功能」页的两个区域伪装开关；hook 在目标应用启动时安装，改动要等它下次启动生效。 */
class RegionSpoofViewModel : ViewModel() {

    private val _state = MutableStateFlow(RegionSpoofUiState())
    val state: StateFlow<RegionSpoofUiState> = _state.asStateFlow()

    /** 上一次的重试循环，避免反复进页面时叠加多个。 */
    private var refreshJob: Job? = null

    init {
        refresh()
    }

    /** 服务绑定是异步的：连不上就退避重试几次，连上后只读一次。 */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            repeat(RETRY_COUNT) { attempt ->
                val snapshot = withContext(Dispatchers.IO) {
                    val read = RegionSpoofController.read() ?: return@withContext null
                    read to RegionSpoofController.missingScope().orEmpty()
                }
                if (snapshot != null) {
                    _state.value = RegionSpoofUiState(
                        connected = true,
                        weather = snapshot.first.weather,
                        download = snapshot.first.download,
                        missingScope = snapshot.second,
                    )
                    return@launch
                }
                if (attempt < RETRY_COUNT - 1) delay(RETRY_DELAY_MS)
            }
        }
    }

    fun setWeather(enabled: Boolean) = set(RegionSpoofPrefs.KEY_WEATHER, enabled)

    fun setDownload(enabled: Boolean) = set(RegionSpoofPrefs.KEY_DOWNLOAD, enabled)

    private fun set(key: String, value: Boolean) {
        val current = _state.value
        if (!current.connected) return

        // 先更新界面让开关立刻响应，写入失败再回到磁盘上的真实状态。
        _state.value = when (key) {
            RegionSpoofPrefs.KEY_WEATHER -> current.copy(weather = value)
            RegionSpoofPrefs.KEY_DOWNLOAD -> current.copy(download = value)
            else -> current
        }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { RegionSpoofController.write(key, value) }
            if (!ok) refresh()
        }
    }

    private companion object {
        const val RETRY_COUNT = 10
        const val RETRY_DELAY_MS = 500L
    }
}
