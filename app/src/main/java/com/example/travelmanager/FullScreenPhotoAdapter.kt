package com.example.travelmanager

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.travelmanager.databinding.ItemFullScreenPhotoBinding
import java.io.File

class FullScreenPhotoAdapter(
    private val context: Context,
    private val photoList: List<String>
) : RecyclerView.Adapter<FullScreenPhotoAdapter.PhotoViewHolder>() {

    inner class PhotoViewHolder(val binding: ItemFullScreenPhotoBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(photoPath: String) {
            // Jeśli ścieżka zaczyna się od '/', traktujemy ją jako lokalną
            val finalUri = if (photoPath.startsWith("/")) {
                Uri.fromFile(File(photoPath))
            } else {
                Uri.parse(photoPath)
            }
            Glide.with(context)
                .load(finalUri)
                .into(binding.fullScreenImageView)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemFullScreenPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(photoList[position])
    }

    override fun getItemCount(): Int = photoList.size
}
