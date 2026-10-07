// Application entry point initializing global AppContainer.
// Enforces singleton lifecycle and application-wide services.
package com.visionguard

import android.app.Application

class VisionGuardApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
    }

    companion object {
        lateinit var instance: VisionGuardApp
            private set
    }
}
