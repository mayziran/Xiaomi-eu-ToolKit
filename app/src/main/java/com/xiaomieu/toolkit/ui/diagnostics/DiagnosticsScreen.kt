package com.xiaomieu.toolkit.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaomieu.toolkit.data.model.DataFileStatus
import com.xiaomieu.toolkit.data.model.FrameworkVerdict
import com.xiaomieu.toolkit.ui.common.MainViewModel
import com.xiaomieu.toolkit.ui.common.SnapshotUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    viewModel: MainViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        val text = message
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("诊断") },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (val s = state) {
            is SnapshotUiState.Loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            is SnapshotUiState.Failed -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text("读取失败：${s.message}") }

            is SnapshotUiState.Ready -> {
                val d = s.snapshot.diagnostics
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (s.refreshing) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    InfoCard(
                        "官方服务框架",
                        listOf(
                            "结论" to frameworkVerdictText(d.framework.verdict),
                            "已安装" to yesNo(d.framework.installed),
                            "系统应用" to yesNo(d.framework.isSystemApp),
                            "版本" to "${d.framework.versionName ?: "-"} (${d.framework.versionCode ?: "-"})",
                            "版本类型" to buildFlavor(d.framework.versionName),
                            "推送支持" to pushSupportText(
                                d.framework.declaresPushSupport,
                                d.framework.signatureIsOfficial,
                            ),
                            "官方签名" to when (d.framework.signatureIsOfficial) {
                                true -> "是"
                                false -> "否"
                                null -> "未知"
                            },
                            "签名指纹" to (d.framework.signingSha256 ?: "-"),
                            "Root" to yesNo(d.rootAvailable),
                        ),
                    )

                    // 国际版空壳的判定只对官方签名框架成立；第三方框架（如 MiPushFramework）本 App 不适配。
                    val official = d.framework.signatureIsOfficial
                    val frameworkUnsupported = official == true &&
                        (d.framework.declaresPushSupport == false ||
                            d.framework.versionName?.endsWith("-G", ignoreCase = true) == true)
                    if (frameworkUnsupported) {
                        Text(
                            text = "当前服务框架不支持系统推送（国际版 -G）：它只声明了推送组件、" +
                                "没有推送实现，任何应用都不会向它注册。需要换成国区版（-C）服务框架。",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (official == false) {
                        Text(
                            text = "服务框架不是官方签名（第三方替换，例如 MiPushFramework）：" +
                                "本 App 只适配官方框架，对第三方框架的判定不适用，下面的数值仅供参考。",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    InfoCard(
                        "区域",
                        listOf(
                            "mipush_region" to (d.region.region ?: "-"),
                            "mipush_country_code" to (d.region.countryCode ?: "-"),
                            "是否国区" to yesNo(s.snapshot.isRegionCn),
                            "框架推送" to if (d.frameworkPushEnabled) {
                                "启用"
                            } else {
                                "禁用（region 不是 China）"
                            },
                        ),
                    )

                    ActionCard("操作") {
                        Button(
                            onClick = { viewModel.applyChinaRegion() },
                            enabled = d.rootAvailable && d.dataDir != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("一键改为国区（China / CN）")
                        }
                        OutlinedButton(
                            onClick = { viewModel.forceStop() },
                            enabled = d.rootAvailable,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("强行停止服务框架")
                        }
                        OutlinedButton(
                            onClick = { confirmClear = true },
                            enabled = d.rootAvailable,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("清除数据与缓存")
                        }
                    }

                    InfoCard(
                        "统计",
                        listOf(
                            "已注册" to d.registeredCount.toString(),
                            "推送被禁用" to d.pushDisabledCount.toString(),
                            "集成 MiPush" to d.integratedCount.toString(),
                            "显式注销" to d.unregisteredCount.toString(),
                            "框架自身注册" to yesNo(d.selfRegistered),
                        ),
                    )

                    if (d.files.isNotEmpty()) {
                        InfoCard("数据文件", d.files.map { it.label to fileStatusText(it) })
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清除数据与缓存？") },
            text = {
                Text(
                    "将执行 pm clear，清空 com.xiaomi.xmsf 的全部数据（含注册表）。" +
                        "所有应用的 MiPush 注册都会丢失，需要重新注册。此操作不可撤销。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clearDataAndCache()
                }) { Text("清除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
        )
    }
}

private fun yesNo(value: Boolean): String = if (value) "是" else "否"

/** 官方服务框架的版本号以 `-C`（国区）或 `-G`（国际）结尾。 */
private fun buildFlavor(versionName: String?): String = when {
    versionName == null -> "未知"
    versionName.endsWith("-C", ignoreCase = true) -> "国区版（-C）"
    versionName.endsWith("-G", ignoreCase = true) -> "国际版（-G）"
    else -> "未知"
}

private fun pushSupportText(declares: Boolean?, signatureIsOfficial: Boolean?): String = when {
    signatureIsOfficial == false -> "未知（非官方框架，本 App 不适配）"
    declares == true -> "支持"
    declares == false -> "不支持（缺推送实现）"
    else -> "未知"
}

private fun frameworkVerdictText(verdict: FrameworkVerdict): String = when (verdict) {
    FrameworkVerdict.OFFICIAL -> "匹配官方服务框架"
    FrameworkVerdict.SIGNATURE_MISMATCH -> "系统应用，但签名非官方（可能被替换）"
    FrameworkVerdict.NOT_SYSTEM_APP -> "已安装但非系统应用（第三方框架）"
    FrameworkVerdict.MISSING -> "未安装 com.xiaomi.xmsf"
    FrameworkVerdict.UNKNOWN -> "无法确定"
}

private fun fileStatusText(status: DataFileStatus): String = when {
    status.readable -> "可读"
    status.exists -> "存在但不可读"
    else -> status.missingNote ?: "不存在"
}

@Composable
private fun InfoCard(title: String, rows: List<Pair<String, String>>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            rows.forEach { (key, value) ->
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        text = key,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
