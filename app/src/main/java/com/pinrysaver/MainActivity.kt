package com.pinrysaver

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.animation.AnimatorInflater
import android.animation.ObjectAnimator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class MainActivity : AppCompatActivity() {
    
    private lateinit var tokenEditText: EditText
    private lateinit var boardIdEditText: EditText
    private lateinit var pinryUrlEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var iconImageView: ImageView
    private lateinit var redChannel: ImageView
    private lateinit var blueChannel: ImageView
    private lateinit var greenChannel: ImageView
    
    private lateinit var encryptedPrefs: EncryptedSharedPreferences
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        initEncryptedPrefs()
        loadSavedValues()
        setupClickListeners()
        startIconAnimation()
    }
    
    private fun initViews() {
        tokenEditText = findViewById(R.id.editTextToken)
        boardIdEditText = findViewById(R.id.editTextBoardId)
        pinryUrlEditText = findViewById(R.id.editTextPinryUrl)
        saveButton = findViewById(R.id.buttonSave)
        iconImageView = findViewById(R.id.iconImageView)
        redChannel = findViewById(R.id.redChannel)
        blueChannel = findViewById(R.id.blueChannel)
        greenChannel = findViewById(R.id.greenChannel)
        
        // RGB channels are already set to alpha=0 in layout and visible
    }
    
    private fun initEncryptedPrefs() {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        
        encryptedPrefs = EncryptedSharedPreferences.create(
            "pinry_saver_prefs",
            masterKeyAlias,
            this,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ) as EncryptedSharedPreferences
    }
    
    private fun loadSavedValues() {
        tokenEditText.setText(encryptedPrefs.getString("api_token", ""))
        boardIdEditText.setText(encryptedPrefs.getString("board_id", ""))
        pinryUrlEditText.setText(encryptedPrefs.getString("pinry_url", ""))
    }
    
    private fun setupClickListeners() {
        saveButton.setOnClickListener {
            saveSettings()
        }
    }
    
    private fun saveSettings() {
        val token = tokenEditText.text.toString().trim()
        val boardId = boardIdEditText.text.toString().trim()
        val pinryUrl = pinryUrlEditText.text.toString().trim()
        
        if (token.isEmpty() || boardId.isEmpty() || pinryUrl.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Ensure URL has proper format
        val formattedUrl = if (!pinryUrl.startsWith("http")) {
            "https://$pinryUrl"
        } else {
            pinryUrl
        }
        
        encryptedPrefs.edit()
            .putString("api_token", token)
            .putString("board_id", boardId)
            .putString("pinry_url", formattedUrl)
            .apply()
        
        Toast.makeText(this, "Settings saved successfully!", Toast.LENGTH_SHORT).show()
    }
    
    private fun startIconAnimation() {
        val mainAnimation = AnimationUtils.loadAnimation(this, R.anim.icon_floating_glitch)
        iconImageView.startAnimation(mainAnimation)
        
        // Start RGB channels fade-in immediately (over 3 seconds)
        startRgbFadeIn()
    }
    
    private fun startRgbFadeIn() {
        // Reset translation values to ensure starting from 0
        redChannel.translationX = 0f
        redChannel.translationY = 0f
        blueChannel.translationX = 0f
        blueChannel.translationY = 0f
        greenChannel.translationX = 0f
        greenChannel.translationY = 0f
        redChannel.alpha = 0f
        blueChannel.alpha = 0f
        greenChannel.alpha = 0f


        // Start glitch animations immediately
        val redAnimator = AnimatorInflater.loadAnimator(this, R.animator.red_channel_animation)
        redAnimator.setTarget(redChannel)
        redAnimator.start()
        
        val blueAnimator = AnimatorInflater.loadAnimator(this, R.animator.blue_channel_animation)
        blueAnimator.setTarget(blueChannel)
        blueAnimator.start()
        
        val greenAnimator = AnimatorInflater.loadAnimator(this, R.animator.green_channel_animation)
        greenAnimator.setTarget(greenChannel)
        greenAnimator.start()
        

        val fadeInRedChannel = ObjectAnimator.ofFloat(redChannel, "alpha", 0f, 1f)
        fadeInRedChannel.duration = 33000
        fadeInRedChannel.start()

        val fadeInBlueChannel = ObjectAnimator.ofFloat(blueChannel, "alpha", 0f, 1f)
        fadeInBlueChannel.duration = 33000
        fadeInBlueChannel.start()

        val fadeInGreenChannel = ObjectAnimator.ofFloat(greenChannel, "alpha", 0f, 1f)
        fadeInGreenChannel.duration = 33000
        fadeInGreenChannel.start()


    }
}
