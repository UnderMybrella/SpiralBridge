package org.abimon.spiralBridge

import com.sun.jna.Pointer
import org.abimon.osl.drills.headerCircuits.SpiralBridgeDrill
import java.util.*

data class DRGameState(val data: IntArray) {
    constructor(pointer: Pointer): this(IntArray(256) { i ->
        pointer.getShort((i * 2).toLong()).toInt()
    })

    val timeOfDay: Int
        get() = data[0]

    val gameMode: Int
        get() = data[15]

    val spiralBridgeOp: Int
        get() = data[SpiralBridgeDrill.OP_CODE_GAME_STATE]

    val spiralBridgeParamOne: Int
        get() = data[SpiralBridgeDrill.OP_CODE_PARAM_BIG]
    val spiralBridgeParamTwo: Int
        get() = data[SpiralBridgeDrill.OP_CODE_PARAM_SMALL]

    val spiralBridgeParam: Int
        get() = (data[SpiralBridgeDrill.OP_CODE_PARAM_BIG] shl 16) or data[SpiralBridgeDrill.OP_CODE_PARAM_SMALL]

    private var cachedSpiralBridgeHash: Int? = null
    private var cachedSpiralBridgeData: SpiralBridgeData<*>? = null

    val spiralBridgeData: SpiralBridgeData<*>
        get() {
            val op = data[SpiralBridgeDrill.OP_CODE_GAME_STATE]
            val param = (data[SpiralBridgeDrill.OP_CODE_PARAM_BIG] shl 8) or data[SpiralBridgeDrill.OP_CODE_PARAM_SMALL]

            var hash = 1
            hash = 31 * hash + op
            hash = 31 * hash + param

            if (hash != cachedSpiralBridgeHash || cachedSpiralBridgeData == null) {
                cachedSpiralBridgeHash = hash
                cachedSpiralBridgeData = SpiralBridgeData.valueFor(op, param)
            }

            return cachedSpiralBridgeData!!
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DRGameState) return false

        if (!Arrays.equals(data, other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        return Arrays.hashCode(data)
    }
}