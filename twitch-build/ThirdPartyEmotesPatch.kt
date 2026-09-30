/*
 * Adapted from "7TV and BTTV emotes" in hoomans-morphe-patches by arandomhooman (GPLv3).
 * Kizu v0.2.1 targets Twitch 31.3.1's stable MessageRecyclerItem$ViewHolder class directly.
 */
package io.github.bakwudo.uyu.patches.twitch.emotes

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import io.github.bakwudo.uyu.patches.twitch.shared.Constants.COMPATIBILITY_TWITCH
import io.github.bakwudo.uyu.patches.twitch.shared.sharedExtensionPatch

private const val EXTENSION = "Lapp/morphe/extension/twitch/emotes/EmoteSupport;"
private const val CHAT_ROW_HOLDER =
    "Ltv/twitch/android/shared/chat/messages/ui/MessageRecyclerItem\$ViewHolder;"
private const val RECYCLER_ADAPTER_ITEM =
    "Ltv/twitch/android/core/adapters/RecyclerAdapterItem;"

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

        val rowClass = mutableClassDefBy(CHAT_ROW_HOLDER)
        val bindMethod = rowClass.methods.singleOrNull { method ->
            method.name == "onBindDataItem" &&
                method.returnType == "V" &&
                method.parameterTypes.map { it.toString() } == listOf(RECYCLER_ADAPTER_ITEM)
        } ?: throw PatchException(
            "Kizu emotes: MessageRecyclerItem.ViewHolder.onBindDataItem was not found uniquely.",
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
                "Kizu emotes: no TextView.setText call found in MessageRecyclerItem.ViewHolder.onBindDataItem.",
            )
        }

        // Use the last setText call in the binder: this is the final rendered chat message text.
        val textCall = textCalls.last()
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
