package com.example.cemo_loginpage

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView


@Suppress("DEPRECATION")
class RewardsPageCemo : AppCompatActivity() {

    private lateinit var navHome: LinearLayout
    private lateinit var navRewards: LinearLayout
    private lateinit var navSettings: LinearLayout
    private lateinit var iconHome: ImageView
    private lateinit var iconRewards: ImageView
    private lateinit var iconSettings: ImageView
    private lateinit var labelHome: TextView
    private lateinit var labelRewards: TextView
    private lateinit var labelSettings: TextView

    private lateinit var auth: com.google.firebase.auth.FirebaseAuth
    private lateinit var db: com.google.firebase.firestore.FirebaseFirestore

    private lateinit var recyclerView: RecyclerView
    private lateinit var rewardAdapter: RewardAdapter
    private lateinit var tvNoRewards: TextView
    private lateinit var tvTotalPoints: TextView

    private var rejectedListener: com.google.firebase.firestore.ListenerRegistration? = null

    private var approvedListener: com.google.firebase.firestore.ListenerRegistration? = null


    private var userDocListener: com.google.firebase.firestore.ListenerRegistration? = null


    private var rewardsListener: com.google.firebase.firestore.ListenerRegistration? = null


    override fun onResume() {
        super.onResume()
        if (auth.currentUser == null) return

        loadUserName()
        loadUserPoints()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.statusBarColor = "#FFFFFF".toColorInt()
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        setContentView(R.layout.cemo_rewards_page)

        auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        db   = com.google.firebase.firestore.FirebaseFirestore.getInstance()

        val rootView = findViewById<View?>(R.id.main)
        rootView?.let { view ->
            ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }
        }

        // Nav bar
        navHome      = findViewById(R.id.navHome)
        navRewards   = findViewById(R.id.navRewards)
        navSettings  = findViewById(R.id.navSettings)
        iconHome     = findViewById(R.id.iconHome)
        iconRewards  = findViewById(R.id.iconRewards)
        iconSettings = findViewById(R.id.iconSettings)
        labelHome    = findViewById(R.id.labelHome)
        labelRewards = findViewById(R.id.labelRewards)
        labelSettings = findViewById(R.id.labelSettings)
        tvNoRewards  = findViewById(R.id.tvNoRewards)
        tvTotalPoints = findViewById(R.id.tvTotalPoints)

        // RecyclerView setup
        recyclerView = findViewById(R.id.recyclerRewards)
        recyclerView.layoutManager = LinearLayoutManager(this)
        rewardAdapter = RewardAdapter(emptyList(), 0) { reward ->
            handleClaim(reward)
        }
        recyclerView.adapter = rewardAdapter

        setActiveTab(Tab.REWARDS)

        navHome.setOnClickListener {
            setActiveTab(Tab.HOME)
            startActivity(Intent(this, HomePageCemo::class.java))
            overridePendingTransition(0, 0)
            finish()
        }
        navRewards.setOnClickListener { setActiveTab(Tab.REWARDS) }
        navSettings.setOnClickListener {
            setActiveTab(Tab.SETTINGS)
            startActivity(Intent(this, SettingsPageCemo::class.java))
            overridePendingTransition(0, 0)
            finish()
        }

