package com.example.woodsreader.domain.reader

import com.example.woodsreader.core.managers.SleepTimer
import com.example.woodsreader.core.tts.AmbiencePreset
import com.example.woodsreader.core.tts.AmbientSoundscapePlayer
import com.example.woodsreader.core.tts.EdgeNeuralTtsClient
import com.example.woodsreader.core.tts.EdgeVoiceOption
import com.example.woodsreader.core.tts.ForestAmbienceEngine
import com.example.woodsreader.core.tts.PhoneticRule
import com.example.woodsreader.core.tts.PronunciationLexiconManager
import com.example.woodsreader.core.tts.SherpaDownloadState
import com.example.woodsreader.core.tts.SherpaModelManager
import com.example.woodsreader.core.tts.SherpaVoiceOption
import com.example.woodsreader.core.tts.SleepTimerPreset
import com.example.woodsreader.core.tts.SupportedTtsLanguage
import com.example.woodsreader.core.tts.TtsManager
import com.example.woodsreader.core.tts.TtsPresetCatalog
import com.example.woodsreader.core.tts.TtsPresetPack
import com.example.woodsreader.data.datastore.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Controlador de dominio que encapsula la gestión de voces TTS (Sherpa ONNX, Edge Neural),
 * configuración léxica fonética, soundscapes de ambiente y temporizador de apagado (SleepTimer).
 *
 * Desacopla estas responsabilidades de ReaderViewModel, reduciendo la complejidad del constructor
 * y facilitando pruebas unitarias aisladas sin alterar las firmas públicas consumidas por la UI.
 */
