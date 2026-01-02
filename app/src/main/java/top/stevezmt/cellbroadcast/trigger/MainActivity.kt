package top.stevezmt.cellbroadcast.trigger

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.stevezmt.cellbroadcast.trigger.ui.theme.无线警报测试Theme
import java.io.BufferedReader
import java.io.InputStreamReader
import android.util.Base64
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            无线警报测试Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    fun isXposedActive(): Boolean = false
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val activity = LocalContext.current as? MainActivity
    val isXposedActive = activity?.isXposedActive() ?: false
    
    var messageBody by remember { mutableStateOf("") }
    var selectedLevelIndex by remember { mutableIntStateOf(1) }
    var delaySeconds by remember { mutableStateOf("0") }
    var logs by remember { mutableStateOf("Ready...\n") }
    
    // Advanced Options State
    var isAdvancedExpanded by remember { mutableStateOf(false) }
    var serialNumber by remember { mutableStateOf("1234") }
    var customCategory by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("3") }
    var geoScope by remember { mutableStateOf("3") }
    var dcs by remember { mutableStateOf("0") }
    var slotIndex by remember { mutableStateOf("0") }
    var languageCode by remember { mutableStateOf(java.util.Locale.getDefault().language) }
    
    // Google Alerts State
    var isGoogleAlertsExpanded by remember { mutableStateOf(false) }
    var isGmsRealAlert by remember { mutableStateOf(true) }
    var gmsMagnitude by remember { mutableStateOf("5.6") }
    var regionName by remember { mutableStateOf("San Francisco") }
    var latitude by remember { mutableStateOf("37.7749") }
    var longitude by remember { mutableStateOf("-122.4194") }
    var distanceKm by remember { mutableStateOf("10.5") }
    var polygonRadiusKm by remember { mutableStateOf("50.0") }
    var alertType by remember { mutableStateOf("1") }
    
    // Debug Overrides (Hidden/Defaults)
    // k=5 is required for EALERT_DISPLAY
    // n=1 (Real Alert UI?), m=2 (Source)
    
    // Root Error Dialog
    var showRootErrorDialog by remember { mutableStateOf(false) }
    
    // Xposed Error Dialog
    var showXposedErrorDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val logStarting = stringResource(R.string.log_starting)
    val toastCopied = stringResource(R.string.toast_copied)
    
    // First Run Warning Dialog
    var showWarningDialog by remember { mutableStateOf(false) }
    val sharedPreferences = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
    
    // About Dialog State
    var showAboutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val hasShownWarning = sharedPreferences.getBoolean("has_shown_warning", false)
        if (!hasShownWarning) {
            showWarningDialog = true
        }
    }

    if (showWarningDialog) {
        AlertDialog(
            onDismissRequest = { /* Prevent dismissal without confirmation */ },
            title = { Text(stringResource(R.string.warning_title)) },
            text = { Text(stringResource(R.string.warning_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        sharedPreferences.edit().putBoolean("has_shown_warning", true).apply()
                        showWarningDialog = false
                    }
                ) {
                    Text(stringResource(R.string.warning_confirm))
                }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text(stringResource(R.string.about_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Xposed Status: ${if (isXposedActive) "Active" else "Inactive"}", 
                        color = if (isXposedActive) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Red)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.about_repo),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/SteveZMTstudios/cellbroadcast-trigger"))
                            context.startActivity(intent)
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.about_report),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/SteveZMTstudios/cellbroadcast-trigger/issues"))
                            context.startActivity(intent)
                        }
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showAboutDialog = false }) {
                    Text(stringResource(R.string.about_close))
                }
            }
        )
    }
    
    if (showRootErrorDialog) {
        AlertDialog(
            onDismissRequest = { showRootErrorDialog = false },
            title = { Text(stringResource(R.string.root_error_title)) },
            text = { Text(stringResource(R.string.root_error_message)) },
            confirmButton = {
                Button(onClick = { showRootErrorDialog = false }) {
                    Text(stringResource(R.string.root_error_confirm))
                }
            }
        )
    }

    if (showXposedErrorDialog) {
        AlertDialog(
            onDismissRequest = { showXposedErrorDialog = false },
            title = { Text(stringResource(R.string.xposed_error_title)) },
            text = { Text(stringResource(R.string.xposed_error_message)) },
            confirmButton = {
                Button(onClick = { showXposedErrorDialog = false }) {
                    Text(stringResource(R.string.xposed_error_confirm))
                }
            }
        )
    }

    val levels = listOf(
        stringResource(R.string.level_presidential) to 0x00,
        stringResource(R.string.level_extreme) to 0x01,
        stringResource(R.string.level_severe) to 0x02,
        stringResource(R.string.level_amber) to 0x03,
        stringResource(R.string.level_test) to 0x04,
        stringResource(R.string.etws_earthquake) to 0x10,
        stringResource(R.string.etws_tsunami) to 0x11,
        stringResource(R.string.etws_earthquake_tsunami) to 0x12,
        stringResource(R.string.etws_test) to 0x13,
        stringResource(R.string.etws_other) to 0x14
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.app_title), style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = { showAboutDialog = true }) {
                Icon(Icons.Filled.Info, contentDescription = "About")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = messageBody,
            onValueChange = { messageBody = it },
            label = { Text(stringResource(R.string.alert_message_label)) },
            placeholder = { Text(stringResource(R.string.default_message)) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(stringResource(R.string.alert_level_label))
        levels.forEachIndexed { index, (name, _) ->
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RadioButton(
                    selected = selectedLevelIndex == index,
                    onClick = { selectedLevelIndex = index }
                )
                Text(text = name, modifier = Modifier.padding(start = 8.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = delaySeconds,
            onValueChange = { delaySeconds = it.filter { char -> char.isDigit() } },
            label = { Text(stringResource(R.string.delay_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Advanced Options Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.advanced_options), style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { isAdvancedExpanded = !isAdvancedExpanded }) {
                        Icon(
                            imageVector = if (isAdvancedExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = null
                        )
                    }
                }
                
                if (isAdvancedExpanded) {
                    OutlinedTextField(
                        value = serialNumber,
                        onValueChange = { serialNumber = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.serial_number_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(stringResource(R.string.serial_number_desc), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))

                    OutlinedTextField(
                        value = customCategory,
                        onValueChange = { customCategory = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.service_category_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(stringResource(R.string.service_category_desc), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))

                    OutlinedTextField(
                        value = priority,
                        onValueChange = { priority = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.priority_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(stringResource(R.string.priority_desc), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))

                    OutlinedTextField(
                        value = geoScope,
                        onValueChange = { geoScope = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.geo_scope_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(stringResource(R.string.geo_scope_desc), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))

                    OutlinedTextField(
                        value = dcs,
                        onValueChange = { dcs = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.dcs_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(stringResource(R.string.dcs_desc), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))

                    OutlinedTextField(
                        value = slotIndex,
                        onValueChange = { slotIndex = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.slot_index_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(stringResource(R.string.slot_index_desc), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))

                    OutlinedTextField(
                        value = languageCode,
                        onValueChange = { languageCode = it },
                        label = { Text(stringResource(R.string.language_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(stringResource(R.string.language_desc), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                scope.launch {
                    logs += logStarting
                    val delayMs = (delaySeconds.toLongOrNull() ?: 0) * 1000
                    val selectedValue = levels[selectedLevelIndex].second
                    val isEtws = selectedValue >= 0x10
                    val level = if (isEtws) (selectedValue - 0x10) else selectedValue
                    
                    val finalBody = if (messageBody.isEmpty()) context.getString(R.string.default_message) else messageBody
                    
                    val adv = mapOf(
                        "serial" to (serialNumber.toIntOrNull() ?: 1234),
                        "category" to (customCategory.toIntOrNull() ?: -1),
                        "priority" to (priority.toIntOrNull() ?: 3),
                        "scope" to (geoScope.toIntOrNull() ?: 3),
                        "dcs" to (dcs.toIntOrNull() ?: 0),
                        "slot" to (slotIndex.toIntOrNull() ?: 0),
                        "language" to languageCode
                    )

                    triggerAlert(context, finalBody, level, delayMs, isEtws, adv, 
                        onRootError = { showRootErrorDialog = true }
                    ) { newLog ->
                        logs += newLog
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.trigger_button))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Google Play Services Alerts Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.google_alerts_section), style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { isGoogleAlertsExpanded = !isGoogleAlertsExpanded }) {
                        Icon(
                            imageVector = if (isGoogleAlertsExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = null
                        )
                    }
                }
                
                if (isGoogleAlertsExpanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = {
                            scope.launch {
                                logs += "Launching Google Earthquake Demo (via Xposed)...\n"
                                // Use Xposed to trigger the demo properly with the required Args object
                                val intent = android.content.Intent("top.stevezmt.trigger.ACTION_REAL_ALERT")
                                intent.setPackage("com.google.android.gms")
                                intent.putExtra("is_test", true)
                                intent.putExtra("ux_extra", "EALERT_DEMO")
                                intent.putExtra("event_id", "Demo Simulation")
                                context.sendBroadcast(intent)
                                logs += "Broadcast sent to GMS. Check Xposed logs.\n"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.google_earthquake_button))
                    }
                    Text(stringResource(R.string.google_earthquake_desc), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                logs += "Launching GMS Earthquake Settings...\n"
                                withContext(Dispatchers.IO) {
                                    try {
                                        val cmd = "am start -n com.google.android.gms/com.google.android.location.settings.EAlertSettingsActivity"
                                        Runtime.getRuntime().exec(arrayOf("su", "-c", cmd)).waitFor()
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) { logs += "Failed: ${e.message}\n" }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("GMS Earthquake Settings")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = isGmsRealAlert, onCheckedChange = { isGmsRealAlert = it })
                        Text(stringResource(R.string.simulate_real_alert_label))
                    }

                    OutlinedTextField(
                        value = gmsMagnitude,
                        onValueChange = { gmsMagnitude = it },
                        label = { Text(stringResource(R.string.magnitude_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(stringResource(R.string.alert_params_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.alert_params_desc),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    OutlinedTextField(
                        value = regionName,
                        onValueChange = { regionName = it },
                        label = { Text(stringResource(R.string.region_name_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = latitude,
                            onValueChange = { latitude = it },
                            label = { Text(stringResource(R.string.latitude_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = longitude,
                            onValueChange = { longitude = it },
                            label = { Text(stringResource(R.string.longitude_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    OutlinedTextField(
                        value = distanceKm,
                        onValueChange = { distanceKm = it },
                        label = { Text(stringResource(R.string.distance_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = polygonRadiusKm,
                            onValueChange = { polygonRadiusKm = it },
                            label = { Text(stringResource(R.string.polygon_radius_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = alertType,
                            onValueChange = { alertType = it },
                            label = { Text(stringResource(R.string.alert_type_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (!isXposedActive) {
                                showXposedErrorDialog = true
                                return@Button
                            }
                            scope.launch {
                                val delayMs = (delaySeconds.toLongOrNull() ?: 0) * 1000
                                if (delayMs > 0) {
                                    logs += "Waiting ${delaySeconds}s before triggering...\n"
                                    kotlinx.coroutines.delay(delayMs)
                                }
                                logs += "Triggering GMS Alert via Xposed Bridge...\n"
                                // Send broadcast to Xposed module in GMS
                                val intent = android.content.Intent("top.stevezmt.trigger.ACTION_REAL_ALERT")
                                intent.setPackage("com.google.android.gms")
                                intent.putExtra("is_test", !isGmsRealAlert)
                                intent.putExtra("magnitude", gmsMagnitude.toDoubleOrNull() ?: 5.6)
                                intent.putExtra("event_id", if (isGmsRealAlert) "Real Simulation" else "Test Simulation")
                                
                                // Pass parameters
                                intent.putExtra("region_name", regionName)
                                intent.putExtra("lat", latitude.toDoubleOrNull() ?: 37.7749)
                                intent.putExtra("lng", longitude.toDoubleOrNull() ?: -122.4194)
                                intent.putExtra("distance", distanceKm.toDoubleOrNull() ?: 10.5)
                                intent.putExtra("polygon_radius", polygonRadiusKm.toDoubleOrNull() ?: 50.0)
                                
                                // Hardcoded working values for Real Alert
                                intent.putExtra("override_k", 5) // Must be 5 for EALERT_DISPLAY
                                intent.putExtra("override_n", alertType.toIntOrNull() ?: 1)
                                intent.putExtra("override_m", 2)
                                
                                // If it's a test, we MUST provide EALERT_DEMO to trigger the demo UI.
                                // If it's real, we MUST provide EALERT_DISPLAY to trigger the real UI.
                                if (!isGmsRealAlert) {
                                    intent.putExtra("ux_extra", "EALERT_DEMO")
                                } else {
                                    intent.putExtra("ux_extra", "EALERT_DISPLAY")
                                }
                                
                                context.sendBroadcast(intent)
                                logs += "Broadcast sent to GMS. Check Xposed logs if nothing happens.\n"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.trigger_gms_xposed_button))
                    }
                    Text(stringResource(R.string.xposed_required_hint), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                scope.launch {
                    openWeaSettings(context) { logs += it }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.settings_button))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Logs", logs)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, toastCopied, Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.copy_logs_button))
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.logs_label), style = MaterialTheme.typography.titleMedium)
        Text(
            text = logs,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(8.dp)
                .verticalScroll(rememberScrollState()) // Make logs scrollable independently
        )
    }
}

suspend fun openWeaSettings(context: android.content.Context, onLog: (String) -> Unit) {
    val intents = listOf(
        android.content.Intent("android.settings.WIRELESS_EMERGENCY_ALERTS_SETTINGS"),
        // Try .module version first as requested
        android.content.Intent().setClassName("com.android.cellbroadcastreceiver.module", "com.android.cellbroadcastreceiver.CellBroadcastSettings"),
        android.content.Intent().setClassName("com.android.cellbroadcastreceiver", "com.android.cellbroadcastreceiver.CellBroadcastSettings"),
        android.content.Intent().setClassName("com.google.android.cellbroadcastreceiver", "com.google.android.cellbroadcastreceiver.CellBroadcastSettings"),
        android.content.Intent().setClassName("com.android.cellbroadcastreceiver", "com.android.cellbroadcastreceiver.CellBroadcastListActivity"),
        // Deprioritize App Info screen
        android.content.Intent(android.provider.Settings.ACTION_SETTINGS), 
        android.content.Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS),
        android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(android.net.Uri.parse("package:com.android.cellbroadcastreceiver"))
    )

    var success = false
    for (intent in intents) {
        try {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            // Check if intent can be resolved
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                onLog("Opened settings via ${intent.action ?: intent.component?.className}\n")
                if (intent.action == android.provider.Settings.ACTION_SETTINGS || intent.action == android.provider.Settings.ACTION_SECURITY_SETTINGS) {
                     Toast.makeText(context, "Please manually find 'Wireless Emergency Alerts' in Settings", Toast.LENGTH_LONG).show()
                }
                success = true
                break
            }
        } catch (e: Exception) {
            // Continue to next intent
        }
    }
    
    if (!success) {
        onLog("Standard launch failed. Trying Root launch for hidden settings...\n")
        withContext(Dispatchers.IO) {
            try {
                // List of components to try via Root
                val components = listOf(
                    "com.android.cellbroadcastreceiver.module/com.android.cellbroadcastreceiver.CellBroadcastSettings",
                    "com.android.cellbroadcastreceiver/com.android.cellbroadcastreceiver.CellBroadcastSettings",
                    "com.google.android.cellbroadcastreceiver/com.google.android.cellbroadcastreceiver.CellBroadcastSettings"
                )

                for (component in components) {
                    val cmd = "am start -n $component"
                    val process = Runtime.getRuntime().exec("su")
                    val os = java.io.DataOutputStream(process.outputStream)
                    os.writeBytes(cmd + "\n")
                    os.writeBytes("exit\n")
                    os.flush()
                    process.waitFor()
                    
                    if (process.exitValue() == 0) {
                        withContext(Dispatchers.Main) {
                            onLog("Successfully launched $component via Root!\n")
                            success = true
                        }
                        break // Stop if successful
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onLog("Root launch failed: ${e.message}\n")
                }
            }
        }
    }

    if (!success) {
        onLog("Failed to open WEA settings. Tried all known methods (including Root).\n")
        Toast.makeText(context, "Could not open settings", Toast.LENGTH_SHORT).show()
    }
}

suspend fun triggerAlert(
    context: android.content.Context,
    body: String,
    cmasClass: Int,
    delayMs: Long,
    isEtws: Boolean,
    adv: Map<String, Any>,
    onRootError: () -> Unit = {},
    onLog: (String) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            val apkPath = context.applicationInfo.sourceDir
            // Base64 encode the body to avoid shell escaping and encoding issues
            val encodedBody = Base64.encodeToString(body.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            
            // Order: body(b64), level, delay, isEtws, serial, category, priority, scope, dcs, slot, language
            val cmd = "CLASSPATH=$apkPath app_process /system/bin top.stevezmt.cellbroadcast.trigger.RootMain " +
                    "\"$encodedBody\" $cmasClass $delayMs $isEtws " +
                    "${adv["serial"]} ${adv["category"]} ${adv["priority"]} " +
                    "${adv["scope"]} ${adv["dcs"]} ${adv["slot"]} \"${adv["language"]}\""
            
            withContext(Dispatchers.Main) { onLog("Executing: $cmd\n") }

            val process = Runtime.getRuntime().exec("su")
            val os = java.io.DataOutputStream(process.outputStream)
            os.writeBytes(cmd + "\n")
            os.writeBytes("exit\n")
            os.flush()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val logLine = line
                withContext(Dispatchers.Main) { onLog("STDOUT: $logLine\n") }
            }
            
            var hasError = false
            while (errorReader.readLine().also { line = it } != null) {
                val logLine = line
                withContext(Dispatchers.Main) { onLog("STDERR: $logLine\n") }
                if (logLine?.contains("su: not found") == true || logLine?.contains("Permission denied") == true) {
                    hasError = true
                }
            }

            process.waitFor()
            val exitValue = process.exitValue()
            withContext(Dispatchers.Main) { 
                onLog("Process exited with code $exitValue\n")
                if (exitValue != 0 || hasError) {
                    onRootError()
                }
            }

        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onLog("Exception: ${e.message}\n")
                if (e.message?.contains("Cannot run program \"su\"") == true) {
                    onRootError()
                }
            }
        }
    }
}
