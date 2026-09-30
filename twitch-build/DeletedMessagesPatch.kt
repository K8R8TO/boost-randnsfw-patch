package io.github.bakwudo.uyu.patches.twitch.chat

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import io.github.bakwudo.uyu.patches.twitch.shared.Constants.COMPATIBILITY_TWITCH
import io.github.bakwudo.uyu.patches.twitch.shared.sharedExtensionPatch

private const val SUPPORT = "Lio/github/bakwudo/uyu/extension/chat/ChatSupport;"

internal val showDeletedMessagesPatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_TWITCH)
    dependsOn(sharedExtensionPatch)

    execute {
        val spanClass = mutableClassDefBy(DeletedMessageSpanCtorFingerprint.classDef)
        val spanType = spanClass.type
        val accessField = spanClass.fields.singleOrNull { field -> field.type == "Z" }
            ?: throw PatchException("Kizu deleted messages: access flag field was not found uniquely.")
        val originalMessageField = spanClass.fields.singleOrNull { field ->
            field.type == "Landroid/text/SpannedString;"
        } ?: throw PatchException(
            "Kizu deleted messages: original-message field was not found uniquely.",
        )

        val constructor = DeletedMessageSpanCtorFingerprint.method
        constructor.addInstructions(
            constructor.instructions.lastIndex,
            """
                invoke-static {}, $SUPPORT->shouldShowDeletedMessages()Z
                move-result v0
                if-eqz v0, :kizu_deleted_constructor_done
                const/4 p3, 0x1
                iput-boolean p3, p0, $accessField
                :kizu_deleted_constructor_done
            """,
        )

        val formatter = DeletedMessageFormatterFingerprint.method
        val formatterInstructions = formatter.instructions
        val getSpansIndex = formatterInstructions.indexOfFirst { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            reference?.definingClass == "Landroid/text/SpannedString;" &&
                reference.name == "getSpans" &&
                reference.returnType == "[Ljava/lang/Object;"
        }
        if (getSpansIndex < 0) {
            throw PatchException("Kizu deleted messages: getSpans call was not found.")
        }
        val arrayLengthIndex = formatterInstructions.indices.firstOrNull { index ->
            index > getSpansIndex && formatterInstructions[index].opcode == Opcode.ARRAY_LENGTH
        } ?: throw PatchException(
            "Kizu deleted messages: existing-span array check was not found.",
        )
        val spanClassRegister = formatterInstructions.take(arrayLengthIndex)
            .lastOrNull { instruction ->
                instruction.opcode == Opcode.CONST_CLASS &&
                    ((instruction as? ReferenceInstruction)?.reference as? TypeReference)?.type == spanType
            } as? OneRegisterInstruction
            ?: throw PatchException(
                "Kizu deleted messages: span class register was not found.",
            )
        val arrayLength = formatter.getInstruction<TwoRegisterInstruction>(arrayLengthIndex)
        val arrayRegister = arrayLength.registerB
        val scratchRegister = spanClassRegister.registerA
        if (arrayRegister == scratchRegister) {
            throw PatchException("Kizu deleted messages: formatter scratch registers overlap.")
        }
        formatter.addInstructionsWithLabels(
            arrayLengthIndex,
            """
                invoke-static {}, $SUPPORT->shouldShowDeletedMessages()Z
                move-result v$scratchRegister
                if-eqz v$scratchRegister, :use_stock_array
                array-length v$scratchRegister, v$arrayRegister
                if-eqz v$scratchRegister, :use_stock_array
                const/4 v$scratchRegister, 0x0
                aget-object v$scratchRegister, v$arrayRegister, v$scratchRegister
                iget-object p1, v$scratchRegister, $originalMessageField
                const/4 p4, 0x1
                const/4 v$scratchRegister, 0x0
                new-array v$arrayRegister, v$scratchRegister, [$spanType
            """,
            ExternalLabel("use_stock_array", formatter.getInstruction(arrayLengthIndex)),
        )

        val accessReads = spanClass.methods.flatMap { method ->
            method.instructions.withIndex().mapNotNull { (index, instruction) ->
                val reference = (instruction as? ReferenceInstruction)?.reference as? FieldReference
                if (instruction.opcode == Opcode.IGET_BOOLEAN && reference == accessField) {
                    method to index
                } else null
            }
        }
        if (accessReads.size != 2) {
            throw PatchException(
                "Kizu deleted messages: expected two access-flag reads, found ${accessReads.size}.",
            )
        }
        accessReads.groupBy({ it.first }, { it.second }).forEach { (method, indexes) ->
            indexes.sortedDescending().forEachIndexed { offset, index ->
                val register = method.getInstruction<TwoRegisterInstruction>(index).registerA
                if (register > 0xf) {
                    throw PatchException(
                        "Kizu deleted messages: access-flag register no longer fits const/4.",
                    )
                }
                val label = ":kizu_deleted_access_done_${method.name}_${index}_${offset}"
                method.addInstructions(
                    index + 1,
                    """
                        invoke-static {}, $SUPPORT->shouldShowDeletedMessages()Z
                        move-result v$register
                        if-eqz v$register, $label
                        const/4 v$register, 0x1
                        $label
                    """,
                )
            }
        }
    }
}
