package com.example.arrowsbot

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 40)
        }
        scroll.addView(layout)

        fun title(t: String) = layout.addView(TextView(this).apply {
            text = t
            textSize = 18f
            setPadding(0, 20, 0, 6)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        fun text(t: String) = layout.addView(TextView(this).apply {
            text = t
            textSize = 14f
            setPadding(0, 2, 0, 10)
        })
        fun btn(t: String, action: () -> Unit) = layout.addView(Button(this).apply {
            text = t
            setOnClickListener { action() }
        })

        title("Arrows Bot (Simple)")
        text("ربات ساده و کاربردی برای حل خودکار پازل فلش‌ها\n\n" +
            "• تشخیص فلش‌ها و جهت آن‌ها\n• پیدا کردن مسیر آزاد\n" +
            "• دکمه شناور START / STOP\n• مناسب Redmi و اندروید ۱۱ به بالا")
        title("۱. Accessibility")
        text("سرویس را فعال کنید.")
        btn("تنظیمات Accessibility") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        title("۲. دکمه شناور")
        text("اجازه نمایش روی برنامه‌های دیگر را بدهید.")
        btn("اجازه Overlay") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            }
        }
        title("۳. باتری (مهم برای شیائومی)")
        text("بهینه‌سازی باتری را خاموش کنید و Autostart را روشن کنید.")
        btn("تنظیمات باتری") {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                })
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
        title("۴. کنترل")
        text("بعد از فعال کردن Accessibility و Overlay، دکمه شناور ظاهر می‌شود.\n" +
            "START بزنید → وارد بازی شوید.\nSTOP بزنید → فوری متوقف می‌شود.")
        btn("شروع از داخل اپ") {
            val service = AutoService.instance
            if (service == null) {
                Toast.makeText(this, "اول Accessibility را فعال کنید", Toast.LENGTH_LONG).show()
            } else {
                service.startBot()
                moveTaskToBack(true)
            }
        }
        btn("توقف") {
            AutoService.instance?.stopBot()
            Toast.makeText(this, "متوقف شد", Toast.LENGTH_SHORT).show()
        }
        setContentView(scroll)
    }
}
