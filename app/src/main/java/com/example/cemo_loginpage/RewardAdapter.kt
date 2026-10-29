package com.example.cemo_loginpage

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView

class RewardAdapter(
    private var rewards   : List<RewardItem>,
    private var userPoints: Int,
    private val onClaim   : (RewardItem) -> Unit
) : RecyclerView.Adapter<RewardAdapter.RewardViewHolder>() {

    inner class RewardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName   : TextView         = itemView.findViewById(R.id.tvRewardName)
        val tvPoints : TextView         = itemView.findViewById(R.id.tvRewardPoints)
        val tvDesc   : TextView         = itemView.findViewById(R.id.tvRewardDesc)
        val tvStock  : TextView         = itemView.findViewById(R.id.tvRewardStock)
        val tvExpiry : TextView         = itemView.findViewById(R.id.tvRewardExpiry)
        val btnClaim : AppCompatButton  = itemView.findViewById(R.id.btnClaimReward)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RewardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reward, parent, false)
        return RewardViewHolder(view)
    }

    override fun onBindViewHolder(holder: RewardViewHolder, position: Int) {
        val reward = rewards[position]

        holder.tvName.text   = reward.name
        holder.tvPoints.text = "${reward.points} pts"
        holder.tvDesc.text   = reward.description

        // Stock badge
        when {
            reward.stock <= 0  -> {
                holder.tvStock.text = "Out of Stock"
                holder.tvStock.setTextColor("#FF4D4D".toColorInt())
                holder.btnClaim.isEnabled = false
                holder.btnClaim.alpha     = 0.5f
            }
            reward.stock <= 5  -> {
                holder.tvStock.text = "Low Stock: ${reward.stock} left"
                holder.tvStock.setTextColor("#FFA500".toColorInt())
                holder.btnClaim.isEnabled = true
                holder.btnClaim.alpha     = 1f
            }
            else -> {
                holder.tvStock.text = "In Stock: ${reward.stock}"
                holder.tvStock.setTextColor("#4CAF50".toColorInt())
                holder.btnClaim.isEnabled = true
                holder.btnClaim.alpha     = 1f
            }
        }

        // Dim claim button if user doesn't have enough points
        if (userPoints < reward.points && reward.stock > 0) {
            holder.btnClaim.alpha = 0.5f
        }

        // Expiry
        if (reward.expiry.isNotBlank()) {
            holder.tvExpiry.visibility = View.VISIBLE
            holder.tvExpiry.text       = "Expires: ${reward.expiry}"
        } else {
            holder.tvExpiry.visibility = View.GONE
        }

        holder.btnClaim.setOnClickListener {
            onClaim(reward)
        }
    }

    override fun getItemCount() = rewards.size

    fun updateRewards(newList: List<RewardItem>) {
        rewards = newList
        notifyDataSetChanged()
    }

    fun updateUserPoints(pts: Int) {
        userPoints = pts
        notifyDataSetChanged()
    }
}