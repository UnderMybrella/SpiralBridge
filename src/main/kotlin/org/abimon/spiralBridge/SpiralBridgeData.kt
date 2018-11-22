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
    data class RequestAction(val action: BridgeRequest): IntData(5, action.code)
    data class StoreValue(val variable: Int, val action: Int, val value: Int): SpiralBridgeData<Triple<Int, Int, Int>>(6, Triple(variable, action, value)) {
        constructor(param: Int): this(((param shr 0) and 0xFF), ((param shr 8) and 0xFF), ((param shr 16) and 0xFFFF))

        override fun serialiseData(): Int = (variable shl 0) or (action shl 8) or (value shl 16)
    }
    data class StoreGameState(val gameState: Int): IntData(7, gameState)
    data class RestoreGameState(val gameState: Int): IntData(8, gameState)
    data class RunScript(val scriptNum: Int): IntData(9, scriptNum)

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
                3 -> WaitForChoice
                4 -> LoadBridgeFile(param)
                5 -> RequestAction(BridgeRequest.valueOf(param))
                6 -> StoreValue(param)
                7 -> StoreGameState(param)
                8 -> RestoreGameState(param)
                9 -> RunScript(param)

                128 -> ServerAck
                129 -> ServerKey(param)
                130 -> ServerChoice(param)

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