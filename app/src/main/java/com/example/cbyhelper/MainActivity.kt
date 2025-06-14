// MainActivity.kt
package com.example.cbyhelper

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL


typealias SackCode = String

fun getSackColor(code: SackCode): Color = when (code.firstOrNull()) {
    'A' -> Color(0xFFE69F00)
    'B' -> Color(0xFF56B4E9)
    'C' -> Color(0xFF009E73)
    'D' -> Color(0xFFF0E442)
    else -> Color(0xFF999999)
}

private val homeColors = listOf(
    Color(0xFFE69F00), Color(0xFF56B4E9), Color(0xFF009E73),
    Color(0xFFF0E442), Color(0xFFCC79A7), Color(0xFFD55E00)
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
    val context = LocalContext.current
    val screenWidth = context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density
    val drawerWidth = screenWidth * 0.6f
    var showExitDialog by remember { mutableStateOf(false) }

    BackHandler {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else if (selected.value == "Home") {
            showExitDialog = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(drawerWidth.dp)) {
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
                    "Shipment Scanning" -> ScannerApp(onBackToHome = { selected.value = "Home" })
                }
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit CBY Helper?") },
            text = { Text("Are you sure you want to close the app?") },
            confirmButton = {
                TextButton(onClick = { showExitDialog = false; (context as? ComponentActivity)?.finish() }) {
                    Text("Exit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DrawerItem(text: String, scope: CoroutineScope, drawerState: DrawerState, onClick: (String) -> Unit) {
    Text(
        text = text,
        fontSize = 12.sp,
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
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
fun ScannerApp(onBackToHome: () -> Unit) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }

    val scanned = remember { mutableStateOf("") } // holds the real parsed input
    val textFieldValue = remember { mutableStateOf("") } // shown in the UI
    val hub = remember { mutableStateOf("") }
    val sack = remember { mutableStateOf("") }
    val osa = remember { mutableStateOf("") }
    val mapState = remember { mutableStateOf<Map<Int, Triple<String, String, String>>>(emptyMap()) }
    val loading = remember { mutableStateOf(false) }
    val invalid = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(200)
        focusRequester.requestFocus()
        keyboardController?.hide()
    }

    BackHandler {
        if (invalid.value) {
            invalid.value = false
        } else {
            onBackToHome()
        }
    }

    Box(Modifier.fillMaxSize().padding(8.dp)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                if (hub.value.isNotEmpty()) {
                    Text(hub.value, fontSize = 14.sp, fontWeight = FontWeight.Normal)
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
                                keyboardController?.hide()
                            }
                        }
                    },
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .height(36.dp)
                ) {
                    if (loading.value) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = Color.White)
                    } else {
                        Text("Refresh Data", fontSize = 12.sp)
                    }
                }
            }

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
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(value, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                            Image(
                                painter = painterResource(if (label.startsWith("Sack")) R.drawable.conveyor else R.drawable.pallet),
                                contentDescription = null,
                                modifier = Modifier.size(96.dp)
                            )
                            Text(label, fontSize = 14.sp)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = textFieldValue.value,
                onValueChange = {
                    textFieldValue.value = it
                    if (invalid.value) invalid.value = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                singleLine = true,
                textStyle = TextStyle(fontSize = 10.sp),
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    scanned.value = textFieldValue.value
                    textFieldValue.value = "" // clear visible input

                    hub.value = ""
                    sack.value = ""
                    osa.value = ""

                    try {
                        val obj = JSONObject(scanned.value)
                        val id = obj.getInt("destination_hub_id")
                        val result = mapState.value[id]
                        if (result != null) {
                            val (n, s, l) = result
                            hub.value = n
                            sack.value = s
                            osa.value = l
                            invalid.value = false
                        } else {
                            invalid.value = true
                        }
                    } catch (_: Exception) {
                        invalid.value = true
                    }

                    focusManager.clearFocus()
                    keyboardController?.hide()

                    coroutineScope.launch {
                        delay(200)
                        focusRequester.requestFocus()
                        keyboardController?.hide()
                    }
                }),
                label = { Text("Scan AWB", fontSize = 10.sp) },
                enabled = !loading.value,
                interactionSource = interactionSource
            )
        }

        if (invalid.value) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.85f))
                    .clickable {
                        invalid.value = false
                        hub.value = ""
                        sack.value = ""
                        osa.value = ""
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("❌", fontSize = 40.sp)
                    Text("Invalid Barcode", fontSize = 14.sp, color = Color.Red, textAlign = TextAlign.Center)
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
