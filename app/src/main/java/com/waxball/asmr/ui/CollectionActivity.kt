package com.waxball.asmr.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.waxball.asmr.R
import com.waxball.asmr.core.BallCatalog
import com.waxball.asmr.core.BallSpec
import com.waxball.asmr.core.Progress
import com.waxball.asmr.databinding.ActivityCollectionBinding

/**
 * 도감. 완파한 볼만 정체를 드러낸다.
 * 색이 아니라 "어떤 소리가 나는 볼인지"를 적어 둔다. 이 앱에서 수집의 실체는 소리다.
 */
class CollectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCollectionBinding
    private lateinit var store: PrefsProgressStore
    private lateinit var progress: Progress

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCollectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Insets.applyBoth(binding.root)
        store = PrefsProgressStore(this)
    }

    override fun onResume() {
        super.onResume()
        progress = store.load()
        binding.collectionCount.text = getString(R.string.collection_count, progress.completed.size)
        buildGrid()
    }

    private fun buildGrid() {
        binding.collectionGrid.removeAllViews()
        val columns = 3
        val cellWidth = (resources.displayMetrics.widthPixels - dp(40) - dp(16)) / columns

        for (spec in BallCatalog.all) {
            val done = spec.id in progress.completed
            val card = card(spec, done)
            val params = GridLayout.LayoutParams().apply {
                width = cellWidth
                height = GridLayout.LayoutParams.WRAP_CONTENT
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            binding.collectionGrid.addView(card, params)
        }
    }

    private fun card(spec: BallSpec, done: Boolean): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = getDrawable(R.drawable.bg_card)
            setPadding(dp(10), dp(12), dp(10), dp(12))
            setOnClickListener { onCardTapped(spec, done) }
        }

        column.addView(TextView(this).apply {
            text = if (done) spec.capsule else "🔒"
            textSize = 26f
            gravity = Gravity.CENTER
        }, wrap())

        val swatch = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (done) spec.shellColor else Color.parseColor("#1A1E25"))
            }
        }
        column.addView(swatch, LinearLayout.LayoutParams(dp(34), dp(34)).apply { topMargin = dp(6) })

        column.addView(TextView(this).apply {
            text = if (done) spec.nameKo else getString(R.string.collection_unknown)
            setTextColor(resources.getColor(if (done) R.color.text_primary else R.color.text_muted, theme))
            textSize = 12f
            gravity = Gravity.CENTER
        }, wrap(top = 8))

        if (done) {
            column.addView(TextView(this).apply {
                text = spec.soundDesc
                setTextColor(resources.getColor(R.color.text_muted, theme))
                textSize = 10f
                gravity = Gravity.CENTER
            }, wrap(top = 4))
        }

        return column
    }

    private fun onCardTapped(spec: BallSpec, done: Boolean) {
        if (progress.isUnlocked(spec.id)) {
            startActivity(
                Intent(this, PlayActivity::class.java)
                    .putExtra(PlayActivity.EXTRA_BALL_ID, spec.id)
                    .putExtra(PlayActivity.EXTRA_MISSION_ID, -1)
            )
            return
        }
        AlertDialog.Builder(this)
            .setTitle(if (done) spec.nameKo else getString(R.string.collection_unknown))
            .setMessage(getString(R.string.collection_locked_hint) + "\n" + getString(R.string.locked_price, spec.price))
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun wrap(top: Int = 0) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(top) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
