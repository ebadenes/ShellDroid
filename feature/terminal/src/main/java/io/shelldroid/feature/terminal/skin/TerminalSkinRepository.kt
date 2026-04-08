package io.shelldroid.feature.terminal.skin

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the currently-active [TerminalSkin] and the list of available
 * skins. Right now this is purely in-memory (`DEFAULT`) — when the user
 * picks a skin it stays until process death.
 *
 * Intended migration path:
 *  - persist the chosen skin `id` in the same DataStore used by
 *    `:core:security` / `:core:db` (qualified `@SkinPrefsDataStore`),
 *  - load / save as a flow,
 *  - expose `save(TerminalSkin)` for user-defined skins that aren't in
 *    [BuiltInSkins].
 *
 * The interface already exposes flows so the call-sites (VM, screen)
 * don't change when persistence lands.
 */
@Singleton
class TerminalSkinRepository @Inject constructor() {

    private val _selected = MutableStateFlow(BuiltInSkins.DEFAULT)
    val selected: Flow<TerminalSkin> = _selected.asStateFlow()

    val available: List<TerminalSkin> get() = BuiltInSkins.ALL

    fun select(skinId: String) {
        val match = available.firstOrNull { it.id == skinId } ?: return
        _selected.value = match
    }

    fun current(): TerminalSkin = _selected.value
}
