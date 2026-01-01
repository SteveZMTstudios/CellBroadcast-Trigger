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
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    var messageBody by remember { mutableStateOf("") }
    // Initialize with default message if empty
    if (messageBody.isEmpty()) {
        messageBody = stringResource(R.string.default_message)
    }
    
    var selectedLevelIndex by remember { mutableIntStateOf(1) } // Default to Extreme
    var delaySeconds by remember { mutableStateOf("0") }
    var logs by remember { mutableStateOf("Ready...\n") }
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val logStarting = stringResource(R.string.log_starting)
    val toastCopied = stringResource(R.string.toast_copied)
    
    // First Run Warning Dialog
    var showWarningDialog by remember { mutableStateOf(false) }
    val sharedPreferences = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
    
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
        Text(stringResource(R.string.app_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = messageBody,
            onValueChange = { messageBody = it },
            label = { Text(stringResource(R.string.alert_message_label)) },
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

        Button(
            onClick = {
                scope.launch {
                    logs += logStarting
                    val delayMs = (delaySeconds.toLongOrNull() ?: 0) * 1000
                    val selectedValue = levels[selectedLevelIndex].second
                    val isEtws = selectedValue >= 0x10
                    val level = if (isEtws) (selectedValue - 0x10) else selectedValue
                    
                    triggerAlert(context, messageBody, level, delayMs, isEtws) { newLog ->
                        logs += newLog
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.trigger_button))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                scope.launch {
                    openWeaSettings(context) { logs += it }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
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
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
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
    onLog: (String) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            val apkPath = context.applicationInfo.sourceDir
            // Base64 encode the body to avoid shell escaping and encoding issues
            val encodedBody = Base64.encodeToString(body.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            
            val cmd = "CLASSPATH=$apkPath app_process /system/bin top.stevezmt.cellbroadcast.trigger.RootMain \"$encodedBody\" $cmasClass $delayMs $isEtws"
            
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
            
            while (errorReader.readLine().also { line = it } != null) {
                val logLine = line
                withContext(Dispatchers.Main) { onLog("STDERR: $logLine\n") }
            }

            process.waitFor()
            val exitValue = process.exitValue()
            withContext(Dispatchers.Main) { 
                onLog("Process exited with code $exitValue\n")
                if (exitValue != 0) {
                    Toast.makeText(context, "Root command failed. Check logs.", Toast.LENGTH_LONG).show()
                }
            }

        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onLog("Exception: ${e.message}\n")
                Toast.makeText(context, "Failed to execute root command. Is device rooted?", Toast.LENGTH_LONG).show()
            }
        }
    }
}
