package com.computedlogic.label

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.computedlogic.label.ble.BleManager
import com.computedlogic.label.render.LabelRenderer


// ── Design tokens ─────────────────────────────────────────────────────────────
// All colors now resolve from MaterialTheme so light/dark just works.

private val FontSizes = listOf(8, 10, 12, 14, 16, 18, 20, 24, 28, 32, 36, 40, 48, 56, 64, 72, 80, 96)
private val FontFamilies = listOf("Arial", "Times New Roman", "Courier New", "Roboto", "Georgia")

// ── Root screen ───────────────────────────────────────────────────────────────

@Composable
fun LabelPrintScreen(
    modifier: Modifier = Modifier,
    vm: LabelViewModel = viewModel()
) {
    val context = LocalContext.current
    val bleState by vm.bleState.collectAsState()

    // ── Permission launcher ───────────────────────────────────────────────────
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
    }

    var permissionsGranted by remember {
        mutableStateOf(permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants -> permissionsGranted = grants.values.all { it } }

    // ── Label state ───────────────────────────────────────────────────────────
    var selectedTab    by remember { mutableIntStateOf(0) }  // 0=Text, 1=Picture, 2=QR
    var labelText      by remember { mutableStateOf("") }
    var fontSize       by remember { mutableIntStateOf(48) }
    var fontFamily     by remember { mutableStateOf("Arial") }
    var isBold         by remember { mutableStateOf(false) }
    var isItalic       by remember { mutableStateOf(false) }
    var isUnderline    by remember { mutableStateOf(false) }
    var hAlign         by remember { mutableStateOf(HAlign.LEFT) }
    var vAlign         by remember { mutableStateOf(VAlign.TOP) }
    var paddingTop     by remember { mutableIntStateOf(0) }
    var paddingBottom  by remember { mutableIntStateOf(0) }
    var paddingLeft    by remember { mutableIntStateOf(0) }
    var paddingRight   by remember { mutableIntStateOf(0) }
    var copies         by remember { mutableIntStateOf(1) }

    // ── Dialog state ─────────────────────────────────────────────────────────
    var showScanDialog by remember { mutableStateOf(false) }

    // Build print params from current UI state
    fun buildParams() = PrintParams(
        text            = labelText,
        fontSize        = fontSize,
        isBold          = isBold,
        isItalic        = isItalic,
        isUnderline     = isUnderline,
        hAlign          = hAlign,
        vAlign          = vAlign,
        paddingTopMm    = paddingTop,
        paddingBottomMm = paddingBottom,
        paddingLeftMm   = paddingLeft,
        paddingRightMm  = paddingRight,
        copies          = copies
    )

    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // ── Header ───────────────────────────────────────────────
        Text(
            "Label Maker",
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onBackground,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Design and print your labels",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))

        // ── Tabs ─────────────────────────────────────────────────
        TabBar(selectedTab = selectedTab, onTabSelected = { selectedTab = it })

        Spacer(Modifier.height(16.dp))

        // ── Preview section header ──────────────────────────────
        SectionHeader(Icons.Default.Visibility, "Preview")
        Spacer(Modifier.height(8.dp))

        // ── Preview card (bitmap only) ───────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                LabelPreview(
                    text        = labelText,
                    fontSize    = fontSize,
                    isBold      = isBold,
                    isItalic    = isItalic,
                    isUnderline = isUnderline,
                    hAlign      = hAlign,
                    vAlign      = vAlign,
                    paddingTopMm    = paddingTop,
                    paddingBottomMm = paddingBottom,
                    paddingLeftMm   = paddingLeft,
                    paddingRightMm  = paddingRight
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Content section header ───────────────────────────────
        SectionHeader(Icons.Default.Edit, "Content")
        Spacer(Modifier.height(8.dp))

        // ── Content card (tab-specific controls) ─────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                when (selectedTab) {
                    0 -> {
                        // ── Text tab ─────────────────────────
                        OutlinedTextField(
                            value = labelText,
                            onValueChange = { labelText = it },
                            placeholder = {
                                Text("Enter label text\u2026", color = colors.onSurfaceVariant)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor      = colors.primary,
                                unfocusedBorderColor    = colors.outline,
                                cursorColor             = colors.primary,
                                focusedTextColor        = colors.onSurface,
                                unfocusedTextColor      = colors.onSurface,
                                focusedContainerColor   = colors.surfaceVariant,
                                unfocusedContainerColor = colors.surfaceVariant,
                            )
                        )
                        Spacer(Modifier.height(12.dp))

                        AlignmentRow(
                            hAlign = hAlign, vAlign = vAlign,
                            onHAlignChanged = { hAlign = it },
                            onVAlignChanged = { vAlign = it }
                        )

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FontSizeDropdown(
                                value = fontSize,
                                onValueChange = { fontSize = it },
                                modifier = Modifier.weight(1f)
                            )
                            FontFamilyDropdown(
                                value = fontFamily,
                                onValueChange = { fontFamily = it },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        TextStyleRow(
                            isBold = isBold, isItalic = isItalic, isUnderline = isUnderline,
                            onBoldToggle      = { isBold      = !isBold },
                            onItalicToggle    = { isItalic    = !isItalic },
                            onUnderlineToggle = { isUnderline = !isUnderline }
                        )
                    }
                    1 -> {
                        // ── Picture tab (placeholder) ────────
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Image, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("Picture support coming soon", color = colors.onSurfaceVariant, fontSize = 14.sp)
                            }
                        }
                    }
                    2 -> {
                        // ── QR Code tab (placeholder) ────────
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.QrCode, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("QR Code support coming soon", color = colors.onSurfaceVariant, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Padding section header ───────────────────────────────
        SectionHeader(Icons.Default.SpaceBar, "Margins")
        Spacer(Modifier.height(8.dp))

        // ── Padding (shared across all tabs) ─────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(Modifier.padding(16.dp)) {
                PaddingControls(
                    paddingTop    = paddingTop,    paddingBottom = paddingBottom,
                    paddingLeft   = paddingLeft,   paddingRight  = paddingRight,
                    onTopChange    = { paddingTop    = it.coerceAtLeast(0) },
                    onBottomChange = { paddingBottom = it.coerceAtLeast(0) },
                    onLeftChange   = { paddingLeft   = it.coerceAtLeast(0) },
                    onRightChange  = { paddingRight  = it.coerceAtLeast(0) }
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Copies ───────────────────────────────────────────────
        SectionHeader(Icons.Default.ContentCopy, "Copies")
        Spacer(Modifier.height(8.dp))
        CopiesStepper(
            value = copies,
            onValueChange = { copies = it.coerceIn(1, 999) }
        )

        Spacer(Modifier.height(20.dp))

        // ── Print / state buttons ────────────────────────────────
        val isConnected = bleState is BleManager.State.Print ||
                bleState is BleManager.State.PrintSuccess

        when (bleState) {
            is BleManager.State.Printing -> {
                val pct = (bleState as BleManager.State.Printing).progress
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    LinearProgressIndicator(
                        progress = { pct / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = colors.primary,
                        trackColor = colors.surfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Printing\u2026 $pct%", color = colors.onSurfaceVariant, fontSize = 14.sp)
                }
            }
            else -> {
                // ── Connect / Disconnect button ───────────────
                if (isConnected) {
                    val currentState = bleState
                    val deviceName = when (currentState) {
                        is BleManager.State.Print        -> currentState.device.displayName()
                        is BleManager.State.PrintSuccess -> currentState.device.displayName()
                        else -> ""
                    }
                    OutlinedButton(
                        onClick = { vm.disconnect() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                    ) {
                        Icon(
                            Icons.Default.BluetoothDisabled, null,
                            tint = colors.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Disconnect $deviceName",
                            color = colors.onSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            if (!permissionsGranted) {
                                permLauncher.launch(permissions)
                                return@Button
                            }
                            showScanDialog = true
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                    ) {
                        Icon(
                            Icons.Default.Bluetooth, null,
                            tint = colors.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        val connectLabel = when (bleState) {
                            is BleManager.State.Connecting -> "Connecting\u2026"
                            else                           -> "Connect Printer"
                        }
                        Text(
                            connectLabel,
                            color = colors.onPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── Print button (disabled when not connected) ─
                Button(
                    onClick = { vm.print(buildParams()) },
                    enabled = isConnected,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        disabledContainerColor = colors.onSurface.copy(alpha = 0.12f)
                    )
                ) {
                    Icon(
                        Icons.Default.Print, null,
                        tint = if (isConnected) colors.onPrimary else colors.onSurface.copy(alpha = 0.38f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Print",
                        color = if (isConnected) colors.onPrimary else colors.onSurface.copy(alpha = 0.38f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    // ── BLE scan dialog ───────────────────────────────────────────────────────
    if (showScanDialog) {
        BleScanDialog(
            bleState    = bleState,
            onScan      = { vm.startScan() },
            onStopScan  = { vm.stopScan() },
            onSelect    = { device ->
                showScanDialog = false
                vm.connectOnly(device)
            },
            onDismiss   = {
                showScanDialog = false
                vm.stopScan()
            }
        )
    }
}

// ── BLE device name helper ────────────────────────────────────────────────────

@SuppressLint("MissingPermission")
private fun BluetoothDevice.displayName(): String = name ?: address


// ── BLE scan dialog ───────────────────────────────────────────────────────────

@Composable
private fun BleScanDialog(
    bleState: BleManager.State,
    onScan: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onStopScan: () -> Unit,
    onSelect: (BluetoothDevice) -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) { onScan() }

    val colors = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text("Select Printer", color = colors.onSurface, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                when (bleState) {
                    is BleManager.State.Scanning -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = colors.primary,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("Scanning for printers\u2026", color = colors.onSurfaceVariant, fontSize = 14.sp)
                        }
                    }
                    is BleManager.State.Connecting -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = colors.primary,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Connecting to ${bleState.device.displayName()}\u2026",
                                color = colors.onSurfaceVariant, fontSize = 14.sp
                            )
                        }
                    }
                    is BleManager.State.DevicesFound -> {
                        if (bleState.devices.isEmpty()) {
                            Text("No printers found.", color = colors.onSurfaceVariant, fontSize = 14.sp)
                        } else {
                            Text(
                                "Found ${bleState.devices.size} printer(s):",
                                color = colors.onSurfaceVariant, fontSize = 13.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            bleState.devices.forEach { device ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.surfaceVariant)
                                        .clickable { onSelect(device) }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.BluetoothConnected,
                                        contentDescription = null,
                                        tint = colors.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            device.displayName(),
                                            color = colors.onSurface, fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            device.address,
                                            color = colors.onSurfaceVariant, fontSize = 12.sp
                                        )
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onScan, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Icon(Icons.Default.Refresh, null, tint = colors.primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Scan again", color = colors.primary, fontSize = 14.sp)
                        }
                    }
                    is BleManager.State.Error -> {
                        Text("Error: ${bleState.message}", color = colors.error, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onScan, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text("Retry scan", color = colors.primary)
                        }
                    }
                    else -> {
                        Text("Tap Scan to find printers.", color = colors.onSurfaceVariant, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.onSurfaceVariant)
            }
        }
    )
}

// ── Section header ────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = colors.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            color = colors.onBackground,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleSmall
        )
    }
}

// ── Tab bar ───────────────────────────────────────────────────────────────────

@Composable
private fun TabBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.outline.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TabItem(Icons.Default.TextFields, "Text",    selectedTab == 0, Modifier.weight(1f)) { onTabSelected(0) }
        TabItem(Icons.Default.Image,      "Picture", selectedTab == 1, Modifier.weight(1f)) { onTabSelected(1) }
        TabItem(Icons.Default.QrCode,     "QR Code", selectedTab == 2, Modifier.weight(1f)) { onTabSelected(2) }
    }
}

@Composable
private fun TabItem(
    icon: ImageVector, label: String, isSelected: Boolean,
    modifier: Modifier = Modifier, onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val fg = if (isSelected) colors.primary else colors.onSurfaceVariant
    val bg = if (isSelected) colors.primaryContainer else Color.Transparent
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, label, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = fg, fontSize = 15.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

// ── Label preview ─────────────────────────────────────────────────────────────

/**
 * Pixel-perfect preview: renders the label with [LabelRenderer] (the same code
 * used for printing) and displays the resulting bitmap scaled to fit the
 * preview area.  What you see IS what will print.
 */

@Composable
private fun LabelPreview(
    text: String, fontSize: Int,
    isBold: Boolean, isItalic: Boolean, isUnderline: Boolean,
    hAlign: HAlign, vAlign: VAlign,
    paddingTopMm: Int = 0, paddingBottomMm: Int = 0,
    paddingLeftMm: Int = 0, paddingRightMm: Int = 0
) {
    // Render the actual printer bitmap (96 px tall, width auto-sized to fit text)
    val imageBitmap by remember(
        text, fontSize, isBold, isItalic, isUnderline,
        hAlign, vAlign,
        paddingTopMm, paddingBottomMm, paddingLeftMm, paddingRightMm
    ) {
        derivedStateOf {
            LabelRenderer.render(
                text            = text,
                fontSize        = fontSize,
                isBold          = isBold,
                isItalic        = isItalic,
                isUnderline     = isUnderline,
                hAlign          = hAlign,
                vAlign          = vAlign,
                paddingTopMm    = paddingTopMm,
                paddingBottomMm = paddingBottomMm,
                paddingLeftMm   = paddingLeftMm,
                paddingRightMm  = paddingRightMm,
                labelLengthPx   = 0   // auto-size width to fit text
            ).asImageBitmap()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = imageBitmap,
            contentDescription = "Label preview",
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.None,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.85f)
        )
    }
}


// ── Alignment row ─────────────────────────────────────────────────────────────

@Composable
private fun AlignmentRow(
    hAlign: HAlign, vAlign: VAlign,
    onHAlignChanged: (HAlign) -> Unit,
    onVAlignChanged: (VAlign) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceVariant),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlignBtn(Icons.AutoMirrored.Filled.FormatAlignLeft,    hAlign == HAlign.LEFT,    Modifier.weight(1f)) { onHAlignChanged(HAlign.LEFT) }
        VDivider()
        AlignBtn(Icons.Default.FormatAlignCenter,              hAlign == HAlign.CENTER,  Modifier.weight(1f)) { onHAlignChanged(HAlign.CENTER) }
        VDivider()
        AlignBtn(Icons.AutoMirrored.Filled.FormatAlignRight,   hAlign == HAlign.RIGHT,   Modifier.weight(1f)) { onHAlignChanged(HAlign.RIGHT) }
        VDivider()
        AlignBtn(Icons.Default.VerticalAlignTop,    vAlign == VAlign.TOP,    Modifier.weight(1f)) { onVAlignChanged(VAlign.TOP) }
        VDivider()
        AlignBtn(Icons.Default.VerticalAlignCenter, vAlign == VAlign.CENTER, Modifier.weight(1f)) { onVAlignChanged(VAlign.CENTER) }
        VDivider()
        AlignBtn(Icons.Default.VerticalAlignBottom, vAlign == VAlign.BOTTOM, Modifier.weight(1f)) { onVAlignChanged(VAlign.BOTTOM) }
    }
}

@Composable
private fun AlignBtn(
    icon: ImageVector, isSelected: Boolean,
    modifier: Modifier = Modifier, onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier.clickable { onClick() }.padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = if (isSelected) colors.primary else colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun VDivider() {
    Box(Modifier.width(1.dp).height(24.dp).background(MaterialTheme.colorScheme.outline))
}

// ── Copies stepper ────────────────────────────────────────────────────────────

@Composable
private fun CopiesStepper(
    value: Int, onValueChange: (Int) -> Unit, modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    var textValue by remember(value) { mutableStateOf(value.toString()) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, colors.outline, RoundedCornerShape(10.dp))
            .background(colors.surface),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Minus button
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .clickable { onValueChange((value - 1).coerceAtLeast(1)) }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Remove, "Decrease", tint = colors.onSurface, modifier = Modifier.size(22.dp))
        }

        Box(Modifier.fillMaxHeight().width(1.dp).background(colors.outline))

        // Inline number input
        OutlinedTextField(
            value = textValue,
            onValueChange = { input: String ->
                val filtered = input.filter { c -> c.isDigit() }
                textValue = filtered
                val parsed = filtered.toIntOrNull() ?: 1
                onValueChange(parsed.coerceAtLeast(1))
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(0.dp),
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor      = Color.Transparent,
                unfocusedBorderColor    = Color.Transparent,
                cursorColor             = colors.primary,
                focusedTextColor        = colors.onSurface,
                unfocusedTextColor      = colors.onSurface,
                focusedContainerColor   = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            )
        )

        Box(Modifier.fillMaxHeight().width(1.dp).background(colors.outline))

        // Plus button
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .clickable { onValueChange(value + 1) }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, "Increase", tint = colors.onSurface, modifier = Modifier.size(22.dp))
        }
    }
}

