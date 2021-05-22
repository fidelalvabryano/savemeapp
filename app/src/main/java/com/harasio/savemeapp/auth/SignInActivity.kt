package com.harasio.savemeapp.auth

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import com.harasio.savemeapp.BottomNavActivity
import com.harasio.savemeapp.R
import com.harasio.savemeapp.User
import kotlinx.android.synthetic.main.activity_register.*
import kotlinx.android.synthetic.main.activity_sign_in.*

class SignInActivity : AppCompatActivity() {
    companion object {
        private const val RC_SIGN_IN = 120
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var database: FirebaseDatabase
    private lateinit var myRef: DatabaseReference


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in)
        supportActionBar?.hide()

        database = FirebaseDatabase.getInstance("https://b21-cap0083-default-rtdb.asia-southeast1.firebasedatabase.app/")
        myRef = database.getReference("users")

        // Configure Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        //Firebase Auth instance
        auth = FirebaseAuth.getInstance()

        btn_sign_in_google.setOnClickListener {
            signInGoogle()
        }
        btn_sign_in.setOnClickListener(){
            doLogin()
        }
        btn_forgotpassword.setOnClickListener{
            forgotpassword()
        }
        btn_registerpage.setOnClickListener{
            register()
            finish()
        }
    }

    private fun doLogin() {
        if (et_email_login.text.toString().isEmpty()){
            et_email_login.error = "Email Address must be filled"
            et_email_login.requestFocus()
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(et_email_login.text.toString()).matches()){
            et_email_login.error = "Email Address is not valid"
            et_email_login.requestFocus()
            return
        }
        if (et_password_login.text.toString().isEmpty()){
            et_password_login.error = "Password must be filled"
            et_password_login.requestFocus()
            return
        }

        auth.signInWithEmailAndPassword(et_email_login.text.toString(), et_password_login.text.toString())
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // Sign in success, update UI with the signed-in user's information
                        val user = auth.currentUser
                        updateUI(user)
                    } else {
                        // If sign in fails, display a message to the user.
                        updateUI(null)
                    }
                }
    }

    private fun signInGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    private fun register() {
        val registerIntent = Intent(this@SignInActivity, RegisterActivity::class.java)
        startActivity(registerIntent)
    }

    private fun forgotpassword() {
        val forgotpasswordIntent = Intent(this@SignInActivity, ForgotPasswordActivity::class.java)
        startActivity(forgotpasswordIntent)
    }

    public override fun onStart() {
        super.onStart()
        // Check if user is signed in (non-null) and update UI accordingly.
        val currentUser = auth.currentUser
        //updateUI(currentUser)
    }

    private fun updateUI(currentUser: FirebaseUser?){
        if (currentUser != null){
            if (currentUser.isEmailVerified){
                retrieveAndStoreToken()
                startActivity(Intent(this, BottomNavActivity::class.java))
                finish()
            } else {
                Toast.makeText(baseContext, "Please verify your email address",
                        Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(baseContext, "Login failed.",
                    Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val exception = task.exception
            if (task.isSuccessful) {
                try {
                    // Google Sign In was successful, authenticate with Firebase
                    val account = task.getResult(ApiException::class.java)!!
                    Log.d("SignInActivity", "firebaseAuthWithGoogle:" + account.id)
                    firebaseAuthWithGoogle(account.idToken!!)
                } catch (e: ApiException) {
                    // Google Sign In failed, update UI appropriately
                    Log.w("SignInActivity", "Google sign in failed", e)
                }
            } else {
                Log.w("SignInActivity", exception.toString())
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // Sign in success, update UI with the signed-in user's information
                        Log.d("SignInActivity", "signInWithCredential:success")
                        saveData()
                        retrieveAndStoreToken()
                        val intent = Intent(this, BottomNavActivity::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        // If sign in fails, display a message to the user.
                        Log.d("SignInActivity", "signInWithCredential:failure")
                    }
                }
    }

    private fun saveData() {
        val name = auth.currentUser?.displayName
        val email = auth.currentUser?.email
        val uid = auth.currentUser?.uid

        val user = User(name, email, uid)
        if (uid != null) {
            myRef.child(uid).setValue(user).addOnCompleteListener{
                Toast.makeText(this, "Success!", Toast.LENGTH_SHORT).show()
            }.addOnFailureListener{
                Toast.makeText(this, "Failed!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun retrieveAndStoreToken() {
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token: String? = task.result
                    val userId: String? = auth.currentUser?.uid
                    if (userId != null) {
                        FirebaseDatabase.getInstance("https://b21-cap0083-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference("tokens").child(userId).setValue(token)
                    }
                }
            }
    }
}