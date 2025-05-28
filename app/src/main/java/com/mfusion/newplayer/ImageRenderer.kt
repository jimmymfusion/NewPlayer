package com.mfusion.newplayer

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.opengl.GLUtils
import android.view.Choreographer
import android.util.Log
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import android.opengl.GLES11Ext

class ImageRenderer(private val context: Context, private val glSurfaceView: GLSurfaceView) : GLSurfaceView.Renderer {
    private val TAG = "ImageRenderer"
    private val choreographer = Choreographer.getInstance()
    private var frameCallback: Choreographer.FrameCallback? = null

    private var program: Int = 0
    private var positionHandle: Int = 0
    private var colorHandle: Int = 0
    private var mvpMatrixHandle: Int = 0

    // 正交投影矩阵
    private val projectionMatrix = FloatArray(16)
    // 视图矩阵（使用单位矩阵）
    private val viewMatrix = FloatArray(16).apply {
        Matrix.setIdentityM(this, 0)
    }
    // MVP矩阵
    private val mvpMatrix = FloatArray(16)

    // 每个区域的顶点数据（以中心点为基准）
    private val zoneVertexCoords = floatArrayOf(
        -0.5f,  0.5f, 0.0f,  // 左上
        0.5f,  0.5f, 0.0f,  // 右上
        -0.5f, -0.5f, 0.0f,  // 左下
        0.5f, -0.5f, 0.0f   // 右下
    )

    // 三个区域的颜色数据
    private val zoneColors = arrayOf(
        floatArrayOf(1.0f, 0.0f, 0.0f, 1.0f),  // PicZone颜色：红色，不透明
        floatArrayOf(0.0f, 1.0f, 0.0f, 1.0f),  // VideoZone颜色：绿色，不透明
        floatArrayOf(0.0f, 0.0f, 1.0f, 0.5f)   // TickerZone颜色：蓝色，半透明
    )

    // 三个区域的位置（中心坐标）
    private val zonePositions = arrayOf(
        floatArrayOf(-4f, 0f, 0f),     // PicZone位置：左侧
        floatArrayOf(4f, 0f, 0f),      // VideoZone位置：中间
        floatArrayOf(0f, -4f, 0f)       // TickerZone位置：底部
    )

    // 三个区域的尺寸（宽度和高度）
    private val zoneScales = arrayOf(
        floatArrayOf(8.0f, 9.0f),    // PicZone尺寸：较窄但高
        floatArrayOf(8.0f, 9.0f),    // VideoZone尺寸：正方形
        floatArrayOf(16.0f, 1.0f)     // TickerZone尺寸：较宽但扁
    )

    private var vertexBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(zoneVertexCoords.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(zoneVertexCoords)
            position(0)
        }

    // 纹理相关变量
    private var textureProgram: Int = 0
    private var textureHandle: Int = 0
    private var texCoordHandle: Int = 0
    private var currentTextureId: Int = 0
    private var lastTextureUpdateTime: Long = 0
    private val TEXTURE_SWITCH_INTERVAL = 3000L // 5秒
    private var currentTextureIndex = 0
    private val imageFiles = mutableListOf<String>()
    private val textureIds = mutableListOf<Int>()

    // 纹理坐标
    private val texCoords = floatArrayOf(
        0.0f, 0.0f,  // 左上
        1.0f, 0.0f,  // 右上
        0.0f, 1.0f,  // 左下
        1.0f, 1.0f   // 右下
    )

    private var texCoordBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(texCoords.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(texCoords)
            position(0)
        }

    // 文字滚动相关变量
    private var tickerTextureId: Int = 0
    private var tickerOffset = 0f
    private val TICKER_TEXTURE_HEIGHT = 256  // 纹理高度
    private val TICKER_SPEED = 0.2f  // 增加速度
    private val TICKER_SMOOTH_FACTOR = 0.3f  // 平滑因子
    private lateinit var textRenderer: TextureTextRenderer
    private var tickerTextWidth: Float = 0f  // 存储文字实际宽度
    private val TICKER_SPACING = 1.0f  // 文字间距

    // Ticker的着色器程序
    private var tickerProgram: Int = 0
    private var tickerMvpMatrixHandle: Int = 0
    private var tickerPositionHandle: Int = 0
    private var tickerTexCoordHandle: Int = 0
    private var tickerOffsetHandle: Int = 0
    private var tickerTextureHandle: Int = 0
    private var tickerSmoothFactorHandle: Int = 0  // 平滑因子uniform

