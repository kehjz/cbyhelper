package com.example.cbyhelper

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cbyhelper.ui.theme.CBYHelperTheme
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import android.speech.tts.TextToSpeech
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CBYHelperTheme {
                AppRoot()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedScreen by remember { mutableStateOf("Home") }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("CBY Helper", fontSize = 20.sp, modifier = Modifier.padding(16.dp))
                Divider()
                DrawerItem("Home", onClick = { selectedScreen = it }, scope, drawerState)
                DrawerItem("Shipment Scanning", onClick = { selectedScreen = it }, scope, drawerState)
                DrawerItem("Departure Timing", onClick = { selectedScreen = it }, scope, drawerState)
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(selectedScreen) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                when (selectedScreen) {
                    "Home" -> HomeScreen(onSelect = { selectedScreen = it })
                    "Shipment Scanning" -> ScannerApp()
                    "Departure Timing" -> DepartureTimingApp()
                }
            }
        }
    }
}

@Composable
fun DrawerItem(
    text: String,
    onClick: (String) -> Unit,
    scope: CoroutineScope,
    drawerState: DrawerState
) {
    Text(
        text = text,
        fontSize = 18.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick(text)
                scope.launch { drawerState.close() }
            }
            .padding(16.dp)
    )
}

@Composable
fun HomeScreen(onSelect: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            HomeTile("Shipment Scanning", Color(0xFF1B9E77)) {
                onSelect("Shipment Scanning")
            }
            HomeTile("Departure Timing", Color(0xFFD95F02)) {
                onSelect("Departure Timing")
            }
        }
    }
}

@Composable
fun HomeTile(label: String, color: Color, onClick: () -> Unit) {
    val lines = label.split(" ")

    Box(
        modifier = Modifier
            .size(120.dp)
            .background(color = color, shape = MaterialTheme.shapes.medium)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            lines.forEach {
                Text(
                    text = it,
                    color = Color.White,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun ScannerApp() {
    val context = LocalContext.current
    var scannedText by remember { mutableStateOf("") }
    var hubName by remember { mutableStateOf("") }
    var sackSorting by remember { mutableStateOf("") }
    var osaLane by remember { mutableStateOf("") }
    var lookupMap by remember { mutableStateOf(mapOf<Int, Triple<String, String, String>>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var refreshedRecently by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = scannedText,
            onValueChange = {
                if (!loading) {
                    scannedText = it
                    refreshedRecently = false
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
                            error = "❌⚠️ Invalid"
                            scannedText = ""
                        }
                    } catch (e: Exception) {
                        hubName = ""
                        sackSorting = ""
                        osaLane = ""
                        error = "❌⚠️ Invalid"
                        scannedText = ""
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text),
            singleLine = true,
            label = {
                Text(if (loading) "Loading... please wait" else "Scan shipment AWB label")
            },
            enabled = !loading
        )

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
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
                                refreshedRecently = true
                                Toast.makeText(context, "✅ Data refreshed", Toast.LENGTH_SHORT).show()
                                focusRequester.requestFocus()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                ) {
                    Text(
                        if (refreshedRecently) "✅ Refreshed" else "Refresh Data",
                        color = Color.White
                    )
                }
            }
        }

        if (hubName.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Column {
                    Text("Shipment Dest Hub", fontSize = 16.sp)
                    Text(hubName, fontSize = 14.sp)
                }

                Divider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Sack Segregation", fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Image(
                            painter = painterResource(id = R.drawable.conveyor),
                            contentDescription = "Sack",
                            modifier = Modifier.size(80.dp)
                        )
                    }
                    Text(sackSorting, fontSize = 45.sp, modifier = Modifier.padding(end = 8.dp))
                }

                Divider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("OSA Lane", fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Image(
                            painter = painterResource(id = R.drawable.pallet),
                            contentDescription = "OSA",
                            modifier = Modifier.size(80.dp)
                        )
                    }
                    Text(osaLane, fontSize = 45.sp, modifier = Modifier.padding(end = 8.dp))
                }
            }
        }

        if (error.isNotEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center
                )
            }
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

@Composable
fun DepartureTimingApp() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Departure Timing Screen (to be built)")
    }
}
