from pathlib import Path
import re
import shutil

ROOT = Path("uyu")
DONOR = Path("hooman")

# Copy Kizu settings/UI.
settings_dst = ROOT / "extensions/twitch/src/main/java/io/github/bakwudo/uyu/extension/settings"
settings_dst.mkdir(parents=True, exist_ok=True)

# Kizu ad blocking uses a live manifest proxy by default. Uyu's built-in client-side
# blocker otherwise masks Twitch's server-stitched ad breaks with the black countdown overlay.
# The proxy is based on the currently published Luminous endpoints; users can still replace it
# through the existing proxy setting.
stream_proxy = ROOT / "extensions/twitch/src/main/java/io/github/bakwudo/uyu/extension/ads/StreamProxy.java"
proxy_text = stream_proxy.read_text()
old = 'String proxy = Settings.ADS_PROXY_URL.get().trim();\n        if (proxy.isEmpty()) return usherUri;'
new = 'String proxy = Settings.ADS_PROXY_URL.get().trim();\n        if (proxy.isEmpty()) proxy = "https://eu2.luminous.dev/live/{channel}?allow_source=true&allow_audio_only=true";'
if old not in proxy_text:
    raise RuntimeError("Could not locate Uyu StreamProxy default")
stream_proxy.write_text(proxy_text.replace(old, new, 1))
(settings_dst / "Settings.java").write_text(Path("twitch-build/Settings.java").read_text())
(settings_dst / "UyuSettingsFragment.java").write_text(Path("twitch-build/UyuSettingsFragment.java").read_text())
(settings_dst / "PrivacySupport.java").write_text(Path("twitch-build/PrivacySupport.java").read_text())

# Add Kizu's umbrella patch.
enhancement_dst = ROOT / "patches/src/main/kotlin/io/github/bakwudo/uyu/patches/twitch/enhancement"
enhancement_dst.mkdir(parents=True, exist_ok=True)
(enhancement_dst / "EnhancementPatch.kt").write_text(Path("twitch-build/EnhancementPatch.kt").read_text())

# Add Kizu's emote bytecode hook.
emote_patch_dst = ROOT / "patches/src/main/kotlin/io/github/bakwudo/uyu/patches/twitch/emotes"
emote_patch_dst.mkdir(parents=True, exist_ok=True)
(emote_patch_dst / "Fingerprints.kt").write_text(Path("twitch-build/EmoteFingerprints.kt").read_text())
(emote_patch_dst / "ThirdPartyEmotesPatch.kt").write_text(Path("twitch-build/ThirdPartyEmotesPatch.kt").read_text())

privacy_patch_dst = ROOT / "patches/src/main/kotlin/io/github/bakwudo/uyu/patches/twitch/privacy"
privacy_patch_dst.mkdir(parents=True, exist_ok=True)
(privacy_patch_dst / "PrivacyPatch.kt").write_text(Path("twitch-build/PrivacyPatch.kt").read_text())

# Copy the proven 7TV/BTTV renderer from hoomans-morphe-patches.
donor_emotes = DONOR / "extensions/twitch/src/main/java/app/morphe/extension/twitch/emotes"
emote_ext_dst = ROOT / "extensions/twitch/src/main/java/app/morphe/extension/twitch/emotes"
if emote_ext_dst.exists():
    shutil.rmtree(emote_ext_dst)
shutil.copytree(donor_emotes, emote_ext_dst)

