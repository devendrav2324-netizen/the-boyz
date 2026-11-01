package com.hostelattendance.ui

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
import androidx.compose.runtime.mutableStateOf
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
 * High-performance, hardware-accelerated video background player using [TextureView] and [MediaPlayer].
 * - Full visibility without blur, shade, or dimming overlay.
 * - Hardware center-crop aspect ratio scaling (no black bars or distortion).
 * - Silent looping (muted volume).
 * - Automatic pause/resume bound to the Activity/Lifecycle.
 */
@Composable
fun BackgroundVideoPlayer(
    @RawRes videoResId: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var currentSurface by remember { mutableStateOf<Surface?>(null) }

    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }
    var viewWidth by remember { mutableIntStateOf(0) }
    var viewHeight by remember { mutableIntStateOf(0) }

    fun adjustVideoScale(view: TextureView, vWidth: Int, vHeight: Int, tWidth: Int, tHeight: Int) {
        if (vWidth <= 0 || vHeight <= 0 || tWidth <= 0 || tHeight <= 0) return

        val viewRatio = tWidth.toFloat() / tHeight.toFloat()
        val videoRatio = vWidth.toFloat() / vHeight.toFloat()

        var scaleX = 1f
        var scaleY = 1f

        if (videoRatio > viewRatio) {
            // Video is wider than view -> scale width to crop sides
            scaleX = videoRatio / viewRatio
        } else {
            // Video is taller than view -> scale height to crop top/bottom
            scaleY = viewRatio / videoRatio
        }

        val matrix = Matrix()
        matrix.setScale(scaleX, scaleY, tWidth / 2f, tHeight / 2f)
        view.setTransform(matrix)
    }

    // Lifecycle observer: pause on ON_PAUSE, resume on ON_RESUME
    DisposableEffect(lifecycleOwner, mediaPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    try {
                        if (mediaPlayer?.isPlaying == true) {
                            mediaPlayer?.pause()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error pausing background video: ${e.message}")
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    try {
                        mediaPlayer?.start()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error resuming background video: ${e.message}")
                    }
                }
                else -> {}
            }
        }

        val lifecycle = lifecycleOwner.lifecycle
        lifecycle.addObserver(observer)

        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    // Clean up MediaPlayer and Surface on disposal
    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.reset()
                mediaPlayer?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing MediaPlayer: ${e.message}")
            }
            mediaPlayer = null

            try {
                currentSurface?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing Surface: ${e.message}")
            }
            currentSurface = null
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            val tv = TextureView(ctx)
            textureViewRef = tv

            tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                    viewWidth = width
                    viewHeight = height
                    val surface = Surface(st)
                    currentSurface = surface

                    if (mediaPlayer == null) {
                        try {
                            val player = MediaPlayer().apply {
                                val afd = ctx.resources.openRawResourceFd(videoResId)
                                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                                afd.close()

                                setSurface(surface)
                                isLooping = true
                                setVolume(0f, 0f)

                                setOnVideoSizeChangedListener { _, w, h ->
                                    if (w > 0 && h > 0) {
                                        videoWidth = w
                                        videoHeight = h
                                        adjustVideoScale(tv, w, h, viewWidth, viewHeight)
                                    }
                                }

                                setOnPreparedListener { mp ->
                                    videoWidth = mp.videoWidth
                                    videoHeight = mp.videoHeight
                                    adjustVideoScale(tv, mp.videoWidth, mp.videoHeight, viewWidth, viewHeight)
                                    mp.start()
                                }

                                prepareAsync()
                            }
                            mediaPlayer = player
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to initialize MediaPlayer for background video: ${e.message}", e)
                        }
                    } else {
                        mediaPlayer?.setSurface(surface)
                        adjustVideoScale(tv, videoWidth, videoHeight, width, height)
                        try {
                            mediaPlayer?.start()
                        } catch (e: Exception) {
                            Log.w(TAG, "Error restarting playback on surface available: ${e.message}")
                        }
                    }
                }

                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
                    viewWidth = width
                    viewHeight = height
                    adjustVideoScale(tv, videoWidth, videoHeight, width, height)
                }

                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                    try {
                        mediaPlayer?.setSurface(null)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error detaching surface: ${e.message}")
                    }
                    currentSurface?.release()
                    currentSurface = null
                    return true
                }

                override fun onSurfaceTextureUpdated(st: SurfaceTexture) {
                    // New frame rendered
                }
            }

            tv
        },
        update = { tv ->
            adjustVideoScale(tv, videoWidth, videoHeight, viewWidth, viewHeight)
        }
    )
}
