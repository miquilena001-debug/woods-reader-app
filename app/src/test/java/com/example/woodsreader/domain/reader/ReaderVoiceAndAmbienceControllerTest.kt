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
import com.example.woodsreader.core.tts.SmartSleepTimerManager
import com.example.woodsreader.core.tts.SupportedTtsLanguage
import com.example.woodsreader.core.tts.TtsManager
import com.example.woodsreader.core.tts.TtsPresetCatalog
import com.example.woodsreader.core.tts.TtsPresetPack
import com.example.woodsreader.data.datastore.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderVoiceAndAmbienceControllerTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val sleepTimerRemainingFlow = MutableStateFlow<Long?>(0L)
    private val activePresetFlow = MutableStateFlow(AmbiencePreset.OFF)
    private val volumeFlow = MutableStateFlow(0.5f)
    private val selectedVoiceFlow = MutableStateFlow(SherpaVoiceOption.CLAUDE_MX)
    private val downloadStateFlow = MutableStateFlow<SherpaDownloadState>(SherpaDownloadState.Idle)
    private val langFilterFlow = MutableStateFlow(SupportedTtsLanguage.SPANISH)
    private val autoDetectFlow = MutableStateFlow(false)
    private val downloadedIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    private val edgeEnabledFlow = MutableStateFlow(false)
    private val edgeVoiceFlow = MutableStateFlow(EdgeVoiceOption.ALVARO_ES)
    private val builtInLexiconFlow = MutableStateFlow(true)
    private val customRulesFlow = MutableStateFlow<List<PhoneticRule>>(emptyList())

    private lateinit var sleepTimer: SleepTimer
    private lateinit var sherpaModelManager: SherpaModelManager
    private lateinit var ambienceEngine: ForestAmbienceEngine
    private lateinit var ambientSoundscapePlayer: AmbientSoundscapePlayer
    private lateinit var edgeNeuralTtsClient: EdgeNeuralTtsClient
    private lateinit var pronunciationLexiconManager: PronunciationLexiconManager
    private lateinit var ttsPresetCatalog: TtsPresetCatalog
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var ttsManager: TtsManager
    private lateinit var smartSleepTimerManager: SmartSleepTimerManager

    private lateinit var controller: ReaderVoiceAndAmbienceController

    @Before
    fun setUp() {
        sleepTimer = mockk(relaxed = true) {
            every { sleepTimerRemaining } returns sleepTimerRemainingFlow
        }
        sherpaModelManager = mockk(relaxed = true) {
            every { selectedVoice } returns selectedVoiceFlow
            every { downloadState } returns downloadStateFlow
            every { selectedLanguageFilter } returns langFilterFlow
            every { autoDetectBookLanguage } returns autoDetectFlow
            every { downloadedModelIds } returns downloadedIdsFlow
        }
        ambienceEngine = mockk(relaxed = true) {
            every { activePreset } returns activePresetFlow
            every { volume } returns volumeFlow
        }
        ambientSoundscapePlayer = mockk(relaxed = true)
        edgeNeuralTtsClient = mockk(relaxed = true) {
            every { isEnabled } returns edgeEnabledFlow
            every { selectedVoice } returns edgeVoiceFlow
        }
        pronunciationLexiconManager = mockk(relaxed = true) {
            every { builtInEnabled } returns builtInLexiconFlow
            every { customRules } returns customRulesFlow
        }
        ttsPresetCatalog = mockk(relaxed = true)
        userPreferencesRepository = mockk(relaxed = true)
        smartSleepTimerManager = mockk(relaxed = true)
        ttsManager = mockk(relaxed = true) {
            every { smartSleepTimerManager } returns this@ReaderVoiceAndAmbienceControllerTest.smartSleepTimerManager
        }

        controller = ReaderVoiceAndAmbienceController(
            sleepTimer = sleepTimer,
            sherpaModelManager = sherpaModelManager,
            ambienceEngine = ambienceEngine,
            ambientSoundscapePlayer = ambientSoundscapePlayer,
            edgeNeuralTtsClient = edgeNeuralTtsClient,
            pronunciationLexiconManager = pronunciationLexiconManager,
            ttsPresetCatalog = ttsPresetCatalog,
            userPreferencesRepository = userPreferencesRepository,
            ttsManager = ttsManager
        )
    }

    @Test
    fun `test sleep timer flow and start with positive minutes`() {
        assertEquals(0L, controller.sleepTimerRemaining.value)

        var speedChanged = 0f
        var expired = false

        controller.startSleepTimer(
            minutes = 15,
            currentSpeed = 1.25f,
            scope = testScope,
            onSpeedChange = { speedChanged = it },
            onTimerExpired = { expired = true }
        )

        verify { smartSleepTimerManager.setSleepTimer(SleepTimerPreset.OFF) }
        verify {
            sleepTimer.startSleepTimer(
                minutes = 15,
                currentSpeed = 1.25f,
                scope = any(),
                onSpeedChange = any(),
                onTimerExpired = any()
            )
        }
    }

    @Test
    fun `test sleep timer start with end of chapter minutes`() {
        controller.startSleepTimer(
            minutes = -1,
            currentSpeed = 1.0f,
            scope = testScope,
            onSpeedChange = {},
            onTimerExpired = {}
        )

        verify { smartSleepTimerManager.setSleepTimer(SleepTimerPreset.END_OF_CHAPTER) }
        verify {
            sleepTimer.startSleepTimer(
                minutes = -1,
                currentSpeed = 1.0f,
                scope = any(),
                onSpeedChange = any(),
                onTimerExpired = any()
            )
        }
    }

    @Test
    fun `test sleep timer cancel and handleSmartSleepTimerPreset`() {
        var restoredSpeed = 0f
        controller.cancelSleepTimer { restoredSpeed = it }
        verify { smartSleepTimerManager.setSleepTimer(SleepTimerPreset.OFF) }
        verify { sleepTimer.cancelSleepTimer(any()) }

        // When preset is OFF and sleepTimerRemaining is -1L, should cancel and stop ambience
        sleepTimerRemainingFlow.value = -1L
        controller.handleSmartSleepTimerPreset(SleepTimerPreset.OFF) { restoredSpeed = it }
        verify { ambienceEngine.stop() }
    }

    @Test
    fun `test ambience control delegations`() {
        controller.toggleNextAmbience()
        verify { ambienceEngine.cycleNextPreset() }

        controller.setAmbiencePreset(AmbiencePreset.GENTLE_RAIN)
        verify { ambienceEngine.setPreset(AmbiencePreset.GENTLE_RAIN) }

        controller.setAmbienceVolume(0.8f)
        verify { ambienceEngine.setVolume(0.8f) }

        controller.stopAmbience()
        verify { ambienceEngine.stop() }

        controller.onReaderVisibilityChanged(true)
        verify { ambientSoundscapePlayer.onReaderVisibilityChanged(true) }
    }

    @Test
    fun `test sherpa voice selection when already downloaded`() = runTest(testDispatcher) {
        val voice = SherpaVoiceOption.CLAUDE_MX
        every { sherpaModelManager.isVoiceDownloaded(voice) } returns true

        controller.selectOrDownloadSherpaVoice(testScope, voice)

        coVerify { ttsManager.switchSherpaVoice(voice) }
        verify(exactly = 0) { sherpaModelManager.selectVoiceOption(any()) }
    }

    @Test
    fun `test sherpa voice download and dialog control`() = runTest(testDispatcher) {
        val voice = SherpaVoiceOption.CLAUDE_MX
        every { sherpaModelManager.isVoiceDownloaded(voice) } returns false
        coEvery { sherpaModelManager.downloadAndExtractModel() } returns Result.success(Unit)

        controller.selectOrDownloadSherpaVoice(testScope, voice)

        verify { sherpaModelManager.selectVoiceOption(voice) }
        coVerify { sherpaModelManager.downloadAndExtractModel() }
        coVerify { ttsManager.switchSherpaVoice(voice) }

        assertFalse(controller.showSherpaDownloadDialog.value)
        every { sherpaModelManager.isModelReady() } returns false
        controller.updateUseSherpaTts(testScope, true)
        assertTrue(controller.showSherpaDownloadDialog.value)

        controller.dismissSherpaDownloadDialog()
        assertFalse(controller.showSherpaDownloadDialog.value)
    }

    @Test
    fun `test edge voice and pronunciation lexicon delegations`() = runTest(testDispatcher) {
        controller.setUseEdgeNeuralTts(true)
        verify { edgeNeuralTtsClient.setEnabled(true) }

        controller.selectEdgeVoice(EdgeVoiceOption.ELVIRA_ES)
        verify { edgeNeuralTtsClient.setVoice(EdgeVoiceOption.ELVIRA_ES) }

        controller.setBuiltInLexiconEnabled(false)
        verify { pronunciationLexiconManager.setBuiltInEnabled(false) }

        controller.addCustomPhoneticRule("Woods", "Wuts", true)
        verify { pronunciationLexiconManager.addCustomRule("Woods", "Wuts", true) }

        controller.removeCustomPhoneticRule("rule-1")
        verify { pronunciationLexiconManager.removeCustomRule("rule-1") }

        coEvery { ttsPresetCatalog.installPresetPack(TtsPresetPack.PINYIN_SPANISH) } returns 12
        val installed = controller.installTtsPresetPack(TtsPresetPack.PINYIN_SPANISH)
        assertEquals(12, installed)
    }

    @Test
    fun `test onCleared lifecycle cleanup`() {
        var speedRestored = 0f
        controller.onCleared { speedRestored = it }

        verify { ambientSoundscapePlayer.onReaderVisibilityChanged(false) }
        verify { ambienceEngine.stop() }
        verify { sleepTimer.cancelSleepTimer(any()) }
    }
}