# Make donor renderer obey Kizu settings.
catalog = emote_ext_dst / "EmoteCatalog.java"
s = catalog.read_text()
s = s.replace(
    "    private final ProviderState globalBetterTtv = new ProviderState();",
    "    private final ProviderState globalBetterTtv = new ProviderState();\n    private final ProviderState globalFfz = new ProviderState();",
)
s = s.replace(
    "import android.content.Context;\n",
    "import android.content.Context;\n\nimport io.github.bakwudo.uyu.extension.settings.Settings;\n",
)
s = s.replace(
    "        schedule(globalSevenTv, now, () -> loadGlobalSevenTv(applicationContext));\n"
    "        schedule(globalBetterTtv, now, () -> loadGlobalBetterTtv(applicationContext));",
    "        if (Settings.EMOTES_7TV.get()) {\n"
    "            schedule(globalSevenTv, now, () -> loadGlobalSevenTv(applicationContext));\n"
    "        }\n"
    "        if (Settings.EMOTES_BTTV.get()) {\n"
    "            schedule(globalBetterTtv, now, () -> loadGlobalBetterTtv(applicationContext));\n"
    "        }\n"
    "        if (Settings.EMOTES_FFZ.get()) {\n"
    "            schedule(globalFfz, now, () -> loadGlobalFfz(applicationContext));\n"
    "        }",
)
s = s.replace(
    "        schedule(channel.sevenTv, now, () -> loadChannelSevenTv(applicationContext, channelId, channel));\n"
    "        schedule(channel.betterTtv, now,\n"
    "                () -> loadChannelBetterTtv(applicationContext, channelId, channel));",
    "        if (Settings.EMOTES_7TV.get()) {\n"
    "            schedule(channel.sevenTv, now, () -> loadChannelSevenTv(applicationContext, channelId, channel));\n"
    "        }\n"
    "        if (Settings.EMOTES_BTTV.get()) {\n"
    "            schedule(channel.betterTtv, now,\n"
    "                    () -> loadChannelBetterTtv(applicationContext, channelId, channel));\n"
    "        }\n"
    "        if (Settings.EMOTES_FFZ.get()) {\n"
    "            schedule(channel.ffz, now, () -> loadChannelFfz(applicationContext, channelId, channel));\n"
    "        }",
)
s, count = re.subn(
    r"    Emote find\(String channelId, String name\) \{.*?\n    \}\n\n    private void schedule",
    """    Emote find(String channelId, String name) {
        boolean sevenTv = Settings.EMOTES_7TV.get();
        boolean betterTtv = Settings.EMOTES_BTTV.get();
        boolean ffz = Settings.EMOTES_FFZ.get();

        if (channelId != null) {
            ChannelState channel = getChannel(channelId, false);
            if (channel != null) {
                if (sevenTv) {
                    Emote emote = channel.sevenTv.emotes.get(name);
                    if (emote != null) return emote;
                }
                if (betterTtv) {
                    Emote emote = channel.betterTtv.emotes.get(name);
                    if (emote != null) return emote;
                }
                if (ffz) {
                    Emote emote = channel.ffz.emotes.get(name);
                    if (emote != null) return emote;
                }
            }
        }

        if (sevenTv) {
            Emote emote = globalSevenTv.emotes.get(name);
            if (emote != null) return emote;
        }
        if (betterTtv) {
            Emote emote = globalBetterTtv.emotes.get(name);
            if (emote != null) return emote;
        }
        return ffz ? globalFfz.emotes.get(name) : null;
    }

    private void schedule""",
    s,
    count=1,
    flags=re.S,
)
if count != 1:
    raise RuntimeError("Could not patch EmoteCatalog.find")

s = s.replace(
    "    private static final class ChannelState {\n        final ProviderState sevenTv = new ProviderState();\n        final ProviderState betterTtv = new ProviderState();\n    }",
    "    private static final class ChannelState {\n        final ProviderState sevenTv = new ProviderState();\n        final ProviderState betterTtv = new ProviderState();\n        final ProviderState ffz = new ProviderState();\n    }",
    1,
)

s = s.replace(
    "    private void schedule",
    """    private void loadGlobalFfz(Context context) {
        boolean updated = false;
        try {
            LoadedValue<JSONArray> response = loadJsonArray(
                    context,
                    "ffz-global",
                    "https://api.betterttv.net/3/cached/frankerfacez/emotes/global"
            );
            Map<String, Emote> loaded = new LinkedHashMap<>();
            parseFfzArray(response.value, loaded);
            globalFfz.publish(loaded, response.fresh);
            updated = true;
        } catch (Exception ignored) {
            globalFfz.failed();
        } finally {
            globalFfz.loading.set(false);
        }
        if (updated) onUpdated.accept(null);
    }

    private void loadChannelFfz(Context context, String channelId, ChannelState channel) {
        boolean updated = false;
        try {
            LoadedValue<JSONArray> response = loadOptionalJsonArray(
                    context,
                    "ffz-channel-" + channelId,
                    "https://api.betterttv.net/3/cached/frankerfacez/users/twitch/" + channelId
            );
            Map<String, Emote> loaded = new LinkedHashMap<>();
            parseFfzArray(response.value, loaded);
            channel.ffz.publish(loaded, response.fresh);
            updated = true;
        } catch (Exception ignored) {
            channel.ffz.failed();
        } finally {
            channel.ffz.loading.set(false);
        }
        if (updated) onUpdated.accept(channelId);
    }

    private void schedule""",
    1,
)

