package com.example.rainyviewwithkotlin

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button

class MainActivity : AppCompatActivity() {

    private val rainyView by lazy<RainyView> {
        this.findViewById(R.id.rv)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val startBtn = findViewById<Button>(R.id.start)
        startBtn.setOnClickListener {
            rainyView.start()
        }

        val stopBtn = findViewById<Button>(R.id.stop)
        stopBtn.setOnClickListener {
            rainyView.stop()
        }

    }

    override fun onPause() {
        super.onPause()
        if (isFinishing) {
            rainyView.release()
        }
    }

}
