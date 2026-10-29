package com.example.cemo_loginpage

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.Toast
import androidx.core.graphics.toColorInt
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

import android.widget.CheckBox


import android.widget.LinearLayout
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.GoogleAuthProvider
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult

@Suppress("DEPRECATION")
class LoginPageCemo : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>

    @SuppressLint("CutPasteId", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //auto-skip login if session is still valid
        val sharedPref = getSharedPreferences("LoginPrefs", MODE_PRIVATE)
        val loginTimestamp = sharedPref.getLong("loginTimestamp", 0L)
        val sixtyDaysMillis = 60L * 24 * 60 * 60 * 1000

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null && loginTimestamp > 0L) {
            val elapsed = System.currentTimeMillis() - loginTimestamp
            if (elapsed < sixtyDaysMillis) {
                // Session still valid → skip to Home
                startActivity(Intent(this, HomePageCemo::class.java))
                overridePendingTransition(0, 0)
                finish()
                return
            } else {
                // 60 days passed → force logout
                FirebaseAuth.getInstance().signOut()
                sharedPref.edit().remove("loginTimestamp").apply()
            }
        }

        // Fix status bar
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.statusBarColor = "#FFFFFF".toColorInt()
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        setContentView(R.layout.cemo_login_page)


        val mainLayout = findViewById<LinearLayout>(R.id.main)
        val tvSignup = findViewById<TextView>(R.id.tvSignup)

        mainLayout.post {
            val signUpLocation = IntArray(2)
            tvSignup.getLocationOnScreen(signUpLocation)
            val signUpBottom = signUpLocation[1] + tvSignup.height
            val screenHeight = resources.displayMetrics.heightPixels

            if (signUpBottom > screenHeight) {
                // Sign Up is hidden, wrap layout in ScrollView programmatically
                val parent = mainLayout.parent as android.view.ViewGroup
                val scrollView = android.widget.ScrollView(this)
                scrollView.isFillViewport = true

                val index = parent.indexOfChild(mainLayout)
                parent.removeView(mainLayout)
                scrollView.addView(mainLayout)
                parent.addView(scrollView, index)
            }
        }

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()


        val etEmail = findViewById<EditText>(R.id.etEmail)
        val cbRememberMe = findViewById<CheckBox>(R.id.cbRememberMe)

        val tvForgotPassword = findViewById<TextView>(R.id.tvForgotPassword)
        tvForgotPassword.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Reset link sent to $email", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }

