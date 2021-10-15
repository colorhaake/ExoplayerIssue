package com.example.exoplayerissue

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class AdsExoPlayerActivity : AppCompatActivity() {

    // Video Player Injections
    private val exoVideoPlayer: AdsExoPlayerFragment by lazy {
        AdsExoPlayerFragment()
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_ads_exo_player)
        setSupportActionBar(findViewById(R.id.toolbar))

        if (state == null) {
            setupPlayer()
        }
    }

    private fun setupPlayer() {
        supportFragmentManager.beginTransaction()
            .add(R.id.player_fragment, exoVideoPlayer)
            .commit()
    }
}
