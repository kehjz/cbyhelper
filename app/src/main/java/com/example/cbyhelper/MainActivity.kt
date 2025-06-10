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
import androidx.core.view.WindowCompat
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cbyhelper.ui.theme.CBYHelperTheme
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

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
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedScreen by remember { mutableStateOf("Home") }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("CBY Helper", fontSize = 20.sp, modifier = Modifier.padding(16.dp))
                HorizontalDivider()
                DrawerItem("Home", onClick = { selectedScreen = it }, scope, drawerState)
                DrawerItem("Shipment Scanning", onClick = { selectedScreen = it }, scope, drawerState)
                DrawerItem("Coming soon", onClick = { selectedScreen = it }, scope, drawerState)
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = selectedScreen, style = MaterialTheme.typography.titleLarge) },
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
                    "Coming soon" -> ComingSoonScreen()
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
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            HomeTile("Shipment Scanning", Color(0xFF009E73)) { onSelect("Shipment Scanning") }
            HomeTile("Coming soon", Color(0xFFE69F00)) { onSelect("Coming soon") }
        }
    }
}

@Composable
fun HomeTile(label: String, color: Color, onClick: () -> Unit) {
    val lines = label.split(" ")
    Box(
        modifier = Modifier
            .size(140.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(color = color)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            lines.forEach {
                Text(
                    it,
                    color = Color.White,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun ComingSoonScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Coming soon", fontSize = 18.sp)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
            label = {
                Text(if (loading) "Loading... please wait" else "Scan shipment AWB label")
            },
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
            if (loading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text("Refresh Data", color = Color.White)
            }
        }

        if (isInvalid) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("❌", fontSize = 80.sp, modifier = Modifier.padding(bottom = 16.dp))
                    Text(
                        text = "Invalid\nBarcode",
                        fontSize = 40.sp,
                        color = Color.Red,
                        textAlign = TextAlign.Center,
                        lineHeight = 44.sp
                    )
                }
            }
        } else if (hubName.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Text("Shipment Dest Hub", fontSize = 16.sp)
                Text(hubName, fontSize = 14.sp)
                HorizontalDivider()

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
                            contentDescription = null,
                            modifier = Modifier.size(80.dp)
                        )
                    }
                    Text(sackSorting, fontSize = 45.sp, textAlign = TextAlign.End)
                }

                HorizontalDivider()

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
                            contentDescription = null,
                            modifier = Modifier.size(80.dp)
                        )
                    }
                    Text(osaLane, fontSize = 45.sp, textAlign = TextAlign.End)
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
