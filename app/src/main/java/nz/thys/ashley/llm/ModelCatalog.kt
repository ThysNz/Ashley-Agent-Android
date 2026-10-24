package nz.thys.ashley.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import nz.thys.ashley.config.ModelProfile
import nz.thys.ashley.config.Paths
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** A model the app knows how to download, plus its models.json entry. */
data class CatalogModel(
    val profile: ModelProfile,
    val url: String,
    val bytes: Long,
    val note: String,
)

object ModelCatalog {

    val gemma4E4b = CatalogModel(
        profile = ModelProfile(
            name = "gemma4-e4b",
            description = "Gemma 4 E4B - default: fast and cool on the GPU, sees images",
            provider = "litert",
            file = "models/gemma-4-E4B-it.litertlm",
            backend = "gpu",
            contextSize = 8192,
            temperature = 0.7,
            maxTokens = 2048,
            supportsTools = true,
            think = false,
        ),
        url = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
        bytes = 3_659_530_240,
        note = "3.7 GB · Apache 2.0 · about 22 words/s on the S26 GPU",
    )

    val qwen35_4b = CatalogModel(
        profile = ModelProfile(
            name = "qwen3.5-4b",
            description = "Qwen3.5 4B - for tricky, multi-step jobs (thinking on demand)",
            provider = "llamacpp",
            file = "models/Qwen3.5-4B-Q4_K_M.gguf",
            backend = "cpu",
            contextSize = 16384,
            temperature = 0.7,
            maxTokens = 2048,
            threads = 6,
            supportsTools = true,
            think = false,
        ),
        url = "https://huggingface.co/unsloth/Qwen3.5-4B-GGUF/resolve/main/Qwen3.5-4B-Q4_K_M.gguf",
        bytes = 2_740_937_888,
        note = "2.7 GB · Apache 2.0 · runs on the CPU (engine is the next build step)",
    )

    val all = listOf(gemma4E4b, qwen35_4b)

    /** Adds any catalogue model missing from the user's models.json (keeps their own entries untouched). */
    fun merge(models: List<ModelProfile>): List<ModelProfile> {
        val missing = all.map { it.profile }.filter { p -> models.none { it.name == p.name } }
        return if (missing.isEmpty()) models else missing + models
    }

    fun forName(name: String) = all.firstOrNull { it.profile.name == name }
}

/**
 * Downloads a model file into the models folder, resuming a partial download (.part) if there is one.
 * [onProgress] gets bytes done and total. Cancel the coroutine to pause; the .part file is kept.
 */
class ModelDownloader(http: OkHttpClient, private val paths: Paths) {

    private val client = http.newBuilder()
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun target(m: CatalogModel) = File(paths.root, m.profile.file!!)

    fun isDownloaded(m: CatalogModel) = target(m).let { it.isFile && it.length() == m.bytes }

    suspend fun download(m: CatalogModel, onProgress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        val dest = target(m)
        dest.parentFile?.mkdirs()
        val part = File(dest.path + ".part")
        val have = if (part.exists()) part.length() else 0L
        val req = Request.Builder().url(m.url).apply { if (have > 0) header("Range", "bytes=$have-") }.build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Download failed: HTTP ${resp.code}")
            val resumed = resp.code == 206
            val total = if (resumed) have + (resp.body?.contentLength() ?: 0) else (resp.body?.contentLength() ?: m.bytes)
            var done = if (resumed) have else 0L
            val body = resp.body ?: throw IllegalStateException("Empty download")
            FileOutputStream(part, resumed).use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(1 shl 16)
                    var lastReport = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (done - lastReport > 8_000_000) { onProgress(done, total); lastReport = done }
                    }
                }
            }
            onProgress(done, total)
        }
        if (part.length() != m.bytes) throw IllegalStateException("Download incomplete (${part.length()} of ${m.bytes} bytes). Tap Download to resume.")
        if (dest.exists()) dest.delete()
        if (!part.renameTo(dest)) throw IllegalStateException("Couldn't save ${dest.name}.")
    }
}
