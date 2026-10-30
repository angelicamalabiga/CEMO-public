package com.example.cemo_loginpage

import android.app.Activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.core.graphics.toColorInt
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.widget.Spinner
import android.widget.ArrayAdapter
import com.bumptech.glide.Glide
import com.google.firebase.storage.FirebaseStorage

import android.view.LayoutInflater



@Suppress("DEPRECATION")
class EditProfilePageCemo : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var ivProfilePhoto: ShapeableImageView
    private var selectedImageUri: Uri? = null
    private var originalEmail: String = ""



    private var emailPollHandler: android.os.Handler? = null
    private var emailPollRunnable: Runnable? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedImageUri = result.data?.data
            ivProfilePhoto.setImageURI(selectedImageUri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.statusBarColor = "#FFFFFF".toColorInt()
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        setContentView(R.layout.cemo_edit_profile_page)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        ivProfilePhoto = findViewById(R.id.ivProfilePhoto)
        val etName = findViewById<EditText>(R.id.etName)
        val spBarangay = findViewById<Spinner>(R.id.spBarangay)
        val baranggays = resources.getStringArray(R.array.baranggay_list)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, baranggays)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spBarangay.adapter = adapter
        val etContact = findViewById<EditText>(R.id.etContact)
        val btnSave = findViewById<AppCompatButton>(R.id.btnSave)
        val btnBack = findViewById<android.widget.ImageView>(R.id.btnBack)
        val btnChangePhoto = findViewById<LinearLayout>(R.id.btnChangePhoto)
        val etEmail = findViewById<EditText>(R.id.etEmail)


        // Back button
        btnBack.setOnClickListener { finish() }

        // Load existing user data — sync any confirmed pending email first
        val uid = auth.currentUser?.uid ?: return
        EmailSyncHelper.syncIfNeeded { didSync ->
            if (didSync) {
                EmailSyncHelper.showEmailChangedDialogAndLogout(this)
                return@syncIfNeeded
            }
            db.collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    val pendingEmail = doc.getString("pendingEmail")
                    if (!pendingEmail.isNullOrEmpty()) {

                        showAwaitingEmailVerificationDialog(pendingEmail)
                        return@addOnSuccessListener
                    }

                    etName.setText(doc.getString("name") ?: "")

                    val photoUrl = doc.getString("photoUrl")
                    if (!photoUrl.isNullOrEmpty()) {
                        Glide.with(this).load(photoUrl).into(ivProfilePhoto)
                    }


                    val savedBarangay = doc.getString("barangay") ?: ""
                    val position = baranggays.indexOf(savedBarangay)
                    if (position >= 0) spBarangay.setSelection(position)
                    etContact.setText(doc.getString("contact") ?: "")
                    originalEmail = doc.getString("email") ?: ""
                    etEmail.setText(originalEmail)

// Pull DOB and Address from verifications collection
                    val uid2 = auth.currentUser?.uid ?: return@addOnSuccessListener
                    db.collection("verifications").document(uid2).get()
                        .addOnSuccessListener { verDoc ->
                            if (verDoc != null && verDoc.exists()) {
                                val etDob     = findViewById<EditText>(R.id.etDob)
                                val etAddress = findViewById<EditText>(R.id.etAddress)
                                etDob?.setText(verDoc.getString("dateOfBirth") ?: "")
                                etAddress?.setText(verDoc.getString("address") ?: "")
                            }
                        }
                }
        }



        // Change photo
        btnChangePhoto.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImageLauncher.launch(intent)
        }

