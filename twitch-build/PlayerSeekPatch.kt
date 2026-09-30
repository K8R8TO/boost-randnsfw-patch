package io.github.bakwudo.uyu.patches.twitch.player

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
            classDef.methods.forEach { method ->
                val instructions = method.instructions.toList()
                val constants = instructions.withIndex().filter { (_, instruction) ->
                    val literal = (instruction as? NarrowLiteralInstruction)?.narrowLiteral
                    literal == -10L || literal == 30L
                }
                if (constants.isEmpty()) return@forEach
                val hasIntegerBox = instructions.any { instruction ->
                    val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                    reference?.definingClass == "Ljava/lang/Integer;" &&
                        reference.name == "valueOf" &&
                        reference.parameterTypes.map { it.toString() } == listOf("I") &&
                        reference.returnType == "Ljava/lang/Integer;"
                }
                if (!hasIntegerBox) return@forEach
                if (!instructions.any { (it as? ReferenceInstruction)?.reference is MethodReference &&
                        ((it as ReferenceInstruction).reference as MethodReference).name == "onNext" }) {
                    return@forEach
                }

                val rewinds = constants.filter {
                    (it.value as NarrowLiteralInstruction).narrowLiteral == -10L
                }
                val forwards = constants.filter {
                    (it.value as NarrowLiteralInstruction).narrowLiteral == 30L
                }
                if (rewinds.size != 1 || forwards.size != 1) return@forEach
                candidates++

                fun replace(index: Int, getter: String) {
                    val register = (instructions[index] as? OneRegisterInstruction)?.registerA
                        ?: throw PatchException("Kizu player seek: literal does not use one register.")
                    method.replaceInstruction(
                        index,
                        "invoke-static {}, $SUPPORT->$getter()I\nmove-result v$register",
                    )
                }

                replace(rewinds[0].index, "getRewindSeek")
                replace(forwards[0].index, "getForwardSeek")
            }
        }
        if (candidates == 0) {
            throw PatchException("Kizu player seek: the -10/+30 fast-seek method was not found.")
        }
    }
}
