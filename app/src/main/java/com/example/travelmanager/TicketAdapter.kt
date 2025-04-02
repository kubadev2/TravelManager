package com.example.travelmanager

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.travelmanager.databinding.ItemTicketBinding

class TicketAdapter(
    private val context: Context,
    private var tickets: List<Ticket>,
    private val onClick: (String) -> Unit,
    private val onDelete: (Ticket) -> Unit
) : RecyclerView.Adapter<TicketAdapter.TicketViewHolder>() {

    inner class TicketViewHolder(private val binding: ItemTicketBinding) :
        RecyclerView.ViewHolder(binding.root) {

        @OptIn(UnstableApi::class)
        fun bind(ticket: Ticket) {
            val uri = Uri.parse(ticket.fileUrl)
            val mimeType = try {
                context.contentResolver.getType(uri) ?: ""
            } catch (e: SecurityException) {
                e.printStackTrace()
                ""
            }

            // Ustawienie miniatury
            if (mimeType.startsWith("image")) {
                Glide.with(context)
                    .load(uri)
                    .centerCrop()
                    .into(binding.ticketImageView)
            } else if (mimeType == "application/pdf") {
                try {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { parcelFileDescriptor ->
                        PdfRenderer(parcelFileDescriptor).use { pdfRenderer ->
                            if (pdfRenderer.pageCount > 0) {
                                pdfRenderer.openPage(0).use { page ->
                                    val width = page.width
                                    val height = page.height
                                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    binding.ticketImageView.setImageBitmap(bitmap)
                                }
                            } else {
                                binding.ticketImageView.setImageResource(R.drawable.ic_pdf)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    binding.ticketImageView.setImageResource(R.drawable.ic_pdf)
                }
            } else {
                binding.ticketImageView.setImageResource(R.drawable.ic_pdf)
            }

            // Obsługa kliknięcia
            binding.root.setOnClickListener {
                try {
                    if (mimeType.startsWith("image")) {
                        val intent = Intent(context, FullScreenPhotosActivity::class.java).apply {
                            putStringArrayListExtra("photoList", arrayListOf(ticket.fileUrl))
                            putExtra("startIndex", 0)
                        }
                        context.startActivity(intent)
                    } else {
                        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/pdf")
                            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        }
                        context.startActivity(viewIntent)
                    }
                } catch (e: SecurityException) {
                    e.printStackTrace()
                    AlertDialog.Builder(context)
                        .setTitle("Brak dostępu")
                        .setMessage("Nie masz już dostępu do tego pliku. Wybierz go ponownie.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }

            // Obsługa długiego kliknięcia (usunięcie biletu)
            binding.root.setOnLongClickListener {
                AlertDialog.Builder(context)
                    .setTitle("Usuń bilet")
                    .setMessage("Czy na pewno chcesz usunąć ten bilet?")
                    .setPositiveButton("Usuń") { _, _ -> onDelete(ticket) }
                    .setNegativeButton("Anuluj", null)
                    .show()
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TicketViewHolder {
        val binding = ItemTicketBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TicketViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TicketViewHolder, position: Int) {
        holder.bind(tickets[position])
    }

    override fun getItemCount(): Int = tickets.size
}

