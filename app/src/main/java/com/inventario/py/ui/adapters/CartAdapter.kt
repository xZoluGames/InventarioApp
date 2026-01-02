package com.inventario.py.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.inventario.py.R
import com.inventario.py.data.local.entity.CartItemWithProduct
import com.inventario.py.data.local.entity.currentStock
import com.inventario.py.data.local.entity.priceModifier
import com.inventario.py.data.local.entity.variantName
import com.inventario.py.databinding.ItemCartBinding
import com.inventario.py.utils.CurrencyUtils

class CartAdapter(
    private val onQuantityChanged: (CartItemWithProduct, Int) -> Unit,
    private val onDeleteClick: (CartItemWithProduct) -> Unit,
    private val onItemClick: (CartItemWithProduct) -> Unit
) : ListAdapter<CartItemWithProduct, CartAdapter.ViewHolder>(CartItemDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCartBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemCartBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
            
         /*   binding.btnDelete.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDeleteClick(getItem(position))
                }
            }*/
            
            binding.btnDecrease.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = getItem(position)
                    val newQuantity = item.cartItem.quantity - 1
                    onQuantityChanged(item, newQuantity)
                }
            }
            
            binding.btnIncrease.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = getItem(position)
                    val newQuantity = item.cartItem.quantity + 1
                    onQuantityChanged(item, newQuantity)
                }
            }
        }

        fun bind(cartItem: CartItemWithProduct) {
            val product = cartItem.product
            val variant = cartItem.variant
            val quantity = cartItem.cartItem.quantity
            
            // Calculate price (variant price modifier or product price)
            val unitPrice = variant?.priceModifier ?: product.salePrice
            val subtotal = unitPrice * quantity
            
            // Available stock
            val availableStock = variant?.currentStock ?: product.currentStock

            with(binding) {
                // Product name
                tvProductName.text = product.name
                
                // Variant info
                if (variant != null) {
                    tvVariantName.visibility = View.VISIBLE
                    tvVariantName.text = variant.variantName
                } else {
                    tvVariantName.visibility = View.GONE
                }
                
                // Price
                tvUnitPrice.text = CurrencyUtils.formatGuarani(unitPrice)
                tvSubtotal.text = CurrencyUtils.formatGuarani(subtotal)
                
                // Quantity
                tvQuantity.text = quantity.toString()
                
                // Disable decrease if quantity is 1
                btnDecrease.isEnabled = quantity > 1
                
                // Disable increase if at max stock
                btnIncrease.isEnabled = quantity < availableStock
                
                // Show warning if low stock
                if (availableStock <= 5 && availableStock > 0) {
                    tvStockWarning.visibility = View.VISIBLE
                    tvStockWarning.text = "Quedan $availableStock"
                } else {
                    tvStockWarning.visibility = View.GONE
                }
                
                // Product image
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

    class CartItemDiffCallback : DiffUtil.ItemCallback<CartItemWithProduct>() {
        override fun areItemsTheSame(oldItem: CartItemWithProduct, newItem: CartItemWithProduct): Boolean {
            return oldItem.cartItem.id == newItem.cartItem.id
        }

        override fun areContentsTheSame(oldItem: CartItemWithProduct, newItem: CartItemWithProduct): Boolean {
            return oldItem == newItem
        }
    }
}
