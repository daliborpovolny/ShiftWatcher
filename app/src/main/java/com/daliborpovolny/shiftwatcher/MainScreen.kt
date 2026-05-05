package com.daliborpovolny.shiftwatcher

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daliborpovolny.shiftwatcher.ui.theme.ShiftWatcherTheme

fun formatTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", mins, secs)
}

@Composable
fun MainScreen(
    // These should ideally come from your ViewModel
    shiftState: ShiftState = ShiftState.INACTIVE,
    remainingTime: String = "59:59"
) {
    val context = LocalContext.current

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
                    modifier = Modifier.size(200.dp)
                ) {
                    Text("START SHIFT")
                }
            }

            ShiftState.ACTIVE -> {
                Text("Next Check-in in:", style = MaterialTheme.typography.labelLarge)
                Text(remainingTime, style = MaterialTheme.typography.displayLarge)

                Spacer(modifier = Modifier.height(32.dp))

                OutlinedButton(
                    onClick = {
                        val intent = Intent(context, WatcherService::class.java).apply {
                            action = "END_SHIFT"
                        }
                        context.startService(intent)
                    }
                ) {
                    Text("END SHIFT")
                }
            }

            ShiftState.ALARMING -> {
                Button(
                    onClick = {
                        val intent = Intent(context, WatcherService::class.java).apply {
                            action = "STOP_ALARM"
                        }
                        context.startService(intent)
                    },
                    modifier = Modifier.fillMaxSize(),
                    colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color.Red),
                    shape = androidx.compose.ui.graphics.RectangleShape
                ) {
                    Text("I AM OKAY", style = MaterialTheme.typography.displayLarge)
                }
            }

            ShiftState.ESCALATING -> {
                Text("Escalating", style = MaterialTheme.typography.displayMedium)
                Text(
                    "Calling contacts from the escalation list",
                    style = MaterialTheme.typography.bodyMedium
                )

            }

            ShiftState.STOPPED_ESCALATION -> {
                Text("Escalation stopped")
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
fun MainScreenPreview() {
    ShiftWatcherTheme() {
        MainScreen()
    }
}