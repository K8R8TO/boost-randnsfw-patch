package io.github.bakwudo.uyu.patches.twitch.emotes

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

// Twitch 31.3.1 obfuscated names. Must be re-verified against the target APK
// whenever Kizu adopts a new Twitch version.
internal const val EMOTE_PICKER_PRESENTER_CLASS = "Ldefpackage/oqf;"
internal const val EMOTE_PICKER_STATE_BUILDER_RETURN = "Ldefpackage/mtf;"
internal const val EMOTE_PICKER_EMOTE_SET = "Ldefpackage/esf;"
internal const val EMOTE_PICKER_SECTION = "Ldefpackage/qqf;"
internal const val EMOTE_PICKER_TUID = "Ltv/twitch/android/models/Tuid;"

/** The emote picker presenter (obfuscated as `oqf` in 31.3.1). */
internal object EmotePickerPresenterClassFingerprint : Fingerprint(
    strings = listOf(
        "createEmotePickerState",
        "actionHandler",
    ),
    custom = { _, classDef -> classDef.type == EMOTE_PICKER_PRESENTER_CLASS },
)

/** `EmotePickerPresenter.G2(EmoteSet, Integer, EmotePickerSection) -> EmoteUiSet` */
internal object EmotePickerStateBuilderFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = EMOTE_PICKER_STATE_BUILDER_RETURN,
    parameters = listOf(
        EMOTE_PICKER_EMOTE_SET,
        "Ljava/lang/Integer;",
        EMOTE_PICKER_SECTION,
    ),
    custom = { _, classDef -> classDef.type == EMOTE_PICKER_PRESENTER_CLASS },
)

/** `EmotePickerPresenter.H2(Tuid, EmotePickerSection) -> void` */
internal object EmotePickerOpenFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(
        EMOTE_PICKER_TUID,
        EMOTE_PICKER_SECTION,
    ),
    custom = { _, classDef -> classDef.type == EMOTE_PICKER_PRESENTER_CLASS },
)
