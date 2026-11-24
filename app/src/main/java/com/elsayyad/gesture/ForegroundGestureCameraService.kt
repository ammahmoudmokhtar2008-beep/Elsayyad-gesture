package com.elsayyad.gesture

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.concurrent.Executors
import kotlin.math.hypot

class ForegroundGestureCameraService : LifecycleService() {
    private val executor = Executors.newSingleThreadExecutor()
    private var landmarker: HandLandmarker? = null
    private var provider: ProcessCameraProvider? = null
    private var lastActionAt = 0L
    private var latched = false
    private val prefs by lazy { getSharedPreferences("elsayyad_settings", MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            val base = BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build()
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(base).setRunningMode(RunningMode.IMAGE).setNumHands(1)
                .setMinHandDetectionConfidence(.55f).setMinHandPresenceConfidence(.5f)
                .setMinTrackingConfidence(.5f).build()
            landmarker = HandLandmarker.createFromOptions(this, options)
        } catch (e: Exception) {
            prefs.edit().putString("service_status", "تعذر تحميل نموذج اليد: ${e.localizedMessage}").apply()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Elsayyad Gesture يعمل")
            .setContentText("الكاميرا تراقب إشارات يدك أثناء استخدام التطبيقات الأخرى")
            .setOngoing(true).setCategory(NotificationCompat.CATEGORY_SERVICE).build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
            else startForeground(NOTIFICATION_ID, notification)
            prefs.edit().putBoolean("gesture_running", true).putString("service_status", "يعمل في الخلفية").apply()
            startCameraAnalysis()
        } catch (e: Exception) {
            prefs.edit().putBoolean("gesture_running", false).putString("service_status", "تعذر بدء خدمة الكاميرا: ${e.localizedMessage}").apply()
            stopSelf()
        }
        return START_STICKY
    }

    private fun startCameraAnalysis() {
        if (landmarker == null) return
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val cam = future.get()
                provider = cam
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { image ->
                    try {
                        val bitmap = image.toBitmap()
                        handleResult(landmarker?.detect(BitmapImageBuilder(bitmap).build()))
                    } catch (_: Exception) {
                        prefs.edit().putString("service_status", "جاري ضبط الكاميرا").apply()
                    } finally { image.close() }
                }
                cam.unbindAll()
                cam.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
                prefs.edit().putString("service_status", "الكاميرا تعمل في الخلفية").apply()
            } catch (e: Exception) {
                prefs.edit().putString("service_status", "فشل تشغيل الكاميرا: ${e.localizedMessage}").apply()
                stopSelf()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun handleResult(result: HandLandmarkerResult?) {
        val points = result?.landmarks()?.firstOrNull()
        if (points == null || points.size < 21) { latched = false; return }
        val wrist = points[0]; val middleBase = points[9]
        val scale = hypot((wrist.x()-middleBase.x()).toDouble(), (wrist.y()-middleBase.y()).toDouble()).toFloat().coerceAtLeast(.001f)
        val thumb = points[4]
        val candidates = listOf(8 to GestureCommand.SCROLL_UP, 20 to GestureCommand.SCROLL_DOWN, 12 to GestureCommand.VOLUME_UP, 16 to GestureCommand.VOLUME_DOWN)
        val threshold = (.48f - prefs.getInt("sensitivity",55)*.0025f).coerceIn(.18f,.48f)
        val match = candidates.firstOrNull { (i,_) -> hypot((thumb.x()-points[i].x()).toDouble(),(thumb.y()-points[i].y()).toDouble()).toFloat()/scale < threshold }
        if (match == null) { latched = false; return }
        val now = SystemClock.elapsedRealtime()
        if (!latched && now-lastActionAt >= prefs.getInt("delay",650)) {
            GestureAccessibilityService.instance?.performGesture(match.second)
            lastActionAt = now; latched = true
            val label = when(match.second) {
                GestureCommand.SCROLL_UP -> "تمرير لأعلى"
                GestureCommand.SCROLL_DOWN -> "تمرير لأسفل"
                GestureCommand.VOLUME_UP -> "رفع الصوت"
                GestureCommand.VOLUME_DOWN -> "خفض الصوت"
            }
            prefs.edit().putString("last_gesture",label).apply()
        }
    }

    override fun onDestroy() {
        provider?.unbindAll(); landmarker?.close(); executor.shutdown()
        prefs.edit().putBoolean("gesture_running",false).putString("service_status","الخدمة متوقفة").apply()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID,"تشغيل إشارات اليد",NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
    companion object {
        const val ACTION_START = "com.elsayyad.gesture.START_CAMERA"
        const val ACTION_STOP = "com.elsayyad.gesture.STOP_CAMERA"
        private const val CHANNEL_ID = "elsayyad_gesture_camera"
        private const val NOTIFICATION_ID = 701
    }
}