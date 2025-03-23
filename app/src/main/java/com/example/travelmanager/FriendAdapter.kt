package com.example.travelmanager

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class FriendAdapter(
    private val context: Context,
    private val friendsList: MutableList<User>,
    private val onFriendRemoved: () -> Unit
) : RecyclerView.Adapter<FriendAdapter.FriendViewHolder>() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    inner class FriendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val friendEmail: TextView = itemView.findViewById(R.id.friendEmail)
        val removeFriendButton: Button = itemView.findViewById(R.id.removeFriendButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_friend, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val friend = friendsList[position]
        holder.friendEmail.text = friend.email

        holder.removeFriendButton.setOnClickListener {
            removeFriend(friend.email)  // ← tutaj użyj email zamiast uid
        }
    }

    override fun getItemCount(): Int {
        return friendsList.size
    }

    private fun removeFriend(friendEmail: String) {
        val currentUser = auth.currentUser
        currentUser?.let { user ->
            db.collection("users").document(user.email!!)
                .update("friends", FieldValue.arrayRemove(friendEmail))
                .addOnSuccessListener {
                    friendsList.removeAll { it.email == friendEmail }
                    notifyDataSetChanged()
                    onFriendRemoved()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Błąd usuwania znajomego", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
