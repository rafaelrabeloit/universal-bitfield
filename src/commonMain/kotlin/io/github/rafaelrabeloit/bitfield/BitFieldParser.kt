package io.github.rafaelrabeloit.bitfield

/**
 * Parse engine that walks a [BitFieldSchema] against raw bytes.
 *
 * For each field in the schema, the parser extracts the relevant bits, resolves
 * a human-readable label, and invokes any registered callbacks. Use
 * [BitFieldSchema.parse] for convenient access.
 */
object BitFieldParser {

    /**
     * Parses [data] according to [schema] and returns a [ParseResult].
     *
     * Callbacks on [SingleBitFlag] and [MultiBitEnum] fields are invoked during
     * this call, in schema declaration order.
     *
     * @param schema The bitfield schema to parse against.
     * @param data The raw bytes to parse.
     * @return A [ParseResult] containing one [ParsedEntry] per field definition.
     * @throws IllegalArgumentException if [data] size does not match [schema.expectedBytes][BitFieldSchema.expectedBytes].
     */
    fun parse(schema: BitFieldSchema, data: ByteArray): ParseResult {
        require(data.size == schema.expectedBytes) {
            "Expected ${schema.expectedBytes} bytes but got ${data.size}"
        }

        val entries = mutableListOf<ParsedEntry>()

        for (byteDef in schema.bytes) {
            val byteValue = data[byteDef.index - 1].toInt() and 0xFF

            for (field in byteDef.fields) {
                val entry = parseField(byteDef.index, byteValue, field)
                entries.add(entry)
            }
        }

        return ParseResult(schema, data, entries)
    }

    private fun parseField(byteIndex: Int, byteValue: Int, field: FieldDefinition): ParsedEntry {
        return when (field) {
            is SingleBitFlag -> parseSingleBit(byteIndex, byteValue, field)
            is MultiBitEnum -> parseMultiBitEnum(byteIndex, byteValue, field)
            is RfuField -> parseRfu(byteIndex, byteValue, field)
        }
    }

    private fun parseSingleBit(byteIndex: Int, byteValue: Int, field: SingleBitFlag): ParsedEntry {
        val bitValue = extractBit(byteValue, field.bit)
        val isSet = bitValue == 1

        if (isSet) {
            field.onSet?.invoke()
        } else {
            field.onUnset?.invoke()
        }

        val label = if (isSet) {
            field.setMeaning
        } else {
            field.unsetMeaning ?: field.setMeaning
        }

        return ParsedEntry(byteIndex, field, bitValue, label)
    }

    private fun parseMultiBitEnum(byteIndex: Int, byteValue: Int, field: MultiBitEnum): ParsedEntry {
        val rawBits = extractBits(byteValue, field.bits)

        val enumValue = field.values[rawBits]
        enumValue?.onMatch?.invoke()

        val label = enumValue?.label ?: "Unknown ($rawBits)"

        return ParsedEntry(byteIndex, field, rawBits, label)
    }

    private fun parseRfu(byteIndex: Int, byteValue: Int, field: RfuField): ParsedEntry {
        val rawBits = extractBits(byteValue, field.bits)
        return ParsedEntry(byteIndex, field, rawBits, "RFU")
    }

    private fun extractBit(byteValue: Int, bit: Int): Int {
        val shift = 7 - bit
        return (byteValue shr shift) and 1
    }

    private fun extractBits(byteValue: Int, bits: IntRange): Int {
        val width = bits.last - bits.first + 1
        val shift = 7 - bits.last
        val mask = (1 shl width) - 1
        return (byteValue shr shift) and mask
    }
}
