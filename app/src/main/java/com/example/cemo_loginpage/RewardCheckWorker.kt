package com.example.cemo_loginpage

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class RewardCheckWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        val uid = auth.currentUser?.uid ?: return Result.success()
        val prefs = context.getSharedPreferences("NotifPrefs", Context.MODE_PRIVATE)

        try {
            val snapshot = db.collection("rewards").get().await()

            snapshot.documents.forEach { doc ->
                val name   = doc.getString("name") ?: return@forEach
                val docId  = doc.id
                val expiry = doc.getString("expiry") ?: ""

                // New reward notification
                val newKey = "new_reward_${uid}_$docId"
                if (!prefs.getBoolean(newKey, false)) {
                    NotificationHelper.newRewardAvailable(context, uid, name)
                    prefs.edit().putBoolean(newKey, true).apply()
                }

                // Expiring soon (3 days)
                if (expiry.isNotEmpty()) {
                    try {
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        val expiryDate = sdf.parse(expiry) ?: return@forEach
                        val daysLeft = ((expiryDate.time - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt()
                        val expiryKey = "expiry_notif_${uid}_$docId"
                        if (daysLeft in 0..3 && !prefs.getBoolean(expiryKey, false)) {
                            NotificationHelper.rewardExpiringSoon(context, uid, name)
                            prefs.edit().putBoolean(expiryKey, true).apply()
                        }
                    } catch (e: Exception) { /* ignore */ }
                }
            }
        } catch (e: Exception) {
            return Result.retry()
        }

        return Result.success()
    }
}