package plugin.replays

import kotlinx.serialization.Serializable

@Serializable
data class ReplayStack(
    val stack: MutableList<ReplayRecord> = mutableListOf()
) {
    fun size() = stack.size

    fun add(record: ReplayRecord) {
        stack.add(record)
    }

    fun removeFirst() {
        stack.removeAt(0)
    }
}