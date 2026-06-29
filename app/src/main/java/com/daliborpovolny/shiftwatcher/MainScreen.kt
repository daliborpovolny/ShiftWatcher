package com.daliborpovolny.shiftwatcher

import android.content.*
import android.os.BatteryManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daliborpovolny.shiftwatcher.ui.theme.ShiftWatcherTheme

fun formatTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", mins, secs)
}

@Composable
fun rememberBatteryState(): Pair<Int, Boolean> {
    val context = LocalContext.current
    var batteryState by remember { mutableStateOf(Pair(-1, false)) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                val pct =
                    if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else -1
                val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val isCharging =
                    status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                batteryState = Pair(pct, isCharging)
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(receiver, filter)
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
    return batteryState
}

@Composable
fun MainScreen(
    shiftState: ShiftState = ShiftState.INACTIVE,
    remainingTime: String = "59:59",
    batteryThreshold: Int = 20
) {
    val context = LocalContext.current
    val (batteryPct, isCharging) = rememberBatteryState()
    val canStartShift =
        if (batteryPct == -1) true else !(batteryPct < batteryThreshold && !isCharging)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (shiftState) {
            ShiftState.INACTIVE -> {
                Button(
                    onClick = {
                        val intent = Intent(context, WatcherService::class.java)
                        context.startForegroundService(intent)
                    },
                    enabled = canStartShift,
                    modifier = Modifier.size(200.dp)
                ) {
                    Text("Začít směnu")
                }

                if (!canStartShift) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Směnu nelze začít: Baterie je pod $batteryThreshold% ($batteryPct%) a telefon se nenabíjí.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }

            ShiftState.ACTIVE -> {
                Text("Další kontrola za:", style = MaterialTheme.typography.labelLarge)
                Text(remainingTime, style = MaterialTheme.typography.displayLarge)

                Spacer(modifier = Modifier.height(32.dp))

                OutlinedButton(
                    onClick = {
                        val intent = Intent(context, WatcherService::class.java).apply {
                            action = WatcherService.ACTION_END_SHIFT
                        }
                        context.startService(intent)
                    }
                ) {
                    Text("Ukončit směnu")
                }
            }

            ShiftState.ALARMING -> {
                Button(
                    onClick = {
                        val intent = Intent(context, WatcherService::class.java).apply {
                            action = WatcherService.ACTION_STOP_ALARM
                        }
                        context.startService(intent)
                    },
                    modifier = Modifier.fillMaxSize(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = androidx.compose.ui.graphics.RectangleShape
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize().padding(16.dp)
                    ) {
                        Text(
                            "Klikni na mě!",
                            style = MaterialTheme.typography.displayLarge,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = remainingTime,
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onError,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Pokud budík nevypnete před vypršením časovače, budou kontaktovány vaše nouzové kontakty.",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onError
                        )
                    }
                }
            }

            ShiftState.ESCALATING -> {
                Text(
                    "Probíhá Eskalace",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        val intent = Intent(context, WatcherService::class.java).apply {
                            action = WatcherService.ACTION_STOP_ESCALATION
                        }
                        context.startService(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("STOP", style = MaterialTheme.typography.titleLarge)
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    "Výpis událostí (nahoře nejnovější)",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(WatcherService.escalationLogs) { log ->
                        Text(
                            text = log,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }

            ShiftState.STOPPED_ESCALATION -> {
                Text(
                    "Eskalace ukončena",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    "Výpis událostí",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(WatcherService.escalationLogs) { log ->
                        Text(
                            text = log,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        val intent = Intent(context, WatcherService::class.java).apply {
                            action = WatcherService.ACTION_END_SHIFT
                        }
                        context.startService(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Zavřít")
                }
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
fun MainScreenPreview() {
    ShiftWatcherTheme {
        MainScreen()
    }
}
