package com.example.cemo_loginpage

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.widget.SwitchCompat
import com.google.firebase.auth.FirebaseAuth

class AppPreferencesPageCemo : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.cemo_app_preferences_page)

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        // ── Get current user UID for per-user prefs ──
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
        val prefs = getSharedPreferences("AppPrefs_$uid", MODE_PRIVATE)

        // ── Show Points Balance ──
        val switchShowPoints = findViewById<SwitchCompat>(R.id.switchShowPoints)
        switchShowPoints.isChecked = prefs.getBoolean("show_points_balance", true)
        switchShowPoints.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("show_points_balance", isChecked).apply()
        }

        // ── Enable Notifications ──
        val switchEnableNotif = findViewById<SwitchCompat>(R.id.switchEnableNotif)
        switchEnableNotif.isChecked = prefs.getBoolean("enable_notifications", true)
        switchEnableNotif.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("enable_notifications", isChecked).apply()
        }
    }
}