package com.liziwa.hisense_autorefresh.view

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.elvishew.xlog.XLog
import com.liziwa.hisense_autorefresh.databinding.ItemAppListBinding

/**
 * 应用选择列表适配器（DataBinding 版）。
 * 点击整行即切换该应用的选中状态，并通过回调上抛新状态给 Activity 持久化。
 *
 * @param items 应用列表项（title/pkg/icon/isChecked）
 * @param onItemCheckedChangeListener 选中状态变化回调
 */
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
        // 整行点击：取反当前选中态并回传
        holder.binding.root.setOnClickListener { v ->
            XLog.d("AppListAdapter: 点击 position=$position, pkg=${items[position].pkg}, newChecked=${!items[position].isChecked}")
            onItemCheckedChangeListener.onItemCheckedChange(
                position,
                items[position],
                !items[position].isChecked
            )
        }
        holder.binding.executePendingBindings()
    }

    override fun getItemCount() = items.size

    /** 选中状态变化监听：position 项被切换为 checked */
    interface OnItemCheckedChangeListener {
        fun onItemCheckedChange(position: Int, item: ListItem, checked: Boolean)
    }

    class ViewHolder(val binding: ItemAppListBinding) : RecyclerView.ViewHolder(binding.root)

    /** 列表项数据：应用名、包名、图标、是否选中 */
    data class ListItem(
        val title: String,
        val pkg: String,
        val icon: Drawable,
        var isChecked: Boolean = false
    )
}