package soft.naitlee.catprinter.nativeapp

import android.content.Context

class PrinterSettings(context: Context) {
    private val preferences = context.getSharedPreferences("cat_printer_native", Context.MODE_PRIVATE)

    var lastPrinterName: String?
        get() = preferences.getString(KEY_LAST_PRINTER_NAME, null)
        set(value) = preferences.edit().putString(KEY_LAST_PRINTER_NAME, value).apply()

    var lastPrinterAddress: String?
        get() = preferences.getString(KEY_LAST_PRINTER_ADDRESS, null)
        set(value) = preferences.edit().putString(KEY_LAST_PRINTER_ADDRESS, value).apply()

    var autoPrintSharedImages: Boolean
        get() = preferences.getBoolean(KEY_AUTO_PRINT_SHARED_IMAGES, false)
        set(value) = preferences.edit().putBoolean(KEY_AUTO_PRINT_SHARED_IMAGES, value).apply()

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

    fun rememberPrinter(printer: CatPrinterBleClient.ScannedPrinter) {
        preferences.edit()
            .putString(KEY_LAST_PRINTER_NAME, printer.name)
            .putString(KEY_LAST_PRINTER_ADDRESS, printer.address)
            .apply()
    }

    companion object {
        private const val KEY_LAST_PRINTER_NAME = "last_printer_name"
        private const val KEY_LAST_PRINTER_ADDRESS = "last_printer_address"
        private const val KEY_AUTO_PRINT_SHARED_IMAGES = "auto_print_shared_images"
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

        const val EXTRA_PRINT_NOW = "soft.naitlee.catprinter.nativeapp.extra.PRINT_NOW"
    }
}
