/*
 * Adapted from "7TV and BTTV emotes" in hoomans-morphe-patches by arandomhooman (GPLv3).
 * Kizu v0.2.1 targets Twitch 31.3.1's MessageRecyclerItem chat holder directly, with a
 * structural fallback if the nested class name changes.
 */
package io.github.bakwudo.uyu.patches.twitch.emotes

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import io.github.bakwudo.uyu.patches.twitch.shared.Constants.COMPATIBILITY_TWITCH
import io.github.bakwudo.uyu.patches.twitch.shared.sharedExtensionPatch

private const val EXTENSION = "Lapp/morphe/extension/twitch/emotes/EmoteSupport;"
private const val CHAT_ROW_HOLDER =
    "Ltv/twitch/android/shared/chat/messages/ui/MessageRecyclerItem\$ViewHolder;"
private const val CHAT_MESSAGE_VIEW_HOLDER =
    "Ltv/twitch/android/shared/chat/messages/ui/ChatMessageViewHolder;"
private const val RECYCLER_ADAPTER_ITEM =
    "Ltv/twitch/android/core/adapters/RecyclerAdapterItem;"

private fun Method.isChatBindMethod(): Boolean {
    if (name != "onBindDataItem" ||
        returnType != "V" ||
        parameterTypes.map { it.toString() } != listOf(RECYCLER_ADAPTER_ITEM)
    ) return false

    return implementation?.instructions?.any { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
        reference?.definingClass == "Landroid/widget/TextView;" &&
            reference.name == "setText" &&
            reference.returnType == "V"
    } == true
}

internal val thirdPartyEmotesPatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_TWITCH)
    dependsOn(sharedExtensionPatch)

    execute {
        val channelConstructor = ChannelConnectionConstructorFingerprint.method
        val channelInstructions = channelConstructor.instructions
        if (channelInstructions.lastOrNull()?.opcode != Opcode.RETURN_VOID) {
            throw PatchException("Kizu emotes: channel connection constructor changed.")
        }
        channelConstructor.addInstructions(
            channelInstructions.lastIndex,
            "invoke-static { p1, p2 }, $EXTENSION->onChannelChanged(Ljava/lang/String;Ljava/lang/String;)V",
        )

        val direct = classDefByOrNull(CHAT_ROW_HOLDER)
        val rowClassDef: ClassDef = if (direct != null && direct.methods.any { it.isChatBindMethod() }) {
            direct
        } else {
            val candidates = mutableListOf<ClassDef>()
            classDefForEach { classDef ->
                if (classDef.superclass == CHAT_MESSAGE_VIEW_HOLDER &&
                    classDef.methods.any { it.isChatBindMethod() }
                ) {
                    candidates += classDef
                }
            }
            candidates.singleOrNull() ?: throw PatchException(
                "Kizu emotes: expected one ChatMessageViewHolder binder, found ${candidates.size}.",
            )
        }

        val rowClass = mutableClassDefBy(rowClassDef)
        val bindMethod = rowClass.methods.singleOrNull { it.isChatBindMethod() }
            ?: throw PatchException(
                "Kizu emotes: chat onBindDataItem was not found uniquely in ${rowClassDef.type}.",
            )

        val textCalls = bindMethod.instructions.withIndex().filter { (_, instruction) ->
            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            reference?.definingClass == "Landroid/widget/TextView;" &&
                reference.name == "setText" &&
                reference.returnType == "V" &&
                reference.parameterTypes.map { it.toString() } ==
                    listOf("Ljava/lang/CharSequence;", "Landroid/widget/TextView\$BufferType;")
        }.toList()

        if (textCalls.isEmpty()) {
            throw PatchException(
                "Kizu emotes: no TextView.setText call found in ${rowClassDef.type}->onBindDataItem.",
            )
        }

        val textCall = textCalls.last()
        val registers = textCall.value as? FiveRegisterInstruction
            ?: throw PatchException("Kizu emotes: final chat TextView.setText is not a 35c invoke.")
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
