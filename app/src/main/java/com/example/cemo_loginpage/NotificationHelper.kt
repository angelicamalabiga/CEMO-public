package com.example.cemo_loginpage

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.FirebaseFirestore

object NotificationHelper {

    private val db = FirebaseFirestore.getInstance()
    private const val CHANNEL_ID = "cemo_channel"

    // ── Check if notifications are enabled ──
    private fun isNotifEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("enable_notifications", true)
    }

    // ── Create Notification Channel (required for Android 8+) ──
    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "CEMO Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "CEMO app notifications"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    // ── Show actual pop-up notification on phone ──
    private fun showLocalNotif(context: Context, title: String, message: String) {
        createChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logonotif)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notif)
    }


    fun push(context: Context, uid: String, title: String, message: String, type: String) {

        // 1. ALWAYS save to Firestore (in-app notif list) — kahit naka-OFF ang toggle
        val data = hashMapOf(
            "title"     to title,
            "message"   to message,
            "type"      to type,
            "timestamp" to System.currentTimeMillis(),
            "isRead"    to false
        )
        db.collection("users").document(uid)
            .collection("notifications")
            .add(data)

        // 2. Pop-up ONLY kung naka-ON ang Enable Notifications
        if (isNotifEnabled(context)) {
            showLocalNotif(context, title, message)
        }
    }

    // ── Welcome Message (new account) ──
    fun welcomeNewUser(context: Context, uid: String) = push(
        context, uid,
        "Welcome to CEMO! 🌱",
        "Thanks for signing up! Start scanning your recyclables to earn Green Points.",
        "welcome"
    )

    // ── Reward Claimed ──
    fun rewardRedeemed(context: Context, uid: String, rewardName: String) = push(
        context, uid,
        "Reward Claimed! 🎉",
        "You successfully claimed \"$rewardName\". Enjoy your reward!",
        "reward_redeemed"
    )

    // ── Reward Expiring Soon ──
    fun rewardExpiringSoon(context: Context, uid: String, rewardName: String) = push(
        context, uid,
        "Reward Expiring Soon ⏰",
        "\"$rewardName\" is expiring in 3 days. Redeem it before it's gone!",
        "reward_expiring"
    )

    // ── Points Expiring Soon (personal balance) ──
    fun pointsExpiringSoon(context: Context, uid: String, points: Double, daysLeft: Int) = push(
        context, uid,
        "Points Expiring Soon ⏰",
        "${String.format("%.2f", points)} pts will expire in $daysLeft day${if (daysLeft == 1) "" else "s"}. Recycle more or redeem a reward before they're gone!",
        "points_expiring"
    )

    // ── New Reward Available ──
    fun newRewardAvailable(context: Context, uid: String, rewardName: String) = push(
        context, uid,
        "New Reward Available",
        "A new reward \"$rewardName\" is now available. Check it out!",
        "new_reward"
    )

    // ── Milestone Reached ──
    fun checkMilestone(context: Context, uid: String, newPoints: Int) {
        val milestones = listOf(100, 500, 1000, 2000, 5000)
        if (newPoints in milestones) {
            push(
                context, uid,
                "Milestone Reached!",
                "Congratulations! You've reached $newPoints Green Points. Keep recycling!",
                "milestone"
            )
        }
    }

    // ── Claim Submitted ──
    fun claimSubmitted(context: Context, uid: String, rewardName: String) = push(
        context, uid,
        "Claim Submitted",
        "Your claim for \"$rewardName\" has been submitted and is pending admin approval.",
        "claim_submitted"
    )

    // ── Claim Approved ──
    fun claimApproved(context: Context, uid: String, rewardName: String) = push(
        context, uid,
        "Reward Claimed! 🎉",
        "Your claim for \"$rewardName\" has been approved. Enjoy your reward!",
        "claim_approved"
    )

    // ── Claim Rejected (with refund) ──
    fun claimRejected(context: Context, uid: String, rewardName: String, pointsRefunded: Int, reason: String) = push(
        context, uid,
        "Claim Rejected",
        "Your claim for \"$rewardName\" was rejected: $reason. $pointsRefunded pts have been refunded to your balance.",
        "claim_rejected"
    )

    // ── Recycling Recorded ──
    fun recyclingRecorded(context: Context, uid: String, points: Double, weightG: Double) = push(
        context, uid,
        "Recycling Recorded ♻️",
        "Your recycling transaction was added. You earned ${String.format("%.2f", points)} pts for ${String.format("%.1f", weightG)}g recycled.",
        "recycling_recorded"
    )

    // ── ID Approved ──
    fun idVerificationApproved(context: Context, uid: String) = push(
        context, uid,
        "ID Verified",
        "Your ID has been verified successfully! You now have full access.",
        "id_approved"
    )

    // ── ID Rejected ──
    fun idVerificationRejected(context: Context, uid: String, reason: String) = push(
        context, uid,
        "ID Verification Failed",
        "Your ID verification was rejected: $reason. Please re-submit with a clear, valid ID.",
        "id_rejected"
    )
}