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

internal val thirdPartyEmotePickerPatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_TWITCH)
    dependsOn(sharedExtensionPatch)

    execute {
        val builderMethod = EmotePickerStateBuilderFingerprint.method
        val returnIndex = builderMethod.instructions.indexOfLast { it.opcode == Opcode.RETURN_OBJECT }
        if (returnIndex < 0) {
            throw PatchException("Kizu emotes: state builder has no return-object.")
        }
        val ret = builderMethod.instructions.elementAt(returnIndex) as? OneRegisterInstruction
            ?: throw PatchException("Kizu emotes: return not single-register.")
        val reg = ret.registerA

        builderMethod.addInstructions(
            returnIndex,
            """
                invoke-static { v$reg }, $PICKER_BRIDGE->mergeGlobal(Ljava/lang/Object;)Ljava/lang/Object;
                move-result-object v$reg
            """.trimIndent(),
        )
    }
}
