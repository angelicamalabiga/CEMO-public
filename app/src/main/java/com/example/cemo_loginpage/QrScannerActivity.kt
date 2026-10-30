package com.example.cemo_loginpage

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QrScannerActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private var hasScanned = false // prevent multiple triggers

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_scanner)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST
            )
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val previewView = findViewById<PreviewView>(R.id.previewView)

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImage(imageProxy)
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalyzer
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Camera error: ${e.message}", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun processImage(imageProxy: ImageProxy) {
        if (hasScanned) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val scanner = BarcodeScanning.getClient()

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    if (barcode.valueType == Barcode.TYPE_TEXT ||
                        barcode.valueType == Barcode.TYPE_URL ||
                        barcode.valueType != Barcode.TYPE_UNKNOWN) {

                        val rawValue = barcode.rawValue ?: continue
                        hasScanned = true

                        runOnUiThread {
                            try {
                                if (!rawValue.startsWith("GP:")) throw IllegalArgumentException("not a GreenPoints QR")

                                // QR payload looks like "GP:<sessionId>:<totalPoints>:<items>:<timestamp>".
                                // Only the first segment after "GP:" is the Firestore document ID —
                                // removePrefix() alone leaves the trailing ":<points>:<items>:<timestamp>"
                                // attached, which made every lookup miss and surface as "expired".
                                val payload = rawValue.removePrefix("GP:")
                                val sessionId = payload.split(":").firstOrNull()?.takeIf { it.isNotBlank() }
                                    ?: throw IllegalArgumentException("malformed GreenPoints QR")

                                claimSession(sessionId)
                            } catch (e: Exception) {
                                Toast.makeText(this, "Invalid QR code.", Toast.LENGTH_SHORT).show()
                                hasScanned = false
                            }
                        }
                        break
                    }
                }
            }
            .addOnFailureListener { /* silent */ }
            .addOnCompleteListener { imageProxy.close() }
    }
    private fun claimSession(sessionId: String) {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Not logged in.", Toast.LENGTH_SHORT).show()
            return
        }

        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val pendingRef = db.collection("pendingScans").document(sessionId)
        val userRef    = db.collection("users").document(uid)

        var capturedWeightG = 0.0   // ── captured for use in the success callback ──

        db.runTransaction { tx ->
            val pendingSnap = tx.get(pendingRef)
            // Split into distinct messages: a missing doc (bad ID, garbage-collected,
            // or a scan of someone else's/an old QR) is a different problem from a
            // QR that's valid but was already redeemed — don't collapse them into
            // one generic "expired" message, it hides real bugs.
            if (!pendingSnap.exists()) throw Exception("QR not found — it may have expired or already been used")
            if (pendingSnap.getBoolean("claimed") == true) throw Exception("This QR has already been scanned!")

            val totalPoints = pendingSnap.getDouble("total_points") ?: 0.0

            @Suppress("UNCHECKED_CAST")
            val itemsDetail = pendingSnap.get("items_detail") as? List<Map<String, Any>> ?: emptyList()

            tx.update(pendingRef, "claimed", true)

            if (itemsDetail.isEmpty()) {
                // Fallback for sessions with no item detail (shouldn't normally happen)
                val fallbackWeight = pendingSnap.getDouble("total_weight_g") ?: 0.0
                capturedWeightG = fallbackWeight

                val recordRef = userRef.collection("recyclingRecords").document(sessionId)
                tx.set(recordRef, hashMapOf(
                    "trash_type"      to "recycling_session",
                    "weight_g"        to fallbackWeight,
                    "points"          to totalPoints,
                    "remainingPoints" to totalPoints,
                    "pointsExpired"   to false,
                    "scanned_at"      to com.google.firebase.firestore.FieldValue.serverTimestamp()
                ))
            } else {
                // One record per item — exact material name, exact weight in grams,
                // exact points for that item. Nothing summed or merged.
                var weightSum = 0.0
                itemsDetail.forEachIndexed { index, item ->
                    val material = item["material"] as? String ?: "Unknown"
                    val bin      = (item["bin"] as? String)?.lowercase() ?: "unknown"
                    val weightG  = (item["weight_g"] as? Number)?.toDouble() ?: 0.0
                    val itemPts  = (item["points"] as? Number)?.toDouble() ?: 0.0
                    weightSum += weightG

                    val recordRef = userRef.collection("recyclingRecords").document("${sessionId}_$index")
                    tx.set(recordRef, hashMapOf(
                        "trash_type"      to material,
                        "bin"             to bin,
                        "weight_g"        to weightG,
                        "points"          to itemPts,
                        "remainingPoints" to itemPts,
                        "pointsExpired"   to false,
                        "scanned_at"      to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    ))
                }
                capturedWeightG = weightSum
            }

            tx.update(userRef, "greenPoints", com.google.firebase.firestore.FieldValue.increment(totalPoints))

            totalPoints
        }.addOnSuccessListener { pts ->
            Toast.makeText(this, "Scanned! +${String.format("%.2f", pts)} pts added", Toast.LENGTH_LONG).show()

            // ── Log "Recycling Recorded" notification ──
            NotificationHelper.recyclingRecorded(this, uid, pts, capturedWeightG)

            finish()
        }.addOnFailureListener { e ->
            Toast.makeText(this, e.message ?: "Failed to claim QR.", Toast.LENGTH_SHORT).show()
            hasScanned = false
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission is required to scan QR.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}