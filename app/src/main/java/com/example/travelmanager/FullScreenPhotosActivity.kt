package com.example.travelmanager

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import androidx.viewpager2.widget.ViewPager2
import com.example.travelmanager.databinding.ActivityFullScreenPhotoBinding

@UnstableApi
class FullScreenPhotosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFullScreenPhotoBinding
    private lateinit var adapter: FullScreenMediaAdapter
    private var currentPage = 0

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFullScreenPhotoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mediaList = intent.getStringArrayListExtra("photoList") ?: arrayListOf()
        val startIndex = intent.getIntExtra("startIndex", 0)

        adapter = FullScreenMediaAdapter(mediaList)
        binding.viewPagerPhotos.adapter = adapter
        binding.viewPagerPhotos.setCurrentItem(startIndex, false)

        var currentPosition = 0

        binding.viewPagerPhotos.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position != currentPosition) {
                    adapter.stopPlayerAt(currentPosition)
                    adapter.notifyItemChanged(currentPosition)
                    currentPosition = position
                }

                // Włącz odtwarzanie tylko dla nowej pozycji
                adapter.playPlayerAt(position)
            }
        })

    }

    @OptIn(UnstableApi::class)
    override fun onStop() {
        super.onStop()
        adapter.releaseAllPlayers()
    }

    @OptIn(UnstableApi::class)
    override fun onPause() {
        super.onPause()
        adapter.releaseAllPlayers()
    }

    override fun onResume() {
        super.onResume()
        binding.viewPagerPhotos.adapter?.notifyDataSetChanged()
    }
}
