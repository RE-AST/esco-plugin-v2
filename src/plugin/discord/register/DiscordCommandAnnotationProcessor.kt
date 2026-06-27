package plugin.discord.register

import arc.util.CommandHandler
import arc.util.Log
import net.dv8tion.jda.api.EmbedBuilder
import plugin.discord.Context
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.jvmErasure

/**
 * Scans a listener object's member functions for [DiscordCommand] annotations and registers them
 * as [arc.util.CommandHandler.CommandRunner] instances on the appropriate command handlers.
 *
 * Usage:
 * ```kotlin
 * DiscordCommandAnnotationProcessor.registerCommands(Commands(), PVars.discordCommands, PVars.globalCommands)
 * ```
 */
object DiscordCommandAnnotationProcessor {
    /**
     * Registers all [DiscordCommand]-annotated functions found on [listener] with the provided handlers.
     *
     * For each annotated function the processor:
     * 1. Checks that the invoking user holds the required [DiscordCommand.requiredPerm]; silently
     *    ignores the call if not.
     * 2. Invokes the function via reflection, resolving the instance, `Array<String>`, and
     *    [plugin.discord.Context] parameters automatically.
     * 3. Unwraps [java.lang.reflect.InvocationTargetException] so that exceptions thrown inside
     *    the command body are handled correctly.
     * 4. Catches [DiscordCommandException] and replies with a red "Error" embed containing the
     *    exception message.
     * 5. Catches any other [Exception], replies with a red "Internal Error" embed, and logs the
     *    stack trace.
     *
     * The target handler(s) are selected according to [DiscordCommand.type]:
     * - [CommandType.DEFAULT] → [defaultCommandHandler] only
     * - [CommandType.GLOBAL]  → [globalCommandListener] only
     * - [CommandType.ALL]     → both handlers
     *
     * @param listener The object whose annotated member functions are registered as commands.
     * @param defaultCommandHandler The guild-specific [CommandHandler] (prefix-based bot commands).
     * @param globalCommandListener The global [CommandHandler] (cross-guild or admin commands).
     */
    fun registerCommands(listener: Any, defaultCommandHandler: CommandHandler, globalCommandListener: CommandHandler) {
        for (function in listener::class.memberFunctions) {
            if (function.parameters.isEmpty()) continue
            val annotation = function.findAnnotation<DiscordCommand>() ?: continue
            val handler = CommandHandler.CommandRunner { args: Array<String>, ctx: Context ->
                if (!ctx.hasPerm(annotation.requiredPerm)) {
                    return@CommandRunner
                }
                try {
                    val instanceParam = function.instanceParameter
                        ?: throw IllegalStateException("Function ${function.name} must be a class member")

                    val contextParam = function.parameters.find { it.type.jvmErasure == Context::class }
                        ?: throw IllegalStateException("Function ${function.name} missing Context parameter")

                    val argsParam = function.parameters.filter { it.kind == KParameter.Kind.VALUE }
                        .find {
                            it.type.jvmErasure.java.isArray || it.type.classifier == Array::class
                        }
                        ?: throw IllegalStateException("Function ${function.name} missing Array<String> parameter")

                    val params = mapOf(
                        instanceParam to listener,
                        contextParam to ctx,
                        argsParam to args
                    )
                    try {
                        function.callBy(params)
                    } catch (e: InvocationTargetException) {
                        throw e.cause ?: e
                    }

                } catch (e: DiscordCommandException) {
                    ctx.replyEmbed(EmbedBuilder().apply {
                        setColor(0xff0000)
                        setTitle("Error")
                        setDescription(e.message)
                    }.build())
                } catch (e: Exception) {
                    ctx.replyEmbed(EmbedBuilder().apply {
                        setColor(0xff0000)
                        setTitle("Internal Error")
                        setDescription("An internal error occurred while running this command.")
                    }.build())
                    Log.err(e)
                }
            }
            val targetHandlers = when (annotation.type) {
                CommandType.ALL -> listOf(defaultCommandHandler, globalCommandListener)
                CommandType.DEFAULT -> listOf(defaultCommandHandler)
                CommandType.GLOBAL -> listOf(globalCommandListener)
            }
            if (annotation.args.isEmpty()) {
                targetHandlers.forEach { it.register(annotation.name, annotation.desc, handler) }
            } else {
                targetHandlers.forEach { it.register(annotation.name, annotation.args, annotation.desc, handler) }
            }
        }
    }
}