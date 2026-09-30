package io.github.bakwudo.uyu.patches.twitch.enhancement

import io.github.bakwudo.uyu.patches.twitch.ads.blockAdsPatch
import io.github.bakwudo.uyu.patches.twitch.appearance.hidePromotionsPatch
import io.github.bakwudo.uyu.patches.twitch.chat.deletedMessagesPatch
import io.github.bakwudo.uyu.patches.twitch.emotes.thirdPartyEmotesPatch
import io.github.bakwudo.uyu.patches.twitch.login.fixLoginPatch
import io.github.bakwudo.uyu.patches.twitch.notifications.fixNotificationsPatch
import io.github.bakwudo.uyu.patches.twitch.privacy.privacyPatch
import io.github.bakwudo.uyu.patches.twitch.settings.settingsPatch
import io.github.bakwudo.uyu.patches.twitch.shared.sharedExtensionPatch

val enhancementPatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_TWITCH)
    dependsOn(sharedExtensionPatch)
    dependsOn(blockAdsPatch)
    dependsOn(hidePromotionsPatch)
    dependsOn(deletedMessagesPatch)
    dependsOn(thirdPartyEmotesPatch)
    dependsOn(fixLoginPatch)
    dependsOn(fixNotificationsPatch)
    dependsOn(privacyPatch)
    dependsOn(settingsPatch)
}
