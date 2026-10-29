    package com.example.cemo_loginpage

    import android.content.Context
    import android.content.Intent
    import androidx.appcompat.app.AlertDialog
    import androidx.appcompat.app.AppCompatActivity
    import com.google.firebase.auth.FirebaseAuth
    import com.google.firebase.firestore.FieldValue
    import com.google.firebase.firestore.FirebaseFirestore

    /**
     * Checks if the user clicked the verification link sent to their new email.
     * If Firebase Auth's email now matches "pendingEmail" in Firestore, syncs it
     * into the "email" field, clears "pendingEmail", and updates the userEmails index.
     *
     * Call this BEFORE loading/displaying user data on any screen that shows the
     * user's email, so the UI never shows a stale value.
     *
     * onComplete receives `didSync = true` if the pending email was JUST confirmed
     * and applied during this call (i.e. the user's email changed just now), or
     * `false` if there was nothing to sync. Callers should use `didSync` to decide
     * whether to force a logout (see [showEmailChangedDialogAndLogout]) instead of
     * loading data as normal.
     */
    object EmailSyncHelper {

        fun syncIfNeeded(onComplete: (didSync: Boolean) -> Unit) {
            val auth = FirebaseAuth.getInstance()
            val db = FirebaseFirestore.getInstance()
            val uid = auth.currentUser?.uid

            if (uid == null) {
                onComplete(false)
                return
            }

            auth.currentUser?.reload()?.addOnCompleteListener {
                val currentAuthEmail = auth.currentUser?.email

                if (currentAuthEmail.isNullOrEmpty()) {
                    onComplete(false)
                    return@addOnCompleteListener
                }

                db.collection("users").document(uid).get()
                    .addOnSuccessListener { doc ->
                        val pendingEmail = doc.getString("pendingEmail")
                        val oldEmail = doc.getString("email")

                        android.util.Log.d("EmailSyncHelper", "currentAuthEmail=$currentAuthEmail, pendingEmail=$pendingEmail, oldEmail=$oldEmail")

                        if (!pendingEmail.isNullOrEmpty() && pendingEmail == currentAuthEmail) {
                            db.collection("users").document(uid).update(
                                mapOf(
                                    "email" to currentAuthEmail,
                                    "pendingEmail" to FieldValue.delete()
                                )
                            ).addOnSuccessListener {
                                db.collection("userEmails").document(currentAuthEmail)
                                    .set(mapOf("uid" to uid))
                                if (!oldEmail.isNullOrEmpty() && oldEmail != currentAuthEmail) {
                                    db.collection("userEmails").document(oldEmail).delete()
                                }
                                onComplete(true)
                            }.addOnFailureListener {
                                // Couldn't finish the sync record — don't force a logout
                                // on a failed write, just let the caller proceed normally.
                                onComplete(false)
                            }
                        } else {
                            onComplete(false)
                        }
                    }
                    .addOnFailureListener {
                        onComplete(false)
                    }
            } ?: onComplete(false)
        }

        /**
         * Shows the "Email Updated Successfully" confirmation, then signs the user
         * out and sends them back to the Login screen. Call this from any screen's
         * syncIfNeeded callback when `didSync == true`.
         */
        fun showEmailChangedDialogAndLogout(activity: AppCompatActivity) {
            AlertDialog.Builder(activity)
                .setTitle("Email Updated Successfully")
                .setMessage("Your email address has been changed. Please log in again using your new email address.")
                .setPositiveButton("OK") { _, _ ->
                    logoutAndRedirectToLogin(activity)
                }
                .setCancelable(false)
                .show()
        }

        private fun logoutAndRedirectToLogin(activity: AppCompatActivity) {
            FirebaseAuth.getInstance().signOut()

            activity.getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)
                .edit()
                .remove("loginTimestamp")
                .apply()

            val intent = Intent(activity, LoginPageCemo::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            activity.startActivity(intent)
            activity.finish()
        }
    }