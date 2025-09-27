package com.q29.reelsblocker

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var enableButton: Button
    private lateinit var settingsButton: Button
    private var isEnabled = false

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d(TAG, "MainActivity created")

        // Initialize views
        enableButton = findViewById(R.id.enableButton)
        settingsButton = findViewById(R.id.settingsButton)

        // Set up button listeners
        setupButtonListeners()

        // Load saved state
        loadBlockingState()
    }

    private fun setupButtonListeners() {
        // Enable button functionality
        enableButton.setOnClickListener {
            Log.d(TAG, "Enable button clicked")

            if (!isAccessibilityServiceEnabled()) {
                Log.d(TAG, "Accessibility service not enabled, showing dialog")
                showAccessibilityDialog()
                return@setOnClickListener
            }

            if (!isEnabled) {
                enableBlocking()
            } else {
                disableBlocking()
            }
        }

        // Settings button functionality
        settingsButton.setOnClickListener {
            showSettingsDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        // Check if accessibility service was enabled while away
        if (isAccessibilityServiceEnabled() && !isEnabled) {
            Log.d(TAG, "Accessibility service is now enabled")
        }
    }

    private fun enableBlocking() {
        isEnabled = true
        enableButton.text = "DISABLE"
        enableButton.setBackgroundColor(resources.getColor(android.R.color.holo_red_light, theme))

        // Save state
        saveBlockingState(true)

        // Notify accessibility service
        notifyAccessibilityService(true)

        Log.d(TAG, "Blocking enabled")
        Toast.makeText(this, "BLOCKER ENABLED! Open Instagram/Facebook/YouTube to test", Toast.LENGTH_LONG).show()
    }

    private fun disableBlocking() {
        isEnabled = false
        enableButton.text = "ENABLE"
        enableButton.setBackgroundColor(resources.getColor(android.R.color.holo_green_light, theme))

        // Save state
        saveBlockingState(false)

        // Notify accessibility service
        notifyAccessibilityService(false)

        Log.d(TAG, "Blocking disabled")
        Toast.makeText(this, "BLOCKER DISABLED", Toast.LENGTH_SHORT).show()
    }

    private fun saveBlockingState(enabled: Boolean) {
        val prefs = getSharedPreferences("ReelsBlockerPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("blocking_enabled", enabled).apply()
        Log.d(TAG, "Saved blocking state: $enabled")
    }

    private fun loadBlockingState() {
        val prefs = getSharedPreferences("ReelsBlockerPrefs", Context.MODE_PRIVATE)
        isEnabled = prefs.getBoolean("blocking_enabled", false)
        Log.d(TAG, "Loaded blocking state: $isEnabled")

        if (isEnabled) {
            enableButton.text = "DISABLE"
            enableButton.setBackgroundColor(resources.getColor(android.R.color.holo_red_light, theme))
        }
    }

    private fun notifyAccessibilityService(enabled: Boolean) {
        val intent = Intent(this, SimpleReelsBlockerService::class.java)
        intent.action = "TOGGLE_BLOCKING"
        intent.putExtra("enabled", enabled)
        startService(intent)
        Log.d(TAG, "Notified accessibility service: $enabled")
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "${packageName}/${SimpleReelsBlockerService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""

        val isEnabled = enabledServices.contains(service)
        Log.d(TAG, "Accessibility service enabled: $isEnabled")
        Log.d(TAG, "Looking for service: $service")
        Log.d(TAG, "Enabled services: $enabledServices")

        return isEnabled
    }

    private fun showAccessibilityDialog() {
        val dialogBuilder = AlertDialog.Builder(this, R.style.BrutalistDialog)

        dialogBuilder.setTitle("ACCESSIBILITY REQUIRED")
        dialogBuilder.setMessage("To detect when you open Instagram/Facebook/YouTube, this app needs accessibility permission.\n\n1. Tap 'OPEN SETTINGS'\n2. Find 'Reels Blocker'\n3. Toggle it ON\n4. Come back and tap ENABLE")

        dialogBuilder.setPositiveButton("OPEN SETTINGS") { dialog, _ ->
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            dialog.dismiss()
        }

        dialogBuilder.setNegativeButton("CANCEL") { dialog, _ ->
            dialog.dismiss()
        }

        val dialog = dialogBuilder.create()
        dialog.show()

        // Style the dialog buttons
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            setTextColor(resources.getColor(android.R.color.black, theme))
            setBackgroundColor(resources.getColor(android.R.color.holo_orange_light, theme))
        }

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
            setTextColor(resources.getColor(android.R.color.black, theme))
            setBackgroundColor(resources.getColor(android.R.color.holo_red_light, theme))
        }
    }

    private fun showSettingsDialog() {
        val dialogBuilder = AlertDialog.Builder(this, R.style.BrutalistDialog)

        dialogBuilder.setTitle("SETTINGS")
        dialogBuilder.setMessage("Future settings will be here:\n\n• Device Admin Permissions\n• Accessibility Service\n• Usage Access\n• Notification Access")

        dialogBuilder.setPositiveButton("GOT IT") { dialog, _ ->
            dialog.dismiss()
        }

        val dialog = dialogBuilder.create()
        dialog.show()

        // Style the dialog buttons
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            setTextColor(resources.getColor(android.R.color.black, theme))
            setBackgroundColor(resources.getColor(android.R.color.holo_orange_light, theme))
            setPadding(40, 20, 40, 20)
        }
    }
}