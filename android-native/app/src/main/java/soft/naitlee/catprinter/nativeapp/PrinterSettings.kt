package soft.naitlee.catprinter.nativeapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class PrinterSettings(context: Context) {
    data class RememberedPrinter(val name: String, val address: String)

    private val preferences = context.getSharedPreferences("cat_printer_native", Context.MODE_PRIVATE)

    var lastPrinterName: String?
        get() = preferences.getString(KEY_LAST_PRINTER_NAME, null)
        set(value) = preferences.edit().putString(KEY_LAST_PRINTER_NAME, value).apply()

    var lastPrinterAddress: String?
        get() = preferences.getString(KEY_LAST_PRINTER_ADDRESS, null)
        set(value) = preferences.edit().putString(KEY_LAST_PRINTER_ADDRESS, value).apply()

    var lastSelectedPrinterAddress: String?
        get() = preferences.getString(KEY_LAST_SELECTED_PRINTER_ADDRESS, null) ?: lastPrinterAddress
        set(value) = preferences.edit().putString(KEY_LAST_SELECTED_PRINTER_ADDRESS, value).apply()

    val lastSelectedPrinter: RememberedPrinter?
        get() {
            val address = lastSelectedPrinterAddress?.takeUnless { it.isBlank() } ?: return null
            val name = rememberedPrinters.firstOrNull { it.address.equals(address, ignoreCase = true) }?.name
                ?: lastPrinterName
                ?: address
            return RememberedPrinter(name, address)
        }

    val rememberedPrinters: List<RememberedPrinter>
        get() {
            val printers = linkedMapOf<String, RememberedPrinter>()
            preferences.getString(KEY_SUCCESSFUL_PRINTERS, null)?.let { encoded ->
                try {
                    val array = JSONArray(encoded)
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val name = item.optString(KEY_JSON_NAME).takeUnless { it.isBlank() } ?: continue
                        val address = item.optString(KEY_JSON_ADDRESS).takeUnless { it.isBlank() } ?: continue
                        printers[address.uppercase()] = RememberedPrinter(name, address)
                    }
                } catch (_: Exception) {
                }
            }
            val legacyName = lastPrinterName
            val legacyAddress = lastPrinterAddress
            if (!legacyName.isNullOrBlank() && !legacyAddress.isNullOrBlank()) {
                val key = legacyAddress.uppercase()
                if (!printers.containsKey(key)) printers[key] = RememberedPrinter(legacyName, legacyAddress)
            }
            return printers.values.toList()
        }

    var printKind: Int
        get() = preferences.getInt(KEY_PRINT_KIND, 0)
        set(value) = preferences.edit().putInt(KEY_PRINT_KIND, value).apply()

    var ditherMode: Int
        get() = preferences.getInt(KEY_DITHER_MODE, 1)
        set(value) = preferences.edit().putInt(KEY_DITHER_MODE, value).apply()

    var dryRun: Boolean
        get() = preferences.getBoolean(KEY_DRY_RUN, false)
        set(value) = preferences.edit().putBoolean(KEY_DRY_RUN, value).apply()

    var showUnknownDevices: Boolean
        get() = preferences.getBoolean(KEY_SHOW_UNKNOWN_DEVICES, false)
        set(value) = preferences.edit().putBoolean(KEY_SHOW_UNKNOWN_DEVICES, value).apply()

    var rotateImage: Boolean
        get() = preferences.getBoolean(KEY_ROTATE_IMAGE, false)
        set(value) = preferences.edit().putBoolean(KEY_ROTATE_IMAGE, value).apply()

    var flipHorizontal: Boolean
        get() = preferences.getBoolean(KEY_FLIP_HORIZONTAL, false)
        set(value) = preferences.edit().putBoolean(KEY_FLIP_HORIZONTAL, value).apply()

    var flipVertical: Boolean
        get() = preferences.getBoolean(KEY_FLIP_VERTICAL, false)
        set(value) = preferences.edit().putBoolean(KEY_FLIP_VERTICAL, value).apply()

    var energy: Int
        get() = preferences.getInt(KEY_ENERGY, 64)
        set(value) = preferences.edit().putInt(KEY_ENERGY, value).apply()

    var brightness: Int
        get() = preferences.getInt(KEY_BRIGHTNESS, 50)
        set(value) = preferences.edit().putInt(KEY_BRIGHTNESS, value).apply()

    var bayerRange: Int
        get() = preferences.getInt(KEY_BAYER_RANGE, 50)
        set(value) = preferences.edit().putInt(KEY_BAYER_RANGE, value).apply()

    var text: String?
        get() = preferences.getString(KEY_TEXT, null)
        set(value) = preferences.edit().putString(KEY_TEXT, value).apply()

    fun rememberSelectedPrinter(printer: CatPrinterBleClient.ScannedPrinter) {
        preferences.edit()
            .putString(KEY_LAST_PRINTER_NAME, printer.name)
            .putString(KEY_LAST_PRINTER_ADDRESS, printer.address)
            .putString(KEY_LAST_SELECTED_PRINTER_ADDRESS, printer.address)
            .apply()
    }

    fun rememberSuccessfulPrinter(printer: CatPrinterBleClient.ScannedPrinter) {
        val printers = linkedMapOf<String, RememberedPrinter>()
        rememberedPrinters.forEach { printers[it.address.uppercase()] = it }
        printers[printer.address.uppercase()] = RememberedPrinter(printer.name, printer.address)

        val array = JSONArray()
        printers.values.forEach { remembered ->
            array.put(JSONObject().apply {
                put(KEY_JSON_NAME, remembered.name)
                put(KEY_JSON_ADDRESS, remembered.address)
            })
        }

        preferences.edit()
            .putString(KEY_LAST_PRINTER_NAME, printer.name)
            .putString(KEY_LAST_PRINTER_ADDRESS, printer.address)
            .putString(KEY_LAST_SELECTED_PRINTER_ADDRESS, printer.address)
            .putString(KEY_SUCCESSFUL_PRINTERS, array.toString())
            .apply()
    }

    companion object {
        private const val KEY_LAST_PRINTER_NAME = "last_printer_name"
        private const val KEY_LAST_PRINTER_ADDRESS = "last_printer_address"
        private const val KEY_LAST_SELECTED_PRINTER_ADDRESS = "last_selected_printer_address"
        private const val KEY_SUCCESSFUL_PRINTERS = "successful_printers"
        private const val KEY_PRINT_KIND = "print_kind"
        private const val KEY_DITHER_MODE = "dither_mode"
        private const val KEY_DRY_RUN = "dry_run"
        private const val KEY_SHOW_UNKNOWN_DEVICES = "show_unknown_devices"
        private const val KEY_ROTATE_IMAGE = "rotate_image"
        private const val KEY_FLIP_HORIZONTAL = "flip_horizontal"
        private const val KEY_FLIP_VERTICAL = "flip_vertical"
        private const val KEY_ENERGY = "energy"
        private const val KEY_BRIGHTNESS = "brightness"
        private const val KEY_BAYER_RANGE = "bayer_range"
        private const val KEY_TEXT = "text"
        private const val KEY_JSON_NAME = "name"
        private const val KEY_JSON_ADDRESS = "address"
    }
}