// Save button
        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val barangay = spBarangay.selectedItem.toString()
            val contact = etContact.text.toString().trim()
            val newEmail = etEmail.text.toString().trim()

            if (name.isEmpty() || contact.isEmpty() || newEmail.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
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

            btnSave.isEnabled = false
            btnSave.text = "Saving..."

            val emailChanged = newEmail != originalEmail

            // If image was picked, upload first
            if (selectedImageUri != null) {
                val storageRef = FirebaseStorage.getInstance()
                    .reference.child("profile_photos/$uid.jpg")

                storageRef.putFile(selectedImageUri!!)
                    .addOnSuccessListener {
                        storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                            if (emailChanged) {
                                requestEmailChangeDialog(newEmail, name, barangay, contact, btnSave, downloadUrl.toString())
                            } else {
                                db.collection("users").document(uid).update(
                                    mapOf(
                                        "name" to name,
                                        "barangay" to barangay,
                                        "contact" to contact,
                                        "photoUrl" to downloadUrl.toString()
                                    )
                                ).addOnSuccessListener {
                                    Toast.makeText(this, "Profile updated!", Toast.LENGTH_SHORT).show()
                                    finish()
                                }.addOnFailureListener {
                                    btnSave.isEnabled = true
                                    btnSave.text = "Save And Continue"
                                    Toast.makeText(this, "Failed to update profile", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Photo upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                        btnSave.isEnabled = true
                        btnSave.text = "Save And Continue"
                    }
            } else {
                val etDob     = findViewById<EditText>(R.id.etDob)
                val etAddress = findViewById<EditText>(R.id.etAddress)
                val dob       = etDob?.text.toString().trim()
                val address   = etAddress?.text.toString().trim()

                if (emailChanged) {
                    requestEmailChangeDialog(newEmail, name, barangay, contact, btnSave, null)
                } else {
                    db.collection("users").document(uid).update(
                        mapOf(
                            "name" to name,
                            "barangay" to barangay,
                            "contact" to contact
                        )
                    ).addOnSuccessListener {
                        if (dob.isNotEmpty() || address.isNotEmpty()) {
                            db.collection("verifications").document(uid).update(
                                mapOf(
                                    "dateOfBirth" to dob,
                                    "address"     to address
                                )
                            )
                        }
                        Toast.makeText(this, "Profile updated!", Toast.LENGTH_SHORT).show()
                        finish()
                    }.addOnFailureListener {
                        btnSave.isEnabled = true
                        btnSave.text = "Save And Continue"
                        Toast.makeText(this, "Failed to update profile", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }


    private fun requestEmailChangeDialog(
        newEmail: String,
        name: String,
        barangay: String,
        contact: String,
        btnSave: AppCompatButton,
        photoUrl: String?
    ) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_password, null)
        val etPassword = dialogView.findViewById<EditText>(R.id.etConfirmEmailPassword)

        AlertDialog.Builder(this)
            .setTitle("Confirm Password")
            .setMessage("Please enter your current password to continue changing your email address.")
            .setView(dialogView)
            .setPositiveButton("Confirm") { _, _ ->
                val currentPassword = etPassword.text.toString().trim()
                if (currentPassword.isEmpty()) {
                    Toast.makeText(this, "Password is required.", Toast.LENGTH_SHORT).show()
                    btnSave.isEnabled = true
                    btnSave.text = "Save And Continue"
                } else {
                    performEmailChange(newEmail, currentPassword, name, barangay, contact, photoUrl, btnSave)
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                btnSave.isEnabled = true
                btnSave.text = "Save And Continue"
            }
            .setCancelable(false)
            .show()
    }

    private fun showAwaitingEmailVerificationDialog(newEmail: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Verify Your New Email")
            .setMessage("We sent a verification link to:\n\n$newEmail\n\nPlease check your inbox and tap the link. This will update automatically once verified.")
            .setCancelable(false)
            .create()
        dialog.show()
        startEmailVerificationPolling(dialog)
    }
    private fun startEmailVerificationPolling(dialog: AlertDialog) {
        emailPollHandler = android.os.Handler(mainLooper)
        emailPollRunnable = object : Runnable {
            override fun run() {
                if (auth.currentUser == null) {

                    onNewEmailConfirmed(dialog)
                    return
                }

                EmailSyncHelper.syncIfNeeded { didSync ->
                    if (didSync || auth.currentUser == null) {

                        onNewEmailConfirmed(dialog)
                    } else {
                        emailPollHandler?.postDelayed(this, 3000)
                    }
                }
            }
        }
        emailPollHandler?.postDelayed(emailPollRunnable!!, 3000)
    }

    private fun onNewEmailConfirmed(dialog: AlertDialog) {
        dialog.dismiss()
        stopEmailVerificationPolling()
        EmailSyncHelper.showEmailChangedDialogAndLogout(this@EditProfilePageCemo)
    }

    private fun stopEmailVerificationPolling() {
        emailPollRunnable?.let { emailPollHandler?.removeCallbacks(it) }
        emailPollHandler = null
        emailPollRunnable = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopEmailVerificationPolling()
    }

    private fun performEmailChange(
        newEmail: String,
        currentPassword: String,
        name: String,
        barangay: String,
        contact: String,
        photoUrl: String?,
        btnSave: AppCompatButton
    ) {
        val user = auth.currentUser
        val oldEmail = user?.email
        val currentUid = user?.uid

        if (user == null || oldEmail.isNullOrEmpty() || currentUid.isNullOrEmpty()) {
            Toast.makeText(this, "Unable to verify account. Please log in again.", Toast.LENGTH_SHORT).show()
            btnSave.isEnabled = true
            btnSave.text = "Save And Continue"
            return
        }

        val credential = EmailAuthProvider.getCredential(oldEmail, currentPassword)

        user.reauthenticate(credential)
            .addOnSuccessListener {
                // ── Check kung ginagamit na ng ibang account itong email ──
                db.collection("userEmails").document(newEmail).get()
                    .addOnSuccessListener { emailDoc ->
                        val ownerUid = emailDoc.getString("uid")
                        val emailTakenByOther = emailDoc.exists() && ownerUid != currentUid

                        if (emailTakenByOther) {
                            btnSave.isEnabled = true
                            btnSave.text = "Save And Continue"
                            Toast.makeText(this, "This email is already used by another account.", Toast.LENGTH_LONG).show()
                            return@addOnSuccessListener
                        }

                        user.verifyBeforeUpdateEmail(newEmail)
                            .addOnSuccessListener {
                                val updates = mutableMapOf<String, Any>(
                                    "name" to name,
                                    "barangay" to barangay,
                                    "contact" to contact,
                                    "pendingEmail" to newEmail
                                )
                                if (photoUrl != null) updates["photoUrl"] = photoUrl

                                db.collection("users").document(currentUid).update(updates.toMap())
                                    .addOnSuccessListener {
                                        showAwaitingEmailVerificationDialog(newEmail)
                                    }
                                    .addOnFailureListener {
                                        btnSave.isEnabled = true
                                        btnSave.text = "Save And Continue"
                                        Toast.makeText(this, "Failed to update profile", Toast.LENGTH_SHORT).show()
                                    }
                            }
                            .addOnFailureListener { e ->
                                btnSave.isEnabled = true
                                btnSave.text = "Save And Continue"
                                Toast.makeText(this, "Failed to send verification: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    }
                    .addOnFailureListener { e ->
                        btnSave.isEnabled = true
                        btnSave.text = "Save And Continue"
                        Toast.makeText(this, "Unable to verify email: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener {
                btnSave.isEnabled = true
                btnSave.text = "Save And Continue"
                Toast.makeText(this, "Current password is incorrect.", Toast.LENGTH_SHORT).show()
            }
    }
}


