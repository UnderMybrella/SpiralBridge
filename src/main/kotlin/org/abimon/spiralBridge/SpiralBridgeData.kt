package org.abimon.spiralBridge

sealed class SpiralBridgeData<T>(open val op: Int, open val data: T) {
    open class IntData(op: Int, override val data: Int): SpiralBridgeData<Int>(op, data) {
        override fun serialiseData(): Int = data
    }

    object NoOp: IntData(0, 0)
    data class Synchronise(override val data: Int): IntData(1, data)
    data class PrevChoice (val prevChoice: Int): IntData(2, prevChoice)
    object WaitForChoice: IntData(3, 0)
    data class LoadBridgeFile(val fileID: Int): IntData(4, fileID) {
        val chapter = fileID / 1000
        val scene = fileID % 1000
    }

    object ServerAck: IntData(128, 0)
    data class ServerKey(val key: Int): IntData(129, key)
    data class ServerChoice(val choice: Int): IntData(130, choice)

    data class UnknownValue(override val op: Int, override val data: Int): IntData(op, data)

    abstract fun serialiseData(): Int

    companion object {
        fun valueFor(op: Int, param: Int): SpiralBridgeData<*> {
            return when (op) {
                1 -> Synchronise(param)
                2 -> PrevChoice(param)
                else -> UnknownValue(op, param)
            }
        }

        fun valueFor(data: Long): SpiralBridgeData<*> {
            val opCode = (data and 0x000000000000FFFF) shr 0
            val param = (data and 0x0000FFFFFFFF0000) shr 16

            return valueFor(opCode.toInt(), param.toInt())
        }
    }
}