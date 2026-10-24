package nz.thys.ashley.llm

import nz.thys.ashley.config.ModelProfile
import nz.thys.ashley.config.Paths
import nz.thys.ashley.core.Msg
import nz.thys.ashley.tools.Tool
import java.io.File

/**
 * On-device GGUF models through llama.cpp (JNI, Adreno OpenCL backend). PHASE 2.
 *
 * Plan:
 *  - add llama.cpp as a git submodule under app/src/main/cpp and build it with CMake
 *    (GGML_OPENCL=ON for the Adreno GPU; Hexagon stays off - experimental)
 *  - a thin JNI bridge: load(path, nCtx, nGpuLayers, nThreads), applyChatTemplate(messagesJson, toolsJson),
 *    generate(prompt, grammar, onToken), free()
 *  - tool calls: chat template with tools + GBNF grammar so a 4B model can't emit broken JSON,
 *    then ToolCallParser.extract on the output
 */
class LlamaCppEngine(override val profile: ModelProfile, private val paths: Paths) : LlmEngine {
    private val file: File? = profile.file?.let { File(paths.root, it) }

    override suspend fun isReachable(): Boolean = false

    override fun notReadyReason(): String =
        if (file?.exists() == true) "llama.cpp engine arrives in phase 2. The model file is in place."
        else "Model file missing: ${profile.file}. Copy it into the app's models folder. The llama.cpp engine arrives in phase 2."

    override suspend fun chat(system: String, messages: List<Msg>, tools: List<Tool>): LlmResult =
        throw IllegalStateException("On-device llama.cpp isn't built yet (phase 2). Pick 'demo' or a PC model in the Model picker.")
}

/** Gemma 4 / Gemma 3n through Google's LiteRT-LM Kotlin API (GPU or NPU). PHASE 5. */
class LiteRtEngine(override val profile: ModelProfile, private val paths: Paths) : LlmEngine {
    override suspend fun isReachable(): Boolean = false

    override fun notReadyReason(): String = "LiteRT-LM engine arrives in phase 5."

    override suspend fun chat(system: String, messages: List<Msg>, tools: List<Tool>): LlmResult =
        throw IllegalStateException("LiteRT-LM isn't added yet (phase 5). Pick 'demo' or a PC model in the Model picker.")
}
