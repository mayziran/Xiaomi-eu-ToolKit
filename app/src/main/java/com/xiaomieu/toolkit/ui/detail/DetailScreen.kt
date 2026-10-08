package com.xiaomieu.toolkit.ui.detail

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiaomieu.toolkit.data.model.RegisteredApp
import com.xiaomieu.toolkit.data.model.MiPushSupport
import com.xiaomieu.toolkit.ui.components.AppIcon
import com.xiaomieu.toolkit.ui.components.StatusBadge
import com.xiaomieu.toolkit.ui.features.FeaturesViewModel
import com.xiaomieu.toolkit.ui.features.SpoofUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    packageName: String,
    featuresViewModel: FeaturesViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: DetailViewModel = viewModel(
        key = packageName,
        factory = DetailViewModelFactory(application, packageName),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val spoofState by featuresViewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("应用详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            is DetailUiState.Loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            is DetailUiState.Failed -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text("读取失败：${s.message}") }

            is DetailUiState.Ready -> {
                val app = s.app
                if (app == null) {
                    Box(
                        Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center,
                    ) { Text("注册表中没有该应用的记录") }
                } else {
                    DetailContent(
                        app = app,
                        spoof = spoofState as? SpoofUiState.Ready,
                        onToggleSpoof = { on -> featuresViewModel.setEnabled(app.packageName, on) },
                        modifier = Modifier.fillMaxSize().padding(padding),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailContent(
    app: RegisteredApp,
    spoof: SpoofUiState.Ready?,
    onToggleSpoof: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            AppIcon(app.packageName, size = 56.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            StatusBadge(
                status = app.status,
                support = app.miPushSupport,
                pushDisabled = app.pushDisabled,
            )
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow("appId", app.appId ?: "-")
                InfoRow("注册密钥", if (app.hasSecret) "有" else "无")
                InfoRow("推送", if (app.pushDisabled) "已禁用" else "启用")
                InfoRow("支持 MiPush", supportText(app.miPushSupport))
                InfoRow("已安装", if (app.installed) "是" else "否")
                InfoRow("系统应用", if (app.systemApp) "是" else "否")
                InfoRow("uid", app.uid?.toString() ?: "-")
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("机型伪装", style = MaterialTheme.typography.titleMedium)

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("对该应用启用", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Xiaomi-eu-ToolKit 为 MIUI / HyperOS 系列 ROM 设计，大多数系统无需伪装。" +
                                "改动后需重启该应用。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = spoof?.enabled?.contains(app.packageName) == true,
                        onCheckedChange = onToggleSpoof,
                        enabled = spoof?.moduleInstalled == true,
                    )
                }

                when {
                    spoof == null -> Text(
                        text = "读取模块配置中…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    !spoof.moduleInstalled -> Text(
                        text = "未检测到 Zygisk 模块（Xiaomi-eu-ToolKit-Zygisk），开关不可用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(key: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            key,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

private fun supportText(support: MiPushSupport): String = when (support) {
    MiPushSupport.SUPPORTED -> "已正确集成"
    MiPushSupport.PARTIAL -> "集成了但配置不全"
    MiPushSupport.NONE -> "未集成"
}
