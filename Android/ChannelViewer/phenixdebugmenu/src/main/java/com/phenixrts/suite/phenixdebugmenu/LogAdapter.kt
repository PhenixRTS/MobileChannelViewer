/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixdebugmenu

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.phenixrts.suite.phenixdebugmenu.databinding.RowLogItemBinding
import kotlin.properties.Delegates

internal class LogAdapter : RecyclerView.Adapter<LogAdapter.ViewHolder>() {

    var items: List<String> by Delegates.observable(emptyList()) { _, old, new ->
        DiffUtil.calculateDiff(
            AdapterDiff(old, new) { oldItem, newItem ->
                oldItem == newItem
            }
        ).dispatchUpdatesTo(this)
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(RowLogItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val log = items[position]
        holder.binding.log = log
    }

    inner class ViewHolder(val binding: RowLogItemBinding) : RecyclerView.ViewHolder(binding.root)

    inner class AdapterDiff<T>(
        private val oldList: List<T>,
        private val newList: List<T>,
        private val compare: (T, T) -> Boolean
    ) : DiffUtil.Callback() {

        override fun getOldListSize() = oldList.size

        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return compare(oldList[oldItemPosition], newList[newItemPosition])
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}
