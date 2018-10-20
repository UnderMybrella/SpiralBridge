package org.abimon.spiralBridge.nativeBridge

import com.sun.jna.Pointer
import org.abimon.colonelAccess.handle.MemoryAccessor
import org.abimon.colonelAccess.handle.MemoryRegion
import org.abimon.colonelAccess.osx.KernReturn
import org.abimon.spiralBridge.Synchronisation
import org.abimon.spiralBridge.getAllViableDanganRegions
import org.abimon.spiralBridge.readInt64LE
import java.util.*
import kotlin.system.measureNanoTime

open class FallbackNativeWrapper<T: Pointer, E: Any>(memoryAccessor: MemoryAccessor<E, T>): NativeWrapper<T, E>(memoryAccessor) {
    val viableRegions: List<Pair<MemoryRegion, Long>>
    val regionMemory: ByteArray
    val candidates: Array<IntArray>

    override fun benchmark(): List<Long> {
        var currentCount: Int
        var currentValue: Int
        var currentMemValue: Int

        return (0 until 10).map {
            viableRegions.map { region ->
                measureNanoTime {
                    val regionNum = region.second.toInt()
                    val (memory, kret, sizeRead) = memoryAccessor.readMemory(region.first.start, region.first.size)

                    if (memory == null || (kret != null && kret != KernReturn.KERN_SUCCESS) || sizeRead == null) {
                        println("ERR: Synchronisation failed; attempted to read ${region.first.size} bytes at memory region 0x${region.first.start.toString(16)}, got $kret, $sizeRead")
                        return emptyList()
                    }

                    try {
                        memory.read(0, regionMemory, 0, sizeRead.toInt())

                        for (i in 0 until (sizeRead - (sizeRead % 2) - 1).toInt()) {
                            currentCount = (candidates[regionNum][i] and Synchronisation.COUNT_MASK) shr 16
                            currentValue = (candidates[regionNum][i] and Synchronisation.VALUE_MASK) shr 0
                            currentMemValue = ((regionMemory[i].toInt() and 0xFF shl 8) or (regionMemory[i + 1].toInt() and 0xFF))

                            if (currentValue == 1 && currentMemValue == 2) {
                            } else if (currentValue == 2 && currentMemValue == 4) {
                            } else if (currentValue == 4 && currentMemValue == 8) {
                            } else if (currentValue == 8 && currentMemValue == 1) {
                            } else if (currentValue == 0) {
                            } else if (currentValue != currentMemValue) {
                            }

                            candidates[regionNum][i] = ((0 and Synchronisation.VALUE_MASK) shl 16) or (0 and Synchronisation.VALUE_MASK)
                        }
                    } finally {
                        memoryAccessor.deallocateMemory(memory)
                    }
                }
            }.sum()
        }
    }

