package com.example.safelaser

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.safelaser.core.DeviceParameter
import com.example.safelaser.core.KnownState
import com.example.safelaser.core.OutputState
import com.example.safelaser.core.SafetyState
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SafeLaserApp() }
    }
}

private enum class AppScreen(val label: String, val icon: ImageVector) {
    STATUS("STATUS", Icons.Default.Home),
    CONFIGURATION("CONFIG", Icons.Default.Tune),
    SWEEP("SWEEP", Icons.Default.Waves),
    LOGS("LOGS", Icons.Default.ReceiptLong),
    SETTINGS("SETTINGS", Icons.Default.Settings),
}

private val SafeDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFC107),
    onPrimary = Color.Black,
    secondary = Color(0xFF80CBC4),
    background = Color(0xFF101214),
    surface = Color(0xFF1B1F23),
    error = Color(0xFFFF6B6B),
)

@Composable
fun SafeLaserApp() {
    val context = LocalContext.current
    val controller = remember { LaserController(context) }
    val ui by controller.state.collectAsState()
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var screen by rememberSaveable { mutableStateOf(AppScreen.STATUS) }
    var showApplyDialog by rememberSaveable { mutableStateOf(false) }
    var showExperimentalDialog by rememberSaveable { mutableStateOf(false) }
    val preferences = remember { context.getSharedPreferences("operator_preferences", 0) }
    var textScale by remember { mutableStateOf(preferences.getFloat("text_scale", 1f)) }
    var highContrast by remember { mutableStateOf(preferences.getBoolean("high_contrast", false)) }
    var reducedMotion by remember { mutableStateOf(preferences.getBoolean("reduced_motion", false)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.all { it }) scope.launch { controller.connectSimulator() }
    }
    val connect: () -> Unit = {
        if (BuildConfig.TRANSPORT_MODE == "SIMULATOR") scope.launch { controller.connectSimulator() }
        else {
            val required = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            if (required.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED })
                scope.launch { controller.connectSimulator() }
            else permissionLauncher.launch(required)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) controller.onBackground()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides androidx.compose.ui.unit.Density(density.density, density.fontScale * textScale)) {
    MaterialTheme(colorScheme = if (highContrast) SafeDarkColors.copy(surface = Color.Black, background = Color.Black) else SafeDarkColors) {
        Scaffold(
            topBar = {
                GlobalStopHeader(
                    output = ui.status?.output ?: OutputState.UNKNOWN,
                    onStop = { scope.launch { controller.requestStop() } },
                )
            },
            bottomBar = {
                NavigationBar(Modifier.navigationBarsPadding()) {
                    AppScreen.entries.forEach { item ->
                        NavigationBarItem(
                            selected = screen == item,
                            onClick = { screen = item },
                            icon = { Icon(item.icon, null) },
                            label = { Text(item.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("CLEANING LASER CONTROLLER", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(ui.buildLabel, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                if (screen == AppScreen.CONFIGURATION || screen == AppScreen.SWEEP) {
                    ExperimentalLiveTuningPanel(
                        enabled = ui.experimentalLiveTuning,
                        onEnable = { showExperimentalDialog = true },
                        onDisable = { controller.setExperimentalLiveTuning(false) },
                    )
                }
                when (screen) {
                    AppScreen.STATUS -> StatusScreen(ui, controller, scope, connect)
                    AppScreen.CONFIGURATION -> ParameterScreen(
                        title = "LASER CONFIGURATION SETTINGS",
                        parameterIds = setOf("output_percent", "pwm_frequency_hz", "duty_cycle_percent", "pulse_on_ms", "pulse_off_ms"),
                        ui = ui,
                        controller = controller,
                        onReview = { showApplyDialog = true },
                    )
                    AppScreen.SWEEP -> SweepScreen(ui, controller) { showApplyDialog = true }
                    AppScreen.LOGS -> LogScreen(ui.auditLines)
                    AppScreen.SETTINGS -> SettingsScreen(
                        textScale, highContrast, reducedMotion,
                        onTextScale = { textScale = it; preferences.edit().putFloat("text_scale", it).apply() },
                        onContrast = { highContrast = it; preferences.edit().putBoolean("high_contrast", it).apply() },
                        onMotion = { reducedMotion = it; preferences.edit().putBoolean("reduced_motion", it).apply() },
                        onLanguage = { tag -> AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) },
                        onExport = {
                            val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", controller.exportAudit())
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = "application/zip"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }, "Export audit session"))
                        },
                        onObservation = controller::addObservation,
                    )
                }
                ui.message?.let { MessageBanner(it) }
            }
        }

        if (showApplyDialog) {
            ParameterReviewDialog(
                ui = ui,
                onCancel = { showApplyDialog = false },
                onApply = {
                    showApplyDialog = false
                    scope.launch { controller.applyPendingParameters() }
                },
            )
        }

        if (ui.showEmissionDialog) {
            EmissionDialog(ui, controller) { scope.launch { controller.requestEmission() } }
        }
        if (showExperimentalDialog) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("ENABLE EXPERIMENTAL LIVE TUNING") },
                text = { Text("This mode permits reviewed power, frequency, and duty cycle changes while output is active. Every command and response is logged.") },
                confirmButton = {
                    Button(onClick = {
                        controller.setExperimentalLiveTuning(true)
                        showExperimentalDialog = false
                    }) { Text("ENABLE EXPERIMENTAL LIVE TUNING") }
                },
                dismissButton = { Button(onClick = { showExperimentalDialog = false }) { Text("CANCEL") } },
            )
        }
    }}
}

