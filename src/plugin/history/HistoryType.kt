package plugin.history

enum class HistoryType {
    rotate,
    breakBlock,
    buildBlock,
    destroyBlock, // when block DESTROYED
    configure;
}