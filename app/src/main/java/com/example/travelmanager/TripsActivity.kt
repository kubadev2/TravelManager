package com.example.travelmanager

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TripsActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var tripAdapter: TripAdapter
    private lateinit var recyclerViewTrips: RecyclerView
    private lateinit var fabAddTrip: ImageButton
    private lateinit var btnHamburger: ImageButton
    private lateinit var tvEmail: TextView
    private lateinit var btnLogout: TextView
    private lateinit var addTripForm: ConstraintLayout
    private lateinit var addTripClose: View
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private lateinit var etTripDates: EditText

    override fun onStart() {
        super.onStart()

        val currentUser = auth.currentUser
        if (currentUser != null) {
            tvEmail.text = currentUser.email
            tvEmail.visibility = View.GONE // Ukryj e-mail po zalogowaniu
            btnLogout.visibility = View.GONE
        } else {
            tvEmail.text = "Brak zalogowanego użytkownika"
            tvEmail.visibility = View.VISIBLE
            btnLogout.visibility = View.GONE
        }
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trips)

        recyclerViewTrips = findViewById(R.id.recyclerViewTrips)
        recyclerViewTrips.layoutManager = LinearLayoutManager(this)

        tripAdapter = TripAdapter(emptyList())
        recyclerViewTrips.adapter = tripAdapter

        fetchTrips()

        addTripForm = findViewById(R.id.addTripForm)
        addTripForm.visibility = View.GONE // Ukryj formularz na początku
        addTripClose = findViewById(R.id.addTripClose)
        addTripClose.visibility = View.GONE
        addTripClose.setOnClickListener {
            addTripForm.visibility = View.GONE
            addTripClose.visibility = View.GONE
            fabAddTrip.visibility = View.VISIBLE
        }

        val btnSaveTrip = findViewById<View>(R.id.btnSaveTrip)
        btnSaveTrip.setOnClickListener {
            val departurePlace = findViewById<EditText>(R.id.etDeparturePlace).text.toString()
            val tripDates = etTripDates.text.toString()  // Data wyjazdu
            val returnDates = findViewById<EditText>(R.id.etTripDates2).text.toString()  // Data powrotu
            val currentUser = auth.currentUser

            if (currentUser != null && departurePlace.isNotEmpty() && tripDates.isNotEmpty() && returnDates.isNotEmpty()) {
                // Walidacja daty
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val isValidDepartureDate = try {
                    dateFormat.parse(tripDates) != null
                } catch (e: Exception) {
                    false
                }

                val isValidReturnDate = try {
                    dateFormat.parse(returnDates) != null
                } catch (e: Exception) {
                    false
                }

                if (!isValidDepartureDate || !isValidReturnDate) {
                    Toast.makeText(this, "Proszę wprowadzić daty w formacie dd/MM/yyyy", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener  // Zatrzymanie zapisywania wycieczki
                }

                // Sprawdzamy, czy data wyjazdu nie jest późniejsza niż data powrotu
                if (dateFormat.parse(tripDates)?.after(dateFormat.parse(returnDates)) == true) {
                    Toast.makeText(this, "Data wyjazdu nie może być późniejsza niż data powrotu", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val trip = mapOf(
                    "userId" to currentUser.uid,
                    "departurePlace" to departurePlace,
                    "tripDates" to tripDates,
                    "returnDates" to returnDates,  // Dodajemy datę powrotu
                    "timestamp" to System.currentTimeMillis()
                )

                db.collection("trips")
                    .add(trip)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Wycieczka zapisana pomyślnie", Toast.LENGTH_SHORT).show()
                        addTripForm.visibility = View.GONE
                        addTripClose.visibility = View.GONE
                        fabAddTrip.visibility = View.VISIBLE
                        fetchTrips()
                    }
                    .addOnFailureListener { exception ->
                        Toast.makeText(this, "Błąd zapisu: $exception", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(this, "Proszę wypełnić wszystkie pola lub zalogować się", Toast.LENGTH_SHORT).show()
            }
        }


        fabAddTrip = findViewById(R.id.fabAddTrip)
        fabAddTrip.setOnClickListener {
            addTripForm.visibility = View.VISIBLE
            addTripClose.visibility = View.VISIBLE
            fabAddTrip.visibility = View.GONE
        }

        btnHamburger = findViewById(R.id.btnHamburger)
        tvEmail = findViewById(R.id.tvEmail)
        btnLogout = findViewById(R.id.btnLogout)
        val menuClose = findViewById<View>(R.id.menuClose)

        btnHamburger.setOnClickListener {
            if (tvEmail.visibility == View.GONE) {
                tvEmail.visibility = View.VISIBLE
                btnLogout.visibility = View.VISIBLE
                menuClose.visibility = View.VISIBLE
            } else {
                tvEmail.visibility = View.GONE
                btnLogout.visibility = View.GONE
                menuClose.visibility = View.GONE
            }
        }

        val rootLayout = findViewById<ConstraintLayout>(R.id.rootLayout)
        rootLayout.setOnClickListener {
            tvEmail.visibility = View.GONE
            btnLogout.visibility = View.GONE
            menuClose.visibility = View.GONE
        }

        btnLogout.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        menuClose.setOnClickListener {
            tvEmail.visibility = View.GONE
            btnLogout.visibility = View.GONE
            menuClose.visibility = View.GONE
        }

        etTripDates = findViewById(R.id.etTripDates2)
        etTripDates.setOnClickListener {
            showDateRangePicker()
        }
    }

    private fun showDateRangePicker() {
        val builder = MaterialDatePicker.Builder.dateRangePicker()
        val datePicker = builder.build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val startDate = selection?.first
            val endDate = selection?.second

            val format = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) // Formatowanie daty
            val startFormatted = startDate?.let { format.format(Date(it)) }
            val endFormatted = endDate?.let { format.format(Date(it)) }

            // Ustawienie dat w odpowiednich polach EditText
            findViewById<EditText>(R.id.etTripDates).setText(startFormatted)  // Data wyjazdu
            findViewById<EditText>(R.id.etTripDates2).setText(endFormatted)   // Data powrotu
        }

        datePicker.show(supportFragmentManager, "date_range_picker")
    }


    private fun fetchTrips() {
        val currentUser = auth.currentUser

        if (currentUser != null) {
            db.collection("trips")
                .whereEqualTo("userId", currentUser.uid)
                .get()
                .addOnSuccessListener { result ->
                    val tripsList = mutableListOf<Trip>()
                    for (document in result) {
                        val trip = document.toObject(Trip::class.java)
                        tripsList.add(trip)
                    }
                    tripAdapter = TripAdapter(tripsList)
                    recyclerViewTrips.adapter = tripAdapter
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(this, "Błąd pobierania danych: $exception", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(this, "Zaloguj się, aby zobaczyć swoje wycieczki", Toast.LENGTH_SHORT).show()
        }
    }
}
