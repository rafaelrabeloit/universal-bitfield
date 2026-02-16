package io.github.rafaelrabeloit.bitfield.dsl

import io.github.rafaelrabeloit.bitfield.ByteSchema
import io.github.rafaelrabeloit.bitfield.FieldDefinition
import io.github.rafaelrabeloit.bitfield.RfuField

/**
 * DSL builder for defining the fields within a single byte.
 *
 * This is the receiver for the [BitFieldSchemaBuilder.byte] block. All 8 bit
 * positions (0-7) must be covered by exactly one field definition.
 */
@BitFieldDslMarker
class ByteSchemaBuilder(
    private val index: Int,
    private val label: String,
) {
    private val fields = mutableListOf<FieldDefinition>()

    /**
     * Defines a single-bit flag with default meanings (name used for both set and unset).
     *
     * @param bit The 0-indexed bit position (0 = MSB/b8, 7 = LSB/b1).
     * @param name Human-readable name and default set meaning for this flag.
     */
    fun bit(bit: Int, name: String) {
        val builder = SingleBitBuilder(bit, name)
        fields.add(builder.build())
    }

    /**
     * Defines a single-bit flag with custom set/unset meanings and optional callbacks.
     *
     * @param bit The 0-indexed bit position (0 = MSB/b8, 7 = LSB/b1).
     * @param name Human-readable name for this flag.
     * @param block DSL block to configure [SingleBitBuilder.set], [SingleBitBuilder.unset],
     *   [SingleBitBuilder.onSet], and [SingleBitBuilder.onUnset].
     */
    fun bit(bit: Int, name: String, block: SingleBitBuilder.() -> Unit) {
        val builder = SingleBitBuilder(bit, name)
        builder.block()
        fields.add(builder.build())
    }

    /**
     * Defines a multi-bit enumerated field.
     *
     * @param bits The contiguous bit range (e.g., `0..1` for a 2-bit enum).
     * @param name Human-readable name for this enum field.
     * @param block DSL block to add [MultiBitEnumBuilder.value] entries.
     */
    fun enum(bits: IntRange, name: String, block: MultiBitEnumBuilder.() -> Unit) {
        val builder = MultiBitEnumBuilder(bits, name)
        builder.block()
        fields.add(builder.build())
    }

    /**
     * Marks a single bit as Reserved for Future Use (RFU).
     *
     * @param bit The 0-indexed bit position to mark as RFU.
     */
    fun rfu(bit: Int) {
        fields.add(RfuField(bit..bit))
    }

    /**
     * Marks a contiguous range of bits as Reserved for Future Use (RFU).
     *
     * @param bits The bit range to mark as RFU (e.g., `2..7`).
     */
    fun rfu(bits: IntRange) {
        fields.add(RfuField(bits))
    }

    internal fun build(): ByteSchema {
        for (field in fields) {
            require(field.startBit in 0..7 && field.endBit in 0..7) {
                "Byte $index ('$label'): field '${field.name}' has bits ${field.bits} outside valid range 0..7"
            }
        }

        val coveredBits = BooleanArray(8)
        for (field in fields) {
            for (bit in field.bits) {
                require(!coveredBits[bit]) {
                    "Byte $index ('$label'): bit $bit is covered by multiple fields (conflict with '${field.name}')"
                }
                coveredBits[bit] = true
            }
        }

        val uncovered = (0..7).filter { !coveredBits[it] }
        require(uncovered.isEmpty()) {
            "Byte $index ('$label'): bits ${uncovered.joinToString(", ")} are not covered by any field"
        }

        return ByteSchema(
            index = index,
            label = label,
            fields = fields.toList(),
        )
    }
}
