package com.xiangming.wechatbridge.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** One JSON shape for every file the app writes, readable in a field report. */
val BridgeJson: Json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

/**
 * The shared contract writes `2026-09-23T09:04:42Z`: ISO-8601, UTC, whole
 * seconds. `Instant.toString()` would add a fraction whenever nanos are set,
 * which the macOS decoder rejects.
 */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("WeChatBridge.Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(format(value))
    }

    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())

    fun format(value: Instant): String = value.truncatedTo(ChronoUnit.SECONDS).toString()
}

/** Write-then-rename, so a reader never sees half a JSON file. */
object AtomicFiles {
    fun write(file: File, bytes: ByteArray) {
        val parent = file.absoluteFile.parentFile
        parent.mkdirs()
        val temporary = File(parent, ".${file.name}.${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            try {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (ignored: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }

    fun writeText(file: File, text: String) = write(file, text.toByteArray(Charsets.UTF_8))
}