// ── Font size dropdown ────────────────────────────────────────────────────────

@Composable
private fun FontSizeDropdown(
    value: Int, onValueChange: (Int) -> Unit, modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceVariant)
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${value}px", color = colors.onSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
            modifier = Modifier.background(colors.surface)) {
            FontSizes.forEach { size ->
                DropdownMenuItem(
                    text    = { Text("${size}px", color = colors.onSurface) },
                    onClick = { onValueChange(size); expanded = false }
                )
            }
        }
    }
}

// ── Font family dropdown ──────────────────────────────────────────────────────

@Composable
private fun FontFamilyDropdown(
    value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceVariant)
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(value, color = colors.onSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
            modifier = Modifier.background(colors.surface)) {
            FontFamilies.forEach { font ->
                DropdownMenuItem(
                    text    = { Text(font, color = colors.onSurface) },
                    onClick = { onValueChange(font); expanded = false }
                )
            }
        }
    }
}

// ── Text style row (B / I / U) ────────────────────────────────────────────────

@Composable
private fun TextStyleRow(
    isBold: Boolean, isItalic: Boolean, isUnderline: Boolean,
    onBoldToggle: () -> Unit, onItalicToggle: () -> Unit, onUnderlineToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StyleToggle("B", isBold,      Modifier.weight(1f), bold      = true, onClick = onBoldToggle)
        VDivider()
        StyleToggle("I", isItalic,    Modifier.weight(1f), italic    = true, onClick = onItalicToggle)
        VDivider()
        StyleToggle("U", isUnderline, Modifier.weight(1f), underline = true, onClick = onUnderlineToggle)
    }
}

