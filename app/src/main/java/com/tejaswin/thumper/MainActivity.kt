package com.tejaswin.thumper

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tejaswin.thumper.ui.HistoryScreen
import com.tejaswin.thumper.ui.WorkoutScreen
import com.tejaswin.thumper.viewmodel.WorkoutViewModel

class MainActivity : ComponentActivity() {

    private val permissionsGranted = mutableStateOf(false)

    private val requiredPermissions = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.RECORD_AUDIO
    )

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            permissionsGranted.value = results.values.all { it }
        }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionsGranted.value = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                if (permissionsGranted.value) {
                    val viewModel: WorkoutViewModel = viewModel()
                    val isWorkoutActive by viewModel.isWorkoutActive.collectAsState()
                    var showHistory by rememberSaveable { mutableStateOf(false) }

                    KeepScreenOn(isWorkoutActive)

                    if (showHistory) {
                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    title = { Text("Workout History") },
                                    navigationIcon = {
                                        IconButton(onClick = { showHistory = false }) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowBack,
                                                contentDescription = "Back"
                                            )
                                        }
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = Color(0xFF121212),
                                        titleContentColor = Color.White,
                                        navigationIconContentColor = Color.White
                                    )
                                )
                            }
                        ) { padding ->
                            Box(modifier = Modifier.padding(padding)) {
                                HistoryScreen(viewModel = viewModel)
                            }
                        }
                    } else {
                        WorkoutScreen(
                            viewModel = viewModel,
                            onNavigateToHistory = { showHistory = true }
                        )
                    }
                } else {
                    PermissionScreen { permissionLauncher.launch(requiredPermissions) }
                }
            }
        }
    }

    @Composable
    private fun KeepScreenOn(enabled: Boolean) {
        DisposableEffect(enabled) {
            if (enabled) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            onDispose {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
}

@Composable
private fun PermissionScreen(onRequestPermissions: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF121212)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Bluetooth permissions are required to connect to your heart rate monitor. Microphone permission is required to hear the jump rope making contact with the ground.",
                color = Color.White,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRequestPermissions) {
                Text("Grant Permissions")
            }
        }
    }
}
