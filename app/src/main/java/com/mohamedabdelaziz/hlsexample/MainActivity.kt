package com.mohamedabdelaziz.hlsexample

import android.content.DialogInterface
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.location.LocationRequestCompat.Quality
import com.google.android.exoplayer2.ExoPlayerLibraryInfo
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.TrackGroup
import com.google.android.exoplayer2.source.hls.DefaultHlsDataSourceFactory
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.google.android.exoplayer2.upstream.cache.CacheDataSourceFactory
import com.google.android.exoplayer2.upstream.cache.NoOpCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import java.io.File


private const val TAG = "MainActivity"
private val resolutions = mutableListOf<Resolution>(
    Resolution("144p", 256, 144),
    Resolution("240p", 426, 240),
    Resolution("360p", 640, 360),
    Resolution("480p", 854, 480),
    Resolution("720p", 1280, 720),
    Resolution("1080p", 1920, 1080),
    Resolution("1440p", 2560, 1440),
    Resolution("2160p", 3840, 2160),
    Resolution("4320p", 7680, 4320)
)

private const val QUALITY_AUTO = -1

private const val ALLOW_AUTO_CHANGE_QUALITY = true

class MainActivity : AppCompatActivity() {

    private val arrayOfUrls = listOf<String>(
        "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
        "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
        "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
        "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
    )

    private lateinit var player: SimpleExoPlayer
    private lateinit var playerBuilder: SimpleExoPlayer.Builder
    private lateinit var playerView: PlayerView


    val cache by lazy { SimpleCache(File(cacheDir, "media"), NoOpCacheEvictor()) }
    private var qualityIndex = QUALITY_AUTO
    private var currentPosition = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState != null)
            currentPosition = savedInstanceState.getLong("position")
        playerView = findViewById(R.id.playerView)
        initializePlayer()



        findViewById<ImageButton>(R.id.exo_settings).setOnClickListener {
            showSettingsDialog()
        }
        findViewById<ImageButton>(R.id.exo_fullscreen).setOnClickListener {

            currentPosition = player.currentPosition

            toggleFullScreen()

        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        currentPosition = savedInstanceState.getLong("position")

    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("position", currentPosition)
    }

    private fun toggleFullScreen() {
        val orientation =
            if (resources.configuration.orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        requestedOrientation = orientation
    }

    private fun showSettingsDialog() {

        AlertDialog.Builder(this).setTitle("Settings")
            .setItems(
                arrayOf("Quality", "Speed"),
                DialogInterface.OnClickListener { dialog, which ->
                    if (which == 0) showQualitySelectionDialog()
                    else if (which == 1) showSpeedSelectionDialog()
                    return@OnClickListener
                }).show()
    }

    private fun showSpeedSelectionDialog() {
        val speeds = arrayOf("0.25", "0.5", "0.75", "1.0", "1.25", "1.5", "1.75", "2.0")
        AlertDialog.Builder(this).setTitle("Select Speed")
            .setItems(speeds, DialogInterface.OnClickListener { dialog, which ->

                player.playbackParameters =
                    com.google.android.exoplayer2.PlaybackParameters(speeds[which].toFloat())

                return@OnClickListener
            }).show()
    }


    private fun setupQuality() {
        val trackSelector = DefaultTrackSelector(this).apply {
            parameters = if (qualityIndex == QUALITY_AUTO) buildUponParameters()
                .setMaxVideoSizeSd()
                .setAllowVideoMixedMimeTypeAdaptiveness(ALLOW_AUTO_CHANGE_QUALITY)
                .build()
            else buildUponParameters().setMaxVideoSize(
                resolutions[qualityIndex].width,
                resolutions[qualityIndex].height
            ).setAllowVideoMixedMimeTypeAdaptiveness(ALLOW_AUTO_CHANGE_QUALITY)
                .build()
        }


        playerBuilder = SimpleExoPlayer.Builder(this).setTrackSelector(trackSelector);

        player = playerBuilder.build()
    }


    private fun getCacheFactory(): DefaultHlsDataSourceFactory {
        val userAgent = "Android Version 1.0 ExoPlayerLib/" + ExoPlayerLibraryInfo.VERSION

        val cacheDataSourceFactory = CacheDataSourceFactory(
            cache,
            DefaultHttpDataSourceFactory(userAgent)
        )

        return DefaultHlsDataSourceFactory(cacheDataSourceFactory)
    }

    private fun getListOfMediaSources(): ConcatenatingMediaSource {

        val concatenatingMediaSource = ConcatenatingMediaSource()

        arrayOfUrls.forEach {
            val mediaItem = MediaItem.fromUri(it)
            val hlsMediaSource =
                HlsMediaSource.Factory(getCacheFactory()).setAllowChunklessPreparation(true)
                    .createMediaSource(mediaItem)
            concatenatingMediaSource.addMediaSource(hlsMediaSource)
        }
        return concatenatingMediaSource
    }

    private fun initializePlayer(currentPosition: Long = 0) {
        playerView.player?.release()
        setupQuality()
        player.setMediaSource(getListOfMediaSources())
        player.prepare()
        player.playWhenReady = true
        playerView.player = player
        player.seekTo(currentPosition)
//        listenForAvailableQualities()
    }

    private fun listenForAvailableQualities() {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                super.onPlaybackStateChanged(playbackState)

                if (playbackState == Player.STATE_READY) {
                    checkCurrentQuality()
                }
            }
        })
    }

    private fun checkCurrentQuality() {
        resolutions.clear()
        val trackGroups = player.currentTrackGroups
        for (i in 0 until trackGroups.length) {
            val trackGroup: TrackGroup = trackGroups.get(i)
            for (j in 0 until trackGroup.length) {
                if (trackGroup.getFormat(j).height != Format.NO_VALUE) {
                    resolutions.add(
                        Resolution(
                            getResolutionName(trackGroup.getFormat(j)),
                            trackGroup.getFormat(j).width,
                            trackGroup.getFormat(j).height,
                        )
                    )
                }
            }
        }
        resolutions.add(0, Resolution("Auto", 0, 0))

    }


    private fun showQualitySelectionDialog() {
        AlertDialog.Builder(this).setTitle("Select Quality")
            .setItems(
                resolutions.map { it.name }.toTypedArray(),
                DialogInterface.OnClickListener { dialog, which ->
                    if (which == 0)
                        qualityIndex = QUALITY_AUTO
                    else
                        qualityIndex = which
                    currentPosition = player.currentPosition
                    initializePlayer(currentPosition)
                    return@OnClickListener
                }).show()
    }

    private fun getResolutionName(format: Format): String {
        if (format.width == Format.NO_VALUE || format.height == Format.NO_VALUE) {
            return "Unknown"
        }

        val width = format.width
        val height = format.height

        return when {
            width >= 1920 -> "${width}p"
            width >= 1280 -> "720p"
            width >= 854 -> "480p"
            else -> "${width}x$height"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cache.release()
        player.release()
    }

}