@Composable
private fun GlobalStopHeader(output: OutputState, onStop: () -> Unit) {
    Surface(shadowElevation = 8.dp) {
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when (output) {
                    OutputState.ON -> "LASER OUTPUT IS ON"
                    OutputState.OFF -> "LASER OUTPUT IS OFF"
                    OutputState.UNKNOWN -> "LASER OUTPUT STATE IS UNKNOWN"
                },
                fontWeight = FontWeight.Bold,
                color = if (output == OutputState.OFF) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
            )
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB00020)),
            ) {
                Icon(Icons.Default.StopCircle, null)
                Spacer(Modifier.width(10.dp))
                Text("REQUEST LASER STOP", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StatusScreen(ui: OperatorUiState, controller: LaserController, scope: kotlinx.coroutines.CoroutineScope, onConnect: () -> Unit) {
    StatusCard("Bluetooth connection", if (ui.status == null) "BLUETOOTH DISCONNECTED" else "BLUETOOTH CONNECTED")
    StatusCard("Device identity", ui.status?.identity?.let { "${it.model} ${it.serialNumber}" } ?: "DEVICE IDENTITY NOT VERIFIED")
    StatusCard("Interlock", when (ui.status?.interlockClosed) {
        KnownState.YES -> "INTERLOCK CLOSED"
        KnownState.NO -> "INTERLOCK OPEN"
        else -> "INTERLOCK STATE IS UNKNOWN"
    })
    StatusCard("Cooling system", when (ui.status?.coolingAvailable) {
        KnownState.YES -> "COOLING SYSTEM READY"
        KnownState.NO -> "COOLING SYSTEM FAULT"
        else -> "COOLING SYSTEM STATE IS UNKNOWN"
    })
    StatusCard("Application state", ui.safetyState::class.simpleName ?: "UNKNOWN")
    StatusCard("Active alarms", ui.status?.alarms?.ifEmpty { listOf("NO ACTIVE ALARMS") }?.joinToString() ?: "ALARM STATE IS UNKNOWN")
    StatusCard("Last valid device update", ui.status?.lastUpdateMillis?.toString() ?: "NO VALID DEVICE UPDATE")
    if (ui.status == null) {
        Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
            Text(if (BuildConfig.TRANSPORT_MODE == "SIMULATOR") "CONNECT SIMULATED DEVICE" else "SCAN FOR BWT LASER")
        }
        ui.discoveredLasers.forEach { device ->
            Button(onClick = { scope.launch { controller.connectLaser(device.address) } }, modifier = Modifier.fillMaxWidth()) {
                Text("${device.name}  ${device.address}  ${device.rssi} dBm")
            }
        }
    } else if (ui.safetyState is SafetyState.ReadyToArm) {
        HoldToArmControl { scope.launch { controller.armAfterHold() } }
    }
}

@Composable
private fun ParameterScreen(
    title: String,
    parameterIds: Set<String>,
    ui: OperatorUiState,
    controller: LaserController,
    onReview: () -> Unit,
) {
    Text(title, fontWeight = FontWeight.Bold, fontSize = 19.sp)
    Text(if (ui.experimentalLiveTuning) "Edits can be applied during active output." else "Edits remain pending until review and apply.")
    ui.status?.parameters?.filter { it.id in parameterIds }?.forEach { parameter ->
        ParameterEditor(parameter, ui.pendingParameters.values[parameter.id]) { controller.editParameter(parameter.id, it) }
    }
    Button(onClick = onReview, modifier = Modifier.fillMaxWidth(), enabled = ui.pendingParameters.values.isNotEmpty()) {
        Text("REVIEW CHANGES")
    }
}

@Composable
private fun SweepScreen(ui: OperatorUiState, controller: LaserController, onReview: () -> Unit) {
    Text("LASER SWEEP PARAMETERS", fontWeight = FontWeight.Bold, fontSize = 19.sp)
    Text("Waveform meanings are unknown. Use waveform slots with the simulator only.")
    Text("Waveform slot", fontWeight = FontWeight.Bold)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0..4).forEach { slot -> SlotButton(slot, ui.pendingParameters.waveformSlot, controller::selectWaveformSlot, Modifier.weight(1f)) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (5..8).forEach { slot -> SlotButton(slot, ui.pendingParameters.waveformSlot, controller::selectWaveformSlot, Modifier.weight(1f)) }
        Spacer(Modifier.weight(1f))
    }
    ui.status?.parameters?.filter { it.id in setOf("swing_frequency_hz", "swing_length_mm") }?.forEach { parameter ->
        ParameterEditor(parameter, ui.pendingParameters.values[parameter.id]) { controller.editParameter(parameter.id, it) }
    }
    Button(onClick = onReview, modifier = Modifier.fillMaxWidth(), enabled = ui.pendingParameters.values.isNotEmpty()) {
        Text("REVIEW CHANGES")
    }
}

