package ru.kyamshanov.notepen

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContextHolder.context = applicationContext
        //init DI
    }
}
