package soft.naitlee.catprinter.nativeapp

data class PrinterModel(
    val prefix: String,
    val paperWidth: Int = 384,
    val isNewKind: Boolean = false,
    val problemFeeding: Boolean = false,
) {
    val lineBytes: Int = paperWidth / 8

    companion object {
        private val models = listOf(
            PrinterModel("_ZZ00"),
            PrinterModel("GB01"),
            PrinterModel("GB02"),
            PrinterModel("GB03", isNewKind = true),
            PrinterModel("GT01"),
            PrinterModel("MX05", problemFeeding = true),
            PrinterModel("MX06", problemFeeding = true),
            PrinterModel("MX08", problemFeeding = true),
            PrinterModel("MX09", problemFeeding = true),
            PrinterModel("MX10", problemFeeding = true),
            PrinterModel("YT01"),
            PrinterModel("MX11"),
            PrinterModel("SC03h"),
            PrinterModel("MXTP"),
            PrinterModel("X5"),
        )

        val fallback: PrinterModel = models.first { it.prefix == "_ZZ00" }
        val supportedPrefixes: List<String> = models.map { it.prefix }.filter { it != "_ZZ00" }

        fun isSupportedName(name: String?): Boolean =
            !name.isNullOrBlank() && supportedPrefixes.any { name.startsWith(it) }

        fun fromName(name: String?): PrinterModel =
            models
                .filter { it.prefix != "_ZZ00" && !name.isNullOrBlank() && name.startsWith(it.prefix) }
                .maxByOrNull { it.prefix.length }
                ?: fallback
    }
}
