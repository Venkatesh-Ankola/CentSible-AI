package com.myapp.catatuang

import org.json.JSONObject
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DatabaseReference
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.ktx.Firebase
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
import java.io.File
import android.content.pm.PackageManager
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.RelativeLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat


class PhotoUploadActivity : AppCompatActivity() {

    private val REQUEST_IMAGE_CAPTURE = 101
    private val REQUEST_IMAGE_PICK = 100
    private lateinit var photoURI: Uri
    private val CAMERA_PERMISSION_CODE = 200
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var recurringCheckBox: CheckBox





    private lateinit var btnSelectPhoto: Button
    private lateinit var imageViewPreview: ImageView
    private var selectedImageUri: Uri? = null
    private lateinit var chatgptResult: TextView
    private lateinit var saveChatGPTResultButton: Button

    private lateinit var dbRef: DatabaseReference //initialize database
    private lateinit var auth: FirebaseAuth
    // Variables to store parsed data
    private var amount: Double = 0.0
    private var category: String = ""
    private var title: String = ""
    private lateinit var btncross : ImageButton

    private lateinit var amountEditText: EditText
    private lateinit var categoryAutoCompleteTextView: AutoCompleteTextView
    private lateinit var titleEditText: EditText


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_upload)

        categoryAutoCompleteTextView = findViewById(R.id.category)

        // Initialize views
        btnSelectPhoto = findViewById(R.id.btnSelectPhoto)
        imageViewPreview = findViewById(R.id.imageViewPreview)
