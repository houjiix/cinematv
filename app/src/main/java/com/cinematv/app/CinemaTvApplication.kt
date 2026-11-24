package com.cinematv.app

import android.app.Application
import com.cinematv.app.di.AppContainer

class CinemaTvApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
