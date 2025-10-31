package com.example.weather

import android.content.Context
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.weather.databinding.ActivityMainBinding
import io.github.inflationx.viewpump.ViewPumpContextWrapper
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import javax.net.ssl.HttpsURLConnection
import kotlin.math.pow

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var forecastAdapter: ForecastAdapter

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(ViewPumpContextWrapper.wrap(newBase!!))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupForecastRecyclerView()
        setupClickListeners()
    }

    private fun setupForecastRecyclerView() {
        forecastAdapter = ForecastAdapter(emptyList())
        binding.rvForecast.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvForecast.adapter = forecastAdapter
    }

    private fun setupClickListeners() {
        binding.btnGetWeather.setOnClickListener {
            val city = binding.etCity.text.toString().trim()
            if (city.isNotEmpty()) {
                getWeatherData(city)
            } else {
                android.widget.Toast.makeText(
                    this,
                    "Please enter a city name",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun getWeatherData(city: String) {
        showLoading(true)
        scope.launch {
            try {
                val currentJson = withContext(Dispatchers.IO) { fetchCurrentWeatherFromApi(city) }
                val forecastJson = withContext(Dispatchers.IO) { fetchForecastFromApi(city) }
                displayWeatherData(currentJson, forecastJson)
            } catch (e: Exception) {
                showError("Error: ${e.message ?: "Unknown error"}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun fetchCurrentWeatherFromApi(city: String): String {
        val apiKey = "f4b7a446c26021a4e4748c4dafcfcd7c"
        val urlString =
            "https://api.openweathermap.org/data/2.5/weather?q=$city&appid=$apiKey&units=metric"
        val url = URL(urlString)
        val conn = url.openConnection() as HttpsURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 30000
        conn.readTimeout = 30000

        return try {
            if (conn.responseCode == HttpsURLConnection.HTTP_OK) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else throw Exception("HTTP error: ${conn.responseCode}")
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchForecastFromApi(city: String): String {
        val apiKey = "f4b7a446c26021a4e4748c4dafcfcd7c"
        val urlString =
            "https://api.openweathermap.org/data/2.5/forecast?q=$city&appid=$apiKey&units=metric"
        val url = URL(urlString)
        val conn = url.openConnection() as HttpsURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 30000
        conn.readTimeout = 30000

        return try {
            if (conn.responseCode == HttpsURLConnection.HTTP_OK) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else throw Exception("HTTP error: ${conn.responseCode}")
        } finally {
            conn.disconnect()
        }
    }

    private fun displayWeatherData(currentJsonString: String, forecastJsonString: String) {
        try {
            val current = JSONObject(currentJsonString)
            val cityName = current.getString("name")
            val coord = current.getJSONObject("coord")
            val lat = coord.getDouble("lat")
            val lon = coord.getDouble("lon")

            val main = current.getJSONObject("main")
            val weatherArray = current.getJSONArray("weather")
            val weatherObj = weatherArray.getJSONObject(0)
            val sys = current.getJSONObject("sys")
            val wind = current.getJSONObject("wind")

            val temp = main.getDouble("temp")
            val feelsLike = main.optDouble("feels_like", temp)
            val humidity = main.optInt("humidity", 0)
            val tempMin = main.optDouble("temp_min", temp)
            val tempMax = main.optDouble("temp_max", temp)
            val pressure = main.optDouble("pressure", 1013.25)
            val seaLevel = main.optDouble("sea_level", 1013.25)
            val description = weatherObj.optString("description", "")

            val windSpeed = wind.optDouble("speed", 0.0)

            val sunriseTimestamp = sys.optLong("sunrise", 0L)
            val sunsetTimestamp = sys.optLong("sunset", 0L)

            val sunriseTime =
                if (sunriseTimestamp > 0) SimpleDateFormat(
                    "hh:mm a",
                    Locale.getDefault()
                ).format(Date(sunriseTimestamp * 1000)) else "--:--"
            val sunsetTime =
                if (sunsetTimestamp > 0) SimpleDateFormat(
                    "hh:mm a",
                    Locale.getDefault()
                ).format(Date(sunsetTimestamp * 1000)) else "--:--"

            // محاسبات
            val seaLevelMeters = if (seaLevel > 0) seaLevel * 0.01019716 else 0.0
            val altitude =
                44330.0 * (1.0 - (pressure / 1013.25).pow(0.1903)) // تقریب ارتفاع از فشار

            // forecast parse
            val forecastList = parseForecastData(JSONObject(forecastJsonString))

            // نمایش در UI
            binding.weatherInfo.visibility = View.VISIBLE
            binding.forecastSection.visibility = View.VISIBLE
            binding.tvError.visibility = View.GONE

            binding.tvCity.text = cityName
            binding.tvTemperature.text = "${temp.toInt()}°C"
            binding.tvFeelsLike.text = "Feels like ${feelsLike.toInt()}°C"
            binding.tvDescription.text = description.replaceFirstChar { it.uppercase() }
            binding.tvHumidity.text = "$humidity%"
            binding.tvWindSpeed.text = "$windSpeed m/s"
            binding.tvSeaLevel.text =
                String.format(Locale.getDefault(), "%.0f hPa (%.2f m)", seaLevel, seaLevelMeters)
            binding.tvAltitude.text = String.format(Locale.getDefault(), "%.0f m", altitude)
            binding.tvSunrise.text = sunriseTime
            binding.tvSunset.text = sunsetTime
            binding.tvMinMax.text = "${tempMin.toInt()}° / ${tempMax.toInt()}°"

            // main animated icon
            val mainIconRes = when {
                description.contains("rain", ignoreCase = true) -> R.drawable.anim_rain
                description.contains("cloud", ignoreCase = true) -> R.drawable.anim_cloud
                description.contains("snow", ignoreCase = true) -> R.drawable.anim_rain
                else -> R.drawable.anim_sun
            }
            binding.ivMainWeather.setImageResource(mainIconRes)
            val mainDrawable = binding.ivMainWeather.drawable
            if (mainDrawable is Animatable) (mainDrawable as Animatable).start()

            // forecast update
            forecastAdapter.updateData(forecastList)

            // نمایش شاخص کیفیت هوا
            fetchAirQuality(lat, lon)

        } catch (e: Exception) {
            showError("Error parsing data: ${e.message ?: "Unknown error"}")
        }
    }

    private fun fetchAirQuality(lat: Double, lon: Double) {
        scope.launch {
            try {
                val apiKey = "f4b7a446c26021a4e4748c4dafcfcd7c"
                val urlString =
                    "https://api.openweathermap.org/data/2.5/air_pollution?lat=$lat&lon=$lon&appid=$apiKey"
                val result = withContext(Dispatchers.IO) {
                    URL(urlString).readText()
                }
                val json = JSONObject(result)
                val list = json.getJSONArray("list").getJSONObject(0)
                val main = list.getJSONObject("main")
                val aqiValue = main.getInt("aqi")

                val aqiText = when (aqiValue) {
                    1 -> "Good"
                    2 -> "Fair"
                    3 -> "Moderate"
                    4 -> "Poor"
                    else -> "Very Poor"
                }

                val color = when (aqiValue) {
                    1 -> "#4CAF50" // سبز
                    2 -> "#b3810e" // زرد
                    3 -> "#FFC107" // نارنجی
                    4 -> "#FF5722" // قرمز
                    else -> "#B71C1C" // قرمز تیره
                }

                // نمایش کارت
                binding.aqiCard.visibility = View.VISIBLE

                // متن شاخص
                binding.tvAqiValue.text = "AQI: $aqiValue ($aqiText)"
                binding.tvAqiValue.setTextColor(android.graphics.Color.parseColor(color))

                // مقدار نوار
                binding.aqiBar.progress = when (aqiValue) {
                    1 -> 100      // Good
                    2 -> 200      // Fair
                    3 -> 300      // Moderate
                    4 -> 400      // Poor
                    else -> 500   // Very Poor
                }

                // تغییر رنگ نوار
                binding.aqiBar.progressTintList =
                    android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(color))

            } catch (e: Exception) {
                e.printStackTrace()
                binding.aqiCard.visibility = View.GONE
            }
        }
    }


    private fun parseForecastData(forecastJson: JSONObject): List<ForecastItem> {
        val out = mutableListOf<ForecastItem>()
        val list = forecastJson.getJSONArray("list")
        val daily = mutableListOf<JSONObject>()
        val processed = mutableSetOf<String>()
        val sdfIn = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val sdfOut = SimpleDateFormat("EEE", Locale.getDefault())

        for (i in 0 until list.length()) {
            val item = list.getJSONObject(i)
            val dtTxt = item.getString("dt_txt")
            val date = sdfIn.parse(dtTxt) ?: continue
            val day = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
            if (dtTxt.contains("12:00:00") && !processed.contains(day)) {
                daily.add(item)
                processed.add(day)
            }
        }

        for (i in 0 until kotlin.math.min(4, daily.size)) {
            val it = daily[i]
            val main = it.getJSONObject("main")
            val weather = it.getJSONArray("weather").getJSONObject(0)
            val dtTxt = it.getString("dt_txt")
            val date = sdfIn.parse(dtTxt)!!
            val item = ForecastItem(
                date = sdfOut.format(date),
                temperature = main.getDouble("temp").toInt(),
                description = weather.getString("description")
            )
            out.add(item)
        }
        return out
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnGetWeather.isEnabled = !show
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
        binding.weatherInfo.visibility = View.GONE
        binding.forecastSection.visibility = View.GONE
        binding.aqiCard.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
