package com.lumi.data.ai

import android.content.Context
import com.lumi.core.ai.ManagedModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where model files live on disk, and the only place that decides whether one is usable.
 *
 * **This class exists to guarantee a model is downloaded once.** 2.6GB over a phone connection is
 * minutes of waiting and real money on a metered plan; re-fetching it because a check was sloppy is
 * the single most annoying bug this feature can have. So the rules are explicit:
 *
 * - A download writes to the `.part` file and is renamed to the real name only when complete. A file at
 *   the real name is therefore never a half-download.
 * - [isReady] is a size comparison plus a recorded digest. It does not re-hash gigabytes on every
 *   launch, because that costs seconds of cold start for a value that cannot have changed.
 * - The digest is verified exactly once, immediately after the bytes land, by [verifyAndCommit].
 *
 * **Location: `filesDir`, not `cacheDir` and not external storage.** `cacheDir` is what Android
 * deletes first under storage pressure, which would silently trigger a re-download. External storage is
 * world-readable and would put the weights somewhere a file manager invites the user to delete.
 * `filesDir` survives an `installDebug` over the same signing key — but **not** an uninstall, and
 * `connectedAndroidTest` uninstalls, which has cost this project its model twice.
 *
 * **Parameterised by [ManagedModel]** rather than hardcoded to one artefact, because there are two now:
 * the generative model and the embedder. One implementation means the digest check and the resumable
 * fetch cannot drift apart between them.
 */
@Singleton
class ModelStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val directory: File
        get() = File(context.filesDir, "models").apply { mkdirs() }

    /** The finished file. Its presence means "complete", by construction. */
    fun fileFor(model: ManagedModel): File = File(directory, model.fileName)

    /** The in-progress download. Resumed from with a Range request, never loaded. */
    fun partialFileFor(model: ManagedModel): File = File(directory, "${model.fileName}.part")

    /** Records the digest of a file already verified, so it is never hashed twice. */
    private fun receiptFileFor(model: ManagedModel): File =
        File(directory, "${model.fileName}.sha256")

    /**
     * Scratch space LiteRT-LM is given for its compiled-kernel cache. Separate from the models so
     * clearing it never touches weights — it is regenerable, they are not.
     *
     * **Currently unused.** Passing a `cacheDir` to `EngineConfig` stopped the model loading on GPU at
     * all, so the harness passes null and the runtime writes its cache beside the model instead. Kept
     * because [clear] must still delete it for anyone upgrading from a build that used it.
     */
    val engineCacheDir: File
        get() = File(context.filesDir, "litertlm-cache").apply { mkdirs() }

    /**
     * Everything on disk that belongs to the models, weights and generated caches together.
     *
     * The runtime writes its compilation caches next to the model file — `xnnpack_cache_*`,
     * `mldrift_program_cache`, `mtp_drafter`, and a handful of small `.bin` files — and on this device
     * they come to roughly 600-850MB on top of the weights. That is a third again more disk than the
     * downloads ask for, so anything reasoning about space has to count them.
     */
    fun bytesOnDisk(): Long =
        (directory.listFiles()?.sumOf { it.length() } ?: 0L) +
            (engineCacheDir.listFiles()?.sumOf { it.length() } ?: 0L)

    /**
     * True when the file on disk is the one expected. Cheap enough for a cold-start check.
     *
     * Deliberately not just `exists()`: a truncated file that survived a crash mid-rename would pass
     * that and then fail inside the native loader, which surfaces to the user as the app being broken
     * rather than as a download that needs retrying.
     */
    fun isReady(model: ManagedModel): Boolean {
        val file = fileFor(model)
        return file.isFile &&
            file.length() == model.sizeBytes &&
            receiptFileFor(model).takeIf { it.isFile }?.readText()?.trim() == model.sha256
    }

    /** How many bytes of a resumable download are already on disk. Zero when there is none. */
    fun partialBytes(model: ManagedModel): Long =
        partialFileFor(model).let { if (it.isFile) it.length() else 0L }

    /**
     * Hash the completed download, and only then publish it.
     *
     * Returns false when the digest does not match, having deleted the bad file — a corrupted multi-
     * gigabyte download must not be left behind to be mistaken for a good one on the next launch.
     */
    fun verifyAndCommit(model: ManagedModel): Boolean {
        val candidate = partialFileFor(model)
        if (!candidate.isFile || candidate.length() != model.sizeBytes) return false

        val digest = candidate.sha256()
        if (!digest.equals(model.sha256, ignoreCase = true)) {
            candidate.delete()
            return false
        }

        val target = fileFor(model)
        target.delete()
        if (!candidate.renameTo(target)) return false
        receiptFileFor(model).writeText(digest)
        return true
    }

    /**
     * Forget everything. Only for an explicit user action — never call this to "fix" a load failure,
     * because a failure to load is usually about the device, not the bytes, and throwing away gigabytes
     * the user waited for is not a recovery strategy.
     *
     * Deletes the whole model directory rather than named files. The runtime writes its compilation
     * caches in there too, and naming files individually left ~600MB of `xnnpack_cache_*` and
     * `mldrift_program_cache` orphaned on disk with nothing that would ever remove them — a "clear the
     * model" action that leaves most of the bytes behind is not one.
     */
    fun clear() {
        directory.deleteRecursively()
        engineCacheDir.deleteRecursively()
    }
}

/** Streamed in 1MB blocks: the whole point is not to hold gigabytes in memory to check them. */
private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { stream ->
        val buffer = ByteArray(1 shl 20)
        while (true) {
            val read = stream.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
