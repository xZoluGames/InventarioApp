package com.inventario.py.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.inventario.py.data.local.entity.CartItemWithProduct
import com.inventario.py.databinding.ItemOrderSummaryBinding
import com.inventario.py.utils.CurrencyUtils

/**
 * Adapter para mostrar el resumen de items en el checkout
 */
class OrderSummaryAdapter : ListAdapter<CartItemWithProduct, OrderSummaryAdapter.ViewHolder>(
    OrderSummaryDiffCallback()
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemOrderSummaryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemOrderSummaryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CartItemWithProduct) {
            with(binding) {
                // Nombre del producto
                tvProductName.text = item.product.name

                // Variante si existe
                if (!item.cartItem.variantDescription.isNullOrEmpty()) {
                    tvVariant.text = item.cartItem.variantDescription
                    tvVariant.visibility = android.view.View.VISIBLE
                } else {
                    tvVariant.visibility = android.view.View.GONE
                }

                // Cantidad
                tvQuantity.text = "x${item.cartItem.quantity}"

                // Precio unitario
                val unitPrice = item.variant?.additionalPrice?.let {
                    item.product.salePrice + it
                } ?: item.product.salePrice
                tvUnitPrice.text = CurrencyUtils.formatGuarani(unitPrice)

                // Subtotal
                val subtotal = unitPrice * item.cartItem.quantity
                tvSubtotal.text = CurrencyUtils.formatGuarani(subtotal)
            }
        }
    }

    class OrderSummaryDiffCallback : DiffUtil.ItemCallback<CartItemWithProduct>() {
        override fun areItemsTheSame(
            oldItem: CartItemWithProduct,
            newItem: CartItemWithProduct
        ): Boolean {
            return oldItem.cartItem.id == newItem.cartItem.id
        }

        override fun areContentsTheSame(
            oldItem: CartItemWithProduct,
            newItem: CartItemWithProduct
        ): Boolean {
            return oldItem == newItem
        }
    }
}
