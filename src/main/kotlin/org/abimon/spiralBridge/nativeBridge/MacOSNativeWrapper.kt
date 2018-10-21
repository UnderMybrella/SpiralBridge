package org.abimon.spiralBridge.nativeBridge

import com.sun.jna.Pointer
import org.abimon.colonelAccess.handle.MemoryAccessor
import org.abimon.spiral.core.utils.copyToStream
import java.io.File
import java.io.FileOutputStream
import java.util.*

class MacOSNativeWrapper<T : Pointer, E : Any>(memoryAccessor: MemoryAccessor<E, T>) : FallbackNativeWrapper<T, E>(memoryAccessor) {
    companion object {
        val resourcePath = "macos_x64/SpiralBridgeNativeMacOSRun.kexe"
        val tmpExecutable: File

        init {
            tmpExecutable = File.createTempFile(UUID.randomUUID().toString(), ".kexe")
            tmpExecutable.setExecutable(true)
            tmpExecutable.deleteOnExit()

            this::class.java.classLoader.getResourceAsStream(resourcePath)?.use { stream -> FileOutputStream(tmpExecutable).use(stream::copyToStream) }
        }
    }

    override fun benchmark(): List<Long> {
        val process = ProcessBuilder(tmpExecutable.absolutePath, "-B", memoryAccessor.pid.toString()).start()
        if (process.waitFor() == 0)
            return String(process.inputStream.readBytes()).split(',').map(String::trim).mapNotNull(String::toLongOrNull)
        return emptyList()
    }
}