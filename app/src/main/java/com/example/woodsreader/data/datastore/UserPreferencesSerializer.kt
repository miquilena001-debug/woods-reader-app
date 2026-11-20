package com.example.woodsreader.data.datastore

import androidx.datastore.core.Serializer
import androidx.datastore.core.CorruptionException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

object UserPreferencesSerializer : Serializer<UserPreferences> {
    override val defaultValue: UserPreferences = UserPreferences()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        coerceInputValues = true
    }

    override suspend fun readFrom(input: InputStream): UserPreferences {
        return try {
            json.decodeFromString(
                UserPreferences.serializer(), 
                input.readBytes().decodeToString()
            ).sanitize()
        } catch (exception: SerializationException) {
            throw CorruptionException("User preferences could not be decoded", exception)
        }
    }

    override suspend fun writeTo(t: UserPreferences, output: OutputStream) {
        withContext(Dispatchers.IO) {
            output.write(
                json.encodeToString(UserPreferences.serializer(), t).encodeToByteArray()
            )
        }
    }
}

private fun UserPreferences.sanitize(): UserPreferences {
    return copy(
        fontSizeSp = fontSizeSp.coerceIn(8f, 50f),
        lineSpacingMultiplier = lineSpacingMultiplier.coerceIn(0.8f, 3.0f),
        horizontalMargin = horizontalMargin.coerceIn(0f, 100f),
        ttsSpeed = ttsSpeed.coerceIn(0.5f, 2.5f)
    )
}
