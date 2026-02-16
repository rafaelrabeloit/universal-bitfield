package io.github.rafaelrabeloit.bitfield.dsl

import io.github.rafaelrabeloit.bitfield.BitFieldSchema
import io.github.rafaelrabeloit.bitfield.ByteSchema

/**
 * DSL builder for constructing a [BitFieldSchema].
 *
 * This is the receiver for the [bitfield] block. Use [byte] to define
 * each byte's field layout.
 */
@BitFieldDslMarker
class BitFieldSchemaBuilder(
    private val name: String,
    private val expectedBytes: Int,
) {
    private val bytes = mutableListOf<ByteSchema>()

    /**
     * Defines the field layout for a single byte.
     *
     * @param index The 1-based byte position (must be sequential starting from 1).
     * @param label Human-readable label for this byte.
     * @param block DSL block defining the bit-level fields within this byte.
     */
    fun byte(index: Int, label: String, block: ByteSchemaBuilder.() -> Unit) {
        val builder = ByteSchemaBuilder(index, label)
        builder.block()
        bytes.add(builder.build())
    }

    internal fun build(): BitFieldSchema {
        require(bytes.size == expectedBytes) {
            "Schema '$name' declares $expectedBytes byte(s) but ${bytes.size} byte definition(s) were provided"
        }

        bytes.forEachIndexed { i, byteSchema ->
            require(byteSchema.index == i + 1) {
                "Schema '$name': byte at position ${i + 1} has index ${byteSchema.index}, expected ${i + 1}"
            }
        }

        return BitFieldSchema(
            name = name,
            expectedBytes = expectedBytes,
            bytes = bytes.toList(),
        )
    }
}
