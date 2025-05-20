package com.myapp.centsibleai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.myapp.centsibleai.databinding.ActivitySignUpBinding


class SignUp : AppCompatActivity() {

    private lateinit var firebaseAuth: FirebaseAuth //untuk autentikasi firebase
    private lateinit var binding: ActivitySignUpBinding

    private fun sendWelcomeNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "welcome_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Welcome Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Welcome notifications for new users"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with your logo/icon
            .setContentTitle("Welcome to CentSible AI 🎉")
            .setContentText("We're excited to help you take control of your finances!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        notificationManager.notify(100, builder.build())
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)


        firebaseAuth = FirebaseAuth.getInstance()

        binding.signupBtn.setOnClickListener { //when the sign up button get click

            val email = binding.email.text.toString()
            val pass = binding.password.text.toString()
            val confirmPass = binding.passwordRetype.text.toString()

            if (email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()){ //checking is the email match or not
                if(pass.isNotEmpty() && confirmPass.isNotEmpty()){
                    if (pass == confirmPass){
                        binding.progressBar.visibility = View.VISIBLE //show loading progress bar
                        firebaseAuth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener {
                            if (it.isSuccessful){
                                sendWelcomeNotification()//if the sign up succesful then change activity to main activity
                                val intent = Intent(this, MainActivity::class.java)
                                Toast.makeText(this, "Sign Up Successful", Toast.LENGTH_LONG).show()
                                binding.progressBar.visibility = View.GONE
                                startActivity(intent)
                            }else{ // jika gagal maka tampilkan pesan error
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this, it.exception.toString(), Toast.LENGTH_LONG).show()
                            }
                        }
                    }else{
                        Toast.makeText(this, "Password id not Matching", Toast.LENGTH_LONG).show()
                    }
                }else{
                    Toast.makeText(this, "Empty Fields Are no Allowed", Toast.LENGTH_LONG).show()
                }
            }else{
                Toast.makeText(this, "Invalid or Empty Email", Toast.LENGTH_LONG).show()
            }
        }

        binding.haveAccount.setOnClickListener {
            finish()
        }

    }
}