package com.xiaomieu.toolkit.xposed

import android.content.Context
import android.util.Log
import com.xiaomieu.toolkit.data.RegionSpoofPrefs
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean

/**
 * LSPosed 入口：在目标进程里改写区域判定点，让天气走国内数据源、下载管理启用迅雷加速。
 *
 * - 天气：`ToolsNet.getText` —— 把请求 URL 里的 `isGlobal` 改成 `false`
 * - 下载程序：`BuildUtils.isInternationalVersion()` —— 固定 `false`（引擎初始化 + 下载走迅雷）
 * - 下载 UI：`miui.os.Build.IS_INTERNATIONAL_BUILD` —— 置 `false`（设置页出现"迅雷加速"）
 *
 * 除下载 UI 外都不翻全局字段：那个字段牵着 App 所有区域分支（天气 32 处引用，含布局与初始化），
 * 翻了会崩。这里只改真正决定结果的地方，App 的界面与初始化流程不碰。三处用到的类名/字段名均未被混淆。
 */
class HookEntry : XposedModule() {

    private var packageName: String = ""

    /** 只在首次改写时打印完整 URL，避免刷屏。请求在网络线程并发，故用原子量。 */
    private val urlLogged = AtomicBoolean(false)

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        try {
            log(Log.INFO, TAG, "module loaded in process=${param.processName} systemServer=${param.isSystemServer}")
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "onModuleLoaded failed", t)
        }
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        packageName = param.packageName
        val switchKey = RegionSpoofPrefs.switchKeyForPackage(packageName) ?: return

        if (readSwitch(switchKey) != true) {
            log(Log.INFO, TAG, "[$packageName] region spoof disabled, skip")
            return
        }

        val classLoader = param.defaultClassLoader
        when (packageName) {
            RegionSpoofPrefs.WEATHER_PACKAGE -> hookWeatherRequests(classLoader)

            RegionSpoofPrefs.DOWNLOADS_PROVIDER_PACKAGE ->
                hookReturning(ENGINE_GATE_CLASS, "isInternationalVersion", false, classLoader, "engine")

            RegionSpoofPrefs.DOWNLOADS_UI_PACKAGE ->
                flipInternationalBuildFlag(classLoader)
        }
    }

    /**
     * 把 `miui.os.Build.IS_INTERNATIONAL_BUILD` 置为 false（只用于下载管理 UI）。
     *
     * 该判定点藏在混淆类 `T0.h` 里（类名随构建变化），所以改框架字段。
     * 敢在 UI 里翻字段：该字段的读取都汇聚到 `T0.h.H()`，调用点约 16 处但全是界面显隐，
     * 没有初始化风险。天气不同（32 处，含 Activity/View），翻了会崩。
     */
    private fun flipInternationalBuildFlag(classLoader: ClassLoader) {
        try {
            val field = Class.forName(FRAMEWORK_BUILD_CLASS, false, classLoader)
                .getDeclaredField(FIELD_INTERNATIONAL)
            field.isAccessible = true

            val before = field.getBoolean(null)
            try {
                field.setBoolean(null, false)
            } catch (e: IllegalAccessException) {
                // static final：清掉 FINAL 修饰位后重试（该字段是运行期计算的非编译期常量）
                val accessFlags = Field::class.java.getDeclaredField("accessFlags")
                accessFlags.isAccessible = true
                accessFlags.setInt(field, field.modifiers and Modifier.FINAL.inv())
                field.setBoolean(null, false)
            }
            log(Log.INFO, TAG, "[$packageName] $FRAMEWORK_BUILD_CLASS.$FIELD_INTERNATIONAL: $before -> ${field.getBoolean(null)}")
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "[$packageName] flip $FIELD_INTERNATIONAL failed", t)
        }
    }

    // ---------------------------------------------------------------- 天气

    /** 天气所有请求都走 ToolsNet，改写 URL 里的 `isGlobal` 即可，不碰混淆类。 */
    private fun hookWeatherRequests(classLoader: ClassLoader) {
        val string = String::class.java
        val context = Context::class.java
        // 只挂这两个：getHttpText 在天气里没有任何调用者
        hookUrlParameter(classLoader, "getText", arrayOf(string), urlIndex = 0)
        hookUrlParameter(classLoader, "getText", arrayOf(context, string, string), urlIndex = 1)
    }

    private fun hookUrlParameter(
        classLoader: ClassLoader,
        methodName: String,
        parameterTypes: Array<Class<*>>,
        urlIndex: Int,
    ) {
        val id = "weather-url:$methodName/${parameterTypes.size}"
        try {
            val method = Class.forName(TOOLS_NET_CLASS, false, classLoader)
                .getDeclaredMethod(methodName, *parameterTypes)
            hook(method).setId("region-spoof:$id").intercept { chain ->
                // 改写失败就当没改（不能把异常抛给 App）；proceed 一律放在 try 外面，避免重复执行
                val original = runCatching { chain.getArg(urlIndex) as? String }.getOrNull()
                val rewritten = if (original == null) null
                else runCatching { rewriteWeatherUrl(original) }.getOrDefault(original)

                if (rewritten == null || rewritten == original) {
                    chain.proceed()
                } else {
                    val args = chain.args.toMutableList()
                    args[urlIndex] = rewritten
                    chain.proceed(args.toTypedArray())
                }
            }
            log(Log.INFO, TAG, "[$packageName] hooked $TOOLS_NET_CLASS.$methodName(${parameterTypes.size} args) ($id)")
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "[$packageName] hook $methodName failed ($id)", t)
        }
    }

    /**
     * 把 URL 里的 `isGlobal` 换成 false。
     *
     * 实测（直接对比服务端返回）：这是决定数据源的唯一参数 —— false 时 `sourceMaps` 从
     * `accu` 变为国内源、AQI 里出现 `caiyun`；而 `modDevice` 无影响，所以不改。
     * 不含该参数的 URL（广告、雷达等）原样放行；`sign` 是固定签名，改写不影响验签。
     */
    private fun rewriteWeatherUrl(url: String): String {
        if (!url.contains("$PARAM_IS_GLOBAL=")) return url

        val rewritten = replaceQueryParam(url, PARAM_IS_GLOBAL, VALUE_IS_GLOBAL)
        if (rewritten == url) return url

        if (urlLogged.compareAndSet(false, true)) {
            log(Log.INFO, TAG, "[$packageName] url rewritten:\n  before: $url\n  after : $rewritten")
        }
        return rewritten
    }

    /** 替换 `?key=xxx` / `&key=xxx` 里 `&` 之前的值；找不到 key 时原样返回。 */
    private fun replaceQueryParam(url: String, key: String, value: String): String {
        val marker = "$key="
        val index = url.indexOf(marker)
        if (index <= 0) return url
        if (url[index - 1] != '?' && url[index - 1] != '&') return url

        val valueStart = index + marker.length
        var valueEnd = url.indexOf('&', valueStart)
        if (valueEnd < 0) valueEnd = url.length
        return url.substring(0, valueStart) + value + url.substring(valueEnd)
    }

    // ---------------------------------------------------------------- 通用

    /** 把静态无参方法固定为 [value]；单个 hook 失败只记日志，不影响同进程其它 hook。 */
    private fun hookReturning(
        className: String,
        methodName: String,
        value: Any,
        classLoader: ClassLoader,
        id: String,
    ) {
        try {
            val method = Class.forName(className, false, classLoader).getDeclaredMethod(methodName)
            hook(method).setId("region-spoof:$id").intercept { value }
            log(Log.INFO, TAG, "[$packageName] $className.$methodName() -> $value")
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "[$packageName] hook $className.$methodName() failed", t)
        }
    }

    /** 读开关；读不到（框架未连接）返回 null，此时不做任何改动。 */
    private fun readSwitch(key: String): Boolean? = try {
        getRemotePreferences(RegionSpoofPrefs.GROUP).getBoolean(key, RegionSpoofPrefs.DEFAULT_ENABLED)
    } catch (t: Throwable) {
        log(Log.ERROR, TAG, "[$packageName] read remote preferences failed", t)
        null
    }

    companion object {
        const val TAG = "XiaomiEuToolKit"

        /** 天气的网络出口（未混淆，所有请求都走它）。 */
        private const val TOOLS_NET_CLASS = "com.miui.weather2.tools.ToolsNet"

        /** 下载程序的区域门（未混淆）。 */
        private const val ENGINE_GATE_CLASS = "com.android.providers.downloads.util.BuildUtils"

        /** 框架类与字段：所有"是不是国际版"判断的源头。 */
        private const val FRAMEWORK_BUILD_CLASS = "miui.os.Build"
        private const val FIELD_INTERNATIONAL = "IS_INTERNATIONAL_BUILD"

        /** 决定天气数据源的请求参数（实测：只有它影响服务端换源）。 */
        private const val PARAM_IS_GLOBAL = "isGlobal"
        private const val VALUE_IS_GLOBAL = "false"
    }
}
