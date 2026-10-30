package com.example.cemo_loginpage

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.graphics.toColorInt
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.os.Build
import android.view.View
import com.google.firebase.firestore.ListenerRegistration
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit


@Suppress("DEPRECATION")
class HomePageCemo : AppCompatActivity() {

    private lateinit var navHome: LinearLayout
    private lateinit var navRewards: LinearLayout
    private lateinit var navSettings: LinearLayout
    private lateinit var iconHome: ImageView
    private lateinit var iconRewards: ImageView
    private lateinit var iconSettings: ImageView
    private lateinit var labelHome: TextView
    private lateinit var labelRewards: TextView
    private lateinit var labelSettings: TextView

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var unreadListener: ListenerRegistration? = null

    private var pointsListener: ListenerRegistration? = null
    private var recordsListener: ListenerRegistration? = null

    private var isExpiryCheckRunning = false


    override fun onResume() {
        super.onResume()
        loadUserData()
        unreadListener?.remove()
        listenUnreadCount()
        pointsListener?.remove()
        listenPoints()
        checkNewRewardNotifications()
        auth.currentUser?.uid?.let {
            checkAndExpirePoints(it)
            checkPointsExpiringSoon(it)
            ClaimNotifier.start(this, it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.statusBarColor = "#243E6D".toColorInt()
        window.decorView.systemUiVisibility = 0

        setContentView(R.layout.cemo_home_page)

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "reward_check",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RewardCheckWorker>(
                15, TimeUnit.MINUTES
            ).build()
        )

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db   = FirebaseFirestore.getInstance()

        // Fetch and display user data
        loadUserData()
        listenUnreadCount()
        listenPoints()




        // Notification Icon
        val notificationIcon = findViewById<ImageView>(R.id.Notification)
        notificationIcon.setOnClickListener {
            startActivity(Intent(this, NotificationPageCemo::class.java))
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // View Full History → separate page
        findViewById<TextView>(R.id.tvViewFullHistory).setOnClickListener {
            startActivity(Intent(this, TransactionHistoryActivity::class.java))
        }



        // QR Scan Button
        val btnScanQr = findViewById<LinearLayout>(R.id.btnScanQr)
        btnScanQr.setOnClickListener {
            checkVerificationBeforeScan()
        }



        // Bottom Navigation
        navHome     = findViewById(R.id.navHome)
        navRewards  = findViewById(R.id.navRewards)
        navSettings = findViewById(R.id.navSettings)
        iconHome     = findViewById(R.id.iconHome)
        iconRewards  = findViewById(R.id.iconRewards)
        iconSettings = findViewById(R.id.iconSettings)
        labelHome     = findViewById(R.id.labelHome)
        labelRewards  = findViewById(R.id.labelRewards)
        labelSettings = findViewById(R.id.labelSettings)

        setActiveTab(Tab.HOME)

        navHome.setOnClickListener { setActiveTab(Tab.HOME) }
        navRewards.setOnClickListener {
            setActiveTab(Tab.REWARDS)
            startActivity(Intent(this, RewardsPageCemo::class.java))
            overridePendingTransition(0, 0)
            finish()
        }
        navSettings.setOnClickListener {
            setActiveTab(Tab.SETTINGS)
            startActivity(Intent(this, SettingsPageCemo::class.java))
            overridePendingTransition(0, 0)
            finish()
        }
    }

    private fun loadUserData() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {

                    // ── Name + Barangay ──
                    val name     = document.getString("name") ?: "User"
                    val barangay = document.getString("barangay") ?: ""
                    val firstName = name.split(" ").firstOrNull() ?: name

                    findViewById<TextView>(R.id.tvUserGreeting)?.text = "Hello, $firstName! 👋"
                    findViewById<TextView>(R.id.tvMemberId)?.text = "Barangay $barangay"

                    // ── Profile Photo ──
                    val photoUrl = document.getString("photoUrl")
                    val ivProfilePhoto = findViewById<ImageView>(R.id.profile_image)
                    if (!photoUrl.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(photoUrl)
                            .circleCrop()
                            .placeholder(R.drawable.prof_icon)
                            .error(R.drawable.prof_icon)
                            .into(ivProfilePhoto)
                    } else {
                        ivProfilePhoto.setImageResource(R.drawable.prof_icon)
                    }

                    // ── Green Points (handled by live listener, see listenPoints()) ──


// ── Points Expiry (shows the EARLIEST upcoming expiry among non-expired batches) ──
                    val tvExpiry = findViewById<TextView>(R.id.tvPointsExpiry)
                    db.collection("settings").document("pointsExpiry").get()
                        .addOnSuccessListener { settingsDoc ->
                            val mode   = settingsDoc.getString("mode") ?: "never"
                            val months = settingsDoc.getLong("months")?.toInt() ?: 12

                            if (mode != "after") {
                                tvExpiry?.visibility = android.view.View.GONE
                                return@addOnSuccessListener
                            }

                            // Don't show expiry if the user currently has 0 (or no) points
                            val currentPoints = document.getDouble("greenPoints") ?: 0.0
                            if (currentPoints <= 0.0) {
                                tvExpiry?.visibility = android.view.View.GONE
                                return@addOnSuccessListener
                            }
                            db.collection("users").document(uid)
                                .collection("recyclingRecords")
                                .get()
                                .addOnSuccessListener { snap ->
                                    var earliestExpiry: java.util.Date? = null

                                    snap.documents.forEach { doc ->
                                        if (doc.getString("trash_type") == "reward_claim") return@forEach
                                        val alreadyExpired = doc.getBoolean("pointsExpired") ?: false
                                        if (alreadyExpired) return@forEach

                                        val originalPoints = doc.getDouble("points") ?: 0.0
                                        val remaining = if (doc.contains("remainingPoints")) {
                                            doc.getDouble("remainingPoints") ?: 0.0
                                        } else {
                                            originalPoints
                                        }
                                        if (remaining <= 0.0) return@forEach

                                        val scannedAt = doc.getTimestamp(
                                            "scanned_at",
                                            com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                                        )?.toDate() ?: return@forEach

                                        val cal = java.util.Calendar.getInstance()
                                        cal.time = scannedAt
                                        cal.add(java.util.Calendar.MONTH, months)
                                        val batchExpiry = cal.time

                                        if (earliestExpiry == null || batchExpiry.before(earliestExpiry)) {
                                            earliestExpiry = batchExpiry
                                        }
                                    }

                                    if (earliestExpiry != null) {
                                        val sdf = java.text.SimpleDateFormat("MM.dd.yy", java.util.Locale.getDefault())
                                        tvExpiry?.text = "Expires ${sdf.format(earliestExpiry)}"
                                        tvExpiry?.visibility = android.view.View.VISIBLE
                                    } else {
                                        tvExpiry?.visibility = android.view.View.GONE
                                    }
                                }
                        }

            // ── Load Recycling Records ──
                    loadRecyclingRecords(uid)
                }
            }
            .addOnFailureListener {
                // silent fail
            }
    }


