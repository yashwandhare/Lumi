package com.lumi.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.lumi.core.ai.GemmaModel
import com.lumi.core.ai.managed
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Answers PRD §5's top risk: does this Gemma 4 build actually accept image and audio input?
 *
 * **A test rather than a feature.** Building the image-attach path to find out would pull Phase 4 work
 * forward for a question that a direct call to the runtime answers in seconds. The engine either accepts
 * `Content.ImageBytes` or it throws, and that is the entire finding.
 *
 * This deliberately does **not** assert that multimodality works — it records what the runtime does. If
 * images are rejected, that is a real answer that Phase 4 needs, not a broken test. Only an unexpected
 * *kind* of failure fails the run.
 *
 * Skips rather than fails when the model is absent, since the 2.6GB artefact is not something CI or a
 * fresh device will have.
 */
@RunWith(AndroidJUnit4::class)
class GemmaMultimodalityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun modelFile(): File =
        File(File(context.filesDir, "models"), GemmaModel.FILE_NAME)

    @Test
    fun recordWhetherThisBuildAcceptsImageInput() = runTest {
        val model = modelFile()
        assumeTrue("Model not on device; skipping", model.isFile && model.length() == GemmaModel.managed.sizeBytes)

        // Vision on GPU, following v1's configuration. Audio is left null here: requesting a GPU audio
        // backend is one of the two things that stopped the engine loading at all.
        val engine = Engine(
            EngineConfig(
                modelPath = model.absolutePath,
                backend = Backend.CPU(),
                visionBackend = Backend.GPU(),
                maxNumTokens = 1024,
            )
        )

        val outcome = runCatching {
            engine.initialize()
            engine.createConversation().use { conversation ->
                conversation.sendMessage(
                    Contents.of(
                        Content.ImageBytes(solidColourPng()),
                        Content.Text("Reply with one word: what colour is this image?"),
                    )
                )
            }
        }

        runCatching { engine.close() }

        outcome.fold(
            onSuccess = { message ->
                val reply = message.plainText()
                Log.i(TAG, "IMAGE INPUT ACCEPTED. Reply: ${reply.take(200)}")
                assertTrue("Accepted the image but returned nothing", reply.isNotBlank())
            },
            onFailure = { failure ->
                // Recorded, not failed. "This build is text-only" is the answer Phase 4 needs, and it
                // needs it stated rather than discovered later behind a half-built feature.
                Log.w(TAG, "IMAGE INPUT REJECTED: ${failure.message}", failure)
                assertTrue(
                    "Failed in an unexpected way: ${failure.message}",
                    failure.message?.isNotBlank() == true,
                )
            },
        )
    }

    @Test
    fun recordWhetherThisBuildAcceptsAudioInput() = runTest {
        val model = modelFile()
        assumeTrue("Model not on device; skipping", model.isFile && model.length() == GemmaModel.managed.sizeBytes)

        // Audio on CPU, which is what v1's config comment insists on.
        val engine = Engine(
            EngineConfig(
                modelPath = model.absolutePath,
                backend = Backend.CPU(),
                audioBackend = Backend.CPU(),
                maxNumTokens = 1024,
            )
        )

        val outcome = runCatching {
            engine.initialize()
            engine.createConversation().use { conversation ->
                conversation.sendMessage(
                    Contents.of(
                        Content.AudioBytes(silentWav()),
                        Content.Text("Reply with one word: did you receive audio?"),
                    )
                )
            }
        }

        runCatching { engine.close() }

        outcome.fold(
            onSuccess = { message ->
                val reply = message.plainText()
                Log.i(TAG, "AUDIO INPUT ACCEPTED. Reply: ${reply.take(200)}")
                assertTrue("Accepted the audio but returned nothing", reply.isNotBlank())
            },
            onFailure = { failure ->
                Log.w(TAG, "AUDIO INPUT REJECTED: ${failure.message}", failure)
                assertTrue(
                    "Failed in an unexpected way: ${failure.message}",
                    failure.message?.isNotBlank() == true,
                )
            },
        )
    }

    /** A small solid-colour PNG. Content does not matter; only whether the runtime accepts the format. */
    private fun solidColourPng(): ByteArray {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.rgb(0, 128, 255))
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    /** Half a second of 16kHz mono silence, with a valid RIFF header. */
    private fun silentWav(): ByteArray {
        val sampleRate = 16_000
        val samples = sampleRate / 2
        val dataBytes = samples * 2
        val out = ByteArrayOutputStream()

        fun ascii(value: String) = out.write(value.toByteArray(Charsets.US_ASCII))
        fun le32(value: Int) = repeat(4) { out.write((value shr (it * 8)) and 0xFF) }
        fun le16(value: Int) = repeat(2) { out.write((value shr (it * 8)) and 0xFF) }

        ascii("RIFF"); le32(36 + dataBytes); ascii("WAVE")
        ascii("fmt "); le32(16); le16(1); le16(1)
        le32(sampleRate); le32(sampleRate * 2); le16(2); le16(16)
        ascii("data"); le32(dataBytes)
        repeat(dataBytes) { out.write(0) }
        return out.toByteArray()
    }

    /** The reply's text parts, concatenated. Non-text parts are not part of a spoken answer. */
    private fun com.google.ai.edge.litertlm.Message.plainText(): String =
        contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }

    private companion object {
        const val TAG = "LumiMultimodal"
    }
}
