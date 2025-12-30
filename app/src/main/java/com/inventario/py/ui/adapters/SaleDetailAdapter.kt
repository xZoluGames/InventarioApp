package com.inventario.py.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.inventario.py.R
import com.inventario.py.data.local.entity.SaleItem
import com.inventario.py.databinding.ItemSaleDetailBinding
import com.inventario.py.utils.CurrencyUtils

class SaleDetailAdapter : ListAdapter<SaleItem, SaleDetailAdapter.ViewHolder>(
    SaleItemDiffCallback()
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSaleDetailBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemSaleDetailBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(saleItem: SaleItem) {
            with(binding) {
                // Product name
                tvProductName.text = saleItem.productName
                
                // Quantity and unit price
                tvQuantityPrice.text = "${saleItem.quantity} x ${CurrencyUtils.formatGuarani(saleItem.unitPrice)}"
                
                // Subtotal
                tvSubtotal.text = CurrencyUtils.formatGuarani(saleItem.subtotal)
                
                // Product image placeholder
                ivProduct.setImageResource(R.drawable.placeholder_product)
            }
        }
    }

    class SaleItemDiffCallback : DiffUtil.ItemCallback<SaleItem>() {
        override fun areItemsTheSame(oldItem: SaleItem, newItem: SaleItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SaleItem, newItem: SaleItem): Boolean {
            return oldItem == newItem
        }
    }
}
