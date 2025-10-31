package com.example.weather

import android.graphics.drawable.Animatable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ForecastAdapter(private var forecastList: List<ForecastItem>) :
    RecyclerView.Adapter<ForecastAdapter.ForecastViewHolder>() {

    inner class ForecastViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDate: TextView = itemView.findViewById(R.id.tvForecastDate)
        val tvTemp: TextView = itemView.findViewById(R.id.tvForecastTemp)
        val tvDesc: TextView = itemView.findViewById(R.id.tvForecastDesc)
        val ivIcon: ImageView = itemView.findViewById(R.id.ivForecastIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ForecastViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_forecast, parent, false)
        return ForecastViewHolder(view)
    }

    override fun onBindViewHolder(holder: ForecastViewHolder, position: Int) {
        val forecast = forecastList[position]
        holder.tvDate.text = forecast.date
        holder.tvTemp.text = "${forecast.temperature}°C"
        holder.tvDesc.text = forecast.description.replaceFirstChar { it.uppercase() }

        val iconRes = when {
            forecast.description.contains("rain", ignoreCase = true) -> R.drawable.anim_rain
            forecast.description.contains("cloud", ignoreCase = true) -> R.drawable.anim_cloud
            forecast.description.contains("snow", ignoreCase = true) -> R.drawable.anim_rain // fallback
            else -> R.drawable.anim_sun
        }

        holder.ivIcon.setImageResource(iconRes)
        val d = holder.ivIcon.drawable
        if (d is Animatable) (d as Animatable).start()
    }

    override fun getItemCount(): Int = forecastList.size

    fun updateData(newList: List<ForecastItem>) {
        forecastList = newList
        notifyDataSetChanged()
    }
}
