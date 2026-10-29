package com.wifivault.app.service

import com.wifivault.app.server.VaultHttpServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 服务与界面之间共享的运行状态（同一进程内的轻量状态容器）。 */
object ServerState {

    private val _running = MutableStateFlow(false)
    val running = _running.asStateFlow()

    private val _port = MutableStateFlow(0)
    val port = _port.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _address = MutableStateFlow<String?>(null)
    val address = _address.asStateFlow()

    fun setRunning(value: Boolean) = _running.tryEmit(value)
    fun setPort(value: Int) = _port.tryEmit(value)
    fun setError(value: String?) = _error.tryEmit(value)
    fun setAddress(value: String?) = _address.tryEmit(value)
}

object ServerHolder {
    @Volatile
    var server: VaultHttpServer? = null
}
