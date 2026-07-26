package com.waxball.asmr.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.waxball.asmr.databinding.ActivityHomeBinding

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.playButton.setOnClickListener {
            startActivity(
                Intent(this, PlayActivity::class.java)
                    .putExtra(PlayActivity.EXTRA_BALL_ID, 0)
                    .putExtra(PlayActivity.EXTRA_MODE, PlayActivity.MODE_SANDBOX)
            )
        }
    }
}
