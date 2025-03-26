package com.example.travelmanager

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private val RC_SIGN_IN = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1) Inicjalizujemy FirebaseAuth, Firestore
        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // 2) Inicjalizujemy GoogleSignInClient
        val clientId = getString(R.string.default_web_client_id)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestIdToken(clientId)  // <-- ważne przy logowaniu przez Firebase
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // 3) Sprawdzamy, czy użytkownik jest zalogowany
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            navigateToTripsActivity()
            finish()
            return
        }

        // 4) Jeśli niezalogowany, konfigurujemy przycisk do logowania
        findViewById<SignInButton>(R.id.sign_in_button).setOnClickListener {
            signIn() // tutaj już nie wywołujemy signOut()
        }
    }

    private fun signIn() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account)
            } catch (e: ApiException) {
                Log.e("MainActivity", "Logowanie nieudane: ${e.statusCode}", e)
                Toast.makeText(this, "Logowanie nieudane: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount?) {
        if (account != null) {
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        Log.d("MainActivity", "Logowanie Firebase powiodło się.")
                        saveUserToFirestore(account)
                    } else {
                        Log.e("MainActivity", "Błąd logowania Firebase", task.exception)
                        Toast.makeText(this, "Błąd logowania Firebase: ${task.exception?.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
        } else {
            Toast.makeText(this, "Błąd: Konto Google jest puste.", Toast.LENGTH_SHORT).show()
        }
    }


    private fun saveUserToFirestore(account: GoogleSignInAccount) {
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            Log.e("MainActivity", "Brak zalogowanego użytkownika Firebase.")
            return
        }

        val userDoc = firestore.collection("users").document(account.email ?: currentUser.uid)

        userDoc.get().addOnSuccessListener { document ->
            if (!document.exists()) {
                // Dokument jeszcze nie istnieje – tworzymy nowego usera w Firestore
                val newUserData = mapOf(
                    "name" to (account.displayName ?: "Nieznane imię"),
                    "email" to account.email,
                    "photoUrl" to (account.photoUrl?.toString() ?: ""),
                    "userId" to currentUser.uid,
                    // Dajemy pustą listę friends, bo to pierwszy zapis
                    "friends" to listOf<String>()
                )

                userDoc.set(newUserData)
                    .addOnSuccessListener {
                        Log.d("MainActivity", "Nowy użytkownik zapisany w Firestore.")
                        navigateToTripsActivity(account)
                    }
                    .addOnFailureListener { e ->
                        Log.e("MainActivity", "Błąd zapisu użytkownika", e)
                        Toast.makeText(this, "Błąd zapisu użytkownika: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }

            } else {
                // Dokument istnieje – pobieramy starą listę friends
                val oldFriends = document.get("friends") as? List<String> ?: emptyList()

                val mergedFriends = oldFriends

                // Teraz tworzymy mapę z DOKŁADNIE tym, co chcemy zaktualizować
                val updatedUserData = mapOf(
                    "name" to (account.displayName ?: "Nieznane imię"),
                    "email" to account.email,
                    "photoUrl" to (account.photoUrl?.toString() ?: ""),
                    "userId" to currentUser.uid,
                    // Zamiast pustej listy – wykorzystujemy starą listę:
                    "friends" to mergedFriends
                )

                userDoc.update(updatedUserData)
                    .addOnSuccessListener {
                        Log.d("MainActivity", "Użytkownik zaktualizowany w Firestore (zachowano friends).")
                        navigateToTripsActivity(account)
                    }
                    .addOnFailureListener { e ->
                        Log.e("MainActivity", "Błąd aktualizacji użytkownika", e)
                        Toast.makeText(this, "Błąd aktualizacji użytkownika: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    private fun navigateToTripsActivity() {
        // Scenariusz: user jest już zalogowany
        val intent = Intent(this, TripsActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun navigateToTripsActivity(account: GoogleSignInAccount) {
        // Scenariusz: właśnie zalogował się przez Google
        Toast.makeText(this, "Logowanie powiodło się!", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, TripsActivity::class.java).apply {
            putExtra("userName", account.displayName)
            putExtra("userEmail", account.email)
        }
        startActivity(intent)
        finish()
    }
}
