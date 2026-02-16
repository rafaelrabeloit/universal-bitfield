package io.github.rafaelrabeloit.bitfield

/**
 * A single resolved field from a [ParseResult].
 *
 * @property byteIndex The 1-based index of the byte this entry belongs to.
 * @property field The [FieldDefinition] that produced this entry.
 * @property rawBits The extracted bit value as an integer (0 or 1 for single-bit fields,
 *   0..2^N-1 for N-bit enum/RFU fields).
 * @property resolvedLabel The human-readable label resolved for this field's value.
 */
class ParsedEntry(
    val byteIndex: Int,
    val field: FieldDefinition,
    val rawBits: Int,
    val resolvedLabel: String,
)
