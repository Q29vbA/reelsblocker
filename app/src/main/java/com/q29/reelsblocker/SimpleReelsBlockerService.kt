package com.q29.reelsblocker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView

class SimpleReelsBlockerService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isServiceEnabled = false
    private val handler = Handler(Looper.getMainLooper())
    private var currentApp = ""

    companion object {
        private const val TAG = "ReelsBlockerService"
    }

    // Target packages
    private val targetPackages = mapOf(
        "com.instagram.android" to "INSTAGRAM DETECTED!",
        "com.facebook.katana" to "FACEBOOK DETECTED!",
        "com.google.android.youtube" to "YOUTUBE DETECTED!"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service Connected!")

        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS

        serviceInfo = info
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Check if blocking is enabled
        checkBlockingStatus()

        Log.d(TAG, "Service setup complete, enabled: $isServiceEnabled")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        Log.d(TAG, "Event received: ${event.eventType}, Package: ${event.packageName}")

        if (!isServiceEnabled) {
            Log.d(TAG, "Service not enabled, ignoring event")
            return
        }

        val packageName = event.packageName?.toString() ?: return

        // Only react to window state changes (app switches)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (targetPackages.containsKey(packageName) && currentApp != packageName) {
                currentApp = packageName
                val message = targetPackages[packageName] ?: "APP DETECTED"
                Log.d(TAG, "Showing overlay for: $packageName")
                showAppDetectedOverlay(message)
            } else if (!targetPackages.containsKey(packageName) && packageName != this.packageName) { // Second condition so overlay won't count as different application
                currentApp = ""
                removeOverlay()
            }
        }
    }

    private fun showAppDetectedOverlay(message: String) {
        // Remove existing overlay first
        removeOverlay()

        Log.d(TAG, "Creating overlay with message: $message")

        // Create overlay layout
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.block_overlay, null)

        val messageText = overlayView?.findViewById<TextView>(R.id.blockMessage)
        messageText?.text = message

        // Set up window parameters
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.CENTER

        try {
            windowManager?.addView(overlayView, params)
            Log.d(TAG, "Overlay added successfully")

            // Auto-hide after 3 seconds
            handler.postDelayed({
                Log.d(TAG, "Auto-removing overlay")
                removeOverlay()
            }, 3000)

        } catch (e: Exception) {
            Log.e(TAG, "Error adding overlay", e)
        }
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
                Log.d(TAG, "Overlay removed")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay", e)
            }
            overlayView = null
        }
    }

    private fun checkBlockingStatus() {
        val prefs = getSharedPreferences("ReelsBlockerPrefs", Context.MODE_PRIVATE)
        isServiceEnabled = prefs.getBoolean("blocking_enabled", false)
        Log.d(TAG, "Blocking status loaded: $isServiceEnabled")
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
        removeOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        removeOverlay()
    }

    // Listen for broadcast from MainActivity when blocking is toggled
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "TOGGLE_BLOCKING") {
            isServiceEnabled = intent.getBooleanExtra("enabled", false)
            Log.d(TAG, "Blocking toggled to: $isServiceEnabled")
            if (!isServiceEnabled) {
                removeOverlay()
                currentApp = ""
            }
        }
        return START_STICKY
    }
}