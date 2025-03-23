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

        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        val clientId = getString(R.string.default_web_client_id)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestIdToken(clientId)
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        findViewById<com.google.android.gms.common.SignInButton>(R.id.sign_in_button).setOnClickListener {
            signIn()
        }
    }

    private fun signIn() {
        googleSignInClient.signOut().addOnCompleteListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
        }
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

        val userDoc = firestore.collection("users").document(currentUser.uid)

        userDoc.get().addOnSuccessListener { document ->
            if (!document.exists()) {
                // Tworzymy użytkownika z listą znajomych
                val userData = mapOf(
                    "name" to (account.displayName ?: "Nieznane imię"),
                    "email" to account.email,
                    "photoUrl" to (account.photoUrl?.toString() ?: ""),
                    "friends" to listOf<String>()
                )

                userDoc.set(userData)
                    .addOnSuccessListener {
                        Log.d("MainActivity", "Nowy użytkownik zapisany w Firestore.")
                        navigateToTripsActivity(account)
                    }
                    .addOnFailureListener { e ->
                        Log.e("MainActivity", "Błąd zapisu użytkownika", e)
                        Toast.makeText(this, "Błąd zapisu użytkownika: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                // Aktualizacja danych istniejącego użytkownika (opcjonalnie)
                val updates = mapOf(
                    "name" to (account.displayName ?: "Nieznane imię"),
                    "photoUrl" to (account.photoUrl?.toString() ?: "")
                )
                userDoc.update(updates)
                    .addOnSuccessListener {
                        Log.d("MainActivity", "Użytkownik zaktualizowany w Firestore.")
                        navigateToTripsActivity(account)
                    }
                    .addOnFailureListener { e ->
                        Log.e("MainActivity", "Błąd aktualizacji użytkownika", e)
                        Toast.makeText(this, "Błąd aktualizacji użytkownika: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    private fun navigateToTripsActivity(account: GoogleSignInAccount) {
        Toast.makeText(this, "Logowanie powiodło się!", Toast.LENGTH_SHORT).show()

        val intent = Intent(this, TripsActivity::class.java).apply {
            putExtra("userName", account.displayName)
            putExtra("userEmail", account.email)
        }
        startActivity(intent)
        finish()
    }
}
