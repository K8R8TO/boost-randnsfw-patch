package io.github.bakwudo.uyu.patches.twitch.enhancement

import app.morphe.patcher.patch.bytecodePatch
import io.github.bakwudo.uyu.patches.twitch.ads.blockAdsPatch
import io.github.bakwudo.uyu.patches.twitch.appearance.hidePromotionsPatch
import io.github.bakwudo.uyu.patches.twitch.chat.showDeletedMessagesPatch
import io.github.bakwudo.uyu.patches.twitch.emotes.thirdPartyEmotesPatch
import io.github.bakwudo.uyu.patches.twitch.login.fixLoginPatch
import io.github.bakwudo.uyu.patches.twitch.notifications.fixNotificationsPatch
import io.github.bakwudo.uyu.patches.twitch.player.playerSeekPatch
import io.github.bakwudo.uyu.patches.twitch.privacy.privacyPatch
import io.github.bakwudo.uyu.patches.twitch.settings.settingsPatch
import io.github.bakwudo.uyu.patches.twitch.shared.Constants.COMPATIBILITY_TWITCH

@Suppress("unused")
val twitchEnhancementPatch = bytecodePatch(
    name = "Twitch Enhancement",
    description = "Kizu: third-party emotes, deleted messages, player seek, ad blocking, privacy, " +
        "and patched-app login/notification compatibility.",
) {
    compatibleWith(COMPATIBILITY_TWITCH)
    dependsOn(
        settingsPatch,
        fixLoginPatch,
        fixNotificationsPatch,
        blockAdsPatch,
        hidePromotionsPatch,
        thirdPartyEmotesPatch,
        showDeletedMessagesPatch,
        playerSeekPatch,
        privacyPatch,
    )
}
