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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.graphics.toColorInt
import android.view.LayoutInflater
import android.widget.EditText
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import android.widget.Button

@Suppress("DEPRECATION")
class SettingsPageCemo : AppCompatActivity() {

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

    override fun onResume() {
        super.onResume()

        EmailSyncHelper.syncIfNeeded {
            loadUserData()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.statusBarColor = "#FFFFFF".toColorInt()
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        setContentView(R.layout.cemo_settings_page)

        // Settings rows
        findViewById<LinearLayout>(R.id.rowPrivacyData).setOnClickListener {
            startActivity(Intent(this, PrivacyDataPageCemo::class.java))
        }
        findViewById<LinearLayout>(R.id.rowChangePassword).setOnClickListener {
            showChangePasswordDialog()
        }
        findViewById<LinearLayout>(R.id.rowAppPreferences).setOnClickListener {
            startActivity(Intent(this, AppPreferencesPageCemo::class.java))
        }
        findViewById<LinearLayout>(R.id.rowAbout).setOnClickListener {
            startActivity(Intent(this, AboutPageCemo::class.java))
        }
        findViewById<LinearLayout>(R.id.rowSupport).setOnClickListener {
            startActivity(Intent(this, SupportPageCemo::class.java))
        }

        findViewById<LinearLayout>(R.id.rowQrScan).setOnClickListener {
            checkVerificationBeforeScan()
        }

        // Firebase
        auth = FirebaseAuth.getInstance()
        db   = FirebaseFirestore.getInstance()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        loadUserData()

        // Edit Profile — only shown when verified
        val tvEditProfile = findViewById<TextView>(R.id.tvEditProfile)
        tvEditProfile.setOnClickListener {
            startActivity(Intent(this, EditProfilePageCemo::class.java))
        }

        // ── Verify ID button ──────────────────────────────────────────
        findViewById<Button>(R.id.btnVerifyId).setOnClickListener {
            startActivity(Intent(this, VerificationIntroPageCemo::class.java))
        }

        // Bottom Nav
        navHome     = findViewById(R.id.navHome)
        navRewards  = findViewById(R.id.navRewards)
        navSettings = findViewById(R.id.navSettings)
        iconHome     = findViewById(R.id.iconHome)
        iconRewards  = findViewById(R.id.iconRewards)
        iconSettings = findViewById(R.id.iconSettings)
        labelHome     = findViewById(R.id.labelHome)
        labelRewards  = findViewById(R.id.labelRewards)
        labelSettings = findViewById(R.id.labelSettings)

        setActiveTab(Tab.SETTINGS)

        navHome.setOnClickListener {
            startActivity(Intent(this, HomePageCemo::class.java))
            overridePendingTransition(0, 0)
            finish()
        }
        navRewards.setOnClickListener {
            startActivity(Intent(this, RewardsPageCemo::class.java))
            overridePendingTransition(0, 0)
            finish()
        }
        navSettings.setOnClickListener { /* already here */ }

        // Log Out
        findViewById<LinearLayout>(R.id.logoutRow).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Log Out") { _, _ ->

                    auth.signOut()


                    getSharedPreferences("LoginPrefs", MODE_PRIVATE)
                        .edit()
                        .remove("loginTimestamp")
                        .apply()


                    Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, LoginPageCemo::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .setCancelable(false)
                .show()
        }
    }

    private fun loadUserData() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val name  = document.getString("name")  ?: "Your Name"
                    val email = document.getString("email") ?: "your@email.com"

                    val photoUrl = document.getString("photoUrl")
                    val ivProfilePhoto = findViewById<ImageView>(R.id.ivProfilePhoto)
                    if (!photoUrl.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(photoUrl)
                            .placeholder(R.drawable.prof_icon)
                            .error(R.drawable.prof_icon)
                            .into(ivProfilePhoto)
                    } else {
                        ivProfilePhoto.setImageResource(R.drawable.prof_icon)
                    }

                    val status = document.getString("verificationStatus")
                    val rejectionReason = document.getString("verificationRejectionReason") ?: "No reason provided."

                    // Show/hide Edit Profile based on verification
                    val tvEditProfile = findViewById<TextView>(R.id.tvEditProfile)
                    tvEditProfile.visibility = if (status == "verified") View.VISIBLE else View.GONE


                    // ── Name always white, no green ──
                    val tvName = findViewById<TextView>(R.id.tvProfileName)
                    tvName.text = name
                    tvName.setTextColor("#FFFFFF".toColorInt())

                    findViewById<TextView>(R.id.tvProfileEmail)?.text = email
                    updateVerificationUI(status)

                    // ── Notification logic ──
                    val prefs2 = getSharedPreferences("VerifNotifPrefs", MODE_PRIVATE)

                    if (status == "verified") {
                        val key = "verif_approved_$uid"
                        if (!prefs2.getBoolean(key, false)) {
                            NotificationHelper.idVerificationApproved(this, uid)
                            prefs2.edit().putBoolean(key, true).apply()
                        }
                    }
                    if (status == "rejected") {
                        val key = "verif_rejected_$uid"
                        if (!prefs2.getBoolean(key, false)) {
                            NotificationHelper.idVerificationRejected(this, uid, rejectionReason)
                            prefs2.edit().putBoolean(key, true).apply()
                        }
                    }
                    if (status == "pending") {
                        prefs2.edit()
                            .remove("verif_approved_$uid")
                            .remove("verif_rejected_$uid")
                            .apply()
                    }

