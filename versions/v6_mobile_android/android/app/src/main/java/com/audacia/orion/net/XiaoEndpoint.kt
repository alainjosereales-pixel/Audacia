package com.audacia.orion.net

data class XiaoEndpoint(
    val ip: String,
    val streamPort: Int = 81,
    val controlPort: Int = 80
) {
    val streamUrl: String get() = "http://$ip:$streamPort/stream"
    val controlUrl: String get() = "http://$ip:$controlPort"
}
