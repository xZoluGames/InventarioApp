package com.inventario.py.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.inventario.py.data.local.entity.ProductVariantEntity
import com.inventario.py.databinding.ItemVariantEditBinding
import com.inventario.py.utils.CurrencyUtils

class EditVariantAdapter(
    private val onDeleteClick: (ProductVariantEntity) -> Unit,
    private val onEditClick: (ProductVariantEntity) -> Unit
) : ListAdapter<ProductVariantEntity, EditVariantAdapter.ViewHolder>(VariantDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVariantEditBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemVariantEditBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.btnDelete.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDeleteClick(getItem(position))
                }
            }

            binding.btnEdit.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onEditClick(getItem(position))
                }
            }
        }

        fun bind(variant: ProductVariantEntity) {
            with(binding) {
                tvVariantName.text = variant.variantValue
                tvVariantType.text = variant.variantLabel
                tvStock.text = "Stock: ${variant.stock}"

                if (variant.additionalPrice > 0) {
                    tvPrice.text = "+${CurrencyUtils.formatGuarani(variant.additionalPrice)}"
                } else {
                    tvPrice.text = "Sin costo adicional"
                }
            }
        }
    }

    class VariantDiffCallback : DiffUtil.ItemCallback<ProductVariantEntity>() {
        override fun areItemsTheSame(oldItem: ProductVariantEntity, newItem: ProductVariantEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ProductVariantEntity, newItem: ProductVariantEntity): Boolean {
            return oldItem == newItem
        }
    }
}