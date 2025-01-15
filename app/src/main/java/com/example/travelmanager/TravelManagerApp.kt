package com.example.travelmanager

import android.app.Application
import com.google.firebase.FirebaseApp

class TravelManagerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Inicjalizacja Firebase
        FirebaseApp.initializeApp(this)
    }
}
