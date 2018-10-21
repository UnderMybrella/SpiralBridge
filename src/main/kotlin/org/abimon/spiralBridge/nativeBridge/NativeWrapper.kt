package org.abimon.spiralBridge.nativeBridge

import com.sun.jna.Pointer
import org.abimon.colonelAccess.handle.MemoryAccessor
import java.util.*

abstract class NativeWrapper<T: Pointer, E: Any>(val memoryAccessor: MemoryAccessor<E, T>) {
    companion object {
        fun <T: Pointer, E: Any> obtainFor(memoryAccessor: MemoryAccessor<E, T>): NativeWrapper<T, E> {
            val os = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH)
            if (os.indexOf("mac") >= 0 || os.indexOf("darwin") >= 0) {
                //return MacOSNativeWrapper(memoryAccessor)
            } else if (os.indexOf("win") >= 0) {
            } else if (os.indexOf("nux") >= 0) {
            }

            return FallbackNativeWrapper(memoryAccessor)
        }
    }

    abstract fun benchmark(): List<Long>
    abstract fun findGameStart(firstSyncValue: Int, secondSyncValue: Int, thirdSyncValue: Int, fourthSyncValue: Int): Long?
    abstract fun findTextBufferStart(firstSyncTextLE: Long, secondSyncTextLE: Long, thirdSyncTextLE: Long, fourthSyncTextLE: Long, firstSyncValue: Int, secondSyncValue: Int, thirdSyncValue: Int, fourthSyncValue: Int): Long?
}