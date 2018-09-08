package org.abimon.spiralBridge

import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.LongByReference
import org.abimon.colonelAccess.osx.*

open class RemapMemoryAccessor(pid: Int) : OSXMemoryAccessor(pid) {
    protected val remappedRegions: MutableMap<Long, MacOSPointer> = HashMap()

    override fun readMemory(address: Long, size: Long): Pair<MacOSPointer?, KernReturn?> {
        val (region, regionKret) = getNextRegion(address)
        if (region == null)
            return null to regionKret

        if (remappedRegions[region.start] == null) {
            val newAddress = LongByReference(0)

            val curProtection = IntByReference()
            val maxProtection = IntByReference()

            val kret = KernReturn.valueOf(SystemB.INSTANCE.mach_vm_remap(ourTask, newAddress, region.size, 0L, 1, task, region.start, false, curProtection, maxProtection, VMInherit.VM_INHERIT_SHARE.code) and 0x000000FF)

            if (kret != KernReturn.KERN_SUCCESS) {
                println("Mapping failed, error: $kret")
                return super.readMemory(address, size)
            }

            remappedRegions[region.start] = MacOSPointer(newAddress.value, region.size)
        }

        return MacOSPointer(Pointer.nativeValue(remappedRegions[region.start]) + (address - region.start), size) to KernReturn.KERN_SUCCESS
    }

    override fun deallocateMemory(pointer: Pointer): KernReturn? = null
    override fun deallocateOurMemory(pointer: MacOSPointer): KernReturn? = null
}