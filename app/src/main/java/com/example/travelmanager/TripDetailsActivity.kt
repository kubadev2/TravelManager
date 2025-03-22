package com.example.travelmanager

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.travelmanager.databinding.ActivityTripDetailsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.Date

class TripDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTripDetailsBinding
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    // Przechowujemy ID wycieczki w zmiennej, żeby dało się ją zapisać/odtworzyć
    private var tripId: String = ""

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val PICK_PDF_REQUEST_CODE = 1001
    private val PICK_PHOTO_REQUEST_CODE = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Przywracamy tripId z savedInstanceState (jeśli istnieje)
        tripId = savedInstanceState?.getString("TRIP_ID") ?: ""

        binding = ActivityTripDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Jeśli jeszcze nie mamy tripId z onSaveInstanceState, spróbujmy pobrać z Intent
        if (tripId.isEmpty()) {
            tripId = intent.getStringExtra("tripId") ?: ""
        }

        Log.d("TripDetailsActivity", "Otrzymano tripId: $tripId")

        if (tripId.isNotEmpty()) {
            fetchTripDetails(tripId)
            fetchTicketsForTrip()
            fetchPhotosForTrip()

            if (supportFragmentManager.findFragmentById(R.id.flTripPlans) == null) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.flTripPlans, TripDetailsFragment.newInstance(tripId))
                    .commit()
            }
        } else {
            Toast.makeText(this, "Brak przekazanego ID wycieczki", Toast.LENGTH_SHORT).show()
        }

        // Obsługa przycisku dodawania biletu (PDF)
        binding.btnAddTicket.setOnClickListener {
            val openDocIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/pdf"
            }
            startActivityForResult(openDocIntent, PICK_PDF_REQUEST_CODE)
        }

        // Obsługa przycisku dodawania zdjęcia – umożliwiamy wybór wielu zdjęć
        binding.btnAddPhoto.setOnClickListener {
            val openPhotoIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            startActivityForResult(openPhotoIntent, PICK_PHOTO_REQUEST_CODE)
        }

        // Obsługa przycisku edycji wycieczki – otwiera dialog edycji danych
        binding.btnEditTrip.setOnClickListener {
            showEditTripDialog(
                binding.tvDeparturePlace.text.toString(),
                binding.tvStartDate.text.toString(),
                binding.tvEndDate.text.toString()
            )
        }
    }

    // Zapisujemy tripId, żeby przy rotacji było można je przywrócić
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("TRIP_ID", tripId)
    }

    private fun showEditTripDialog(currentDeparture: String, currentStartDate: String, currentEndDate: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_trip, null)

        val etDeparture = dialogView.findViewById<EditText>(R.id.etEditDeparture)
        val etStartDate = dialogView.findViewById<EditText>(R.id.etEditStartDate)
        val etEndDate = dialogView.findViewById<EditText>(R.id.etEditEndDate)

        etDeparture.setText(currentDeparture)
        etStartDate.setText(currentStartDate)
        etEndDate.setText(currentEndDate)

        AlertDialog.Builder(this)
            .setTitle("Edytuj dane wycieczki")
            .setView(dialogView)
            .setPositiveButton("Zapisz") { dialog, _ ->
                val newDeparture = etDeparture.text.toString()
                val newStartDate = etStartDate.text.toString()
                val newEndDate = etEndDate.text.toString()
                updateTripDetails(newDeparture, newStartDate, newEndDate)
                dialog.dismiss()
            }
            .setNegativeButton("Anuluj") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun updateTripDetails(newDeparture: String, newStartDate: String, newEndDate: String) {
        db.collection("trips").document(tripId)
            .update(
                mapOf(
                    "departurePlace" to newDeparture,
                    "startDate" to newStartDate,
                    "endDate" to newEndDate
                )
            )
            .addOnSuccessListener {
                Toast.makeText(this, "Dane wycieczki zaktualizowane", Toast.LENGTH_SHORT).show()
                binding.tvDeparturePlace.text = newDeparture
                binding.tvStartDate.text = newStartDate
                binding.tvEndDate.text = newEndDate

                // aktualizacja liczby dni w planie wycieczki
                val fragment = supportFragmentManager.findFragmentById(R.id.flTripPlans)
                if (fragment is TripDetailsFragment && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    fragment.refreshPlans(newStartDate, newEndDate)
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Błąd aktualizacji: ${exception.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchTripDetails(tripId: String) {
        db.collection("trips")
            .document(tripId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val trip = document.toObject(Trip::class.java)?.copy(tripId = document.id)
                    trip?.let {
                        binding.tvDeparturePlace.text = it.departurePlace
                        binding.tvStartDate.text = it.startDate
                        binding.tvEndDate.text = it.endDate
                    } ?: run {
                        Toast.makeText(this, "Brak danych wycieczki", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Nie znaleziono wycieczki o tym ID", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Błąd pobierania danych: $exception", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchTicketsForTrip() {
        db.collection("tickets")
            .whereEqualTo("tripId", tripId)
            .get()
            .addOnSuccessListener { result ->
                val tickets = result.mapNotNull { document ->
                    val fileUrl = document.getString("fileUrl") ?: return@mapNotNull null
                    Ticket(document.id, fileUrl)
                }
                binding.rvTickets.layoutManager =
                    LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
                binding.rvTickets.adapter = TicketAdapter(
                    context = this,
                    tickets = tickets,
                    onClick = { fileUrl ->
                        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse(fileUrl), "application/pdf")
                            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        }
                        startActivity(viewIntent)
                    },
                    onDelete = { ticket ->
                        deleteTicket(ticket)
                    }
                )
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Błąd pobierania biletów: $exception", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchPhotosForTrip() {
        db.collection("photos")
            .whereEqualTo("tripId", tripId)
            .get()
            .addOnSuccessListener { result ->
                val photos = result.mapNotNull { document ->
                    val photoUrl = document.getString("photoUrl") ?: return@mapNotNull null
                    Photo(document.id, photoUrl)
                }
                binding.rvPhotos.layoutManager =
                    LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
                binding.rvPhotos.adapter = PhotoAdapter(
                    context = this,
                    photos = photos,
                    onClick = { photo, position ->
                        // Utwórz listę ścieżek zdjęć
                        val photoUrls = ArrayList<String>()
                        photos.forEach { photoUrls.add(it.photoUrl) }
                        val intent = Intent(this, FullScreenPhotosActivity::class.java)
                        intent.putStringArrayListExtra("photoList", photoUrls)
                        intent.putExtra("startIndex", position)
                        startActivity(intent)
                    },
                    onDelete = { photo ->
                        deletePhoto(photo)
                    }
                )
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Błąd pobierania zdjęć: ${exception.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && data != null) {
            when (requestCode) {
                PICK_PDF_REQUEST_CODE -> {
                    val pdfUri = data.data
                    pdfUri?.let { saveTicketUrlToFirestore(it) }
                }
                PICK_PHOTO_REQUEST_CODE -> {
                    if (data.clipData != null) {
                        val count = data.clipData!!.itemCount
                        for (i in 0 until count) {
                            val photoUri = data.clipData!!.getItemAt(i).uri
                            copyFileToLocalStorage(photoUri)?.let { localPath ->
                                savePhotoPathToFirestore(localPath)
                            }
                        }
                    } else {
                        data.data?.let { photoUri ->
                            copyFileToLocalStorage(photoUri)?.let { localPath ->
                                savePhotoPathToFirestore(localPath)
                            }
                        }
                    }
                }
            }
        }
    }

    // Kopiuje plik z podanego URI do lokalnego katalogu aplikacji i zwraca jego absolutną ścieżkę.
    private fun copyFileToLocalStorage(uri: Uri): String? {
        return try {
            val folder = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            if (folder != null && !folder.exists()) {
                folder.mkdirs()
            }
            val fileName = "photo_${System.currentTimeMillis()}.jpg"
            val file = File(folder, fileName)
            val inputStream = contentResolver.openInputStream(uri)
            val outputStream = FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            file.absolutePath
        } catch (e: Exception) {
            Toast.makeText(this, "Błąd kopiowania zdjęcia: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            null
        }
    }

    // Zapisuje lokalną ścieżkę zdjęcia w Firestore
    private fun savePhotoPathToFirestore(localPath: String) {
        val photoData = hashMapOf(
            "photoUrl" to localPath,
            "tripId" to tripId,
            "userId" to auth.currentUser?.uid.orEmpty()
        )
        db.collection("photos")
            .add(photoData)
            .addOnSuccessListener {
                Toast.makeText(this, "Zdjęcie dodane", Toast.LENGTH_SHORT).show()
                fetchPhotosForTrip()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Błąd zapisywania zdjęcia: ${exception.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveTicketUrlToFirestore(uri: Uri) {
        try {
            // Zapis uprawnień do tego pliku (żeby nie stracić dostępu po restarcie)
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

        // Zapisujemy po prostu URI w Firestore
        val ticketData = hashMapOf(
            "fileUrl" to uri.toString(),
            "tripId" to tripId,
            "userId" to auth.currentUser?.uid.orEmpty()
        )

        db.collection("tickets")
            .add(ticketData)
            .addOnSuccessListener {
                Toast.makeText(this, "Bilet został dodany", Toast.LENGTH_SHORT).show()
                fetchTicketsForTrip() // odśwież widok
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Błąd zapisu biletu: ${exception.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteTicket(ticket: Ticket) {
        db.collection("tickets")
            .document(ticket.ticketId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Bilet usunięty", Toast.LENGTH_SHORT).show()
                fetchTicketsForTrip()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Błąd usuwania biletu: $exception", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deletePhoto(photo: Photo) {
        db.collection("photos")
            .document(photo.photoId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Zdjęcie usunięte", Toast.LENGTH_SHORT).show()
                fetchPhotosForTrip()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Błąd usuwania zdjęcia: ${exception.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }
}
