package com.example.itantra

import android.Manifest
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.itantra.ui.theme.ITantraTheme

class MainActivity : ComponentActivity() {
    private lateinit var sherpaEngine: SherpaOnnxEngine
    private lateinit var networkManager: NetworkManager

    private val transcript = mutableStateListOf<String>()
    private val modeState = mutableStateOf("SENDER")
    private val targetIpState = mutableStateOf("")
    private val isEmergencyState = mutableStateOf(false)

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted -> }
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)

        networkManager = NetworkManager { receivedText ->
            runOnUiThread {
                transcript.add(0, "Received: $receivedText")
                if (modeState.value == "RECEIVER") {
                    sherpaEngine.synthesizeAndPlay(receivedText)
                }
            }
        }

        sherpaEngine = SherpaOnnxEngine(this) { sttText ->
            runOnUiThread {
                val finalMessage = if (isEmergencyState.value) "[ALERT]$sttText" else sttText
                transcript.add(0, "Sent: $finalMessage")
                if (modeState.value == "SENDER" && targetIpState.value.isNotEmpty()) {
                    networkManager.sendText(targetIpState.value, finalMessage)
                }
            }
        }

        setContent {
            ITantraTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    WalkieTalkieScreen()
                }
            }
        }
    }

    @ExperimentalComposeUiApi
    @Composable
    fun WalkieTalkieScreen() {
        var mode by modeState
        var targetIp by targetIpState
        var isEmergency by isEmergencyState

        LaunchedEffect(mode) {
            if (mode == "RECEIVER") {
                networkManager.startServer()
            } else {
                networkManager.stopServer()
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(
                    onClick = { mode = "SENDER" },
                    colors = ButtonDefaults.buttonColors(containerColor = if (mode == "SENDER") MaterialTheme.colorScheme.primary else Color.Gray)
                ) {
                    Text("SENDER")
                }
                Button(
                    onClick = { mode = "RECEIVER" },
                    colors = ButtonDefaults.buttonColors(containerColor = if (mode == "RECEIVER") MaterialTheme.colorScheme.primary else Color.Gray)
                ) {
                    Text("RECEIVER")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (mode == "SENDER") {
                TextField(
                    value = targetIp,
                    onValueChange = { targetIp = it },
                    label = { Text("Receiver IP Address") },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text("Waiting for incoming messages...", fontSize = 14.sp, color = Color.Gray)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("EMERGENCY ALERT")
                Spacer(modifier = Modifier.width(8.dp))
                Switch(checked = isEmergency, onCheckedChange = { isEmergency = it })
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {},
                modifier = Modifier
                    .size(180.dp)
                    .pointerInteropFilter {
                        when (it.action) {
                            MotionEvent.ACTION_DOWN -> {
                                sherpaEngine.startListening()
                                true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                sherpaEngine.stopListening()
                                true
                            }
                            else -> false
                        }
                    },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("PTT", fontSize = 24.sp, color = Color.White)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text("Transcript", style = MaterialTheme.typography.titleLarge)
            HorizontalDivider()
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(transcript) { line ->
                    Text(line, modifier = Modifier.padding(vertical = 4.dp), fontSize = 16.sp)
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        networkManager.stopServer()
        sherpaEngine.stopListening()
    }
}
