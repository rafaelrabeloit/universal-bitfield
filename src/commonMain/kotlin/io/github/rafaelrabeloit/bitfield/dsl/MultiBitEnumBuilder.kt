package io.github.rafaelrabeloit.bitfield.dsl

import io.github.rafaelrabeloit.bitfield.EnumValue
import io.github.rafaelrabeloit.bitfield.MultiBitEnum

/**
 * DSL builder for defining a [MultiBitEnum] field's enumerated values.
 *
 * This is the receiver for the [ByteSchemaBuilder.enum] block.
 */
@BitFieldDslMarker
class MultiBitEnumBuilder(
    private val bits: IntRange,
    private val name: String,
) {
    private val values = mutableMapOf<Int, EnumValue>()

    /**
     * Maps a bit pattern to a label.
     *
     * @param pattern The integer value of the bit pattern (e.g., `0b01`).
     * @param label Human-readable meaning of this value.
     */
    fun value(pattern: Int, label: String) {
        values[pattern] = EnumValue(label)
    }

    /**
     * Maps a bit pattern to a label with an [onMatch] callback.
     *
     * @param pattern The integer value of the bit pattern.
     * @param label Human-readable meaning of this value.
     * @param onMatch Callback invoked during parsing when this pattern matches.
     */
    fun value(pattern: Int, label: String, onMatch: () -> Unit) {
        values[pattern] = EnumValue(label, onMatch)
    }

    internal fun build(): MultiBitEnum {
        val bitCount = bits.last - bits.first + 1
        val maxValue = (1 shl bitCount) - 1

        for (pattern in values.keys) {
            require(pattern in 0..maxValue) {
                "Enum '$name': value $pattern is out of range for $bitCount-bit field (valid: 0..$maxValue)"
            }
        }

        return MultiBitEnum(
            bits = bits,
            name = name,
            values = values.toMap(),
        )
    }
}
