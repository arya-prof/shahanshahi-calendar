package com.byagowi.persiancalendar.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.WorkerThread
import androidx.core.graphics.PathParser
import androidx.core.graphics.set
import androidx.core.graphics.withScale
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.byagowi.persiancalendar.R
import com.byagowi.persiancalendar.Variants.debugAssertNotNull
import com.byagowi.persiancalendar.databinding.FragmentMapBinding
import com.byagowi.persiancalendar.ui.shared.ArrowView
import com.byagowi.persiancalendar.ui.shared.SolarDraw
import com.byagowi.persiancalendar.ui.utils.setupUpNavigation
import com.byagowi.persiancalendar.utils.formatDateAndTime
import com.byagowi.persiancalendar.utils.logException
import com.cepmuvakkit.times.posAlgo.SunMoonPositionForMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.util.*
import java.util.zip.GZIPInputStream

class MapFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val binding = FragmentMapBinding.inflate(inflater)
        binding.appBar.toolbar.let {
            it.setTitle(R.string.map)
            it.setupUpNavigation()
        }

        solarDraw = SolarDraw(layoutInflater.context)

        val args by navArgs<MapFragmentArgs>()
        val date = GregorianCalendar().also { it.add(Calendar.MINUTE, args.minutesOffset) }

        update(binding, date)

        binding.startArrow.rotateTo(ArrowView.Direction.START)
        binding.startArrow.setOnClickListener {
            binding.startArrow.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            date.add(Calendar.HOUR, -1)
            update(binding, date)
        }
        binding.startArrow.setOnLongClickListener {
            date.add(Calendar.DATE, -1)
            update(binding, date)
            true
        }
        binding.endArrow.rotateTo(ArrowView.Direction.END)
        binding.endArrow.setOnClickListener {
            binding.endArrow.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            date.add(Calendar.HOUR, 1)
            update(binding, date)
        }
        binding.endArrow.setOnLongClickListener {
            date.add(Calendar.DATE, 1)
            update(binding, date)
            true
        }

        return binding.root
    }

    private fun update(binding: FragmentMapBinding, date: GregorianCalendar) {
        lifecycleScope.launch {
            runCatching {
                val bitmap = withContext(Dispatchers.IO) { createDayNightMap(date) }
                binding.map.setImageBitmap(bitmap)
                binding.date.text = date.formatDateAndTime()
            }.onFailure(logException).getOrNull().debugAssertNotNull // handle production OOM and so
        }
    }

    private val cachedMap by lazy {
        val zippedMapPath = resources.openRawResource(R.raw.worldmap).use { it.readBytes() }
        val mapPathString = String(GZIPInputStream(ByteArrayInputStream(zippedMapPath)).readBytes())
        val mapPath = PathParser.createPathFromPathData(mapPathString)
        val scale = 4
        val bitmap =
            Bitmap.createBitmap(360 * 16 / scale, 180 * 16 / scale, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).also {
            it.drawColor(0xFF809DB5.toInt())
            it.withScale(1f / scale, 1f / scale) {
                it.drawPath(mapPath, Paint().apply { color = 0xFFFBF8E5.toInt() })
            }
        }
        bitmap
    }

    private var solarDraw: SolarDraw? = null

    @WorkerThread
    private fun createDayNightMap(date: GregorianCalendar): Bitmap {
        val nightMask = Bitmap.createBitmap(360, 180, Bitmap.Config.ALPHA_8)
        val sunPosition = SunMoonPositionForMap(date)
        var sunLat = .0f
        var sunLong = .0f
        var sunAlt = .0
        var moonLat = .0f
        var moonLong = .0f
        var moonAlt = .0
        (-90 until 90).forEach { lat ->
            (-180 until 180).forEach { long ->
                val sunAltitude = sunPosition.sunAltitude(lat.toDouble(), long.toDouble())
                if (sunAltitude < 0)
                    nightMask[long + 180, 179 - (lat + 90)] =
                        (-sunAltitude.toInt()).coerceAtMost(17) * 5 shl 24
                if (sunAltitude > sunAlt) { // find lat/long of a point with maximum sun altitude
                    sunAlt = sunAltitude; sunLat = 179f - (lat + 90); sunLong = long + 180f
                }
                val moonAltitude = sunPosition.moonAltitude(lat.toDouble(), long.toDouble())
                if (moonAltitude > moonAlt) { // this time for moon
                    moonAlt = moonAltitude; moonLat = 179f - (lat + 90); moonLong = long + 180f
                }
            }
        }
        val result = cachedMap.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(result).also {
            it.drawBitmap(nightMask, null, Rect(0, 0, result.width, result.height), null)
            val scale = result.width / 360
            solarDraw?.sun(it, sunLong * scale, sunLat * scale, scale * 12.5f)
            solarDraw?.simpleMoon(it, moonLong * scale, moonLat * scale, scale * 8f)
        }
        return result
    }
}
