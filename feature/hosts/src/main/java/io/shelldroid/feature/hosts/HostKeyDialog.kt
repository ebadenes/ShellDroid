package io.shelldroid.feature.hosts

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.shelldroid.feature.hosts.tofu.ComposeHostKeyPrompter
import io.shelldroid.feature.hosts.tofu.HostKeyPrompt
import io.shelldroid.feature.hosts.tofu.PromptType

/**
 * Collects [ComposeHostKeyPrompter.prompts] and shows the appropriate dialog.
 *
 * Drop this composable somewhere high in the app's tree (e.g. inside the
 * MainActivity Scaffold) and it will surface every TOFU prompt as the
 * verifier emits them.
 */
@Composable
fun HostKeyDialogHost(prompter: ComposeHostKeyPrompter) {
    var current by remember { mutableStateOf<HostKeyPrompt?>(null) }

    LaunchedEffect(prompter) {
        prompter.prompts.collect { prompt ->
            current = prompt
        }
    }

    val active = current ?: return
    when (active.type) {
        PromptType.FIRST_SEEN -> FirstSeenDialog(
            prompt = active,
            onAccept = {
                active.response.complete(true)
                current = null
            },
            onReject = {
                active.response.complete(false)
                current = null
            },
        )
        PromptType.KEY_CHANGED -> KeyChangedDialog(
            prompt = active,
            onDismiss = {
                active.response.complete(false)
                current = null
            },
        )
    }
}

@Composable
private fun FirstSeenDialog(
    prompt: HostKeyPrompt,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onReject,
        title = { Text("Host key desconocido") },
        text = {
            Column {
                Text("${prompt.hostname}:${prompt.port}")
                Text("Tipo: ${prompt.keyType}")
                Text("Fingerprint SHA256:")
                Text(prompt.fingerprint)
                Text("¿Confiás en esta clave?")
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("ACEPTAR") } },
        dismissButton = { TextButton(onClick = onReject) { Text("RECHAZAR") } },
    )
}

@Composable
private fun KeyChangedDialog(
    prompt: HostKeyPrompt,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¡Host key cambió!") },
        text = {
            Column {
                Text("${prompt.hostname}:${prompt.port}")
                Text("La clave del servidor cambió desde la última conexión.")
                Text("Esto puede indicar un ataque MITM. La conexión fue rechazada.")
                Text("Anterior: ${prompt.oldFingerprint}")
                Text("Nueva (${prompt.keyType}): ${prompt.fingerprint}")
                Text("Eliminá la entrada en Known Hosts si confiás en el cambio.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("ENTENDIDO") } },
    )
}
