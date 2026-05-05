package soft.naitlee.catprinter.nativeapp

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private enum class PrintKind(val label: String) {
        IMAGE("Image"),
        TEXT("Text"),
        PATTERN("Pattern"),
    }

    private data class Palette(
        val background: Int,
        val surface: Int,
        val text: Int,
        val mutedText: Int,
        val accent: Int,
        val previewBackground: Int,
        val buttonBackground: Int,
        val buttonText: Int,
    )

    private data class PrinterListItem(
        val printer: CatPrinterBleClient.ScannedPrinter,
        val detected: Boolean,
    )

    private val bleClient by lazy { CatPrinterBleClient(this) }
    private val settings by lazy { PrinterSettings(this) }
    private val worker = Executors.newSingleThreadExecutor()
    private val previewHandler = Handler(Looper.getMainLooper())
    private val previewGeneration = AtomicInteger(0)
    private val devices = mutableListOf<CatPrinterBleClient.ScannedPrinter>()
    private val deviceItems = mutableListOf<PrinterListItem>()
    private val lastScannedPrinters = mutableListOf<CatPrinterBleClient.ScannedPrinter>()
    private val palette by lazy { currentPalette() }

    private var selectedDevice: CatPrinterBleClient.ScannedPrinter? = null
    private var selectedImage: Uri? = null
    private var connectedModel: PrinterModel = PrinterModel.fallback
    private var latestPreview: PrintBitmap? = null
    private var baseLeft = 0
    private var baseTop = 0
    private var baseRight = 0
    private var baseBottom = 0
    private var restoringSettings = false
    private var scanning = false
    private var connecting = false

    private lateinit var statusView: TextView
    private lateinit var deviceSpinner: Spinner
    private lateinit var progressBar: ProgressBar
    private lateinit var scanButton: Button
    private lateinit var connectButton: Button
    private lateinit var scanButtonSpinner: ProgressBar
    private lateinit var connectButtonSpinner: ProgressBar
    private lateinit var printKindSpinner: Spinner
    private lateinit var ditherSpinner: Spinner
    private lateinit var dryRunBox: CheckBox
    private lateinit var unknownBox: CheckBox
    private lateinit var rotateBox: CheckBox
    private lateinit var flipHBox: CheckBox
    private lateinit var flipVBox: CheckBox
    private lateinit var energySeek: SeekBar
    private lateinit var brightnessSeek: SeekBar
    private lateinit var bayerRangeLabel: TextView
    private lateinit var bayerRangeSeek: SeekBar
    private lateinit var textInput: EditText
    private lateinit var imageStatus: TextView
    private lateinit var preview: ImageView
    private lateinit var imageSection: LinearLayout
    private lateinit var textSection: LinearLayout
    private lateinit var patternSection: LinearLayout
    private lateinit var printButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureSystemBars()
        setContentView(buildUi())
        refreshPrinterList(emptyList(), settings.lastSelectedPrinterAddress)
        requestRuntimePermissions()
        handleIncomingIntent(intent)
        updateVisiblePrintSection()
        schedulePreviewUpdate()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onPause() {
        savePrintSettings()
        super.onPause()
    }

    override fun onDestroy() {
        savePrintSettings()
        worker.shutdownNow()
        latestPreview?.preview?.recycle()
        latestPreview?.originalPreview?.recycle()
        bleClient.disconnect()
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMAGE_PICK_REQUEST && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            contentResolver.takePersistableUriPermissionIfPossible(uri, data.flags)
            selectedImage = uri
            imageStatus.text = "Image selected"
            printKindSpinner.setSelection(PrintKind.IMAGE.ordinal)
            schedulePreviewUpdate(force = true)
        }
    }

    private fun buildUi(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(palette.background)
            clipToPadding = false
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(28))
            setBackgroundColor(palette.background)
        }
        baseLeft = root.paddingLeft
        baseTop = root.paddingTop
        baseRight = root.paddingRight
        baseBottom = root.paddingBottom
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bottomInset = if (Build.VERSION.SDK_INT >= 30) {
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetBottom
            }
            view.setPadding(baseLeft, baseTop, baseRight, baseBottom + bottomInset)
            insets
        }
        scroll.addView(root)

        root.addView(title("Cat Printer", 26f).apply { setPadding(0, dp(2), 0, dp(12)) })

        statusView = body("Ready")
        root.addView(statusView)
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
        }
        root.addView(progressBar, matchWrap())

        unknownBox = checkBox("Show unknown BLE devices", settings.showUnknownDevices) {
            settings.showUnknownDevices = unknownBox.isChecked
        }
        root.addView(unknownBox)
        scanButton = button("Scan") { scan() }
        connectButton = button("Connect") { connectSelected() }
        scanButtonSpinner = smallSpinner()
        connectButtonSpinner = smallSpinner()
        root.addView(row(
            buttonWithSpinner(scanButton, scanButtonSpinner),
            buttonWithSpinner(connectButton, connectButtonSpinner),
        ))

        deviceSpinner = spinner(arrayOf("No saved printers")) {
            selectedDevice = deviceItems.getOrNull(deviceSpinner.selectedItemPosition)?.printer
            selectedDevice?.let(settings::rememberSelectedPrinter)
            updateConnectButtonState()
        }
        root.addView(deviceSpinner, matchWrap())

        root.addView(sectionLabel("Print Type"))
        printKindSpinner = spinner(PrintKind.entries.map { it.label }.toTypedArray()) {
            updateVisiblePrintSection()
            savePrintSettings()
            schedulePreviewUpdate()
        }
        restoringSettings = true
        printKindSpinner.setSelection(settings.printKind.coerceIn(0, PrintKind.entries.lastIndex))
        restoringSettings = false
        root.addView(printKindSpinner, matchWrap())

        imageSection = LinearLayout(this).vertical()
        imageStatus = body("No image selected", muted = true)
        imageSection.addView(imageStatus)
        imageSection.addView(row(
            button("Select Image") { pickImage() },
            button("Refresh Preview") { schedulePreviewUpdate(force = true) },
        ))
        root.addView(imageSection)

        textSection = LinearLayout(this).vertical()
        textInput = EditText(this).apply {
            hint = "Text to print"
            minLines = 4
            gravity = Gravity.TOP or Gravity.START
            setSingleLine(false)
            setText(settings.text ?: "Hello from Cat Printer")
            setTextColor(palette.text)
            setHintTextColor(palette.mutedText)
            setBackgroundColor(palette.surface)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = schedulePreviewUpdate()
                override fun afterTextChanged(s: Editable?) {
                    if (!restoringSettings) settings.text = s?.toString()
                }
            })
        }
        textSection.addView(textInput, matchWrap())
        root.addView(textSection)

        patternSection = LinearLayout(this).vertical()
        patternSection.addView(body("Built-in checkerboard test pattern", muted = true))
        root.addView(patternSection)

        preview = ImageView(this).apply {
            setBackgroundColor(palette.previewBackground)
            adjustViewBounds = true
            minimumHeight = dp(160)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setOnTouchListener { _, event ->
                val bitmap = latestPreview ?: return@setOnTouchListener false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        setImageDrawable(nearestDrawable(bitmap.originalPreview))
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        setImageBitmap(bitmap.preview)
                        true
                    }
                    else -> true
                }
            }
        }
        root.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260)).apply { topMargin = dp(12) })

        root.addView(sectionLabel("Options"))
        dryRunBox = checkBox("Dry run: send blank image data", settings.dryRun) {
            savePrintSettings()
            schedulePreviewUpdate()
        }
        rotateBox = checkBox("Rotate image 90 degrees", settings.rotateImage) {
            savePrintSettings()
            schedulePreviewUpdate()
        }
        flipHBox = checkBox("Flip horizontally", settings.flipHorizontal) {
            savePrintSettings()
            schedulePreviewUpdate()
        }
        flipVBox = checkBox("Flip vertically", settings.flipVertical) {
            savePrintSettings()
            schedulePreviewUpdate()
        }
        listOf(dryRunBox, rotateBox, flipHBox, flipVBox).forEach(root::addView)

        root.addView(label("Dithering"))
        ditherSpinner = spinner(ThermalBitmap.ditherLabels) {
            updateDitherControls()
            savePrintSettings()
            schedulePreviewUpdate()
        }
        restoringSettings = true
        ditherSpinner.setSelection(settings.ditherMode.coerceIn(0, DitherMode.entries.lastIndex))
        restoringSettings = false
        root.addView(ditherSpinner, matchWrap())

        root.addView(label("Energy"))
        energySeek = seekBar(100, settings.energy.coerceIn(0, 100)) {
            savePrintSettings()
            schedulePreviewUpdate()
        }
        root.addView(energySeek, matchWrap())

        root.addView(label("Brightness"))
        brightnessSeek = seekBar(100, settings.brightness.coerceIn(0, 100)) {
            savePrintSettings()
            schedulePreviewUpdate()
        }
        root.addView(brightnessSeek, matchWrap())

        bayerRangeLabel = label("Bayer range")
        root.addView(bayerRangeLabel)
        bayerRangeSeek = seekBar(100, settings.bayerRange.coerceIn(0, 100)) {
            savePrintSettings()
            schedulePreviewUpdate()
        }
        root.addView(bayerRangeSeek, matchWrap())

        printButton = button("Print Image") { printCurrentSelection() }
        root.addView(printButton, matchWrap().apply { topMargin = dp(18) })
        root.addView(button("Open Bluetooth Settings") {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }, matchWrap())
        root.addView(button("View Error Log") { showLog() }, matchWrap())

        updateDitherControls()

        return scroll
    }

    private fun scan() {
        if (!ensurePermissions()) return
        scanning = true
        updateScanButtonState()
        setBusy(true, "Scanning for printers...")
        bleClient.scan(4_000, unknownBox.isChecked) { result ->
            scanning = false
            updateScanButtonState()
            setBusy(false, result.fold(
                onSuccess = { list -> "Found ${list.size} device(s)" },
                onFailure = { error ->
                    val message = error.message ?: "Scan failed"
                    showToast(message)
                    message
                },
            ))
            result.onSuccess { list ->
                lastScannedPrinters.clear()
                lastScannedPrinters.addAll(list)
                refreshPrinterList(list, settings.lastSelectedPrinterAddress)
                if (list.isEmpty()) AppLog.add("Scan finished with no devices")
            }
        }
    }

    private fun connectSelected() {
        if (!ensurePermissions()) return
        val target = selectedDevice ?: run {
            status("Scan and select a printer first")
            return
        }
        connecting = true
        updateConnectButtonState()
        setBusy(true, "Connecting to ${target.name}...")
        bleClient.connect(target.device) { result ->
            connecting = false
            setBusy(false, result.fold(
                onSuccess = {
                    connectedModel = PrinterModel.fromName(target.name)
                    settings.rememberSelectedPrinter(target)
                    schedulePreviewUpdate(force = true)
                    AppLog.add("Connected to ${target.name} ${target.address}")
                    "Connected to ${target.name}"
                },
                onFailure = { error ->
                    val message = error.message ?: "Connection failed"
                    showToast(message)
                    message
                },
            ))
            updateConnectButtonState()
        }
    }

    private fun printCurrentSelection() {
        val label = selectedPrintKind().label.lowercase()
        if (!bleClient.isConnected()) {
            val target = selectedDevice
            if (target == null) {
                showError("Connect to a printer first")
                return
            }
            showToast("Connecting to ${target.name} before printing")
            connecting = true
            updateConnectButtonState()
            setBusy(true, "Connecting to ${target.name}...")
            bleClient.connect(target.device) { result ->
                connecting = false
                updateConnectButtonState()
                result.fold(
                    onSuccess = {
                        connectedModel = PrinterModel.fromName(target.name)
                        settings.rememberSelectedPrinter(target)
                        setBusy(false, "Connected to ${target.name}")
                        showToast("Connected. Printing $label...")
                        startPrintJob(label)
                    },
                    onFailure = { error -> showError(error.message ?: "Connection failed") },
                )
            }
            return
        }
        startPrintJob(label)
    }

    private fun startPrintJob(label: String) {
        worker.execute { runPrintJob(label) { createCurrentPrintBitmap() } }
    }

    private fun createCurrentPrintBitmap(): PrintBitmap {
        return when (selectedPrintKind()) {
            PrintKind.IMAGE -> {
                val imageUri = selectedImage ?: error("Select an image first")
                contentResolver.openInputStream(imageUri).use { input ->
                    ThermalBitmap.fromImageStream(
                        input ?: error("Could not open selected image"),
                        connectedModel.paperWidth,
                        brightnessSeek.progress,
                        energySeek.progress,
                        selectedDitherMode(),
                        bayerRangeSeek.progress,
                        rotateBox.isChecked,
                        flipHBox.isChecked,
                        flipVBox.isChecked,
                    )
                }
            }
            PrintKind.TEXT -> ThermalBitmap.fromText(
                textInput.text?.toString().orEmpty(),
                connectedModel.paperWidth,
                28f,
                brightnessSeek.progress,
                energySeek.progress,
                selectedDitherMode(),
                bayerRangeSeek.progress,
            )
            PrintKind.PATTERN -> ThermalBitmap.fromPacked(
                connectedModel.paperWidth,
                160,
                CatPrinterProtocol.testPattern(connectedModel.paperWidth),
                energySeek.progress,
            )
        }
    }

    private fun runPrintJob(label: String, bitmapFactory: () -> PrintBitmap) {
        try {
            runOnUiThread { setBusy(true, "Preparing $label...") }
            val bitmap = bitmapFactory()
            val energy = ((energySeek.progress / 100f) * 0xffff).roundToInt().coerceIn(0, 0xffff)
            val speed = 4 * (3 + 5)
            val commands = CatPrinterProtocol.printJob(
                connectedModel,
                bitmap.payload,
                dryRunBox.isChecked,
                energy,
                speed,
            ) { line, total ->
                val progress = ((line.toFloat() / total.coerceAtLeast(1)) * 100).roundToInt()
                runOnUiThread {
                    progressBar.progress = progress
                    statusView.text = "Printing $label: $line / $total lines"
                }
            }
            bleClient.print(commands) { message -> runOnUiThread { statusView.text = message } }
            runOnUiThread {
                val message = "Finished printing $label (${bitmap.height} lines)"
                selectedDevice?.let { printer ->
                    settings.rememberSuccessfulPrinter(printer)
                    refreshPrinterList(lastScannedPrinters, printer.address)
                }
                AppLog.add(message)
                setBusy(false, message)
                updateConnectButtonState()
            }
        } catch (error: Throwable) {
            runOnUiThread {
                showError(error.message ?: "Print failed")
                updateConnectButtonState()
            }
        }
    }

    private fun schedulePreviewUpdate(force: Boolean = false) {
        if (!force) previewHandler.removeCallbacksAndMessages(PREVIEW_TOKEN)
        val generation = previewGeneration.incrementAndGet()
        val task = Runnable { renderPreview(generation) }
        if (force) previewHandler.post(task) else previewHandler.postDelayed(task, PREVIEW_TOKEN, 180L)
    }

    private fun renderPreview(generation: Int) {
        val kind = selectedPrintKind()
        if (kind == PrintKind.IMAGE && selectedImage == null) {
            latestPreview = null
            preview.setImageDrawable(null)
            imageStatus.text = "No image selected"
            status("Select an image to preview")
            return
        }
        worker.execute {
            try {
                val bitmap = createCurrentPrintBitmap()
                if (previewGeneration.get() != generation) return@execute
                runOnUiThread {
                    latestPreview?.preview?.recycle()
                    latestPreview?.originalPreview?.recycle()
                    latestPreview = bitmap
                    preview.setImageBitmap(bitmap.preview)
                    status("Preview ready: ${bitmap.width} x ${bitmap.height}")
                    if (kind == PrintKind.IMAGE) imageStatus.text = "Image preview reflects current print options"
                }
            } catch (error: Throwable) {
                if (previewGeneration.get() != generation) return@execute
                runOnUiThread {
                    latestPreview?.preview?.recycle()
                    latestPreview?.originalPreview?.recycle()
                    latestPreview = null
                    preview.setImageDrawable(null)
                    status(error.message ?: "Preview failed")
                }
            }
        }
    }

    private fun updateVisiblePrintSection() {
        if (!::imageSection.isInitialized) return
        val kind = selectedPrintKind()
        setSectionVisible(imageSection, kind == PrintKind.IMAGE)
        setSectionVisible(textSection, kind == PrintKind.TEXT)
        setSectionVisible(patternSection, kind == PrintKind.PATTERN)
        if (::printButton.isInitialized) printButton.text = "Print ${kind.label}"
        if (::rotateBox.isInitialized) rotateBox.visibility = if (kind == PrintKind.IMAGE) View.VISIBLE else View.GONE
        if (::flipHBox.isInitialized) flipHBox.visibility = if (kind == PrintKind.IMAGE) View.VISIBLE else View.GONE
        if (::flipVBox.isInitialized) flipVBox.visibility = if (kind == PrintKind.IMAGE) View.VISIBLE else View.GONE
        updateDitherControls()
    }

    private fun updateDitherControls() {
        if (!::bayerRangeSeek.isInitialized) return
        val visibility = if (selectedDitherMode() == DitherMode.BAYER_4X4) View.VISIBLE else View.GONE
        bayerRangeLabel.visibility = visibility
        bayerRangeSeek.visibility = visibility
    }

    private fun setSectionVisible(section: View, visible: Boolean) {
        section.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun refreshPrinterList(scannedPrinters: List<CatPrinterBleClient.ScannedPrinter>, preferredAddress: String?) {
        val merged = linkedMapOf<String, PrinterListItem>()

        fun addPrinter(printer: CatPrinterBleClient.ScannedPrinter, detected: Boolean) {
            val key = printer.address.uppercase()
            val existing = merged[key]
            merged[key] = PrinterListItem(printer, detected || existing?.detected == true)
        }

        settings.rememberedPrinters.forEach { remembered ->
            bleClient.rememberedPrinter(remembered.name, remembered.address)?.let { addPrinter(it, detected = false) }
        }
        settings.lastSelectedPrinter?.let { remembered ->
            bleClient.rememberedPrinter(remembered.name, remembered.address)?.let { addPrinter(it, detected = false) }
        }
        scannedPrinters.forEach { addPrinter(it, detected = true) }

        deviceItems.clear()
        deviceItems.addAll(merged.values)
        devices.clear()
        devices.addAll(deviceItems.map { it.printer })

        val labels = if (deviceItems.isEmpty()) {
            listOf("No saved printers")
        } else {
            deviceItems.map { printerLabel(it.printer) }
        }
        deviceSpinner.adapter = printerAdapter(labels)

        if (deviceItems.isEmpty()) {
            selectedDevice = null
        } else {
            val selectedIndex = deviceItems.indexOfFirst { item ->
                preferredAddress != null && item.printer.address.equals(preferredAddress, ignoreCase = true)
            }.takeIf { it >= 0 } ?: 0
            selectedDevice = deviceItems[selectedIndex].printer
            deviceSpinner.setSelection(selectedIndex, false)
        }
        updateConnectButtonState()
    }

    private fun printerLabel(printer: CatPrinterBleClient.ScannedPrinter): String = "${printer.name}  ${printer.address}"

    private fun updateScanButtonState() {
        if (!::scanButton.isInitialized) return
        scanButton.text = if (scanning) "Scanning" else "Scan"
        scanButton.isEnabled = !scanning
        scanButtonSpinner.visibility = if (scanning) View.VISIBLE else View.GONE
    }

    private fun updateConnectButtonState() {
        if (!::connectButton.isInitialized) return
        val connected = bleClient.isConnected()
        connectButton.text = when {
            connecting -> "Connecting"
            connected -> "Connected"
            else -> "Connect"
        }
        connectButton.isEnabled = !connecting && selectedDevice != null
        connectButtonSpinner.visibility = if (connecting) View.VISIBLE else View.GONE
        setConnectButtonDot(if (connected) connectedColor() else disconnectedColor())
    }

    private fun setConnectButtonDot(color: Int) {
        val dot = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setSize(dp(10), dp(10))
        }
        connectButton.compoundDrawablePadding = dp(8)
        connectButton.setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null)
    }

    private fun detectedPrinterColor(): Int = if (isNightMode()) Color.rgb(101, 224, 150) else Color.rgb(24, 137, 73)

    private fun connectedColor(): Int = if (isNightMode()) Color.rgb(90, 224, 139) else Color.rgb(22, 150, 73)

    private fun disconnectedColor(): Int = if (isNightMode()) Color.rgb(255, 105, 105) else Color.rgb(204, 50, 50)

    private fun isNightMode(): Boolean = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun selectedPrintKind(): PrintKind = PrintKind.entries.getOrElse(printKindSpinner.selectedItemPosition) { PrintKind.IMAGE }

    private fun selectedDitherMode(): DitherMode = ThermalBitmap.ditherModeFromLabel(ditherSpinner.selectedItem?.toString().orEmpty())

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, IMAGE_PICK_REQUEST)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val uri = when (intent.action) {
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> incomingImageUri(intent)
            Intent.ACTION_VIEW -> intent.data
            else -> null
        } ?: return
        val mimeType = intent.type.orEmpty()
        if (mimeType.isNotEmpty() && !mimeType.startsWith("image/")) return
        try {
            contentResolver.takePersistableUriPermissionIfPossible(uri, intent.flags)
        } catch (_: Exception) {
        }
        selectedImage = uri
        imageStatus.text = "Shared image selected"
        printKindSpinner.setSelection(PrintKind.IMAGE.ordinal)
        schedulePreviewUpdate(force = true)
        AppLog.add("Received image intent: $uri")
    }

    private fun incomingImageUri(intent: Intent): Uri? {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { return it }
        @Suppress("DEPRECATION")
        intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.firstOrNull()?.let { return it }
        val clipData = intent.clipData ?: return null
        for (index in 0 until clipData.itemCount) {
            clipData.getItemAt(index).uri?.let { return it }
        }
        return null
    }

    private fun showLog() {
        val messageView = TextView(this).apply {
            text = AppLog.text()
            setTextColor(palette.text)
            setBackgroundColor(palette.surface)
            setPadding(dp(20), dp(12), dp(20), dp(12))
            textSize = 13f
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(palette.surface)
            addView(messageView)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Error Log")
            .setView(scroll)
            .setPositiveButton("OK", null)
            .show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(palette.surface))
        dialog.findViewById<TextView>(resources.getIdentifier("alertTitle", "id", "android"))?.setTextColor(palette.text)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(palette.accent)
    }

    private fun requestRuntimePermissions() {
        val permissions = CatPrinterBleClient.requiredPermissions().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (permissions.isNotEmpty()) requestPermissions(permissions.toTypedArray(), PERMISSIONS_REQUEST)
    }

    private fun ensurePermissions(): Boolean {
        if (CatPrinterBleClient.hasRequiredPermissions(this)) return true
        requestRuntimePermissions()
        status("Bluetooth permissions are required")
        return false
    }

    private fun setBusy(busy: Boolean, message: String) {
        progressBar.isIndeterminate = busy
        if (!busy) progressBar.isIndeterminate = false
        status(message)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showError(message: String) {
        AppLog.add(message)
        setBusy(false, message)
        showToast(message)
    }

    private fun status(message: String) {
        statusView.text = message
        AppLog.add(message)
    }

    private fun configureSystemBars() {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        window.statusBarColor = if (night) Color.rgb(18, 22, 24) else Color.WHITE
        window.navigationBarColor = if (night) Color.rgb(10, 12, 13) else Color.WHITE
        if (Build.VERSION.SDK_INT >= 23) {
            var flags = if (night) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            if (!night && Build.VERSION.SDK_INT >= 26) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.decorView.systemUiVisibility = flags
        }
    }

    private fun savePrintSettings() {
        if (restoringSettings) return
        if (::printKindSpinner.isInitialized) settings.printKind = printKindSpinner.selectedItemPosition
        if (::ditherSpinner.isInitialized) settings.ditherMode = ditherSpinner.selectedItemPosition
        if (::dryRunBox.isInitialized) settings.dryRun = dryRunBox.isChecked
        if (::unknownBox.isInitialized) settings.showUnknownDevices = unknownBox.isChecked
        if (::rotateBox.isInitialized) settings.rotateImage = rotateBox.isChecked
        if (::flipHBox.isInitialized) settings.flipHorizontal = flipHBox.isChecked
        if (::flipVBox.isInitialized) settings.flipVertical = flipVBox.isChecked
        if (::energySeek.isInitialized) settings.energy = energySeek.progress
        if (::brightnessSeek.isInitialized) settings.brightness = brightnessSeek.progress
        if (::bayerRangeSeek.isInitialized) settings.bayerRange = bayerRangeSeek.progress
        if (::textInput.isInitialized) settings.text = textInput.text?.toString()
    }

    private fun nearestDrawable(bitmap: android.graphics.Bitmap): BitmapDrawable = BitmapDrawable(resources, bitmap).apply {
        setFilterBitmap(false)
        setDither(false)
    }

    private fun currentPalette(): Palette {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return if (night) {
            Palette(
                background = Color.rgb(18, 22, 24),
                surface = Color.rgb(32, 38, 41),
                text = Color.rgb(234, 239, 239),
                mutedText = Color.rgb(166, 178, 181),
                accent = Color.rgb(92, 203, 184),
                previewBackground = Color.rgb(10, 12, 13),
                buttonBackground = Color.rgb(44, 52, 55),
                buttonText = Color.rgb(234, 239, 239),
            )
        } else {
            Palette(
                background = Color.rgb(247, 249, 248),
                surface = Color.WHITE,
                text = Color.rgb(20, 29, 32),
                mutedText = Color.rgb(78, 89, 93),
                accent = Color.rgb(0, 121, 107),
                previewBackground = Color.WHITE,
                buttonBackground = Color.rgb(229, 242, 239),
                buttonText = Color.rgb(20, 29, 32),
            )
        }
    }

    private fun title(text: String, size: Float): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(palette.text)
        typeface = Typeface.DEFAULT_BOLD
    }

    private fun body(text: String, muted: Boolean = false): TextView = TextView(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(if (muted) palette.mutedText else palette.text)
        setPadding(0, dp(8), 0, dp(8))
    }

    private fun sectionLabel(text: String): TextView = label(text).apply {
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(palette.accent)
    }

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(palette.mutedText)
        setPadding(0, dp(12), 0, 0)
    }

    private fun button(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        setTextColor(palette.buttonText)
        backgroundTintList = ColorStateList.valueOf(palette.buttonBackground)
        setOnClickListener { onClick() }
    }

    private fun smallSpinner(): ProgressBar = ProgressBar(this, null, android.R.attr.progressBarStyleSmall).apply {
        isIndeterminate = true
        visibility = View.GONE
        if (Build.VERSION.SDK_INT >= 21) indeterminateTintList = ColorStateList.valueOf(palette.accent)
    }

    private fun buttonWithSpinner(button: Button, spinner: ProgressBar): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(button, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(spinner, LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginStart = dp(6) })
    }

    private fun checkBox(text: String, checked: Boolean, onChange: () -> Unit): CheckBox = CheckBox(this).apply {
        this.text = text
        isChecked = checked
        setTextColor(palette.text)
        setOnCheckedChangeListener { _, _ -> onChange() }
    }

    private fun spinner(values: Array<String>, onSelected: () -> Unit): Spinner = Spinner(this).apply {
        adapter = themedAdapter(values.toList())
        backgroundTintList = ColorStateList.valueOf(palette.text)
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = onSelected()
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun themedAdapter(values: List<String>): ArrayAdapter<String> = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, values) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = super.getView(position, convertView, parent).also(::themeSpinnerText)
        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View = super.getDropDownView(position, convertView, parent).also(::themeSpinnerText)
    }

    private fun printerAdapter(values: List<String>): ArrayAdapter<String> = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, values) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = super.getView(position, convertView, parent).also { themePrinterText(it, position) }
        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View = super.getDropDownView(position, convertView, parent).also { themePrinterText(it, position) }
    }

    private fun themeSpinnerText(view: View) {
        (view as? TextView)?.apply {
            setTextColor(palette.text)
            setBackgroundColor(palette.surface)
        }
    }

    private fun themePrinterText(view: View, position: Int) {
        (view as? TextView)?.apply {
            val detected = deviceItems.getOrNull(position)?.detected == true
            setTextColor(if (detected) detectedPrinterColor() else palette.text)
            setBackgroundColor(palette.surface)
        }
    }

    private fun seekBar(max: Int, progress: Int, onChange: () -> Unit): SeekBar = SeekBar(this).apply {
        this.max = max
        this.progress = progress
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) onChange()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = onChange()
        })
    }

    private fun row(vararg views: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEach { child -> addView(child, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }) }
    }

    private fun LinearLayout.vertical(): LinearLayout = apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(6), 0, dp(6))
    }

    private fun matchWrap(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun android.content.ContentResolver.takePersistableUriPermissionIfPossible(uri: Uri, flags: Int) {
        try {
            takePersistableUriPermission(uri, flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val IMAGE_PICK_REQUEST = 7041
        private const val PERMISSIONS_REQUEST = 7042
        private val PREVIEW_TOKEN = Any()
    }
}
