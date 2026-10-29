package com.example.cemo_loginpage

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@Suppress("DEPRECATION")
class SignupPageCemo : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var spBaranggay: Spinner
    private lateinit var etContact: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var btnRegister: AppCompatButton
    private lateinit var tvLogin: TextView

    private lateinit var pbPasswordStrength: ProgressBar
    private lateinit var tvPasswordStrength: TextView

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    @SuppressLint("ClickableViewAccessibility", "CutPasteId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.statusBarColor = "#243E6D".toColorInt()
        window.decorView.systemUiVisibility = 0

        setContentView(R.layout.cemo_signup_page)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        etName            = findViewById(R.id.etName)
        spBaranggay       = findViewById(R.id.spBaranggay)
        etContact         = findViewById(R.id.etContact)
        etEmail           = findViewById(R.id.etEmail)
        etPassword        = findViewById(R.id.etPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        btnRegister       = findViewById(R.id.btnRegister)
        tvLogin           = findViewById(R.id.tvLogin)
        pbPasswordStrength = findViewById(R.id.pbPasswordStrength)
        tvPasswordStrength = findViewById(R.id.tvPasswordStrength)

        etPassword.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                updatePasswordStrength(s?.toString() ?: "")
            }
        })
        // Barangay Spinner
        val baranggays = resources.getStringArray(R.array.baranggay_list)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, baranggays)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spBaranggay.adapter = adapter

        // Register Button
        btnRegister.setOnClickListener {
            val name            = etName.text.toString().trim()
            val contact         = etContact.text.toString().trim()
            val email           = etEmail.text.toString().trim()
            val password        = etPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()
            val barangay        = spBaranggay.selectedItem.toString()

            // Validation
            if (name.isEmpty() || contact.isEmpty() || email.isEmpty() ||
                password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (barangay == "[Select Barangay]") {
                Toast.makeText(this, "Please select your barangay", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (contact.length != 11) {
                Toast.makeText(this, "Contact number must be 11 digits", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnRegister.isEnabled = false
            btnRegister.text = "Registering..."

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        sendOtpAndShowDialog(name, email, contact, barangay)
                    } else {
                        btnRegister.isEnabled = true
                        btnRegister.text = "Register"
                        Toast.makeText(this, task.exception?.message ?: "Registration failed", Toast.LENGTH_LONG).show()
                    }
                }
        }

        // Login text → Login Page
        tvLogin.setOnClickListener {
            startActivity(Intent(this, LoginPageCemo::class.java))
            finish()
        }

        // Password visibility toggles
        setupPasswordToggle(etPassword, false) { }
        setupPasswordToggle(etConfirmPassword, false) { }
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

    override fun onResume() {
        super.onResume()
        val user = auth.currentUser ?: return
        if (user.isEmailVerified) return

        user.reload().addOnCompleteListener {
            if (auth.currentUser?.isEmailVerified == true) {
                val name     = etName.text.toString().trim()
                val contact  = etContact.text.toString().trim()
                val email    = etEmail.text.toString().trim()
                val barangay = spBaranggay.selectedItem.toString()
                saveUserToFirestore(name, email, contact, barangay)
            }
        }
    }

    private fun sendOtpAndShowDialog(
        name: String, email: String, contact: String, barangay: String
    ) {
        com.google.firebase.functions.FirebaseFunctions.getInstance()
            .getHttpsCallable("sendOtp")
            .call(hashMapOf("email" to email))
            .addOnCompleteListener { task ->
                btnRegister.isEnabled = true
                btnRegister.text = "Register"
                if (task.isSuccessful) {
                    showOtpDialog(name, email, contact, barangay)
                } else {
                    auth.currentUser?.delete()
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Debug Error")
                        .setMessage(task.exception.toString())
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
    }

    private fun showOtpDialog(name: String, email: String, contact: String, barangay: String) {
        val otpView = layoutInflater.inflate(R.layout.dialog_otp_input, null)
        val otpBoxes = listOf<EditText>(
            otpView.findViewById(R.id.etOtp1),
            otpView.findViewById(R.id.etOtp2),
            otpView.findViewById(R.id.etOtp3),
            otpView.findViewById(R.id.etOtp4),
            otpView.findViewById(R.id.etOtp5),
            otpView.findViewById(R.id.etOtp6)
        )

        // Auto-advance / auto-backspace between boxes
        for (i in otpBoxes.indices) {
            otpBoxes[i].addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val text = s?.toString() ?: ""

                    // Handle pasted multi-digit code (e.g. pasting "123456" into one box)
                    if (text.length > 1) {
                        val digits = text.filter { it.isDigit() }
                        otpBoxes[i].removeTextChangedListener(this)
                        otpBoxes[i].setText(if (digits.isNotEmpty()) digits[0].toString() else "")
                        otpBoxes[i].addTextChangedListener(this)

                        var nextIndex = i + 1
                        for (d in digits.drop(1)) {
                            if (nextIndex >= otpBoxes.size) break
                            otpBoxes[nextIndex].setText(d.toString())
                            nextIndex++
                        }
                        val focusIndex = (i + digits.length).coerceAtMost(otpBoxes.size - 1)
                        otpBoxes[focusIndex].requestFocus()
                        return
                    }

                    if (text.length == 1 && i < otpBoxes.size - 1) {
                        otpBoxes[i + 1].requestFocus()
                    }
                }
            })
            otpBoxes[i].setOnKeyListener { _, keyCode, event ->
                if (keyCode == android.view.KeyEvent.KEYCODE_DEL &&
                    event.action == android.view.KeyEvent.ACTION_DOWN &&
                    otpBoxes[i].text.isEmpty() && i > 0
                ) {
                    otpBoxes[i - 1].requestFocus()
                    otpBoxes[i - 1].setText("")
                }
                false
            }
        }
        otpBoxes.first().requestFocus()

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Verify Your Email")
            .setMessage("We sent a 6-digit code to:\n\n$email\n\nEnter it below to verify your account.")
            .setView(otpView)
            .setCancelable(false)
            .setPositiveButton("Verify", null)
            .setNegativeButton("Resend Code") { _, _ ->
                sendOtpAndShowDialog(name, email, contact, barangay)
            }
            .create()

        dialog.setOnShowListener {
            val verifyButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            verifyButton.setOnClickListener {
                val code = otpBoxes.joinToString("") { it.text.toString() }
                if (code.length != 6) {
                    Toast.makeText(this, "Please enter the 6-digit code.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                verifyButton.isEnabled = false
                com.google.firebase.functions.FirebaseFunctions.getInstance()
                    .getHttpsCallable("verifyOtp")
                    .call(hashMapOf("email" to email, "code" to code))
                    .addOnCompleteListener { task ->
                        verifyButton.isEnabled = true
                        if (task.isSuccessful) {
                            dialog.dismiss()
                            saveUserToFirestore(name, email, contact, barangay)
                        } else {
                            Toast.makeText(this, task.exception?.message ?: "Verification failed.", Toast.LENGTH_LONG).show()
                        }
                    }
            }
        }
        dialog.show()
    }

    private fun saveUserToFirestore(
        name: String, email: String,
        contact: String, barangay: String
    ) {
        val uid = auth.currentUser?.uid ?: return
        val userMap = hashMapOf(
            "name"      to name,
            "email"     to email,
            "contact"   to contact,
            "barangay"  to barangay,
            "createdAt" to System.currentTimeMillis()
        )
        db.collection("users").document(uid)
            .set(userMap)
            .addOnSuccessListener {
                db.collection("userEmails").document(email)
                    .set(mapOf("uid" to uid))
                NotificationHelper.welcomeNewUser(this, uid)

                // Mark session valid so future app opens auto-skip login
                getSharedPreferences("LoginPrefs", MODE_PRIVATE)
                    .edit()
                    .putLong("loginTimestamp", System.currentTimeMillis())
                    .apply()

                Toast.makeText(this, "Account verified and created!", Toast.LENGTH_LONG).show()
                startActivity(Intent(this, HomePageCemo::class.java))
                finishAffinity()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save data: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPasswordToggle(field: EditText, initialVisible: Boolean, onToggle: (Boolean) -> Unit) {
        var visible = initialVisible
        field.setOnTouchListener { _, event ->
            val drawableRight = field.compoundDrawables[2]
            if (drawableRight != null && event.action == MotionEvent.ACTION_UP) {
                if (event.rawX >= (field.right - drawableRight.bounds.width())) {
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
                    onToggle(visible)
                    return@setOnTouchListener true
                }
            }
            false
        }
    }
}