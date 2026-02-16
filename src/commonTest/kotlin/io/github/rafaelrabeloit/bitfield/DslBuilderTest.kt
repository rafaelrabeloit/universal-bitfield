package io.github.rafaelrabeloit.bitfield

import io.github.rafaelrabeloit.bitfield.dsl.bitfield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class DslBuilderTest {

    @Test
    fun simpleSingleBitSchema() {
        val schema = bitfield("Test", bytes = 1) {
            byte(1, "Byte 1") {
                bit(0, "Flag A")
                bit(1, "Flag B")
                rfu(2..7)
            }
        }

        assertEquals("Test", schema.name)
        assertEquals(1, schema.expectedBytes)
        assertEquals(1, schema.bytes.size)

        val byteSchema = schema.bytes[0]
        assertEquals(1, byteSchema.index)
        assertEquals("Byte 1", byteSchema.label)
        assertEquals(3, byteSchema.fields.size)

        val flag = byteSchema.fields[0]
        assertIs<SingleBitFlag>(flag)
        assertEquals(0, flag.bit)
        assertEquals("Flag A", flag.name)
        assertEquals("Flag A", flag.setMeaning)
        assertNull(flag.unsetMeaning)
    }

    @Test
    fun singleBitWithSetUnsetMeanings() {
        val schema = bitfield("Test", bytes = 1) {
            byte(1, "Byte 1") {
                bit(0, "Feature") {
                    set("Enabled")
                    unset("Disabled")
                }
                rfu(1..7)
            }
        }

        val flag = schema.bytes[0].fields[0]
        assertIs<SingleBitFlag>(flag)
        assertEquals("Enabled", flag.setMeaning)
        assertEquals("Disabled", flag.unsetMeaning)
    }

    @Test
    fun singleBitWithCallbacks() {
        var setCalled = false
        var unsetCalled = false

        val schema = bitfield("Test", bytes = 1) {
            byte(1, "Byte 1") {
                bit(0, "Feature") {
                    onSet { setCalled = true }
                    onUnset { unsetCalled = true }
                }
                rfu(1..7)
            }
        }

        val flag = schema.bytes[0].fields[0]
        assertIs<SingleBitFlag>(flag)
        flag.onSet?.invoke()
        assertEquals(true, setCalled)
        flag.onUnset?.invoke()
        assertEquals(true, unsetCalled)
    }

    @Test
    fun multiBitEnumSchema() {
        val schema = bitfield("CID", bytes = 1) {
            byte(1, "Byte 1") {
                enum(0..1, "Cryptogram Type") {
                    value(0b00, "AAC")
                    value(0b01, "ARQC")
                    value(0b10, "TC")
                    value(0b11, "RFU")
                }
                rfu(2..7)
            }
        }

        val field = schema.bytes[0].fields[0]
        assertIs<MultiBitEnum>(field)
        assertEquals(0..1, field.bits)
        assertEquals("Cryptogram Type", field.name)
        assertEquals(4, field.values.size)
        assertEquals("AAC", field.values[0b00]?.label)
        assertEquals("ARQC", field.values[0b01]?.label)
    }

    @Test
    fun multiBitEnumWithCallback() {
        var matched = false

        val schema = bitfield("Test", bytes = 1) {
            byte(1, "Byte 1") {
                enum(0..1, "Type") {
                    value(0b00, "None") { matched = true }
                    value(0b01, "One")
                }
                rfu(2..7)
            }
        }

        val field = schema.bytes[0].fields[0]
        assertIs<MultiBitEnum>(field)
        field.values[0b00]?.onMatch?.invoke()
        assertEquals(true, matched)
        assertNull(field.values[0b01]?.onMatch)
    }

    @Test
    fun rfuSingleBit() {
        val schema = bitfield("Test", bytes = 1) {
            byte(1, "Byte 1") {
                bit(0, "Flag")
                rfu(1)
                rfu(2..7)
            }
        }

        val rfu = schema.bytes[0].fields[1]
        assertIs<RfuField>(rfu)
        assertEquals(1..1, rfu.bits)
        assertEquals("RFU", rfu.name)
    }

    @Test
    fun multiByteSchema() {
        val schema = bitfield("AIP", bytes = 2) {
            byte(1, "Byte 1") {
                bit(0, "CDA Supported")
                bit(1, "DDA Supported")
                bit(2, "SDA Supported")
                bit(3, "CV Supported")
                bit(4, "TRM Performed")
                bit(5, "Issuer Auth Supported")
                bit(6, "On-device CV Supported")
                rfu(7)
            }
            byte(2, "Byte 2") {
                bit(0, "EMV Mode")
                bit(1, "Contactless EMV Mode")
                rfu(2..7)
            }
        }

        assertEquals("AIP", schema.name)
        assertEquals(2, schema.expectedBytes)
        assertEquals(2, schema.bytes.size)
        assertEquals(8, schema.bytes[0].fields.size)
        assertEquals(3, schema.bytes[1].fields.size)
    }
}
