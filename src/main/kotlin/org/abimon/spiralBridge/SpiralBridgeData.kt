package org.abimon.spiralBridge

sealed class SpiralBridgeData<T>(open val op: Int, open val data: T) {
    data class Synchronise(override val data: Int): SpiralBridgeData<Int>(0, data)
    data class PrevChoice (override val data: Int): SpiralBridgeData<Int>(1, data)

    data class UnknownValue(override val op: Int, override val data: Int): SpiralBridgeData<Int>(op, data)

    companion object {
        fun valueFor(op: Int, param: Int): SpiralBridgeData<*> {
            return when (op) {
                0 -> Synchronise(param)
                1 -> PrevChoice(param)
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