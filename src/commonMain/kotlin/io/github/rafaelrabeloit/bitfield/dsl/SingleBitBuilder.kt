package io.github.rafaelrabeloit.bitfield.dsl

import io.github.rafaelrabeloit.bitfield.SingleBitFlag

/**
 * DSL builder for configuring a [SingleBitFlag] field.
 *
 * This is the receiver for the [ByteSchemaBuilder.bit] block. By default,
 * [setMeaning][SingleBitFlag.setMeaning] equals the field name and
 * [unsetMeaning][SingleBitFlag.unsetMeaning] is null (falls back to
 * [setMeaning][SingleBitFlag.setMeaning] during parsing).
 */
@BitFieldDslMarker
class SingleBitBuilder(
    private val bit: Int,
    private val name: String,
) {
    private var setMeaning: String = name
    private var unsetMeaning: String? = null
    private var onSet: (() -> Unit)? = null
    private var onUnset: (() -> Unit)? = null

    /**
     * Sets the label returned when the bit is 1.
     *
     * @param meaning The human-readable meaning when set.
     */
    fun set(meaning: String) {
        setMeaning = meaning
    }

    /**
     * Sets the label returned when the bit is 0.
     *
     * @param meaning The human-readable meaning when unset.
     */
    fun unset(meaning: String) {
        unsetMeaning = meaning
    }

    /**
     * Registers a callback invoked during parsing when the bit is 1.
     *
     * @param handler The callback to invoke.
     */
    fun onSet(handler: () -> Unit) {
        onSet = handler
    }

    /**
     * Registers a callback invoked during parsing when the bit is 0.
     *
     * @param handler The callback to invoke.
     */
    fun onUnset(handler: () -> Unit) {
        onUnset = handler
    }

    internal fun build(): SingleBitFlag = SingleBitFlag(
        bit = bit,
        name = name,
        setMeaning = setMeaning,
        unsetMeaning = unsetMeaning,
        onSet = onSet,
        onUnset = onUnset,
    )
}
