package com.xiaomieu.toolkit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xiaomieu.toolkit.data.model.MiPushSupport
import com.xiaomieu.toolkit.data.model.RegistrationStatus

private val Green = Color(0xFF4CAF50)
private val Yellow = Color(0xFFFF9800)
private val Red = Color(0xFFF41804)
private val Grey = Color(0xFF9E9E9E)

/** Colour for the separate "push disabled" marker, distinct from the registration colours. */
private val Orange = Color(0xFFD84315)

const val PUSH_DISABLED_LABEL = "推送禁用"

fun statusLabel(status: RegistrationStatus, support: MiPushSupport): String = when (status) {
    RegistrationStatus.REGISTERED -> "已注册"
    RegistrationStatus.REGISTERED_NO_SECRET -> "已注册·缺密钥"
    RegistrationStatus.UNREGISTERED -> "已注销"
    RegistrationStatus.NOT_REGISTERED -> when (support) {
        MiPushSupport.SUPPORTED -> "未注册"
        MiPushSupport.PARTIAL -> "配置不全"
        MiPushSupport.NONE -> "未集成"
    }
}

fun statusColor(status: RegistrationStatus, support: MiPushSupport): Color = when (status) {
    RegistrationStatus.REGISTERED -> Green
    RegistrationStatus.REGISTERED_NO_SECRET -> Yellow
    RegistrationStatus.UNREGISTERED -> Grey
    RegistrationStatus.NOT_REGISTERED -> when (support) {
        MiPushSupport.SUPPORTED -> Red
        MiPushSupport.PARTIAL -> Yellow
        MiPushSupport.NONE -> Grey
    }
}

/**
 * Registration state plus, independently, whether the framework has push disabled for the app.
 * The two are separate dimensions: an app can be registered and still have push disabled.
 */
@Composable
fun StatusBadge(
    status: RegistrationStatus,
    support: MiPushSupport,
    pushDisabled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
        Pill(statusLabel(status, support), statusColor(status, support))
        if (pushDisabled) {
            Pill(PUSH_DISABLED_LABEL, Orange)
        }
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}
