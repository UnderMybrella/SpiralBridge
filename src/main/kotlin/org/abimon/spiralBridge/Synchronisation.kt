package org.abimon.spiralBridge

import org.abimon.colonelAccess.handle.MemoryAccessor
import org.abimon.colonelAccess.osx.KernReturn
import org.abimon.osl.OpenSpiralLanguageParser
import org.abimon.osl.SpiralDrillBit
import org.abimon.spiral.core.objects.customLin
import org.abimon.spiral.core.objects.scripting.CustomLin
import org.abimon.spiral.core.objects.scripting.lin.LinScript
import org.abimon.spiral.core.objects.scripting.lin.dr1.DR1LoadScriptEntry
import org.abimon.spiral.core.objects.scripting.lin.dr2.DR2LoadScriptEntry
import org.abimon.spiralBridge.osx.RemapMemoryAccessor
import org.parboiled.parserunners.BasicParseRunner
import org.parboiled.support.ParsingResult
import java.awt.Desktop
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import java.util.function.UnaryOperator
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.system.measureNanoTime

object Synchronisation {
    val os = EnumOS.ourOS

    val SCRIPT_REGEX = "e\\d{2}_\\d{3}_\\d{3}\\.lin".toRegex()
    val BACKED_UP_SCRIPT_REGEX = "sync_backup_e\\d{2}_\\d{3}_\\d{3}\\.lin".toRegex()

    val VALUE_MASK = 0x0000FFFF.toInt()
    val COUNT_MASK = 0xFFFF0000.toInt()

    val GAME_STATE_OFFSET = 30 * 2

    fun accessorForSystem(pid: Int): MemoryAccessor<*, *> {
        val os = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH)
        if (os.indexOf("mac") >= 0 || os.indexOf("darwin") >= 0) {
            return RemapMemoryAccessor(pid)
        } else if (os.indexOf("win") >= 0) {
            //return BufferMemoryAccessor(pid)
        } else if (os.indexOf("nux") >= 0) {
            //return LinuxMemoryAccessor(pid)
        }

