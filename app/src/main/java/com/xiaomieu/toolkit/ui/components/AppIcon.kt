package com.xiaomieu.toolkit.ui.components

import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.xiaomieu.toolkit.data.AppInfoResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 已光栅化的图标。
 *
 * 列表有几百个应用，重复读盘 + 重复光栅化是滚动掉帧的主因，所以这里缓存的是最终位图而不是
 * Drawable；容量按**字节**限制：位图大小随图标本身差异很大（48px 到 192px），只按个数限的话
 * 顶格能吃掉几十 MB。
 */
private const val ICON_CACHE_BYTES = 12 * 1024 * 1024

private val iconBitmaps = object : LruCache<String, ImageBitmap>(ICON_CACHE_BYTES) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
}

/**
 * 一个应用的图标。
 *
 * 读取和光栅化都放到 IO 线程：在组合期做这两件事会占用主线程，快速滚动时直接掉帧。
 * 还没读到（或读失败）时显示首字母占位块。
 */
@Composable
fun AppIcon(pkg: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val context = LocalContext.current
    val resolver = remember { AppInfoResolver.shared(context) }
    val bitmap by produceState<ImageBitmap?>(initialValue = iconBitmaps.get(pkg), key1 = pkg) {
        val cached = iconBitmaps.get(pkg)
        if (cached != null) {
            // 命中缓存：同步写回，划回来时不会闪一下占位块。
            value = cached
            return@produceState
        }
        // 未命中：先清掉上一行的图标，避免复用节点时显示错图。
        value = null
        value = withContext(Dispatchers.IO) {
            runCatching { resolver.icon(pkg)?.toBitmap()?.asImageBitmap() }
                .getOrNull()
                ?.also { iconBitmaps.put(pkg, it) }
        }
    }
    val shape = RoundedCornerShape(10.dp)
    val icon = bitmap

    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(shape),
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = pkg.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
