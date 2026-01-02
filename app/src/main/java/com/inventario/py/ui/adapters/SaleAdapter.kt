package com.inventario.py.ui.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.inventario.py.R
import com.inventario.py.data.local.entity.ProductEntity
import com.inventario.py.data.local.entity.ProductVariantEntity
import com.inventario.py.data.local.entity.ProductWithVariants
import com.inventario.py.data.local.entity.SaleWithDetails
import com.inventario.py.data.local.entity.createdAt
import com.inventario.py.data.local.entity.name
import com.inventario.py.data.local.entity.totalAmount
import com.inventario.py.databinding.ItemSaleBinding
import com.inventario.py.utils.CurrencyUtils
import java.text.SimpleDateFormat
import java.util.*

class SaleAdapter(
    private val onItemClick: (SaleWithDetails) -> Unit,
    private val showDetails: Boolean = true
) : ListAdapter<SaleWithDetails, SaleAdapter.ViewHolder>(SaleDiffCallback()) {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("es", "PY"))
    private val timeFormat = SimpleDateFormat("HH:mm", Locale("es", "PY"))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSaleBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemSaleBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        @SuppressLint("SetTextI18n")
        fun bind(saleWithDetails: SaleWithDetails) {
            val sale = saleWithDetails.sale
            val itemCount = saleWithDetails.items.size

            with(binding) {
                // Sale ID
                tvSaleId.text = "#${sale.id}"

                // Date and time
                tvDate.text = dateFormat.format(sale.createdAt)
                tvTime.text = timeFormat.format(sale.createdAt)

                // Total amount
                tvTotal.text = CurrencyUtils.formatGuarani(sale.totalAmount)

                // Item count
                tvItemCount.text = "$itemCount ${if (itemCount == 1) "producto" else "productos"}"

                // Payment method icon
                val paymentIcon = when (sale.paymentMethod) {
                    "CASH" -> R.drawable.ic_money
                    "CARD" -> R.drawable.ic_card
                    "TRANSFER" -> R.drawable.ic_cloud
                    else -> R.drawable.ic_money
                }
                ivPaymentMethod.setImageResource(paymentIcon)

                // Payment method text
                tvPaymentMethod.text = when (sale.paymentMethod) {
                    "CASH" -> "Efectivo"
                    "CARD" -> "Tarjeta"
                    "TRANSFER" -> "Transferencia"
                    else -> sale.paymentMethod
                }

                // Status indicator
                when (sale.status) {
                    "COMPLETED" -> {
                        statusIndicator.setBackgroundResource(R.drawable.bg_stock_ok)
                    }
                    "PENDING" -> {
                        statusIndicator.setBackgroundResource(R.drawable.bg_stock_low)
                    }
                    "CANCELLED" -> {
                        statusIndicator.setBackgroundResource(R.drawable.bg_stock_out)
                    }
                }

                // Show/hide details
                if (showDetails) {
                    layoutDetails.visibility = View.VISIBLE

                    // Show first few items
                    val itemsText = saleWithDetails.items.take(3).joinToString("\n") { item ->
                        "${item.quantity}x ${item.productName}"
                    }
                    tvItems.text = if (saleWithDetails.items.size > 3) {
                        "$itemsText\n+${saleWithDetails.items.size - 3} más..."
                    } else {
                        itemsText
                    }
                } else {
                    layoutDetails.visibility = View.GONE
                }

                // Seller name (if available)
                saleWithDetails.seller?.let { seller ->
                    tvSeller.visibility = View.VISIBLE
                    tvSeller.text = "Por: ${seller.name}"
                } ?: run {
                    tvSeller.visibility = View.GONE
                }
            }
        }
    }

    class SaleDiffCallback : DiffUtil.ItemCallback<SaleWithDetails>() {
        override fun areItemsTheSame(oldItem: SaleWithDetails, newItem: SaleWithDetails): Boolean {
            return oldItem.sale.id == newItem.sale.id
        }

        override fun areContentsTheSame(oldItem: SaleWithDetails, newItem: SaleWithDetails): Boolean {
            return oldItem == newItem
        }
    }
}
/**
 * EXTENSIONES ADICIONALES PARA COMPATIBILIDAD
 * Agregar estas extensiones al archivo MissingTypes.kt existente
 */

// ==================== EXTENSIONES PARA ProductEntity ====================

// Alias para compatibilidad - costPrice es lo mismo que purchasePrice
val ProductEntity.costPrice: Long get() = this.purchasePrice

// supplier devuelve el nombre del proveedor
val ProductEntity.supplier: String? get() = this.supplierName

// ==================== EXTENSIONES PARA ProductVariantEntity ====================

// Convertir ProductVariantEntity a ProductVariant (data class UI)
fun ProductVariantEntity.toProductVariant(): ProductVariant = ProductVariant(
    id = this.id,
    productId = this.productId,
    name = this.variantValue,
    sku = this.barcode,
    barcode = this.barcode,
    additionalPrice = this.additionalPrice,
    stock = this.stock,
    isActive = this.isActive
)

// Lista de conversión
fun List<ProductVariantEntity>.toProductVariants(): List<ProductVariant> =
    this.map { it.toProductVariant() }

// ==================== DATA CLASS ProductVariant ACTUALIZADA ====================

/**
 * Data class para UI - representa una variante de producto
 * Esta versión incluye todas las propiedades necesarias para ProductDetailFragment
 */
data class ProductVariant(
    val id: String,
    val productId: String,
    val name: String,
    val sku: String? = null,
    val barcode: String? = null,
    val additionalPrice: Long = 0,
    val stock: Int = 0,
    val isActive: Boolean = true
) {
    // Propiedades de compatibilidad
    val variantName: String get() = name
    val priceModifier: Long get() = additionalPrice
    val currentStock: Int get() = stock

    // Método copy con nombres alternativos de parámetros
    fun copyWith(
        variantName: String? = null,
        sku: String? = null,
        priceModifier: Long? = null,
        currentStock: Int? = null
    ): ProductVariant = copy(
        name = variantName ?: this.name,
        sku = sku ?: this.sku,
        additionalPrice = priceModifier ?: this.additionalPrice,
        stock = currentStock ?: this.stock
    )
}

// ==================== EXTENSIONES PARA ProductWithVariants ====================

// Agregar propiedad supplier a ProductWithVariants
val ProductWithVariants.supplier: String? get() = product.supplierName
val ProductWithVariants.costPrice: Long get() = product.purchasePrice

// ==================== TOPPRODUCT PARA ADAPTERS ====================

/**
 * TopProduct en el paquete de adapters (para evitar conflicto de imports)
 * El adapter espera com.inventario.py.ui.adapters.TopProduct
 * pero el ViewModel devuelve com.inventario.py.data.local.entity.TopProduct
 */
// La conversión se hace en el fragment/adapter

// ==================== DATE RANGE ENUM ====================

enum class DateRange {
    TODAY,
    YESTERDAY,
    THIS_WEEK,
    WEEK,      // Alias para THIS_WEEK
    THIS_MONTH,
    MONTH,     // Alias para THIS_MONTH
    THIS_YEAR,
    YEAR,      // Alias para THIS_YEAR
    CUSTOM,
    ALL;

    companion object {
        fun fromString(value: String): DateRange {
            return try {
                valueOf(value.uppercase())
            } catch (e: Exception) {
                TODAY
            }
        }
    }
}