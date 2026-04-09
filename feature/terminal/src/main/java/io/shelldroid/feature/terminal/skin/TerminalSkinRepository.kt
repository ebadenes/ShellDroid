package io.shelldroid.feature.terminal.skin

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the currently-active [TerminalSkin] and the list of available
 * skins. Right now this is purely in-memory — when the user picks a
 * skin or changes font size it stays until process death.
 *
 * Intended migration path:
 *  - persist the chosen skin `id` + font size override in a DataStore,
 *  - load / save as a flow,
 *  - expose `save(TerminalSkin)` for user-defined skins.
 */
@Singleton
class TerminalSkinRepository @Inject constructor() {

    private val _selected = MutableStateFlow(BuiltInSkins.DEFAULT)
    val selected: Flow<TerminalSkin> = _selected.asStateFlow()

    val available: List<TerminalSkin> get() = BuiltInSkins.ALL

    fun select(skinId: String) {
        val match = available.firstOrNull { it.id == skinId } ?: return
        // Preserve the current font size override if the user changed it
        // independently of the skin (via the Settings slider or volume keys).
        _selected.value = match.copy(textSizeSp = _selected.value.textSizeSp)
    }

    /** Change font size without changing the skin. */
    fun setFontSize(sp: Float) {
        _selected.value = _selected.value.copy(textSizeSp = sp)
    }

    fun current(): TerminalSkin = _selected.value
}
