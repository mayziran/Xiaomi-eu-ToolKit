package com.xiaomieu.toolkit.ui.overview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaomieu.toolkit.data.model.RegisteredApp
import com.xiaomieu.toolkit.data.model.RegistrationStatus
import com.xiaomieu.toolkit.ui.common.MainViewModel
import com.xiaomieu.toolkit.ui.common.SnapshotUiState
import com.xiaomieu.toolkit.ui.components.AppIcon
import com.xiaomieu.toolkit.ui.components.StatusBadge

private enum class AppFilter(val label: String) {
    ALL("全部"),
    REGISTERED("已注册"),
    NOT_REGISTERED("未注册"),
    UNREGISTERED("已注销"),
    PUSH_DISABLED("推送禁用"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    viewModel: MainViewModel,
    onOpenDetail: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(AppFilter.ALL) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MiPush 注册") },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            is SnapshotUiState.Loading -> CenteredBox(Modifier.padding(padding)) {
                CircularProgressIndicator()
            }

            is SnapshotUiState.Failed -> CenteredBox(Modifier.padding(padding)) {
                Text("读取失败：${s.message}")
            }

            is SnapshotUiState.Ready -> {
                val all = s.snapshot.apps
                val filtered = all.filter { matchesFilter(it, filter) && matchesQuery(it, query) }
                val counts = AppFilter.entries.associateWith { f -> all.count { matchesFilter(it, f) } }

                Column(Modifier.fillMaxSize().padding(padding)) {
                    if (s.refreshing) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }

                    if (!s.snapshot.diagnostics.rootAvailable) {
                        Text(
                            text = "未获得 root 权限，无法读取 XMSF 数据。请在 root 授权弹窗中允许本应用。",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }

                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        label = { Text("搜索应用名 / 包名 / appId") },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        AppFilter.entries.forEach { f ->
                            FilterChip(
                                selected = filter == f,
                                onClick = { filter = f },
                                label = { Text("${f.label} ${counts[f] ?: 0}") },
                            )
                        }
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 16.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (filtered.isEmpty()) {
                            item { EmptyHint() }
                        }
                        items(filtered, key = { it.packageName }) { app ->
                            AppRow(app = app, onClick = { onOpenDetail(app.packageName) })
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun EmptyHint() {
    Text(
        text = "没有匹配的条目",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(32.dp),
    )
}

@Composable
private fun AppRow(app: RegisteredApp, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
        Column(horizontalAlignment = Alignment.End) {
            StatusBadge(
                status = app.status,
                support = app.miPushSupport,
                pushDisabled = app.pushDisabled,
            )
            app.appId?.let {
                Text(
                    text = "appId $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun matchesFilter(app: RegisteredApp, filter: AppFilter): Boolean = when (filter) {
    AppFilter.ALL -> true
    AppFilter.REGISTERED -> app.status.isRegistered
    AppFilter.NOT_REGISTERED -> app.status == RegistrationStatus.NOT_REGISTERED
    AppFilter.UNREGISTERED -> app.status == RegistrationStatus.UNREGISTERED
    AppFilter.PUSH_DISABLED -> app.pushDisabled
}

private fun matchesQuery(app: RegisteredApp, query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim().lowercase()
    return app.packageName.lowercase().contains(q) ||
        app.label.lowercase().contains(q) ||
        (app.appId?.contains(q) == true)
}
