package io.github.rafaelrabeloit.bitfield

/**
 * Base class for all field definitions within a [ByteSchema].
 *
 * Each field covers one or more contiguous bit positions. Bit numbering uses
 * 0-indexed MSB-first convention: bit 0 = MSB (b8), bit 7 = LSB (b1).
 */
sealed class FieldDefinition {
    /** Human-readable name of this field. */
    abstract val name: String

    /** The bit positions this field covers, inclusive. */
    abstract val bits: IntRange

    /** The first (most significant) bit position of this field. */
    val startBit: Int get() = bits.first

    /** The last (least significant) bit position of this field. */
    val endBit: Int get() = bits.last
}

/**
 * A single-bit flag field that resolves to a set or unset meaning.
 *
 * @property bit The 0-indexed bit position (0 = MSB).
 * @property name Human-readable name of this flag.
 * @property setMeaning Label returned when the bit is 1.
 * @property unsetMeaning Label returned when the bit is 0. If null, [setMeaning] is used for both states.
 * @property onSet Callback invoked during parsing when the bit is 1.
 * @property onUnset Callback invoked during parsing when the bit is 0.
 */
class SingleBitFlag(
    val bit: Int,
    override val name: String,
    val setMeaning: String,
    val unsetMeaning: String?,
    val onSet: (() -> Unit)? = null,
    val onUnset: (() -> Unit)? = null,
) : FieldDefinition() {
    override val bits: IntRange get() = bit..bit
}

/**
 * A multi-bit enumerated field that maps bit patterns to labels.
 *
 * @property bits The contiguous bit range this field covers (e.g., `0..1` for a 2-bit enum).
 * @property name Human-readable name of this enum field.
 * @property values Map from bit pattern (as an integer) to [EnumValue]. Unmapped patterns
 *   resolve to "Unknown (N)" during parsing.
 */
class MultiBitEnum(
    override val bits: IntRange,
    override val name: String,
    val values: Map<Int, EnumValue>,
) : FieldDefinition()

/**
 * A label and optional callback for one value in a [MultiBitEnum].
 *
 * @property label Human-readable meaning of this enum value.
 * @property onMatch Callback invoked during parsing when this value matches.
 */
class EnumValue(
    val label: String,
    val onMatch: (() -> Unit)? = null,
)

/**
 * A Reserved for Future Use (RFU) field marker.
 *
 * RFU fields always resolve to the label "RFU" during parsing.
 *
 * @property bits The contiguous bit range reserved for future use.
 */
class RfuField(
    override val bits: IntRange,
) : FieldDefinition() {
    override val name: String get() = "RFU"
}
