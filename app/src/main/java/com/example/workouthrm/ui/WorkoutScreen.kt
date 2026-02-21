package com.example.workouthrm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.workouthrm.ble.ConnectionState
import com.example.workouthrm.viewmodel.WorkoutViewModel

private val DarkBg = Color(0xFF121212)
private val CardBg = Color(0xFF1E1E1E)
private val AccentRed = Color(0xFFEF5350)
private val AccentGreen = Color(0xFF66BB6A)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFB0B0B0)

@Composable
fun WorkoutScreen(viewModel: WorkoutViewModel, onNavigateToHistory: () -> Unit) {
    val bpm by viewModel.heartRate.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val isWorkoutActive by viewModel.isWorkoutActive.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsState()
    val scannedDevices by viewModel.scannedDevices.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hamburger menu row
            HamburgerMenu(onNavigateToHistory = onNavigateToHistory)

            // Connection status
            ConnectionStatusBadge(connectionState)

            Spacer(modifier = Modifier.height(48.dp))

            // BPM display
            BpmDisplay(bpm, connectionState)

            Spacer(modifier = Modifier.height(32.dp))

            // Timer
            TimerDisplay(elapsedSeconds)

            Spacer(modifier = Modifier.height(48.dp))

            // Workout controls
            if (!isWorkoutActive) {
                Button(
                    onClick = { viewModel.startWorkout() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                ) {
                    Text("Start Workout", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            if (isPaused) viewModel.resumeWorkout() else viewModel.pauseWorkout()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPaused) AccentGreen else Color(0xFFFFA726)
                        )
                    ) {
                        Text(
                            if (isPaused) "Resume" else "Pause",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Button(
                        onClick = { viewModel.stopWorkout() },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                    ) {
                        Text("Stop", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // HRM connection controls at the bottom
            if (connectionState == ConnectionState.CONNECTED) {
                OutlinedButton(
                    onClick = { viewModel.disconnectHrm() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) {
                    Text("Disconnect HRM")
                }
            } else if (connectionState == ConnectionState.CONNECTING) {
                Text(
                    text = "Connecting...",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            } else {
                DeviceScanSection(
                    connectionState = connectionState,
                    devices = scannedDevices,
                    onScanClick = { viewModel.startScan() },
                    onStopScanClick = { viewModel.stopScan() },
                    onDeviceClick = { viewModel.connectToDevice(it.device) }
                )
            }
        }
    }
}

@Composable
private fun HamburgerMenu(onNavigateToHistory: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menu",
                tint = TextPrimary
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Workout History") },
                onClick = {
                    expanded = false
                    onNavigateToHistory()
                }
            )
        }
    }
}

@Composable
private fun ConnectionStatusBadge(state: ConnectionState) {
    val (color, label) = when (state) {
        ConnectionState.DISCONNECTED -> TextSecondary to "Disconnected"
        ConnectionState.SCANNING -> Color(0xFFFFA726) to "Scanning..."
        ConnectionState.CONNECTING -> Color(0xFFFFA726) to "Connecting..."
        ConnectionState.CONNECTED -> AccentGreen to "Connected"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun BpmDisplay(bpm: Int?, connectionState: ConnectionState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = bpm?.toString() ?: "--",
            color = if (bpm != null) AccentRed else TextSecondary,
            fontSize = 96.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "BPM",
            color = TextSecondary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Light
        )
    }
}

@Composable
private fun TimerDisplay(elapsedSeconds: Long) {
    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    Text(
        text = "%02d:%02d".format(minutes, seconds),
        color = TextPrimary,
        fontSize = 96.sp,
        fontWeight = FontWeight.Bold
    )
}


@Composable
private fun DeviceScanSection(
    connectionState: ConnectionState,
    devices: List<com.example.workouthrm.ble.ScannedDevice>,
    onScanClick: () -> Unit,
    onStopScanClick: () -> Unit,
    onDeviceClick: (com.example.workouthrm.ble.ScannedDevice) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (connectionState == ConnectionState.SCANNING) {
            Button(
                onClick = onStopScanClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA726))
            ) {
                Text(
                    text = "Stop Scanning",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Button(
                onClick = onScanClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
            ) {
                Text(
                    text = "Scan for HR Monitors",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (devices.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Tap a device to connect",
                color = TextSecondary,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(devices) { device ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDeviceClick(device) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = device.name,
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = device.address,
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