@Composable
private fun StyleToggle(
    label: String, isSelected: Boolean,
    modifier: Modifier = Modifier,
    bold: Boolean = false, italic: Boolean = false, underline: Boolean = false,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier.clickable { onClick() }.padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color          = if (isSelected) colors.primary else colors.onSurfaceVariant,
            fontSize       = 16.sp,
            fontWeight     = if (bold)      FontWeight.Bold      else FontWeight.Normal,
            fontStyle      = if (italic)    FontStyle.Italic     else FontStyle.Normal,
            textDecoration = if (underline) TextDecoration.Underline else TextDecoration.None
        )
    }
}

// ── Padding controls ──────────────────────────────────────────────────────────

@Composable
private fun PaddingControls(
    paddingTop: Int, paddingBottom: Int, paddingLeft: Int, paddingRight: Int,
    onTopChange: (Int) -> Unit, onBottomChange: (Int) -> Unit,
    onLeftChange: (Int) -> Unit, onRightChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MarginCell("Top",    paddingTop,    onTopChange,    Modifier.weight(1f))
            MarginCell("Bottom", paddingBottom, onBottomChange, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MarginCell("Left",   paddingLeft,   onLeftChange,   Modifier.weight(1f))
            MarginCell("Right",  paddingRight,  onRightChange,  Modifier.weight(1f))
        }
    }
}

