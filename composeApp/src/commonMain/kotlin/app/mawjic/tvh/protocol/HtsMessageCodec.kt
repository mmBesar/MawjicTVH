package app.mawjic.tvh.protocol

/**
 * Codec for Tvheadend's HTSMSG binary format (not MessagePack).
 * The four-byte root frame length is handled by the TCP transport.
 */
object HtsMessageCodec {
    const val MAX_MESSAGE_BYTES: Int = 16 * 1024 * 1024
    private const val FIELD_HEADER_BYTES = 6

    private object Type {
        const val MAP = 1
        const val S64 = 2
        const val STR = 3
        const val BIN = 4
        const val LIST = 5
        const val DOUBLE = 6 // Reserved by the published type table; not emitted by Tvheadend's current binary codec.
        const val BOOL = 7
        const val UUID = 8
    }

    fun encodeBody(message: HtsMessage): ByteArray {
        require(!message.isList) { "The HTSP root message must be a map" }
        val body = encodeFields(message)
        require(body.size <= MAX_MESSAGE_BYTES) { "HTSMSG message exceeds $MAX_MESSAGE_BYTES bytes" }
        return body
    }

    fun decodeBody(body: ByteArray): HtsMessage {
        require(body.size <= MAX_MESSAGE_BYTES) { "HTSMSG message exceeds $MAX_MESSAGE_BYTES bytes" }
        return decodeFields(body, isList = false, depth = 0)
    }

    private fun encodeFields(message: HtsMessage): ByteArray {
        val output = ByteAccumulator()
        for (field in message.fields) {
            val name = field.name?.encodeToByteArray() ?: ByteArray(0)
            require(name.size <= 255) { "HTSMSG field names cannot exceed 255 UTF-8 bytes" }
            if (message.isList) require(name.isEmpty()) { "HTSMSG list fields must not have names" }
            else require(name.isNotEmpty()) { "HTSMSG map fields must have names" }

            val (type, data) = encodeValue(field.value)
            require(data.size <= MAX_MESSAGE_BYTES) { "HTSMSG field exceeds the message limit" }
            output.writeByte(type)
            output.writeByte(name.size)
            output.writeInt(data.size)
            output.write(name)
            output.write(data)
            require(output.size <= MAX_MESSAGE_BYTES) { "HTSMSG message exceeds $MAX_MESSAGE_BYTES bytes" }
        }
        return output.toByteArray()
    }

    private fun encodeValue(value: HtsValue): Pair<Int, ByteArray> = when (value) {
        is HtsValue.Int64 -> Type.S64 to encodeS64(value.value)
        is HtsValue.Text -> Type.STR to value.value.encodeToByteArray()
        is HtsValue.Binary -> Type.BIN to value.value
        is HtsValue.MapValue -> {
            require(!value.value.isList) { "Map field must contain a map" }
            Type.MAP to encodeFields(value.value)
        }
        is HtsValue.ListValue -> Type.LIST to encodeFields(HtsMessage.listOf(value.value))
        is HtsValue.BooleanValue -> Type.BOOL to if (value.value) byteArrayOf(1) else ByteArray(0)
        is HtsValue.Uuid64 -> Type.UUID to value.value
    }

    private fun encodeS64(value: Long): ByteArray {
        // Tvheadend stores the least-significant byte first. Negative values occupy all 8 bytes.
        if (value < 0) return ByteArray(8) { index -> (value ushr (index * 8)).toByte() }
        var remaining = value
        val bytes = ArrayList<Byte>(8)
        while (remaining != 0L) {
            bytes += remaining.toByte()
            remaining = remaining ushr 8
        }
        return bytes.toByteArray()
    }

