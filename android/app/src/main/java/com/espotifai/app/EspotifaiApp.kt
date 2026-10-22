package com.espotifai.app

import android.app.Application

class EspotifaiApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // recoge los fallos y los sube al servidor en el siguiente arranque
        CrashReporter.install(this)
        Thread { CrashReporter.uploadPending(this) }.start()
    }
}
