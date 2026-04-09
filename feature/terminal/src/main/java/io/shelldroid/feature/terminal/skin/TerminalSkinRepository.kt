package io.shelldroid.feature.terminal.skin

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the currently-active [TerminalSkin] and the list of available
 * skins. Font size and selected skin id are persisted in SharedPreferences
 * so they survive process death.
 */
@Singleton
class TerminalSkinRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("terminal_skin", Context.MODE_PRIVATE)

    private val _selected: MutableStateFlow<TerminalSkin>

    init {
        val savedSkinId = prefs.getString(KEY_SKIN_ID, BuiltInSkins.DEFAULT.id)
        val savedFontSize = prefs.getFloat(KEY_FONT_SIZE, -1f)
        val baseSkin = BuiltInSkins.ALL.firstOrNull { it.id == savedSkinId } ?: BuiltInSkins.DEFAULT
        val skin = if (savedFontSize > 0f) baseSkin.copy(textSizeSp = savedFontSize) else baseSkin
        _selected = MutableStateFlow(skin)
    }

    val selected: Flow<TerminalSkin> = _selected.asStateFlow()

    val available: List<TerminalSkin> get() = BuiltInSkins.ALL

    fun select(skinId: String) {
        val match = available.firstOrNull { it.id == skinId } ?: return
        _selected.value = match.copy(textSizeSp = _selected.value.textSizeSp)
        prefs.edit().putString(KEY_SKIN_ID, skinId).apply()
    }

    /** Change font size without changing the skin. Persisted immediately. */
    fun setFontSize(sp: Float) {
        _selected.value = _selected.value.copy(textSizeSp = sp)
        prefs.edit().putFloat(KEY_FONT_SIZE, sp).apply()
    }

    fun current(): TerminalSkin = _selected.value

    companion object {
        private const val KEY_SKIN_ID = "skin_id"
        private const val KEY_FONT_SIZE = "font_size_sp"
    }
}
