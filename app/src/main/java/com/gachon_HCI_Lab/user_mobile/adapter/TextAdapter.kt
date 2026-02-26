package com.gachon_HCI_Lab.user_mobile.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gachon_HCI_Lab.user_mobile.R

// 리사이클러 어댑터
class TextAdapter(private val textList: MutableList<String>) : RecyclerView.Adapter<TextAdapter.TextViewHolder>() {

    inner class TextViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView.findViewById(R.id.textLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TextViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_text, parent, false)
        return TextViewHolder(view)
    }

    override fun onBindViewHolder(holder: TextViewHolder, position: Int) {
        holder.textView.text = textList[position]
    }

    override fun getItemCount() = textList.size
}