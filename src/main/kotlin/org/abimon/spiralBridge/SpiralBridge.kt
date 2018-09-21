package org.abimon.spiralBridge

import com.sun.jna.Memory
import com.sun.jna.Pointer
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import org.abimon.colonelAccess.handle.MemoryAccessor
import org.abimon.spiral.core.utils.writeIntXLE
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class SpiralBridge<E : Any, P : Pointer>(val memoryAccessor: MemoryAccessor<E, P>, val gameStateAddress: Long, val textBufferAddress: Long? = null) {
    companion object {
        var FRAMERATE = 1000L / 60

        var TEXT_BUFFER_SIZE = 512L
    }

    val listeners: MutableList<SpiralBridgeListener> = ArrayList()
    private val changes: MutableList<Long> = ArrayList()
    private var prevMemData = 0L

    val scoutingJob: Job = launch {
        while (isActive && eventBusJob.isActive) {
            delay(FRAMERATE, TimeUnit.MILLISECONDS)

            val (memory, error, readSize) = memoryAccessor.readMemory(gameStateAddress + (28 * 2), 6)

            if (memory == null || readSize != 6L)
                break

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
        while (isActive && scoutingJob.isActive) {
            delay(FRAMERATE, TimeUnit.MILLISECONDS)

            if (changes.isNotEmpty()) {
                val data = SpiralBridgeData.valueFor(changes.removeAt(0))
                listeners.forEach(data::offerTo)
            }
        }
    }

    fun readGameState(): DRGameState? {
        val (memory) = memoryAccessor.readMemory(gameStateAddress, 512)

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

        //Reverse of this is (param1 shl 16) or param2
        val param = data.serialiseData()
        val param1 = param shr 16
        val param2 = (param and 0x0000FFFF)

        mem.setShort(2, param1.toShort())
        mem.setShort(4, param2.toShort())

        return memoryAccessor.writeMemory(gameStateAddress + (28 * 2), mem, mem.size())
    }

    var currentText: String?
        get() = textBufferAddress?.let { textBuffer ->
            val (memory, error, readSize) = memoryAccessor.readMemory(textBufferAddress, TEXT_BUFFER_SIZE)

            if (memory == null)
                return@let null

            val baos = ByteArrayOutputStream()

            for (i in 0 until (readSize ?: 0) step 2) {
                val read = memory.readIntXLE(i, 2)
                if (read == -1)
                    break
                if (read == 0x00)
                    break

                baos.writeIntXLE(read, 2)
            }

            return@let String(baos.toByteArray(), Charsets.UTF_16LE)
        }
        set(value) {
            if (textBufferAddress != null) {
                memoryAccessor.writeMemory(textBufferAddress, value?.toByteArray(Charsets.UTF_16LE) ?: byteArrayOf(0x00, 0x00))
            }
        }

    fun clearOutTextBuffer(): Pair<Boolean, E?> {
        if (textBufferAddress == null)
            return false to null

        val (memory, error, readSize) = memoryAccessor.readMemory(textBufferAddress, TEXT_BUFFER_SIZE)

        if (memory == null)
            return false to error

        val (writeError) = memoryAccessor.writeMemory(textBufferAddress, ByteArray(readSize?.toInt() ?: 2) { 0x00 })

        return true to writeError
    }

    inline fun Pointer.readIntXLE(offset: Long, x: Int): Number =
            when (x) {
                1 -> getByte(offset)
                2 -> getShort(offset)
                4 -> getInt(offset)
                8 -> getLong(offset)
                else -> throw IllegalArgumentException("$x is not 1, 2, 4, or 8")
            }
}