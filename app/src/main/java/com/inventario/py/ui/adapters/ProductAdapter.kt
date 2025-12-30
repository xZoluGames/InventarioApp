package com.inventario.py.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.inventario.py.R
import com.inventario.py.data.local.entity.ProductWithVariants
import com.inventario.py.data.local.entity.category
import com.inventario.py.data.local.entity.currentStock
import com.inventario.py.data.local.entity.minStock
import com.inventario.py.databinding.ItemProductBinding
import com.inventario.py.databinding.ItemProductCompactBinding
import com.inventario.py.utils.CurrencyUtils

class ProductAdapter(
    private val onItemClick: (ProductWithVariants) -> Unit,
    private val onAddToCartClick: ((ProductWithVariants) -> Unit)? = null
) : ListAdapter<ProductWithVariants, RecyclerView.ViewHolder>(ProductDiffCallback()) {

    private var isGridView = true

    companion object {
        private const val VIEW_TYPE_GRID = 0
        private const val VIEW_TYPE_LIST = 1
    }

    fun setViewType(isGrid: Boolean) {
        if (isGridView != isGrid) {
            isGridView = isGrid
            notifyDataSetChanged()
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (isGridView) VIEW_TYPE_GRID else VIEW_TYPE_LIST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_GRID) {
            val binding = ItemProductBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            GridViewHolder(binding)
        } else {
            val binding = ItemProductCompactBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            ListViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val product = getItem(position)
        when (holder) {
            is GridViewHolder -> holder.bind(product)
            is ListViewHolder -> holder.bind(product)
        }
    }

    inner class GridViewHolder(
        private val binding: ItemProductBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
            
            binding.btnAddToCart.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onAddToCartClick?.invoke(getItem(position))
                }
            }
        }

        fun bind(productWithVariants: ProductWithVariants) {
            val product = productWithVariants.product
            val totalStock = getTotalStock(productWithVariants)

            with(binding) {
                tvProductName.text = product.name
                tvPrice.text = CurrencyUtils.formatGuarani(product.salePrice)
                tvStock.text = "$totalStock unidades"
                
                // Category
                tvCategory.text = product.category ?: "Sin categoría"
                
                // Stock status indicator
                when {
                    totalStock <= 0 -> {
                        stockIndicator.setBackgroundResource(R.drawable.bg_stock_out)
                        btnAddToCart.isEnabled = false
                    }
                    totalStock <= product.minStock -> {
                        stockIndicator.setBackgroundResource(R.drawable.bg_stock_low)
                        btnAddToCart.isEnabled = true
                    }
                    else -> {
                        stockIndicator.setBackgroundResource(R.drawable.bg_stock_ok)
                        btnAddToCart.isEnabled = true
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
                
                // Show add to cart button only if callback is provided
                btnAddToCart.visibility = if (onAddToCartClick != null) View.VISIBLE else View.GONE
            }
        }
    }

    inner class ListViewHolder(
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
            val totalStock = getTotalStock(productWithVariants)

            with(binding) {
                tvProductName.text = product.name
                tvPrice.text = CurrencyUtils.formatGuarani(product.salePrice)
                tvStock.text = "$totalStock unidades"
                
                // Stock status
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

    private fun getTotalStock(productWithVariants: ProductWithVariants): Int {
        return if (productWithVariants.variants.isEmpty()) {
            productWithVariants.product.currentStock
        } else {
            productWithVariants.variants.sumOf { it.currentStock }
        }
    }

    class ProductDiffCallback : DiffUtil.ItemCallback<ProductWithVariants>() {
        override fun areItemsTheSame(oldItem: ProductWithVariants, newItem: ProductWithVariants): Boolean {
            return oldItem.product.id == newItem.product.id
        }

        override fun areContentsTheSame(oldItem: ProductWithVariants, newItem: ProductWithVariants): Boolean {
            return oldItem == newItem
        }
    }
}
