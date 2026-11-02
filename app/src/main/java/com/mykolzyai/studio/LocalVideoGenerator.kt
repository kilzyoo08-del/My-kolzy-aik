package com.mykolzyai.studio

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Environment
import android.view.Surface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * On-device procedural MP4 renderer. This creates animated graphic scenes,
 * not neural text-to-video. Frames are encoded locally as H.264.
 */
object LocalVideoGenerator {
    data class ResultFile(val file: File, val width: Int, val height: Int, val durationSeconds: Int)

    fun generate(
        context: Context,
        prompt: String,
        durationSeconds: Int,
        aspectRatio: String,
        imageUri: Uri? = null,
        onProgress: (Int) -> Unit
    ): ResultFile {
        require(prompt.isNotBlank()) { "Сначала опиши видео." }
        require(durationSeconds in 1..10) { "В локальном режиме доступно от 1 до 10 секунд." }

        val (width, height) = when (aspectRatio) {
            "9:16" -> 360 to 640
            "1:1" -> 480 to 480
            else -> 640 to 360
        }
        val fps = 15
        val totalFrames = durationSeconds * fps
        val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(context.filesDir, "movies")
        if (!outputDir.exists() && !outputDir.mkdirs()) error("Не удалось создать папку видео.")
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val output = File(outputDir, "Kolzai_$stamp.mp4")
        val referenceBitmap = imageUri?.let { uri ->
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                stream.mark(64 * 1024)
                BitmapFactory.decodeStream(stream, null, bounds)
                val longest = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
                val desiredSample = kotlin.math.ceil(longest / 1600.0).toInt().coerceAtLeast(1)
                val lowerPower = Integer.highestOneBit(desiredSample)
                val sample = if (lowerPower < desiredSample) lowerPower shl 1 else lowerPower
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                    ?: throw IllegalArgumentException("Формат выбранного изображения не поддерживается.")
            } ?: throw IllegalArgumentException("Не удалось прочитать выбранное фото.")
        }

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 1_500_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        var muxer: MediaMuxer? = null
        var inputSurface: Surface? = null
        var trackIndex = -1
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()

        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = encoder.createInputSurface()
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            encoder.start()

            fun drain(endOfStream: Boolean) {
                if (endOfStream) encoder.signalEndOfInputStream()
                var finished = false
                var emptyPollsAfterEos = 0
                while (!finished) {
                    when (val index = encoder.dequeueOutputBuffer(bufferInfo, 10_000)) {
                        MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!endOfStream) return
                            emptyPollsAfterEos++
                            check(emptyPollsAfterEos < 100) { "Тайм-аут завершения видеокодека." }
                        }
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            check(!muxerStarted) { "Формат кодека изменился повторно." }
                            trackIndex = muxer!!.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        else -> if (index >= 0) {
                            emptyPollsAfterEos = 0
                            val encoded = encoder.getOutputBuffer(index)
                            if (encoded != null && bufferInfo.size > 0 && muxerStarted) {
                                encoded.position(bufferInfo.offset)
                                encoded.limit(bufferInfo.offset + bufferInfo.size)
                                muxer!!.writeSampleData(trackIndex, encoded, bufferInfo)
                            }
                            val eos = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            encoder.releaseOutputBuffer(index, false)
                            if (eos) finished = true
                        }
                    }
                }
            }

            val surface = inputSurface
            for (frame in 0 until totalFrames) {
                val canvas = surface.lockCanvas(null)
                try {
                    drawFrame(canvas, width, height, frame, totalFrames, prompt, referenceBitmap)
                } finally {
                    surface.unlockCanvasAndPost(canvas)
                }
                drain(false)
                // Surface input timestamps are generated by the producer; pace frames to the target FPS.
                Thread.sleep(1_000L / fps)
                onProgress(((frame + 1) * 95f / totalFrames).toInt())
            }
            drain(true)
            check(muxerStarted) { "Кодек не подготовил MP4-дорожку." }
            onProgress(100)
            return ResultFile(output, width, height, durationSeconds)
        } catch (error: Throwable) {
            output.delete()
            referenceBitmap?.recycle()
            throw error
        } finally {
            runCatching { encoder.stop() }
            runCatching { inputSurface?.release() }
            runCatching { encoder.release() }
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            if (referenceBitmap?.isRecycled == false) referenceBitmap.recycle()
        }
    }

    private fun drawFrame(canvas: Canvas, width: Int, height: Int, frame: Int, total: Int, prompt: String, photo: Bitmap?) {
        val progress = frame.toFloat() / total.coerceAtLeast(1)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val background = Paint().apply {
            shader = android.graphics.LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(Color.rgb(13, 18, 43), Color.rgb(64, 35, 94), Color.rgb(15, 91, 105)),
                null, android.graphics.Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), background)
        if (photo != null && !photo.isRecycled) {
            val scale = maxOf(width.toFloat() / photo.width, height.toFloat() / photo.height)
            val cropWidth = width / scale
            val cropHeight = height / scale
            val left = ((photo.width - cropWidth) / 2f).toInt()
            val top = ((photo.height - cropHeight) / 2f).toInt()
            val source = Rect(left, top, (left + cropWidth).toInt().coerceAtMost(photo.width),
                (top + cropHeight).toInt().coerceAtMost(photo.height))
            canvas.drawBitmap(photo, source, Rect(0, 0, width, height), Paint(Paint.ANTI_ALIAS_FLAG))
            paint.color = Color.argb(105, 5, 8, 20)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }

        val centerX = width * (0.5f + 0.12f * sin(progress * Math.PI * 2).toFloat())
        val centerY = height * (0.42f + 0.08f * cos(progress * Math.PI * 2).toFloat())
        val radius = minOf(width, height) * (0.12f + 0.025f * sin(progress * Math.PI * 4).toFloat())
        paint.color = Color.argb(65, 170, 145, 255)
        canvas.drawCircle(centerX, centerY, radius * 2.1f, paint)
        paint.color = Color.rgb(173, 153, 255)
        canvas.drawCircle(centerX, centerY, radius, paint)
        paint.color = Color.WHITE
        for (i in 0 until 28) {
            val angle = i * 2.399f + progress * 2f
            val orbit = radius * (1.7f + (i % 5) * 0.32f)
            val x = centerX + cos(angle) * orbit
            val y = centerY + sin(angle) * orbit
            paint.alpha = 90 + (i % 6) * 25
            canvas.drawCircle(x.toFloat(), y.toFloat(), (2 + i % 3).toFloat(), paint)
        }
        paint.alpha = 255
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = minOf(width, height) * 0.055f
        paint.setShadowLayer(8f, 0f, 2f, Color.BLACK)
        val cleanPrompt = prompt.replace(Regex("\\s+"), " ").trim().take(90)
        val words = cleanPrompt.split(" ")
        val lines = mutableListOf<String>()
        var line = ""
        for (word in words) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > width * 0.82f && line.isNotEmpty()) {
                lines += line
                line = word
            } else line = candidate
        }
        if (line.isNotEmpty()) lines += line
        val startY = height * 0.78f - (lines.size - 1) * paint.textSize * 0.58f
        lines.take(3).forEachIndexed { index, text ->
            canvas.drawText(text, width / 2f, startY + index * paint.textSize * 1.18f, paint)
        }
        paint.textSize = minOf(width, height) * 0.025f
        paint.color = Color.argb(190, 255, 255, 255)
        canvas.drawText("KOLZAI  •  LOCAL VIDEO", width / 2f, height * 0.94f, paint)
        paint.clearShadowLayer()
    }
}
