package com.hostelattendance.ui

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.annotation.RawRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private const val TAG = "BackgroundVideoPlayer"

/**
 * Singleton manager for the application background video player.
 * Keeps the [MediaPlayer] continuously prepared and playing in memory across all
 * navigation and tab transitions, completely eliminating reloading, buffering lag,
 * and audio/video hitching.
 */
object AppBackgroundVideoManager {
    private var mediaPlayer: MediaPlayer? = null
    private var isPrepared = false
    private var activeSurface: Surface? = null
    var videoWidth: Int = 0
        private set
    var videoHeight: Int = 0
        private set

    @Synchronized
    fun ensureInitialized(context: Context, @RawRes videoResId: Int) {
        if (mediaPlayer != null) return

        try {
            val player = MediaPlayer().apply {
                val afd = context.resources.openRawResourceFd(videoResId)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()

                isLooping = true
                setVolume(0f, 0f) // Silent loop

                setOnVideoSizeChangedListener { _, w, h ->
                    if (w > 0 && h > 0) {
                        this@AppBackgroundVideoManager.videoWidth = w
                        this@AppBackgroundVideoManager.videoHeight = h
                    }
                }

                setOnPreparedListener { mp ->
                    isPrepared = true
                    this@AppBackgroundVideoManager.videoWidth = mp.videoWidth
                    this@AppBackgroundVideoManager.videoHeight = mp.videoHeight
                    activeSurface?.let { surf ->
                        if (surf.isValid) {
                            try {
                                mp.setSurface(surf)
                                mp.start()
                            } catch (e: Exception) {
                                Log.w(TAG, "Error starting on prepared: ${e.message}")
                            }
                        }
                    }
                }

                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing background video player: ${e.message}", e)
        }
    }

    @Synchronized
    fun attachSurface(surface: Surface) {
        activeSurface = surface
        val player = mediaPlayer ?: return
        if (isPrepared && surface.isValid) {
            try {
                player.setSurface(surface)
                if (!player.isPlaying) {
                    player.start()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error attaching surface to player: ${e.message}")
            }
        }
    }

    @Synchronized
    fun detachSurface() {
        try {
            mediaPlayer?.setSurface(null)
        } catch (e: Exception) {
            Log.w(TAG, "Error detaching surface: ${e.message}")
        }
        activeSurface = null
    }

    @Synchronized
    fun pause() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error pausing video: ${e.message}")
        }
    }

    @Synchronized
    fun resume() {
        try {
            if (isPrepared && activeSurface?.isValid == true && mediaPlayer?.isPlaying == false) {
                mediaPlayer?.start()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error resuming video: ${e.message}")
        }
    }

    @Synchronized
    fun release() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.reset()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing video: ${e.message}")
        }
        mediaPlayer = null
        isPrepared = false
        activeSurface = null
    }
}

/**
 * High-performance, hardware-accelerated video background player using [TextureView].
 * - Full visibility without blur, shade, or dimming overlay.
 * - Hardware center-crop aspect ratio scaling (no black bars or distortion).
 * - Silent continuous looping via [AppBackgroundVideoManager] with zero reloads on tab switches.
 * - Automatic pause/resume bound to the Activity/Lifecycle.
 */
@Composable
fun BackgroundVideoPlayer(
    @RawRes videoResId: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var viewWidth by remember { mutableIntStateOf(0) }
    var viewHeight by remember { mutableIntStateOf(0) }

    fun adjustVideoScale(view: TextureView, vWidth: Int, vHeight: Int, tWidth: Int, tHeight: Int) {
        val vw = if (vWidth > 0) vWidth else AppBackgroundVideoManager.videoWidth
        val vh = if (vHeight > 0) vHeight else AppBackgroundVideoManager.videoHeight
        if (vw <= 0 || vh <= 0 || tWidth <= 0 || tHeight <= 0) return

        val viewRatio = tWidth.toFloat() / tHeight.toFloat()
        val videoRatio = vw.toFloat() / vh.toFloat()

        var scaleX = 1f
        var scaleY = 1f

        if (videoRatio > viewRatio) {
            scaleX = videoRatio / viewRatio
        } else {
            scaleY = viewRatio / videoRatio
        }

        val matrix = Matrix()
        matrix.setScale(scaleX, scaleY, tWidth / 2f, tHeight / 2f)
        view.setTransform(matrix)
    }

    // Ensure player is initialized once
    DisposableEffect(videoResId) {
        AppBackgroundVideoManager.ensureInitialized(context.applicationContext, videoResId)
        onDispose {}
    }

    // Lifecycle observer: pause on ON_PAUSE, resume on ON_RESUME
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> AppBackgroundVideoManager.pause()
                Lifecycle.Event.ON_RESUME -> AppBackgroundVideoManager.resume()
                else -> {}
            }
        }

        val lifecycle = lifecycleOwner.lifecycle
        lifecycle.addObserver(observer)

        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            val tv = TextureView(ctx)

            tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                    viewWidth = width
                    viewHeight = height
                    val surface = Surface(st)
                    AppBackgroundVideoManager.attachSurface(surface)
                    adjustVideoScale(tv, AppBackgroundVideoManager.videoWidth, AppBackgroundVideoManager.videoHeight, width, height)
                }

                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
                    viewWidth = width
                    viewHeight = height
                    adjustVideoScale(tv, AppBackgroundVideoManager.videoWidth, AppBackgroundVideoManager.videoHeight, width, height)
                }

                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                    AppBackgroundVideoManager.detachSurface()
                    return true
                }

                override fun onSurfaceTextureUpdated(st: SurfaceTexture) {
                    // Frame rendered
                }
            }

            tv
        },
        update = { tv ->
            adjustVideoScale(tv, AppBackgroundVideoManager.videoWidth, AppBackgroundVideoManager.videoHeight, viewWidth, viewHeight)
        }
    )
}
