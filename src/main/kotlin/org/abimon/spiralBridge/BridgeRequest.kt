package org.abimon.spiralBridge

sealed class BridgeRequest(val code: Int) {
    object TEXT_BUFFER_CLEAR: BridgeRequest(0)

    class UNK(code: Int): BridgeRequest(code)

    companion object {
        fun valueOf(code: Int): BridgeRequest = when (code) {
            0 -> TEXT_BUFFER_CLEAR
            else -> UNK(code)
        }
    }
}