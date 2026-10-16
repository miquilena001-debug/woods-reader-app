package com.example.woodsreader.core.managers

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WoodsSupporterManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("woods_supporter_prefs", Context.MODE_PRIVATE)

    private val _isSupporterUnlocked = MutableStateFlow(checkIsUnlocked())
    val isSupporterUnlocked: StateFlow<Boolean> = _isSupporterUnlocked.asStateFlow()

    private val _rewardExpiresAt = MutableStateFlow(prefs.getLong("reward_expires_at", 0L))
    val rewardExpiresAt: StateFlow<Long> = _rewardExpiresAt.asStateFlow()

    private fun checkIsUnlocked(): Boolean {
        val lifetime = prefs.getBoolean("lifetime_supporter", true)
        val expires = prefs.getLong("reward_expires_at", 0L)
        return lifetime || System.currentTimeMillis() < expires
    }

    fun grantVoluntaryRewardPass(hours: Int = 24) {
        require(hours > 0) { "La duración del pase debe ser mayor que cero." }
        val base = maxOf(System.currentTimeMillis(), _rewardExpiresAt.value)
        val newExpiry = base + hours * 3_600_000L
        prefs.edit().putLong("reward_expires_at", newExpiry).apply()
        _rewardExpiresAt.value = newExpiry
        _isSupporterUnlocked.value = true
    }

    fun setLifetimeSupporter(enabled: Boolean) {
        prefs.edit().putBoolean("lifetime_supporter", enabled).apply()
        _isSupporterUnlocked.value = checkIsUnlocked()
    }
}
