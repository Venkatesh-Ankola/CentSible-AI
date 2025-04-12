package com.myapp.centsibleai

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.myapp.centsibleai.R

class SmsAdapter(
    private val items: List<SmsModel>,
    private val onAddClick: (SmsModel) -> Unit
) : RecyclerView.Adapter<SmsAdapter.SmsViewHolder>() {

    inner class SmsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val bodyText: TextView = itemView.findViewById(R.id.smsBody)
        val amountText: TextView = itemView.findViewById(R.id.smsAmount)
        val addButton: Button = itemView.findViewById(R.id.addBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SmsViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sms, parent, false)
        return SmsViewHolder(view)
    }

    override fun onBindViewHolder(holder: SmsViewHolder, position: Int) {
        val sms = items[position]
        holder.bodyText.text = sms.body
        holder.amountText.text = "₹${sms.amount}"
        holder.addButton.setOnClickListener {
            onAddClick(sms)
        }
    }

    override fun getItemCount() = items.size
}
