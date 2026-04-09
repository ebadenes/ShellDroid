package io.shelldroid.feature.terminal

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Deep-link channel for "open the terminal for host X" requests.
 *
 * [io.shelldroid.MainActivity] inspects `Intent.EXTRA_HOST_ID` in
 * `onCreate` and `onNewIntent` and forwards the host id here.
 * `ShellDroidNavHost` collects this flow in a `LaunchedEffect` and
 * navigates to `terminal/{hostId}` on every emission.
 *
 * Implemented as a shared flow with replay=1 so that a request fired
 * before the nav host has had a chance to collect still lands once
 * composition catches up.
 */
object TerminalLaunchRequest {
    const val EXTRA_HOST_ID: String = "io.shelldroid.extra.HOST_ID"

    private val _requests = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 4)
    val requests: SharedFlow<String> = _requests.asSharedFlow()

    fun request(hostId: String) {
        _requests.tryEmit(hostId)
    }
}
