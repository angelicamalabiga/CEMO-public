package com.example.cemo_loginpage

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.Calendar
import java.util.UUID
import android.text.Editable
import android.text.TextWatcher

@Suppress("DEPRECATION")
class VerificationFormPageCemo : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    private var selectedIdUri: Uri? = null

    private lateinit var spinnerBarangay: Spinner


    companion object {
        private const val REQUEST_PICK_IMAGE = 1001
    }

    // --- ID Types accepted as proof of Marikina residency ---
    private val idTypes = arrayOf(
        "Select ID type…",
        "Barangay ID / Clearance",
        "Voter's ID",
        "PhilSys (National ID)",
        "Driver's License",
        "Postal ID",
        "Senior Citizen ID",
        "PWD ID",
        "PRC ID",
        "Passport",
        "SSS / GSIS ID",
        "PhilHealth ID",
        "Other Government ID"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.statusBarColor = "#FFFFFF".toColorInt()
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        setContentView(R.layout.activity_verification_form)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        auth    = FirebaseAuth.getInstance()
        db      = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        // Back
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        // Pre-fill name / contact from Firestore if available
        prefillFromProfile()

        // Date of Birth picker
        val etDob = findViewById<EditText>(R.id.etDateOfBirth)
        etDob.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        etDob.addTextChangedListener(object : android.text.TextWatcher {
            var isFormatting = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (isFormatting) return
                isFormatting = true

                val digits = s.toString().replace("/", "")
                val formatted = StringBuilder()

                for (i in digits.indices) {
                    if (i == 2 || i == 4) formatted.append("/")
                    if (i < 8) formatted.append(digits[i])
                }

                etDob.setText(formatted.toString())
                etDob.setSelection(formatted.length)
                isFormatting = false
            }
        })
        etDob.setOnClickListener { showDatePicker(etDob) }

        // Full Name - letters only
        val etFullName = findViewById<EditText>(R.id.etFullName)
        etFullName.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val filtered = s.toString().replace(Regex("[^a-zA-Z ñÑ.]"), "")
                if (filtered != s.toString()) {
                    etFullName.removeTextChangedListener(this)
                    etFullName.setText(filtered)
                    etFullName.setSelection(filtered.length)
                    etFullName.addTextChangedListener(this)
                }
            }
        })

        val etContact = findViewById<EditText>(R.id.etContact)
        etContact.filters = arrayOf(android.text.InputFilter.LengthFilter(11))
        etContact.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val filtered = s.toString().replace(Regex("[^0-9]"), "")
                if (filtered != s.toString()) {
                    etContact.setText(filtered)
                    etContact.setSelection(filtered.length)
                }
            }
        })

        // ID Type Spinner
        val spinner = findViewById<Spinner>(R.id.spinnerIdType)
        spinnerBarangay = findViewById(R.id.spinnerBarangay)
        val barangayList = resources.getStringArray(R.array.baranggay_list)
        val barangayAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, barangayList)
        barangayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerBarangay.adapter = barangayAdapter


        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, idTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        // Upload area
        val layoutUpload      = findViewById<LinearLayout>(R.id.layoutUploadId)
        val ivPreview         = findViewById<ImageView>(R.id.ivIdPreview)
        val layoutPlaceholder = findViewById<LinearLayout>(R.id.layoutUploadPlaceholder)
        val tvChangePhoto     = findViewById<TextView>(R.id.tvChangePhoto)

        layoutUpload.setOnClickListener { openImagePicker() }
        tvChangePhoto.setOnClickListener { openImagePicker() }

        // Submit
        val btnSubmit = findViewById<Button>(R.id.btnSubmitVerification)
        btnSubmit.setOnClickListener {
            val fullName  = findViewById<EditText>(R.id.etFullName).text.toString().trim()
            val dob       = etDob.text.toString().trim()
            val contact   = findViewById<EditText>(R.id.etContact).text.toString().trim()
            val barangay = spinnerBarangay.selectedItem.toString()
            val address   = findViewById<EditText>(R.id.etAddress).text.toString().trim()
            val idType    = spinner.selectedItem.toString()
            val consent   = findViewById<CheckBox>(R.id.checkConsent).isChecked

            // --- Validation ---
            if (fullName.isEmpty())  { toast("Please enter your full name.");      return@setOnClickListener }
            if (dob.isEmpty())       { toast("Please select your date of birth."); return@setOnClickListener }
            if (contact.isEmpty())   { toast("Please enter your contact number."); return@setOnClickListener }
            if (barangay == "[Select Baranggay]") { toast("Please select your barangay."); return@setOnClickListener }
            if (address.isEmpty())   { toast("Please enter your complete address."); return@setOnClickListener }
            if (idType == idTypes[0]) { toast("Please select an ID type.");       return@setOnClickListener }
            if (selectedIdUri == null) { toast("Please upload a photo of your ID."); return@setOnClickListener }
            if (!consent) { toast("Please accept the consent statement to continue."); return@setOnClickListener }

            btnSubmit.isEnabled = false
            btnSubmit.text = "Uploading…"

            uploadIdAndSave(fullName, dob, contact, barangay, address, idType, btnSubmit)
        }
    }

    // --- Pre-fill editable fields from existing profile ---
    private fun prefillFromProfile() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    val name    = doc.getString("name")    ?: ""
                    val contact = doc.getString("contact") ?: ""
                    val brgy    = doc.getString("barangay") ?: ""

                    findViewById<EditText>(R.id.etFullName).setText(name)
                    findViewById<EditText>(R.id.etContact).setText(contact)
                    val position = resources.getStringArray(R.array.baranggay_list).indexOf(brgy)
                    if (position >= 0) spinnerBarangay.setSelection(position)                }
            }
    }

    // --- Date Picker ---
    private fun showDatePicker(target: EditText) {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                target.setText(String.format("%02d/%02d/%04d", month + 1, day, year))
            },
            cal.get(Calendar.YEAR) - 18,  // Default: 18 years ago
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // --- Image Picker ---
    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
        startActivityForResult(Intent.createChooser(intent, "Select ID Photo"), REQUEST_PICK_IMAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                selectedIdUri = uri

                val ivPreview         = findViewById<ImageView>(R.id.ivIdPreview)
                val layoutPlaceholder = findViewById<LinearLayout>(R.id.layoutUploadPlaceholder)
                val tvChangePhoto     = findViewById<TextView>(R.id.tvChangePhoto)

                ivPreview.setImageURI(uri)
                ivPreview.visibility         = View.VISIBLE
                layoutPlaceholder.visibility = View.GONE
                tvChangePhoto.visibility     = View.VISIBLE
            }
        }
    }

    // --- Upload ID photo to Firebase Storage, then save record to Firestore ---
    private fun uploadIdAndSave(
        fullName: String,
        dob: String,
        contact: String,
        barangay: String,
        address: String,
        idType: String,
        btnSubmit: Button
    ) {
        val uid      = auth.currentUser?.uid ?: return
        val fileName = "id_${uid}_${UUID.randomUUID()}.jpg"
        val ref      = storage.reference.child("verifications/$uid/$fileName")

        ref.putFile(selectedIdUri!!)
            .addOnProgressListener { snap ->
                val pct = (100.0 * snap.bytesTransferred / snap.totalByteCount).toInt()
                btnSubmit.text = "Uploading… $pct%"
            }
            .addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { downloadUri ->
                    saveVerificationRecord(
                        uid, fullName, dob, contact, barangay, address,
                        idType, downloadUri.toString(), btnSubmit
                    )
                }
            }
            .addOnFailureListener {
                btnSubmit.isEnabled = true
                btnSubmit.text = "Submit for Verification"
                toast("Upload failed. Please check your connection and try again.")
            }
    }

    // --- Save verification record to Firestore ---
    private fun saveVerificationRecord(
        uid: String,
        fullName: String,
        dob: String,
        contact: String,
        barangay: String,
        address: String,
        idType: String,
        idPhotoUrl: String,
        btnSubmit: Button
    ) {
        val record = hashMapOf(
            "uid"              to uid,
            "fullName"         to fullName,
            "dateOfBirth"      to dob,
            "contact"          to contact,
            "barangay"         to barangay,
            "address"          to address,
            "idType"           to idType,
            "idPhotoUrl"       to idPhotoUrl,
            "status"           to "pending",   // pending | verified | rejected
            "submittedAt"      to com.google.firebase.Timestamp.now(),
            "rejectedAt"       to null
        )

        // Store in dedicated verifications collection
        db.collection("verifications").document(uid)
            .set(record)
            .addOnSuccessListener {
                // Also update verificationStatus field on the user document
                db.collection("users").document(uid)
                    .update(
                        mapOf(
                            "verificationStatus" to "pending",
                            "rejectionNotified"  to false
                        )
                    )
                    .addOnSuccessListener {
                        btnSubmit.isEnabled = true
                        btnSubmit.text = "Submit for Verification"
                        showSuccessAndFinish()
                    }
            }
            .addOnFailureListener {
                btnSubmit.isEnabled = true
                btnSubmit.text = "Submit for Verification"
                toast("Something went wrong saving your data. Please try again.")
            }
    }

    private fun showSuccessAndFinish() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Submission Received!")
            .setMessage("Thank you! Your ID has been submitted for verification. Our team will review it within 1–3 business days. We'll notify you once the review is complete.")
            .setPositiveButton("OK") { _, _ ->
                // Go back all the way to Settings
                val intent = Intent(this, SettingsPageCemo::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                startActivity(intent)
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}