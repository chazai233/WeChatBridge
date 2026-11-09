package com.xiangming.wechatbridge.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.UUID

enum class Lang {
    ZH, EN;

    companion object {
        fun of(language: String?): Lang = if (language?.lowercase()?.startsWith("zh") == true) ZH else EN
    }
}

/**
 * A scene prompt picked at forward time. A slimmer cut of macOS `WeChatScene`:
 * Android has no group binding or skill library yet, so a scene is a name, an
 * instruction and an output spec.
 */
@Serializable
data class Scene(
    val id: String = UUID.randomUUID().toString().uppercase(),
    val name: String,
    val instruction: String,
    val outputSpec: String = "",
)

@Serializable
data class SceneLibrary(
    val schemaVersion: Int = 1,
    val scenes: List<Scene> = emptyList(),
    @SerialName("defaultSceneID") val defaultSceneId: String? = null,
) {
    fun scene(id: String?): Scene? = scenes.firstOrNull { it.id == id }

    val defaultScene: Scene? get() = scene(defaultSceneId)

    /** Replaces the scene with the same id in place, or appends a new one. */
    fun upsert(scene: Scene): SceneLibrary {
        val index = scenes.indexOfFirst { it.id == scene.id }
        val next = if (index < 0) scenes + scene else scenes.toMutableList().also { it[index] = scene }
        return copy(scenes = next)
    }

    fun remove(id: String): SceneLibrary = copy(
        scenes = scenes.filterNot { it.id == id },
        defaultSceneId = defaultSceneId.takeUnless { it == id },
    )

    fun withDefault(id: String?): SceneLibrary =
        copy(defaultSceneId = id.takeIf { candidate -> scenes.any { it.id == candidate } })

    /** Translate only untouched built-ins; edits, custom scenes and deletions remain as written. */
    fun localizingUneditedStarters(lang: Lang): SceneLibrary {
        val previous = SceneDefaults.starters(if (lang == Lang.ZH) Lang.EN else Lang.ZH).associateBy { it.id }
        val localized = SceneDefaults.starters(lang).associateBy { it.id }
        return copy(scenes = scenes.map { scene ->
            if (scene == previous[scene.id]) localized.getValue(scene.id) else scene
        })
    }

    /** Adds back any starter scene the user deleted, without touching edited ones. */
    fun restoringStarters(lang: Lang): SceneLibrary {
        val existing = scenes.map { it.id }.toSet()
        return copy(scenes = scenes + SceneDefaults.starters(lang).filterNot { it.id in existing })
    }
}

/**
 * Starter scenes. Ids match the macOS/Windows starters so a later sync can
 * line them up; 「群聊总结」 is new on Android and becomes the default. The
 * wording says 「下面的聊天记录」 because Android sends the chat as text after
 * the prompt rather than as an attached ZIP.
 */
object SceneDefaults {
    const val GROUP_SUMMARY = "wechatflow.android.group-summary"
    const val DAILY_SUMMARY = "wechatflow.starter.daily-summary"
    const val CUSTOMER_REVIEW = "wechatflow.starter.customer-review"
    const val PROJECT_SYNC = "wechatflow.starter.project-sync"

    fun library(lang: Lang): SceneLibrary = SceneLibrary(scenes = starters(lang), defaultSceneId = GROUP_SUMMARY)

    fun standardOutputSpec(lang: Lang): String = when (lang) {
        Lang.ZH -> "输出以下六项：1. 要点；2. 结论；3. 待办；4. 风险；5. 负责人；6. 截止时间。没有信息的项目明确写“无”。"
        Lang.EN -> "Output six sections: key points, conclusions, action items, risks, owners, and deadlines. Write \"None\" when a section has no information."
    }

    fun starters(lang: Lang): List<Scene> {
        val spec = standardOutputSpec(lang)
        return when (lang) {
            Lang.ZH -> listOf(
                Scene(GROUP_SUMMARY, "群聊总结", "阅读下面的微信群聊记录，提炼讨论的主要话题、各方观点和最终结论，不要逐条复述。", spec),
                Scene(DAILY_SUMMARY, "日常摘要", "阅读下面的微信聊天记录，按时间线总结重要信息，不要逐条复述。", spec),
                Scene(CUSTOMER_REVIEW, "客户复盘", "阅读下面的聊天记录，聚焦客户需求、业务结论、承诺和下一步推进。", spec),
                Scene(PROJECT_SYNC, "项目周会", "阅读下面的聊天记录，整理项目进展、决策、阻塞和待办。", spec),
            )
            Lang.EN -> listOf(
                Scene(GROUP_SUMMARY, "Group chat summary", "Read the WeChat group chat below. Distil the main topics, each side's views and the final conclusions without repeating every message.", spec),
                Scene(DAILY_SUMMARY, "Daily summary", "Read the WeChat chat below and summarize important information chronologically without repeating every message.", spec),
                Scene(CUSTOMER_REVIEW, "Customer review", "Read the chat below and focus on customer needs, business conclusions, commitments, and next steps.", spec),
                Scene(PROJECT_SYNC, "Project sync", "Read the chat below and organize project progress, decisions, blockers, and action items.", spec),
            )
        }
    }
}

object ScenePrompt {
    private val skillReference = Regex("\\{\\{\\s*skill:([A-Za-z0-9._-]+)\\s*\\}\\}")

    /**
     * Instruction, then the output spec under its heading. `{{skill:id}}` is
     * rendered the way macOS renders a skill the destination cannot load:
     * named, with a note to finish the task directly.
     */
    fun render(scene: Scene, lang: Lang): String? {
        fun expand(text: String) = skillReference.replace(text) { match ->
            val id = match.groupValues[1]
            when (lang) {
                Lang.ZH -> "「$id」技能（技能文件不可用，请直接完成）"
                Lang.EN -> "the “$id” skill (the skill file is unavailable; complete the task directly)"
            }
        }.trim()

        val instruction = expand(scene.instruction)
        val output = expand(scene.outputSpec)
        if (instruction.isEmpty() && output.isEmpty()) return null
        val parts = mutableListOf<String>()
        if (instruction.isNotEmpty()) parts += instruction
        if (output.isNotEmpty()) {
            parts += when (lang) {
                Lang.ZH -> "输出规范：\n$output"
                Lang.EN -> "Output spec:\n$output"
            }
        }
        return parts.joinToString("\n\n")
    }
}

/** `scenes.json`. A missing file seeds the starters; an unreadable one is kept aside, not overwritten. */
class SceneStore(private val file: File) {
    fun load(lang: Lang): SceneLibrary {
        if (!file.isFile) return SceneDefaults.library(lang).also { save(it) }
        return runCatching { BridgeJson.decodeFromString<SceneLibrary>(file.readText()) }.getOrElse {
            file.renameTo(File(file.parentFile, "${file.name}.unreadable-${System.currentTimeMillis()}"))
            SceneDefaults.library(lang).also { save(it) }
        }
    }

    fun save(library: SceneLibrary) {
        AtomicFiles.writeText(file, BridgeJson.encodeToString(library))
    }
}
