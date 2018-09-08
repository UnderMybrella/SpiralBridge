package org.abimon.spiralBridge

typealias SpiralBridgeListener=(SpiralBridgeData<*>) -> Any

fun <T> T.offerTo(func: (T) -> Any) { func(this) }