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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.cbyhelper.ui.theme.CBYHelperTheme
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL


// ─────────────── Color Helpers ───────────────
typealias SackCode = String

fun getSackColor(code: SackCode): Color = when (code.firstOrNull()) {
    'A' -> Color(0xFF009E73) // Green
    'B' -> Color(0xFF56B4E9) // Blue
    'C' -> Color(0xFFE69F00) // Orange
    'D' -> Color(0xFFF0E442) // Yellow
    else -> Color(0xFF999999) // Grey fallback
}

private val homeColors = listOf(
    Color(0xFF009E73), // Green
    Color(0xFF56B4E9), // Blue
    Color(0xFFE69F00), // Orange
    Color(0xFFCC79A7), // Pink
    Color(0xFFF0E442), // Yellow
    Color(0xFFD55E00)  // Red
)

private const val SHEET_ENDPOINT =
    "https://script.google.com/macros/s/AKfycbza1E7FT2x62m-THXFzRNddvQHIwlFzp3UTcC1OaQ2vhzAi0EjJYqMnHjDT8B__Uhum/exec"

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.WHITE
        WindowCompat.getInsetsController(window, window.decorView)
            ?.isAppearanceLightStatusBars = true
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
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedScreen by remember { mutableStateOf("Home") }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("CBY Helper", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
                Divider()
                DrawerItem("Home", scope, drawerState) { selectedScreen = it }
                DrawerItem("Shipment Scanning", scope, drawerState) { selectedScreen = it }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(selectedScreen, style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    }
                )
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (selectedScreen) {
                    "Home" -> HomeScreen { selectedScreen = it }
                    "Shipment Scanning" -> ScannerApp()
                }
            }
        }
    }
}

@Composable
fun DrawerItem(
    text: String,
    scope: CoroutineScope,
    drawerState: DrawerState,
    onClick: (String) -> Unit
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
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
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(2) { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                repeat(2) { col ->
                    val index = row * 2 + col
                    val label = if (index == 0) "Shipment Scanning" else ""
                    HomeTile(label, homeColors[index]) { if (label.isNotEmpty()) onSelect(label) }
                }
            }
        }
    }
}

@Composable
fun RowScope.HomeTile(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(color)
            .clickable(enabled = label.isNotEmpty(), onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ScannerApp() {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    var scannedText by remember { mutableStateOf("") }
    var hubName by remember { mutableStateOf("") }
    var sackSorting by remember { mutableStateOf("") }
    var osaLane by remember { mutableStateOf("") }
    var lookupMap by remember { mutableStateOf(mapOf<Int, Triple<String, String, String>>()) }
    var loading by remember { mutableStateOf(false) }
    var isInvalid by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        val totalHeight = maxHeight

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.height(totalHeight * 0.2f)
        ) {
            OutlinedTextField(
                value = scannedText,
                onValueChange = { scannedText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (!loading) {
                            try {
                                val json = JSONObject(scannedText)
                                val hubId = json.getInt("destination_hub_id")
                                lookupMap[hubId]?.let { (name, sack, lane) ->
                                    hubName = name
                                    sackSorting = sack
                                    osaLane = lane
                                    isInvalid = false
                                } ?: run {
                                    hubName = ""
                                    sackSorting = ""
                                    osaLane = ""
                                    isInvalid = true
                                }
                            } catch (_: Exception) {
                                hubName = ""
                                sackSorting = ""
                                osaLane = ""
                                isInvalid = true
                            }
                            scannedText = ""
                            focusManager.clearFocus()
                            CoroutineScope(Dispatchers.Main).launch {
                                delay(300)
                                focusRequester.requestFocus()
                            }
                        }
                    }
                ),
                label = { Text(if (loading) "Loading... please wait" else "Scan shipment AWB label") },
                enabled = !loading
            )

            Button(
                onClick = {
                    loading = true
                    CoroutineScope(Dispatchers.IO).launch {
                        val updated = fetchHubMap()
                        withContext(Dispatchers.Main) {
                            lookupMap = updated
                            loading = false
                            Toast.makeText(context, "Data refreshed", Toast.LENGTH_SHORT).show()
                            focusRequester.requestFocus()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                if (loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                else Text("Refresh Data", color = Color.White)
            }
        }

        if (isInvalid) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("❌", fontSize = 80.sp)
                    Text("Invalid\nBarcode", fontSize = 40.sp, color = Color.Red, textAlign = TextAlign.Center)
                }
            }
        } else if (hubName.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = totalHeight * 0.2f)
            ) {
                // Shipment Dest Hub Block
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(totalHeight * 0.2f)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF999999)),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Shipment Dest Hub", fontSize = 16.sp)
                        Text(hubName, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
                // Sack Segregation Block
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(totalHeight * 0.32f)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(getSackColor(sackSorting)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        Modifier.fillMaxSize().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sack Segregation", fontSize = 15.sp)
                            Image(painter = painterResource(id = R.drawable.conveyor), contentDescription = null, modifier = Modifier.size(80.dp))
                        }
                        Text(sackSorting, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                    }
                }
                // OSA Lane Block
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(totalHeight * 0.32f)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(getSackColor(osaLane)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        Modifier.fillMaxSize().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("OSA Lane", fontSize = 15.sp)
                            Image(painter = painterResource(id = R.drawable.pallet), contentDescription = null, modifier = Modifier.size(80.dp))
                        }
                        Text(osaLane, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

fun fetchHubMap(): Map<Int, Triple<String, String, String>> =
    try {
        val json = URL(SHEET_ENDPOINT).readText()
        val arr = JSONArray(json)
        buildMap {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                put(
                    o.getInt("Shipment Destination Hub ID"),
                    Triple(
                        o.getString("Shipment Destination Hub Name"),
                        o.getString("Sack Segregation"),
                        o.getString("OSA lane")
                    )
                )
            }
        }
    } catch (e: Exception) {
        Log.e("fetchHubMap", "Error: ${e.message}")
        emptyMap()
    }
