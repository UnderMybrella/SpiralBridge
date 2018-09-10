package org.abimon.spiralBridge

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Advapi32
import com.sun.jna.platform.win32.Advapi32Util
import org.abimon.colonelAccess.handle.MemoryAccessor
import org.abimon.colonelAccess.handle.MemoryRegion
import org.abimon.colonelAccess.osx.OSXMemoryAccessor
import org.abimon.colonelAccess.win.WindowsMemoryAccessor
import java.util.*

typealias SpiralBridgeListener = (SpiralBridgeData<*>) -> Any

fun <T> T.offerTo(func: (T) -> Any) {
    func(this)
}

fun <E : Any, P : Pointer> MemoryAccessor<E, P>.getAllViableDanganRegions(): Array<MemoryRegion> {
    return when (this) {
//        is WindowsMemoryAccessor -> getAllRegions()
//                .filter { region -> region.detail.equals(detail, true) } //Don't worry about reading, we handle that in the next step
//                .filter { region ->
//                    val readResponse = readMemory(region.start, region.size)
//
//                    try {
//                        return@filter (readResponse.third ?: 0) > 0
//                    } finally {
//                        if (readResponse.first != null)
//                            deallocateOurMemory(readResponse.first!!)
//                    }
//                }.toTypedArray()

        is WindowsMemoryAccessor -> getAllRegions()
                .filter { region -> region.size < 100 * 1000 * 1000 }
                .filter { region ->
                    val readResponse = readMemory(region.start, region.size)

                    try {
                        return@filter (readResponse.third ?: 0) > 0
                    } finally {
                        if (readResponse.first != null)
                            deallocateOurMemory(readResponse.first!!)
                    }
                }.let { viableRegions ->
                    val startAddr = viableRegions.first().start
                    var endAddr = viableRegions.last().let { region -> region.start + region.size }

                    for (i in 0 until viableRegions.size - 1) {
                        if (viableRegions[i].start + viableRegions[i].size < viableRegions[i + 1].start) {
                            endAddr = viableRegions[i].start + viableRegions[i].size
                            break
                        }
                    }

                    println("Is our test region within bounds? (0x${startAddr.toString(16)} <= 0x2E47E4 <= 0x${endAddr.toString(16)}): ${startAddr <= 0x2E47E4 && 0x2E47E4 <= endAddr}")

                    return@let arrayOf(MemoryRegion(startAddr, endAddr - startAddr, 1, detail))
                }

        else -> getAllRegions()
                .filter { region -> region.canWrite && (region.detail.isBlank() || region.detail == detail) } //We only want regions that are ours, or private (blank)
                .take(2) //We only want two
                .toTypedArray()
    }
}

fun enableDebugForWindows() {
    val os = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH)
    if (os.indexOf("mac") >= 0 || os.indexOf("darwin") >= 0) {
    } else if (os.indexOf("win") >= 0) {
        val privilege = Advapi32Util.Privilege("SeDebugPrivilege")

        privilege.enable()
    } else if (os.indexOf("nux") >= 0) {
    }
}
