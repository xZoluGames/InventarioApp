package com.inventario.py.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.inventario.py.R
import com.inventario.py.data.local.entity.StockMovement
import com.inventario.py.databinding.ItemStockMovementBinding
import java.text.SimpleDateFormat
import java.util.*

class StockMovementAdapter : ListAdapter<StockMovement, StockMovementAdapter.ViewHolder>(
    StockMovementDiffCallback()
) {

    private val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "PY"))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStockMovementBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemStockMovementBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(movement: StockMovement) {
            val context = binding.root.context
            
            with(binding) {
                // Movement type
                val (typeText, typeIcon, isPositive) = when (movement.movementType) {
                    "SALE" -> Triple("Venta", R.drawable.ic_cart, false)
                    "PURCHASE" -> Triple("Compra", R.drawable.ic_add, true)
                    "ADJUSTMENT_IN" -> Triple("Ajuste entrada", R.drawable.ic_add, true)
                    "ADJUSTMENT_OUT" -> Triple("Ajuste salida", R.drawable.ic_remove, false)
                    "RETURN" -> Triple("Devolución", R.drawable.ic_back, true)
                    "DAMAGE" -> Triple("Daño/Pérdida", R.drawable.ic_warning, false)
                    else -> Triple(movement.movementType, R.drawable.ic_inventory, true)
                }
                
                tvMovementType.text = typeText
                ivMovementType.setImageResource(typeIcon)
                
                // Quantity with sign
                val quantityText = if (isPositive) {
                    "+${movement.quantity}"
                } else {
                    "-${movement.quantity}"
                }
                tvQuantity.text = quantityText
                
                // Color based on movement direction
                val quantityColor = if (isPositive) {
                    ContextCompat.getColor(context, R.color.stock_ok)
                } else {
                    ContextCompat.getColor(context, R.color.stock_out)
                }
                tvQuantity.setTextColor(quantityColor)
                
                // Icon background color
                val iconBgColor = if (isPositive) {
                    R.drawable.bg_stock_ok
                } else {
                    R.drawable.bg_stock_out
                }
                viewIconBg.setBackgroundResource(iconBgColor)
                
                // Reason
                tvReason.text = movement.reason ?: "Sin descripción"
                
                // Date and time
                tvDateTime.text = dateTimeFormat.format(movement.createdAt)
            }
        }
    }

    class StockMovementDiffCallback : DiffUtil.ItemCallback<StockMovement>() {
        override fun areItemsTheSame(oldItem: StockMovement, newItem: StockMovement): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: StockMovement, newItem: StockMovement): Boolean {
            return oldItem == newItem
        }
    }
}
