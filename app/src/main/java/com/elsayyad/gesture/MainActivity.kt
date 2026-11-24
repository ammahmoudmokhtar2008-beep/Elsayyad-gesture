package com.elsayyad.gesture

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("elsayyad_settings", MODE_PRIVATE) }
    private val gold = 0xFFD6AD55.toInt()
    private val ink = 0xFF090909.toInt()
    private val panel = 0xFF191919.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = ink
        window.navigationBarColor = ink
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(22,24,22,24); setBackgroundColor(ink) }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(TextView(this).apply { text="ELSAYYAD"; textSize=29f; letterSpacing=.18f; setTextColor(gold); gravity=Gravity.CENTER }, lp(-1,64))
        content.addView(TextView(this).apply { text="GESTURE CONTROL  •  التحكم بالإشارة"; textSize=12f; gravity=Gravity.CENTER; setTextColor(0xFFB8B8B8.toInt()) }, lp(-1,32))
        content.addView(cardTitle("لوحة التحكم"))
        content.addView(TextView(this).apply {
            text="تم تجهيز واجهة التطبيق والإعدادات. محرك الكاميرا والتعرف الفعلي على الإشارات ما زال قيد التطوير."
            textSize=14f; setTextColor(0xFFE7E0D2.toInt()); setPadding(16,16,16,16); setBackgroundColor(panel)
        }, lp(-1,-2,12))
        content.addView(cardTitle("الإشارات"))
        listOf("الإبهام + السبابة     ←     تمرير لأعلى","الإبهام + الخنصر     ←     تمرير لأسفل","الإبهام + الوسطى     ←     رفع الصوت","الإبهام + البنصر     ←     خفض الصوت").forEach {
            content.addView(TextView(this).apply { text=it; textSize=15f; setTextColor(0xFFF4F0E8.toInt()); setPadding(16,16,16,16); setBackgroundColor(panel) }, lp(-1,-2,4))
        }
        content.addView(cardTitle("الإعدادات"))
        addSwitch(content,"إظهار زر عائم","floating",true)
        addSwitch(content,"تنبيه عند اختفاء اليد","hand_alert",true)
        addSwitch(content,"إعادة المحاولة تلقائيًا عند ظهور اليد","auto_detect",true)
        addSwitch(content,"حفظ الإعدادات تلقائيًا","save_settings",true)
        content.addView(TextView(this).apply { text="مدة الاستجابة: ${prefs.getInt("delay",350)} ms"; textSize=14f; setTextColor(gold); setPadding(4,18,4,4) })
        content.addView(SeekBar(this).apply {
            max=1000; progress=prefs.getInt("delay",350)
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s:SeekBar?,p:Int,user:Boolean){ if(user) prefs.edit().putInt("delay",p).apply() }
                override fun onStartTrackingTouch(s:SeekBar?) {}
                override fun onStopTrackingTouch(s:SeekBar?) {}
            })
        }, lp(-1,52))
        content.addView(TextView(this).apply { text="حساسية الإشارة"; textSize=14f; setTextColor(gold); setPadding(4,14,4,4) })
        content.addView(SeekBar(this).apply {
            max=100; progress=prefs.getInt("sensitivity",55)
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s:SeekBar?,p:Int,user:Boolean){ if(user) prefs.edit().putInt("sensitivity",p).apply() }
                override fun onStartTrackingTouch(s:SeekBar?) {}
                override fun onStopTrackingTouch(s:SeekBar?) {}
            })
        }, lp(-1,52))
        content.addView(Button(this).apply { text="إعداد صلاحية التحكم بالتمرير"; setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }, lp(-1,54,18))
        content.addView(Button(this).apply { text="إعدادات صلاحية الكاميرا"; setOnClickListener { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(android.net.Uri.parse("package:$packageName"))) } }, lp(-1,54,8))
        scroll.addView(content); root.addView(scroll); setContentView(root)
    }

    private fun cardTitle(label:String)=TextView(this).apply { text=label; textSize=18f; setTextColor(gold); setPadding(2,24,2,12) }
    private fun addSwitch(parent:LinearLayout,label:String,key:String,default:Boolean) {
        parent.addView(Switch(this).apply {
            text=label; textSize=14f; setTextColor(0xFFF4F0E8.toInt()); isChecked=prefs.getBoolean(key,default)
            setOnCheckedChangeListener { _,checked -> prefs.edit().putBoolean(key,checked).apply() }
        },lp(-1,56))
    }
    private fun lp(w:Int,h:Int,top:Int=0)=LinearLayout.LayoutParams(w,h).apply { topMargin=(top*resources.displayMetrics.density).toInt() }
}
