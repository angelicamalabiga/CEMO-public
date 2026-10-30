package com.example.cemo_loginpage

import android.content.Context
import android.widget.Toast
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

object ClaimNotifier {

    private var listeningUid: String? = null
    private var rejectedReg: ListenerRegistration? = null
    private var approvedReg: ListenerRegistration? = null

    fun start(context: Context, uid: String) {
        if (listeningUid == uid) return   // already listening, wag mag-double
        stop()
        listeningUid = uid

        val app = context.applicationContext
        val db = FirebaseFirestore.getInstance()
        val prefs = app.getSharedPreferences("NotifPrefs", Context.MODE_PRIVATE)

        rejectedReg = db.collection("claimedRewards")
            .whereEqualTo("userId", uid)
            .whereEqualTo("status", "rejected")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                for (doc in snapshot.documents) {
                    val key = "rejected_notif_${uid}_${doc.id}"
                    val notifiedRemote = doc.getBoolean("userNotified") ?: false
                    if (notifiedRemote || prefs.getBoolean(key, false)) continue

                    val rewardName = doc.getString("rewardName") ?: "reward"
                    val refunded = doc.getLong("pointsCost")?.toInt() ?: 0
                    val reason = doc.getString("rejectionReason") ?: "No reason provided."

                    prefs.edit().putBoolean(key, true).apply()
                    doc.reference.update("userNotified", true)
                    Toast.makeText(app, "Claim for \"$rewardName\" was rejected: $reason", Toast.LENGTH_LONG).show()
                    NotificationHelper.claimRejected(app, uid, rewardName, refunded, reason)
                }
            }

        approvedReg = db.collection("claimedRewards")
            .whereEqualTo("userId", uid)
            .whereEqualTo("status", "claimed")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                for (doc in snapshot.documents) {
                    val key = "approved_notif_${uid}_${doc.id}"
                    val notifiedRemote = doc.getBoolean("userNotified") ?: false
                    if (notifiedRemote || prefs.getBoolean(key, false)) continue

                    val rewardName = doc.getString("rewardName") ?: "reward"

                    prefs.edit().putBoolean(key, true).apply()
                    doc.reference.update("userNotified", true)
                    Toast.makeText(app, "\"$rewardName\" claimed! 🎉", Toast.LENGTH_LONG).show()
                    NotificationHelper.claimApproved(app, uid, rewardName)
                }
            }
    }

    fun stop() {
        rejectedReg?.remove(); rejectedReg = null
        approvedReg?.remove(); approvedReg = null
        listeningUid = null
    }
}