package org.abimon.spiralBridge

import com.sun.jna.Pointer
import org.abimon.osl.drills.headerCircuits.SpiralBridgeDrill
import java.util.*
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

data class DRGameState(val data: IntArray) {
    constructor(pointer: Pointer): this(IntArray(256) { i ->
        pointer.getShort((i * 2).toLong()).toInt()
    })

    var timeOfDay: Int by reference(0)
    var health: Int by reference(13)
    var gameMode: Int by reference(15)

    var spiralBridgeOp: Int by reference(SpiralBridgeDrill.OP_CODE_GAME_STATE)

    var spiralBridgeParamOne: Int by reference(SpiralBridgeDrill.OP_CODE_PARAM_BIG)
    var spiralBridgeParamTwo: Int by reference(SpiralBridgeDrill.OP_CODE_PARAM_SMALL)

    var spiralBridgeParam: Int
        get() = (data[SpiralBridgeDrill.OP_CODE_PARAM_BIG] shl 16) or data[SpiralBridgeDrill.OP_CODE_PARAM_SMALL]
        set(param) {
            val param1 = param shr 16
            val param2 = (param and 0x0000FFFF)

            data[SpiralBridgeDrill.OP_CODE_PARAM_BIG] = param1
            data[SpiralBridgeDrill.OP_CODE_PARAM_SMALL] = param2
        }

    private var cachedSpiralBridgeHash: Int? = null
    private var cachedSpiralBridgeData: SpiralBridgeData<*>? = null

    var spiralBridgeData: SpiralBridgeData<*>
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
        set(value) {
            val param = value.serialiseData()
            val param1 = param shr 16
            val param2 = (param and 0x0000FFFF)

            data[SpiralBridgeDrill.OP_CODE_GAME_STATE] = value.op
            data[SpiralBridgeDrill.OP_CODE_PARAM_BIG] = param1
            data[SpiralBridgeDrill.OP_CODE_PARAM_SMALL] = param2
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

    fun reference(offset: Int): ReadWriteProperty<DRGameState, Int> = object: ReadWriteProperty<DRGameState, Int> {
        override fun getValue(thisRef: DRGameState, property: KProperty<*>): Int = thisRef[offset]
        override fun setValue(thisRef: DRGameState, property: KProperty<*>, value: Int) { thisRef[offset] = value }
    }

    operator fun get(offset: Int): Int = data[offset]
    operator fun set(offset: Int, value: Int) {
        data[offset] = value
    }
}