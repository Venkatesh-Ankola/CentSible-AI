package com.myapp.catatuang

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.gson.gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhotoUploadActivity : AppCompatActivity() {

    private lateinit var btnSelectPhoto: Button
    private lateinit var imageViewPreview: ImageView
    private var selectedImageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_upload)

        // Initialize views
        btnSelectPhoto = findViewById(R.id.btnSelectPhoto)
        imageViewPreview = findViewById(R.id.imageViewPreview)

        // Set click listener for selecting a photo
        btnSelectPhoto.setOnClickListener {
            openGallery()
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, 100)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == Activity.RESULT_OK && data != null) {
            selectedImageUri = data.data
            imageViewPreview.setImageURI(selectedImageUri) // Display the selected image
            selectedImageUri?.let {
                processImage(it)
            }
        } else {
            Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processImage(uri: Uri) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val extractedText = visionText.text
                    if (extractedText.isNotEmpty()) {
                        Toast.makeText(this, "Text: $extractedText", Toast.LENGTH_LONG).show()
                        sendToChatGPT(extractedText)
                    } else {
                        Toast.makeText(this, "No text detected", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                    Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendToChatGPT(extractedText: String) {
        val client = HttpClient(CIO) {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                gson()
            }
        }

        val prompt = """
            Extract the following details from the text:
            - Amount
            - Category
            - Title

            Here is the text:
            $extractedText
        """.trimIndent()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response: HttpResponse = client.post("https://api.openai.com/v1/chat/completions") {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer sk-2dzxM6-wwKOkqyePIBDkdX06yLDMAqxmekWz-VYIjUT3BlbkFJrxgwPqpUlw-5V52Au6ZOIvEt6YVfpf4GlfEIXtmuwA")
                    setBody(
                        mapOf(
                            "model" to "gpt-3.5-turbo",
                            "messages" to listOf(
                                mapOf(
                                    "role" to "user",
                                    "content" to prompt
                                )
                            ),
                            "max_tokens" to 150,
                            "temperature" to 0.5
                        )
                    )
                }

                val responseBody: String = response.body()
                withContext(Dispatchers.Main) {
//                    Snackbar.make(findViewById(android.R.id.content), "Response: $responseBody", Snackbar.LENGTH_LONG).show()

                    Log.d("ChatGPTResponse", "Response: $responseBody") // For successful responses
//                    Log.e("ChatGPTError", "Error: ${e.message}") // For errors

                    Toast.makeText(this@PhotoUploadActivity, "Response: $responseBody", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PhotoUploadActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                client.close()
            }
        }
    }
}