s = s.replace(
    "    private static LoadedValue<JSONObject> loadJson(Context context, String cacheKey, String url)",
    """    private static void parseFfzArray(JSONArray emotes, Map<String, Emote> target) {
        if (emotes == null) return;
        for (int index = 0; index < emotes.length(); index++) {
            JSONObject item = emotes.optJSONObject(index);
            if (item == null) continue;
            String name = item.optString("code", "");
            JSONObject images = item.optJSONObject("images");
            if (name.isEmpty() || images == null) continue;
            String url = images.optString("2x", "");
            if (url.isEmpty()) url = images.optString("1x", "");
            if (url.isEmpty()) url = images.optString("4x", "");
            if (url.isEmpty()) continue;
            boolean animated = "gif".equalsIgnoreCase(item.optString("imageType", ""));
            target.put(name, new Emote(name, url, animated));
        }
    }

    private static LoadedValue<JSONArray> loadOptionalJsonArray(
            Context context,
            String cacheKey,
            String url
    ) throws IOException, JSONException {
        LoadedValue<String> text = loadText(context, cacheKey, url, true);
        try {
            return new LoadedValue<>(new JSONArray(text.value), text.fresh);
        } catch (JSONException failure) {
            deleteCachedText(context, cacheKey);
            throw failure;
        }
    }

    private static LoadedValue<JSONObject> loadJson(Context context, String cacheKey, String url)""",
    1,
)

    catalog.write_text(s)

loader = emote_ext_dst / "EmoteImageLoader.java"
s = loader.read_text()
s = s.replace(
    "import android.util.Size;\n",
    "import android.util.Size;\n\nimport io.github.bakwudo.uyu.extension.settings.Settings;\n",
)
s = s.replace("decode(cached, emote.animated, targetDimension)",
              "decode(cached, emote.animated && Settings.EMOTES_ANIMATED.get(), targetDimension)")
s = s.replace("decode(downloaded, emote.animated, targetDimension)",
              "decode(downloaded, emote.animated && Settings.EMOTES_ANIMATED.get(), targetDimension)")
loader.write_text(s)

support = emote_ext_dst / "EmoteSupport.java"
s = support.read_text()
s = s.replace(
    "import android.widget.TextView;\n",
    "import android.widget.TextView;\n\nimport io.github.bakwudo.uyu.extension.settings.Settings;\n",
)
needle = "    public static void bind(TextView textView, String sourceChannelId) {"
if needle not in s:
    raise RuntimeError("Could not locate EmoteSupport.bind")
s = s.replace(
    needle,
    """    public static void bind(TextView textView) {
        bind(textView, null);
    }

    public static void bind(TextView textView, String sourceChannelId) {""",
    1,
)
needle = "    private static void bindInternal(TextView textView, String sourceChannelId) {\n"
if needle not in s:
    raise RuntimeError("Could not locate EmoteSupport.bindInternal")
s = s.replace(
    needle,
    needle +
    "        if (!Settings.EMOTES_7TV.get() && !Settings.EMOTES_BTTV.get() && !Settings.EMOTES_FFZ.get()) {\n"
    "            forget(textView);\n"
    "            return;\n"
    "        }\n",
    1,
)
support.write_text(s)

