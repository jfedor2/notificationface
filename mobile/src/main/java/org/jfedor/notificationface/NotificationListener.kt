package org.jfedor.notificationface

import android.app.NotificationManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.io.ByteArrayOutputStream
import java.util.*

class NotificationListener : NotificationListenerService() {

    companion object {
        private const val ICON_SIZE = 48
        private const val URI = "/foobar"
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        sendToWatch(currentRanking)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        sendToWatch(rankingMap)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap) {
        sendToWatch(rankingMap)
    }

    private fun sendToWatch(rankingMap: RankingMap) {
        val putDataMapReq = PutDataMapRequest.create(URI)
        val bitmaps = ArrayList<Bitmap>()
        for (notification in activeNotifications) {
            val ranking = Ranking()
            rankingMap.getRanking(notification.key, ranking)

            if (ranking.importance <= NotificationManager.IMPORTANCE_MIN ||
                    notification.packageName == "android" &&
                    notification.notification.channelId == "FOREGROUND_SERVICE") {
                continue
            }

            val bitmap = drawableToBitmap(
                    notification.notification.smallIcon.loadDrawable(this))
            if (!bitmaps.any { it.sameAs(bitmap) }) {
                bitmaps.add(bitmap)
            }
        }
        for ((i, bitmap) in bitmaps.withIndex()) {
            putDataMapReq.dataMap.putByteArray("icon$i", bitmapToByteArray(bitmap))
            putDataMapReq.dataMap.putByteArray("safeicon$i",
                    bitmapToByteArray(createBurnInSafeBitmap(bitmap)))
        }
        putDataMapReq.setUrgent()
        val putDataReq = putDataMapReq.asPutDataRequest()
        Wearable.getDataClient(this).putDataItem(putDataReq)
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    private fun createBurnInSafeBitmap(bitmap: Bitmap): Bitmap {
        val bitmap2 = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)

        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                if (y % 4 == 2 * (x % 2)) {
                    bitmap2.setPixel(x, y, bitmap.getPixel(x, y))
                }
            }
        }

        return bitmap2
    }
}
