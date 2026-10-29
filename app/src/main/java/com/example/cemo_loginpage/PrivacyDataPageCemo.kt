package com.example.cemo_loginpage

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.graphics.toColorInt
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class PrivacyDataPageCemo : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.statusBarColor = "#FFFFFF".toColorInt()
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        setContentView(R.layout.cemo_privacy_data_page)

        // Back button
        val btnBack = findViewById<ImageView>(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }

        // "Request to Delete My Data" button — starts 30-day deletion grace period
        val btnRequestDelete = findViewById<AppCompatButton>(R.id.btnRequestDelete)
        btnRequestDelete.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Request to Delete Your Data?")
                .setMessage(
                    "Your account will enter a 30-day deletion period. During this time, you may " +
                            "reactivate your account by logging in. If no action is taken within 30 days, " +
                            "your account and personal data will be permanently deleted. This action cannot " +
                            "be undone after the 30-day period."
                )
                .setPositiveButton("Request Deletion") { _, _ ->
                    requestAccountDeletion()
                }
                .setNegativeButton("Cancel", null)
                .setCancelable(true)
                .show()
        }
    }

    private fun requestAccountDeletion() {
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        db.collection("users").document(uid)
            .update("deletionRequestedAt", System.currentTimeMillis())
            .addOnSuccessListener {
                Toast.makeText(this, "Deletion requested. You have 30 days to reactivate by logging in.", Toast.LENGTH_LONG).show()
                auth.signOut()
                getSharedPreferences("LoginPrefs", MODE_PRIVATE).edit().remove("loginTimestamp").apply()
                val intent = Intent(this, LoginPageCemo::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to request deletion: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}