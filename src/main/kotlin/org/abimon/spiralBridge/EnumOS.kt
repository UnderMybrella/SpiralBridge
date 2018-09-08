package org.abimon.spiralBridge

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

                            val dr = EnumGame.values().firstOrNull { game -> game.processNames.any { name -> parts[0].contains(name) } }
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

                            val dr = EnumGame.values().firstOrNull { game -> game.processNames.any { name -> parts[3].contains(name) } }
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