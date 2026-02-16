package io.github.rafaelrabeloit.bitfield

/**
 * An immutable schema defining the structure of a bitfield.
 *
 * A [BitFieldSchema] describes how to interpret a fixed-length byte array by
 * specifying what each bit or group of bits means. Use the [dsl.bitfield] DSL
 * function to construct instances.
 *
 * @property name Human-readable name for this schema (e.g., "Application Interchange Profile").
 * @property expectedBytes The exact number of bytes this schema expects to parse.
 * @property bytes The per-byte field definitions, ordered by byte index.
 */
class BitFieldSchema(
    val name: String,
    val expectedBytes: Int,
    val bytes: List<ByteSchema>,
) {
    /**
     * Parses a byte array against this schema.
     *
     * Callbacks registered in the schema's field definitions are invoked during parsing.
     *
     * @param data The raw bytes to parse. Must have exactly [expectedBytes] elements.
     * @return A [ParseResult] containing the resolved value for every field.
     * @throws IllegalArgumentException if [data] size does not match [expectedBytes].
     */
    fun parse(data: ByteArray): ParseResult = BitFieldParser.parse(this, data)
}