        loadUserName()
        loadUserPoints()
        loadRewards()
        listenForRejectedClaims()
        listenForApprovedClaims()

    }

    private fun loadUserName() {
        // name removed, kept for layout spacing
    }

    // ── Live-listen to user's current green points (auto-refreshes when admin approves) ──
    private fun loadUserPoints() {
        val uid = auth.currentUser?.uid ?: return
        userDocListener?.remove()
        userDocListener = db.collection("users").document(uid)
            .addSnapshotListener { document, error ->
                if (error != null || document == null || !document.exists()) return@addSnapshotListener

                val pts = when (val raw = document.get("greenPoints")) {
                    is Double -> raw.toLong()
                    is Long   -> raw
                    is Int    -> raw.toLong()
                    else      -> 0L
                }
                tvTotalPoints.text = "Total Points: $pts pts"
                // Refresh adapter with updated user points
                rewardAdapter.updateUserPoints(pts.toInt())

                // check show points pref (per-user)
                val prefs = getSharedPreferences("AppPrefs_$uid", MODE_PRIVATE)
                val showPoints = prefs.getBoolean("show_points_balance", true)
                tvTotalPoints.visibility = if (showPoints) View.VISIBLE else View.GONE
            }
    }

    // ── Load rewards from Firestore (same collection admin uses) ──
    private fun loadRewards() {

        if (auth.currentUser == null) return

        rewardsListener = db.collection("rewards")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Failed to load rewards: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    .format(java.util.Date())

                val rewards = snapshot?.documents?.mapNotNull { doc ->
                    val name    = doc.getString("name")    ?: return@mapNotNull null
                    val desc    = doc.getString("description") ?: ""
                    val points  = doc.getLong("points")?.toInt() ?: 0
                    val stock   = doc.getLong("stock")?.toInt()  ?: 0
                    val claimed = doc.getLong("claimed")?.toInt() ?: 0
                    val expiry  = doc.getString("expiry")  ?: ""
                    val docId   = doc.id

                    // Skip expired rewards
                    if (expiry.isNotEmpty() && expiry < today) return@mapNotNull null
                    if (stock <= 0) return@mapNotNull null

                    RewardItem(docId, name, desc, points, stock, claimed, expiry)
                } ?: emptyList()

                if (rewards.isEmpty()) {
                    tvNoRewards.visibility   = View.VISIBLE
                    recyclerView.visibility  = View.GONE
                } else {
                    tvNoRewards.visibility   = View.GONE
                    recyclerView.visibility  = View.VISIBLE
                    rewardAdapter.updateRewards(rewards)
                    // ── Notification logic ──
                    val uid = auth.currentUser?.uid ?: return@addSnapshotListener
                    val prefs = getSharedPreferences("NotifPrefs", MODE_PRIVATE)

                    rewards.forEach { reward ->
                        // New reward notification
                        val newKey = "new_reward_${uid}_${reward.docId}"
                        if (!prefs.getBoolean(newKey, false)) {
                            NotificationHelper.newRewardAvailable(this, uid, reward.name)
                            prefs.edit().putBoolean(newKey, true).apply()
                        }

                        // Expiring soon (3 days)
                        if (reward.expiry.isNotEmpty()) {
                            try {
                                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                val expiryDate = sdf.parse(reward.expiry) ?: return@forEach
                                val daysLeft = ((expiryDate.time - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt()
                                val expiryKey = "expiry_notif_${uid}_${reward.docId}"
                                if (daysLeft in 0..3 && !prefs.getBoolean(expiryKey, false)) {
                                    NotificationHelper.rewardExpiringSoon(this, uid, reward.name)
                                    prefs.edit().putBoolean(expiryKey, true).apply()
                                }
                            } catch (e: Exception) { /* ignore */ }
                        }
                    }
                }
            }
    }

    // ── Listen for rejected claims and notify the user ──
    // ── Listen for rejected claims and notify the user ──
    private fun listenForRejectedClaims() {
        val uid = auth.currentUser?.uid ?: return
        val prefs = getSharedPreferences("NotifPrefs", MODE_PRIVATE)

        rejectedListener?.remove()
        rejectedListener = db.collection("claimedRewards")
            .whereEqualTo("userId", uid)
            .whereEqualTo("status", "rejected")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                // NEW
                for (doc in snapshot.documents) {
                    val alreadyNotifiedRemote = doc.getBoolean("userNotified") ?: false
                    val key = "rejected_notif_${uid}_${doc.id}"
                    if (!alreadyNotifiedRemote && !prefs.getBoolean(key, false)) {
                        val rewardName = doc.getString("rewardName") ?: "reward"
                        val pointsRefunded = doc.getLong("pointsCost")?.toInt() ?: 0
                        val reason = doc.getString("rejectionReason") ?: "No reason provided."

                        Toast.makeText(
                            this,
                            "Claim for \"$rewardName\" was rejected: $reason",
                            Toast.LENGTH_LONG
                        ).show()
                        // Mark notified locally first — this stops the repeat
                        prefs.edit().putBoolean(key, true).apply()
                        // Best-effort remote flag too
                        doc.reference.update("userNotified", true)

                        // ── Log "Claim Rejected" notification ──
                        NotificationHelper.claimRejected(this, uid, rewardName, pointsRefunded, reason)
                    }
                }
            }
    }

    // ── Listen for approved (claimed) claims and notify the user ──
    private fun listenForApprovedClaims() {
        val uid = auth.currentUser?.uid ?: return
        val prefs = getSharedPreferences("NotifPrefs", MODE_PRIVATE)

        approvedListener?.remove()
        approvedListener = db.collection("claimedRewards")
            .whereEqualTo("userId", uid)
            .whereEqualTo("status", "claimed")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                for (doc in snapshot.documents) {
                    val alreadyNotifiedRemote = doc.getBoolean("userNotified") ?: false
                    val key = "approved_notif_${uid}_${doc.id}"
                    if (!alreadyNotifiedRemote && !prefs.getBoolean(key, false)) {
                        val rewardName = doc.getString("rewardName") ?: "reward"
                        Toast.makeText(
                            this,
                            "\"$rewardName\" claimed! 🎉",
                            Toast.LENGTH_LONG
                        ).show()
                        prefs.edit().putBoolean(key, true).apply()
                        doc.reference.update("userNotified", true)

                        // ── Log "Claim Approved" notification ──
                        NotificationHelper.claimApproved(this, uid, rewardName)

                        // Auto-refresh points + rewards list right away
                        loadUserPoints()
                    }
                }
            }
    }

    // ── Handle claim button tap ──
    private fun handleClaim(reward: RewardItem) {
        val uid = auth.currentUser?.uid ?: return

        // First check if user already has a pending claim for this reward
        db.collection("claimedRewards")
            .whereEqualTo("userId", uid)
            .whereEqualTo("rewardId", reward.docId)
            .whereEqualTo("status", "pending")
            .get()
            .addOnSuccessListener { pendingSnap ->
                if (!pendingSnap.isEmpty) {
                    // Already has a pending claim — show the existing code
                    val existingCode = pendingSnap.documents[0].getString("claimCode") ?: "—"
                    showClaimCodeDialog(existingCode, reward.name)
                    Toast.makeText(this, "You already have an active claim for \"${reward.name}\".", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                // No pending claim — proceed normally
                db.collection("users").document(uid).get()
                    .addOnSuccessListener { doc ->
                        val userPoints = when (val raw = doc.get("greenPoints")) {
                            is Double -> raw.toInt()
                            is Long   -> raw.toInt()
                            is Int    -> raw
                            else      -> 0
                        }
                        when {
                            reward.stock <= 0 -> {
                                Toast.makeText(this, "Sorry, \"${reward.name}\" is out of stock.", Toast.LENGTH_SHORT).show()
                            }
                            userPoints < reward.points -> {
                                val needed = reward.points - userPoints
                                Toast.makeText(this, "You need $needed more pts to claim \"${reward.name}\".", Toast.LENGTH_LONG).show()
                            }
                            else -> {
                                showClaimConfirmDialog(uid, reward, userPoints)
                            }
                        }
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error checking claims: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showClaimConfirmDialog(uid: String, reward: RewardItem, userPoints: Int) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Claim Reward")
            .setMessage("Claim \"${reward.name}\" for ${reward.points} pts?\n\nA claim code will be generated. Show it to the admin to receive your reward. Points will be deducted once the admin marks it as claimed.")
            .setPositiveButton("Claim") { _, _ ->
                generateClaimCode(uid, reward)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun generateClaimCode(uid: String, reward: RewardItem) {
        val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
        val data = hashMapOf("rewardId" to reward.docId)

        functions.getHttpsCallable("requestClaim")
            .call(data)
            .addOnSuccessListener { result ->
                @Suppress("UNCHECKED_CAST")
                val response = result.getData() as? Map<String, Any>
                val code = response?.get("code") as? String ?: "—"
                val rewardName = response?.get("rewardName") as? String ?: reward.name
                showClaimCodeDialog(code, rewardName)

                // ── Log "Claim Submitted" notification ──
                NotificationHelper.claimSubmitted(this, uid, rewardName)
            }
            .addOnFailureListener { e ->
                val msg = e.message ?: "Failed to claim."
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
    }

    private fun showClaimCodeDialog(code: String, rewardName: String) {
        val view = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
            gravity = android.view.Gravity.CENTER
        }

        val tvLabel = android.widget.TextView(this).apply {
            text = "Your Claim Code"
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.GRAY)
        }

        val tvCode = android.widget.TextView(this).apply {
            text = code
            textSize = 28f
            gravity = android.view.Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor("#1A1A1A".toColorInt())
            setPadding(0, 16, 0, 8)
        }

        val tvInstr = android.widget.TextView(this).apply {
            text = "Show this code to the admin to claim your \"$rewardName\"."
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.GRAY)
            setPadding(0, 8, 0, 0)
        }

        view.addView(tvLabel)
        view.addView(tvCode)
        view.addView(tvInstr)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .setPositiveButton("Done", null)
            .show()
    }

    override fun onStop() {
        super.onStop()
        rewardsListener?.remove()
        rewardsListener = null
        rejectedListener?.remove()
        rejectedListener = null
        approvedListener?.remove()
        approvedListener = null
        userDocListener?.remove()
        userDocListener = null
    }

    // ── Deduct points & decrement stock in Firestore ──
    private fun confirmClaim(uid: String, reward: RewardItem, currentPoints: Int) {
        val userRef   = db.collection("users").document(uid)
        val rewardRef = db.collection("rewards").document(reward.docId)

        db.runTransaction { tx ->
            val rewardSnap = tx.get(rewardRef)
            val latestStock   = rewardSnap.getLong("stock")?.toInt()   ?: 0
            val latestClaimed = rewardSnap.getLong("claimed")?.toInt() ?: 0

            if (latestStock <= 0) throw Exception("Out of stock")

            tx.update(userRef, "greenPoints", com.google.firebase.firestore.FieldValue.increment(-(reward.points.toDouble())))
            tx.update(rewardRef, "stock",   latestStock - 1)
            tx.update(rewardRef, "claimed", latestClaimed + 1)
        }.addOnSuccessListener {
            Toast.makeText(this, ". \"${reward.name}\" claimed! Enjoy your reward.", Toast.LENGTH_LONG).show()
            loadUserPoints() // Refresh displayed points
            NotificationHelper.rewardRedeemed(this, uid, reward.name)

        }.addOnFailureListener { e ->
            Toast.makeText(this, "Claim failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Nav tab helpers ──
    enum class Tab { HOME, REWARDS, SETTINGS }

    private fun setActiveTab(tab: Tab) {
        val activeColor   = Color.WHITE
        val inactiveColor = "#322d2d".toColorInt()

        listOf(navHome, navRewards, navSettings).forEach { it.setBackgroundColor(Color.TRANSPARENT) }
        listOf(labelHome, labelRewards, labelSettings).forEach { it.setTextColor(inactiveColor) }
        listOf(iconHome, iconRewards, iconSettings).forEach { it.setColorFilter(inactiveColor) }

        val (activeNav, activeLabel, activeIcon) = when (tab) {
            Tab.HOME     -> Triple(navHome,     labelHome,     iconHome)
            Tab.REWARDS  -> Triple(navRewards,  labelRewards,  iconRewards)
            Tab.SETTINGS -> Triple(navSettings, labelSettings, iconSettings)
        }
        activeNav.setBackgroundResource(R.drawable.nav_active_bg)
        activeLabel.setTextColor(activeColor)
        activeIcon.setColorFilter(activeColor)
    }
}