# Keep the copied donor classes when R8 builds the extension.
proguard = ROOT / "extensions/proguard-rules.pro"
s = proguard.read_text()
if "-keep class app.morphe.extension.twitch.emotes.** { *; }
-keep class io.github.bakwudo.uyu.extension.settings.** { *; }" not in s:
    s += "\n# Kizu third-party emote renderer (adapted from hoomans-morphe-patches).\n"
    s += "-keep class app.morphe.extension.twitch.emotes.** { *; }\n"
proguard.write_text(s)

# Remove uyu features that are not part of Kizu's one-patch surface.
for relative in [
    "patches/src/main/kotlin/io/github/bakwudo/uyu/patches/twitch/separateapp",
    "patches/src/main/kotlin/io/github/bakwudo/uyu/patches/twitch/channelpoints",
    "patches/src/main/kotlin/io/github/bakwudo/uyu/patches/twitch/danmaku",
]:
    path = ROOT / relative
    if path.exists():
        shutil.rmtree(path)

# Visible branding.
p = settings_dst / "SettingsPatch.java"
s = p.read_text().replace(
    'public static final String TITLE = "uyu";',
    'public static final String TITLE = "Kizu";',
)
p.write_text(s)

# Kizu gets its own preference namespace.
p = settings_dst / "Setting.java"
s = p.read_text().replace(
    'public static final String PREFERENCES_NAME = "uyu_settings";',
    'public static final String PREFERENCES_NAME = "kizu_settings";',
)
p.write_text(s)

# Hide all helper patches so Morphe exposes only "Twitch Enhancement".
internal_patches = [
    (ROOT / "patches/src/main/kotlin/io/github/bakwudo/uyu/patches/twitch/ads/BlockAdsPatch.kt",
     "blockAdsPatch"),
    (ROOT / "patches/src/main/kotlin/io/github/bakwudo/uyu/patches/twitch/appearance/HidePromotionsPatch.kt",
     "hidePromotionsPatch"),
    (ROOT / "patches/src/main/kotlin/io/github/bakwudo/uyu/patches/twitch/login/FixLoginPatch.kt",
     "fixLoginPatch"),
    (ROOT / "patches/src/main/kotlin/io/github/bakwudo/uyu/patches/twitch/notifications/FixNotificationsPatch.kt",
     "fixNotificationsPatch"),
]
for path, symbol in internal_patches:
    s = path.read_text()
    pattern = rf'@Suppress\("unused"\)\nval {symbol} = bytecodePatch\(.*?\n\) \{{\n    compatibleWith'
    replacement = f'internal val {symbol} = bytecodePatch {{\n    compatibleWith'
    s, count = re.subn(pattern, replacement, s, count=1, flags=re.S)
    if count != 1:
        raise RuntimeError(f"Could not internalize {symbol}")
    path.write_text(s)

# Project / bundle identity.
p = ROOT / "settings.gradle.kts"
p.write_text(p.read_text().replace('rootProject.name = "uyu"', 'rootProject.name = "kizu"'))

p = ROOT / "patches/build.gradle.kts"
s = p.read_text()
s = s.replace('group = "io.github.bakwudo.uyu"', 'group = "io.github.k8r8to.kizu"')
s = 'version = "0.3.0"\n\n' + re.sub(r'^version = ".*?"\n\n', '', s)
s = s.replace('name = "uyu"', 'name = "Kizu"')
s = s.replace(
    'description = "Patches for Twitch: channel points auto claim, Niconico-style scrolling comments and ad blocking."',
    'description = "Kizu enhancements for the Android Twitch app, based on uyu and hoomans-morphe-patches."',
)
s = s.replace('source = "git@github.com:bakwudo/uyu.git"',
              'source = "https://github.com/K8R8TO/boost-randnsfw-patch"')
s = s.replace('author = "bakwudo"', 'author = "K8R8TO"')
s = s.replace('contact = "https://github.com/bakwudo/uyu/issues"',
              'contact = "https://github.com/K8R8TO/boost-randnsfw-patch/issues"')
s = s.replace('website = "https://github.com/bakwudo/uyu"',
              'website = "https://github.com/K8R8TO/boost-randnsfw-patch"')
s += """
tasks.withType<org.gradle.jvm.tasks.Jar>().configureEach {
    archiveBaseName.set("kizu")
}
"""
p.write_text(s)
