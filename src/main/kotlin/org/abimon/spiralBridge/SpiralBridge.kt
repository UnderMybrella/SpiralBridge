package org.abimon.spiralBridge

import com.sun.jna.Memory
import com.sun.jna.Pointer
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import org.abimon.colonelAccess.handle.MemoryAccessor
import java.util.concurrent.TimeUnit

class SpiralBridge<E: Any, P: Pointer>(val memoryAccessor: MemoryAccessor<E, P>, val gameStateAddress: Long) {
    companion object {
        var FRAMERATE = 1000L / 60
    }
    
    val listeners: MutableList<SpiralBridgeListener> = ArrayList()
    private val changes: MutableList<Long> = ArrayList()
    private var prevMemData = 0L

    val scoutingJob: Job = launch {
        while (isActive) {
            delay(FRAMERATE, TimeUnit.MILLISECONDS)

            val (memory, error, readSize) = memoryAccessor.readMemory(gameStateAddress + (28 * 2), 6)

            if (memory == null || readSize != 6L) {
                println("read size of $readSize; $error")
                delay(FRAMERATE, TimeUnit.MILLISECONDS)
                continue
            }

            val op = memory.getShort(0).toLong()
            val param1 = memory.getShort(2).toLong()
            val param2 = memory.getShort(4).toLong()

            val memData = ((param1 shl 32) or (param2 shl 16)) or (op shl 0)

            if (memData != prevMemData) {
                prevMemData = memData
                changes.add(memData)
            }
        }
    }
    
    val eventBusJob: Job = launch { 
        while (isActive) {
            delay(FRAMERATE, TimeUnit.MILLISECONDS)

            if (changes.isNotEmpty()) {
                val data = SpiralBridgeData.valueFor(changes.removeAt(0))
                listeners.forEach(data::offerTo)
            }
        }
    }

    fun readGameState(): DRGameState? {
        val (memory) = memoryAccessor.readMemory(gameStateAddress, 60)

        if (memory == null)
            return null

        return DRGameState(memory)
    }

    fun writeGameState(state: DRGameState): Pair<E?, Long?> {
        val mem = Memory((state.data.size * 2).toLong())
        mem.write(0, state.data, 0, state.data.size)

        return memoryAccessor.writeMemory(gameStateAddress, mem, mem.size())
    }

    fun writeSpiralBridgeData(data: SpiralBridgeData<*>): Pair<E?, Long?> {
        val mem = Memory(6)
        mem.setShort(0, data.op.toShort())

        //Reverse of this is (param2 shl 16) or param1
        val param = data.serialiseData()
        val param1 = (param and 0x0000FFFF)
        val param2 = param shr 16

        mem.setShort(2, param1.toShort())
        mem.setShort(4, param2.toShort())

        return memoryAccessor.writeMemory(gameStateAddress + (28 * 2), mem, mem.size())
    }
}