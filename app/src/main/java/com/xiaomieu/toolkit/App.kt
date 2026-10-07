package com.xiaomieu.toolkit

import android.app.Application
import com.xiaomieu.toolkit.service.ServiceHolder
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

/**
 * Registers the LSPosed service listener so the UI can later talk to the module side
 * (remote preferences / future hook controls). The framework binds to the
 * `io.github.libxposed.service.XposedProvider` shipped by the service library.
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                ServiceHolder.setService(service)
            }

            override fun onServiceDied(service: XposedService) {
                ServiceHolder.setService(null)
            }
        })
    }
}
