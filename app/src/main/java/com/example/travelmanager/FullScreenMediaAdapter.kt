package com.example.travelmanager

import android.net.Uri
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.core.util.set
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.travelmanager.databinding.ItemFullScreenMediaBinding

@UnstableApi
@OptIn(UnstableApi::class) // ✅ To obejmuje całą klasę
class FullScreenMediaAdapter(
    private val mediaList: List<String>
) : RecyclerView.Adapter<FullScreenMediaAdapter.MediaViewHolder>() {

    private val players = SparseArray<ExoPlayer?>()

    inner class MediaViewHolder(val binding: ItemFullScreenMediaBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding = ItemFullScreenMediaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MediaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        val uriStr = mediaList[position]
        val uri = Uri.parse(uriStr)
        val context = holder.itemView.context
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri)

        if (mimeType == null) {
            Toast.makeText(context, "Nie można odczytać typu pliku", Toast.LENGTH_SHORT).show()
            return
        }

        if (mimeType.startsWith("video")) {
            holder.binding.imageView.visibility = ImageView.GONE
            holder.binding.playerView.visibility = PlayerView.VISIBLE
            holder.binding.playerView.useController = false

            val player = ExoPlayer.Builder(context).build().also {
                holder.binding.playerView.player = it
                it.setMediaItem(MediaItem.fromUri(uri))
                it.prepare()
                it.playWhenReady = false
            }

            holder.binding.playerView.setOnClickListener {
                holder.binding.playerView.useController = true
                holder.binding.playerView.showController()
            }

            players[position] = player
        } else {
            holder.binding.playerView.visibility = PlayerView.GONE
            holder.binding.imageView.visibility = ImageView.VISIBLE

            Glide.with(context)
                .load(uri)
                .into(holder.binding.imageView)

            players[position] = null
        }
    }

    override fun getItemCount(): Int = mediaList.size

    override fun onViewRecycled(holder: MediaViewHolder) {
        super.onViewRecycled(holder)
        val player = holder.binding.playerView.player
        player?.release()
        holder.binding.playerView.player = null
    }

    fun releasePlayerAt(position: Int) {
        players[position]?.release()
        players.remove(position)
    }

    fun releaseAllPlayers() {
        for (i in 0 until players.size()) {
            players.valueAt(i)?.release()
        }
        players.clear()
    }
    fun stopPlayerAt(position: Int) {
        players[position]?.let { player ->
            player.stop()
            player.release()
            players.remove(position)
        }
    }
    fun playPlayerAt(position: Int) {
        players[position]?.playWhenReady = true
    }



}
