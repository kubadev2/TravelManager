package com.example.travelmanager

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
    private val tripsList = mutableListOf<Trip>()
    private lateinit var btnFriends: TextView
    private lateinit var menuClose: View

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            tvEmail.text = currentUser.email
            tvEmail.visibility = View.GONE
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
        tripAdapter = TripAdapter(this, tripsList)
        recyclerViewTrips.adapter = tripAdapter

        fetchTrips()

        addTripForm = findViewById(R.id.addTripForm)
        addTripForm.visibility = View.GONE
        addTripClose = findViewById(R.id.addTripClose)
        addTripClose.visibility = View.GONE
        addTripClose.setOnClickListener {
            hideAddTripForm()
        }

        btnHamburger = findViewById(R.id.btnHamburger)
        fabAddTrip = findViewById(R.id.fabAddTrip)
        tvEmail = findViewById(R.id.tvEmail)
        btnLogout = findViewById(R.id.btnLogout)
        btnFriends = findViewById(R.id.btnFriends)
        menuClose = findViewById(R.id.menuClose)

        fabAddTrip.setOnClickListener {
            addTripForm.visibility = View.VISIBLE
            addTripClose.visibility = View.VISIBLE
            fabAddTrip.visibility = View.GONE
        }

        val btnSaveTrip = findViewById<View>(R.id.btnSaveTrip)
        btnSaveTrip.setOnClickListener {
            val etName = findViewById<EditText>(R.id.etDeparturePlace)
            val etDeparture = findViewById<EditText>(R.id.etDeparturePlace)
            val etStartDate = findViewById<EditText>(R.id.etTripDates)
            val etEndDate = findViewById<EditText>(R.id.etTripDates2)

            val name = etName.text.toString().trim()
            val departure = etDeparture.text.toString().trim()
            val startDate = etStartDate.text.toString().trim()
            val endDate = etEndDate.text.toString().trim()

            if (name.isEmpty() || departure.isEmpty() || startDate.isEmpty() || endDate.isEmpty()) {
                Toast.makeText(this, "Wypełnij wszystkie pola", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val currentUser = auth.currentUser
            if (currentUser != null) {
                val trip = hashMapOf(
                    "name" to name,
                    "departurePlace" to departure,
                    "startDate" to startDate,
                    "endDate" to endDate,
                    "userId" to currentUser.uid,
                    "companions" to emptyList<String>()
                )

                db.collection("trips")
                    .add(trip)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Wycieczka zapisana", Toast.LENGTH_SHORT).show()
                        fetchTrips()
                        hideAddTripForm()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Błąd zapisu: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        btnHamburger.setOnClickListener {
            if (tvEmail.visibility == View.GONE) {
                tvEmail.visibility = View.VISIBLE
                btnLogout.visibility = View.VISIBLE
                btnFriends.visibility = View.VISIBLE
                menuClose.visibility = View.VISIBLE
            } else {
                tvEmail.visibility = View.GONE
                btnLogout.visibility = View.GONE
                btnFriends.visibility = View.GONE
                menuClose.visibility = View.GONE
            }
        }

        btnFriends.setOnClickListener {
            startActivity(Intent(this, FriendsActivity::class.java))
            tvEmail.visibility = View.GONE
            btnLogout.visibility = View.GONE
            btnFriends.visibility = View.GONE
            menuClose.visibility = View.GONE
        }

        val rootLayout = findViewById<ConstraintLayout>(R.id.rootLayout)
        rootLayout.setOnClickListener {
            tvEmail.visibility = View.GONE
            btnFriends.visibility = View.GONE
            btnLogout.visibility = View.GONE
            menuClose.visibility = View.GONE
        }

        btnLogout.setOnClickListener {
            // Wylogowanie z Firebase
            FirebaseAuth.getInstance().signOut()

            // Jeśli w tym pliku nie masz googleSignInClient, musisz go stworzyć:
            val clientId = getString(R.string.default_web_client_id)
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile()
                .requestIdToken(clientId)
                .build()
            val googleSignInClient = GoogleSignIn.getClient(this, gso)

            // Wylogowanie z Google
            googleSignInClient.signOut().addOnCompleteListener {
                // Teraz user jest wylogowany i przy następnym signIn() będzie wybór konta
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }



        menuClose.setOnClickListener {
            tvEmail.visibility = View.GONE
            btnLogout.visibility = View.GONE
            btnFriends.visibility = View.GONE
            menuClose.visibility = View.GONE
        }

        etTripDates = findViewById(R.id.etTripDates2)
        etTripDates.setOnClickListener { showDateRangePicker() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (addTripForm.visibility == View.VISIBLE) {
                    hideAddTripForm()
                } else if (
                    tvEmail.visibility == View.VISIBLE &&
                    btnLogout.visibility == View.VISIBLE &&
                    btnFriends.visibility == View.VISIBLE &&
                    menuClose.visibility == View.VISIBLE
                ) {
                    tvEmail.visibility = View.GONE
                    btnLogout.visibility = View.GONE
                    btnFriends.visibility = View.GONE
                    menuClose.visibility = View.GONE
                } else {
                    showCloseAppConfirmation()
                }
            }
        })
    }

    private fun showCloseAppConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Zamknąć aplikację?")
            .setMessage("Czy na pewno chcesz zamknąć aplikację?")
            .setPositiveButton("Tak") { _, _ ->
                finishAffinity()
            }
            .setNegativeButton("Nie") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun hideAddTripForm() {
        addTripForm.visibility = View.GONE
        addTripClose.visibility = View.GONE
        fabAddTrip.visibility = View.VISIBLE
    }

    private fun showDateRangePicker() {
        val builder = MaterialDatePicker.Builder.dateRangePicker()
        val datePicker = builder.build()
        datePicker.addOnPositiveButtonClickListener { selection ->
            val startDate = selection?.first
            val endDate = selection?.second
            val format = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val startFormatted = startDate?.let { format.format(Date(it)) }
            val endFormatted = endDate?.let { format.format(Date(it)) }
            findViewById<EditText>(R.id.etTripDates).setText(startFormatted)
            findViewById<EditText>(R.id.etTripDates2).setText(endFormatted)
        }
        datePicker.show(supportFragmentManager, "date_range_picker")
    }

    private fun fetchTrips() {
        val currentUser = auth.currentUser ?: return

        tripsList.clear()

        db.collection("trips")
            .whereEqualTo("userId", currentUser.uid)
            .get()
            .addOnSuccessListener { ownedTrips ->
                for (doc in ownedTrips) {
                    val trip = doc.toObject(Trip::class.java).copy(tripId = doc.id, isGuest = false)
                    tripsList.add(trip)
                }

                db.collection("trips")
                    .whereArrayContains("companions", currentUser.email ?: "")
                    .get()
                    .addOnSuccessListener { guestTrips ->
                        for (doc in guestTrips) {
                            val tripId = doc.id
                            val alreadyAdded = tripsList.any { it.tripId == tripId }
                            if (!alreadyAdded) {
                                val trip = doc.toObject(Trip::class.java).copy(tripId = tripId, isGuest = true)
                                tripsList.add(trip)
                            }
                        }

                        tripAdapter.notifyDataSetChanged()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Błąd pobierania wycieczek", Toast.LENGTH_SHORT).show()
            }
    }
}
