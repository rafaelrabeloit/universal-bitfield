package io.github.rafaelrabeloit.bitfield

/**
 * The result of parsing a byte array against a [BitFieldSchema].
 *
 * Contains one [ParsedEntry] for every field definition in the schema, in
 * declaration order.
 *
 * @property schema The schema that was used for parsing.
 * @property rawBytes The original byte array that was parsed.
 * @property entries All parsed field entries, in schema declaration order.
 */
class ParseResult(
    val schema: BitFieldSchema,
    val rawBytes: ByteArray,
    val entries: List<ParsedEntry>,
) {
    /**
     * Looks up the parsed entry for a specific field within a specific byte.
     *
     * @param byteIndex The 1-based byte index.
     * @param field The [FieldDefinition] to look up (matched by identity).
     * @return The matching [ParsedEntry].
     * @throws NoSuchElementException if no entry matches.
     */
    fun entryFor(byteIndex: Int, field: FieldDefinition): ParsedEntry {
        return entries.first { it.byteIndex == byteIndex && it.field === field }
    }
}