@Composable
private fun SlotButton(slot: Int, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    Button(
        onClick = { onSelect(slot) },
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (slot == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (slot == selected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) { Text(slot.toString()) }
}

@Composable
private fun ParameterEditor(parameter: DeviceParameter, pendingValue: Double?, onEdit: (Double) -> Unit) {
    var text by remember(parameter.id, pendingValue) {
        mutableStateOf((pendingValue ?: parameter.appliedValue)?.toString() ?: "")
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(parameter.name, fontWeight = FontWeight.Bold)
            Text("Applied: ${parameter.appliedValue ?: "UNKNOWN"} ${parameter.unit}")
            Text("Range: ${parameter.minimum} to ${parameter.maximum} ${parameter.unit}", fontSize = 12.sp)
            OutlinedTextField(
                value = text,
                onValueChange = { newValue ->
                    text = newValue
                    newValue.toDoubleOrNull()?.let(onEdit)
                },
                label = { Text("Pending value (${parameter.unit})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                isError = text.toDoubleOrNull()?.let { it !in parameter.minimum..parameter.maximum } ?: true,
            )
        }
    }
}

@Composable
private fun LogScreen(lines: List<String>) {
    Text("LASER COMMAND LOG", fontWeight = FontWeight.Bold, fontSize = 19.sp)
    Text("The app stores a bounded log in private app storage.")
    if (lines.isEmpty()) Text("NO COMMAND LOG ENTRIES")
    lines.forEach { line ->
        Card(Modifier.fillMaxWidth()) { Text(line, Modifier.padding(12.dp), fontSize = 12.sp) }
    }
}

@Composable
private fun ExperimentalLiveTuningPanel(enabled: Boolean, onEnable: () -> Unit, onDisable: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = if (enabled) Color(0xFF7A4E00) else MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("EXPERIMENTAL LIVE TUNING", fontWeight = FontWeight.Bold)
            Text(if (BuildConfig.TRANSPORT_MODE == "SIMULATOR") "This mode changes simulated parameters." else "This mode can change power, frequency, and duty cycle during active output.")
            Button(onClick = if (enabled) onDisable else onEnable, modifier = Modifier.fillMaxWidth()) {
                Text(if (enabled) "DISABLE EXPERIMENTAL MODE" else "REVIEW EXPERIMENTAL MODE")
            }
        }
    }
}

@Composable
private fun ParameterReviewDialog(ui: OperatorUiState, onCancel: () -> Unit, onApply: () -> Unit) {
    val simulatedLiveApply = ui.status?.output == OutputState.ON && ui.experimentalLiveTuning
    AlertDialog(
        onDismissRequest = {},
        title = { Text("REVIEW PARAMETER CHANGES") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ui.pendingParameters.values.forEach { (id, value) -> Text("$id: $value") }
                Text("Waveform slot: ${ui.pendingParameters.waveformSlot}")
                if (simulatedLiveApply) Text("These changes apply during simulated emission.", color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = { Button(onClick = onApply) { Text("APPLY PARAMETERS") } },
        dismissButton = { Button(onClick = onCancel) { Text("DISCARD CHANGES") } },
    )
}

@Composable
private fun EmissionDialog(ui: OperatorUiState, controller: LaserController, onEmission: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("REQUEST LASER EMISSION") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("This action requests simulated laser emission with the applied parameters.")
                Text("Device: ${ui.status?.identity?.serialNumber ?: "UNKNOWN"}")
                Text("Waveform slot: ${ui.appliedWaveformSlot}")
                ui.status?.parameters?.forEach { Text("${it.name}: ${it.appliedValue} ${it.unit}") }
            }
        },
        confirmButton = { Button(onClick = onEmission) { Text("REQUEST LASER EMISSION") } },
        dismissButton = { Button(onClick = controller::cancelEmissionDialog) { Text("CANCEL") } },
    )
}

