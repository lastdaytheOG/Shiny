package com.shiny.music.ui.screens.equalizer.axion

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiny.music.eq.EqualizerService
import com.shiny.music.eq.data.EQProfileRepository
import com.shiny.music.eq.data.FilterType
import com.shiny.music.eq.data.ParametricEQBand
import com.shiny.music.eq.data.SavedEQProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AxionEqViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val equalizerService: EqualizerService,
    private val eqProfileRepository: EQProfileRepository
) : ViewModel() {

    private val prefs = context.getSharedPreferences("shiny_eq_prefs", Context.MODE_PRIVATE)

    /**
     * The curve being edited is kept in the repository as its one non-custom profile. One saved
     * there under an earlier id, before these prefs existed, seeds the sliders once and is then
     * moved to [WORKING_PROFILE_ID], so an update never resets someone's EQ.
     */
    private val carriedOver: SavedEQProfile? =
        if (prefs.contains("band_0")) null
        else eqProfileRepository.getAllProfiles().firstOrNull { !it.isCustom && it.id != WORKING_PROFILE_ID }

    /** The Equaliser's master switch (bands, effects and speed), off each time Shiny starts. */
    val enabled = com.shiny.music.eq.fx.SoundFxEngine.enabled

    private val bandFrequencies = doubleArrayOf(31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)

    private val _bandGains = MutableStateFlow(
        FloatArray(10) { i ->
            prefs.getFloat("band_$i", carriedOver?.bands?.getOrNull(i)?.let { (it.gain * 50.0).toFloat() } ?: 0f)
        }
    )
    val bandGains = _bandGains.asStateFlow()

    /** dB before the bands, −12 to +6. */
    private val _preamp = MutableStateFlow(prefs.getFloat("preamp", carriedOver?.preamp?.toFloat() ?: 0f))
    val preamp = _preamp.asStateFlow()

    private val _mode = MutableStateFlow(prefs.getInt("mode", 0)) 
    val mode = _mode.asStateFlow()

    private val _isDirty = MutableStateFlow(false)
    val isDirty = _isDirty.asStateFlow()

    val customProfiles = eqProfileRepository.profiles.map { profiles ->
        profiles.filter { it.isCustom }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        carriedOver?.let { old ->
            prefs.edit().apply {
                _bandGains.value.forEachIndexed { i, f -> putFloat("band_$i", f) }
                putFloat("preamp", _preamp.value)
            }.apply()
            viewModelScope.launch {
                val wasActive = eqProfileRepository.getActiveProfile()?.id == old.id
                eqProfileRepository.saveProfile(old.copy(id = WORKING_PROFILE_ID, name = "Shiny Tuning"))
                if (wasActive) eqProfileRepository.setActiveProfile(WORKING_PROFILE_ID)
                eqProfileRepository.deleteProfile(old.id)
            }
        }
    }

    /**
     * Off keeps the chosen curve for next time: the service applies it only while the master
     * switch is on, so off needs nothing but the switch.
     */
    fun setEnabled(enabled: Boolean) {
        com.shiny.music.eq.fx.SoundFxEngine.setEnabled(enabled)
        if (enabled) applyToService()
    }

    /** A hand edit is asking to hear it: the Equaliser turns on. */
    private fun edited() {
        com.shiny.music.eq.fx.SoundFxEngine.setEnabled(true)
        applyToService()
    }

    fun setMode(mode: Int) {
        _mode.value = mode
        prefs.edit().putInt("mode", mode).apply()
        _isDirty.value = false 
    }

    fun setBandGain(index: Int, gain: Float) {
        val newGains = _bandGains.value.copyOf()
        newGains[index] = gain
        _bandGains.value = newGains
        prefs.edit().putFloat("band_$index", gain).apply()
        _isDirty.value = true
        edited()
    }

    fun setBandsGains(gains: FloatArray, fromUser: Boolean = false, turnOn: Boolean = true) {
        _bandGains.value = gains
        val editor = prefs.edit()
        gains.forEachIndexed { index, f -> editor.putFloat("band_$index", f) }
        editor.apply()
        _isDirty.value = fromUser 
        if (turnOn) edited() else if (enabled.value) applyToService()
    }

    fun setPreamp(db: Float) {
        _preamp.value = db.coerceIn(-12f, 6f)
        prefs.edit().putFloat("preamp", _preamp.value).apply()
        edited()
    }

    fun reset() {
        _preamp.value = 0f
        prefs.edit().putFloat("preamp", 0f).apply()
        val flat = FloatArray(10) { 0f }
        // Resetting is not a reason to switch the Equaliser on.
        setBandsGains(flat, turnOn = false)
    }

    fun saveCustomProfile(name: String) {
        viewModelScope.launch {
            val bands = _bandGains.value.mapIndexed { index, f ->
                ParametricEQBand(
                    frequency = bandFrequencies[index],
                    gain = f.toDouble() / 50.0,
                    q = 1.41,
                    filterType = FilterType.PK,
                    enabled = true
                )
            }
            
            val id = "custom_${System.currentTimeMillis()}"
            val profile = SavedEQProfile(
                id = id,
                name = name,
                deviceModel = "Equalizer",
                bands = bands,
                preamp = _preamp.value.toDouble(),
                isCustom = true,
                isActive = true
            )
            
            eqProfileRepository.saveProfile(profile)
            eqProfileRepository.setActiveProfile(profile.id)
            _isDirty.value = false
        }
    }

    fun deleteProfiles(ids: List<String>) {
        viewModelScope.launch {
            ids.forEach { id ->
                eqProfileRepository.deleteProfile(id)
            }
        }
    }

    private fun applyToService() {
        viewModelScope.launch {
            val bands = _bandGains.value.mapIndexed { index, f ->
                ParametricEQBand(
                    frequency = bandFrequencies[index],
                    gain = f.toDouble() / 50.0, 
                    q = 1.41,
                    filterType = FilterType.PK,
                    enabled = true
                )
            }
            
            val profile = SavedEQProfile(
                id = WORKING_PROFILE_ID,
                name = "Shiny Tuning",
                deviceModel = "Equalizer",
                bands = bands,
                preamp = _preamp.value.toDouble(),
                isCustom = false,
                isActive = true
            )
            
            
            eqProfileRepository.saveProfile(profile)
            eqProfileRepository.setActiveProfile(profile.id)
            
            equalizerService.applyProfile(profile)
        }
    }

    private companion object {
        const val WORKING_PROFILE_ID = "shiny_tuning"
    }
}
