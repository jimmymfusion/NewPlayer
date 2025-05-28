package com.mfusion.newplayer

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.view.WindowManager

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private var glSurfaceView: GLSurfaceView? = null
    private var renderer: ImageRenderer? = null
    
    private val PERMISSIONS_REQUEST_CODE = 123
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")
        
        // 设置全屏
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        // 检查并请求权限
        if (!hasRequiredPermissions()) {
            requestPermissions()
        } else {
            initializeApp()
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        // 显示权限说明
        Toast.makeText(this, "需要存储权限来播放视频", Toast.LENGTH_LONG).show()
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST_CODE)
    }

    private fun initializeApp() {
        try {
            // 设置横屏
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            
            // 创建 GLSurfaceView
            glSurfaceView = GLSurfaceView(this).apply {
                setEGLContextClientVersion(2)
                preserveEGLContextOnPause = true
                
                // 配置 EGL
                setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                
                // 创建渲染器
                renderer = ImageRenderer(this@MainActivity, this)
                setRenderer(renderer)
                
                // 设置渲染模式
                renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                
                // 设置EGL配置
                setEGLContextClientVersion(2)
                setEGLConfigChooser(true)  // 使用默认配置
            }
            
            setContentView(glSurfaceView)
            
            Log.d(TAG, "onCreate completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            Toast.makeText(this, "初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
            throw e
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initializeApp()
            } else {
                // 权限被拒绝，显示提示并关闭应用
                Toast.makeText(this, "需要存储权限才能运行应用", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Required permissions not granted")
                finish()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        glSurfaceView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        glSurfaceView?.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        renderer?.cleanup()
    }
} 