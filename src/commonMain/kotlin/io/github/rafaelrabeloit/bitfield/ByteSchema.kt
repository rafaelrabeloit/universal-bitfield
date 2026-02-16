package io.github.rafaelrabeloit.bitfield

/**
 * Defines the field layout for a single byte within a [BitFieldSchema].
 *
 * Every bit position (0–7) must be covered by exactly one field. Bit 0 is the
 * MSB (b8) and bit 7 is the LSB (b1), matching EMV convention.
 *
 * @property index The 1-based byte position within the schema.
 * @property label Human-readable label for this byte (e.g., "Byte 1").
 * @property fields The field definitions covering all 8 bits of this byte.
 */
class ByteSchema(
    val index: Int,
    val label: String,
    val fields: List<FieldDefinition>,
)
