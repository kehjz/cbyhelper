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
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.cbyhelper.ui.theme.CBYHelperTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

typealias SackCode = String

fun getSackColor(code: SackCode): Color = when (code.firstOrNull()) {
    'A' -> Color(0xFF009E73)
    'B' -> Color(0xFF56B4E9)
    'C' -> Color(0xFFE69F00)
    'D' -> Color(0xFFF0E442)
    else -> Color(0xFF999999)
}

private val homeColors = listOf(
    Color(0xFF009E73), Color(0xFF56B4E9), Color(0xFFE69F00),
    Color(0xFFCC79A7), Color(0xFFF0E442), Color(0xFFD55E00)
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
        setContent { CBYHelperTheme { AppRoot() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val selected = remember { mutableStateOf("Home") }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("CBY Helper", fontSize = 16.sp, modifier = Modifier.padding(12.dp))
                Divider()
                DrawerItem("Home", scope, drawerState) { selected.value = it }
                DrawerItem("Shipment Scanning", scope, drawerState) { selected.value = it }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(selected.value, fontSize = 16.sp) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = null)
                        }
                    }
                )
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (selected.value) {
                    "Home" -> HomeScreen { selected.value = it }
                    "Shipment Scanning" -> ScannerApp()
                }
            }
        }
    }
}

@Composable
fun DrawerItem(
    text: String,
    scope: kotlinx.coroutines.CoroutineScope,
    drawerState: DrawerState,
    onClick: (String) -> Unit
) {
    Text(
        text = text,
        fontSize = 14.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(text); scope.launch { drawerState.close() } }
            .padding(12.dp)
    )
}

@Composable
fun HomeScreen(onSelect: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        repeat(3) { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                repeat(2) { col ->
                    val idx = row * 2 + col
                    val label = if (idx == 0) "Shipment Scanning" else ""
                    HomeTile(label, homeColors[idx]) { if (label.isNotEmpty()) onSelect(label) }
                }
            }
        }
    }
}

@Composable
fun HomeTile(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color)
            .clickable(enabled = label.isNotEmpty()) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (label.isNotEmpty()) {
            Text(label, color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun ScannerApp() {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    val scanned = remember { mutableStateOf("") }
    val hub = remember { mutableStateOf("") }
    val sack = remember { mutableStateOf("") }
    val osa = remember { mutableStateOf("") }
    val mapState = remember { mutableStateOf<Map<Int, Triple<String, String, String>>>(emptyMap()) }
    val loading = remember { mutableStateOf(false) }
    val invalid = remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().padding(8.dp)) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top: Hub & refresh
            Column {
                if (hub.value.isNotEmpty()) {
                    Text(hub.value, fontSize = 16.sp, fontWeight = FontWeight.Normal)
                }
                Button(
                    onClick = {
                        loading.value = true
                        coroutineScope.launch(Dispatchers.IO) {
                            val m = fetchHubMap()
                            withContext(Dispatchers.Main) {
                                mapState.value = m
                                loading.value = false
                                Toast.makeText(context, "Data refreshed", Toast.LENGTH_SHORT).show()
                                focusRequester.requestFocus()
                            }
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Refresh", fontSize = 16.sp)
                }
            }

            // Middle: Sack & OSA side by side
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "Sack Segregation" to sack.value,
                    "OSA Lane" to osa.value
                ).forEach { (label, value) ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(getSackColor(value)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(label, fontSize = 10.sp)
                            val resId = if (label.startsWith("Sack")) R.drawable.conveyor else R.drawable.pallet
                            Image(
                                painter = painterResource(resId),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(value, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Bottom: Scan field
            OutlinedTextField(
                value = scanned.value,
                onValueChange = {
                    scanned.value = it
                    if (invalid.value) invalid.value = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                singleLine = true,
                textStyle = TextStyle(fontSize = 16.sp),
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    if (!loading.value) {
                        try {
                            val obj = JSONObject(scanned.value)
                            val id = obj.getInt("destination_hub_id")
                            mapState.value[id]?.let { (n, s, l) ->
                                hub.value = n
                                sack.value = s
                                osa.value = l
                                invalid.value = false
                            } ?: run { invalid.value = true }
                        } catch (_: Exception) {
                            invalid.value = true
                        }
                        scanned.value = ""
                        focusManager.clearFocus()
                        coroutineScope.launch {
                            delay(200)
                            focusRequester.requestFocus()
                        }
                    }
                }),
                label = { Text("Scan AWB", fontSize = 16.sp) },
                enabled = !loading.value
            )
        }

        // Overlay error without hiding input
        if (invalid.value) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.85f))
                    .clickable { invalid.value = false },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("❌", fontSize = 40.sp)
                    Text(
                        "Invalid Barcode",
                        fontSize = 14.sp,
                        color = Color.Red,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

fun fetchHubMap(): Map<Int, Triple<String, String, String>> = try {
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
    Log.e("fetchHubMap", e.message ?: "")
    emptyMap()
}