    override fun findGameStart(firstSyncValue: Int, secondSyncValue: Int, thirdSyncValue: Int, fourthSyncValue: Int): Long? {
        //5a.   We begin listening out for any blocks of memory that have an int value of our initial synchronisation value, and store them in a candidates list
        //5b.   Alternatively, if any of our blocks of memory have an int value of our initial synchronisation value * 4, we store them in a second candidates list
        //5c.   If any block of memory ever ends up

        var newValue: Int
        var newCount: Int
        var gameStateStart: Long? = null
        var currentCount: Int
        var currentValue: Int
        var currentMemValue: Int

        while (gameStateStart == null) {
            viableRegions.forEach { region ->
                val regionNum = region.second.toInt()
                val (memory, kret, sizeRead) = memoryAccessor.readMemory(region.first.start, region.first.size)

                if (memory == null || (kret != null && kret != KernReturn.KERN_SUCCESS) || (sizeRead ?: 0) <= 0) {
                    println("ERR: Synchronisation failed; attempted to read ${region.first.size} bytes at memory region 0x${region.first.start.toString(16)}, got $kret, $sizeRead")
                    return null
                }

                try {
                    memory.read(0, regionMemory, 0, sizeRead!!.toInt())

                    for (i in 0 until (sizeRead - (sizeRead % 2) - 1).toInt()) {
                        currentCount = (candidates[regionNum][i] and Synchronisation.COUNT_MASK) shr 16
                        currentValue = (candidates[regionNum][i] and Synchronisation.VALUE_MASK) shr 0
                        //TODO: If this ever breaks, check endianness
                        currentMemValue = (((regionMemory[i + 1].toInt() and 0xFF) shl 8) or (regionMemory[i].toInt() and 0xFF))

                        newValue = currentValue
                        newCount = currentCount

                        if (currentValue == firstSyncValue && currentMemValue == secondSyncValue) {
                            if (currentCount == 3) {
                                gameStateStart = region.first.start + i - Synchronisation.GAME_STATE_OFFSET
                                break
                            }

                            newValue = secondSyncValue
                            newCount = ++currentCount
                        } else if (currentValue == secondSyncValue && currentMemValue == thirdSyncValue) {
                            if (currentCount == 3) {
                                gameStateStart = region.first.start + i - Synchronisation.GAME_STATE_OFFSET
                                break
                            }

                            newValue = thirdSyncValue
                            newCount = ++currentCount
                        } else if (currentValue == thirdSyncValue && currentMemValue == fourthSyncValue) {
                            if (currentCount == 3) {
                                gameStateStart = region.first.start + i - Synchronisation.GAME_STATE_OFFSET
                                break
                            }

                            newValue = fourthSyncValue
                            newCount = ++currentCount
                        } else if (currentValue == fourthSyncValue && currentMemValue == firstSyncValue) {
                            if (currentCount == 3) {
                                gameStateStart = region.first.start + i - Synchronisation.GAME_STATE_OFFSET
                                break
                            }

                            newValue = firstSyncValue
                            newCount = ++currentCount
                        } else if (currentValue == 0) {
                            when (currentMemValue) {
                                firstSyncValue -> {
                                    newValue = firstSyncValue
                                    newCount = 1
                                }
                                secondSyncValue -> {
                                    newValue = secondSyncValue
                                    newCount = 1
                                }
                                thirdSyncValue -> {
                                    newValue = thirdSyncValue
                                    newCount = 1
                                }
                                fourthSyncValue -> {
                                    newValue = fourthSyncValue
                                    newCount = 1
                                }
                            }
                        } else if (currentValue != currentMemValue) {
                            newValue = 0
                            newCount = 0
                        }

                        candidates[region.second.toInt()][i] = ((newCount and Synchronisation.VALUE_MASK) shl 16) or (newValue and Synchronisation.VALUE_MASK)
                    }
                } finally {
                    memoryAccessor.deallocateMemory(memory)
                }
            }

            //Thread.sleep(10)
        }

        candidates.forEach { intArray -> Arrays.fill(intArray, 0) }
        return gameStateStart
    }

