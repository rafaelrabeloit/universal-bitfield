package io.github.rafaelrabeloit.bitfield.dsl

import io.github.rafaelrabeloit.bitfield.BitFieldSchema

/** Scope-control marker for the bitfield DSL. */
@DslMarker
annotation class BitFieldDslMarker

/**
 * Top-level entry point for the bitfield schema DSL.
 *
 * Defines a [BitFieldSchema] declaratively by specifying the name, expected byte count,
 * and per-byte field definitions.
 *
 * Example:
 * ```kotlin
 * val schema = bitfield("Application Interchange Profile", bytes = 2) {
 *     byte(1, "Byte 1") {
 *         bit(0, "CDA Supported")
 *         bit(1, "DDA Supported")
 *         // ...
 *         rfu(7)
 *     }
 *     byte(2, "Byte 2") {
 *         bit(0, "EMV Mode supported")
 *         bit(1, "Contactless EMV Mode supported")
 *         rfu(2..7)
 *     }
 * }
 * ```
 *
 * @param name Human-readable schema name.
 * @param bytes The exact number of bytes this schema covers.
 * @param block DSL block defining the byte-level field layout.
 * @return An immutable [BitFieldSchema] ready for parsing.
 * @throws IllegalArgumentException if validation fails (overlapping bits, incomplete coverage, etc.).
 */
fun bitfield(name: String, bytes: Int, block: BitFieldSchemaBuilder.() -> Unit): BitFieldSchema {
    val builder = BitFieldSchemaBuilder(name, bytes)
    builder.block()
    return builder.build()
}
