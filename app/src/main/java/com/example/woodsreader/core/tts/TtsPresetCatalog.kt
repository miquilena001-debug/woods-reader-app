package com.example.woodsreader.core.tts

import com.example.woodsreader.data.database.TtsRuleDao
import com.example.woodsreader.data.database.TtsRuleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class TtsRuleBackupItem(
    val pattern: String,
    val replacement: String,
    val isRegex: Boolean = false,
    val isEnabled: Boolean = true,
    val bookId: String? = null
)

enum class TtsPresetPack(val title: String, val description: String) {
    PINYIN_SPANISH(
        title = "Fonética Pinyin (Novelas Chinas)",
        description = "Adapta sílabas como Zhao, Xiao, Qing, Xuan, Cheng y Qi a su sonido natural en español."
    ),
    WEB_NOVEL_CLEANER(
        title = "Limpiador de Novelas Web",
        description = "Omite notas entre corchetes [N/T], barras de estado repetitivas y onomatopeyas rotas."
    ),
    EDITORIAL_ABBREVIATIONS(
        title = "Abreviaturas Editoriales",
        description = "Expande Cap., Pág., Sr., Sra., Dr., Gral. y números de capítulo para lectura fluida."
    )
}

@Singleton
class TtsPresetCatalog @Inject constructor(
    private val ttsRuleDao: TtsRuleDao
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun getRulesForPack(pack: TtsPresetPack): List<TtsRuleEntity> {
        return when (pack) {
            TtsPresetPack.PINYIN_SPANISH -> listOf(
                TtsRuleEntity(pattern = "\\bZhao\\b", replacement = "Chao", isRegex = true),
                TtsRuleEntity(pattern = "\\bXiao\\b", replacement = "Shiao", isRegex = true),
                TtsRuleEntity(pattern = "\\bQing\\b", replacement = "Ching", isRegex = true),
                TtsRuleEntity(pattern = "\\bQian\\b", replacement = "Chian", isRegex = true),
                TtsRuleEntity(pattern = "\\bXuan\\b", replacement = "Shuan", isRegex = true),
                TtsRuleEntity(pattern = "\\bXing\\b", replacement = "Shing", isRegex = true),
                TtsRuleEntity(pattern = "\\bZong\\b", replacement = "Dzong", isRegex = true),
                TtsRuleEntity(pattern = "\\bZhang\\b", replacement = "Chang", isRegex = true),
                TtsRuleEntity(pattern = "\\bZheng\\b", replacement = "Cheng", isRegex = true),
                TtsRuleEntity(pattern = "\\bZhou\\b", replacement = "Chou", isRegex = true),
                TtsRuleEntity(pattern = "\\bQi\\b", replacement = "Chi", isRegex = true),
                TtsRuleEntity(pattern = "-chan\\b", replacement = " chan", isRegex = true),
                TtsRuleEntity(pattern = "-sama\\b", replacement = " sama", isRegex = true),
                TtsRuleEntity(pattern = "-kun\\b", replacement = " kun", isRegex = true)
            )
            TtsPresetPack.WEB_NOVEL_CLEANER -> listOf(
                TtsRuleEntity(pattern = "\\[(?:N/T|NT|Nota del traductor)[^\\]]*\\]", replacement = "", isRegex = true),
                TtsRuleEntity(pattern = "\\((?:N/T|NT):[^)]*\\)", replacement = "", isRegex = true),
                TtsRuleEntity(pattern = "[=\\-*_]{3,}", replacement = "", isRegex = true),
                TtsRuleEntity(pattern = "\\bTsk\\b", replacement = "Chasquido", isRegex = true)
            )
            TtsPresetPack.EDITORIAL_ABBREVIATIONS -> listOf(
                TtsRuleEntity(pattern = "\\bCap\\.\\s*", replacement = "Capítulo ", isRegex = true),
                TtsRuleEntity(pattern = "\\bPág\\.\\s*", replacement = "Página ", isRegex = true),
                TtsRuleEntity(pattern = "\\bDr\\.\\s*", replacement = "Doctor ", isRegex = true),
                TtsRuleEntity(pattern = "\\bSr\\.\\s*", replacement = "Señor ", isRegex = true),
                TtsRuleEntity(pattern = "\\bSra\\.\\s*", replacement = "Señora ", isRegex = true)
            )
        }
    }

    suspend fun installPresetPack(pack: TtsPresetPack): Int = withContext(Dispatchers.IO) {
        val existingPatterns = ttsRuleDao.getAllRulesFlow().first().map { it.pattern }.toSet()
        val packRules = getRulesForPack(pack)
        var added = 0
        for (rule in packRules) {
            if (rule.pattern !in existingPatterns) {
                ttsRuleDao.insert(rule)
                added++
            }
        }
        added
    }

    suspend fun exportRulesToJson(): String = withContext(Dispatchers.IO) {
        val rules = ttsRuleDao.getAllRulesFlow().first().map {
            TtsRuleBackupItem(
                pattern = it.pattern,
                replacement = it.replacement,
                isRegex = it.isRegex,
                isEnabled = it.isEnabled,
                bookId = it.bookId
            )
        }
        json.encodeToString(rules)
    }

    suspend fun importRulesFromJson(jsonString: String): Int = withContext(Dispatchers.IO) {
        val imported = runCatching { json.decodeFromString<List<TtsRuleBackupItem>>(jsonString) }
            .getOrDefault(emptyList())
        if (imported.isEmpty()) return@withContext 0
        val existingPatterns = ttsRuleDao.getAllRulesFlow().first().map { it.pattern to it.bookId }.toSet()
        var count = 0
        for (item in imported) {
            if ((item.pattern to item.bookId) !in existingPatterns) {
                ttsRuleDao.insert(
                    TtsRuleEntity(
                        pattern = item.pattern,
                        replacement = item.replacement,
                        isRegex = item.isRegex,
                        isEnabled = item.isEnabled,
                        bookId = item.bookId
                    )
                )
                count++
            }
        }
        count
    }
}