        return MemoryAccessor.accessorForSystem(pid)
    }

    fun synchronise(rootDir: File): SpiralBridge<*, *>? {
        println("Attempting synchronisation to $rootDir (Exists: ${rootDir.exists()})")
        var running: Pair<Int, EnumGame>? = os.getDRGame()

        if (running == null) {
            Desktop.getDesktop().browse(URI("steam://run/413410"))

            print("Waiting for Danganronpa: Trigger Happy Havoc to start up")

            while (running == null) {
                print(".")

                running = os.getDRGame()
                Thread.sleep(1000)
            }

            println()
        }

        val (pid, danganronpa) = running

        println("Danganronpa game $danganronpa found with PID $pid")
        println("Acquiring memory accessor...")

        val memoryAccessor = accessorForSystem(pid)

        println("Grabbing viable memory regions of Danganronpa; heap, or stack")

        val viableRegions = memoryAccessor.getAllViableDanganRegions()
                .mapIndexed { index, memoryRegion -> memoryRegion to index.toLong() } //Saves memory heap later (I hope)

        println("${viableRegions.size} viable regions")

        val regionMemory = ByteArray((viableRegions.maxBy { region -> region.first.size }!!.first.size).toInt()) { 0 }

        println("Currently have ${regionMemory.size} bytes stored in memory")

        println("Benchmarking read times...")

        val candidates: Array<IntArray> = viableRegions.map { (region) -> IntArray(region.size.toInt()) }.toTypedArray()

        var gameStateStart: Long? = null
        var currentCount: Int
        var currentValue: Int
        var currentMemValue: Int

        val loopTimes = (0 until 10).map {
            viableRegions.map { region ->
                measureNanoTime {
                    val (memory, kret, sizeRead) = memoryAccessor.readMemory(region.first.start, region.first.size)

                    if (memory == null || (kret != null && kret != KernReturn.KERN_SUCCESS) || sizeRead == null) {
                        println("ERR: Synchronisation failed; attempted to read ${region.first.size} bytes at memory region 0x${region.first.start.toString(16)}, got $kret, $sizeRead")
                        return null
                    }

                    try {
                        memory.read(0, regionMemory, 0, sizeRead.toInt())

                        for (i in 0 until (sizeRead - (sizeRead % 2) - 1).toInt()) {
                            currentCount = (candidates[region.second.toInt()][i] and COUNT_MASK) shr 16
                            currentValue = (candidates[region.second.toInt()][i] and VALUE_MASK) shr 0
                            currentMemValue = ((regionMemory[i].toInt() and 0xFF shl 8) or (regionMemory[i + 1].toInt() and 0xFF))

                            if (currentValue == 1 && currentMemValue == 2) {
                            } else if (currentValue == 2 && currentMemValue == 4) {
                            } else if (currentValue == 4 && currentMemValue == 8) {
                            } else if (currentValue == 8 && currentMemValue == 1) {
                            } else if (currentValue == 0) {
                            } else if (currentValue != currentMemValue) {
                            }

                            candidates[region.second.toInt()][i] = ((0 and VALUE_MASK) shl 16) or (0 and VALUE_MASK)
                        }
                    } finally {
                        memoryAccessor.deallocateMemory(memory)
                    }
                }
            }.sum()
        }

        val minLoopTime = loopTimes.min()!!
        val maxLoopTime = loopTimes.max()!!
        val avgLoopTime = loopTimes.average().roundToLong()

        println("Benchmarked times min/max/avg: $minLoopTime ns/$maxLoopTime ns/$avgLoopTime ns")

        //We take the maximum time it would take to loop through the memory, and multiply it by 1.5 to be safe
        //Then, we multiple that by 2 to simulate 2 loops through memory space, and divide it by the duration of 1 frame

        val waitXFrames = ceil(((maxLoopTime * 1.5) * 5) / (TimeUnit.NANOSECONDS.convert(1, TimeUnit.SECONDS) / 60)).toInt()

        println("Wait $waitXFrames frames (${waitXFrames / 60}s) per loop")

        println("Backing up existing scripts")

        //For this, we want to do the following:
        //1.    Take all existing scripts and back them up
        //1nb.  In the event of finding existing sync scripts, use the paths of those instead of backing up scripts

        //2a.   We pick a random number; this is our initial synchronisation value. It should be between 16 and 64, to give us a nice general range
        //2b.   We pick a second random number, which should be at least 3 times our initial sync value, with some fuzziness.
        //2c.   We pick a third random number, which should be at least half our initial sync value, with some fuzziness; minimum of half the first number
        //2d.   We pick a fourth random number, which should be the exact sum of our previous values plus 256, to ensure we have to use a short

        //3a.   We write a script that calls itself, and starts communicating back to us.
        //3b.   The script has to set the sync code to our first random number, and then our second number; we wait a number of frames in between to ensure the mothership picks up on it
        //3c.   We set the text four times, the first is to (%FIRST_SYNC_VALUE << 48) | (%SECOND_SYNC_VALUE << 32) | (%THIRD_SYNC_VALUE << 16) | (%FOURTH_SYNC_VALUE << 0).
        //3d.   Each time, we switch the order of the sync values for the text

        //4.    We compile that script, and save it to every script that we backed up

        //5a.   We begin listening out for any blocks of memory that have an int value of our initial synchronisation value, and store them in a candidates list
        //5b.   Alternatively, if any of our blocks of memory have an int value of our initial synchronisation value * 4, we store them in a second candidates list
        //5c.   If any block of memory ever ends up

        val scriptsFolder = File(rootDir, "Dr1${File.separator}data${File.separator}us${File.separator}script")
        val scripts = scriptsFolder.listFiles()
                .filter { file -> file.isFile && file.name.matches(BACKED_UP_SCRIPT_REGEX) }
                .map { file -> File(scriptsFolder, file.name.substring(12)) }
                .takeIf { list -> list.isNotEmpty() }
                ?: scriptsFolder.listFiles().filter { file ->
                    file.isFile && file.name.matches(SCRIPT_REGEX)
                }

        //1.    Take all existing scripts and back them up
        val backupTime = measureNanoTime {
            scripts.forEach { file ->
                val dest = File(scriptsFolder, "sync_backup_${file.name}")
                if (!dest.exists() && file.exists())
                    Files.move(file.toPath(), dest.toPath())

                if (file.exists())
                    file.delete()
            }
        }
        println("Backing up existing scripts took $backupTime ns")

        println("Compiling OSL script...")

        try {
            //2a.   We pick a random number; this is our initial synchronisation value. It should be between 16 and 64, to give us a nice general range
            //2b.   We pick a second random number, which should be at least 3 times our initial sync value, with some fuzziness.
            //2c.   We pick a third random number, which should be at least half our initial sync value, with some fuzziness; minimum of half the first number
            //2d.   We pick a fourth random number, which should be the exact sum of our previous values
            val firstSyncValue = ThreadLocalRandom.current().nextInt(16, 64)
            val secondSyncValue = firstSyncValue * 3 + ThreadLocalRandom.current().nextInt(16, 64)
            val thirdSyncValue = max(secondSyncValue / 2 - ThreadLocalRandom.current().nextInt(0, 16), firstSyncValue)
            val fourthSyncValue = firstSyncValue + secondSyncValue + thirdSyncValue + 256
//            val fourthSyncValueDiv = fourthSyncValue / 256
//            val fourthSyncValueRem = fourthSyncValue % 256

            //3a.   We write a script that calls itself, and starts communicating back to us.
            //3b.   The script has to set the sync code to our number from before, and then that number times 4; we wait s in between to ensure the mothership picks up on it
            //3c.   We set the text four times, the first is to (%FIRST_SYNC_VALUE << 48) | (%SECOND_SYNC_VALUE << 32) | (%THIRD_SYNC_VALUE << 16) | (%FOURTH_SYNC_VALUE << 0).
            //3d.   Each time, we switch the order of the sync values for the text

            //4.    We compile that script, and save it to every script that we backed up
            val script = retrieve("scripts/synchronisation.osl")?.let { bytes -> String(bytes) } ?: ""
            val customScript: CustomLin

            val randChapter = ThreadLocalRandom.current().nextInt(0, 255)
            val randRoom = ThreadLocalRandom.current().nextInt(0, 255)
            val randScene = ThreadLocalRandom.current().nextInt(0, 255)

            val firstSyncText = String(intArrayOf(
                    firstSyncValue and 0xFF, (firstSyncValue shr 8) and 0xFF,
                    secondSyncValue and 0xFF, (secondSyncValue shr 8) and 0xFF,
                    thirdSyncValue and 0xFF, (thirdSyncValue shr 8) and 0xFF,
                    fourthSyncValue and 0xFF, (fourthSyncValue shr 8) and 0xFF
            ).map(Int::toByte).toByteArray(), Charsets.UTF_16LE)

            val secondSyncText = String(intArrayOf(
                    fourthSyncValue and 0xFF, (fourthSyncValue shr 8) and 0xFF,
                    firstSyncValue and 0xFF, (firstSyncValue shr 8) and 0xFF,
                    secondSyncValue and 0xFF, (secondSyncValue shr 8) and 0xFF,
                    thirdSyncValue and 0xFF, (thirdSyncValue shr 8) and 0xFF
            ).map(Int::toByte).toByteArray(), Charsets.UTF_16LE)

            val thirdSyncText = String(intArrayOf(
                    thirdSyncValue and 0xFF, (thirdSyncValue shr 8) and 0xFF,
                    fourthSyncValue and 0xFF, (fourthSyncValue shr 8) and 0xFF,
                    firstSyncValue and 0xFF, (firstSyncValue shr 8) and 0xFF,
                    secondSyncValue and 0xFF, (secondSyncValue shr 8) and 0xFF
            ).map(Int::toByte).toByteArray(), Charsets.UTF_16LE)

            val fourthSyncText = String(intArrayOf(
                    secondSyncValue and 0xFF, (secondSyncValue shr 8) and 0xFF,
                    thirdSyncValue and 0xFF, (thirdSyncValue shr 8) and 0xFF,
                    fourthSyncValue and 0xFF, (fourthSyncValue shr 8) and 0xFF,
                    firstSyncValue and 0xFF, (firstSyncValue shr 8) and 0xFF
            ).map(Int::toByte).toByteArray(), Charsets.UTF_16LE)

            val firstSyncTextLE = firstSyncText.toByteArray(Charsets.UTF_16LE).readInt64LE()
            val firstSyncTextBE = firstSyncText.toByteArray(Charsets.UTF_16LE).readInt64LE()

            val secondSyncTextLE = secondSyncText.toByteArray(Charsets.UTF_16LE).readInt64LE()
            val secondSyncTextBE = secondSyncText.toByteArray(Charsets.UTF_16LE).readInt64LE()

            val thirdSyncTextLE = thirdSyncText.toByteArray(Charsets.UTF_16LE).readInt64LE()
            val thirdSyncTextBE = thirdSyncText.toByteArray(Charsets.UTF_16LE).readInt64LE()

            val fourthSyncTextLE = fourthSyncText.toByteArray(Charsets.UTF_16LE).readInt64LE()
            val fourthSyncTextBE = fourthSyncText.toByteArray(Charsets.UTF_16LE).readInt64LE()

            try {
                val parser = OpenSpiralLanguageParser { ByteArray(0) }

                //For slow systems like mine
                parser.maxForLoopFange = max(parser.maxForLoopFange, waitXFrames)

                parser["FILE_CHAPTER"] = randChapter
                parser["FILE_ROOM"] = randRoom
                parser["FILE_SCENE"] = randScene

                parser["FIRST_SYNC_VALUE"] = firstSyncValue
                parser["SECOND_SYNC_VALUE"] = secondSyncValue
                parser["THIRD_SYNC_VALUE"] = thirdSyncValue
                parser["FOURTH_SYNC_VALUE"] = fourthSyncValue

                parser["FIRST_SYNC_TEXT"] = firstSyncText
                parser["SECOND_SYNC_TEXT"] = secondSyncText
                parser["THIRD_SYNC_TEXT"] = thirdSyncText
                parser["FOURTH_SYNC_TEXT"] = fourthSyncText

                parser["FRAMES_BETWEEN_VALUES"] = waitXFrames

                customScript = parser.compile(script) ?: error("Error: script is an invalid OSL script")
            } finally {

            }

            var currentChapter: Int = 0
            var currentRoom: Int = 0
            var currentScene: Int = 0

            val replaceInScript: UnaryOperator<LinScript> = UnaryOperator { scriptEntry: LinScript ->
                when (scriptEntry) {
                    is DR1LoadScriptEntry -> {
                        if (scriptEntry.chapter == randChapter && scriptEntry.room == randRoom && scriptEntry.scene == randScene) {
                            return@UnaryOperator DR1LoadScriptEntry(currentChapter, currentRoom, currentScene) as LinScript
                        } else {
                            return@UnaryOperator scriptEntry
                        }
                    }
                    is DR2LoadScriptEntry -> {
                        if (scriptEntry.chapter == randChapter && scriptEntry.room == randRoom && scriptEntry.scene == randScene) {
                            return@UnaryOperator DR2LoadScriptEntry(currentChapter, currentRoom, currentScene)
                        } else {
                            return@UnaryOperator scriptEntry
                        }
                    }
                    else -> return@UnaryOperator scriptEntry
                }
            }

            println("Compiling synchronisation scripts...")

            val compilingSynchronisationScriptTime = measureNanoTime {
                val scriptEntries = customScript.entries.toTypedArray()
                scripts.forEach { file ->
                    currentChapter = file.name.substring(1, 3).toInt()
                    currentRoom = file.name.substring(4, 7).toInt()
                    currentScene = file.name.substring(8, 11).toInt()

                    customScript.entries.clear()
                    customScript.addAll(scriptEntries.map(replaceInScript::apply))

                    FileOutputStream(file).use(customScript::compile)
                }
            }

            println("Compilation of synchronisation scripts took $compilingSynchronisationScriptTime ns")

            println("Synchronising on $firstSyncValue, $secondSyncValue, $thirdSyncValue, and $fourthSyncValue")

            //5a.   We begin listening out for any blocks of memory that have an int value of our initial synchronisation value, and store them in a candidates list
            //5b.   Alternatively, if any of our blocks of memory have an int value of our initial synchronisation value * 4, we store them in a second candidates list
            //5c.   If any block of memory ever ends up

            var newValue: Int
            var newCount: Int

            while (gameStateStart == null) {
                viableRegions.forEach { region ->
                    val (memory, kret, sizeRead) = memoryAccessor.readMemory(region.first.start, region.first.size)

                    if (memory == null || (kret != null && kret != KernReturn.KERN_SUCCESS) || (sizeRead ?: 0) <= 0) {
                        println("ERR: Synchronisation failed; attempted to read ${region.first.size} bytes at memory region 0x${region.first.start.toString(16)}, got $kret, $sizeRead")
                        return null
                    }

                    try {
                        memory.read(0, regionMemory, 0, sizeRead!!.toInt() / 2)

                        for (i in 0 until (sizeRead - (sizeRead % 2) - 1).toInt()) {
                            currentCount = (candidates[region.second.toInt()][i] and COUNT_MASK) shr 16
                            currentValue = (candidates[region.second.toInt()][i] and VALUE_MASK) shr 0
                            currentMemValue = ((regionMemory[i].toInt() and 0xFF shl 8) or (regionMemory[i + 1].toInt() and 0xFF))

                            newValue = currentValue
                            newCount = currentCount

                            if (currentValue == firstSyncValue && currentMemValue == secondSyncValue) {
                                if (currentCount == 3) {
                                    gameStateStart = region.first.start + i * 2 - GAME_STATE_OFFSET
                                    break
                                }

                                newValue = secondSyncValue
                                newCount = ++currentCount
                            } else if (currentValue == secondSyncValue && currentMemValue == thirdSyncValue) {
                                if (currentCount == 3) {
                                    gameStateStart = region.first.start + i * 2 - GAME_STATE_OFFSET
                                    break
                                }

                                newValue = thirdSyncValue
                                newCount = ++currentCount
                            } else if (currentValue == thirdSyncValue && currentMemValue == fourthSyncValue) {
                                if (currentCount == 3) {
                                    gameStateStart = region.first.start + i * 2 - GAME_STATE_OFFSET
                                    break
                                }

                                newValue = fourthSyncValue
                                newCount = ++currentCount
                            } else if (currentValue == fourthSyncValue && currentMemValue == firstSyncValue) {
                                if (currentCount == 3) {
                                    gameStateStart = region.first.start + i * 2 - GAME_STATE_OFFSET
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

                            candidates[region.second.toInt()][i] = ((newCount and VALUE_MASK) shl 16) or (newValue and VALUE_MASK)
                        }
                    } finally {
                        memoryAccessor.deallocateMemory(memory)
                    }
                }

                //Thread.sleep(10)
            }

            println("Found Sync Address: $gameStateStart")
            println("Tracking down text buffer...")

            Arrays.fill(candidates, 0)

//            var textBufferStart: Long? = null
//
//            while (textBufferStart == null) {
//                viableRegions.forEach { region ->
//                    val (memory, kret, sizeRead) = memoryAccessor.readMemory(region.first.start, region.first.size)
//
//                    if (memory == null || (kret != null && kret != KernReturn.KERN_SUCCESS) || (sizeRead ?: 0) <= 0) {
//                        println("ERR: Synchronisation failed; attempted to read ${region.first.size} bytes at memory region 0x${region.first.start.toString(16)}, got $kret, $sizeRead")
//                        return null
//                    }
//
//                    try {
//                        memory.read(0, regionMemory, 0, sizeRead!!.toInt() / 2)
//
//                        for (i in 0 until (sizeRead / 8).toInt()) {
//                            currentCount = (candidates[region.second.toInt()][i] and COUNT_MASK) shr 16
//                            currentValue = (candidates[region.second.toInt()][i] and VALUE_MASK) shr 0
//
//                            currentMemValue = when ((regionMemory[i * 4 + 0].toLong() shl 0) or
//                                    (regionMemory[i * 4 + 1].toLong() shl 16) or
//                                    (regionMemory[i * 4 + 2].toLong() shl 32) or
//                                    (regionMemory[i * 4 + 3].toLong() shl 48)) {
//                                firstSyncTextBE -> firstSyncValue
//                                firstSyncTextLE -> firstSyncValue
//
//                                secondSyncTextBE -> secondSyncValue
//                                secondSyncTextLE -> secondSyncValue
//
//                                thirdSyncTextBE -> thirdSyncValue
//                                thirdSyncTextLE -> thirdSyncValue
//
//                                fourthSyncTextBE -> fourthSyncValue
//                                fourthSyncTextLE -> fourthSyncValue
//
//                                0L -> 0
//
//                                else -> -1
//                            }
//
//                            newValue = currentValue
//                            newCount = currentCount
//
//                            if (currentValue == firstSyncValue && currentMemValue == secondSyncValue) {
//                                if (currentCount == 3) {
//                                    gameStateStart = region.first.start + i * 8
//                                    break
//                                }
//
//                                newValue = secondSyncValue
//                                newCount = ++currentCount
//                            } else if (currentValue == secondSyncValue && currentMemValue == thirdSyncValue) {
//                                if (currentCount == 3) {
//                                    gameStateStart = region.first.start + i * 8
//                                    break
//                                }
//
//                                newValue = thirdSyncValue
//                                newCount = ++currentCount
//                            } else if (currentValue == thirdSyncValue && currentMemValue == fourthSyncValue) {
//                                if (currentCount == 3) {
//                                    gameStateStart = region.first.start + i * 8
//                                    break
//                                }
//
//                                newValue = fourthSyncValue
//                                newCount = ++currentCount
//                            } else if (currentValue == fourthSyncValue && currentMemValue == firstSyncValue) {
//                                if (currentCount == 3) {
//                                    gameStateStart = region.first.start + i * 8
//                                    break
//                                }
//
//                                newValue = firstSyncValue
//                                newCount = ++currentCount
//                            } else if (currentValue == 0) {
//                                when (currentMemValue) {
//                                    firstSyncValue -> {
//                                        newValue = firstSyncValue
//                                        newCount = 1
//                                    }
//                                    secondSyncValue -> {
//                                        newValue = secondSyncValue
//                                        newCount = 1
//                                    }
//                                    thirdSyncValue -> {
//                                        newValue = thirdSyncValue
//                                        newCount = 1
//                                    }
//                                    fourthSyncValue -> {
//                                        newValue = fourthSyncValue
//                                        newCount = 1
//                                    }
//                                }
//                            } else if (currentValue != currentMemValue) {
//                                newValue = 0
//                                newCount = 0
//                            }
//
//                            candidates[region.second.toInt()][i] = ((newCount and VALUE_MASK) shl 16) or (newValue and VALUE_MASK)
//                        }
//                    } finally {
//                        memoryAccessor.deallocateMemory(memory)
//                    }
//                }
//
//                //Thread.sleep(10)
//            }

            return SpiralBridge(memoryAccessor, gameStateStart!!)
        } finally {
            println("Finished synchronising, returning original scripts")

            //6. Delete
            val returnTime = measureNanoTime {
                scriptsFolder.listFiles()
                        .filter { file -> file.isFile && file.name.matches(BACKED_UP_SCRIPT_REGEX) }
                        .forEach { file ->
                            val dest = File(scriptsFolder, file.name.substring(12))
                            Files.move(file.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        }
            }

            println("Returning original scripts took $returnTime ns")
            println("Done.")
        }
    }

    fun OpenSpiralLanguageParser.compileFromName(name: String): CustomLin? =
            retrieve(name)?.let { data -> compile(String(data)) }

    fun OpenSpiralLanguageParser.parseLocally(lines: String): ParsingResult<Any> {
        val headerRunner = BasicParseRunner<Any>(OpenSpiralHeader())

        var stack: List<Any>
        var script: String = lines.replace("\r\n", "\n")

        do {
            stack = headerRunner.run(script).valueStack.reversed()
            script = stack.joinToString("\n") { str -> (str as Array<*>)[1].toString().trim() }
        } while (stack.any { value -> (value as Array<*>)[0] != null })

        val runner = BasicParseRunner<Any>(OpenSpiralLanguage())
        return runner.run(script)
    }

    @Suppress("UNCHECKED_CAST")
    fun OpenSpiralLanguageParser.compile(input: String): CustomLin? {
        val parser = this
        val result = parseLocally(input)
        val stack = result.valueStack?.toList()?.asReversed() ?: return null

        if (result.hasErrors() || result.valueStack.isEmpty) {
            println("Compile error: \n\t${result.parseErrors.joinToString("\n\t") { error -> "$error (${error.startIndex}-${error.endIndex})" }}")
            return null
        }

        val customLin = customLin {
            stack.forEach { value ->
                if (value is List<*>) {
                    val drillBit = (value[0] as? SpiralDrillBit) ?: return@forEach
                    val head = drillBit.head
                    try {
                        val valueParams = value.subList(1, value.size).filterNotNull().toTypedArray()

                        val products = head.operate(parser, valueParams)

                        when (head.klass) {
                            LinScript::class -> add(products as LinScript)
                            Array<LinScript>::class -> addAll(products as Array<LinScript>)
                            Unit::class -> {
                            }
                        }
                    } catch (th: Throwable) {
                        throw IllegalArgumentException("Script line [${drillBit.script}] threw an error", th)
                    }
                }
            }
        }

        if (customLin.entries.isEmpty())
            return null

        return customLin
    }

    fun retrieve(name: String): ByteArray? =
            Synchronisation::class.java.classLoader.getResourceAsStream(name)?.use { stream -> stream.readBytes() }

    fun ByteArray.readInt64LE(index: Int = 0): Long {
        val a = this[index + 0].toLong()
        val b = this[index + 1].toLong()
        val c = this[index + 2].toLong()
        val d = this[index + 3].toLong()
        val e = this[index + 4].toLong()
        val f = this[index + 5].toLong()
        val g = this[index + 6].toLong()
        val h = this[index + 7].toLong()

        return (((b shl 8) or (a shl 0) shl 48)) or (((d shl 8) or (c shl 0)) shl 32) or
                (((f shl 8) or (e shl 0)) shl 16) or (((h shl 8) or (g shl 0)) shl 0)
    }

    fun ByteArray.readInt64BE(index: Int = 0): Long {
        val a = this[index + 0].toLong()
        val b = this[index + 1].toLong()
        val c = this[index + 2].toLong()
        val d = this[index + 3].toLong()
        val e = this[index + 4].toLong()
        val f = this[index + 5].toLong()
        val g = this[index + 6].toLong()
        val h = this[index + 7].toLong()

        return (((a shl 8) or (b shl 0) shl 48)) or (((c shl 8) or (d shl 0)) shl 32) or
                (((e shl 8) or (f shl 0)) shl 16) or (((g shl 8) or (h shl 0)) shl 0)
    }
}