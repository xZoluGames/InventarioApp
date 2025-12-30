package com.inventario.py.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.inventario.py.R
import com.inventario.py.databinding.ItemTopProductBinding
import com.inventario.py.utils.CurrencyUtils

data class TopProduct(
    val rank: Int,
    val productId: Long,
    val productName: String,
    val imageUrl: String?,
    val quantitySold: Int,
    val revenue: Double,
    val percentageOfTotal: Double
)

class TopProductAdapter(
    private val onItemClick: (TopProduct) -> Unit
) : ListAdapter<TopProduct, TopProductAdapter.ViewHolder>(TopProductDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTopProductBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemTopProductBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(topProduct: TopProduct) {
            with(binding) {
                // Rank number
                tvRank.text = "#${topProduct.rank}"
                
                // Rank background color based on position
                val rankBg = when (topProduct.rank) {
                    1 -> R.color.gold
                    2 -> R.color.silver
                    3 -> R.color.bronze
                    else -> R.color.colorSurfaceVariant
                }
                viewRankBg.setBackgroundResource(rankBg)
                
                // Product name
                tvProductName.text = topProduct.productName
                
                // Quantity sold
                tvQuantitySold.text = "${topProduct.quantitySold} vendidos"
                
                // Revenue
                tvRevenue.text = CurrencyUtils.formatGuarani(topProduct.revenue)
                
                // Percentage bar
                progressPercentage.progress = topProduct.percentageOfTotal.toInt()
                tvPercentage.text = String.format("%.1f%%", topProduct.percentageOfTotal)
                
                // Product image
                if (topProduct.imageUrl.isNullOrEmpty()) {
                    ivProduct.setImageResource(R.drawable.placeholder_product)
                } else {
                    Glide.with(ivProduct.context)
                        .load(topProduct.imageUrl)
                        .placeholder(R.drawable.placeholder_product)
                        .error(R.drawable.placeholder_product)
                        .centerCrop()
                        .into(ivProduct)
                }
            }
        }
    }

    class TopProductDiffCallback : DiffUtil.ItemCallback<TopProduct>() {
        override fun areItemsTheSame(oldItem: TopProduct, newItem: TopProduct): Boolean {
            return oldItem.productId == newItem.productId
        }

        override fun areContentsTheSame(oldItem: TopProduct, newItem: TopProduct): Boolean {
            return oldItem == newItem
        }
    }
}
