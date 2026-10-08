package com.xiaomieu.toolkit.ui.features

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaomieu.toolkit.ui.common.MainViewModel

/**
 * 「功能」页 —— 模块各项功能的入口列表，以及通用设置。
 *
 * 每项功能占一行，点进去才是具体页面；以后模块新增功能，在这里加一行即可。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeaturesScreen(
    mainViewModel: MainViewModel,
    viewModel: FeaturesViewModel,
    onOpenSpoof: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hideSystemApps by mainViewModel.hideSystemApps.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("功能") }) },
    ) { padding ->
        val spoof = state as? SpoofUiState.Ready

        Column(Modifier.fillMaxSize().padding(padding)) {
            FeatureEntry(
                title = "机型伪装",
                subtitle = when {
                    spoof == null -> "读取中…"
                    !spoof.moduleInstalled -> "未检测到 Zygisk 模块"
                    else -> "已开启 ${spoof.enabled.size} 个应用"
                },
                onClick = onOpenSpoof,
            )
            HorizontalDivider()

            SettingRow(
                title = "隐藏系统应用",
                subtitle = "所有应用列表通用",
                checked = hideSystemApps,
                onCheckedChange = mainViewModel::setHideSystemApps,
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun FeatureEntry(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
