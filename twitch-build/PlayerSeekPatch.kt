package io.github.bakwudo.uyu.patches.twitch.player

import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import io.github.bakwudo.uyu.patches.twitch.shared.Constants.COMPATIBILITY_TWITCH
import io.github.bakwudo.uyu.patches.twitch.shared.sharedExtensionPatch

private const val SUPPORT = "Lio/github/bakwudo/uyu/extension/player/PlayerSupport;"

internal val playerSeekPatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_TWITCH)
    dependsOn(sharedExtensionPatch)

    execute {
        var candidates = 0

        classDefForEach { classDef ->
            val mutableClass = mutableClassDefBy(classDef)
            mutableClass.methods.forEach { method ->
                // FIX: Skip abstract/native methods that have no instructions
                if (method.instructions == null) return@forEach
                
                val instructions = method.instructions.toList()

                val rewinds = instructions.withIndex().filter { indexed ->
                    (indexed.value as? NarrowLiteralInstruction)?.narrowLiteral == -10
                }
                val forwards = instructions.withIndex().filter { indexed ->
                    (indexed.value as? NarrowLiteralInstruction)?.narrowLiteral == 30
                }

                if (rewinds.size != 1 || forwards.size != 1) return@forEach

                val hasIntegerBox = instructions.any { instruction ->
                    val reference =
                        (instruction as? ReferenceInstruction)?.reference as? MethodReference
                    reference?.definingClass == "Ljava/lang/Integer;" &&
                        reference.name == "valueOf" &&
                        reference.parameterTypes.map { it.toString() } == listOf("I") &&
                        reference.returnType == "Ljava/lang/Integer;"
                }
                if (!hasIntegerBox) return@forEach

                val hasSubject = instructions.any { instruction ->
                    val reference =
                        (instruction as? ReferenceInstruction)?.reference as? MethodReference
                    reference?.name == "onNext"
                }
                if (!hasSubject) return@forEach

                fun replace(index: Int, getter: String) {
                    val register =
                        (instructions[index] as? OneRegisterInstruction)?.registerA
                            ?: throw PatchException(
                                "Kizu player seek: target literal is not a one-register instruction."
                            )
                    method.replaceInstruction(
                        index,
                        "invoke-static {}, $SUPPORT->$getter()I\nmove-result v$register",
                    )
                }

                replace(rewinds[0].index, "getRewindSeek")
                replace(forwards[0].index, "getForwardSeek")
                candidates++
            }
        }

        if (candidates == 0) {
            throw PatchException(
                "Kizu player seek: the Twitch fast-seek method with -10/+30 was not found."
            )
        }
    }
}
