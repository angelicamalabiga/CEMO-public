package com.example.cemo_loginpage

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*
import android.widget.LinearLayout


class NotificationAdapter(
    private val items: MutableList<NotificationItem>,
    private val onDelete: (NotificationItem) -> Unit,
    private val onMarkRead: (NotificationItem) -> Unit

) : RecyclerView.Adapter<NotificationAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle:   TextView = view.findViewById(R.id.tvNotifTitle)
        val tvMessage: TextView = view.findViewById(R.id.tvNotifMessage)
        val tvTime:    TextView = view.findViewById(R.id.tvNotifTime)
        val tvDots:    TextView = view.findViewById(R.id.tvNotifDots)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val notif = items[position]

        holder.tvTitle.text   = notif.title
        holder.tvMessage.text = notif.message

        val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        holder.tvTime.text = sdf.format(Date(notif.timestamp))

        val rowLayout = holder.itemView.findViewById<LinearLayout>(R.id.notifRow)
        if (!notif.isRead) {
            rowLayout.setBackgroundColor("#F0F7FF".toColorInt())
        } else {
            rowLayout.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        holder.tvDots.setOnClickListener { view ->
            val popup = android.widget.PopupMenu(view.context, view)
            if (!notif.isRead) popup.menu.add("Mark as Read")
            popup.menu.add("Delete")
            popup.setOnMenuItemClickListener { item ->
                when (item.title) {
                    "Mark as Read" -> onMarkRead(notif)
                    "Delete"       -> onDelete(notif)
                }
                true
            }
            popup.show()
        }
    }

    fun updateList(newList: MutableList<NotificationItem>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }
}