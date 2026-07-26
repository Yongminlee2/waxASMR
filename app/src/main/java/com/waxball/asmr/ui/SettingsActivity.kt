package com.waxball.asmr.ui

import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.waxball.asmr.R
import com.waxball.asmr.core.Progress
import com.waxball.asmr.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var store: PrefsProgressStore
    private lateinit var progress: Progress

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = PrefsProgressStore(this)
        progress = store.load()
        bind()
    }

    private fun bind() {
        binding.hapticsSwitch.isChecked = progress.hapticsOn
        binding.hapticsSwitch.setOnCheckedChangeListener { _, on ->
            progress.hapticsOn = on
            store.save(progress)
        }

        binding.orbitLockSwitch.isChecked = progress.orbitLocked
        binding.orbitLockSwitch.setOnCheckedChangeListener { _, on ->
            progress.orbitLocked = on
            store.save(progress)
        }

        binding.volumeBar.progress = (progress.volume * 100).toInt()
        binding.volumeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                progress.volume = value / 100f
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) = store.save(progress)
        })

        when (progress.qualitySetting) {
            0 -> binding.qualityLow.isChecked = true
            1 -> binding.qualityMedium.isChecked = true
            2 -> binding.qualityHigh.isChecked = true
            else -> binding.qualityAuto.isChecked = true
        }
        binding.qualityGroup.setOnCheckedChangeListener { _, checkedId ->
            progress.qualitySetting = when (checkedId) {
                R.id.qualityLow -> 0
                R.id.qualityMedium -> 1
                R.id.qualityHigh -> 2
                else -> -1
            }
            store.save(progress)
        }

        binding.resetButton.setOnClickListener { confirmReset() }
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_reset)
            .setMessage(R.string.settings_reset_confirm)
            .setPositiveButton(R.string.settings_reset) { _, _ ->
                progress = Progress.fresh()
                store.save(progress)
                bind()
                Toast.makeText(this, R.string.settings_reset_done, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
