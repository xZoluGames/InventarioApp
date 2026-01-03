package com.inventario.py.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.inventario.py.R
import com.inventario.py.data.local.entity.MovementType
import com.inventario.py.data.local.entity.StockMovementEntity
import com.inventario.py.databinding.ItemStockMovementBinding
import com.inventario.py.utils.DateUtils

class StockMovementAdapter : ListAdapter<StockMovementEntity, StockMovementAdapter.MovementViewHolder>(
    MovementDiffCallback()
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MovementViewHolder {
        val binding = ItemStockMovementBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MovementViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MovementViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MovementViewHolder(
        private val binding: ItemStockMovementBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(movement: StockMovementEntity) {
            val context = binding.root.context

            // Parse movement type from String
            val movementType = try {
                MovementType.valueOf(movement.movementType.uppercase())
            } catch (e: Exception) {
                MovementType.ADJUSTMENT
            }

            // Set movement type text and colors
            val (typeText, iconRes, colorRes) = when (movementType) {
                MovementType.IN -> Triple(
                    context.getString(R.string.stock_in),
                    R.drawable.ic_arrow_up,
                    R.color.success
                )
                MovementType.OUT -> Triple(
                    context.getString(R.string.stock_out),
                    R.drawable.ic_arrow_down,
                    R.color.error
                )
                MovementType.ADJUSTMENT -> Triple(
                    context.getString(R.string.adjustment),
                    R.drawable.ic_edit,
                    R.color.warning
                )
                MovementType.SALE -> Triple(
                    context.getString(R.string.sale),
                    R.drawable.ic_cart,
                    R.color.primary
                )
                MovementType.RETURN -> Triple(
                    context.getString(R.string.return_item),
                    R.drawable.ic_return,
                    R.color.info
                )
                MovementType.TRANSFER -> Triple(
                    context.getString(R.string.transfer),
                    R.drawable.ic_transfer,
                    R.color.secondary
                )
                MovementType.CANCELLATION -> Triple(
                    context.getString(R.string.cancellation),
                    R.drawable.ic_close,  // O usa ic_close si no existe
                    R.color.error
                )
            }

            with(binding) {
                // Movement type
                tvMovementType.text = typeText
                ivMovementType.setImageResource(iconRes)
                ivMovementType.setColorFilter(ContextCompat.getColor(context, colorRes))

                // Reason
                tvReason.text = movement.reason ?: context.getString(R.string.no_reason)

                // Date
                tvDate.text = DateUtils.formatRelative(movement.createdAt)

                // Quantity with sign
                val quantityText = when (movementType) {
                    MovementType.IN, MovementType.RETURN -> "+${movement.quantity}"
                    MovementType.OUT, MovementType.SALE -> "-${kotlin.math.abs(movement.quantity)}"
                    MovementType.CANCELLATION -> "+${movement.quantity}"
                    else -> movement.quantity.toString()
                }
                tvQuantity.text = quantityText

                // Quantity color
                val quantityColor = when (movementType) {
                    MovementType.IN, MovementType.RETURN -> R.color.success
                    MovementType.OUT, MovementType.SALE -> R.color.error
                    MovementType.CANCELLATION -> R.color.success
                    else -> R.color.text_primary
                }
                tvQuantity.setTextColor(ContextCompat.getColor(context, quantityColor))

                // Stock change
                tvStockChange.text = "${movement.previousStock} → ${movement.newStock}"
            }
        }
    }

    class MovementDiffCallback : DiffUtil.ItemCallback<StockMovementEntity>() {
        override fun areItemsTheSame(
            oldItem: StockMovementEntity,
            newItem: StockMovementEntity
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: StockMovementEntity,
            newItem: StockMovementEntity
        ): Boolean = oldItem == newItem
    }
}
