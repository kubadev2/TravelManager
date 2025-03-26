// file: Utils.kt
package com.example.travelmanager

import android.content.Context
import android.net.ConnectivityManager
import android.widget.Toast

fun Context.showOfflineInfo() {
    Toast.makeText(this, "Zmiany zostaną zapisane, gdy połączenie z internetem zostanie przywrócone.", Toast.LENGTH_LONG).show()
}

fun Context.isNetworkAvailable(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    return connectivityManager.activeNetworkInfo?.isConnectedOrConnecting == true
}
