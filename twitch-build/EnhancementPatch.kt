package io.github.bakwudo.uyu.patches.twitch.enhancement

import app.morphe.patcher.patch.bytecodePatch
import io.github.bakwudo.uyu.patches.twitch.ads.blockAdsPatch
import io.github.bakwudo.uyu.patches.twitch.appearance.hidePromotionsPatch
import io.github.bakwudo.uyu.patches.twitch.emotes.thirdPartyEmotesPatch
import io.github.bakwudo.uyu.patches.twitch.login.fixLoginPatch
import io.github.bakwudo.uyu.patches.twitch.notifications.fixNotificationsPatch
import io.github.bakwudo.uyu.patches.twitch.settings.settingsPatch
import io.github.bakwudo.uyu.patches.twitch.shared.Constants.COMPATIBILITY_TWITCH

/**
 * Kizu's single user-facing Twitch patch.
 *
 * Real hooks currently included:
 * - uyu ad blocking
 * - uyu chat/Bits visibility hook
 * - login and notification compatibility fixes
 *
 * Other Kizu settings stay disabled/not advertised until their hooks are implemented.
 */
@Suppress("unused")
val twitchEnhancementPatch = bytecodePatch(
    name = "Twitch Enhancement",
    description = "Kizu's single configurable Twitch enhancement patch. Current real hooks include " +
        "ad blocking and the Bits-button " +
        "visibility control, and patched-app login/notification compatibility.",
) {
    compatibleWith(COMPATIBILITY_TWITCH)
    dependsOn(
        settingsPatch,
        fixLoginPatch,
        fixNotificationsPatch,
        blockAdsPatch,
        hidePromotionsPatch,
    )
}
