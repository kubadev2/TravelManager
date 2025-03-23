package com.example.travelmanager

import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class FriendsActivity : AppCompatActivity() {

    private lateinit var recyclerViewFriends: RecyclerView
    private lateinit var friendAdapter: FriendAdapter
    private val friendsList = mutableListOf<User>()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friends)

        recyclerViewFriends = findViewById(R.id.recyclerViewFriends)
        recyclerViewFriends.layoutManager = LinearLayoutManager(this)

        friendAdapter = FriendAdapter(this, friendsList) {
            fetchFriends() // odśwież listę po usunięciu
        }
        recyclerViewFriends.adapter = friendAdapter

        fetchFriends()

        val btnAddFriend: Button = findViewById(R.id.btnAddFriend)
        btnAddFriend.setOnClickListener {
            showAddFriendDialog()
        }
    }

    private fun fetchFriends() {
        val currentUser = auth.currentUser
        currentUser?.let { user ->
            db.collection("users").document(user.email!!).get()
                .addOnSuccessListener { document ->
                    val userData = document.toObject(User::class.java)
                    val friendEmails = userData?.friends ?: listOf()
                    loadFriends(friendEmails)
                }
                .addOnFailureListener { e ->
                    Log.e("FriendsActivity", "Błąd pobierania danych użytkownika", e)
                }
        }
    }


    private fun loadFriends(friendEmails: List<String>) {
        friendsList.clear()
        for (friendEmail in friendEmails) {
            db.collection("users").document(friendEmail).get()
                .addOnSuccessListener { document ->
                    val friend = document.toObject(User::class.java)
                    friend?.let { friendsList.add(it) }
                    friendAdapter.notifyDataSetChanged()
                }
                .addOnFailureListener { e ->
                    Log.e("FriendsActivity", "Błąd pobierania znajomego", e)
                }
        }
    }


    private fun showAddFriendDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Dodaj znajomego")

        val input = EditText(this)
        input.inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        builder.setView(input)

        builder.setPositiveButton("Dodaj") { _, _ ->
            val email = input.text.toString()
            addFriendByEmail(email)
        }
        builder.setNegativeButton("Anuluj") { dialog, _ -> dialog.cancel() }

        builder.show()
    }

    private fun addFriendByEmail(email: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Brak zalogowanego użytkownika", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("users").document(email).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    db.collection("users").document(currentUser.email!!)
                        .update("friends", FieldValue.arrayUnion(email))
                        .addOnSuccessListener {
                            Toast.makeText(this, "Dodano znajomego", Toast.LENGTH_SHORT).show()
                            fetchFriends()
                        }
                        .addOnFailureListener { e ->
                            Log.e("FriendsActivity", "Błąd dodawania znajomego", e)
                            Toast.makeText(this, "Błąd dodawania znajomego", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Toast.makeText(this, "Użytkownik o takim emailu nie istnieje", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e("FriendsActivity", "Błąd wyszukiwania użytkownika", e)
            }
    }

}
