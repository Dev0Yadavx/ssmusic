package com.example

import android.app.Application
import com.example.data.local.SMusicDatabase
import com.example.player.PlayerManager

class SMusicApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Pre-initialize database and player engine
        SMusicDatabase.getInstance(this)
        PlayerManager.getInstance(this)
    }
}
