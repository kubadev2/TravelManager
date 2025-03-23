package com.example.travelmanager

import android.os.Bundle
import android.text.InputType
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
            db.collection("users").document(user.uid).get()
                .addOnSuccessListener { document ->
                    val userData = document.toObject(User::class.java)
                    val friendIds = userData?.friends ?: listOf()
                    loadFriends(friendIds)
                }
        }
    }

    private fun loadFriends(friendIds: List<String>) {
        friendsList.clear()
        if (friendIds.isEmpty()) {
            friendAdapter.notifyDataSetChanged()
            return
        }

        for (friendId in friendIds) {
            db.collection("users").document(friendId).get()
                .addOnSuccessListener { document ->
                    val friend = document.toObject(User::class.java)
                    friend?.let {
                        friendsList.add(it)
                        friendAdapter.notifyDataSetChanged()
                    }
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
        db.collection("users").whereEqualTo("email", email).get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Toast.makeText(this, "Użytkownik nie znaleziony", Toast.LENGTH_SHORT).show()
                } else {
                    val friend = documents.first().toObject(User::class.java)
                    val currentUser = auth.currentUser
                    currentUser?.let { user ->
                        db.collection("users").document(user.uid)
                            .update("friends", FieldValue.arrayUnion(friend.uid))
                            .addOnSuccessListener {
                                Toast.makeText(this, "Dodano znajomego", Toast.LENGTH_SHORT).show()
                                fetchFriends()
                            }
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Wystąpił błąd", Toast.LENGTH_SHORT).show()
            }
    }
}
