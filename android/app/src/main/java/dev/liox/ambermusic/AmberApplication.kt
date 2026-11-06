package dev.liox.ambermusic

import android.app.Application
import dev.liox.ambermusic.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class AmberApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@AmberApplication)
            modules(appModule)
        }
    }
}
