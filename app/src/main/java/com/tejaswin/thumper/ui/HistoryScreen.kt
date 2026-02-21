package com.tejaswin.thumper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tejaswin.thumper.data.WorkoutEntity
import com.tejaswin.thumper.viewmodel.WorkoutViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DarkBg = Color(0xFF121212)
private val CardBg = Color(0xFF1E1E1E)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFB0B0B0)
private val AccentRed = Color(0xFFEF5350)
private val AccentBlue = Color(0xFF42A5F5)

@Composable
fun HistoryScreen(viewModel: WorkoutViewModel) {
    val workouts by viewModel.workoutHistory.collectAsState(initial = emptyList())
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBg
    ) {
        if (workouts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "No workouts yet",
                    color = TextSecondary,
                    fontSize = 18.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.exportCsv(context) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue)
                        ) {
                            Text("Export CSV", fontSize = 14.sp)
                        }
                    }
                }
                items(workouts) { workout ->
                    WorkoutCard(workout)
                }
            }
        }
    }
}

@Composable
private fun WorkoutCard(workout: WorkoutEntity) {
    val dateFormat = SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault())
    val date = dateFormat.format(Date(workout.startTimeMillis))

    val minutes = workout.durationSeconds / 60
    val seconds = workout.durationSeconds % 60
    val duration = "%d:%02d".format(minutes, seconds)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = date,
                color = TextSecondary,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = duration,
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Duration",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Column {
                    Text(
                        text = workout.avgHeartRate?.toString() ?: "--",
                        color = if (workout.avgHeartRate != null) AccentRed else TextSecondary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Avg BPM",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Column {
                    Text(
                        text = workout.jumpCount?.toString() ?: "--",
                        color = if (workout.jumpCount != null) AccentBlue else TextSecondary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Jumps",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