    // 动画相关
    private var lastFrameTime: Long = 0
    private var totalTime: Float = 0f
    private var targetOffset: Float = 0f  // 目标偏移量
    private var currentOffset: Float = 0f  // 当前偏移量
    private var lastUpdateTime: Long = 0  // 上次更新时间
    private val FRAME_TIME_NANOS = 16666667L  // 60fps 的帧时间（纳秒）

    // FPS计算相关变量
    private var frameCount = 0
    private var lastFpsUpdateTime = System.currentTimeMillis()
    private var currentFps = 0f
    private var fpsTextureId: Int = 0
    private var lastFpsText = ""

    // 缓存着色器变量位置
    private var textureMvpMatrixHandle: Int = 0
    private var texturePositionHandle: Int = 0
    private var textureTexCoordHandle: Int = 0
    private var textureTextureHandle: Int = 0

    // 添加淡入淡出相关变量
    private var fadeProgram: Int = 0
    private var fadeTexture1Handle: Int = 0
    private var fadeTexture2Handle: Int = 0
    private var fadeProgressHandle: Int = 0
    private var fadePositionHandle: Int = 0
    private var fadeTexCoordHandle: Int = 0
    private var fadeMvpMatrixHandle: Int = 0
    private var fadeProgress: Float = 0f
    private var isTransitioning: Boolean = false
    private var transitionStartTime: Long = 0
    private val TRANSITION_DURATION = 2000L // 1秒过渡时间
    private var nextTextureIndex: Int = 0

    private var videoPlayer: VideoPlayer? = null
    private var videoProgram: Int = 0
    private var videoTextureHandle: Int = 0
    private var videoMvpMatrixHandle: Int = 0
    private var videoPositionHandle: Int = 0
    private var videoTexCoordHandle: Int = 0

    private var isFirstSurfaceCreated = true // 新增标志位

    init {
        // 加载图片文件列表
        try {
            context.assets.list("images2")?.forEach {
                if (it.endsWith(".jpg") || it.endsWith(".png")) {
                    imageFiles.add(it)
                }
            }
            imageFiles.shuffle() // 随机顺序
        } catch (e: IOException) {
            Log.e(TAG, "Failed to list images", e)
        }
        videoPlayer = VideoPlayer(context, glSurfaceView)
        
        // 初始化文字渲染器
        textRenderer = TextureTextRenderer(context)
    }

    // 添加读取着色器文件的辅助方法
    private fun readShaderFile(fileName: String): String {
        return try {
            context.assets.open("shaders/$fileName").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Could not read shader file: $fileName", e)
            throw RuntimeException("Could not read shader file: $fileName", e)
        }
    }

