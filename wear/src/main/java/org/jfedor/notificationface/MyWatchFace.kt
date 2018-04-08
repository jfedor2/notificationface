package org.jfedor.notificationface

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.Bundle
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.util.Log
import android.view.SurfaceHolder
import com.google.android.gms.wearable.*
import java.util.*

class MyWatchFace : CanvasWatchFaceService() {

    companion object {
        private const val ICON_SIZE = 48
        private const val URI = "/foobar"
    }

    override fun onCreateEngine(): Engine {
        return Engine()
    }

    inner class Engine : CanvasWatchFaceService.Engine(), DataClient.OnDataChangedListener {

        private val random = Random()

        private val typeface = Typeface.createFromAsset(assets, "Roboto-Regular.ttf")
        private val ambientTypeface = Typeface.createFromAsset(assets, "Roboto-Thin.ttf")

        private lateinit var calendar: Calendar

        private var registeredTimeZoneReceiver = false

        private lateinit var mTextPaint: Paint

        private var bitmaps: List<Bitmap> = listOf()
        private var safeBitmaps: List<Bitmap> = listOf()

        private var ambient = false
        private var lowBitAmbient = false
        private var burnInProtection = false

        private val timeZoneReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                calendar.timeZone = TimeZone.getDefault()
                invalidate()
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)

            setWatchFaceStyle(WatchFaceStyle.Builder(this@MyWatchFace).build())

            calendar = Calendar.getInstance()

            mTextPaint = Paint().apply {
                typeface = this@Engine.typeface
                isAntiAlias = true
                color = Color.WHITE
                textSize = 120f
                textAlign = Paint.Align.CENTER
            }
        }

        override fun onPropertiesChanged(properties: Bundle) {
            super.onPropertiesChanged(properties)
            lowBitAmbient = properties.getBoolean(
                    WatchFaceService.PROPERTY_LOW_BIT_AMBIENT, false)
            burnInProtection = properties.getBoolean(
                    WatchFaceService.PROPERTY_BURN_IN_PROTECTION, false)
        }

        override fun onTimeTick() {
            super.onTimeTick()
            invalidate()
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)
            ambient = inAmbientMode

            if (lowBitAmbient) {
                mTextPaint.isAntiAlias = !inAmbientMode
            }

            mTextPaint.typeface = if (ambient) ambientTypeface else typeface
        }

        override fun onDraw(canvas: Canvas, bounds: Rect) {
            canvas.drawColor(Color.BLACK)

            calendar.timeInMillis = System.currentTimeMillis()

            val text = String.format("%d:%02d",
                    calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))

            val textX = bounds.width() * 0.5f
            val textY = bounds.height() * 0.5f + if (bitmaps.isEmpty()) 32f else -12f
            canvas.drawText(text, textX, textY, mTextPaint)

            val padding = 2
            var x = bounds.width() / 2 - (bitmaps.size * (ICON_SIZE + padding) / 2)
            var y = bounds.height() / 2 + 32
            if (ambient) {
                x += random.nextInt(64) - 32
                y += random.nextInt(64)
            }
            for ((i, bitmap) in (if (ambient && burnInProtection) safeBitmaps else bitmaps).withIndex()) {
                canvas.drawBitmap(bitmap, null, Rect(x + i * (ICON_SIZE + padding), y,
                        x + i * (ICON_SIZE + padding) + ICON_SIZE, y + ICON_SIZE),
                        mTextPaint)
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)

            if (visible) {
                Wearable.getDataClient(this@MyWatchFace).addListener(this)
                Wearable.getDataClient(this@MyWatchFace).dataItems.addOnSuccessListener { dataItemBuffer ->
                    for (dataItem in dataItemBuffer) {
                        processDataItem(dataItem)
                    }
                    dataItemBuffer.release()
                    invalidate()
                }
                registerReceiver()

                // Update time zone in case it changed while we weren't visible.
                calendar.timeZone = TimeZone.getDefault()
                invalidate()
            } else {
                Wearable.getDataClient(this@MyWatchFace).removeListener(this)
                unregisterReceiver()
            }
        }

        private fun registerReceiver() {
            if (registeredTimeZoneReceiver) {
                return
            }
            registeredTimeZoneReceiver = true
            val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
            this@MyWatchFace.registerReceiver(timeZoneReceiver, filter)
        }

        private fun unregisterReceiver() {
            if (!registeredTimeZoneReceiver) {
                return
            }
            registeredTimeZoneReceiver = false
            this@MyWatchFace.unregisterReceiver(timeZoneReceiver)
        }

        override fun onDataChanged(dataEvents: DataEventBuffer) {
            for (event in dataEvents) {
                if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == URI) {
                    processDataItem(event.dataItem)
                    invalidate()
                }
            }
        }

        private fun processDataItem(dataItem: DataItem) {
            val newBitmaps = ArrayList<Bitmap>()
            val newSafeBitmaps = ArrayList<Bitmap>()
            val dataMapItem = DataMapItem.fromDataItem(dataItem)
            var i = 0
            while (true) {
                val byteArray = dataMapItem.dataMap.getByteArray("icon$i")
                val safeByteArray = dataMapItem.dataMap.getByteArray("safeicon$i")
                if (byteArray == null) {
                    break
                }
                newBitmaps.add(loadBitmapFromByteArray(byteArray))
                newSafeBitmaps.add(loadBitmapFromByteArray(safeByteArray))
                i++
            }
            bitmaps = newBitmaps
            safeBitmaps = newSafeBitmaps
        }

        private fun loadBitmapFromByteArray(byteArray: ByteArray): Bitmap {
            return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
        }
    }
}
