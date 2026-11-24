package com.elsayyad.gesture

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("elsayyad_settings", MODE_PRIVATE) }
    private val gold = Color.rgb(218,178,94)
    private val ink = Color.rgb(8,10,13)
    private val panel = Color.rgb(23,26,31)
    private var statusView: TextView? = null
    private var toggleButton: Button? = null
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startBackgroundCamera() else toast("لازم تسمح باستخدام الكاميرا لتشغيل الإشارات")
    }

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); window.statusBarColor=ink; window.navigationBarColor=ink; buildDashboard() }
    override fun onResume() { super.onResume(); refreshStatus() }

    private fun buildDashboard() {
        val outer=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(ink) }
        val scroll=ScrollView(this)
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(18),dp(20),dp(28)) }
        body.addView(TextView(this).apply { text="ELSAYYAD"; textSize=31f; letterSpacing=.22f; gravity=Gravity.CENTER; setTextColor(gold); typeface=android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD) },lp(-1,58))
        body.addView(TextView(this).apply { text="GESTURE CONTROL  /  تحكّم بإشارات يدك"; textSize=11f; letterSpacing=.08f; gravity=Gravity.CENTER; setTextColor(Color.LTGRAY) },lp(-1,30))
        body.addView(section("مركز التحكم"))
        statusView=TextView(this).apply { text="الخدمة متوقفة"; textSize=14f; setTextColor(gold); gravity=Gravity.CENTER; setPadding(dp(8),dp(24),dp(8),dp(24)); setBackgroundColor(panel) }
        body.addView(statusView,lp(-1,-2))
        toggleButton=Button(this).apply {
            text="▶  تشغيل التحكم في الخلفية"; isAllCaps=false; setTextColor(Color.BLACK); setBackgroundTintList(android.content.res.ColorStateList.valueOf(gold))
            setOnClickListener { if(prefs.getBoolean("gesture_running",false)) stopBackgroundCamera() else if(ContextCompat.checkSelfPermission(this@MainActivity,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) startBackgroundCamera() else cameraPermission.launch(Manifest.permission.CAMERA) }
        }
        body.addView(toggleButton,lp(-1,54,12))
        body.addView(section("خريطة الإشارات"))
        listOf("🤏  الإبهام + السبابة" to "تمرير لأعلى","🤏  الإبهام + الخنصر" to "تمرير لأسفل","🤏  الإبهام + الوسطى" to "رفع صوت الوسائط","🤏  الإبهام + البنصر" to "خفض صوت الوسائط").forEach { (a,b) ->
            val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(dp(14),dp(13),dp(14),dp(13)); setBackgroundColor(panel) }
            row.addView(TextView(this).apply { text=a; textSize=14f; setTextColor(Color.WHITE) },LinearLayout.LayoutParams(0,-2,1f))
            row.addView(TextView(this).apply { text=b; textSize=12f; setTextColor(gold) }); body.addView(row,lp(-1,-2,5))
        }
        body.addView(section("تخصيص التجربة"))
        body.addView(TextView(this).apply { text="زمن الانتظار بين الإشارات"; setTextColor(gold); textSize=13f },lp(-1,30))
        body.addView(SeekBar(this).apply { max=1500; progress=prefs.getInt("delay",650).coerceIn(150,1500); setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s:SeekBar?,p:Int,user:Boolean){if(user)prefs.edit().putInt("delay",p.coerceAtLeast(150)).apply()}
            override fun onStartTrackingTouch(s:SeekBar?)=Unit; override fun onStopTrackingTouch(s:SeekBar?)=Unit
        }) },lp(-1,48))
        body.addView(TextView(this).apply { text="حساسية التقارب"; setTextColor(gold); textSize=13f },lp(-1,30))
        body.addView(SeekBar(this).apply { max=100; progress=prefs.getInt("sensitivity",55); setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s:SeekBar?,p:Int,user:Boolean){if(user)prefs.edit().putInt("sensitivity",p).apply()}
            override fun onStartTrackingTouch(s:SeekBar?)=Unit; override fun onStopTrackingTouch(s:SeekBar?)=Unit
        }) },lp(-1,48))
        body.addView(section("الصلاحيات"))
        body.addView(Button(this).apply { text="تفعيل صلاحية التحكم في التطبيقات"; isAllCaps=false; setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } },lp(-1,50))
        body.addView(TextView(this).apply { text="ابدأ الخدمة من هنا أولاً، وبعدها اخرج من التطبيق واستخدم تطبيقاتك. ستظل الكاميرا تعمل بإشعار ظاهر. فعّل صلاحية Accessibility لتنفيذ التمرير داخل التطبيقات."; textSize=11f; setTextColor(Color.LTGRAY); setPadding(dp(4),dp(14),dp(4),0) })
        scroll.addView(body); outer.addView(scroll); setContentView(outer); refreshStatus()
    }

    private fun startBackgroundCamera() {
        val i=Intent(this,ForegroundGestureCameraService::class.java).apply { action=ForegroundGestureCameraService.ACTION_START }
        try {
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) ContextCompat.startForegroundService(this,i) else startService(i)
            prefs.edit().putBoolean("gesture_running",true).apply(); refreshStatus(); toast("بدأ التحكم — تقدر تخرج من التطبيق الآن")
        } catch(e:Exception){toast("لم يبدأ تشغيل الخلفية: ${e.localizedMessage}")}
    }
    private fun stopBackgroundCamera() {
        startService(Intent(this,ForegroundGestureCameraService::class.java).apply { action=ForegroundGestureCameraService.ACTION_STOP })
        prefs.edit().putBoolean("gesture_running",false).apply(); refreshStatus()
    }
    private fun refreshStatus() {
        val active=prefs.getBoolean("gesture_running",false)
        statusView?.text=prefs.getString("service_status",if(active)"الخدمة تعمل في الخلفية" else "الخدمة متوقفة")
        toggleButton?.text=if(active)"■  إيقاف التحكم" else "▶  تشغيل التحكم في الخلفية"
    }
    private fun section(t:String)=TextView(this).apply { text=t; textSize=17f; setTextColor(gold); setPadding(2,dp(22),2,dp(10)); typeface=android.graphics.Typeface.DEFAULT_BOLD }
    private fun lp(w:Int,h:Int,top:Int=0)=LinearLayout.LayoutParams(w,h).apply{topMargin=dp(top)}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
}