package io.shelldroid.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.shelldroid.core.ui.R as UiR

/**
 * Full-screen overlay that blocks app access until a valid PIN is entered
 * or biometric authentication succeeds.
 *
 * @param onPinSubmit Called with the entered PIN. Returns true if verification
 *   succeeded, false otherwise.
 * @param onBiometricClick Called when the user taps "Use fingerprint".
 * @param biometricAvailable Whether biometric auth is available on this device.
 */
@Composable
fun LockScreen(
    onPinSubmit: (String) -> Unit,
    onBiometricClick: () -> Unit,
    biometricAvailable: Boolean,
    errorMessage: String? = null,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(UiR.string.enter_pin),
                style = MaterialTheme.typography.headlineSmall,
            )

            Spacer(modifier = Modifier.height(24.dp))

            var pin by remember { mutableStateOf("") }

            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
                label = { Text("PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onPinSubmit(pin) },
                enabled = pin.length >= 4,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(UiR.string.unlock))
            }

            if (biometricAvailable) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onBiometricClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(UiR.string.use_biometric))
                }
            }
        }
    }
}
