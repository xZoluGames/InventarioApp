package com.inventario.py.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.inventario.py.R
import com.inventario.py.data.local.entity.ProductVariant
import com.inventario.py.databinding.ItemVariantBinding
import com.inventario.py.utils.CurrencyUtils

class VariantAdapter(
    private val onEditClick: (ProductVariant) -> Unit
) : ListAdapter<ProductVariant, VariantAdapter.ViewHolder>(VariantDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVariantBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemVariantBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.btnEdit.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onEditClick(getItem(position))
                }
            }
            
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onEditClick(getItem(position))
                }
            }
        }

        fun bind(variant: ProductVariant) {
            with(binding) {
                tvVariantName.text = variant.variantName
                
                // SKU
                tvSku.text = variant.sku ?: "Sin SKU"
                
                // Price
                if (variant.priceModifier != null) {
                    tvPrice.text = CurrencyUtils.formatGuarani(variant.priceModifier)
                } else {
                    tvPrice.text = "Precio base"
                }
                
                // Stock
                tvStock.text = "${variant.currentStock} unidades"
                
                // Stock status color
                when {
                    variant.currentStock <= 0 -> {
                        viewVariantColor.setBackgroundResource(R.drawable.bg_stock_out)
                    }
                    variant.currentStock <= 5 -> {
                        viewVariantColor.setBackgroundResource(R.drawable.bg_stock_low)
                    }
                    else -> {
                        viewVariantColor.setBackgroundResource(R.drawable.bg_variant_color)
                    }
                }
            }
        }
    }

    class VariantDiffCallback : DiffUtil.ItemCallback<ProductVariant>() {
        override fun areItemsTheSame(oldItem: ProductVariant, newItem: ProductVariant): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ProductVariant, newItem: ProductVariant): Boolean {
            return oldItem == newItem
        }
    }
}
