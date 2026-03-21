package plugin

import kotlinx.coroutines.*

object KVars {
    val globalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val eventsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}