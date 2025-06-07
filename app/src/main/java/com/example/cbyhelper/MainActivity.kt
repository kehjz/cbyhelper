package com.example.cbyhelper

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import androidx.compose.ui.Alignment
import com.example.cbyhelper.ui.theme.CBYHelperTheme
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.compose.runtime.SideEffect

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CBYHelperTheme {
                ScannerApp()
            }
        }
    }
}

@Composable
fun ScannerApp() {
    val context = LocalContext.current
    val view = LocalView.current

    SideEffect {
        val window = (view.context as Activity).window
        window.statusBarColor = Color.White.toArgb() // Background color
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true // Use dark icons
    }

    var scannedText by remember { mutableStateOf("") }
    var hubName by remember { mutableStateOf("") }
    var sackSorting by remember { mutableStateOf("") }
    var osaLane by remember { mutableStateOf("") }
    var lookupMap by remember { mutableStateOf(mapOf<Int, Triple<String, String, String>>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        loading = true
        lookupMap = fetchHubMap()
        loading = false

        delay(500)
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Shipment Scanner",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.Black
        )

        OutlinedTextField(
            value = scannedText,
            onValueChange = {
                scannedText = it
                try {
                    val json = JSONObject(scannedText)
                    val hubId = json.get("destination_hub_id").toString().toInt()
                    val result = lookupMap[hubId]
                    if (result != null) {
                        hubName = result.first
                        sackSorting = result.second
                        osaLane = result.third
                        error = ""

                        CoroutineScope(Dispatchers.Main).launch {
                            delay(300)
                            scannedText = ""
                            focusRequester.requestFocus()
                        }
                    } else {
                        hubName = ""
                        sackSorting = ""
                        osaLane = ""
                        error = "❌ Hub ID $hubId not found"
                        scannedText = ""
                        focusRequester.requestFocus()
                    }
                } catch (e: Exception) {
                    hubName = ""
                    sackSorting = ""
                    osaLane = ""
                    error = "❌ Invalid JSON format"
                    scannedText = ""
                    focusRequester.requestFocus()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text),
            singleLine = true,
            label = { Text("Scan shipment AWB label") }
        )

        if (loading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    loading = true
                    CoroutineScope(Dispatchers.IO).launch {
                        val updatedMap = fetchHubMap()
                        withContext(Dispatchers.Main) {
                            lookupMap = updatedMap
                            loading = false
                            focusRequester.requestFocus()
                        }
                    }
                }
            ) {
                Text("\uD83D\uDD04 Refresh Data")
            }
        }

        if (hubName.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Shipment Dest Hub:", fontSize = 18.sp)
            Text(hubName, fontSize = 20.sp)
            Divider(thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

            Image(
                painter = painterResource(id = R.drawable.conveyor),
                contentDescription = "Conveyor Icon",
                modifier = Modifier.size(96.dp),
                contentScale = ContentScale.Fit
            )
            Text("Sack Segregation:", fontSize = 20.sp)
            Text(sackSorting, fontSize = 40.sp)
            Divider(thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

            Image(
                painter = painterResource(id = R.drawable.pallet),
                contentDescription = "Pallet Icon",
                modifier = Modifier.size(96.dp),
                contentScale = ContentScale.Fit
            )
            Text("OSA Lane:", fontSize = 20.sp)
            Text(osaLane, fontSize = 40.sp)
        }

        if (error.isNotEmpty()) {
            Text("⚠️ $error", color = MaterialTheme.colorScheme.error)
        }
    }
}

fun fetchHubMap(): Map<Int, Triple<String, String, String>> {
    val map = mutableMapOf<Int, Triple<String, String, String>>()
    return try {
        val json = URL("https://script.google.com/macros/s/AKfycbza1E7FT2x62m-THXFzRNddvQHIwlFzp3UTcC1OaQ2vhzAi0EjJYqMnHjDT8B__Uhum/exec?pull_all=1")
            .readText()

        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val hubId = obj.getInt("Shipment Destination Hub ID")
            val hubName = obj.getString("Shipment Destination Hub Name")
            val sack = obj.getString("Sack Segregation")
            val lane = obj.get("OSA lane").toString()
            map[hubId] = Triple(hubName, sack, lane)
        }

        Log.d("DEBUG", "✅ Map Loaded: $map")
        map
    } catch (e: Exception) {
        Log.e("fetchHubMap", "❌ Error: ${e.message}")
        emptyMap()
    }
}
