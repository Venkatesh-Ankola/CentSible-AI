package com.myapp.centsibleai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.ktx.Firebase
import com.myapp.centsibleai.R

class SmsImportActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SmsAdapter
    private val smsList = mutableListOf<SmsModel>()
    private lateinit var btncross : ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sms_import)

        btncross = findViewById(R.id.backBtn)

        btncross.setOnClickListener{
            intent = Intent(this, MainActivity :: class.java )
            startActivity(intent)
        }

        recyclerView = findViewById(R.id.smsRecycler)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = SmsAdapter(smsList) { sms -> showAddDialog(sms) }
        recyclerView.adapter = adapter

        checkAndLoadSms()
    }

    private fun checkAndLoadSms() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_SMS), 103)
        } else {
            loadTransactionSms()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 103 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadTransactionSms()
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadTransactionSms() {
        val cursor = contentResolver.query(
            Uri.parse("content://sms/inbox"),
            arrayOf("address", "body", "date"),
            null,
            null,
            "date DESC"
        )

        cursor?.use {
            while (it.moveToNext() && smsList.size < 20) {
                val body = it.getString(it.getColumnIndexOrThrow("body"))
                val sender = it.getString(it.getColumnIndexOrThrow("address"))
                val date = it.getLong(it.getColumnIndexOrThrow("date"))

                val type = when {
                    body.contains("credited", true) || body.contains("received", true) -> 0 // Income
                    body.contains("debited", true) || body.contains("purchase", true) -> 1 // Expense
                    else -> continue
                }

                val amount = extractAmount(body)
                if (amount != null) {
                    smsList.add(SmsModel(sender, body, amount, date, type)) // include type
                }
            }
            adapter.notifyDataSetChanged()
        }
    }


    private fun extractAmount(body: String): Double? {
        val regex = Regex("(INR|Rs\\.?|₹)\\s?([0-9,]+\\.?[0-9]*)")
        val match = regex.find(body)
        return match?.groups?.get(2)?.value?.replace(",", "")?.toDoubleOrNull()
    }

    override fun onBackPressed() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }

    private fun showAddDialog(sms: SmsModel) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_sms, null)

        val amountEditText = dialogView.findViewById<EditText>(R.id.dialogAmount)
        val titleEditText = dialogView.findViewById<EditText>(R.id.dialogTitle)
        val categoryEditText = dialogView.findViewById<EditText>(R.id.dialogCategory)
        val noteEditText = dialogView.findViewById<EditText>(R.id.dialogNote)
        val categoryDropdown = dialogView.findViewById<AutoCompleteTextView>(R.id.dialogCategory)
        val typeDropdown = dialogView.findViewById<AutoCompleteTextView>(R.id.dialogType) // ADD THIS

        // Set category dropdown
        val categoryAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            CategoryOptions.expenseCategory()
        )
        categoryDropdown.setAdapter(categoryAdapter)

        // Set type dropdown
        val typeAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            listOf("Expense", "Income")
        )
        typeDropdown.setAdapter(typeAdapter)
        typeDropdown.setText(if (sms.type == 1) "Expense" else "Income", false)

        // Prefill extracted values
        amountEditText.setText(sms.amount.toString())
        titleEditText.setText("SMS Transaction")
        categoryDropdown.setText("Banking", false)
        noteEditText.setText(sms.body.take(80))

        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Add Transaction")
            .setView(dialogView)
            .create()

        dialogView.findViewById<Button>(R.id.dialogAddButton).setOnClickListener {
            val title = titleEditText.text.toString()
            val category = categoryEditText.text.toString()
            val note = noteEditText.text.toString()
            val amount = amountEditText.text.toString().toDoubleOrNull() ?: 0.0
            val date = sms.date
            val invertedDate = date * -1

            // GET TYPE FROM DROPDOWN
            val typeStr = typeDropdown.text.toString()
            val type = if (typeStr == "Income") 0 else 1

            val transactionId = FirebaseDatabase.getInstance().reference.push().key ?: return@setOnClickListener
            val user = Firebase.auth.currentUser ?: return@setOnClickListener
            val model = TransactionModel(transactionId, type, title, category, amount, date, note, invertedDate, false)

            FirebaseDatabase.getInstance().getReference(user.uid)
                .child(transactionId)
                .setValue(model)
                .addOnSuccessListener {
                    Toast.makeText(this, "Added successfully", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }

        dialog.show()
    }


}
