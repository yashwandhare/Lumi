package com.trace.data.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.trace.core.ai.GemmaModel
import com.trace.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** What happened. Distinguished so the UI can say something true and specific. */
sealed interface DownloadOutcome {
    data object Success : DownloadOutcome

    /** Retrying is worth offering: no network, a dropped connection, a 5xx. */
    data class Retryable(val reason: String) : DownloadOutcome

    /** Retrying will fail identically: no disk space, a 404, a bad digest. */
    data class Permanent(val reason: String) : DownloadOutcome
}

/**
 * Fetches the model once, resumably.
 *
 * `HttpURLConnection` rather than a new HTTP dependency: this makes exactly one GET, needs one
 * request header, and adding OkHttp to the APK for that would be weight without benefit. If Phase 4
 * needs a real HTTP client for something else, revisit then.
 *
 * **Resumable, because 1.9GB on a phone connection will be interrupted.** Bytes append to
 * `ModelStore.partialFile` and a resume asks for `Range: bytes=N-`. A server that ignores the header
 * and replies 200 instead of 206 is handled by starting over rather than by appending a second copy
 * of the file to the first — silent corruption is worse than a slow retry.
 */
@Singleton
class ModelDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: ModelStore,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    /**
     * @param onProgress called with bytes-on-disk, the total, and a smoothed recent rate in bytes
     *   per second — null until there is enough of a sample to report honestly. Total is
     *   [GemmaModel.SIZE_BYTES] rather than whatever the server reports, so a truncated response
     *   cannot make the bar look finished.
     */
    suspend fun download(
        onProgress: (downloaded: Long, total: Long, bytesPerSecond: Long?) -> Unit,
    ): DownloadOutcome =
        withContext(io) {
            if (store.isReady()) return@withContext DownloadOutcome.Success
            if (!hasNetwork()) {
                return@withContext DownloadOutcome.Retryable("No internet connection.")
            }
            if (!hasRoomForModel()) {
                return@withContext DownloadOutcome.Permanent(
                    "Not enough free space. Trace needs ${GemmaModel.HUMAN_SIZE} plus a little room to unpack."
                )
            }

            try {
                fetch(onProgress)
            } catch (io: IOException) {
                // The partial file is left in place on purpose: the next attempt resumes from it.
                DownloadOutcome.Retryable(io.message ?: "The download was interrupted.")
            }
        }

    private suspend fun fetch(
        onProgress: (Long, Long, Long?) -> Unit,
    ): DownloadOutcome {
        var alreadyHave = store.partialBytes()
        if (alreadyHave > GemmaModel.SIZE_BYTES) {
            // Longer than the real file means it is not the real file. Start clean.
            store.partialFile.delete()
            alreadyHave = 0L
        }

        val connection = (URL(GemmaModel.URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            if (alreadyHave > 0) setRequestProperty("Range", "bytes=$alreadyHave-")
        }

        try {
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_PARTIAL -> Unit
                HttpURLConnection.HTTP_OK -> {
                    // The server ignored our Range. Whatever is on disk cannot be appended to this
                    // stream, so discard it rather than concatenating two overlapping copies.
                    if (alreadyHave > 0) {
                        store.partialFile.delete()
                        alreadyHave = 0L
                    }
                }
                HttpURLConnection.HTTP_NOT_FOUND, HttpURLConnection.HTTP_GONE ->
                    return DownloadOutcome.Permanent(
                        "Trace could not find the model file on the server."
                    )
                HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN ->
                    // Loud on purpose. A tokenless model is a hard rule, so this means the URL is
                    // wrong, not that a credential is missing.
                    return DownloadOutcome.Permanent(
                        "The model download now requires an account, which Trace will not ask you for."
                    )
                else -> return DownloadOutcome.Retryable("The server replied $code.")
            }

            store.partialFile.outputStream().channel.use { channel ->
                channel.position(alreadyHave)
                connection.inputStream.use { input ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    var written = alreadyHave
                    var lastReported = 0L
                    val rate = TransferRate()
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        channel.write(java.nio.ByteBuffer.wrap(buffer, 0, read))
                        written += read
                        // Throttled: a callback per 64KB chunk would be tens of thousands of UI updates.
                        if (written - lastReported >= PROGRESS_STEP_BYTES) {
                            lastReported = written
                            onProgress(written, GemmaModel.SIZE_BYTES, rate.sample(written))
                        }
                    }
                    onProgress(written, GemmaModel.SIZE_BYTES, rate.sample(written))
                }
            }
        } finally {
            connection.disconnect()
        }

        if (store.partialBytes() != GemmaModel.SIZE_BYTES) {
            return DownloadOutcome.Retryable("The download ended early.")
        }
        return if (store.verifyAndCommit()) {
            DownloadOutcome.Success
        } else {
            DownloadOutcome.Permanent(
                "The downloaded model failed its integrity check and was discarded."
            )
        }
    }

    private fun hasNetwork(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** Checked before starting, so the user is told up front instead of at 94%. */
    private fun hasRoomForModel(): Boolean {
        val needed = GemmaModel.SIZE_BYTES - store.partialBytes() + HEADROOM_BYTES
        return context.filesDir.usableSpace > needed
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 30_000
        const val READ_TIMEOUT_MS = 60_000
        const val BUFFER_BYTES = 1 shl 16
        const val PROGRESS_STEP_BYTES = 2L shl 20
        const val HEADROOM_BYTES = 256L shl 20
    }
}

/**
 * A smoothed transfer rate.
 *
 * Reports null until [MIN_SAMPLE_MS] has passed, because the first fraction of a second of a download
 * reads as an absurd number — showing "180 MB/s" and then dropping to 4 is worse than showing nothing
 * for a moment.
 *
 * Exponentially smoothed rather than instantaneous: a raw per-chunk rate on a mobile connection
 * flickers hard enough to be unreadable. [SMOOTHING] weights the running value against each new
 * sample, so the number moves but stays legible.
 */
private class TransferRate {

    private var windowStartMs = System.currentTimeMillis()
    private var windowStartBytes = -1L
    private var smoothed: Double? = null

    /** @return bytes per second, or null while the sample is too short to be honest about. */
    fun sample(totalBytes: Long): Long? {
        if (windowStartBytes < 0) windowStartBytes = totalBytes

        val now = System.currentTimeMillis()
        val elapsedMs = now - windowStartMs
        if (elapsedMs < MIN_SAMPLE_MS) return smoothed?.toLong()

        val instant = (totalBytes - windowStartBytes) * 1000.0 / elapsedMs
        smoothed = smoothed?.let { it * (1 - SMOOTHING) + instant * SMOOTHING } ?: instant

        windowStartMs = now
        windowStartBytes = totalBytes
        return smoothed?.toLong()
    }

    private companion object {
        const val MIN_SAMPLE_MS = 700L
        const val SMOOTHING = 0.35
    }
}
