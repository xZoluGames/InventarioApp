package com.inventario.py.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.inventario.py.R
import com.inventario.py.data.local.entity.ProductWithVariants
import com.inventario.py.databinding.ItemProductCompactBinding
import com.inventario.py.utils.CurrencyUtils

class ProductCompactAdapter(
    private val onItemClick: (ProductWithVariants) -> Unit
) : ListAdapter<ProductWithVariants, ProductCompactAdapter.ViewHolder>(ProductCompactDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemProductCompactBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemProductCompactBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(productWithVariants: ProductWithVariants) {
            val product = productWithVariants.product
            val totalStock = if (productWithVariants.variants.isEmpty()) {
                product.currentStock
            } else {
                productWithVariants.variants.sumOf { it.currentStock }
            }

            with(binding) {
                tvProductName.text = product.name
                tvPrice.text = CurrencyUtils.formatGuarani(product.salePrice)
                tvStock.text = "$totalStock unidades"
                
                // Stock status indicator
                when {
                    totalStock <= 0 -> {
                        stockIndicator.setBackgroundResource(R.drawable.bg_stock_out)
                    }
                    totalStock <= product.minStock -> {
                        stockIndicator.setBackgroundResource(R.drawable.bg_stock_low)
                    }
                    else -> {
                        stockIndicator.setBackgroundResource(R.drawable.bg_stock_ok)
                    }
                }
                
                // Image
                if (product.imageUrl.isNullOrEmpty()) {
                    ivProduct.setImageResource(R.drawable.placeholder_product)
                } else {
                    Glide.with(ivProduct.context)
                        .load(product.imageUrl)
                        .placeholder(R.drawable.placeholder_product)
                        .error(R.drawable.placeholder_product)
                        .centerCrop()
                        .into(ivProduct)
                }
            }
        }
    }

    class ProductCompactDiffCallback : DiffUtil.ItemCallback<ProductWithVariants>() {
        override fun areItemsTheSame(oldItem: ProductWithVariants, newItem: ProductWithVariants): Boolean {
            return oldItem.product.id == newItem.product.id
        }

        override fun areContentsTheSame(oldItem: ProductWithVariants, newItem: ProductWithVariants): Boolean {
            return oldItem == newItem
        }
    }
}
