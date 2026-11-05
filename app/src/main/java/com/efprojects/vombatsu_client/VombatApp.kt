package com.efprojects.vombatsu_client

import android.app.Application

class VombatApp : Application() {
    val graph by lazy { AppGraph(this) }

    override fun onCreate() {
        super.onCreate()
        ErrorLog.init(this)
    }
}
