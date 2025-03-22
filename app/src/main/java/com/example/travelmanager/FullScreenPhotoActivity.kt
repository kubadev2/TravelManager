package com.example.travelmanager

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.example.travelmanager.databinding.ActivityFullScreenPhotoBinding

import java.io.File

class FullScreenPhotosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFullScreenPhotoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFullScreenPhotoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Pobierz listę zdjęć i indeks początkowy
        val photoList = intent.getStringArrayListExtra("photoList")
        val startIndex = intent.getIntExtra("startIndex", 0)

        if (photoList != null && photoList.isNotEmpty()) {
            val adapter = FullScreenPhotoAdapter(this, photoList)
            binding.viewPagerPhotos.adapter = adapter
            binding.viewPagerPhotos.setCurrentItem(startIndex, false)
        }
    }
}
