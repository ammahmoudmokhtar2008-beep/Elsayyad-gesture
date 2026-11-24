package com.elsayyad.gesture

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.concurrent.Executors
import kotlin.math.hypot

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("elsayyad_settings", MODE_PRIVATE) }
    private val gold = Color.rgb(218, 178, 94)
    private val ink = Color.rgb(8, 10, 13)
    private val panel = Color.rgb(23, 26, 31)
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var previewView: PreviewView? = null
    private var landmarker: HandLandmarker? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var running = false
    private var lastActionAt = 0L
    private var latched = false
    private var statusView: TextView? = null
    private var actionView: TextView? = null

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else toast("لازم تسمح باستخدام الكاميرا لتشغيل الإشارات")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = ink
        window.navigationBarColor = ink
        window.decorView.systemUiVisibility = 0
        buildDashboard()
        initializeLandmarker()
    }

    private fun buildDashboard() {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ink)
        }
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(28))
        }
        body.addView(TextView(this).apply {
            text = "ELSAYYAD"
            textSize = 31f
            letterSpacing = .22f
            gravity = Gravity.CENTER
            setTextColor(gold)
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        }, lp(-1, 58))
        body.addView(TextView(this).apply {
            text = "GESTURE CONTROL  /  تحكّم بإشارات يدك"
            textSize = 11f
            letterSpacing = .08f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
        }, lp(-1, 30))
        body.addView(section("مركز التحكم"))
        val previewCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(panel)
        }
        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            visibility = View.GONE
        }
        previewCard.addView(previewView, lp(-1, 230))
        statusView = TextView(this).apply {
            text = "النظام متوقف — فعّل الكاميرا لبدء التعرف على اليد"
            textSize = 13f
            setTextColor(gold)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(14), dp(8), dp(8))
        }
        previewCard.addView(statusView)
        actionView = TextView(this).apply {
            text = "آخر حركة: —"
            textSize = 12f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(8))
        }
        previewCard.addView(actionView)
        body.addView(previewCard, lp(-1, -2))
        val start = Button(this).apply {
            text = "▶  تشغيل الكاميرا والتعرّف"
            isAllCaps = false
            setTextColor(Color.BLACK)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(gold))
            setOnClickListener {
                if (running) stopCamera()
                else if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
                else cameraPermission.launch(Manifest.permission.CAMERA)
            }
        }
        body.addView(start, lp(-1, 54, 12))
        body.addView(section("خريطة الإشارات"))
        listOf(
            "🤏  الإبهام + السبابة" to "تمرير لأعلى",
            "🤏  الإبهام + الخنصر" to "تمرير لأسفل",
            "🤏  الإبهام + الوسطى" to "رفع صوت الوسائط",
            "🤏  الإبهام + البنصر" to "خفض صوت الوسائط"
        ).forEach { (a, b) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(13), dp(14), dp(13))
                setBackgroundColor(panel)
            }
            row.addView(TextView(this).apply { text = a; textSize = 14f; setTextColor(Color.WHITE) }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(TextView(this).apply { text = b; textSize = 12f; setTextColor(gold) })
            body.addView(row, lp(-1, -2, 5))
        }
        body.addView(section("تخصيص التجربة"))
        body.addView(TextView(this).apply { text = "زمن الانتظار بين الإشارات: ${prefs.getInt("delay", 650)} ms"; setTextColor(gold); textSize = 13f; setPadding(2, dp(12), 2, 0) })
        body.addView(SeekBar(this).apply {
            max = 1500
            progress = prefs.getInt("delay", 650).coerceIn(150, 1500)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) { if (user) prefs.edit().putInt("delay", p.coerceAtLeast(150)).apply() }
                override fun onStartTrackingTouch(s: SeekBar?) = Unit
                override fun onStopTrackingTouch(s: SeekBar?) = Unit
            })
        }, lp(-1, 48))
        body.addView(TextView(this).apply { text = "حساسية التقارب"; setTextColor(gold); textSize = 13f; setPadding(2, dp(10), 2, 0) })
        body.addView(SeekBar(this).apply {
            max = 100
            progress = prefs.getInt("sensitivity", 55)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) { if (user) prefs.edit().putInt("sensitivity", p).apply() }
                override fun onStartTrackingTouch(s: SeekBar?) = Unit
                override fun onStopTrackingTouch(s: SeekBar?) = Unit
            })
        }, lp(-1, 48))
        body.addView(section("الصلاحيات"))
        body.addView(Button(this).apply {
            text = "تفعيل صلاحية التحكم في التطبيقات"
            isAllCaps = false
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }, lp(-1, 50))
        body.addView(TextView(this).apply {
            text = "ملاحظة: هذه النسخة تشغّل الكاميرا أثناء بقاء شاشة التطبيق مفتوحة. استمرار الكاميرا فوق التطبيقات الأخرى يحتاج خدمة كاميرا أمامية متوافقة مع قيود إصدار أندرويد، وسيتم استكماله بعد اختبار هذه النسخة."
            textSize = 11f
            setTextColor(Color.LTGRAY)
            setPadding(dp(4), dp(14), dp(4), 0)
        })
        scroll.addView(body)
        outer.addView(scroll)
        setContentView(outer)
        start.tag = "toggle"
        toggleButton = start
    }

    private var toggleButton: Button? = null

    private fun initializeLandmarker() {
        try {
            val base = BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build()
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(base)
                .setRunningMode(RunningMode.IMAGE)
                .setNumHands(1)
                .setMinHandDetectionConfidence(.55f)
                .setMinHandPresenceConfidence(.5f)
                .setMinTrackingConfidence(.5f)
                .build()
            landmarker = HandLandmarker.createFromOptions(this, options)
        } catch (e: Exception) {
            statusView?.text = "تعذر تحميل نموذج التعرف: ${e.localizedMessage ?: "خطأ"}"
        }
    }

    private fun startCamera() {
        if (landmarker == null) initializeLandmarker()
        if (landmarker == null) { toast("نموذج التعرف غير جاهز"); return }
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView?.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(cameraExecutor) { image ->
                    try {
                        val bitmap = image.toBitmap()
                        val mpImage = BitmapImageBuilder(bitmap).build()
                        val result = landmarker?.detect(mpImage)
                        runOnUiThread { handleResult(result) }
                    } catch (_: Exception) {
                        runOnUiThread { statusView?.text = "جاري ضبط الصورة..." }
                    } finally {
                        image.close()
                    }
                }
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
                running = true
                previewView?.visibility = View.VISIBLE
                toggleButton?.text = "■  إيقاف الكاميرا"
                statusView?.text = "الكاميرا تعمل — قرّب الإبهام من أحد الأصابع"
            } catch (e: Exception) {
                toast("تعذر تشغيل الكاميرا: ${e.localizedMessage}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun handleResult(result: HandLandmarkerResult?) {
        val landmarks = result?.landmarks()?.firstOrNull()
        if (landmarks == null || landmarks.size < 21) {
            statusView?.text = "لم يتم رصد اليد — أظهر يدك أمام الكاميرا"
            latched = false
            return
        }
        statusView?.text = "تم رصد اليد ✓ — جرّب تقريب الإبهام من إصبع"
        val wrist = landmarks[0]
        val middleBase = landmarks[9]
        val palmScale = hypot((wrist.x() - middleBase.x()).toDouble(), (wrist.y() - middleBase.y()).toDouble()).toFloat().coerceAtLeast(.001f)
        val thumb = landmarks[4]
        val candidates = listOf(
            8 to GestureCommand.SCROLL_UP,
            20 to GestureCommand.SCROLL_DOWN,
            12 to GestureCommand.VOLUME_UP,
            16 to GestureCommand.VOLUME_DOWN
        )
        val threshold = (.48f - prefs.getInt("sensitivity", 55) * .0025f).coerceIn(.18f, .48f)
        val match = candidates.firstOrNull { (index, _) ->
            hypot((thumb.x() - landmarks[index].x()).toDouble(), (thumb.y() - landmarks[index].y()).toDouble()).toFloat() / palmScale < threshold
        }
        if (match == null) { latched = false; return }
        val now = System.currentTimeMillis()
        if (!latched && now - lastActionAt >= prefs.getInt("delay", 650)) {
            val command = match.second
            GestureAccessibilityService.instance?.performGesture(command)
            lastActionAt = now
            latched = true
            actionView?.text = "آخر حركة: " + when(command) {
                GestureCommand.SCROLL_UP -> "تمرير لأعلى"
                GestureCommand.SCROLL_DOWN -> "تمرير لأسفل"
                GestureCommand.VOLUME_UP -> "رفع الصوت"
                GestureCommand.VOLUME_DOWN -> "خفض الصوت"
            }
        }
    }

    private fun stopCamera() {
        cameraProvider?.unbindAll()
        running = false
        previewView?.visibility = View.GONE
        toggleButton?.text = "▶  تشغيل الكاميرا والتعرّف"
        statusView?.text = "تم إيقاف الكاميرا"
    }

    private fun section(title: String) = TextView(this).apply {
        text = title
        textSize = 17f
        setTextColor(gold)
        setPadding(2, dp(22), 2, dp(10))
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private fun lp(w: Int, h: Int, top: Int = 0) = LinearLayout.LayoutParams(w, h).apply { topMargin = dp(top) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        stopCamera()
        landmarker?.close()
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}
