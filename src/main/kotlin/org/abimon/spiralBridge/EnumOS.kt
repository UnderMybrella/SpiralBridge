package org.abimon.spiralBridge

import org.abimon.colonelAccess.handle.MemoryAccessor
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.*
import java.util.concurrent.TimeUnit

enum class EnumOS {
    WINDOWS,
    MAC,
    LINUX,
    OTHER;

    companion object {
        val ourOS: EnumOS by lazy {
            val os = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH)
            return@lazy if (os.indexOf("mac") >= 0 || os.indexOf("darwin") >= 0) {
                MAC
            } else if (os.indexOf("win") >= 0) {
                WINDOWS
            } else if (os.indexOf("nux") >= 0) {
                LINUX
            } else {
                OTHER
            }
        }

        val UNIX = arrayOf(MAC, LINUX)
    }

    fun getDRGame(): Pair<Int, EnumGame>? {
        when(this) {
            WINDOWS -> {
                try {
                    val p = Runtime.getRuntime().exec("tasklist")
                    p.waitFor(5, TimeUnit.SECONDS)

                    val regex = "\\s+".toRegex()
                    BufferedReader(InputStreamReader(p.inputStream)).useLines { lines ->
                        lines.forEach { process ->
                            val parts = process.trim().split(regex, limit = 5)
                            if(parts.size < 3)
                                return@forEach

                            if (parts[1].trim().toIntOrNull() == MemoryAccessor.ourPID)
                                return@forEach

                            val dr = EnumGame.values().firstOrNull { game -> game.processNames.any { name -> parts[0].toLowerCase().contains(name.toLowerCase()) } }
                            if(dr != null)
                                return parts[1].trim().toInt() to dr
                        }
                    }
                } catch (err: Exception) {
                    err.printStackTrace()
                }
            }
            in UNIX -> {
                try {
                    val p = Runtime.getRuntime().exec("ps -e")
                    p.waitFor(5, TimeUnit.SECONDS)

                    val regex = "\\s+".toRegex()
                    BufferedReader(InputStreamReader(p.inputStream)).useLines { lines ->
                        lines.forEach { process ->
                            val parts = process.trim().split(regex, limit = 4)

                            if (parts[0].trim().toIntOrNull() == MemoryAccessor.ourPID)
                                return@forEach

                            val dr = EnumGame.values().firstOrNull { game -> game.processNames.any { name -> parts[3].toLowerCase().contains(name.toLowerCase()) } }
                            if(dr != null)
                                return parts[0].trim().toInt() to dr
                        }
                    }
                } catch (err: Exception) {
                    err.printStackTrace()
                }
            }
        }

        return null
    }
}