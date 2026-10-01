package io.github.bakwudo.uyu.patches.twitch.emotes

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import io.github.bakwudo.uyu.patches.twitch.shared.Constants.COMPATIBILITY_TWITCH
import io.github.bakwudo.uyu.patches.twitch.shared.sharedExtensionPatch

private const val PICKER_BRIDGE = "Lapp/morphe/extension/twitch/emotes/EmotePickerBridge;"

/**
 * Adds third-party emotes (7TV / BTTV / FFZ) into Twitch's native emote picker.
 *
 * Hook 1: `oqf.H2(Tuid, qqf)` captures the current channel ID when the picker opens.
 * Hook 2: `oqf.G2(esf, Integer, qqf)` appends third-party emote entries to the
 *         assembled EmoteUiSet just before it is returned.
 *
 * Both hooks are no-ops when the bridge has no emotes for the current channel, so
 * picker behaviour matches stock Twitch whenever third-party emotes are disabled
 * or not yet loaded.
 */
internal val thirdPartyEmotePickerPatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_TWITCH)
    dependsOn(sharedExtensionPatch)

    execute {
        // ---- Hook 1: channel capture on picker open ----
        val openMethod = EmotePickerOpenFingerprint.method
        openMethod.addInstructions(
            0,
            "invoke-static { p1 }, $PICKER_BRIDGE->onPickerOpened(Ljava/lang/Object;)V",
        )

        // ---- Hook 2: append third-party emotes before the state builder returns ----
        val builderMethod = EmotePickerStateBuilderFingerprint.method
        val returnIndex = builderMethod.instructions.indexOfLast { it.opcode == Opcode.RETURN_OBJECT }
        if (returnIndex < 0) {
            throw PatchException("Kizu emotes: emote picker state builder has no return-object.")
        }

        val returnInstruction = builderMethod.instructions.elementAt(returnIndex)
        if (returnInstruction !is OneRegisterInstruction) {
            throw PatchException("Kizu emotes: emote picker state builder return is not a single-register instruction.")
        }
        val returnRegister = returnInstruction.registerA

        builderMethod.addInstructions(
            returnIndex,
            """
                invoke-static { v$returnRegister }, $PICKER_BRIDGE->mergeInto(Ljava/lang/Object;)Ljava/lang/Object;
                move-result-object v$returnRegister
            """.trimIndent(),
        )
    }
}
