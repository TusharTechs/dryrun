package com.dryrun.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dryrun.app.billing.Offer
import com.dryrun.app.billing.Plus

/**
 * Shown only after the before/after card has been seen -- the value is felt
 * before anyone is asked to pay for it. If billing is unavailable this screen
 * is never reached at all; the app is simply free.
 */
@Composable
fun PaywallScreen(
    offers: List<Offer>,
    isWorking: Boolean,
    error: String?,
    onBuy: (Offer) -> Unit,
    onRestore: () -> Unit,
    onClose: () -> Unit
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeContentPadding()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                TextButton(onClick = onClose) { Text("Not now") }
                Spacer(Modifier.height(20.dp))

                Text("Keep going", style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(12.dp))
                Text(
                    "You've had two runs. The next conversation is always a different one.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(28.dp))
                listOf(
                    "Unlimited runs, any conversation",
                    "Every run kept, so you can see the change",
                    "Turn the difficulty up when they go easy on you",
                    "Your hedging over time"
                ).forEach { line ->
                    Row(Modifier.padding(bottom = 12.dp)) {
                        Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Text(line, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    "${Plus.FREE_TRIAL_DAYS} days free. Cancel any time, in the store, in two taps.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                error?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }

            Surface(tonalElevation = 2.dp) {
                Column(Modifier.padding(24.dp)) {
                    if (isWorking) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(16.dp))
                    }
                    offers.forEach { offer ->
                        Button(
                            onClick = { onBuy(offer) },
                            enabled = !isWorking,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(54.dp).padding(bottom = 8.dp)
                        ) {
                            Text("${offer.title} · ${offer.price}")
                        }
                    }
                    TextButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                        Text("Restore a purchase")
                    }
                }
            }
        }
    }
}
