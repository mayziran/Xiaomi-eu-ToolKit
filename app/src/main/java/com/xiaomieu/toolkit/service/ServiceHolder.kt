package com.xiaomieu.toolkit.service

import io.github.libxposed.service.XposedService

/**
 * Holds the [XposedService] connection delivered by the framework, so the UI can later read/write
 * remote preferences and drive module-side hook controls.
 */
object ServiceHolder {

    @Volatile
    private var service: XposedService? = null

    @Volatile
    private var listener: ((XposedService?) -> Unit)? = null

    fun getService(): XposedService? = service

    fun setService(value: XposedService?) {
        service = value
        listener?.invoke(value)
    }

    fun setListener(callback: ((XposedService?) -> Unit)?) {
        listener = callback
    }
}
