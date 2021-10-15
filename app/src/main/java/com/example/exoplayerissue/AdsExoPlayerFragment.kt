package com.example.exoplayerissue

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import com.google.ads.interactivemedia.v3.api.AdEvent
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.ext.ima.ImaAdsLoader
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.SilenceMediaSource
import com.google.android.exoplayer2.source.ads.AdsMediaSource
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DataSpec
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.Observables
import io.reactivex.subjects.PublishSubject

class AdsExoPlayerFragment : Fragment() {

    companion object {
        const val TAG = "AdsExoPlayerFragment"
    }

    private lateinit var imaAdsLoader: ImaAdsLoader

    // The video player.
    private lateinit var videoPlayerContainer: ViewGroup
    private lateinit var videoPlayerView: PlayerView
    private lateinit var videoPlayer: SimpleExoPlayer
    private lateinit var videoPlayButton: ImageButton
    private lateinit var videoPlayerProgress: ProgressBar
    private lateinit var thumbnailView: ImageView

    private val playSubject by lazy { PublishSubject.create<Boolean>() }
    private val playbackStateSubject by lazy { PublishSubject.create<Int>() }
    private var playDisposable: Disposable? = null

    private val playerEventListener by lazy {
        object : Player.EventListener {
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                super.onPlayerStateChanged(playWhenReady, playbackState)
                Log.d(TAG, "playback State: $playbackState")
                playbackStateSubject.onNext(playbackState)

                val isBuffering = playbackState == Player.STATE_BUFFERING
                val isPlayEnded = playbackState == Player.STATE_IDLE
                        || playbackState == Player.STATE_ENDED
                val isPlaying = thumbnailView.visibility == View.GONE
                thumbnailView.visibility =
                    if ((isBuffering && !isPlaying) || isPlayEnded) View.VISIBLE
                    else View.GONE
                videoPlayerProgress.visibility = if (isBuffering) View.VISIBLE else View.GONE

                if (isPlayEnded) showVideoPlayButton(true)
            }
        }
    }

    private val adsEventListener by lazy {
        val playingAndPauseStatus = listOf(
            AdEvent.AdEventType.STARTED,
            AdEvent.AdEventType.RESUMED,
            AdEvent.AdEventType.PAUSED,
            AdEvent.AdEventType.COMPLETED
        )
        AdEvent.AdEventListener { event ->
            Log.d(TAG, "event type: ${event.type}")
            Log.d(TAG, "isPlayingAd: ${videoPlayer.isPlayingAd}")
            Log.d(TAG, "playbackState: ${videoPlayer.playbackState}")
            Log.d(TAG, "buffer position: ${videoPlayer.bufferedPosition}")
            Log.d(TAG, "buffer percentage: ${videoPlayer.bufferedPercentage}")
            Log.d(TAG, "current position: ${videoPlayer.currentPosition}")
            Log.d(TAG, "content buffer position: ${videoPlayer.contentBufferedPosition}")
            Log.d(TAG, "content position: ${videoPlayer.contentPosition}")
            Log.d(TAG, "duration: ${videoPlayer.duration}")
            Log.d(TAG, "total buffer duration: ${videoPlayer.totalBufferedDuration}")
            Log.d(TAG, "content duration: ${videoPlayer.contentDuration}")
            Log.d(TAG, "content duration: ${videoPlayer.currentTimeline}")

            if (playingAndPauseStatus.contains(event.type)) {
                videoPlayButton.setImageResource(
                    if (event.type != AdEvent.AdEventType.PAUSED
                        && event.type != AdEvent.AdEventType.COMPLETED
                    ) {
                        R.drawable.exo_controls_pause
                    } else {
                        R.drawable.exo_controls_play
                    }
                )
            }

            if (event.type == AdEvent.AdEventType.LOADED) {
                // The below codes will make issues
//                Handler(Looper.getMainLooper()).post {
//                    videoPlayer.playWhenReady = true
//                }

                // If we call the below code, less change to make issues
//                videoPlayer.playWhenReady = true

                // Workaround solution to skip the issues
                playSubject.onNext(true)

                // Another workaround solution. But this still make issues
//                Handler(Looper.getMainLooper()).postDelayed({
//                    videoPlayer.playWhenReady = true
//                }, 500L)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val rootView =
            inflater.inflate(R.layout.fragment_ads_exo_player, container, false) as ViewGroup
        videoPlayerContainer = rootView.findViewById(R.id.video_player_container)
        videoPlayerView = rootView.findViewById(R.id.video_player_view)
        videoPlayerProgress = rootView.findViewById(R.id.video_player_progress)
        videoPlayButton = rootView.findViewById(R.id.video_player_play)
        thumbnailView = rootView.findViewById(R.id.video_player_thumbnail)

        return rootView
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        initPlayer()
        setupVideoController()
    }

    private fun initPlayer() {
        videoPlayer = SimpleExoPlayer.Builder(requireContext()).build()
            .apply { addListener(playerEventListener) }

        imaAdsLoader = ImaAdsLoader.Builder(requireContext())
            .setDebugModeEnabled(true)
            .setAdEventListener(adsEventListener)
            .build()
            .apply { setPlayer(videoPlayer) }

        val mediaSourceWithAds = AdsMediaSource(
            // We only want to play ads video and we don't have any content video
            // Pass empty url here and it would raise MalformedURLException exception but we ignore it
            SilenceMediaSource(0),
            DataSpec(Uri.parse("https://c4d-cdn.adcolony.com/adc/2.14.5/191029/9bb82d82-020a-45ee-bc4a-469a4fbd381f/d306db1b-b0c2-4a55-bc9e-35ae6e1b43c8/vast.xml")),
            Object(),
            DefaultMediaSourceFactory(requireContext()),
            imaAdsLoader,
            videoPlayerView
        )

        videoPlayer.setMediaSource(mediaSourceWithAds)
        videoPlayer.prepare()
        videoPlayerView.player = videoPlayer
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupVideoController() {
        // setOnClickListener would intercept any click events (like Learn More button) to the video view
        // use setOnTouchListener and skip any interception
        videoPlayerView.overlayFrameLayout?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN && isVideoReady()) {
                showVideoPlayButton(videoPlayButton?.visibility == View.GONE)
            }
            false
        }

        videoPlayButton.setOnClickListener {
            val isPlayEnded = videoPlayer.playbackState == Player.STATE_IDLE
                    || videoPlayer.playbackState == Player.STATE_ENDED

            if (isPlayEnded) {
                release()
                initPlayer()
            } else {
                videoPlayer.playWhenReady = !videoPlayer.playWhenReady
            }
        }

        playDisposable = Observables.zip(
            playSubject.filter { it },
            playbackStateSubject.filter { it == Player.STATE_READY || it == Player.STATE_ENDED }
        )
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                videoPlayer.playWhenReady = true
            }
    }

    private fun isVideoReady(): Boolean {
        // player status: buffering -> video -> ended
        // it means not buffering and not play ended
        return videoPlayerProgress.visibility != View.VISIBLE
                && thumbnailView.visibility != View.VISIBLE
    }

    private fun showVideoPlayButton(isShowing: Boolean) {
        videoPlayButton.visibility = if (isShowing) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        // it means we switch to background while playing ads and switch back to foreground
        // ads is paused and we should show play button
        if (isVideoReady()) showVideoPlayButton(true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        release()
    }

    private fun release() {
        videoPlayer.removeListener(playerEventListener)
        videoPlayer.release()
        imaAdsLoader.release()
        playDisposable?.dispose()
    }
}
