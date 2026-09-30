/*
 * Adapted from "7TV and BTTV emotes" in hoomans-morphe-patches by arandomhooman (GPLv3).
 * This version uses Kizu's shared extension and a safer one-register chat-row hook.
 */
package io.github.bakwudo.uyu.patches.twitch.emotes

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import io.github.bakwudo.uyu.patches.twitch.shared.Constants.COMPATIBILITY_TWITCH
import io.github.bakwudo.uyu.patches.twitch.shared.sharedExtensionPatch

private const val EXTENSION = "Lapp/morphe/extension/twitch/emotes/EmoteSupport;"

internal val thirdPartyEmotesPatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_TWITCH)
    dependsOn(sharedExtensionPatch)

    execute {
        val channelConstructor = ChannelConnectionConstructorFingerprint.method
        val channelInstructions = channelConstructor.instructions
        if (channelInstructions.lastOrNull()?.opcode != Opcode.RETURN_VOID) {
            throw PatchException("Kizu emotes: channel connection constructor no longer ends in return-void.")
        }
        channelConstructor.addInstructions(
            channelInstructions.lastIndex,
            "invoke-static { p1, p2 }, $EXTENSION->onChannelChanged(Ljava/lang/String;Ljava/lang/String;)V",
        )

        val messageClass = MessageRecyclerItemClassFingerprint.classDef

        fun isChatBindMethod(method: Method): Boolean {
            val instructions = method.implementation?.instructions ?: return false
            return method.returnType == "V" &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[0].toString() == messageClass.type &&
                method.parameterTypes[1].toString() == "Z" &&
                instructions.count { instruction ->
                    val reference =
                        (instruction as? ReferenceInstruction)?.reference as? MethodReference
                    reference?.toString() == CHAT_TEXT_SETTER
                } == 1
        }

        val rowClassDef = classDefByStrings("glideTarget")
            .singleOrNull { classDef ->
                val hasContextPin = classDef.methods.any { method ->
                    method.implementation?.instructions?.any { instruction ->
                        ((instruction as? ReferenceInstruction)?.reference as? StringReference)?.string ==
                            "getApplicationContext(...)"
                    } == true
                }
                hasContextPin && classDef.methods.any(::isChatBindMethod)
            } ?: throw PatchException(
                "Kizu emotes: chat row holder was not found uniquely.",
            )

        val rowClass = mutableClassDefBy(rowClassDef)
        val bindMethod = rowClass.methods.singleOrNull(::isChatBindMethod)
            ?: throw PatchException("Kizu emotes: chat row bind method was not found uniquely.")

        val textCalls = bindMethod.instructions.withIndex().filter { (_, instruction) ->
            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            reference?.toString() == CHAT_TEXT_SETTER
        }.toList()
        if (textCalls.size != 1) {
            throw PatchException(
                "Kizu emotes: expected one chat TextView.setText call, found ${textCalls.size}.",
            )
        }

        val textCall = textCalls.single()
        val registers = textCall.value as? FiveRegisterInstruction
            ?: throw PatchException("Kizu emotes: chat TextView.setText is not a 35c invoke.")
        if (registers.registerCount != 3) {
            throw PatchException("Kizu emotes: unexpected TextView.setText register count.")
        }

        val textViewRegister = registers.registerC
        bindMethod.addInstructions(
            textCall.index + 1,
            """
                invoke-static/range { v$textViewRegister .. v$textViewRegister }, $EXTENSION->bind(Landroid/widget/TextView;)V
            """,
        )
    }
}
