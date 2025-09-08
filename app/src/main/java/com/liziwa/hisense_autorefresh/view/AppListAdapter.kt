package com.liziwa.hisense_autorefresh.view

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.liziwa.hisense_autorefresh.databinding.ItemAppListBinding

class AppListAdapter(
    private val items: List<ListItem>,
    private val onItemCheckedChangeListener: OnItemCheckedChangeListener
) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding = ItemAppListBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        holder.binding.item = items[position]
        holder.binding.root.setOnClickListener { v ->
            onItemCheckedChangeListener.onItemCheckedChange(
                position,
                items[position],
                !items[position].isChecked
            )
        }
        holder.binding.executePendingBindings()
    }

    override fun getItemCount() = items.size

    interface OnItemCheckedChangeListener {
        fun onItemCheckedChange(position: Int, item: ListItem, checked: Boolean)
    }

    class ViewHolder(val binding: ItemAppListBinding) : RecyclerView.ViewHolder(binding.root)

    data class ListItem(
        val title: String,
        val pkg: String,
        val icon: Drawable,
        var isChecked: Boolean = false
    )
}