//        chatgptResult = findViewById(R.id.chatgptResult)
        saveChatGPTResultButton = findViewById(R.id.saveChatGPTResultButton)
        recurringCheckBox = findViewById(R.id.recurringCheckBox)
        recurringCheckBox.isChecked = false

        btnSelectPhoto.setOnClickListener {
            val options = arrayOf("Take Photo", "Choose from Gallery")
            val builder = android.app.AlertDialog.Builder(this)
            builder.setTitle("Select Option")
            builder.setItems(options) { dialog, which ->
                when (which) {
                    0 -> openCamera()
                    1 -> openGallery()
                }
            }
            builder.show()
        }


        btncross = findViewById(R.id.backBtn)

        btncross.setOnClickListener{
            intent = Intent(this, MainActivity :: class.java )
            startActivity(intent)
        }

        amountEditText = findViewById(R.id.amount)
        categoryAutoCompleteTextView = findViewById(R.id.category)
        titleEditText = findViewById(R.id.title)

        val expenseCategories = CategoryOptions.expenseCategory()
        adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            expenseCategories
        )
        categoryAutoCompleteTextView.setAdapter(adapter)
        categoryAutoCompleteTextView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                categoryAutoCompleteTextView.showDropDown()
            }
        }
        categoryAutoCompleteTextView.setOnClickListener {
            categoryAutoCompleteTextView.showDropDown()
        }



        saveChatGPTResultButton.setOnClickListener {
            if (amount != 0.0 && category.isNotEmpty() && title.isNotEmpty()) {
                saveChatGPTTransaction()
            } else {
                Toast.makeText(this, "Invalid ChatGPT data. Cannot save.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
        } else {
            launchCameraIntent()
        }
    }

    private fun launchCameraIntent() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val photoFile = File.createTempFile("IMG_", ".jpg", externalCacheDir)
        photoURI = FileProvider.getUriForFile(this, "$packageName.provider", photoFile)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
        startActivityForResult(intent, REQUEST_IMAGE_CAPTURE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchCameraIntent()
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }




    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_IMAGE_PICK)
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_PICK -> {
                    selectedImageUri = data?.data
                    selectedImageUri?.let {
                        imageViewPreview.setImageURI(it)
                        processImage(it)
                    }
                }
                REQUEST_IMAGE_CAPTURE -> {
                    selectedImageUri = photoURI
                    imageViewPreview.setImageURI(photoURI)
                    processImage(photoURI)
                }
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
            Amount, Category, Title
            
            I need it in the format:
            Amount:xxx, Category:xxx, Title:xxx
            
            Do not give multiple values.

            Here is the text:
            $extractedText
        """.trimIndent()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response: HttpResponse = client.post("https://api.openai.com/v1/chat/completions") {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer sk-2dzxM6-wwKOkqyePIBDkdX06yLDMAqxmekWz-VYIjUT3BlbkFJrxgwPqpUlw-5V52Au6ZOIvEt6YVfpf4GlfEIXtmuwA") // Replace with your API key
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
                val parsedResult = parseChatGPTResponse(responseBody)
                withContext(Dispatchers.Main) {
                    Log.d("ChatGPTResponse", "Response: $responseBody")
                    if (parsedResult != null) {
                        amount = parsedResult.first.toDoubleOrNull() ?: 0.0
                        category = parsedResult.second
                        title = parsedResult.third

                        amountEditText.setText(amount.toString())
                        categoryAutoCompleteTextView.setText(category, false)
                        titleEditText.setText(title)
                        amountEditText.visibility = EditText.VISIBLE
                        categoryAutoCompleteTextView.setAdapter(adapter)
                        categoryAutoCompleteTextView.visibility = AutoCompleteTextView.VISIBLE
                        titleEditText.visibility = EditText.VISIBLE
                        recurringCheckBox.visibility = CheckBox.VISIBLE
                        val layoutParams = recurringCheckBox.layoutParams as RelativeLayout.LayoutParams
                        layoutParams.addRule(RelativeLayout.BELOW, R.id.categoryTIL)
                        recurringCheckBox.layoutParams = layoutParams


//                        chatgptResult.text = "Amount: $amount\nCategory: $category\nTitle: $title"
//                        chatgptResult.visibility = TextView.VISIBLE
                        saveChatGPTResultButton.visibility = Button.VISIBLE
                        val saveParams = saveChatGPTResultButton.layoutParams as RelativeLayout.LayoutParams
                        saveParams.addRule(RelativeLayout.BELOW, R.id.recurringCheckBox)
                        saveChatGPTResultButton.layoutParams = saveParams
                    } else {
                        Log.e("ChatGPTParsing", "Failed to parse ChatGPT response")
                    }
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

    override fun onBackPressed() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }


    private fun parseChatGPTResponse(responseBody: String): Triple<String, String, String>? {
        return try {
            val jsonObject = JSONObject(responseBody)
            val choicesArray = jsonObject.getJSONArray("choices")
            val messageObject = choicesArray.getJSONObject(0).getJSONObject("message")
            val content = messageObject.getString("content")

            val regex = Regex("Amount:\\s*\\$?(\\d+(\\.\\d+)?),\\s*Category:\\s*([^,]+),\\s*Title:\\s*(.+)")
            val matchResult = regex.find(content)

            if (matchResult != null) {
                val amount = matchResult.groupValues[1]
                val category = matchResult.groupValues[3]
                val title = matchResult.groupValues[4]
                Triple(amount, category, title)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun saveChatGPTTransaction() {
        if (amount == 0.0 || category.isEmpty() || title.isEmpty()) {
            Toast.makeText(this, "Missing required fields", Toast.LENGTH_SHORT).show()
            return
        }
        val user = Firebase.auth.currentUser
        val uid = user?.uid
        if (uid != null) {
            dbRef = FirebaseDatabase.getInstance().getReference(uid) //initialize database with uid as the parent
        }
        auth = Firebase.auth

        val transactionID = dbRef.push().key!!

        amount = amountEditText.text.toString().toDouble()
        val updatedTitle = titleEditText.text.toString()
        val updatedCategory = categoryAutoCompleteTextView.text.toString()
        val updatedAmount = amountEditText.text.toString().toDoubleOrNull() ?: 0.0

        val transactionData = mapOf(
            "transactionID" to transactionID,
            "type" to 1,
            "title" to updatedTitle,
            "category" to updatedCategory,
            "amount" to updatedAmount,
            "date" to System.currentTimeMillis(),
            "note" to "",
            "recurring" to recurringCheckBox.isChecked,
            "invertedDate" to -System.currentTimeMillis()
        )

        dbRef.child(transactionID).setValue(transactionData)
            .addOnCompleteListener {
                Toast.makeText(this, "ChatGPT Transaction Saved Successfully", Toast.LENGTH_LONG).show()
                val intent = Intent(this, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { err ->
                Toast.makeText(this, "Error saving transaction: ${err.message}", Toast.LENGTH_LONG).show()
            }
    }
}