    override fun findTextBufferStart(firstSyncTextLE: Long, secondSyncTextLE: Long, thirdSyncTextLE: Long, fourthSyncTextLE: Long, firstSyncValue: Int, secondSyncValue: Int, thirdSyncValue: Int, fourthSyncValue: Int): Long? {
        var textBufferStart: Long? = null
        var currentCount: Int
        var currentValue: Int
        var currentMemValue: Int
        var newValue: Int
        var newCount: Int

        while (textBufferStart == null) {
            viableRegions.forEach { region ->
                val (memory, kret, sizeRead) = memoryAccessor.readMemory(region.first.start, region.first.size)

                if (memory == null || (kret != null && kret != KernReturn.KERN_SUCCESS) || (sizeRead ?: 0) <= 0) {
                    println("ERR: Synchronisation failed; attempted to read ${region.first.size} bytes at memory region 0x${region.first.start.toString(16)}, got $kret, $sizeRead")
                    return null
                }

                try {
                    memory.read(0, regionMemory, 0, sizeRead!!.toInt())

                    for (i in 0 until (sizeRead - (sizeRead % 8) - 7).toInt()) {
                        currentCount = (candidates[region.second.toInt()][i] and Synchronisation.COUNT_MASK) shr 16
                        currentValue = (candidates[region.second.toInt()][i] and Synchronisation.VALUE_MASK) shr 0

                        currentMemValue = when (regionMemory.readInt64LE(i)) {
                        //firstSyncTextBE -> firstSyncValue
                            firstSyncTextLE -> firstSyncValue

                        //secondSyncTextBE -> secondSyncValue
                            secondSyncTextLE -> secondSyncValue

                        //thirdSyncTextBE -> thirdSyncValue
                            thirdSyncTextLE -> thirdSyncValue

                        //fourthSyncTextBE -> fourthSyncValue
                            fourthSyncTextLE -> fourthSyncValue

                            0L -> 0

                            else -> -1
                        }

                        newValue = currentValue
                        newCount = currentCount

                        if (currentValue == firstSyncValue && currentMemValue == secondSyncValue) {
                            if (currentCount == 3) {
                                textBufferStart = region.first.start + i
                                break
                            }

                            newValue = secondSyncValue
                            newCount = ++currentCount
                        } else if (currentValue == secondSyncValue && currentMemValue == thirdSyncValue) {
                            if (currentCount == 3) {
                                textBufferStart = region.first.start + i
                                break
                            }

                            newValue = thirdSyncValue
                            newCount = ++currentCount
                        } else if (currentValue == thirdSyncValue && currentMemValue == fourthSyncValue) {
                            if (currentCount == 3) {
                                textBufferStart = region.first.start + i
                                break
                            }

                            newValue = fourthSyncValue
                            newCount = ++currentCount
                        } else if (currentValue == fourthSyncValue && currentMemValue == firstSyncValue) {
                            if (currentCount == 3) {
                                textBufferStart = region.first.start + i
                                break
                            }

                            newValue = firstSyncValue
                            newCount = ++currentCount
                        } else if (currentValue == 0) {
                            when (currentMemValue) {
                                firstSyncValue -> {
                                    newValue = firstSyncValue
                                    newCount = 1
                                }
                                secondSyncValue -> {
                                    newValue = secondSyncValue
                                    newCount = 1
                                }
                                thirdSyncValue -> {
                                    newValue = thirdSyncValue
                                    newCount = 1
                                }
                                fourthSyncValue -> {
                                    newValue = fourthSyncValue
                                    newCount = 1
                                }
                            }
                        } else if (currentValue != currentMemValue) {
                            newValue = 0
                            newCount = 0
                        }

                        candidates[region.second.toInt()][i] = ((newCount and Synchronisation.VALUE_MASK) shl 16) or (newValue and Synchronisation.VALUE_MASK)
                    }
                } finally {
                    memoryAccessor.deallocateMemory(memory)
                }
            }

            //Thread.sleep(10)
        }

        candidates.forEach { intArray -> Arrays.fill(intArray, 0) }
        return textBufferStart
    }

    init {
        println("Using fallback wrapper")
        println("Grabbing viable memory regions of Danganronpa; heap, or stack")

        viableRegions = memoryAccessor.getAllViableDanganRegions()
                .mapIndexed { index, memoryRegion -> memoryRegion to index.toLong() } //Saves memory heap later (I hope)

        println("${viableRegions.size} viable regions")

        candidates = viableRegions.map { (region) -> IntArray(region.size.toInt()) }.toTypedArray()
        regionMemory = ByteArray((viableRegions.maxBy { region -> region.first.size }!!.first.size).toInt()) { 0 }

        println("Currently have ${regionMemory.size} bytes stored in memory")
    }

}