@Singleton
class ReaderVoiceAndAmbienceController @Inject constructor(
    private val sleepTimer: SleepTimer,
    private val sherpaModelManager: SherpaModelManager,
    private val ambienceEngine: ForestAmbienceEngine,
    val ambientSoundscapePlayer: AmbientSoundscapePlayer,
    private val edgeNeuralTtsClient: EdgeNeuralTtsClient,
    private val pronunciationLexiconManager: PronunciationLexiconManager,
    private val ttsPresetCatalog: TtsPresetCatalog,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val ttsManager: TtsManager
) {

    // ── Sleep Timer ─────────────────────────────────────────────────────────

    val sleepTimerRemaining: StateFlow<Long?> = sleepTimer.sleepTimerRemaining

    fun startSleepTimer(
        minutes: Int,
        currentSpeed: Float,
        scope: CoroutineScope,
        onSpeedChange: (Float) -> Unit,
        onTimerExpired: () -> Unit
    ) {
        if (minutes == -1) {
            ttsManager.smartSleepTimerManager.setSleepTimer(SleepTimerPreset.END_OF_CHAPTER)
            sleepTimer.startSleepTimer(
                minutes = -1,
                currentSpeed = currentSpeed,
                scope = scope,
                onSpeedChange = onSpeedChange,
                onTimerExpired = {
                    onTimerExpired()
                    ambienceEngine.stop()
                }
            )
        } else if (minutes > 0) {
            ttsManager.smartSleepTimerManager.setSleepTimer(SleepTimerPreset.OFF)
            sleepTimer.startSleepTimer(
                minutes = minutes,
                currentSpeed = currentSpeed,
                scope = scope,
                onSpeedChange = onSpeedChange,
                onTimerExpired = {
                    onTimerExpired()
                    ambienceEngine.stop()
                }
            )
        } else {
            cancelSleepTimer(onSpeedChange)
        }
    }

    fun cancelSleepTimer(onSpeedChange: (Float) -> Unit) {
        ttsManager.smartSleepTimerManager.setSleepTimer(SleepTimerPreset.OFF)
        sleepTimer.cancelSleepTimer(onSpeedChange)
    }

    fun handleSmartSleepTimerPreset(preset: SleepTimerPreset, onSpeedChange: (Float) -> Unit) {
        if (preset == SleepTimerPreset.OFF && sleepTimer.sleepTimerRemaining.value == -1L) {
            sleepTimer.cancelSleepTimer(onSpeedChange)
            ambienceEngine.stop()
        }
    }

    // ── Ambience Soundscapes ────────────────────────────────────────────────

    val activeAmbiencePreset: StateFlow<AmbiencePreset> = ambienceEngine.activePreset
    val ambienceVolume: StateFlow<Float> = ambienceEngine.volume

    fun toggleNextAmbience() {
        ambienceEngine.cycleNextPreset()
    }

    fun setAmbiencePreset(preset: AmbiencePreset) {
        ambienceEngine.setPreset(preset)
    }

    fun setAmbienceVolume(volume: Float) {
        ambienceEngine.setVolume(volume)
    }

    fun stopAmbience() {
        ambienceEngine.stop()
    }

    fun onReaderVisibilityChanged(isVisible: Boolean) {
        ambientSoundscapePlayer.onReaderVisibilityChanged(isVisible)
    }

    // ── Sherpa ONNX Voices ──────────────────────────────────────────────────

    val selectedSherpaVoice: StateFlow<SherpaVoiceOption> = sherpaModelManager.selectedVoice
    val sherpaDownloadState: StateFlow<SherpaDownloadState> = sherpaModelManager.downloadState
    val selectedLanguageFilter: StateFlow<SupportedTtsLanguage> = sherpaModelManager.selectedLanguageFilter
    val autoDetectBookLanguage: StateFlow<Boolean> = sherpaModelManager.autoDetectBookLanguage
    val downloadedModelIds: StateFlow<Set<String>> = sherpaModelManager.downloadedModelIds

    private val _showSherpaDownloadDialog = MutableStateFlow(false)
    val showSherpaDownloadDialog: StateFlow<Boolean> = _showSherpaDownloadDialog.asStateFlow()

    fun isSherpaVoiceDownloaded(option: SherpaVoiceOption): Boolean =
        sherpaModelManager.isVoiceDownloaded(option)

    fun selectOrDownloadSherpaVoice(scope: CoroutineScope, option: SherpaVoiceOption) {
        scope.launch {
            if (sherpaModelManager.isVoiceDownloaded(option)) {
                ttsManager.switchSherpaVoice(option)
            } else {
                sherpaModelManager.selectVoiceOption(option)
                val res = sherpaModelManager.downloadAndExtractModel()
                if (res.isSuccess) {
                    ttsManager.switchSherpaVoice(option)
                }
            }
        }
    }

    fun deleteSherpaVoiceFromReader(scope: CoroutineScope, option: SherpaVoiceOption) {
        scope.launch {
            ttsManager.stop()
            sherpaModelManager.selectVoiceOption(option)
            sherpaModelManager.deleteModel()
            ttsManager.resetSherpa()
        }
    }

    fun switchSherpaVoice(scope: CoroutineScope, option: SherpaVoiceOption) {
        scope.launch {
            ttsManager.switchSherpaVoice(option)
        }
    }

    fun dismissSherpaDownloadDialog() {
        _showSherpaDownloadDialog.value = false
    }

    fun downloadSherpaModel(scope: CoroutineScope) {
        _showSherpaDownloadDialog.value = false
        scope.launch {
            val result = sherpaModelManager.downloadAndExtractModel()
            if (result.isSuccess) {
                userPreferencesRepository.updateUseSherpaTts(true)
                ttsManager.initSherpaIfNeeded()
            }
        }
    }

    fun updateUseSherpaTts(scope: CoroutineScope, enabled: Boolean) {
        scope.launch {
            if (!enabled) {
                userPreferencesRepository.updateUseSherpaTts(false)
            } else {
                if (sherpaModelManager.isModelReady()) {
                    userPreferencesRepository.updateUseSherpaTts(true)
                    ttsManager.initSherpaIfNeeded()
                } else {
                    _showSherpaDownloadDialog.value = true
                }
            }
        }
    }

    fun setSelectedLanguageFilter(language: SupportedTtsLanguage) {
        sherpaModelManager.setSelectedLanguageFilter(language)
    }

    fun setAutoDetectBookLanguage(enabled: Boolean) {
        sherpaModelManager.setAutoDetectBookLanguage(enabled)
    }

    fun deleteDownloadedModel(scope: CoroutineScope, modelId: String) {
        scope.launch {
            ttsManager.stop()
            sherpaModelManager.deleteDownloadedModel(modelId)
            ttsManager.resetSherpa()
        }
    }

    // ── Edge Neural Voices ──────────────────────────────────────────────────

    val useEdgeNeuralTts: StateFlow<Boolean> = edgeNeuralTtsClient.isEnabled
    val selectedEdgeVoice: StateFlow<EdgeVoiceOption> = edgeNeuralTtsClient.selectedVoice

    fun setUseEdgeNeuralTts(enabled: Boolean) {
        edgeNeuralTtsClient.setEnabled(enabled)
    }

    fun selectEdgeVoice(option: EdgeVoiceOption) {
        edgeNeuralTtsClient.setVoice(option)
    }

    // ── Pronunciation Lexicon & Preset Catalog ──────────────────────────────

    val builtInLexiconEnabled: StateFlow<Boolean> = pronunciationLexiconManager.builtInEnabled
    val customPhoneticRules: StateFlow<List<PhoneticRule>> = pronunciationLexiconManager.customRules

    fun setBuiltInLexiconEnabled(enabled: Boolean) {
        pronunciationLexiconManager.setBuiltInEnabled(enabled)
    }

    fun addCustomPhoneticRule(original: String, phonetic: String, caseSensitive: Boolean = false) {
        pronunciationLexiconManager.addCustomRule(original, phonetic, caseSensitive)
    }

    fun removeCustomPhoneticRule(id: String) {
        pronunciationLexiconManager.removeCustomRule(id)
    }

    suspend fun installTtsPresetPack(pack: TtsPresetPack): Int {
        return ttsPresetCatalog.installPresetPack(pack)
    }

    // ── Lifecycle Cleanup ───────────────────────────────────────────────────

    fun onCleared(onSpeedChange: (Float) -> Unit) {
        ambientSoundscapePlayer.onReaderVisibilityChanged(false)
        ambienceEngine.stop()
        cancelSleepTimer(onSpeedChange)
    }
}