// Load saved email if remembered
        val savedEmail = sharedPref.getString("email", "")
        if (!savedEmail.isNullOrEmpty()) {
            etEmail.setText(savedEmail)
            cbRememberMe.isChecked = true
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("942489799004-2equvg46l6luv5mhvdnlns8eauua7275.apps.googleusercontent.com")
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        googleSignInLauncher = registerForActivityResult(StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                handleGoogleSignInResult(task)
            }
        }

        val rootView = findViewById<View?>(R.id.main)
        rootView?.let { view ->
            ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }
        }

        // Password Toggle
        val etPassword = findViewById<EditText>(R.id.etPassword)
        var passwordVisible = false

        etPassword.setOnTouchListener { _, event ->
            val drawableRight = 2
            val drawable = etPassword.compoundDrawables[drawableRight]
            if (drawable != null && event.action == MotionEvent.ACTION_UP) {
                if (event.rawX >= (etPassword.right - drawable.bounds.width())) {
                    passwordVisible = if (passwordVisible) {
                        etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                        etPassword.setCompoundDrawablesWithIntrinsicBounds(
                            null, null, ContextCompat.getDrawable(this, R.drawable.ic_visibility_off), null
                        )
                        false
                    } else {
                        etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                        etPassword.setCompoundDrawablesWithIntrinsicBounds(
                            null, null, ContextCompat.getDrawable(this, R.drawable.ic_visibility), null
                        )
                        true
                    }
                    etPassword.setSelection(etPassword.text.length)
                    return@setOnTouchListener true
                }
            }
            false
        }



        // Login Button
        val btnLogin = findViewById<AppCompatButton>(R.id.btnLogin)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email & password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Disable button to prevent double-tap
            btnLogin.isEnabled = false
            btnLogin.text = "Logging in..."

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    btnLogin.isEnabled = true
                    btnLogin.text = "Login"

                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        val hasGoogleProvider = user?.providerData?.any { it.providerId == "google.com" } == true

                        if (user != null && (user.isEmailVerified || hasGoogleProvider)) {
                            checkDeletionStatusAndProceed(user.uid, email, cbRememberMe.isChecked, sharedPref, btnLogin)
                        } else {
                            // Not verified → block access
                            auth.signOut()
                            Toast.makeText(
                                this,
                                "Please verify your email first. Check your inbox.",
                                Toast.LENGTH_LONG
                            ).show()
                            btnLogin.isEnabled = true
                            btnLogin.text = "Login"
                        }

                    } else {
                        val errorMessage = when {

                            task.exception?.message?.contains("credential") == true ->
                                "Incorrect email or password. Please try again."

                            task.exception?.message?.contains("badly formatted") == true ->
                                "Invalid email format. Please check your email."

                            task.exception?.message?.contains("too many requests") == true ->
                                "Too many failed attempts. Please try again later."

                            else -> task.exception?.message ?: "Login failed. Please try again."
                        }
                        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                    }

                }
        }

        // Signup text → Signup Page
        tvSignup.setOnClickListener {
            startActivity(Intent(this, SignupPageCemo::class.java))
        }

        val btnGoogle = findViewById<LinearLayout>(R.id.btnGoogle)
        btnGoogle.setOnClickListener {
            googleSignInClient.signOut().addOnCompleteListener(this) {
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            }
        }
    }
    private fun checkDeletionStatusAndProceed(
        uid: String,
        email: String,
        remember: Boolean,
        sharedPref: android.content.SharedPreferences,
        btnLogin: AppCompatButton
    ) {
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                val deletionRequestedAt = document.getLong("deletionRequestedAt")
                val thirtyDaysMillis = 30L * 24 * 60 * 60 * 1000

                if (deletionRequestedAt != null) {
                    val elapsed = System.currentTimeMillis() - deletionRequestedAt
                    if (elapsed >= thirtyDaysMillis) {
                        // Grace period expired → permanently delete data, block login
                        db.collection("users").document(uid).delete()
                            .addOnCompleteListener {
                                auth.currentUser?.delete()
                                Toast.makeText(this, "This account has been permanently deleted.", Toast.LENGTH_LONG).show()
                                btnLogin.isEnabled = true
                                btnLogin.text = "Login"
                            }
                        return@addOnSuccessListener
                    } else {
                        // Still within grace period → ask for confirmation before reactivating
                        val daysLeft = 30 - (elapsed / (24 * 60 * 60 * 1000))
                        btnLogin.isEnabled = true
                        btnLogin.text = "Login"

                        androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Account Deactivated")
                            .setMessage("Your account is deactivated and is subject to deletion in $daysLeft day(s). Logging in now will restore your account. Do you want to continue?")
                            .setPositiveButton("Restore Account") { _, _ ->
                                btnLogin.isEnabled = false
                                btnLogin.text = "Logging in..."
                                db.collection("users").document(uid)
                                    .update("deletionRequestedAt", com.google.firebase.firestore.FieldValue.delete())
                                    .addOnCompleteListener {
                                        Toast.makeText(this, "Account reactivated!", Toast.LENGTH_SHORT).show()
                                        finishLogin(uid, email, remember, sharedPref, btnLogin)
                                    }
                            }
                            .setNegativeButton("Cancel") { _, _ ->
                                auth.signOut()
                            }
                            .setCancelable(false)
                            .show()
                        return@addOnSuccessListener
                    }
                }

                finishLogin(uid, email, remember, sharedPref, btnLogin)
            }
            .addOnFailureListener {
                btnLogin.isEnabled = true
                btnLogin.text = "Login"
                Toast.makeText(this, "Unable to verify account status. Try again.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun finishLogin(
        uid: String,
        email: String,
        remember: Boolean,
        sharedPref: android.content.SharedPreferences,
        btnLogin: AppCompatButton
    ) {
        if (remember) {
            sharedPref.edit().putString("email", email).apply()
        } else {
            sharedPref.edit().remove("email").apply()
        }
        sharedPref.edit().putLong("loginTimestamp", System.currentTimeMillis()).apply()

        Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, HomePageCemo::class.java))
        overridePendingTransition(0, 0)
        finish()
    }


    private fun handleGoogleSignInResult(task: Task<GoogleSignInAccount?>) {
        try {
            val account = task.getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(account?.idToken, null)

            auth.signInWithCredential(credential).addOnCompleteListener(this) { authTask ->
                if (authTask.isSuccessful) {
                    val user = auth.currentUser


                    if (user != null) {
                        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        db.collection("users").document(user.uid).get()
                            .addOnSuccessListener { document ->
                                if (!document.exists()) {
                                    val userMap = hashMapOf(
                                        "name"      to (user.displayName ?: "User"),
                                        "email"     to (user.email ?: ""),
                                        "contact"   to "",
                                        "barangay"  to "",
                                        "createdAt" to System.currentTimeMillis()
                                    )
                                    db.collection("users").document(user.uid).set(userMap)
                                        .addOnSuccessListener {
                                            NotificationHelper.welcomeNewUser(this, user.uid)
                                            getSharedPreferences("LoginPrefs", MODE_PRIVATE)
                                                .edit()
                                                .putLong("loginTimestamp", System.currentTimeMillis())
                                                .apply()
                                            // NEW user → go to complete profile
                                            startActivity(Intent(this, CompleteProfileActivity::class.java))
                                            overridePendingTransition(0, 0)
                                            finish()
                                        }
                                } else {
                                    // EXISTING user → check deletion status first
                                    val deletionRequestedAt = document.getLong("deletionRequestedAt")
                                    val thirtyDaysMillis = 30L * 24 * 60 * 60 * 1000

                                    if (deletionRequestedAt != null &&
                                        System.currentTimeMillis() - deletionRequestedAt >= thirtyDaysMillis) {
                                        // Grace period expired → permanently delete, block login
                                        db.collection("users").document(user.uid).delete()
                                            .addOnCompleteListener {
                                                auth.currentUser?.delete()
                                                Toast.makeText(this, "This account has been permanently deleted.", Toast.LENGTH_LONG).show()
                                            }
                                        return@addOnSuccessListener
                                    }

                                    if (deletionRequestedAt != null) {
                                        // Still within grace period → ask for confirmation before reactivating
                                        val daysLeft = 30 - ((System.currentTimeMillis() - deletionRequestedAt) / (24 * 60 * 60 * 1000))

                                        androidx.appcompat.app.AlertDialog.Builder(this)
                                            .setTitle("Account Deactivated")
                                            .setMessage("Your account is deactivated and is subject to deletion in $daysLeft day(s). Logging in now will restore your account. Do you want to continue?")
                                            .setPositiveButton("Restore Account") { _, _ ->
                                                db.collection("users").document(user.uid)
                                                    .update("deletionRequestedAt", com.google.firebase.firestore.FieldValue.delete())
                                                    .addOnCompleteListener {
                                                        Toast.makeText(this, "Account reactivated!", Toast.LENGTH_SHORT).show()
                                                        getSharedPreferences("LoginPrefs", MODE_PRIVATE)
                                                            .edit()
                                                            .putLong("loginTimestamp", System.currentTimeMillis())
                                                            .apply()
                                                        startActivity(Intent(this, HomePageCemo::class.java))
                                                        overridePendingTransition(0, 0)
                                                        finish()
                                                    }
                                            }
                                            .setNegativeButton("Cancel") { _, _ ->
                                                auth.signOut()
                                            }
                                            .setCancelable(false)
                                            .show()
                                        return@addOnSuccessListener
                                    }

                                    getSharedPreferences("LoginPrefs", MODE_PRIVATE)
                                        .edit()
                                        .putLong("loginTimestamp", System.currentTimeMillis())
                                        .apply()
                                    startActivity(Intent(this, HomePageCemo::class.java))
                                    overridePendingTransition(0, 0)
                                    finish()
                                }
                            }
                            .addOnFailureListener {
                                startActivity(Intent(this, HomePageCemo::class.java))
                                overridePendingTransition(0, 0)
                                finish()
                            }
                    } else {
                        auth.signOut()
                        Toast.makeText(this, "Google sign-in failed.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Google sign-in failed.", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: ApiException) {
            Toast.makeText(this, "Sign-in error: ${e.statusCode}", Toast.LENGTH_SHORT).show()
        }
    }


}