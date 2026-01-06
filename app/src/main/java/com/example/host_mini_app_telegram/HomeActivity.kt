package com.example.host_mini_app_telegram

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.Button

class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.homeRoot)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Setup button click
        val btnOpenMiniApp = findViewById<Button>(R.id.btnOpenMiniApp)
        btnOpenMiniApp.setOnClickListener {
            openMiniApp()
        }
    }

    // Trong HomeActivity.kt
    private fun openMiniApp() {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("user_id", "999999")
        intent.putExtra("first_name", "Hoàng")
        intent.putExtra("username", "Hoàng Hữu Tín")
        intent.putExtra("start_param", "test123") // Thêm dòng này
        startActivity(intent)
    }
}