@Composable
private fun StatusCard(label: String, value: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(label, fontWeight = FontWeight.Bold)
            Text(value, fontSize = 17.sp)
        }
    }
}

@Composable
private fun MessageBanner(message: String) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp)) {
        Icon(Icons.Default.ReportProblem, null)
        Spacer(Modifier.width(8.dp))
        Text(message)
    }
}

@Composable
private fun SettingsScreen(
    textScale: Float,
    highContrast: Boolean,
    reducedMotion: Boolean,
    onTextScale: (Float) -> Unit,
    onContrast: (Boolean) -> Unit,
    onMotion: (Boolean) -> Unit,
    onLanguage: (String) -> Unit,
    onExport: () -> Unit,
    onObservation: (String) -> Unit,
) {
    var observation by remember { mutableStateOf("") }
    Text("SETTINGS", fontWeight = FontWeight.Bold, fontSize = 19.sp)
    Text("Language", fontWeight = FontWeight.Bold)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("en" to "EN", "es" to "ES", "zh-CN" to "中文", "fr" to "FR").forEach { (tag, label) ->
            Button(onClick = { onLanguage(tag) }, modifier = Modifier.weight(1f)) { Text(label) }
        }
    }
    Text("Text size", fontWeight = FontWeight.Bold)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(1f to "NORMAL", 1.2f to "LARGE", 1.4f to "EXTRA LARGE").forEach { (scale, label) ->
            Button(onClick = { onTextScale(scale) }, modifier = Modifier.weight(1f), enabled = textScale != scale) { Text(label) }
        }
    }
    SettingSwitch("High contrast", highContrast, onContrast)
    SettingSwitch("Reduced motion", reducedMotion, onMotion)
    Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text("EXPORT ACTIVE AUDIT SESSION") }
    OutlinedTextField(
        value = observation,
        onValueChange = { observation = it },
        label = { Text("Operator observation") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
    )
    Button(onClick = { onObservation(observation); observation = "" }, enabled = observation.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        Text("RECORD OBSERVATION")
    }
    Text("Build: ${BuildConfig.VERSION_NAME}  ${BuildConfig.TRANSPORT_MODE}")
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}


@Composable
private fun HoldToArmControl(onComplete: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().height(72.dp).pointerInput(Unit) {
            detectTapGestures(onPress = {
                val releasedEarly = withTimeoutOrNull(2_000) { tryAwaitRelease() }
                if (releasedEarly == null) onComplete()
            })
        },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("HOLD TO ARM LASER", color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Hold for two seconds", color = Color.Black)
        }
    }
}
