package app.mawjic.tvh.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HtsMessageCodecTest {
    @Test
    fun s64UsesTvheadendLittleEndianVariableWidthEncoding() {
        val message = HtsMessage.mapOf("value" to s64(1337))
        val encoded = HtsMessageCodec.encodeBody(message)
        assertContentEquals(
            byteArrayOf(2, 5, 0, 0, 0, 2, 118, 97, 108, 117, 101, 0x39, 0x05),
            encoded,
        )
        assertEquals(1337L, HtsMessageCodec.decodeBody(encoded).long("value"))
    }

    @Test
    fun negativeS64UsesEightBytes() {
        val encoded = HtsMessageCodec.encodeBody(HtsMessage.mapOf("n" to s64(-1)))
        assertContentEquals(ByteArray(8) { 0xff.toByte() }, encoded.takeLast(8).toByteArray())
        assertEquals(-1L, HtsMessageCodec.decodeBody(encoded).long("n"))
    }

    @Test
    fun nestedMapsListsStringsAndBinaryRoundTrip() {
        val original = HtsMessage.mapOf(
            "method" to hstr("channelAdd"),
            "meta" to HtsValue.MapValue(
                HtsMessage.mapOf("number" to s64(7), "visible" to HtsValue.BooleanValue(true)),
            ),
            "members" to HtsValue.ListValue(listOf(s64(4), s64(9), hstr("radio"))),
            "challenge" to hbin(byteArrayOf(0, 1, 2, -1)),
        )
        val decoded = HtsMessageCodec.decodeBody(HtsMessageCodec.encodeBody(original))
        assertEquals("channelAdd", decoded.text("method"))
        assertEquals(7L, decoded.message("meta")?.long("number"))
        assertEquals(true, decoded.message("meta")?.boolean("visible"))
        assertEquals(4L, (decoded.list("members")?.get(0) as HtsValue.Int64).value)
        assertEquals("radio", (decoded.list("members")?.get(2) as HtsValue.Text).value)
        assertContentEquals(byteArrayOf(0, 1, 2, -1), decoded.binary("challenge"))
    }

    @Test
    fun emptyAndFalseValuesAreRepresentedWithoutPayloadBytes() {
        val original = HtsMessage.mapOf(
            "zero" to s64(0),
            "false" to HtsValue.BooleanValue(false),
            "true" to HtsValue.BooleanValue(true),
        )
        val decoded = HtsMessageCodec.decodeBody(HtsMessageCodec.encodeBody(original))
        assertEquals(0L, decoded.long("zero"))
        assertEquals(false, decoded.boolean("false"))
        assertEquals(true, decoded.boolean("true"))
    }

    @Test
    fun rejectsTruncatedHeadersAndPayloads() {
        assertFailsWith<IllegalArgumentException> { HtsMessageCodec.decodeBody(byteArrayOf(2, 1, 0)) }
        assertFailsWith<IllegalArgumentException> {
            HtsMessageCodec.decodeBody(byteArrayOf(3, 1, 0, 0, 0, 8, 97, 98))
        }
    }

    @Test
    fun rejectsListAtRootAndInvalidUuidLength() {
        assertFailsWith<IllegalArgumentException> {
            HtsMessageCodec.encodeBody(HtsMessage.listOf(listOf(s64(1))))
        }
        assertFailsWith<IllegalArgumentException> {
            HtsValue.Uuid64(byteArrayOf(1, 2, 3))
        }
    }
}
