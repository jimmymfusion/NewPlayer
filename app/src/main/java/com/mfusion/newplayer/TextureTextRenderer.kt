package com.mfusion.newplayer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class TextureTextRenderer(private val context: Context) {
    private val TAG = "TextureTextRenderer"
    private val paint = Paint().apply {
        textSize = 256f  // 设置字体大小
        color = Color.WHITE  // 设置文字颜色为白色
        isAntiAlias = true  // 启用抗锯齿
        textAlign = Paint.Align.LEFT  // 文字左对齐
        typeface = Typeface.DEFAULT_BOLD  // 使用粗体字
    }

    // 添加边距常量
    private val PADDING_X = 100f  // 水平边距
    private val PADDING_Y = 20f   // 垂直边距

    fun createTextTexture(text: String, height: Int): Pair<Bitmap, Float> {
        // 计算文字实际宽度
        val textWidth = paint.measureText(text)
        
        // 创建透明背景的Bitmap，宽度为文字实际宽度加上边距
        val bitmap = Bitmap.createBitmap(
            (textWidth + PADDING_X * 2).toInt(),  // 总宽度 = 文字宽度 + 左右边距
            height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        
        // 计算文字位置，使其垂直居中
        val fontMetrics = paint.fontMetrics
        val y = height / 2f - (fontMetrics.descent + fontMetrics.ascent) / 2f
        
        // 绘制文字，添加水平边距
        canvas.drawText(text, PADDING_X, y, paint)

        // 保存bitmap到文件
        saveBitmapToFile(bitmap, text)
        
        return Pair(bitmap, textWidth + PADDING_X * 2)  // 返回包含边距的总宽度
    }

    private fun saveBitmapToFile(bitmap: Bitmap, text: String) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "ticker_${timestamp}.png"
            
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val tickerDir = File(picturesDir, "TickerTexts")
            if (!tickerDir.exists()) {
                tickerDir.mkdirs()
            }
            
            val file = File(tickerDir, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Log.d(TAG, "Saved ticker text bitmap to: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save ticker text bitmap", e)
        }
    }
} 