@Composable
private fun MarginCell(
    label: String, value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    var textValue by remember(value) { mutableStateOf(value.toString()) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Label
        Text(label, color = colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium)

        // Input row: − [field] + as one unified element
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, colors.outline, RoundedCornerShape(10.dp))
                .background(colors.surface),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Minus button
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable { onValueChange((value - 1).coerceAtLeast(0)) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Remove, "Decrease", tint = colors.onSurface, modifier = Modifier.size(20.dp))
            }

            // Left divider
            Box(Modifier.fillMaxHeight().width(1.dp).background(colors.outline))

            // Inline number input (no border — the outer row provides it)
            OutlinedTextField(
                value = textValue,
                onValueChange = { input: String ->
                    val filtered = input.filter { c -> c.isDigit() }
                    textValue = filtered
                    val parsed = filtered.toIntOrNull() ?: 0
                    onValueChange(parsed.coerceAtLeast(0))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(0.dp),
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor      = Color.Transparent,
                    unfocusedBorderColor    = Color.Transparent,
                    cursorColor             = colors.primary,
                    focusedTextColor        = colors.onSurface,
                    unfocusedTextColor      = colors.onSurface,
                    focusedContainerColor   = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
                suffix = { Text("mm", color = colors.onSurfaceVariant, fontSize = 11.sp) }
            )

            // Right divider
            Box(Modifier.fillMaxHeight().width(1.dp).background(colors.outline))

            // Plus button
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable { onValueChange(value + 1) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, "Increase", tint = colors.onSurface, modifier = Modifier.size(20.dp))
            }
        }
    }
}