                    // ── Show rejection notification dialog ──
                    if (status == "rejected") {
                        val prefs = getSharedPreferences("VerifPrefs", MODE_PRIVATE)
                        val alreadyNotified = prefs.getBoolean("rejectionNotified_$uid", false)
                        if (!alreadyNotified) {
                            showRejectionDialog(rejectionReason)
                            prefs.edit().putBoolean("rejectionNotified_$uid", true).apply()
                        }
                    }

                    // Reset notification flag if re-submitted
                    if (status == "pending") {
                        val prefs = getSharedPreferences("VerifPrefs", MODE_PRIVATE)
                        prefs.edit().remove("rejectionNotified_$uid").apply()
                    }
                }
            }
    }

    private fun showRejectionDialog(reason: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Verification Rejected")
            .setMessage(
                "Unfortunately, your ID verification was rejected by our team.\n\n" +
                        "Reason: $reason\n\n" +
                        "Please re-submit with a valid, clear photo of your ID."
            )
            .setPositiveButton("Re-submit Now") { _, _ ->
                startActivity(Intent(this, VerificationIntroPageCemo::class.java))
            }
            .setNegativeButton("Later", null)
            .setCancelable(false)
            .show()
    }



    /**
     * Shows the correct UI in the profile card depending on verification state:
     *   null / ""   → show "Verify ID" button (white pill)
     *   "pending"   → hide button, show "Verification Pending" badge
     *   "verified"  → hide button, show "✓ Verified" badge
     *   "rejected"  → show "Re-submit ID" button (warn colour)
     */
    private fun updateVerificationUI(status: String?) {
        val btnVerify    = findViewById<Button>(R.id.btnVerifyId)
        val layoutStatus = findViewById<LinearLayout>(R.id.layoutVerifyStatus)
        val tvStatus     = findViewById<TextView>(R.id.tvVerifyStatus)
        val ivStatusIcon = findViewById<ImageView>(R.id.ivVerifyStatusIcon)
        val ivVerifiedBadge = findViewById<ImageView>(R.id.ivVerifiedBadge)



        btnVerify.visibility    = View.GONE
        layoutStatus.visibility = View.GONE
        ivVerifiedBadge.visibility = View.GONE


        when (status) {
            null, "" -> {
                btnVerify.text = getString(R.string.settings_verify_id)
                btnVerify.visibility = View.VISIBLE
            }
            "pending" -> {
                layoutStatus.visibility = View.VISIBLE
                tvStatus.text = "Verification Pending"
                tvStatus.setTextColor("#F9A825".toColorInt())
                ivStatusIcon.setColorFilter("#F9A825".toColorInt())
            }
            "verified" -> {
                ivVerifiedBadge.visibility = View.VISIBLE

            }
            "rejected" -> {
                btnVerify.text = "Re-submit Verification"
                btnVerify.setBackgroundColor("#E53935".toColorInt())
                btnVerify.visibility = View.VISIBLE
            }
        }
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
                Toast.makeText(this, "Unable to check verification status. Try again.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showNotVerifiedDialog() {
        AlertDialog.Builder(this)
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
        AlertDialog.Builder(this)
            .setTitle("Verification Pending")
            .setMessage("Your ID verification is still being reviewed by our team. Please wait for approval before scanning QR codes.")
            .setPositiveButton("OK", null)
            .setCancelable(true)
            .show()
    }

    private fun showChangePasswordDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null)

        val etCurrent = dialogView.findViewById<EditText>(R.id.etCurrentPassword)
        val etNew = dialogView.findViewById<EditText>(R.id.etNewPassword)
        val etConfirm = dialogView.findViewById<EditText>(R.id.etConfirmPassword)
        val tvForgot = dialogView.findViewById<TextView>(R.id.tvForgotPassword)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Change Password")
            .setView(dialogView)
            .setPositiveButton("Change Password", null)
            .setNegativeButton("Cancel", null)
            .create()

        tvForgot.setOnClickListener {
            val email = auth.currentUser?.email
            if (email.isNullOrEmpty()) {
                Toast.makeText(this, "No email found for this account.", Toast.LENGTH_SHORT).show()
            } else {
                auth.sendPasswordResetEmail(email)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Password reset link sent to $email", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Unable to send reset email. Try again.", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        dialog.setOnShowListener {
            val btnPositive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btnPositive.setOnClickListener {
                val currentPassword = etCurrent.text.toString().trim()
                val newPassword = etNew.text.toString().trim()
                val confirmPassword = etConfirm.text.toString().trim()

                when {
                    currentPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty() -> {
                        Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show()
                    }
                    newPassword.length < 6 -> {
                        Toast.makeText(this, "New password must be at least 6 characters.", Toast.LENGTH_SHORT).show()
                    }
                    newPassword != confirmPassword -> {
                        Toast.makeText(this, "New password and retype password do not match.", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        changePassword(currentPassword, newPassword, dialog)
                    }
                }
            }
        }

        dialog.show()
    }

    private fun changePassword(currentPassword: String, newPassword: String, dialog: AlertDialog) {
        val user = auth.currentUser
        val email = user?.email

        if (user == null || email.isNullOrEmpty()) {
            Toast.makeText(this, "Unable to verify account. Please log in again.", Toast.LENGTH_SHORT).show()
            return
        }

        val credential = EmailAuthProvider.getCredential(email, currentPassword)

        user.reauthenticate(credential)
            .addOnSuccessListener {
                user.updatePassword(newPassword)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Password changed successfully.", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to update password. Try again.", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Current password is incorrect.", Toast.LENGTH_SHORT).show()
            }
    }
}