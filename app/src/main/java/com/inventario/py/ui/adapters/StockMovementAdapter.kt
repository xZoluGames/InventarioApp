package com.inventario.py.ui.adapters

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ListAdapter
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.inventario.py.R
import com.inventario.py.data.local.entity.MovementType
import com.inventario.py.data.local.entity.StockMovementEntity
import com.inventario.py.databinding.ItemStockMovementBinding


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
            val movementTypeEnum = movement.movementType

            // Set movement type text and colors
            val (typeText, iconRes, colorRes) = when (movementTypeEnum) {
                MovementType.IN.name -> Triple(
                    context.getString(R.string.stock_in),
                    R.drawable.ic_arrow_up,
                    R.color.success
                )
                MovementType.OUT.name -> Triple(
                    context.getString(R.string.stock_out),
                    R.drawable.ic_arrow_down,
                    R.color.error
                )
                MovementType.ADJUSTMENT.name -> Triple(
                    context.getString(R.string.adjustment),
                    R.drawable.ic_edit,
                    R.color.warning
                )
                MovementType.SALE.name -> Triple(
                    context.getString(R.string.sale),
                    R.drawable.ic_cart,
                    R.color.primary
                )
                MovementType.RETURN.name -> Triple(
                    context.getString(R.string.return_item),
                    R.drawable.ic_return,
                    R.color.info
                )
                MovementType.TRANSFER.name -> Triple(
                    context.getString(R.string.transfer),
                    R.drawable.ic_transfer,
                    R.color.secondary
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
                val quantityText = when {
                    movement.quantity > 0 && movementTypeEnum != MovementType.OUT.name -> "+${movement.quantity}"
                    movement.quantity < 0 -> movement.quantity.toString()
                    movementTypeEnum == MovementType.OUT -> "-${kotlin.math.abs(movement.quantity)}"
                    else -> movement.quantity.toString()
                }
                tvQuantity.text = quantityText

                // Quantity color
                val quantityColor = when (movementTypeEnum) {
                    MovementType.IN.name, MovementType.RETURN.name -> R.color.success
                    MovementType.OUT.name, MovementType.SALE.name -> R.color.error
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