    private fun loadTexture(fileName: String): Int {
        val textureHandle = IntArray(1)
        GLES20.glGenTextures(1, textureHandle, 0)

        if (textureHandle[0] != 0) {
            val options = BitmapFactory.Options()
            options.inScaled = false

            try {
                context.assets.open("images2/$fileName").use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream, null, options)

                    if (bitmap != null) {
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0])

                        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

                        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
                        bitmap.recycle()

                        return textureHandle[0]
                    } else {
                        Log.e(TAG, "Failed to decode bitmap: $fileName")
                        GLES20.glDeleteTextures(1, textureHandle, 0)
                        return 0
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to load texture: $fileName", e)
                GLES20.glDeleteTextures(1, textureHandle, 0)
                return 0
            }
        }

        return 0
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.d(TAG, "onSurfaceCreated called. isFirstSurfaceCreated=$isFirstSurfaceCreated")
        
        try {
            // 设置渲染模式为 RENDERMODE_WHEN_DIRTY，只在需要时渲染
            glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY)
            glSurfaceView.preserveEGLContextOnPause = true  // 保持 EGL context
            
            // 清理之前的纹理资源（如果有的话）
            if (textureIds.isNotEmpty()) {
                GLES20.glDeleteTextures(textureIds.size, textureIds.toIntArray(), 0)
                textureIds.clear()
            }

            // 设置背景色
            GLES20.glClearColor(0.2f, 0.2f, 0.2f, 1.0f)

            // 启用混合
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

            // 创建着色器程序
            program = createShaderProgram("square.vert", "square.frag")
            textureProgram = createShaderProgram("texture.vert", "texture.frag")
            tickerProgram = createShaderProgram("ticker.vert", "ticker.frag")
            fadeProgram = createShaderProgram("fade.vert", "fade.frag")
            videoProgram = createShaderProgram("video.vert", "video.frag")

            // 获取着色器变量位置
            initializeShaderHandles()

            // 加载所有纹理
            loadTextures()

            // 创建文字纹理
            tickerTextureId = createTickerTexture("欢迎使用 NewPlayer 播放器！这是一个测试文本，用于演示跑马灯效果。")

            // 创建FPS显示纹理，使用初始值 0
            updateFpsTexture("FPS: 0.0")

            // Initialize video player and start playback only on the first surface creation
            videoPlayer?.createTexture()
            if (isFirstSurfaceCreated) {
                Log.d(TAG, "First surface created, starting video playback.")
                videoPlayer?.playNextVideo()
                isFirstSurfaceCreated = false
            }

            // 启动动画
            startAnimation()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onSurfaceCreated", e)
            throw e
        }
    }

    private fun initializeShaderHandles() {
        // 普通着色器变量
        positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")

        // 纹理着色器变量
        textureMvpMatrixHandle = GLES20.glGetUniformLocation(textureProgram, "uMVPMatrix")
        texturePositionHandle = GLES20.glGetAttribLocation(textureProgram, "vPosition")
        textureTexCoordHandle = GLES20.glGetAttribLocation(textureProgram, "aTexCoord")
        textureTextureHandle = GLES20.glGetUniformLocation(textureProgram, "uTexture")

        // Ticker着色器变量
        tickerMvpMatrixHandle = GLES20.glGetUniformLocation(tickerProgram, "uMVPMatrix")
        tickerPositionHandle = GLES20.glGetAttribLocation(tickerProgram, "vPosition")
        tickerTexCoordHandle = GLES20.glGetAttribLocation(tickerProgram, "aTexCoord")
        tickerOffsetHandle = GLES20.glGetUniformLocation(tickerProgram, "uOffset")
        tickerTextureHandle = GLES20.glGetUniformLocation(tickerProgram, "uTexture")
        tickerSmoothFactorHandle = GLES20.glGetUniformLocation(tickerProgram, "uSmoothFactor")

        // 淡入淡出着色器变量
        fadeTexture1Handle = GLES20.glGetUniformLocation(fadeProgram, "uTexture1")
        fadeTexture2Handle = GLES20.glGetUniformLocation(fadeProgram, "uTexture2")
        fadeProgressHandle = GLES20.glGetUniformLocation(fadeProgram, "uProgress")
        fadePositionHandle = GLES20.glGetAttribLocation(fadeProgram, "vPosition")
        fadeTexCoordHandle = GLES20.glGetAttribLocation(fadeProgram, "aTexCoord")
        fadeMvpMatrixHandle = GLES20.glGetUniformLocation(fadeProgram, "uMVPMatrix")

        // 视频着色器变量
        videoTextureHandle = GLES20.glGetUniformLocation(videoProgram, "uTexture")
        videoMvpMatrixHandle = GLES20.glGetUniformLocation(videoProgram, "uMVPMatrix")
        videoPositionHandle = GLES20.glGetAttribLocation(videoProgram, "vPosition")
        videoTexCoordHandle = GLES20.glGetAttribLocation(videoProgram, "aTexCoord")
    }

    private fun loadTextures() {
        imageFiles.forEach {
            val textureId = loadTexture(it)
            if (textureId != 0) {
                textureIds.add(textureId)
            }
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        try {
            // 设置视口
            GLES20.glViewport(0, 0, width, height)

            // 计算宽高比
            val aspectRatio = width.toFloat() / height.toFloat()

            // 设置正交投影矩阵（使用中心坐标系统）
            Matrix.orthoM(projectionMatrix, 0,
                -8f, 8f,      // left, right
                -4.5f, 4.5f,  // bottom, top
                -1f, 1f)      // near, far

            Log.d(TAG, "Surface changed: $width x $height, aspect ratio: $aspectRatio")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onSurfaceChanged", e)
            throw e
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // 处理淡入淡出过渡
        if (isTransitioning) {
            val currentTime = System.currentTimeMillis()
            val elapsedTime = currentTime - transitionStartTime
            
            if (elapsedTime >= TRANSITION_DURATION) {
                isTransitioning = false
                fadeProgress = 1f
                currentTextureIndex = nextTextureIndex
            } else {
                fadeProgress = elapsedTime.toFloat() / TRANSITION_DURATION
            }

            // 使用淡入淡出着色器绘制
            GLES20.glUseProgram(fadeProgram)
            
            // 设置顶点属性
            GLES20.glEnableVertexAttribArray(fadePositionHandle)
            GLES20.glVertexAttribPointer(fadePositionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
            
            GLES20.glEnableVertexAttribArray(fadeTexCoordHandle)
            GLES20.glVertexAttribPointer(fadeTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)
            
            // 设置纹理
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[currentTextureIndex])
            GLES20.glUniform1i(fadeTexture1Handle, 0)
            
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[nextTextureIndex])
            GLES20.glUniform1i(fadeTexture2Handle, 1)
            
            // 设置进度
            GLES20.glUniform1f(fadeProgressHandle, fadeProgress)
            
            // 设置MVP矩阵
            GLES20.glUniformMatrix4fv(fadeMvpMatrixHandle, 1, false, mvpMatrix, 0)
            
            // 绘制
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            
            // 禁用顶点属性
            GLES20.glDisableVertexAttribArray(fadePositionHandle)
            GLES20.glDisableVertexAttribArray(fadeTexCoordHandle)
        } else {
            // 正常绘制当前纹理
            GLES20.glUseProgram(textureProgram)
            
            // 设置顶点属性
            GLES20.glEnableVertexAttribArray(texturePositionHandle)
            GLES20.glVertexAttribPointer(texturePositionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
            
            GLES20.glEnableVertexAttribArray(textureTexCoordHandle)
            GLES20.glVertexAttribPointer(textureTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)
            
            // 设置纹理
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[currentTextureIndex])
            GLES20.glUniform1i(textureTextureHandle, 0)
            
            // 设置MVP矩阵
            GLES20.glUniformMatrix4fv(textureMvpMatrixHandle, 1, false, mvpMatrix, 0)
            
            // 绘制
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            
            // 禁用顶点属性
            GLES20.glDisableVertexAttribArray(texturePositionHandle)
            GLES20.glDisableVertexAttribArray(textureTexCoordHandle)
        }

        // 检查是否需要切换纹理
        updateTexture()

        // Update video texture
        videoPlayer?.updateTexture()

        // Draw three zones
        for (i in zonePositions.indices) {
            val modelMatrix = FloatArray(16)
            Matrix.setIdentityM(modelMatrix, 0)

            // 先平移到目标位置
            Matrix.translateM(modelMatrix, 0,
                zonePositions[i][0],
                zonePositions[i][1],
                0f)

            // 再进行缩放
            Matrix.scaleM(modelMatrix, 0,
                zoneScales[i][0],  // 宽度
                zoneScales[i][1],  // 高度
                1.0f)

            Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)

            when (i) {
                0 -> { // PicZone
                    if (textureIds.isNotEmpty()) {
                        // 使用纹理着色器
                        GLES20.glUseProgram(textureProgram)
                        
                        // 设置顶点属性
                        GLES20.glEnableVertexAttribArray(texturePositionHandle)
                        GLES20.glVertexAttribPointer(texturePositionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
                        
                        GLES20.glEnableVertexAttribArray(textureTexCoordHandle)
                        GLES20.glVertexAttribPointer(textureTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)
                        
                        // 设置纹理
                        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[currentTextureIndex])
                        GLES20.glUniform1i(textureTextureHandle, 0)
                        
                        // 设置MVP矩阵
                        GLES20.glUniformMatrix4fv(textureMvpMatrixHandle, 1, false, mvpMatrix, 0)
                        
                        // 绘制
                        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                        
                        // 清理
                        GLES20.glDisableVertexAttribArray(texturePositionHandle)
                        GLES20.glDisableVertexAttribArray(textureTexCoordHandle)
                    } else {
                        // 如果没有纹理，使用纯色绘制
                        GLES20.glUseProgram(program)
                        GLES20.glEnableVertexAttribArray(positionHandle)
                        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
                        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
                        GLES20.glUniform4fv(colorHandle, 1, zoneColors[i], 0)
                        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                        GLES20.glDisableVertexAttribArray(positionHandle)
                    }
                }
                1 -> { // VideoZone
                    if (videoPlayer != null) {
                        // Use video shader program
                        GLES20.glUseProgram(videoProgram)

                        // Set MVP matrix
                        GLES20.glUniformMatrix4fv(videoMvpMatrixHandle, 1, false, mvpMatrix, 0)

                        // Set vertex position
                        GLES20.glEnableVertexAttribArray(videoPositionHandle)
                        GLES20.glVertexAttribPointer(videoPositionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

                        // Set texture coordinates
                        GLES20.glEnableVertexAttribArray(videoTexCoordHandle)
                        GLES20.glVertexAttribPointer(videoTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

                        // Bind video texture
                        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoPlayer?.getTextureId() ?: 0)
                        GLES20.glUniform1i(videoTextureHandle, 0)

                        // Draw
                        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

                        // Cleanup
                        GLES20.glDisableVertexAttribArray(videoPositionHandle)
                        GLES20.glDisableVertexAttribArray(videoTexCoordHandle)
                    } else {
                        // 如果没有视频播放器，使用纯色绘制
                        GLES20.glUseProgram(program)
                        GLES20.glEnableVertexAttribArray(positionHandle)
                        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
                        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
                        GLES20.glUniform4fv(colorHandle, 1, zoneColors[i], 0)
                        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                        GLES20.glDisableVertexAttribArray(positionHandle)
                    }
                }
                else -> { // TickerZone
                    // 先绘制背景色
                    GLES20.glUseProgram(program)
                    GLES20.glEnableVertexAttribArray(positionHandle)
                    GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
                    GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
                    GLES20.glUniform4fv(colorHandle, 1, zoneColors[i], 0)
                    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                    GLES20.glDisableVertexAttribArray(positionHandle)

                    // 再绘制滚动文字
                    GLES20.glUseProgram(tickerProgram)
                    
                    // 设置MVP矩阵
                    GLES20.glUniformMatrix4fv(tickerMvpMatrixHandle, 1, false, mvpMatrix, 0)
                    
                    // 设置顶点位置
                    GLES20.glEnableVertexAttribArray(tickerPositionHandle)
                    GLES20.glVertexAttribPointer(tickerPositionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
                    
                    // 设置纹理坐标
                    GLES20.glEnableVertexAttribArray(tickerTexCoordHandle)
                    GLES20.glVertexAttribPointer(tickerTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)
                    
                    // 设置滚动偏移
                    GLES20.glUniform1f(tickerOffsetHandle, tickerOffset)
                    // 设置平滑因子
                    GLES20.glUniform1f(tickerSmoothFactorHandle, TICKER_SMOOTH_FACTOR)
                    
                    // 绑定纹理
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tickerTextureId)
                    GLES20.glUniform1i(tickerTextureHandle, 0)
                    
                    // 绘制
                    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                    
                    // 清理
                    GLES20.glDisableVertexAttribArray(tickerPositionHandle)
                    GLES20.glDisableVertexAttribArray(tickerTexCoordHandle)
                }
            }
        }

        // 绘制FPS显示
        if (fpsTextureId != 0) {
            GLES20.glUseProgram(textureProgram)
            
            // 设置顶点属性
            GLES20.glEnableVertexAttribArray(texturePositionHandle)
            GLES20.glVertexAttribPointer(texturePositionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
            
            GLES20.glEnableVertexAttribArray(textureTexCoordHandle)
            GLES20.glVertexAttribPointer(textureTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)
            
            // 设置纹理
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fpsTextureId)
            GLES20.glUniform1i(textureTextureHandle, 0)
            
            // 设置MVP矩阵
            GLES20.glUniformMatrix4fv(textureMvpMatrixHandle, 1, false, mvpMatrix, 0)
            
            // 绘制
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            
            // 清理
            GLES20.glDisableVertexAttribArray(texturePositionHandle)
            GLES20.glDisableVertexAttribArray(textureTexCoordHandle)
        }
    }

    private fun createShaderProgram(vertexShaderFile: String, fragmentShaderFile: String): Int {
        try {
            val vertexShaderCode = readShaderFile(vertexShaderFile)
            val fragmentShaderCode = readShaderFile(fragmentShaderFile)
            
            val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
            val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
            
            val program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)
            GLES20.glLinkProgram(program)
            
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] != GLES20.GL_TRUE) {
                val error = GLES20.glGetProgramInfoLog(program)
                Log.e(TAG, "Error linking program: $error")
                GLES20.glDeleteProgram(program)
                return 0
            }

            return program
        } catch (e: Exception) {
            Log.e(TAG, "Error creating shader program", e)
            throw e
        }
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        try {
            // 创建着色器
            val shader = GLES20.glCreateShader(type)
            if (shader == 0) {
                Log.e(TAG, "Failed to create shader type $type")
                return 0
            }

            // 加载着色器源代码
            GLES20.glShaderSource(shader, shaderCode)
            // 编译着色器
            GLES20.glCompileShader(shader)
            
            // 检查编译状态
            val compileStatus = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
            if (compileStatus[0] == 0) {
                val error = GLES20.glGetShaderInfoLog(shader)
                Log.e(TAG, "Error compiling shader type $type: $error")
                GLES20.glDeleteShader(shader)
                return 0
            }
            return shader
        } catch (e: Exception) {
            Log.e(TAG, "Error loading shader", e)
            throw e
        }
    }

    private fun createTickerTexture(text: String): Int {
        val textureHandle = IntArray(1)
        GLES20.glGenTextures(1, textureHandle, 0)
        
        if (textureHandle[0] != 0) {
            val (bitmap, width) = textRenderer.createTextTexture(text, TICKER_TEXTURE_HEIGHT)
            tickerTextWidth = width  // 保存文字实际宽度
            
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0])
            // 优化纹理参数
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            bitmap.recycle()
            
            return textureHandle[0]
        }
        return 0
    }

    private fun updateFpsTexture(fpsText: String) {
        // 只在 FPS 变化超过 1 时才更新纹理
        val shouldSkip = if (lastFpsText.startsWith("FPS: ") && fpsText.startsWith("FPS: ")) {
            val oldValue = lastFpsText.substringAfter("FPS: ").toFloatOrNull() ?: 0f
            val newValue = fpsText.substringAfter("FPS: ").toFloatOrNull() ?: 0f
            Math.abs(newValue - oldValue) < 1.0f
        } else false
        if (shouldSkip) return
        lastFpsText = fpsText

        if (fpsTextureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(fpsTextureId), 0)
        }

        val (bitmap, _) = textRenderer.createTextTexture(fpsText, 256)
        val textureHandle = IntArray(1)
        GLES20.glGenTextures(1, textureHandle, 0)
        
        if (textureHandle[0] != 0) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            bitmap.recycle()
            
            fpsTextureId = textureHandle[0]
        }
    }

    // 添加开始过渡的方法
    fun startTransition() {
        if (!isTransitioning) {
            isTransitioning = true
            transitionStartTime = System.currentTimeMillis()
            fadeProgress = 0f
            nextTextureIndex = (currentTextureIndex + 1) % textureIds.size
        }
    }

    private fun updateTexture() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTextureUpdateTime >= TEXTURE_SWITCH_INTERVAL) {
            if (!isTransitioning) {
                startTransition()
            }
            lastTextureUpdateTime = currentTime
        }
    }

    fun toggleVideoMute() {
        videoPlayer?.toggleMute()
    }

    fun isVideoMuted(): Boolean = videoPlayer?.isMuted() ?: false

    private fun startAnimation() {
        frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                // 更新动画状态
                updateAnimationState(frameTimeNanos)
                // 请求渲染
                glSurfaceView.requestRender()
                // 继续监听下一帧
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(frameCallback!!)
    }

    private fun stopAnimation() {
        frameCallback?.let {
            choreographer.removeFrameCallback(it)
            frameCallback = null
        }
    }

    private fun updateAnimationState(frameTimeNanos: Long) {
        // 计算时间差
        val deltaTime = if (lastFrameTime == 0L) {
            FRAME_TIME_NANOS
        } else {
            frameTimeNanos - lastFrameTime
        }
        lastFrameTime = frameTimeNanos
        
        // 更新动画状态
        totalTime += deltaTime / 1_000_000_000.0f
        
        // 更新跑马灯位置
        targetOffset = (totalTime * TICKER_SPEED) % 1.0f
        currentOffset += (targetOffset - currentOffset) * TICKER_SMOOTH_FACTOR
        tickerOffset = currentOffset

        // 更新FPS
        frameCount++
        val currentTimeMillis = System.currentTimeMillis()
        if (currentTimeMillis - lastFpsUpdateTime >= 1000) {  // 每秒更新一次FPS
            currentFps = frameCount * 1000f / (currentTimeMillis - lastFpsUpdateTime)
            frameCount = 0
            lastFpsUpdateTime = currentTimeMillis
            updateFpsTexture(String.format("FPS: %.1f", currentFps))
        }
    }

    fun cleanup() {
        stopAnimation()
        videoPlayer?.release()
        videoPlayer = null
        // Clean up other resources
        if (textureIds.isNotEmpty()) {
            GLES20.glDeleteTextures(textureIds.size, textureIds.toIntArray(), 0)
            textureIds.clear()
        }
        if (fpsTextureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(fpsTextureId), 0)
            fpsTextureId = 0
        }
        if (tickerTextureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(tickerTextureId), 0)
            tickerTextureId = 0
        }
    }
}
