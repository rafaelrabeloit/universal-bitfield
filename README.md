# BitField Parser

A Kotlin Multiplatform library for declaratively defining and parsing bitfield schemas. Define bitfield layouts with a type-safe DSL, then parse raw bytes to get structured, labeled results with optional callbacks.

**Targets:** JVM, iOS (arm64, x64, simulatorArm64)
**Dependencies:** None beyond `kotlin-stdlib`

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("io.github.rafaelrabeloit:bitfield-parser:0.1.0")
}
```

## Quick Start

```kotlin
import io.github.rafaelrabeloit.bitfield.dsl.bitfield

val schema = bitfield("Application Interchange Profile", bytes = 2) {
    byte(1, "Byte 1") {
        bit(0, "CDA Supported")
        bit(1, "DDA Supported")
        bit(2, "SDA Supported")
        bit(3, "Cardholder Verification is supported")
        bit(4, "Terminal Risk Management is to be performed")
        bit(5, "Issuer Authentication is supported")
        bit(6, "On-device Cardholder Verification is supported")
        rfu(7)
    }
    byte(2, "Byte 2") {
        bit(0, "EMV Mode supported")
        bit(1, "Contactless EMV Mode supported")
        rfu(2..7)
    }
}

val result = schema.parse(byteArrayOf(0x19, 0x80.toByte()))

result.entries.forEach { entry ->
    println("Byte ${entry.byteIndex}, ${entry.field.name}: ${entry.resolvedLabel}")
}
```

## DSL Reference

### Single-Bit Flags

The simplest field type. The field name is used as the label when the bit is set:

```kotlin
bit(0, "CDA Supported")
```

Custom labels for set/unset states:

```kotlin
bit(4, "Issuer Application Data") {
    set("IAD provided")
    unset("IAD not provided")
}
```

### Multi-Bit Enums

Map bit patterns across a range to labeled values:

```kotlin
enum(0..1, "Cryptogram Type") {
    value(0b00, "Application Authentication Cryptogram (AAC)")
    value(0b01, "Authorization Request Cryptogram (ARQC)")
    value(0b10, "Transaction Certificate (TC)")
    value(0b11, "Reserved for Future Use")
}
```

Unmapped patterns resolve to `"Unknown (N)"` where N is the raw value.

### Reserved for Future Use (RFU)

Mark unused bits or ranges:

```kotlin
rfu(7)       // single bit
rfu(2..7)    // bit range
```

### Callbacks

Callbacks fire during `parse()` when a bit or enum value matches:

```kotlin
bit(0, "SDA Failed") {
    onSet { println("SDA failed!") }
    onUnset { println("SDA passed") }
}

enum(0..1, "Auth Method") {
    value(0b00, "No auth")
    value(0b01, "SDA") { println("SDA selected") }
    value(0b10, "DDA") { println("DDA selected") }
    value(0b11, "CDA")
}
```

## Parsing

Call `parse()` on a schema with a byte array:

```kotlin
val result = schema.parse(byteArrayOf(0x19, 0x80.toByte()))
```

The result contains a list of `ParsedEntry` objects:

```kotlin
result.entries.forEach { entry ->
    entry.byteIndex     // 1-based byte position
    entry.field          // the FieldDefinition
    entry.rawBits        // extracted bit value(s) as Int
    entry.resolvedLabel  // human-readable label
}
```

Look up a specific entry:

```kotlin
val entry = result.entryFor(byteIndex = 1, field = someFieldDef)
```

## Bit Numbering

The library uses **0-indexed MSB-first** numbering, matching the EMV convention:

| DSL index | EMV bit | Position |
|-----------|---------|----------|
| 0         | b8      | MSB      |
| 1         | b7      |          |
| ...       | ...     |          |
| 7         | b1      | LSB      |

## Schema Validation

Schemas are validated at construction time (fail-fast):

- Byte count must match the `bytes` parameter
- No overlapping bit ranges within a byte
- All 8 bits in each byte must be covered (use `rfu()` for unused bits)
- Enum values must be within the valid range for the bit width
- Byte indices must be sequential starting from 1

Invalid schemas throw `IllegalArgumentException` with a descriptive message.

## Full Example: Cryptogram Information Data

```kotlin
val cidSchema = bitfield("Cryptogram Information Data", bytes = 1) {
    byte(1, "Byte 1") {
        enum(0..1, "Cryptogram Type (high)") {
            value(0b00, "Application Authentication Cryptogram (AAC)")
            value(0b01, "Authorization Request Cryptogram (ARQC)")
            value(0b10, "Transaction Certificate (TC)")
            value(0b11, "Reserved for Future Use")
        }
        rfu(2..3)
        bit(4, "Issuer Application Data") {
            set("IAD provided")
            unset("IAD not provided")
        }
        bit(5, "Application Transaction Counter") {
            set("ATC provided")
            unset("ATC not provided")
        }
        enum(6..7, "Cryptogram Type (low)") {
            value(0b00, "Application Authentication Cryptogram (AAC)")
            value(0b01, "Authorization Request Cryptogram (ARQC)")
            value(0b10, "Transaction Certificate (TC)")
            value(0b11, "Reserved for Future Use")
        }
    }
}

val result = cidSchema.parse(byteArrayOf(0x80.toByte()))
result.entries.forEach { println("${it.field.name}: ${it.resolvedLabel}") }
```

## License

TBD
