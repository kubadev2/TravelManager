package com.example.travelmanager

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues.TAG
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.travelmanager.databinding.ActivityTripDetailsBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.DriveScopes
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.Permission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class TripDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTripDetailsBinding
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private lateinit var companionsAdapter: CompanionsAdapter
    private val companionsList = mutableListOf<Companion>()
    private var tripId: String = ""
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val PICK_PDF_REQUEST_CODE = 1001
    private val PICK_PHOTO_REQUEST_CODE = 1002
    private val PICK_SHARED_PHOTOS_REQUEST_CODE = 1003
    private val RC_SIGN_IN = 400
    private val RC_AUTHORIZATION = 401

    private var driveService: Drive? = null

    private var isCurrentUserOwner: Boolean = false
    private var tripOwnerEmail: String? = null

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && data != null) {
            when (requestCode) {
                RC_SIGN_IN, RC_AUTHORIZATION -> {
                    uploadAllLocalPhotosToDrive()
                }

                PICK_PDF_REQUEST_CODE -> {
                    val uri = data.data
                    uri?.let {
                        try {
                            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        } catch (e: SecurityException) {
                            e.printStackTrace()
                        }
                        saveTicketUrlToFirestore(it)
                    }
                }

                PICK_PHOTO_REQUEST_CODE -> {
                    if (data.clipData != null) {
                        val count = data.clipData!!.itemCount
                        for (i in 0 until count) {
                            val photoUri = data.clipData!!.getItemAt(i).uri
                            try {
                                contentResolver.takePersistableUriPermission(photoUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            } catch (e: SecurityException) {
                                e.printStackTrace()
                            }
                            savePhotoPathToFirestore(photoUri.toString())
                        }
                    } else {
                        data.data?.let { photoUri ->
                            try {
                                contentResolver.takePersistableUriPermission(photoUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            } catch (e: SecurityException) {
                                e.printStackTrace()
                            }
                            savePhotoPathToFirestore(photoUri.toString())
                        }
                    }
                }

                PICK_SHARED_PHOTOS_REQUEST_CODE -> {
                    val uris = mutableListOf<Uri>()
                    if (data.clipData != null) {
                        val count = data.clipData!!.itemCount
                        for (i in 0 until count) {
                            val uri = data.clipData!!.getItemAt(i).uri
                            try {
                                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            } catch (e: SecurityException) {
                                e.printStackTrace()
                            }
                            uris.add(uri)
                        }
                    } else {
                        data.data?.let {
                            try {
                                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            } catch (e: SecurityException) {
                                e.printStackTrace()
                            }
                            uris.add(it)
                        }
                    }
                    uploadSharedPhotosToDrive(uris)
                }
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tripId = savedInstanceState?.getString("TRIP_ID") ?: ""
        binding = ActivityTripDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (tripId.isEmpty()) {
            tripId = intent.getStringExtra("tripId") ?: ""
        }

        if (tripId.isNotEmpty()) {
            checkIfUserIsOwner()
            fetchTripDetails(tripId)
            fetchTicketsForTrip()
            fetchPhotosForTrip()
            fetchCompanionsForTrip()
            checkSharedFolderExistence()
            if (supportFragmentManager.findFragmentById(R.id.flTripPlans) == null) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.flTripPlans, TripDetailsFragment.newInstance(tripId))
                    .commit()
            }
        } else {
            Toast.makeText(this, "Brak przekazanego ID wycieczki", Toast.LENGTH_SHORT).show()
        }

        binding.btnAddTicket.setOnClickListener {
            val openDocIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "image/*"))
            }
            startActivityForResult(openDocIntent, PICK_PDF_REQUEST_CODE)
        }

        binding.btnAddPhoto.setOnClickListener {
            val openMediaIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            startActivityForResult(openMediaIntent, PICK_PHOTO_REQUEST_CODE)
        }

        binding.btnUploadSharedPhotos.setOnClickListener {
            showUploadSharedPhotosDialog()
        }

        binding.ivGoogleDriveIcon.setOnClickListener {
            val link = binding.tvSharedFolderLink.text.toString()
            if (link.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                startActivity(intent)
            } else {
                Toast.makeText(this, "Brak linku do folderu", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnEditTrip.setOnClickListener {
            showEditTripDialog(
                binding.tvDeparturePlace.text.toString(),
                binding.tvStartDate.text.toString(),
                binding.tvEndDate.text.toString()
            )
        }

        companionsAdapter = CompanionsAdapter(companionsList) { companion ->
            AlertDialog.Builder(this)
                .setMessage("Czy na pewno chcesz usunąć ${companion.email}?")
                .setPositiveButton("Usuń") { _, _ ->
                    removeCompanionFromTrip(companion)
                }
                .setNegativeButton("Anuluj", null)
                .show()
        }
        binding.rvCompanions.layoutManager = LinearLayoutManager(this)
        binding.rvCompanions.adapter = companionsAdapter

        binding.btnAddCompanion.setOnClickListener {
            showCompanionSelectionDialog()
        }
    }

    private fun showCompanionSelectionDialog() {
        val currentUser = auth.currentUser
        currentUser?.email?.let { userEmail ->
            db.collection("users").document(userEmail)
                .get()
                .addOnSuccessListener { document ->
                    // [CHANGE] sprawdź czy activity nie jest niszczona
                    if (isFinishing || isDestroyed) return@addOnSuccessListener

                    val friendsEmails = document.get("friends") as? List<String> ?: emptyList()
                    val selectedFriends = mutableListOf<String>()
                    val friendsArray = friendsEmails.toTypedArray()
                    val checkedArray = BooleanArray(friendsArray.size)

                    AlertDialog.Builder(this)
                        .setTitle("Wybierz znajomych do wycieczki")
                        .setMultiChoiceItems(friendsArray, checkedArray) { _, which, isChecked ->
                            if (isChecked) {
                                selectedFriends.add(friendsArray[which])
                            } else {
                                selectedFriends.remove(friendsArray[which])
                            }
                        }
                        .setPositiveButton("Dodaj") { _, _ ->
                            selectedFriends.forEach { email ->
                                addCompanionToTrip(email)
                            }
                        }
                        .setNegativeButton("Anuluj", null)
                        .show()
                }
        }
    }

    private fun requestSignIn() {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .requestIdToken(getString(R.string.default_web_client_id))
            .build()
        val client = GoogleSignIn.getClient(this, options)
        startActivityForResult(client.signInIntent, RC_SIGN_IN)
    }

    private fun checkSharedFolderExistence() {
        db.collection("trips").document(tripId).get()
            .addOnSuccessListener { doc ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener

                val folderLink = doc.getString("sharedFolderLink")
                if (folderLink.isNullOrEmpty()) {
                    binding.btnCreateSharedFolder.visibility = View.VISIBLE
                    binding.tvSharedFolderLink.visibility = View.GONE
                    binding.ivGoogleDriveIcon.visibility = View.GONE
                    binding.btnUploadSharedPhotos.visibility = View.GONE
                } else {
                    binding.btnCreateSharedFolder.visibility = View.GONE
                    binding.tvSharedFolderLink.apply {
                        text = folderLink
                        visibility = View.VISIBLE
                    }
                    binding.ivGoogleDriveIcon.visibility = View.VISIBLE
                    binding.btnUploadSharedPhotos.visibility = View.VISIBLE
                }
            }

        binding.btnCreateSharedFolder.setOnClickListener {
            createSharedFolderOnDrive()
        }
    }

    private fun createSharedFolderOnDrive() {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account == null) {
            requestSignIn()
            return
        }

        val credential = GoogleAccountCredential.usingOAuth2(this, listOf(DriveScopes.DRIVE_FILE))
        credential.selectedAccount = account.account

        driveService = Drive.Builder(
            NetHttpTransport(),
            GsonFactory(),
            credential
        ).setApplicationName("TravelManager").build()

        Thread {
            try {
                val folderMetadata = com.google.api.services.drive.model.File()
                folderMetadata.name = "Wycieczka_${tripId}_SharedPhotos"
                folderMetadata.mimeType = "application/vnd.google-apps.folder"

                val folder = driveService!!.files().create(folderMetadata)
                    .setFields("id, webViewLink")
                    .execute()

                val folderId = folder.id
                val folderLink = folder.webViewLink

                addPermissionsToFolder(driveService!!, folderId)
                saveFolderInfoToFirestore(folderId, folderLink)

                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    Toast.makeText(this, "Folder utworzony!", Toast.LENGTH_SHORT).show()
                    binding.tvSharedFolderLink.apply {
                        text = folderLink
                        visibility = View.VISIBLE
                    }
                    binding.btnCreateSharedFolder.visibility = View.GONE
                }
            } catch (e: UserRecoverableAuthIOException) {
                startActivityForResult(e.intent, RC_AUTHORIZATION)
            } catch (e: Exception) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    Toast.makeText(this, "Błąd: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun addPermissionsToFolder(service: Drive, folderId: String) {
        db.collection("trips").document(tripId).get()
            .addOnSuccessListener { document ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                val companionsEmails = document.get("companions") as? List<String> ?: emptyList()
                companionsEmails.forEach { email ->
                    val userPermission = Permission()
                        .setType("user")
                        .setRole("writer")
                        .setEmailAddress(email)

                    try {
                        service.permissions().create(folderId, userPermission)
                            .setSendNotificationEmail(true)
                            .execute()
                    } catch (e: Exception) {
                        Log.e("TripDetailsActivity", "Błąd nadawania uprawnień dla $email", e)
                    }
                }
            }
    }

    private fun removePermissionFromFolder(email: String) {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account == null) {
            requestSignIn()
            return
        }

        val credential = GoogleAccountCredential.usingOAuth2(this, listOf(DriveScopes.DRIVE_FILE))
        credential.selectedAccount = account.account

        val driveService = Drive.Builder(
            NetHttpTransport(),
            GsonFactory(),
            credential
        ).setApplicationName("TravelManager").build()

        db.collection("trips").document(tripId).get()
            .addOnSuccessListener { doc ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener

                val folderId = doc.getString("sharedFolderId") ?: return@addOnSuccessListener
                Thread {
                    try {
                        val permissions = driveService.permissions().list(folderId)
                            .setFields("permissions(id,emailAddress)")
                            .execute()

                        val permission = permissions.permissions?.firstOrNull {
                            it.emailAddress == email
                        }

                        if (permission != null) {
                            driveService.permissions().delete(folderId, permission.id).execute()
                            Log.d("TripDetailsActivity", "Usunięto uprawnienia dla $email")
                        } else {
                            Log.w("TripDetailsActivity", "Nie znaleziono uprawnień dla $email")
                        }

                    } catch (e: Exception) {
                        Log.e("TripDetailsActivity", "Błąd usuwania uprawnień dla $email", e)
                    }
                }.start()
            }
    }

    private fun checkIfUserIsOwner() {
        val currentUser = auth.currentUser ?: return
        db.collection("trips").document(tripId).get()
            .addOnSuccessListener { document ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener

                val ownerId = document.getString("userId")
                val ownerEmail = document.getString("userEmail")
                tripOwnerEmail = ownerEmail
                isCurrentUserOwner = currentUser.uid == ownerId
                setupVisibilityBasedOnRole(isCurrentUserOwner)
                fetchCompanions()
            }
    }

    private fun setupVisibilityBasedOnRole(isOwner: Boolean) {
        if (!isOwner) {
            binding.btnAddCompanion.visibility = View.GONE
            binding.btnEditTrip.visibility = View.GONE
            binding.btnCreateSharedFolder.visibility = View.GONE

            binding.btnAddTicket.visibility = View.VISIBLE
            binding.btnAddPhoto.visibility = View.VISIBLE
        } else {
            // jeśli jest owner, nic nie chowamy
        }
    }

    private fun removeCompanionFromTrip(companion: Companion) {
        db.collection("trips")
            .document(tripId)
            .update("companions", FieldValue.arrayRemove(companion.email))
            .addOnSuccessListener {
                if (isFinishing || isDestroyed) return@addOnSuccessListener

                Toast.makeText(this, "Usunięto towarzysza", Toast.LENGTH_SHORT).show()
                fetchCompanions()
                removePermissionFromFolder(companion.email)
            }
            .addOnFailureListener {
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(this, "Błąd podczas usuwania", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun addCompanionToTrip(email: String) {
        val tripRef = db.collection("trips").document(tripId)
        tripRef.get().addOnSuccessListener { document ->
            if (isFinishing || isDestroyed) return@addOnSuccessListener

            val sharedFolderId = document.getString("sharedFolderId")

            tripRef.update("companions", FieldValue.arrayUnion(email))
                .addOnSuccessListener {
                    if (isFinishing || isDestroyed) return@addOnSuccessListener

                    Toast.makeText(this, "Dodano towarzysza", Toast.LENGTH_SHORT).show()
                    fetchCompanions()

                    if (!sharedFolderId.isNullOrEmpty()) {
                        grantDrivePermissionToUser(sharedFolderId, email)
                    }
                }
                .addOnFailureListener {
                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(this, "Błąd podczas dodawania", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    private fun grantDrivePermissionToUser(folderId: String, email: String) {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account == null) {
            requestSignIn()
            return
        }

        val credential = GoogleAccountCredential.usingOAuth2(
            this, listOf(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = account.account

        val driveService = Drive.Builder(
            NetHttpTransport(),
            GsonFactory(),
            credential
        ).setApplicationName("TravelManager").build()

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val permission = Permission().apply {
                        type = "user"
                        role = "writer"
                        emailAddress = email
                    }
                    driveService.permissions().create(folderId, permission)
                        .setSendNotificationEmail(false)
                        .execute()
                }
            } catch (e: Exception) {
                Log.e("DrivePermission", "Błąd nadawania uprawnień dla $email", e)
            }
        }
    }

    private fun fetchCompanionsForTrip() {
        db.collection("trips").document(tripId)
            .get()
            .addOnSuccessListener { doc ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener

                val emails = doc.get("companions") as? List<String> ?: emptyList()
                companionsList.clear()
                emails.forEach { email ->
                    companionsList.add(Companion(email))
                }
                companionsAdapter.notifyDataSetChanged()
            }
    }

    private fun fetchCompanions() {
        val currentUser = auth.currentUser ?: return

        db.collection("trips").document(tripId).get().addOnSuccessListener { tripDoc ->
            if (isFinishing || isDestroyed) return@addOnSuccessListener

            val companionsEmails = tripDoc.get("companions") as? List<String> ?: listOf()
            val ownerId = tripDoc.getString("userId") ?: return@addOnSuccessListener

            db.collection("users")
                .whereEqualTo("userId", ownerId)
                .limit(1)
                .get()
                .addOnSuccessListener { userSnapshot ->
                    if (isFinishing || isDestroyed) return@addOnSuccessListener

                    val ownerEmail = userSnapshot.documents.firstOrNull()?.getString("email")
                    if (ownerEmail == null) {
                        Toast.makeText(this, "Nie znaleziono emaila właściciela", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }

                    val companions = mutableListOf<Companion>()
                    companions.add(Companion("$ownerEmail (założyciel)"))
                    companionsEmails
                        .filter { it != ownerEmail }
                        .forEach { companions.add(Companion(it)) }

                    setupCompanionsRecyclerView(companions)
                }
        }
    }

    private fun setupCompanionsRecyclerView(companions: MutableList<Companion>) {
        binding.rvCompanions.layoutManager = LinearLayoutManager(this)
        companionsAdapter = CompanionsAdapter(companions) { companion ->
            if (!isCurrentUserOwner) {
                Toast.makeText(this, "Tylko założyciel może usuwać towarzyszy", Toast.LENGTH_SHORT).show()
                return@CompanionsAdapter
            }
            if (companion.email.contains("(założyciel)")) {
                Toast.makeText(this, "Nie można usunąć założyciela wycieczki", Toast.LENGTH_SHORT).show()
            } else {
                AlertDialog.Builder(this)
                    .setMessage("Czy na pewno chcesz usunąć ${companion.email}?")
                    .setPositiveButton("Usuń") { _, _ -> removeCompanionFromTrip(companion) }
                    .setNegativeButton("Anuluj", null)
                    .show()
            }
        }
        binding.rvCompanions.adapter = companionsAdapter
    }

    private fun showEditTripDialog(
        currentDeparture: String,
        currentStartDate: String,
        currentEndDate: String
    ) {
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
            .setNegativeButton("Anuluj") { dialog, _ -> dialog.dismiss() }
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
                if (isFinishing || isDestroyed) return@addOnSuccessListener

                Toast.makeText(this, "Dane wycieczki zaktualizowane", Toast.LENGTH_SHORT).show()
                binding.tvDeparturePlace.text = newDeparture
                binding.tvStartDate.text = newStartDate
                binding.tvEndDate.text = newEndDate

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val fragment = supportFragmentManager.findFragmentById(R.id.flTripPlans)
                    if (fragment is TripDetailsFragment) {
                        fragment.refreshPlans(newStartDate, newEndDate)
                    }
                }
            }
            .addOnFailureListener { exception ->
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(this, "Błąd aktualizacji: ${exception.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun fetchTripDetails(tripId: String) {
        db.collection("trips")
            .document(tripId)
            .get()
            .addOnSuccessListener { document ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener

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
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(this, "Błąd pobierania danych: $exception", Toast.LENGTH_SHORT)
                        .show()
                }
            }
    }

    @OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun fetchPhotosForTrip() {
        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("photos")
            .whereEqualTo("tripId", tripId)
            .whereEqualTo("userId", currentUserId)
            .get()
            .addOnSuccessListener { result ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener

                val photos = result.mapNotNull {
                    val photoUrl = it.getString("photoUrl") ?: return@mapNotNull null
                    Photo(it.id, photoUrl)
                }

                binding.rvPhotos.layoutManager =
                    LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
                binding.rvPhotos.adapter = PhotoAdapter(
                    context = this,
                    photos = photos,
                    onClick = { photo, position ->
                        val photoUrls = photos.map { it.photoUrl }
                        val intent = Intent(this, FullScreenPhotosActivity::class.java)
                        intent.putStringArrayListExtra("photoList", ArrayList(photoUrls))
                        intent.putExtra("startIndex", position)
                        startActivity(intent)
                    },
                    onDelete = { photo -> deletePhoto(photo) }
                )
            }
    }

    private fun copyFileToLocalStorage(uri: Uri): String? {
        return try {
            val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
            val extension = when {
                mimeType.startsWith("video") -> ".mp4"
                mimeType.startsWith("image") -> ".jpg"
                else -> ".dat" // na wszelki wypadek
            }

            val folder = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            if (folder != null && !folder.exists()) {
                folder.mkdirs()
            }

            val fileName = "media_${System.currentTimeMillis()}$extension"
            val file = File(folder, fileName)

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }

            file.absolutePath
        } catch (e: Exception) {
            Toast.makeText(this, "Błąd kopiowania pliku: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            null
        }
    }


    private fun savePhotoPathToFirestore(localPath: String) {
        if (!isNetworkAvailable()) {
            showOfflineInfo()
        }

        val uri = Uri.parse(localPath)

        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

        val photoData = hashMapOf(
            "photoUrl" to localPath,
            "tripId" to tripId,
            "userId" to auth.currentUser?.uid.orEmpty()
        )
        db.collection("photos")
            .add(photoData)
            .addOnSuccessListener {
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                Toast.makeText(this, "Zdjęcie dodane", Toast.LENGTH_SHORT).show()
                fetchPhotosForTrip()
            }
            .addOnFailureListener { exception ->
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(this, "Błąd zapisywania zdjęcia: ${exception.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
    }


    private fun deletePhoto(photo: Photo) {
        if (!isNetworkAvailable()) {
            showOfflineInfo()
        }

        db.collection("photos")
            .document(photo.photoId)
            .delete()
            .addOnSuccessListener {
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                Toast.makeText(this, "Zdjęcie usunięte", Toast.LENGTH_SHORT).show()
                fetchPhotosForTrip()
            }
            .addOnFailureListener { exception ->
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(this, "Błąd usuwania zdjęcia: ${exception.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun fetchTicketsForTrip() {
        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("tickets")
            .whereEqualTo("tripId", tripId)
            .whereEqualTo("userId", currentUserId)
            .get()
            .addOnSuccessListener { result ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener

                val tickets = result.mapNotNull {
                    val fileUrl = it.getString("fileUrl") ?: return@mapNotNull null
                    Ticket(it.id, fileUrl)
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
                    onDelete = { ticket -> deleteTicket(ticket) }
                )
            }
    }

    private fun saveTicketUrlToFirestore(uri: Uri) {
        if (!isNetworkAvailable()) {
            showOfflineInfo()
        }

        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

        val ticketData = hashMapOf(
            "fileUrl" to uri.toString(),
            "tripId" to tripId,
            "userId" to auth.currentUser?.uid.orEmpty()
        )

        db.collection("tickets")
            .add(ticketData)
            .addOnSuccessListener {
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                Toast.makeText(this, "Bilet został dodany", Toast.LENGTH_SHORT).show()
                fetchTicketsForTrip()
            }
            .addOnFailureListener { exception ->
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(this, "Błąd zapisu biletu: ${exception.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun deleteTicket(ticket: Ticket) {
        if (!isNetworkAvailable()) {
            showOfflineInfo()
        }

        db.collection("tickets")
            .document(ticket.ticketId)
            .delete()
            .addOnSuccessListener {
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                Toast.makeText(this, "Bilet usunięty", Toast.LENGTH_SHORT).show()
                fetchTicketsForTrip()
            }
            .addOnFailureListener { exception ->
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(this, "Błąd usuwania biletu: $exception", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun showUploadSharedPhotosDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_upload_shared_photos, null)
        val cbTransportTickets = dialogView.findViewById<CheckBox>(R.id.cbTransportTickets)
        val cbPhotos = dialogView.findViewById<CheckBox>(R.id.cbPhotos)
        val cbPlanTickets = dialogView.findViewById<CheckBox>(R.id.cbPlanTickets)
        val cbConfirm = dialogView.findViewById<CheckBox>(R.id.cbConfirmSharedUpload)

        AlertDialog.Builder(this)
            .setTitle("Prześlij dane")
            .setView(dialogView)
            .setPositiveButton("Prześlij") { _, _ ->
                val selectedItems = mutableListOf<String>()
                if (cbTransportTickets.isChecked) selectedItems.add("transport")
                if (cbPhotos.isChecked) selectedItems.add("photos")
                if (cbPlanTickets.isChecked) selectedItems.add("plans")

                if (!cbConfirm.isChecked) {
                    Toast.makeText(this, "Musisz potwierdzić widoczność danych", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (selectedItems.isEmpty()) {
                    Toast.makeText(this, "Wybierz co chcesz przesłać", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                ensureStoragePermissions {
                    uploadSelectedDataToDrive(selectedItems)
                }

            }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    private fun uploadSelectedDataToDrive(selectedItems: List<String>) {
        if ("photos" in selectedItems) {
            uploadAllLocalPhotosToDrive()
        }
        if ("transport" in selectedItems) {
            uploadTransportTicketsToDrive()
        }
        if ("plans" in selectedItems) {
            uploadPlanTicketsToDrive()
        }
    }

    private fun uploadAllLocalPhotosToDrive() {
        Log.d("UploadPhotos", "⏳ Rozpoczynamy uploadAllLocalPhotosToDrive")

        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account == null) {
            Log.w("UploadPhotos", "❌ Brak zalogowanego konta Google - wymagane ponowne logowanie")
            requestSignIn()
            return
        }

        val credential = GoogleAccountCredential.usingOAuth2(this, listOf(DriveScopes.DRIVE_FILE))
        credential.selectedAccount = account.account

        val drive = Drive.Builder(NetHttpTransport(), GsonFactory(), credential)
            .setApplicationName("TravelManager").build()

        db.collection("trips").document(tripId).get().addOnSuccessListener { doc ->
            val folderId = doc.getString("sharedFolderId")
            if (folderId.isNullOrEmpty()) {
                Log.e("UploadPhotos", "❌ sharedFolderId nie istnieje w Firestore")
                return@addOnSuccessListener
            }

            db.collection("photos")
                .whereEqualTo("tripId", tripId)
                .get()
                .addOnSuccessListener { result ->
                    val photoUris = result.mapNotNull { it.getString("photoUrl") }
                    Log.d("UploadPhotos", "📷 Znaleziono ${photoUris.size} lokalnych zdjęć/wideo")

                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val existingDriveFileNames = try {
                                drive.files().list()
                                    .setQ("'$folderId' in parents")
                                    .setFields("files(name)")
                                    .execute()
                                    .files
                                    .mapNotNull { it.name }
                            } catch (e: UserRecoverableAuthIOException) {
                                Log.w("UploadPhotos", "🔐 Potrzebna dodatkowa autoryzacja – uruchamiam ekran zgody")
                                runOnUiThread {
                                    startActivityForResult(e.intent, RC_AUTHORIZATION)
                                }
                                return@launch
                            }


                            for (uriStr in photoUris) {
                                try {
                                    val uri = Uri.parse(uriStr)
                                    val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
                                    val extension = when {
                                        mimeType.startsWith("video") -> ".mp4"
                                        mimeType.startsWith("image") -> ".jpg"
                                        else -> ".dat"
                                    }

                                    val fileName = "media_${System.currentTimeMillis()}$extension"

                                    if (fileName in existingDriveFileNames) {
                                        Log.d("UploadPhotos", "⏭️ Pomijam $fileName (już na Dysku)")
                                        continue
                                    }

                                    val inputStream = contentResolver.openInputStream(uri)
                                    if (inputStream == null) {
                                        Log.w("UploadPhotos", "⚠️ Nie udało się otworzyć InputStream z $uri")
                                        continue
                                    }

                                    val fileMetadata = com.google.api.services.drive.model.File().apply {
                                        name = fileName
                                        parents = listOf(folderId)
                                    }

                                    val mediaContent = com.google.api.client.http.InputStreamContent(
                                        mimeType, inputStream
                                    )

                                    drive.files().create(fileMetadata, mediaContent)
                                        .setFields("id")
                                        .execute()

                                    Log.d("UploadPhotos", "✅ Przesłano $fileName")

                                } catch (e: Exception) {
                                    Log.e("UploadPhotos", "❌ Błąd uploadu $uriStr: ${e.localizedMessage}", e)
                                }
                            }

                            withContext(Dispatchers.Main) {
                                if (isFinishing || isDestroyed) return@withContext
                                Toast.makeText(this@TripDetailsActivity, "✅ Nowe multimedia przesłane na Dysk", Toast.LENGTH_SHORT).show()
                            }

                        } catch (e: Exception) {
                            Log.e("UploadPhotos", "❌ Błąd pobierania plików z Dysku: ${e.localizedMessage}", e)
                            withContext(Dispatchers.Main) {
                                if (!isFinishing && !isDestroyed) {
                                    Toast.makeText(this@TripDetailsActivity, "Błąd pobierania plików z Dysku", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
        }
    }



    // ZMIANA: Obsługa zdjęć jako biletów
    private fun uploadTransportTicketsToDrive() {
        val account = GoogleSignIn.getLastSignedInAccount(this) ?: return requestSignIn()
        val credential = GoogleAccountCredential.usingOAuth2(this, listOf(DriveScopes.DRIVE_FILE))
        credential.selectedAccount = account.account
        val drive = Drive.Builder(NetHttpTransport(), GsonFactory(), credential)
            .setApplicationName("TravelManager").build()

        db.collection("trips").document(tripId).get().addOnSuccessListener { doc ->
            if (isFinishing || isDestroyed) return@addOnSuccessListener

            val tripFolderId = doc.getString("sharedFolderId") ?: return@addOnSuccessListener

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val biletyFolderId = createOrGetSubfolder(drive, tripFolderId, "Bilety")

                    val ticketsSnapshot = db.collection("tickets")
                        .whereEqualTo("tripId", tripId)
                        .whereEqualTo("userId", auth.currentUser?.uid)
                        .get()
                        .await()

                    for (ticketDoc in ticketsSnapshot.documents) {
                        val fileUrl = ticketDoc.getString("fileUrl") ?: continue
                        val uri = Uri.parse(fileUrl)
                        val mimeType = contentResolver.getType(uri) ?: "application/pdf"
                        val extension = when {
                            mimeType.startsWith("image") -> ".jpg"
                            mimeType.startsWith("video") -> ".mp4"
                            else -> ".pdf"
                        }
                        val fileName = "transport_ticket_${System.currentTimeMillis()}$extension"
                        val inputStream = contentResolver.openInputStream(uri) ?: continue

                        val fileMetadata = com.google.api.services.drive.model.File().apply {
                            name = fileName
                            parents = listOf(biletyFolderId)
                        }

                        val mediaContent = com.google.api.client.http.InputStreamContent(
                            mimeType, inputStream
                        )

                        drive.files().create(fileMetadata, mediaContent).execute()
                    }

                    withContext(Dispatchers.Main) {
                        if (isFinishing || isDestroyed) return@withContext
                        Toast.makeText(this@TripDetailsActivity, "Bilety komunikacyjne przesłane", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("UploadTransportTickets", "Błąd: ${e.localizedMessage}", e)
                }
            }
        }
    }

    private fun uploadPlanTicketsToDrive() {
        val account = GoogleSignIn.getLastSignedInAccount(this) ?: return requestSignIn()
        val credential = GoogleAccountCredential.usingOAuth2(this, listOf(DriveScopes.DRIVE_FILE))
        credential.selectedAccount = account.account
        val drive = Drive.Builder(NetHttpTransport(), GsonFactory(), credential)
            .setApplicationName("TravelManager").build()

        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("trips").document(tripId).get().addOnSuccessListener { doc ->
            if (isFinishing || isDestroyed) return@addOnSuccessListener

            val tripFolderId = doc.getString("sharedFolderId") ?: return@addOnSuccessListener

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val biletyFolderId = createOrGetSubfolder(drive, tripFolderId, "Bilety")

                    val ticketsSnapshot = db.collection("tickets")
                        .whereEqualTo("tripId", tripId)
                        .whereEqualTo("userId", currentUserId)
                        .get()
                        .await()

                    for (ticketDoc in ticketsSnapshot.documents) {
                        val dayNumber = ticketDoc.getLong("dayNumber") ?: continue
                        if (dayNumber <= 0) continue

                        val fileUrl = ticketDoc.getString("fileUrl") ?: continue
                        val uri = Uri.parse(fileUrl)
                        val mimeType = contentResolver.getType(uri) ?: "application/pdf"
                        val extension = when {
                            mimeType.startsWith("image") -> ".jpg"
                            mimeType.startsWith("video") -> ".mp4"
                            else -> ".pdf"
                        }
                        val fileName = "plan_ticket_${System.currentTimeMillis()}$extension"
                        val inputStream = contentResolver.openInputStream(uri) ?: continue

                        val fileMetadata = com.google.api.services.drive.model.File().apply {
                            name = fileName
                            parents = listOf(biletyFolderId)
                        }

                        val mediaContent = com.google.api.client.http.InputStreamContent(
                            mimeType, inputStream
                        )

                        drive.files().create(fileMetadata, mediaContent).execute()
                    }

                    withContext(Dispatchers.Main) {
                        if (isFinishing || isDestroyed) return@withContext
                        Toast.makeText(this@TripDetailsActivity, "Bilety z planu dnia przesłane", Toast.LENGTH_SHORT).show()
                    }

                } catch (e: Exception) {
                    Log.e("UploadPlanTickets", "Błąd: ${e.localizedMessage}", e)
                }
            }
        }
    }


    private fun createOrGetSubfolder(drive: Drive, parentId: String, folderName: String): String {
        val result = drive.files().list()
            .setQ("mimeType='application/vnd.google-apps.folder' and '$parentId' in parents and name='$folderName'")
            .setFields("files(id, name)")
            .execute()

        val folder = result.files?.firstOrNull()
        if (folder != null) return folder.id

        val metadata = com.google.api.services.drive.model.File().apply {
            name = folderName
            mimeType = "application/vnd.google-apps.folder"
            parents = listOf(parentId)
        }

        return drive.files().create(metadata).setFields("id").execute().id
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("TRIP_ID", tripId)
    }

    private fun saveFolderInfoToFirestore(folderId: String, folderLink: String) {
        if (!isNetworkAvailable()) {
            showOfflineInfo()
        }

        db.collection("trips").document(tripId)
            .update(mapOf("sharedFolderId" to folderId, "sharedFolderLink" to folderLink))
    }

    private fun uploadSharedPhotosToDrive(uris: List<Uri>) {
        val account = GoogleSignIn.getLastSignedInAccount(this) ?: return requestSignIn()
        val credential = GoogleAccountCredential.usingOAuth2(this, listOf(DriveScopes.DRIVE_FILE))
        credential.selectedAccount = account.account
        val drive = Drive.Builder(NetHttpTransport(), GsonFactory(), credential)
            .setApplicationName("TravelManager").build()

        db.collection("trips").document(tripId).get().addOnSuccessListener { doc ->
            if (isFinishing || isDestroyed) return@addOnSuccessListener

            val folderId = doc.getString("sharedFolderId") ?: return@addOnSuccessListener
            lifecycleScope.launch(Dispatchers.IO) {
                for (uri in uris) {
                    try {
                        val inputStream = contentResolver.openInputStream(uri)
                        val fileMetadata = com.google.api.services.drive.model.File().apply {
                            name = "shared_photo_${System.currentTimeMillis()}.jpg"
                            parents = listOf(folderId)
                        }
                        val mediaContent = com.google.api.client.http.InputStreamContent("image/jpeg", inputStream)
                        drive.files().create(fileMetadata, mediaContent)
                            .setFields("id")
                            .execute()
                    } catch (e: Exception) {
                        Log.e("UploadToDrive", "Błąd uploadu zdjęcia: ${e.localizedMessage}")
                    }
                }
                withContext(Dispatchers.Main) {
                    if (isFinishing || isDestroyed) return@withContext
                    Toast.makeText(this@TripDetailsActivity, "Zdjęcia przesłane na Dysk", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    private fun ensureStoragePermissions(onGranted: () -> Unit) {
        val permissions = arrayOf(
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        val missingPermissions = permissions.filter {
            checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            onGranted()
        } else {
            requestPermissions(missingPermissions.toTypedArray(), 1234)
            // Po zaakceptowaniu użytkownik musi kliknąć jeszcze raz przycisk
            Toast.makeText(this, "Nadaj uprawnienia i spróbuj ponownie", Toast.LENGTH_LONG).show()
        }
    }


}
