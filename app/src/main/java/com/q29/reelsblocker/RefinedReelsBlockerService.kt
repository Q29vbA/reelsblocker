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
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView

class RefinedReelsBlockerService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isServiceEnabled = false
    private val handler = Handler(Looper.getMainLooper())
    private var currentApp = ""
    private var isBlocked = false

    // Simple periodic checking - check every 1 second
    private val CHECK_INTERVAL_MS = 1000L
    private var isCheckingActive = false

    // Persistence tracking
    private var reelsDetectionCount = 0
    private val REQUIRED_DETECTIONS = 2

    companion object {
        private const val TAG = "RefinedReelsBlocker"
    }

    private val targetApps = setOf(
        "com.instagram.android",
        "com.google.android.youtube"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service Connected!")

        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        info.packageNames = targetApps.toTypedArray()
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS

        serviceInfo = info
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        checkBlockingStatus()
        Log.d(TAG, "Service setup complete, enabled: $isServiceEnabled")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !isServiceEnabled) return

        val packageName = event.packageName?.toString() ?: return

        // Handle app switching
        if (!targetApps.contains(packageName)) {
            // Left target apps - stop checking
            if (currentApp.isNotEmpty()) {
                Log.d(TAG, "LEFT target apps - clearing all state")
                stopPeriodicChecking()
                clearState()
            }
            return
        }

        // Entered or switched within target apps
        if (currentApp != packageName) {
            Log.d(TAG, "Entered/switched to: $packageName")
            clearState()
            currentApp = packageName
        }

        // Start periodic checking if not already running
        if (!isCheckingActive) {
            startPeriodicChecking()
        }
    }

    private fun startPeriodicChecking() {
        if (isCheckingActive) return

        isCheckingActive = true
        Log.d(TAG, "▶️ Started periodic checking every ${CHECK_INTERVAL_MS}ms")
        scheduleNextCheck()
    }

    private fun stopPeriodicChecking() {
        isCheckingActive = false
        handler.removeCallbacks(periodicCheckRunnable)
        Log.d(TAG, "⏸️ Stopped periodic checking")
    }

    private fun scheduleNextCheck() {
        if (!isCheckingActive) return
        handler.postDelayed(periodicCheckRunnable, CHECK_INTERVAL_MS)
    }

    private val periodicCheckRunnable = Runnable {
        if (isCheckingActive && currentApp.isNotEmpty()) {
            checkForReelsContent(currentApp)
            scheduleNextCheck()
        }
    }

    private fun checkForReelsContent(packageName: String) {
        val rootNode = rootInActiveWindow ?: return

        Log.d(TAG, "=== Periodic check for reels in $packageName ===")

        val isReels = when (packageName) {
            "com.instagram.android" -> detectInstagramReels(rootNode)
            "com.google.android.youtube" -> detectYouTubeShorts(rootNode)
            else -> false
        }

        Log.d(TAG, "Reels detected: $isReels")

        if (isReels) {
            reelsDetectionCount++
            Log.d(TAG, "Detection count: $reelsDetectionCount/$REQUIRED_DETECTIONS")

            if (reelsDetectionCount >= REQUIRED_DETECTIONS && !isBlocked) {
                blockContent(packageName)
            }
        } else {
            reelsDetectionCount = 0
            if (isBlocked) {
                unblockContent()
            }
        }
    }

    private fun detectInstagramReels(rootNode: AccessibilityNodeInfo): Boolean {
        // Instagram detection using element presence validation
        val reelsElements = listOf(
            "com.instagram.android:id/clips_video_container",
            "com.instagram.android:id/clips_viewer_container",
            "com.instagram.android:id/clips_tab_container",
            "com.instagram.android:id/reel_viewer_container"
        )

        val feedElements = listOf(
            "com.instagram.android:id/row_feed_photo_profile_name",
            "com.instagram.android:id/secondary_label",
            "com.instagram.android:id/row_feed_button_save"
        )

        val explorerElements = listOf(
            "com.instagram.android:id/action_bar_search_edit_text",
            "com.instagram.android:id/image_button",
            "com.instagram.android:id/layout_container"
        )

        val userProfileElements = listOf(
            "com.instagram.android:id/action_bar_large_title_auto_size",
            "com.instagram.android:id/profile_tab_icon_view",
            "com.instagram.android:id/profile_header_bio_text",
            "com.instagram.android:id/profile_header_follow_button"
        )

        val ephemeralContentElements = listOf(
            "com.instagram.android:id/message_composer_container",
            "com.instagram.android:id/toolbar_like_button",
            "com.instagram.android:id/toolbar_reshare_button"
        )

        // Check for presence of each UI state
        val reelsInterface = reelsElements.any { elementId ->
            rootNode.findAccessibilityNodeInfosByViewId(elementId).isNotEmpty()
        }

        val mainFeedInterface = feedElements.any { elementId ->
            rootNode.findAccessibilityNodeInfosByViewId(elementId).isNotEmpty()
        }

        val discoveryInterface = explorerElements.any { elementId ->
            rootNode.findAccessibilityNodeInfosByViewId(elementId).isNotEmpty()
        }

        val personalPageInterface = userProfileElements.any { elementId ->
            rootNode.findAccessibilityNodeInfosByViewId(elementId).isNotEmpty()
        }

        val temporaryMediaInterface = ephemeralContentElements.any { elementId ->
            rootNode.findAccessibilityNodeInfosByViewId(elementId).isNotEmpty()
        }

        Log.d(TAG, "Instagram UI State: reelsInterface=$reelsInterface, mainFeed=$mainFeedInterface, discovery=$discoveryInterface, personalPage=$personalPageInterface, temporaryMedia=$temporaryMediaInterface")

        // Block only when reels interface is active and no other safe interfaces are detected
        val allowedStates = mainFeedInterface || discoveryInterface || personalPageInterface || temporaryMediaInterface
        return reelsInterface && !allowedStates
    }

    private fun detectYouTubeShorts(rootNode: AccessibilityNodeInfo): Boolean {
        // YouTube detection using UI component analysis
        val shortFormVideoElements = listOf(
            "com.google.android.youtube:id/reel_recycler",
            "com.google.android.youtube:id/shorts_player_container",
            "com.google.android.youtube:id/shorts_video_container",
            "com.google.android.youtube:id/reel_player_page_container"
        )

        val mainScreenElements = listOf(
            "com.google.android.youtube:id/browse_container",
            "com.google.android.youtube:id/home_container",
            "com.google.android.youtube:id/feed_container"
        )

        val videoPlayerElements = listOf(
            "com.google.android.youtube:id/watch_player",
            "com.google.android.youtube:id/player_view"
        )

        val searchInterfaceElements = listOf(
            "com.google.android.youtube:id/search_edit_text",
            "com.google.android.youtube:id/search_container"
        )

        // Validate presence of short-form content components
        val shortFormContentInterface = shortFormVideoElements.any { viewId ->
            rootNode.findAccessibilityNodeInfosByViewId(viewId).isNotEmpty()
        }

        // Validate presence of standard navigation components
        val standardBrowsingInterface = mainScreenElements.any { viewId ->
            rootNode.findAccessibilityNodeInfosByViewId(viewId).isNotEmpty()
        }

        // Validate presence of long-form video components
        val longFormPlayerInterface = videoPlayerElements.any { viewId ->
            rootNode.findAccessibilityNodeInfosByViewId(viewId).isNotEmpty()
        }

        // Validate presence of search functionality components
        val searchModeInterface = searchInterfaceElements.any { viewId ->
            rootNode.findAccessibilityNodeInfosByViewId(viewId).isNotEmpty()
        }

        Log.d(TAG, "YouTube Component Analysis: shortForm=$shortFormContentInterface, standardBrowsing=$standardBrowsingInterface, longFormPlayer=$longFormPlayerInterface, searchMode=$searchModeInterface")

        // Trigger blocking when short-form content is detected without safe browsing modes
        val allowedStates = standardBrowsingInterface || longFormPlayerInterface || searchModeInterface
        return shortFormContentInterface && !allowedStates
    }

    private fun blockContent(packageName: String) {
        if (isBlocked) return

        isBlocked = true
        val appName = when (packageName) {
            "com.instagram.android" -> "INSTAGRAM"
            "com.google.android.youtube" -> "YOUTUBE"
            else -> "APP"
        }

        showBlockingOverlay("$appName REELS/SHORTS BLOCKED")
        Log.d(TAG, "🚫 BLOCKED: $appName")
    }

    private fun unblockContent() {
        if (!isBlocked) return

        isBlocked = false
        removeOverlay()
        Log.d(TAG, "✅ UNBLOCKED")
    }

    private fun clearState() {
        currentApp = ""
        isBlocked = false
        reelsDetectionCount = 0
        removeOverlay()
    }

    private fun showBlockingOverlay(message: String) {
        removeOverlay()

        Log.d(TAG, "Showing overlay: $message")

        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.block_overlay, null)

        val messageText = overlayView?.findViewById<TextView>(R.id.blockMessage)
        messageText?.text = message

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
            Log.d(TAG, "Overlay displayed")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing overlay", e)
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
        Log.d(TAG, "Blocking status: $isServiceEnabled")
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
        stopPeriodicChecking()
        clearState()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        stopPeriodicChecking()
        clearState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "TOGGLE_BLOCKING") {
            isServiceEnabled = intent.getBooleanExtra("enabled", false)
            Log.d(TAG, "Blocking toggled to: $isServiceEnabled")
            if (!isServiceEnabled) {
                stopPeriodicChecking()
                clearState()
            }
        }
        return START_STICKY
    }
}