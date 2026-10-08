package com.xiaomieu.toolkit.ui.features

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaomieu.toolkit.data.AppInfoResolver
import com.xiaomieu.toolkit.data.model.RegisteredApp
import com.xiaomieu.toolkit.ui.common.MainViewModel
import com.xiaomieu.toolkit.ui.common.SnapshotUiState
import com.xiaomieu.toolkit.ui.components.AppIcon

/** 列表排序方式。 */
private enum class AppSort(val label: String) {
    NAME("名称"),
    UPDATED("更新时间"),
    INSTALLED("安装时间"),
}

/**
 * 「功能 → 机型伪装」。
 *
 * 每个应用一个开关，改动写进 Zygisk 模块的 `apps.conf`；模块在该应用下次启动时生效。
 * 具体伪装哪些内容由模块决定，界面只负责选择生效的应用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpoofScreen(
    mainViewModel: MainViewModel,
    viewModel: FeaturesViewModel,
    onOpenDetail: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val resolver = remember { AppInfoResolver.shared(context) }
    val snapshotState by mainViewModel.state.collectAsStateWithLifecycle()
    val spoofState by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    // 全局设置（总开关在「功能」页）。
    val hideSystem by mainViewModel.hideSystemApps.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(AppSort.NAME) }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.clearMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("机型伪装") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val spoof = spoofState as? SpoofUiState.Ready
        val snapshot = (snapshotState as? SnapshotUiState.Ready)?.snapshot
        val allApps = remember(snapshot) { snapshot?.apps.orEmpty() }
        val enabledPackages = spoof?.enabled.orEmpty()
        val visible = remember(allApps, query, sort, hideSystem, enabledPackages) {
            allApps
                .filter { matchesQuery(it, query) }
                // 已开启的应用始终显示：否则开了系统应用再"隐藏系统应用"，它就消失且关不掉了。
                .filter { !hideSystem || !it.systemApp || it.packageName in enabledPackages }
                .sortedWith(comparatorFor(sort, resolver))
        }
        val canToggle = spoof?.moduleInstalled == true && snapshot?.diagnostics?.rootAvailable == true

        // 说明、提示、搜索、排序都作为列表项 —— 整页一起滚，说明不会一直占着屏幕。
        LazyColumn(
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item { SpoofInfoCard(spoof) }

            if (spoof?.moduleInstalled == false) {
                item {
                    Text(
                        text = "未检测到 Zygisk 模块：请先安装 Xiaomi-eu-ToolKit-Zygisk，开关才会生效。",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            if (snapshot?.diagnostics?.rootAvailable == false) {
                item {
                    Text(
                        text = "未获得 root 权限，无法读取/写入模块配置。",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("搜索应用名 / 包名") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    AppSort.entries.forEach { option ->
                        FilterChip(
                            selected = sort == option,
                            onClick = { sort = option },
                            label = { Text(option.label) },
                        )
                    }
                    Text(
                        text = "共 ${visible.size} 个",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (visible.isEmpty()) {
                item {
                    Text(
                        text = "没有匹配的应用",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
            }

            items(visible, key = { it.packageName }) { app ->
                AppToggleRow(
                    app = app,
                    checked = spoof?.enabled?.contains(app.packageName) == true,
                    switchEnabled = canToggle,
                    onToggle = { viewModel.setEnabled(app.packageName, it) },
                    onClick = { onOpenDetail(app.packageName) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SpoofInfoCard(spoof: SpoofUiState.Ready?) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "需要先安装 Xiaomi-eu-ToolKit-Zygisk（Zygisk 模块）。Xiaomi-eu-ToolKit 为 " +
                    "MIUI / HyperOS 系列 ROM 设计：大多数系统无需伪装，仅为 xiaomi.eu 这类特殊 " +
                    "ROM 准备。改动在目标应用下次启动时生效。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val moduleText = when {
                spoof == null -> "模块状态：读取中…"
                spoof.moduleInstalled -> "模块状态：已安装"
                else -> "模块状态：未安装"
            }
            Text(
                text = "$moduleText · 已开启 ${spoof?.enabled?.size ?: 0} 个应用",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppToggleRow(
    app: RegisteredApp,
    checked: Boolean,
    switchEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        AppIcon(app.packageName)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onToggle, enabled = switchEnabled)
    }
}

private fun comparatorFor(sort: AppSort, resolver: AppInfoResolver): Comparator<RegisteredApp> =
    when (sort) {
        AppSort.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.label }
        AppSort.UPDATED -> compareByDescending { resolver.lastUpdateTime(it.packageName) ?: 0L }
        AppSort.INSTALLED -> compareByDescending { resolver.firstInstallTime(it.packageName) ?: 0L }
    }

private fun matchesQuery(app: RegisteredApp, query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim().lowercase()
    return app.packageName.lowercase().contains(q) ||
        app.label.lowercase().contains(q) ||
        (app.appId?.contains(q) == true)
}
