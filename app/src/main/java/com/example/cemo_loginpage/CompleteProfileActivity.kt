package com.example.cemo_loginpage

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.graphics.toColorInt
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.annotation.SuppressLint
import android.text.InputType
import android.view.MotionEvent
import androidx.core.content.ContextCompat
import com.google.firebase.auth.EmailAuthProvider

@Suppress("DEPRECATION")
class CompleteProfileActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var spBarangay: Spinner
    private lateinit var etContact: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var btnSave: AppCompatButton
    private lateinit var pbPasswordStrength: ProgressBar
    private lateinit var tvPasswordStrength: TextView

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.statusBarColor = "#243E6D".toColorInt()
        window.decorView.systemUiVisibility = 0

        setContentView(R.layout.activity_complete_profile)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        etName             = findViewById(R.id.etName)
        spBarangay         = findViewById(R.id.spBarangay)
        etContact          = findViewById(R.id.etContact)
        etPassword         = findViewById(R.id.etPassword)
        etConfirmPassword  = findViewById(R.id.etConfirmPassword)
        btnSave            = findViewById(R.id.btnSave)
        pbPasswordStrength = findViewById(R.id.pbPasswordStrength)
        tvPasswordStrength = findViewById(R.id.tvPasswordStrength)

        setupPasswordToggle(etPassword)
        setupPasswordToggle(etConfirmPassword)

        etPassword.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                updatePasswordStrength(s?.toString() ?: "")
            }
        })

        // Pre-fill name from Google account
        val currentUser = auth.currentUser
        if (currentUser != null) {
            etName.setText(currentUser.displayName ?: "")
        }

        // Barangay Spinner
        val barangays = resources.getStringArray(R.array.baranggay_list)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, barangays)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spBarangay.adapter = adapter

        btnSave.setOnClickListener {
            val name            = etName.text.toString().trim()
            val contact         = etContact.text.toString().trim()
            val barangay        = spBarangay.selectedItem.toString()
            val password        = etPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()

            if (name.isEmpty() || contact.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (barangay == "[Select Barangay]") {
                Toast.makeText(this, "Please select your barangay", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (contact.length != 11 || !contact.all { it.isDigit() }) {
                Toast.makeText(this, "Contact number must be 11 digits", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!PhoneNumberHelper.isValidPhMobile(contact)) {
                Toast.makeText(this, "Please enter a valid Philippine mobile number.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSave.isEnabled = false
            btnSave.text = "Saving..."

            val uid = currentUser?.uid ?: return@setOnClickListener
            val userEmail = currentUser.email

            if (userEmail.isNullOrEmpty()) {
                btnSave.isEnabled = true
                btnSave.text = "Save & Continue"
                Toast.makeText(this, "No email found on this account.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Link an email/password credential so the user can also log in that way
            val credential = EmailAuthProvider.getCredential(userEmail, password)
            currentUser.linkWithCredential(credential)
                .addOnCompleteListener { linkTask ->
                    if (!linkTask.isSuccessful &&
                        linkTask.exception?.message?.contains("already linked") != true) {
                        btnSave.isEnabled = true
                        btnSave.text = "Save & Continue"
                        Toast.makeText(this, "Failed to set password: ${linkTask.exception?.message}", Toast.LENGTH_LONG).show()
                        return@addOnCompleteListener
                    }

                    val updates = mapOf(
                        "name"     to name,
                        "contact"  to contact,
                        "barangay" to barangay
                    )

                    db.collection("users").document(uid)
                        .update(updates)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Profile saved!", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, HomePageCemo::class.java))
                            overridePendingTransition(0, 0)
                            finish()
                        }
                        .addOnFailureListener { e ->
                            btnSave.isEnabled = true
                            btnSave.text = "Save & Continue"
                            Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
        }
    }

    private fun updatePasswordStrength(password: String) {
        if (password.isEmpty()) {
            pbPasswordStrength.visibility = android.view.View.GONE
            tvPasswordStrength.visibility = android.view.View.GONE
            return
        }

        var score = 0
        if (password.length >= 6) score++
        if (password.length >= 10) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++

        val (progress, label, color) = when {
            score <= 1 -> Triple(25, "Weak password", "#E53935")
            score in 2..3 -> Triple(60, "Medium password", "#FB8C00")
            else -> Triple(100, "Strong password", "#43A047")
        }

        pbPasswordStrength.visibility = android.view.View.VISIBLE
        tvPasswordStrength.visibility = android.view.View.VISIBLE
        pbPasswordStrength.progress = progress
        pbPasswordStrength.progressTintList = android.content.res.ColorStateList.valueOf(color.toColorInt())
        tvPasswordStrength.text = label
        tvPasswordStrength.setTextColor(color.toColorInt())
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPasswordToggle(field: EditText) {
        var visible = false
        field.setOnTouchListener { _, event ->
            val drawableEnd = field.compoundDrawables[2]
            if (drawableEnd != null && event.action == MotionEvent.ACTION_UP) {
                if (event.rawX >= (field.right - drawableEnd.bounds.width())) {
                    visible = if (visible) {
                        field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                        field.setCompoundDrawablesWithIntrinsicBounds(
                            null, null, ContextCompat.getDrawable(this, R.drawable.ic_visibility_off), null
                        )
                        false
                    } else {
                        field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                        field.setCompoundDrawablesWithIntrinsicBounds(
                            null, null, ContextCompat.getDrawable(this, R.drawable.ic_visibility), null
                        )
                        true
                    }
                    field.setSelection(field.text.length)
                    return@setOnTouchListener true
                }
            }
            false
        }
    }
}