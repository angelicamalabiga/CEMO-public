package com.example.cemo_loginpage

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class NotificationPageCemo : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var notifAdapter: NotificationAdapter
    private var notifListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.statusBarColor = "#FFFFFF".toColorInt()
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        setContentView(R.layout.cemo_notification_page)

        auth = FirebaseAuth.getInstance()


        db   = FirebaseFirestore.getInstance()

        tvEmpty      = findViewById(R.id.tvNoNotifications)
        recyclerView = findViewById(R.id.recyclerNotifications)
        recyclerView.layoutManager = LinearLayoutManager(this)

        notifAdapter = NotificationAdapter(
            mutableListOf(),
            onDelete = { notif -> deleteNotification(notif) },
            onMarkRead = { notif -> markOneAsRead(notif) }
        )
        recyclerView.adapter = notifAdapter

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
            overridePendingTransition(0, 0)
        }

        findViewById<TextView>(R.id.tvMarkAllRead).setOnClickListener {
            markAllAsRead()
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }


    }

    override fun onResume() {
        super.onResume()
        // Re-attach listener every time page becomes visible
        notifListener?.remove()
        listenToNotifications()

    }

    // ── Real-time listener for notifications ──────────────────────────
    private fun listenToNotifications() {
        val uid = auth.currentUser?.uid ?: return

        notifListener = db.collection("users").document(uid)
            .collection("notifications")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                val list = snapshot.documents.mapNotNull { doc ->
                    val title     = doc.getString("title")     ?: return@mapNotNull null
                    val message   = doc.getString("message")   ?: ""
                    val type      = doc.getString("type")      ?: "general"
                    val timestamp = doc.getLong("timestamp")   ?: 0L
                    val isRead    = doc.getBoolean("isRead")   ?: false
                    val docId     = doc.id
                    NotificationItem(docId, title, message, type, timestamp, isRead)
                }.toMutableList()

                notifAdapter.updateList(list)

                if (list.isEmpty()) {
                    tvEmpty.visibility      = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    tvEmpty.visibility      = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }
            }
    }

    private fun markOneAsRead(notif: NotificationItem) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid)
            .collection("notifications")
            .document(notif.docId)
            .update("isRead", true)
    }

    // ── Mark all as read when user opens this page ────────────────────
    private fun markAllAsRead() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid)
            .collection("notifications")
            .whereEqualTo("isRead", false)
            .get()
            .addOnSuccessListener { snapshot ->
                val batch = db.batch()
                snapshot.documents.forEach { doc ->
                    batch.update(doc.reference, "isRead", true)
                }
                batch.commit()
            }
    }

    // ── Delete a single notification ──────────────────────────────────
    private fun deleteNotification(notif: NotificationItem) {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid)
            .collection("notifications")
            .document(notif.docId)
            .delete()
    }

    override fun onStop() {
        super.onStop()
        notifListener?.remove()
        notifListener = null
    }
}

// ── Data class ────────────────────────────────────────────────────────
data class NotificationItem(
    val docId: String,
    val title: String,
    val message: String,
    val type: String,
    val timestamp: Long,
    val isRead: Boolean
)