    private fun decodeFields(data: ByteArray, isList: Boolean, depth: Int): HtsMessage {
        require(depth <= 32) { "HTSMSG nesting is too deep" }
        val fields = ArrayList<HtsField>()
        var offset = 0
        while (offset < data.size) {
            require(data.size - offset >= FIELD_HEADER_BYTES) { "Truncated HTSMSG field header" }
            val type = data[offset].toInt() and 0xff
            val nameLength = data[offset + 1].toInt() and 0xff
            val payloadLength = readInt32(data, offset + 2)
            require(payloadLength >= 0 && payloadLength <= MAX_MESSAGE_BYTES) { "Invalid HTSMSG payload length" }
            offset += FIELD_HEADER_BYTES
            require(nameLength <= data.size - offset) { "Truncated HTSMSG field name" }
            val name = if (nameLength == 0) null else data.copyOfRange(offset, offset + nameLength).decodeToString()
            offset += nameLength
            require(payloadLength <= data.size - offset) { "Truncated HTSMSG field payload" }
            require(if (isList) name == null else !name.isNullOrEmpty()) {
                if (isList) "Named field inside HTSMSG list" else "Unnamed field inside HTSMSG map"
            }
            val payload = data.copyOfRange(offset, offset + payloadLength)
            offset += payloadLength
            val value = decodeValue(type, payload, depth)
            fields += HtsField(name, value)
            require(fields.size <= 100_000) { "HTSMSG contains too many fields" }
        }
        return HtsMessage(fields, isList)
    }

    private fun decodeValue(type: Int, payload: ByteArray, depth: Int): HtsValue = when (type) {
        Type.MAP -> HtsValue.MapValue(decodeFields(payload, isList = false, depth = depth + 1))
        Type.LIST -> HtsValue.ListValue(decodeFields(payload, isList = true, depth = depth + 1).fields.map { it.value })
        Type.S64 -> HtsValue.Int64(decodeS64(payload))
        Type.STR -> HtsValue.Text(payload.decodeToString())
        Type.BIN -> HtsValue.Binary(payload)
        Type.BOOL -> {
            require(payload.isEmpty() || payload.size == 1) { "Invalid HTSMSG boolean length" }
            HtsValue.BooleanValue(payload.size == 1 && payload[0].toInt() != 0)
        }
        Type.UUID -> {
            require(payload.size == 8) { "HTSMSG UUID must be 8 bytes" }
            HtsValue.Uuid64(payload)
        }
        Type.DOUBLE -> error("HTSMSG double fields are not supported by this codec")
        else -> error("Unknown HTSMSG field type: $type")
    }

    private fun decodeS64(bytes: ByteArray): Long {
        require(bytes.size <= 8) { "HTSMSG S64 payload cannot exceed 8 bytes" }
        var value = 0uL
        for (index in bytes.indices.reversed()) {
            value = (value shl 8) or (bytes[index].toLong() and 0xff).toULong()
        }
        return value.toLong()
    }

    private fun readInt32(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)

    private class ByteAccumulator {
        private var buffer = ByteArray(128)
        var size: Int = 0
            private set

        private fun ensureCapacity(additional: Int) {
            require(additional >= 0 && additional <= MAX_MESSAGE_BYTES - size) {
                "HTSMSG message exceeds $MAX_MESSAGE_BYTES bytes"
            }
            val required = size + additional
            if (required > buffer.size) {
                var capacity = buffer.size
                while (capacity < required) capacity = (capacity * 2).coerceAtMost(MAX_MESSAGE_BYTES)
                buffer = buffer.copyOf(capacity)
            }
        }

        fun writeByte(value: Int) {
            ensureCapacity(1)
            buffer[size++] = value.toByte()
        }

        fun writeInt(value: Int) {
            writeByte(value ushr 24)
            writeByte(value ushr 16)
            writeByte(value ushr 8)
            writeByte(value)
        }

        fun write(bytes: ByteArray) {
            ensureCapacity(bytes.size)
            bytes.copyInto(buffer, destinationOffset = size)
            size += bytes.size
        }

        fun toByteArray(): ByteArray = buffer.copyOf(size)
    }
}
