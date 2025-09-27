package com.q29.reelsblocker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import android.widget.Toast

class ReelsBlockerAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isServiceEnabled = false
    private val handler = Handler(Looper.getMainLooper())

    // Target packages
    private val targetPackages = setOf(
        "com.instagram.android",
        "com.facebook.katana",
        "com.google.android.youtube"
    )

    // Keywords to detect reels/shorts sections
    private val reelsKeywords = setOf(
        "reels", "reel", "shorts", "short", "stories", "story"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()

        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        info.packageNames = targetPackages.toTypedArray()
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS

        serviceInfo = info
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Check if blocking is enabled
        checkBlockingStatus()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isServiceEnabled || event == null) return

        val packageName = event.packageName?.toString() ?: return

        if (!targetPackages.contains(packageName)) return

        when (packageName) {
            "com.instagram.android" -> handleInstagram(event)
            "com.facebook.katana" -> handleFacebook(event)
            "com.google.android.youtube" -> handleYouTube(event)
        }
    }

    private fun handleInstagram(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return

        // Look for Instagram Reels tab or content
        if (containsReelsContent(rootNode, "reels")) {
            showBlockOverlay("INSTAGRAM REELS BLOCKED", "#E1306C")
        }
    }

    private fun handleFacebook(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return

        // Look for Facebook Reels section
        if (containsReelsContent(rootNode, "reels")) {
            showBlockOverlay("FACEBOOK REELS BLOCKED", "#4267B2")
        }
    }

    private fun handleYouTube(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return

        // Look for YouTube Shorts
        if (containsReelsContent(rootNode, "shorts") || containsReelsContent(rootNode, "short")) {
            showBlockOverlay("YOUTUBE SHORTS BLOCKED", "#FF0000")
        }
    }

    private fun containsReelsContent(node: AccessibilityNodeInfo?, keyword: String): Boolean {
        if (node == null) return false

        // Check current node text
        val nodeText = node.text?.toString()?.lowercase() ?: ""
        val contentDescription = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewIdResourceName = node.viewIdResourceName?.lowercase() ?: ""

        if (nodeText.contains(keyword) ||
            contentDescription.contains(keyword) ||
            viewIdResourceName.contains(keyword)) {
            return true
        }

        // Check children recursively
        for (i in 0 until node.childCount) {
            if (containsReelsContent(node.getChild(i), keyword)) {
                return true
            }
        }

        return false
    }

    private fun showBlockOverlay(message: String, color: String) {
        // Remove existing overlay
        removeOverlay()

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

            // Auto-hide after 2 seconds
            handler.postDelayed({
                removeOverlay()
            }, 2000)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayView = null
        }
    }

    private fun checkBlockingStatus() {
        val prefs = getSharedPreferences("ReelsBlockerPrefs", Context.MODE_PRIVATE)
        isServiceEnabled = prefs.getBoolean("blocking_enabled", false)
    }

    override fun onInterrupt() {
        removeOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
    }

    // Listen for broadcast from MainActivity when blocking is toggled
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "TOGGLE_BLOCKING") {
            isServiceEnabled = intent.getBooleanExtra("enabled", false)
        }
        return START_STICKY
    }
}