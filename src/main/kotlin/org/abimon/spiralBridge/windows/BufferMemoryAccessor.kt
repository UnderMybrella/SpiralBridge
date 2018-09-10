package org.abimon.spiralBridge.windows

import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.LongByReference
import org.abimon.colonelAccess.osx.KernReturn
import org.abimon.colonelAccess.osx.MacOSPointer
import org.abimon.colonelAccess.osx.SystemB
import org.abimon.colonelAccess.osx.VMInherit
import org.abimon.colonelAccess.win.Colonel32
import org.abimon.colonelAccess.win.WinMemory
import org.abimon.colonelAccess.win.WindowsMemoryAccessor

class BufferMemoryAccessor(pid: Int) : WindowsMemoryAccessor(pid) {
    protected val remappedRegions: MutableMap<Long, Array<WinMemory>> = HashMap()

    override fun readMemory(address: Long, size: Long): Triple<WinMemory?, Int?, Long?> {
        val (region, regionError) = getNextRegion(address)
        if (region == null)
            return Triple(null, regionError, null)

        val mappedRegion = remappedRegions[region.start]?.firstOrNull { mem -> mem.size() == size }

        if (mappedRegion == null) {
            val readMemory = super.readMemory(address, size)

            if (readMemory.first != null && (readMemory.third ?: 0) > 0L)
                remappedRegions[region.start] = (remappedRegions[region.start] ?: emptyArray()).plus(readMemory.first!!)

            return readMemory
        }

        val read = IntByReference()

        if (Colonel32.INSTANCE.ReadProcessMemory(process, Pointer(baseAddress + address), mappedRegion, size.toInt(), read))
            return Triple(mappedRegion, null, read.value.toLong())
        else if (Colonel32.INSTANCE.GetLastError() == 299) { //ERROR_PARTIAL_COPY
            mappedRegion.readSize = read.value.toLong()
            return Triple(mappedRegion, null, read.value.toLong())
        }

        return Triple(null, Colonel32.INSTANCE.GetLastError(), 0L)
    }

    override fun deallocateMemory(pointer: Pointer): Int? = null
    override fun deallocateOurMemory(pointer: WinMemory): Int? = null

    fun demapRegion(pointer: WinMemory) {
        remappedRegions.values.filter { array -> pointer in array }.forEach { array -> array.toList().minus(pointer).toTypedArray() }
        pointer.dispose()
    }
}