package com.example.woodsreader.data.datastore

import androidx.datastore.core.Serializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import android.util.Log

object UserPreferencesSerializer : Serializer<UserPreferences> {
    override val defaultValue: UserPreferences = UserPreferences()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    override suspend fun readFrom(input: InputStream): UserPreferences {
        return try {
            json.decodeFromString(
                UserPreferences.serializer(), 
                input.readBytes().decodeToString()
            ).sanitize()
        } catch (exception: SerializationException) {
            // JSON inválido o preferencias corrompidas
            Log.e("UserPrefsSerializer", "Error al leer preferencias: JSON inválido", exception)
            defaultValue
        } catch (exception: IOException) {
            // Archivo corrompido (corte de luz, fallo de almacenamiento, etc.)
            Log.e("UserPrefsSerializer", "Error al leer preferencias: Fallo de I/O", exception)
            defaultValue
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
