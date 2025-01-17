package com.example.travelmanager

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.example.travelmanager.R
import com.google.android.gms.auth.api.signin.GoogleSignInClient

class MainActivity : AppCompatActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private val RC_SIGN_IN = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicjalizacja Firebase
        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Pobieramy client ID z strings.xml
        val clientId = getString(R.string.default_web_client_id)

        // Konfiguracja Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestIdToken(clientId)
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Obsługa kliknięcia przycisku logowania
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
        val email = account.email
        if (email.isNullOrEmpty()) {
            Log.e("MainActivity", "Błąd: Adres e-mail użytkownika jest pusty.")
            Toast.makeText(this, "Błąd: Adres e-mail użytkownika jest pusty.", Toast.LENGTH_SHORT).show()
            return
        }

        val user = mapOf(
            "name" to (account.displayName ?: "Nieznane imię"),
            "email" to email,
            "photoUrl" to (account.photoUrl?.toString() ?: "")
        )

        firestore.collection("users")
            .document(email)
            .set(user)
            .addOnSuccessListener {
                Log.d("MainActivity", "Użytkownik zapisany w Firestore.")
                Toast.makeText(this, "Logowanie powiodło się!", Toast.LENGTH_SHORT).show()

                // Przejdź do TripsActivity
                val intent = Intent(this, TripsActivity::class.java).apply {
                    putExtra("userName", account.displayName)
                    putExtra("userEmail", email)
                }
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Log.e("MainActivity", "Błąd zapisu użytkownika w Firestore", e)
                Toast.makeText(this, "Błąd zapisu użytkownika: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }
}