    // ── Live-listen to greenPoints so Home auto-refreshes when admin approves a claim ──
    private fun listenPoints() {
        val uid = auth.currentUser?.uid ?: return
        pointsListener?.remove()
        pointsListener = db.collection("users").document(uid)
            .addSnapshotListener { document, error ->
                if (error != null || document == null || !document.exists()) return@addSnapshotListener
                val totalPoints = document.getDouble("greenPoints") ?: 0.0
                findViewById<TextView>(R.id.total_points)?.text =
                    "Total Green Points: ${String.format("%.2f", totalPoints)} pts"
            }
    }
    private fun checkAndExpirePoints(uid: String) {
        if (isExpiryCheckRunning) return   // ── prevent overlapping runs (Sanhi A) ──
        isExpiryCheckRunning = true

        db.collection("settings").document("pointsExpiry").get()
            .addOnSuccessListener { settingsDoc ->
                val mode = settingsDoc.getString("mode") ?: "never"
                if (mode != "after") {
                    isExpiryCheckRunning = false
                    return@addOnSuccessListener
                }
                val months = settingsDoc.getLong("months")?.toInt() ?: 12

                db.collection("users").document(uid)
                    .collection("recyclingRecords")
                    .get()
                    .addOnSuccessListener { records ->
                        val now = java.util.Calendar.getInstance().time
                        val userRef = db.collection("users").document(uid)

                        // ── Figure out which docs LOOK expired based on this read ──
                        val candidateExpiredRefs = mutableListOf<com.google.firebase.firestore.DocumentReference>()
                        val candidateBackfillRefs = mutableListOf<com.google.firebase.firestore.DocumentReference>()

                        records.documents.forEach { doc ->
                            if (doc.getString("trash_type") == "reward_claim") return@forEach
                            val alreadyExpired = doc.getBoolean("pointsExpired") ?: false
                            if (alreadyExpired) return@forEach

                            val scannedAt = doc.getTimestamp(
                                "scanned_at",
                                com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                            )?.toDate() ?: return@forEach

                            val cal = java.util.Calendar.getInstance()
                            cal.time = scannedAt
                            cal.add(java.util.Calendar.MONTH, months)

                            if (cal.time.before(now)) {
                                candidateExpiredRefs.add(doc.reference)
                            } else if (!doc.contains("remainingPoints") || !doc.contains("pointsExpired")) {
                                candidateBackfillRefs.add(doc.reference)
                            }
                        }

                        if (candidateExpiredRefs.isEmpty() && candidateBackfillRefs.isEmpty()) {
                            isExpiryCheckRunning = false
                            return@addOnSuccessListener
                        }

                        // ── STEP 2: TRANSACTION — ALL reads first, THEN all writes (Firestore requires this order) ──
                        db.runTransaction { tx ->
                            // ── READ PHASE — walang write dito, lahat ng tx.get() muna ──
                            val expiredSnaps  = candidateExpiredRefs.map { ref -> ref to tx.get(ref) }
                            val backfillSnaps = candidateBackfillRefs.map { ref -> ref to tx.get(ref) }
                            val userSnap = tx.get(userRef)

                            // ── COMPUTE PHASE — walang Firestore call, plain logic lang ──
                            var totalExpiredPoints = 0.0
                            val expiredUpdates  = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any>>>()
                            val backfillUpdates = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any>>>()

                            for ((ref, snap) in expiredSnaps) {
                                val stillNotExpired = snap.getBoolean("pointsExpired") != true
                                if (!stillNotExpired) continue  // someone else already expired it — skip

                                val originalPoints = snap.getDouble("points") ?: 0.0
                                val remaining = if (snap.contains("remainingPoints")) {
                                    snap.getDouble("remainingPoints") ?: 0.0
                                } else originalPoints

                                if (remaining > 0.0) totalExpiredPoints += remaining

                                expiredUpdates.add(ref to mapOf(
                                    "pointsExpired"   to true,
                                    "remainingPoints" to 0.0
                                ))
                            }

                            for ((ref, snap) in backfillSnaps) {
                                if (snap.getBoolean("pointsExpired") == true) continue // already handled
                                val originalPoints = snap.getDouble("points") ?: 0.0
                                val remaining = if (snap.contains("remainingPoints")) {
                                    snap.getDouble("remainingPoints") ?: 0.0
                                } else originalPoints
                                backfillUpdates.add(ref to mapOf(
                                    "pointsExpired"   to false,
                                    "remainingPoints" to remaining
                                ))
                            }

                            // ── WRITE PHASE — lahat ng writes dito, wala nang read pagkatapos nito ──
                            for ((ref, update) in expiredUpdates) tx.update(ref, update)
                            for ((ref, update) in backfillUpdates) tx.update(ref, update)

                            if (totalExpiredPoints > 0.0) {
                                val currentPoints = when (val raw = userSnap.get("greenPoints")) {
                                    is Double -> raw
                                    is Long   -> raw.toDouble()
                                    is Int    -> raw.toDouble()
                                    else      -> 0.0
                                }
                                val newPoints = (currentPoints - totalExpiredPoints).coerceAtLeast(0.0)
                                tx.update(userRef, "greenPoints", newPoints)

                                val expiryRecordRef = userRef.collection("recyclingRecords").document()
                                tx.set(expiryRecordRef, hashMapOf(
                                    "trash_type"      to "points_expired",
                                    "weight_g"        to 0.0,
                                    "points"          to -totalExpiredPoints,
                                    "remainingPoints" to 0.0,
                                    "pointsExpired"   to false,
                                    "note"            to "Points expired",
                                    "scanned_at"      to com.google.firebase.firestore.FieldValue.serverTimestamp()
                                ))

                                val notifRef = db.collection("users").document(uid)
                                    .collection("notifications").document()
                                tx.set(notifRef, hashMapOf(
                                    "title"     to "Points Expired",
                                    "message"   to "${String.format("%.2f", totalExpiredPoints)} pts have expired and were removed from your balance.",
                                    "type"      to "points_expired",
                                    "timestamp" to System.currentTimeMillis(),
                                    "isRead"    to false
                                ))
                            }

                            null
                        }.addOnCompleteListener {
                            isExpiryCheckRunning = false
                        }.addOnFailureListener { e ->
                            android.util.Log.e("CEMO_Expiry", "Transaction failed: ${e.message}", e)
                        }
                    }
                    .addOnFailureListener { isExpiryCheckRunning = false }
            }
            .addOnFailureListener { isExpiryCheckRunning = false }
    }

