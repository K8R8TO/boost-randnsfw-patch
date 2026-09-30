package io.github.bakwudo.uyu.patches.twitch.enhancement

import app.morphe.patcher.patch.bytecodePatch
import io.github.bakwudo.uyu.patches.twitch.ads.blockAdsPatch
import io.github.bakwudo.uyu.patches.twitch.appearance.hidePromotionsPatch
import io.github.bakwudo.uyu.patches.twitch.settings.settingsPatch
import io.github.bakwudo.uyu.patches.twitch.shared.Constants.COMPATIBILITY_TWITCH

/**
 * Kizu's main Twitch enhancement patch.
 *
 * All Kizu features are exposed as runtime controls inside Twitch under Settings -> Kizu.
 * Individual implementation patches are kept internal and wired through this umbrella patch.
 */
@Suppress("unused")
val twitchEnhancementPatch = bytecodePatch(
    name = "Twitch Enhancement",
    description = "Adds Kizu's configurable Twitch enhancements. Includes the Kizu settings " +
        "menu, ad blocking, chat/UI controls, and the runtime switches used by the emote, player, " +
        "interface and privacy enhancements as they are implemented.",
) {
    compatibleWith(COMPATIBILITY_TWITCH)
    dependsOn(settingsPatch, blockAdsPatch, hidePromotionsPatch)
}
