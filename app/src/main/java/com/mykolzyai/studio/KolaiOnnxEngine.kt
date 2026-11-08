package com.mykolzyai.studio

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.LongBuffer
import java.nio.charset.StandardCharsets

/**
 * Optional on-device Kolai inference.
 * The ONNX asset is produced from the validated Kolai checkpoint and is not bundled
 * until the real checkpoint has passed training and inference tests.
 */
object KolaiOnnxEngine {
    private const val MODEL_ASSET = "models/kolai_2m_v1.onnx"
    private const val BOS = 256
    private const val EOS = 257
    private const val CONTEXT_BYTES = 256
    private const val MAX_NEW_BYTES = 180

    private val environment: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    @Volatile private var session: OrtSession? = null
    @Volatile private var cachedModelFile: File? = null

    fun isAvailable(context: Context): Boolean =
        try {
            context.assets.open(MODEL_ASSET).use { true }
        } catch (_: Exception) {
            false
        }

    @Synchronized
    private fun getSession(context: Context): OrtSession {
        session?.let { return it }
        val target = File(context.filesDir, "kolai_2m_v1.onnx")
        if (cachedModelFile != target || !target.isFile) {
            context.assets.open(MODEL_ASSET).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            cachedModelFile = target
        }
        val options = OrtSession.SessionOptions()
        options.setIntraOpNumThreads(2)
        options.setInterOpNumThreads(1)
        val loaded = environment.createSession(target.absolutePath, options)
        options.close()
        session = loaded
        return loaded
    }

    fun generate(
        context: Context,
        message: String,
        history: List<Pair<String, String>> = emptyList(),
        maxNewBytes: Int = MAX_NEW_BYTES
    ): String {
        require(message.isNotBlank()) { "Сообщение не должно быть пустым." }
        val activeSession = getSession(context)
        val turns = history.takeLast(4) + listOf("user" to message)
        val body = turns.joinToString("\n") { (role, text) ->
            (if (role == "user") "Пользователь: " else "Centerion: ") + text
        }
        // Preserve the task header and BOS within the model's 256-byte context.
        val boundedBody = body.toByteArray(StandardCharsets.UTF_8)
            .takeLast(150).toByteArray().toString(StandardCharsets.UTF_8)
        val prompt = "### Instruction:\n$boundedBody\n### Response:\n"
        val ids = ArrayList<Int>(CONTEXT_BYTES)
        ids.add(BOS)
        prompt.toByteArray(StandardCharsets.UTF_8).takeLast(CONTEXT_BYTES - 1).forEach {
            ids.add(it.toInt() and 0xFF)
        }

        val inputName = activeSession.inputNames.iterator().next()
        val outputName = activeSession.outputNames.iterator().next()
        val output = ByteArrayOutputStream()
        repeat(maxNewBytes.coerceIn(1, 512)) {
            val inputIds = ids.takeLast(CONTEXT_BYTES).map { it.toLong() }.toLongArray()
            val tensor = OnnxTensor.createTensor(
                environment,
                LongBuffer.wrap(inputIds),
                longArrayOf(1L, inputIds.size.toLong())
            )
            val result = activeSession.run(mapOf(inputName to tensor))
            val nextToken = try {
                @Suppress("UNCHECKED_CAST")
                val logits = result.get(outputName).get().value as Array<Array<FloatArray>>
                val last = logits[0][logits[0].lastIndex]
                var bestIndex = 0
                var bestValue = Float.NEGATIVE_INFINITY
                for (i in last.indices) {
                    if (last[i] > bestValue) {
                        bestValue = last[i]
                        bestIndex = i
                    }
                }
                bestIndex
            } finally {
                result.close()
                tensor.close()
            }
            if (nextToken == EOS || nextToken >= 256) return@repeat
            output.write(nextToken)
            ids.add(nextToken)
        }
        return output.toByteArray().toString(StandardCharsets.UTF_8).trim()
            .ifBlank { "Kolai пока не смогла сформировать содержательный ответ." }
    }
}
