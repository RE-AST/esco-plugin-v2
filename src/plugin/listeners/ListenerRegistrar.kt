package plugin.listeners

import arc.Events
import kotlin.collections.get
import kotlin.reflect.KClass
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberFunctions

object ListenerRegistrar {

    fun register(listener: Any) {
        for (function in listener::class.memberFunctions) {

            if (!function.hasAnnotation<EventListener>())
                continue

            if (function.parameters.size != 2)
                continue

            val eventParameter = function.parameters[1]
            val eventClass =
                eventParameter.type.classifier as? KClass<*>
                    ?: continue

            Events.on(eventClass.java) { event ->
                function.call(listener, event)
            }
        }
    }
}