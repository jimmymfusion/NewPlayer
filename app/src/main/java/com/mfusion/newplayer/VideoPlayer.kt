package com.mfusion.newplayer

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Log
import android.view.Surface
import android.opengl.GLSurfaceView
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.Executors

class VideoPlayer(private val context: Context, private val glSurfaceView: GLSurfaceView) {
    private val TAG = "VideoPlayer"
    private var mediaPlayer: MediaPlayer? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null
    private var textureId: Int = 0
    @Volatile private var isPlaying = false
    private var isMuted = AtomicBoolean(false)
    private var currentVideoPath: String? = null
    private val videoFiles = mutableListOf<String>()
    private var currentVideoIndex = 0
    private var tempFile: File? = null
    private val CACHE_DIR = "video_cache"
    private val MAX_CACHE_SIZE = 100 * 1024 * 1024 // 100MB max cache size

    @Volatile private var isPreparingOrPlaying = false // 新增标志位

    private val videoExecutor = Executors.newSingleThreadExecutor()

    init {
        try {
            context.assets.list("videos")?.forEach {
                if (it.endsWith(".mp4")) {
                    videoFiles.add(it)
                }
            }
            videoFiles.shuffle()
            Log.d(TAG, "Loaded ${videoFiles.size} video files: ${videoFiles.joinToString()}")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to list videos", e)
        }
    }

    fun createTexture() {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        surfaceTexture = SurfaceTexture(textureId)
        surfaceTexture?.setOnFrameAvailableListener {
            glSurfaceView.requestRender()
        }
    }

    fun getTextureId(): Int = textureId

    fun playNextVideo() {
        if (videoFiles.isEmpty()) {
            Log.e(TAG, "No video files available")
            return
        }

        val videoFile = videoFiles[currentVideoIndex]
        currentVideoIndex = (currentVideoIndex + 1) % videoFiles.size

        videoExecutor.execute {
            try {
                // 清理缓存目录
                cleanupCache()
                // 检查可用空间
                if (!hasEnoughSpace()) {
                    Log.e(TAG, "Not enough space to play video")
                    return@execute
                }
                playVideo(videoFile)
            } catch (e: Exception) {
                Log.e(TAG, "Error playing video: $videoFile", e)
            }
        }
    }

    private fun hasEnoughSpace(): Boolean {
        val cacheDir = File(context.cacheDir, CACHE_DIR)
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        
        val freeSpace = cacheDir.freeSpace
        return freeSpace > 50 * 1024 * 1024 // 至少需要50MB空间
    }

    private fun cleanupCache() {
        val cacheDir = File(context.cacheDir, CACHE_DIR)
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
            return
        }

        // 删除所有缓存文件
        cacheDir.listFiles()?.forEach { it.delete() }

        // 如果缓存目录总大小超过限制，删除最旧的文件
        var totalSize = 0L
        val files = cacheDir.listFiles()?.sortedBy { it.lastModified() } ?: return
        
        for (file in files) {
            totalSize += file.length()
            if (totalSize > MAX_CACHE_SIZE) {
                file.delete()
            }
        }
    }

    private fun playVideo(videoFile: String) {
        try {
            // 停止当前播放
            mediaPlayer?.release()
            mediaPlayer = null

            // 从assets复制视频文件到缓存（I/O）
            val cacheFile = copyAssetToFile(videoFile)

            // MediaPlayer初始化和prepareAsync都在子线程
            val mp = MediaPlayer()
            mp.setOnPreparedListener {
                isPlaying = true
                it.start()
            }
            mp.setOnCompletionListener {
                isPlaying = false
                playNextVideo()
            }
            mp.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                playNextVideo()
                true
            }
            mp.setDataSource(cacheFile.absolutePath)
            // setSurface 必须在GL线程
            glSurfaceView.queueEvent {
                if (surfaceTexture == null) {
                    createTexture()
                }
                mp.setSurface(Surface(surfaceTexture))
                mp.setVolume(if (isMuted.get()) 0f else 1f, if (isMuted.get()) 0f else 1f)
                mp.prepareAsync()
                mediaPlayer = mp
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing video: $videoFile", e)
            playNextVideo()
        }
    }

    private fun copyAssetToFile(assetName: String): File {
        val cacheDir = File(context.cacheDir, CACHE_DIR)
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        val outputFile = File(cacheDir, "temp_${System.currentTimeMillis()}.mp4")
        
        try {
            context.assets.open("videos/$assetName").use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }
            return outputFile
        } catch (e: IOException) {
            Log.e(TAG, "Error copying asset to file: $assetName", e)
            throw e
        }
    }

    fun toggleMute() {
        isMuted.set(!isMuted.get())
        mediaPlayer?.setVolume(if (isMuted.get()) 0f else 1f, if (isMuted.get()) 0f else 1f)
    }

    fun isMuted(): Boolean = isMuted.get()

    fun release() {
        try {
            mediaPlayer?.release()
            mediaPlayer = null
            surfaceTexture?.release()
            surfaceTexture = null
            
            // 清理缓存
            cleanupCache()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing resources", e)
        }
    }

    fun updateTexture() {
        surfaceTexture?.updateTexImage()
    }
} 