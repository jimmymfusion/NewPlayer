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
import android.os.Build
import android.view.ViewGroup
import android.widget.FrameLayout
import android.content.Intent
import android.provider.Settings
import android.app.AlertDialog

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private var glSurfaceView: GLSurfaceView? = null
    private var renderer: ImageRenderer? = null
    
    private val PERMISSIONS_REQUEST_CODE = 123
    private val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_AUDIO
        )
    } else {
        arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")
        
        // 设置全屏
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        // 创建根布局
        val rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        
        setContentView(rootLayout)
        
        // 检查并请求权限
        if (!hasRequiredPermissions()) {
            showPermissionExplanationDialog()
        } else {
            initializeApp(rootLayout)
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun showPermissionExplanationDialog() {
        val message = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            "应用需要访问媒体文件（图片、视频、音频）的权限。请在接下来的对话框中授予权限。"
        } else {
            "应用需要存储权限来播放视频和显示图片。请在接下来的对话框中授予权限。"
        }

        AlertDialog.Builder(this)
            .setTitle("需要权限")
            .setMessage(message)
            .setPositiveButton("授予权限") { _, _ ->
                requestPermissions()
            }
            .setNegativeButton("取消") { _, _ ->
                showSettingsDialog()
            }
            .setCancelable(false)
            .show()
    }

    private fun showSettingsDialog() {
        val message = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            "没有媒体访问权限，应用无法正常工作。是否前往设置页面手动授予权限？"
        } else {
            "没有存储权限，应用无法正常工作。是否前往设置页面手动授予权限？"
        }

        AlertDialog.Builder(this)
            .setTitle("权限被拒绝")
            .setMessage(message)
            .setPositiveButton("去设置") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("退出") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun openAppSettings() {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", packageName, null)
            startActivity(this)
        }
    }

    private fun requestPermissions() {
        Log.d(TAG, "Requesting permissions: ${REQUIRED_PERMISSIONS.joinToString()}")
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST_CODE)
    }

    private fun initializeApp(rootLayout: FrameLayout) {
        try {
            // 设置横屏
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            
            // 创建 GLSurfaceView
            glSurfaceView = GLSurfaceView(this).apply {
                setEGLContextClientVersion(2)
                preserveEGLContextOnPause = true
                
                // 配置 EGL
                setEGLConfigChooser(true)  // 使用默认配置
                
                // 创建渲染器
                renderer = ImageRenderer(this@MainActivity, this)
                setRenderer(renderer)
                
                // 设置渲染模式
                renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                
                // 设置布局参数
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            
            // 添加 GLSurfaceView 到根布局
            rootLayout.addView(glSurfaceView)
            
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
            Log.d(TAG, "Permission results: ${permissions.zip(grantResults.toList())}")
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // 权限已授予，初始化应用
                val rootLayout = findViewById<FrameLayout>(android.R.id.content)
                initializeApp(rootLayout)
            } else {
                // 权限被拒绝，显示设置对话框
                showSettingsDialog()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        // 检查权限状态
        if (hasRequiredPermissions()) {
            glSurfaceView?.onResume()
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        glSurfaceView?.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        renderer?.cleanup()
    }
} 