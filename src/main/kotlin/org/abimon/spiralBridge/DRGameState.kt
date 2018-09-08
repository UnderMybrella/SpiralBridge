package org.abimon.spiralBridge

import com.sun.jna.Pointer
import java.util.*

data class DRGameState(val data: IntArray) {
    constructor(pointer: Pointer): this(IntArray(30) { i ->
        pointer.getShort((i * 2).toLong()).toInt()
    })

    val timeOfDay: Int
        get() = data[0]

    val gameMode: Int
        get() = data[15]

    val spiralBridgeOp: Int
        get() = data[28]

    val spiralBridgeParamOne: Int
        get() = data[29]
    val spiralBridgeParamTwo: Int
        get() = data[30]

    val spiralBridgeParam: Int
        get() = (data[29] shl 16) or data[30]

    private var cachedSpiralBridgeHash: Int? = null
    private var cachedSpiralBridgeData: SpiralBridgeData<*>? = null

    val spiralBridgeData: SpiralBridgeData<*>
        get() {
            val op = data[28]
            val param = (data[29] shl 8) or data[30]

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