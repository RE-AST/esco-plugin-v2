package plugin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object KVars {
    val globalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val eventsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}