    // ── Remind user BEFORE points expire (1-3 days out) ──
    private fun checkPointsExpiringSoon(uid: String) {
        val prefs = getSharedPreferences("NotifPrefs", MODE_PRIVATE)

        db.collection("settings").document("pointsExpiry").get()
            .addOnSuccessListener { settingsDoc ->
                val mode = settingsDoc.getString("mode") ?: "never"
                if (mode != "after") return@addOnSuccessListener
                val months = settingsDoc.getLong("months")?.toInt() ?: 12

                db.collection("users").document(uid)
                    .collection("recyclingRecords")
                    .get()
                    .addOnSuccessListener { records ->
                        val now = java.util.Calendar.getInstance().time
                        val expiryFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())

                        // ── STEP 1: group remaining points by their expiry date (batch) ──
                        data class Batch(var totalRemaining: Double = 0.0, var expiryDate: java.util.Date = now)
                        val batches = mutableMapOf<String, Batch>()

                        records.documents.forEach { doc ->
                            if (doc.getString("trash_type") == "reward_claim") return@forEach
                            val alreadyExpired = doc.getBoolean("pointsExpired") ?: false
                            if (alreadyExpired) return@forEach

                            val originalPoints = doc.getDouble("points") ?: 0.0
                            val remaining = if (doc.contains("remainingPoints")) {
                                doc.getDouble("remainingPoints") ?: 0.0
                            } else originalPoints
                            if (remaining <= 0.0) return@forEach

                            val scannedAt = doc.getTimestamp(
                                "scanned_at",
                                com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                            )?.toDate() ?: return@forEach

                            val cal = java.util.Calendar.getInstance()
                            cal.time = scannedAt
                            cal.add(java.util.Calendar.MONTH, months)
                            val expiryDate = cal.time
                            val batchKey = expiryFmt.format(expiryDate)

                            val batch = batches.getOrPut(batchKey) { Batch(0.0, expiryDate) }
                            batch.totalRemaining += remaining
                        }

                        // ── STEP 2: one notification per batch, using the TOTAL remaining points ──
                        batches.forEach { (batchKey, batch) ->
                            if (batch.totalRemaining <= 0.0) return@forEach

                            val daysLeft = ((batch.expiryDate.time - now.time) / (1000 * 60 * 60 * 24)).toInt()
                            val remainingAmount = batch.totalRemaining

                            // ── Reminder window: 0-3 days before expiry, once per day-count AND per remaining amount ──
                            if (daysLeft in 0..3) {
                                val key = "points_expiring_notif_${uid}_${batchKey}_d${daysLeft}_amt${String.format("%.2f", remainingAmount)}"
                                if (!prefs.getBoolean(key, false)) {
                                    NotificationHelper.pointsExpiringSoon(
                                        this, uid, remainingAmount, daysLeft.coerceAtLeast(1)
                                    )
                                    prefs.edit().putBoolean(key, true).apply()
                                }
                            }
                        }
                    }
            }
    }

    private fun listenUnreadCount() {
        val uid = auth.currentUser?.uid ?: return
        unreadListener = db.collection("users").document(uid)
            .collection("notifications")
            .whereEqualTo("isRead", false)
            .addSnapshotListener { snapshot, _ ->
                val count = snapshot?.size() ?: 0
                val badge = findViewById<TextView>(R.id.tvNotifBadge)
                if (count > 0) {
                    badge?.visibility = View.VISIBLE
                    badge?.text = if (count > 99) "99+" else count.toString()
                } else {
                    badge?.visibility = View.GONE
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        unreadListener?.remove()
        unreadListener = null
        pointsListener?.remove()
        pointsListener = null
        recordsListener?.remove()
        recordsListener = null
    }

    private fun loadRecyclingRecords(uid: String) {
        val recordsContainer  = findViewById<LinearLayout>(R.id.staticRecordsContainer)

        recordsListener?.remove()
        recordsListener = db.collection("users").document(uid)
            .collection("recyclingRecords")
            .orderBy("scanned_at", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { records, error ->
                if (error != null || records == null) return@addSnapshotListener
                val visibleDocs = records.documents.filter { doc ->
                    val trashType = doc.getString("trash_type") ?: ""
                    when (trashType) {
                        "reward_claim"  -> true
                        "reward_refund" -> true
                        else            -> true
                    }
                }

                // ── Update Environmental Impact Card ──
                var totalWeightG = 0.0
                visibleDocs.forEach { doc ->
                    totalWeightG += doc.getDouble("weight_g") ?: 0.0
                }

                // Waste Recycled (individual items are in grams; total shown in kg)
                val wasteLabel = if (totalWeightG < 1000.0) {
                    "${String.format("%.1f", totalWeightG)}g"
                } else {
                    "${String.format("%.1f", totalWeightG / 1000.0)}kg"
                }
                findViewById<TextView>(R.id.tvWasteRecycled)?.text = "Waste Recycled: $wasteLabel"

                // Last transaction date + time
                val latestDoc = visibleDocs.firstOrNull()
                val latestDate = latestDoc?.getTimestamp(
                    "scanned_at",
                    com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )?.toDate()
                if (latestDate != null) {
                    val sdf = java.text.SimpleDateFormat("EEEE, MMMM dd, yyyy", java.util.Locale.getDefault())
                    val stf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                    findViewById<TextView>(R.id.last_updated)?.text = sdf.format(latestDate)
                    findViewById<TextView>(R.id.tvLastTime)?.text = stf.format(latestDate)
                } else {
                    findViewById<TextView>(R.id.last_updated)?.text = "No transactions yet"
                    findViewById<TextView>(R.id.tvLastTime)?.text = ""
                }
                recordsContainer.removeAllViews()

                if (visibleDocs.isEmpty()) {
                    val empty = TextView(this)
                    empty.text = "No recycling records yet."
                    empty.textSize = 13f
                    empty.setTextColor("#A09E9E".toColorInt())
                    empty.setPadding(0, 24, 0, 24)
                    empty.gravity = android.view.Gravity.CENTER
                    recordsContainer.addView(empty)
                } else {
                    renderGroupedRecords(recordsContainer, limitDocsToGroups(visibleDocs, 5))
                }
            }
    }

    private fun addRecordRow(container: LinearLayout, doc: com.google.firebase.firestore.DocumentSnapshot) {
        val trashType = doc.getString("trash_type") ?: "—"
        val weightG   = doc.getDouble("weight_g") ?: 0.0
        val points    = doc.getDouble("points") ?: 0.0
        val scannedAt = doc.getTimestamp(
            "scanned_at",
            com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        )?.toDate()
        val note       = doc.getString("note") ?: ""
        val rewardName = doc.getString("rewardName") ?: ""
        val dateStr   = scannedAt?.let {
            java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.getDefault()).format(it)
        } ?: "—"

// ── Handle reward claim / refund / expiry records differently ──
        val isRewardClaim   = trashType == "reward_claim"
        val isRewardRefund  = trashType == "reward_refund"
        val isPointsExpired = trashType == "points_expired"

        val trashLabel = when {
            isRewardRefund  -> "Refunded: " + rewardName.ifEmpty { note.removePrefix("Refunded: ").ifEmpty { "Reward" } }
            isRewardClaim   -> rewardName.ifEmpty { note.removePrefix("Claimed: ").ifEmpty { "Reward Claimed" } }
            isPointsExpired -> "Points Expired"
            trashType == "recycling_session" -> "Recycling Session"
            else -> trashType
        }

        val weightLabel = when {
            isRewardRefund  -> "Refund"
            isRewardClaim   -> "Rewards"
            isPointsExpired -> "Expired"
            else            -> "${String.format("%.1f", weightG)}g"
        }

        val pointsLabel = when {
            isRewardRefund  -> "+${String.format("%.2f", points)} pts"
            isRewardClaim   -> "${String.format("%.2f", points)} pts"
            isPointsExpired -> "${String.format("%.2f", points)} pts"   // already stored as negative
            else -> "+${String.format("%.2f", points)} pts"
        }

        val pointsColor = when {
            isRewardClaim   -> "#D32F2F"  // red — points spent
            isPointsExpired -> "#D32F2F"  // red — points lost
            else -> "#2E7D32"             // green — points earned/refunded
        }



        val wrapper = LinearLayout(this)
        wrapper.orientation = LinearLayout.VERTICAL
        wrapper.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )

        val tvDateHeader = TextView(this)
        tvDateHeader.text = dateStr
        tvDateHeader.textSize = 13f
        tvDateHeader.setTypeface(null, android.graphics.Typeface.BOLD)
        tvDateHeader.setTextColor("#A09E9E".toColorInt())
        val headerParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        headerParams.bottomMargin = 6.dpToPx()
        tvDateHeader.layoutParams = headerParams
        wrapper.addView(tvDateHeader)

        val itemLayout = LinearLayout(this)
        itemLayout.orientation = LinearLayout.VERTICAL
        val itemParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        itemParams.bottomMargin = 8.dpToPx()
        itemLayout.layoutParams = itemParams

        val row1 = LinearLayout(this)
        row1.orientation = LinearLayout.HORIZONTAL
        row1.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )

        val tvLabel = TextView(this)
        tvLabel.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        tvLabel.text = trashLabel
        tvLabel.textSize = 13f
        tvLabel.setTextColor("#1A1A1A".toColorInt())
        tvLabel.setTypeface(null, android.graphics.Typeface.BOLD)

        val tvWeight = TextView(this)
        tvWeight.text = weightLabel
        tvWeight.textSize = 13f
        tvWeight.setTextColor("#1A1A1A".toColorInt())
        tvWeight.setTypeface(null, android.graphics.Typeface.BOLD)

        row1.addView(tvLabel)
        row1.addView(tvWeight)

        val row2 = LinearLayout(this)
        row2.orientation = LinearLayout.HORIZONTAL
        row2.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        row2.gravity = android.view.Gravity.END

        val tvPts = TextView(this)
        tvPts.text = pointsLabel
        tvPts.textSize = 11f
        tvPts.setTextColor(pointsColor.toColorInt())
        tvPts.setTypeface(null, android.graphics.Typeface.BOLD)
        row2.addView(tvPts)

        itemLayout.addView(row1)
        itemLayout.addView(row2)
        wrapper.addView(itemLayout)

        val divider = View(this)
        val divParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
        divParams.bottomMargin = 8.dpToPx()
        divider.layoutParams = divParams
        divider.setBackgroundColor("#F0F0F0".toColorInt())
        wrapper.addView(divider)
        container.addView(wrapper)
    }

    private fun extractSessionId(docId: String): String {
        val lastUnderscore = docId.lastIndexOf("_")
        return if (lastUnderscore != -1 && docId.substring(lastUnderscore + 1).toIntOrNull() != null) {
            docId.substring(0, lastUnderscore)
        } else {
            docId
        }
    }

    private fun limitDocsToGroups(
        docs: List<com.google.firebase.firestore.DocumentSnapshot>,
        maxGroups: Int
    ): List<com.google.firebase.firestore.DocumentSnapshot> {
        val result = mutableListOf<com.google.firebase.firestore.DocumentSnapshot>()
        var groupsCount = 0
        var i = 0
        while (i < docs.size && groupsCount < maxGroups) {
            val doc = docs[i]
            if (doc.contains("bin")) {
                val sessionId = extractSessionId(doc.id)
                var j = i
                while (j < docs.size && docs[j].contains("bin") && extractSessionId(docs[j].id) == sessionId) {
                    result.add(docs[j])
                    j++
                }
                i = j
            } else {
                result.add(doc)
                i++
            }
            groupsCount++
        }
        return result
    }

    private fun renderGroupedRecords(container: LinearLayout, docs: List<com.google.firebase.firestore.DocumentSnapshot>) {
        var i = 0
        while (i < docs.size) {
            val doc = docs[i]
            if (doc.contains("bin")) {
                val sessionId = extractSessionId(doc.id)
                val group = mutableListOf(doc)
                var j = i + 1
                while (j < docs.size && docs[j].contains("bin") && extractSessionId(docs[j].id) == sessionId) {
                    group.add(docs[j])
                    j++
                }
                addTransactionGroup(container, group)
                i = j
            } else {
                addRecordRow(container, doc)
                i++
            }
        }
    }

    private fun addTransactionGroup(container: LinearLayout, group: List<com.google.firebase.firestore.DocumentSnapshot>) {
        val scannedAt = group.first().getTimestamp(
            "scanned_at",
            com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        )?.toDate()
        val dateStr = scannedAt?.let {
            java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.getDefault()).format(it)
        } ?: "—"

        val tvDateHeader = TextView(this)
        tvDateHeader.text = dateStr
        tvDateHeader.textSize = 13f
        tvDateHeader.setTypeface(null, android.graphics.Typeface.BOLD)
        tvDateHeader.setTextColor("#A09E9E".toColorInt())
        val headerParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        headerParams.bottomMargin = 6.dpToPx()
        tvDateHeader.layoutParams = headerParams
        container.addView(tvDateHeader)

        group.forEach { doc ->
            val label   = doc.getString("trash_type") ?: "—"
            val weightG = doc.getDouble("weight_g") ?: 0.0
            val points  = doc.getDouble("points") ?: 0.0

            val itemLayout = LinearLayout(this)
            itemLayout.orientation = LinearLayout.VERTICAL
            val itemParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            itemParams.bottomMargin = 8.dpToPx()
            itemLayout.layoutParams = itemParams

            val row1 = LinearLayout(this)
            row1.orientation = LinearLayout.HORIZONTAL
            row1.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )

            val tvLabel = TextView(this)
            tvLabel.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            tvLabel.text = label
            tvLabel.textSize = 13f
            tvLabel.setTextColor("#1A1A1A".toColorInt())
            tvLabel.setTypeface(null, android.graphics.Typeface.BOLD)

            val tvWeight = TextView(this)
            tvWeight.text = "${String.format("%.1f", weightG)}g"

            tvWeight.textSize = 13f
            tvWeight.setTextColor("#1A1A1A".toColorInt())
            tvWeight.setTypeface(null, android.graphics.Typeface.BOLD)

            row1.addView(tvLabel)
            row1.addView(tvWeight)

            val row2 = LinearLayout(this)
            row2.orientation = LinearLayout.HORIZONTAL
            row2.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            row2.gravity = android.view.Gravity.END

            val tvPts = TextView(this)
            tvPts.text = "+${String.format("%.2f", points)}pts"
            tvPts.textSize = 11f
            tvPts.setTextColor("#2E7D32".toColorInt())
            tvPts.setTypeface(null, android.graphics.Typeface.BOLD)
            row2.addView(tvPts)

            itemLayout.addView(row1)
            itemLayout.addView(row2)
            container.addView(itemLayout)
        }

        val divider = View(this)
        val divParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
        divParams.bottomMargin = 8.dpToPx()
        divider.layoutParams = divParams
        divider.setBackgroundColor("#F0F0F0".toColorInt())
        container.addView(divider)
    }

    // Helper
    private fun Int.dpToPx(): Int =
        (this * resources.displayMetrics.density).toInt()


    private fun checkNewRewardNotifications() {
        val uid = auth.currentUser?.uid ?: return
        val prefs = getSharedPreferences("NotifPrefs", MODE_PRIVATE)

        db.collection("rewards").get()
            .addOnSuccessListener { snapshot ->
                snapshot.documents.forEach { doc ->
                    val name   = doc.getString("name") ?: return@forEach
                    val docId  = doc.id
                    val expiry = doc.getString("expiry") ?: ""

                    // New reward notification
                    val newKey = "new_reward_${uid}_$docId"
                    if (!prefs.getBoolean(newKey, false)) {
                        NotificationHelper.newRewardAvailable(this, uid, name)
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
                                NotificationHelper.rewardExpiringSoon(this, uid, name)
                                prefs.edit().putBoolean(expiryKey, true).apply()
                            }
                        } catch (e: Exception) { /* ignore */ }
                    }
                }
            }
    }


    private fun checkVerificationBeforeScan() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                val status = document.getString("verificationStatus") ?: ""

                when (status) {
                    "verified" -> startActivity(Intent(this, QrScannerActivity::class.java))
                    "pending"  -> showPendingVerificationDialog()
                    else       -> showNotVerifiedDialog()
                }
            }
            .addOnFailureListener {
                android.widget.Toast.makeText(
                    this,
                    "Unable to check verification status. Try again.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun showNotVerifiedDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Account Not Verified")
            .setMessage("You need to verify your account first before you can scan QR codes.")
            .setPositiveButton("Verify Now") { _, _ ->
                startActivity(Intent(this, VerificationIntroPageCemo::class.java))
            }
            .setNegativeButton("Cancel", null)
            .setCancelable(true)
            .show()
    }

    private fun showPendingVerificationDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Verification Pending")
            .setMessage("Your ID verification is still being reviewed by our team. Please wait for approval before scanning QR codes.")
            .setPositiveButton("OK", null)
            .setCancelable(true)
            .show()
    }

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