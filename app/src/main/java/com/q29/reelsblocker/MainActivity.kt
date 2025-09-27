package com.q29.reelsblocker

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var enableButton: Button
    private lateinit var settingsButton: Button
    private var isEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views
        enableButton = findViewById(R.id.enableButton)
        settingsButton = findViewById(R.id.settingsButton)

        // Set up button listeners
        setupButtonListeners()
    }

    private fun setupButtonListeners() {
        // Enable button functionality
        enableButton.setOnClickListener {
            if (!isEnabled) {
                isEnabled = true
                enableButton.text = "DISABLE"
                enableButton.setBackgroundColor(resources.getColor(android.R.color.holo_red_light, theme))
                showHelloMessage()
            } else {
                isEnabled = false
                enableButton.text = "ENABLE"
                enableButton.setBackgroundColor(resources.getColor(android.R.color.holo_green_light, theme))
                Toast.makeText(this, "BLOCKER DISABLED", Toast.LENGTH_SHORT).show()
            }
        }

        // Settings button functionality
        settingsButton.setOnClickListener {
            showSettingsDialog()
        }
    }

    private fun showHelloMessage() {
        Toast.makeText(this, "HELLO! BLOCKER ENABLED", Toast.LENGTH_SHORT).show()
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