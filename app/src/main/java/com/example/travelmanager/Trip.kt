package com.example.travelmanager

import java.text.SimpleDateFormat
import java.util.*

data class Trip(
    val userId: String = "",
    val departurePlace: String = "",
    val tripDates: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val timestamp: Long = 0
) {
    fun endDateCalculated(): String {
        val startDateStr = tripDates // Przykładowa data rozpoczęcia

        // Ustawienie formatu daty
        val format = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val startDate = format.parse(startDateStr)

        // Dodanie timestamp (milisekundy) do daty rozpoczęcia
        val calendar = Calendar.getInstance()
        calendar.time = startDate
        calendar.add(Calendar.MILLISECOND, timestamp.toInt())  // Dodajemy timestamp w milisekundach

        // Formatowanie nowej daty zakończenia
        return format.format(calendar.time)
    }
}

