package com.example.iu

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

class IUTouchLayerService : Service() {

    private lateinit var windowManager: WindowManager

    private val touchViews =
        mutableListOf<View>()

    override fun onCreate() {
        super.onCreate()

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager =
            getSystemService(WINDOW_SERVICE)
                as WindowManager

        val metrics =
            resources.displayMetrics

        val screenWidth =
            metrics.widthPixels

        val screenHeight =
            metrics.heightPixels

        val cardWidth =
            (screenWidth * 0.75f * 0.857f).toInt()

        val cardHeight =
            (screenHeight * 0.42f * 0.857f).toInt()

        val cardLeft =
            (screenWidth - cardWidth) / 2

        val cardTop =
            (screenHeight - cardHeight) / 2

        val cardRight =
            cardLeft + cardWidth

        val cardBottom =
            cardTop + cardHeight

        addTouchRegion(
            left = 0,
            top = 0,
            right = screenWidth,
            bottom = cardTop
        )

        addTouchRegion(
            left = 0,
            top = cardTop,
            right = cardLeft,
            bottom = cardBottom
        )

        addTouchRegion(
            left = cardRight,
            top = cardTop,
            right = screenWidth,
            bottom = cardBottom
        )

        addTouchRegion(
            left = 0,
            top = cardBottom,
            right = screenWidth,
            bottom = screenHeight
        )
    }

    private fun addTouchRegion(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        val width =
            right - left

        val height =
            bottom - top

        if (width <= 0 || height <= 0) {
            return
        }

        val view =
            View(this).apply {

                setBackgroundColor(
                    Color.TRANSPARENT
                )

                setOnTouchListener { _, event ->

                    if (
                        event.action ==
                        MotionEvent.ACTION_DOWN
                    ) {
                        stopSelf()

                        val intent =
                            Intent(
                                this@IUTouchLayerService,
                                MainActivity::class.java
                            ).apply {
                                addFlags(
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                                )
                            }

                        intent.putExtra(
                            "MINIMIZE_IU",
                            true
                        )

                        startActivity(intent)

                        true
                    } else {
                        true
                    }
                }
            }

        val params =
            WindowManager.LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {

                gravity =
                    Gravity.TOP or Gravity.START

                x = left
                y = top
            }

        windowManager.addView(
            view,
            params
        )

        touchViews.add(view)
    }

    override fun onDestroy() {

        for (view in touchViews) {
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
            }
        }

        touchViews.clear()

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }
}