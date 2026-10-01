package io.github.bakwudo.uyu.patches.twitch.player

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import io.github.bakwudo.uyu.patches.twitch.shared.Constants.COMPATIBILITY_TWITCH
import io.github.bakwudo.uyu.patches.twitch.shared.sharedExtensionPatch
import java.util.logging.Logger

private const val SUPPORT = "Lio/github/bakwudo/uyu/extension/player/PlayerSupport;"

private class SeekTarget(
    val classDef: ClassDef,
    val name: String,
    val returnType: String,
    val parameterTypes: List<String>,
    val rewindIndex: Int,
    val rewindRegister: Int,
    val forwardIndex: Int,
    val forwardRegister: Int,
)

/**
 * Replaces the hard-coded -10 / +30 second values of Twitch's fast-seek with the Kizu settings.
 *
 * Two bugs from the previous version are fixed here:
 *  - `method.instructions` is `implementation!!.instructions` in Morphe, so comparing it to null
 *    threw a NullPointerException on abstract/native methods. Methods are now skipped by checking
 *    `method.implementation` instead.
 *  - `replaceInstruction(index, "a\nb")` only keeps the first instruction, which dropped the
 *    `move-result`. `replaceInstructions` keeps both.
 *
 * If the expected method is not found the patch logs a warning and does nothing, so it can never
 * stop the rest of the bundle from being applied.
 */
internal val playerSeekPatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_TWITCH)
    dependsOn(sharedExtensionPatch)

    execute {
        val targets = mutableListOf<SeekTarget>()

        // Phase 1: read-only scan. No class is made mutable and nothing is modified here.
        classDefForEach { classDef ->
            classDef.methods.forEach { method ->
                val implementation = method.implementation ?: return@forEach
                val instructions = implementation.instructions.toList()

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

                val rewindRegister =
                    (rewinds[0].value as? OneRegisterInstruction)?.registerA ?: return@forEach
                val forwardRegister =
                    (forwards[0].value as? OneRegisterInstruction)?.registerA ?: return@forEach

                targets.add(
                    SeekTarget(
                        classDef = classDef,
                        name = method.name,
                        returnType = method.returnType,
                        parameterTypes = method.parameterTypes.map { it.toString() },
                        rewindIndex = rewinds[0].index,
                        rewindRegister = rewindRegister,
                        forwardIndex = forwards[0].index,
                        forwardRegister = forwardRegister,
                    ),
                )
            }
        }

        if (targets.isEmpty()) {
            Logger.getLogger("Kizu").warning(
                "Kizu player seek: the Twitch fast-seek method with -10/+30 was not found; skipping.",
            )
            return@execute
        }

        // Phase 2: modify. Replace the later index first so the earlier index stays valid.
        targets.forEach { target ->
            val mutableClass = mutableClassDefBy(target.classDef)
            val method = mutableClass.methods.first { candidate ->
                candidate.name == target.name &&
                    candidate.returnType == target.returnType &&
                    candidate.parameterTypes.map { it.toString() } == target.parameterTypes
            }

            val edits = listOf(
                Triple(target.rewindIndex, target.rewindRegister, "getRewindSeek"),
                Triple(target.forwardIndex, target.forwardRegister, "getForwardSeek"),
            ).sortedByDescending { it.first }

            edits.forEach { (index, register, getter) ->
                method.replaceInstructions(
                    index,
                    "invoke-static {}, $SUPPORT->$getter()I\nmove-result v$register",
                )
            }
        }
    }
}
