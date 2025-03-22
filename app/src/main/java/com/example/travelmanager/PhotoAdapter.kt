package com.example.travelmanager

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.travelmanager.databinding.ItemPhotoBinding
import java.io.File

class PhotoAdapter(
    private val context: Context,
    private var photos: List<Photo>,
    private val onClick: (Photo, Int) -> Unit,
    private val onDelete: (Photo) -> Unit
) : RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

    inner class PhotoViewHolder(val binding: ItemPhotoBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(photo: Photo) {
            Glide.with(context)
                .load(photo.photoUrl)
                .centerCrop()
                .into(binding.imageViewPhoto)

            binding.root.setOnClickListener {
                onClick(photo, adapterPosition)
            }

            binding.root.setOnLongClickListener {
                AlertDialog.Builder(context)
                    .setTitle("Usuń zdjęcie")
                    .setMessage("Czy na pewno chcesz usunąć to zdjęcie?")
                    .setPositiveButton("Usuń") { dialog, which ->
                        onDelete(photo)
                    }
                    .setNegativeButton("Anuluj", null)
                    .show()
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(photos[position])
    }

    override fun getItemCount(): Int = photos.size
}
