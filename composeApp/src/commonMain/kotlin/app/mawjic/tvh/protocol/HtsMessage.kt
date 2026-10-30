package app.mawjic.tvh.protocol

/** Values supported by Tvheadend's HTSMSG binary representation. */
sealed class HtsValue {
    data class Int64(val value: Long) : HtsValue()
    data class Text(val value: String) : HtsValue()
    data class Binary(val value: ByteArray) : HtsValue()
    data class MapValue(val value: HtsMessage) : HtsValue()
    data class ListValue(val value: List<HtsValue>) : HtsValue()
    data class BooleanValue(val value: Boolean) : HtsValue()
    data class Uuid64(val value: ByteArray) : HtsValue() {
        init { require(value.size == 8) { "HTSMSG UUID values are exactly 8 bytes" } }
    }
}

data class HtsField(val name: String?, val value: HtsValue)

data class HtsMessage(
    val fields: List<HtsField>,
    val isList: Boolean = false,
) {
    fun value(name: String): HtsValue? = fields.lastOrNull { it.name == name }?.value
    fun text(name: String): String? = (value(name) as? HtsValue.Text)?.value
    fun long(name: String): Long? = (value(name) as? HtsValue.Int64)?.value
    fun binary(name: String): ByteArray? = (value(name) as? HtsValue.Binary)?.value
    fun boolean(name: String): Boolean? = (value(name) as? HtsValue.BooleanValue)?.value
    fun message(name: String): HtsMessage? = (value(name) as? HtsValue.MapValue)?.value
    fun list(name: String): List<HtsValue>? = (value(name) as? HtsValue.ListValue)?.value

    companion object {
        fun emptyMap(): HtsMessage = HtsMessage(emptyList())
        fun mapOf(vararg entries: Pair<String, HtsValue>): HtsMessage =
            HtsMessage(entries.map { HtsField(it.first, it.second) })
        fun listOf(values: List<HtsValue>): HtsMessage =
            HtsMessage(values.map { HtsField(null, it) }, isList = true)
    }
}

fun s64(value: Long) = HtsValue.Int64(value)
fun hstr(value: String) = HtsValue.Text(value)
fun hbin(value: ByteArray) = HtsValue.Binary(value)
