from pathlib import Path
import re

ROOT = Path("uyu")

# Copy Kizu settings/UI and enhancement patch into the fresh uyu checkout.
settings_dst = ROOT / "extensions/twitch/src/main/java/io/github/bakwudo/uyu/extension/settings"
settings_dst.mkdir(parents=True, exist_ok=True)
(settings_dst / "Settings.java").write_text(Path("twitch-build/Settings.java").read_text())
(settings_dst / "UyuSettingsFragment.java").write_text(Path("twitch-build/UyuSettingsFragment.java").read_text())

enhancement_dst = ROOT / "patches/src/main/kotlin/io/github/bakwudo/uyu/patches/twitch/enhancement"
enhancement_dst.mkdir(parents=True, exist_ok=True)
(enhancement_dst / "EnhancementPatch.kt").write_text(Path("twitch-build/EnhancementPatch.kt").read_text())

# Remove uyu's separate-install patch completely.
separate = ROOT / "patches/src/main/kotlin/io/github/bakwudo/uyu/patches/twitch/separateapp/SeparateAppPatch.kt"
if separate.exists():
    separate.unlink()

# Rebrand the in-app settings entry.
p = settings_dst / "SettingsPatch.java"
s = p.read_text()
s = s.replace('public static final String TITLE = "uyu";',
              'public static final String TITLE = "Kizu";')
p.write_text(s)

# Give Kizu its own preferences namespace.
p = settings_dst / "Setting.java"
s = p.read_text().replace(
    'public static final String PREFERENCES_NAME = "uyu_settings";',
    'public static final String PREFERENCES_NAME = "kizu_settings";'
)
p.write_text(s)

# Make the implementation patches internal/unnamed so users see one Twitch Enhancement patch.
internal_patches = [
    (ROOT / "patches/src/main/kotlin/io/github/bakwudo/uyu/patches/twitch/ads/BlockAdsPatch.kt",
     "blockAdsPatch"),
    (ROOT / "patches/src/main/kotlin/io/github/bakwudo/uyu/patches/twitch/appearance/HidePromotionsPatch.kt",
     "hidePromotionsPatch"),
]
for path, symbol in internal_patches:
    s = path.read_text()
    pattern = rf'@Suppress\("unused"\)\nval {symbol} = bytecodePatch\(.*?\n\) \{{\n    compatibleWith'
    replacement = f'internal val {symbol} = bytecodePatch {{\n    compatibleWith'
    s, count = re.subn(pattern, replacement, s, count=1, flags=re.S)
    if count != 1:
        raise RuntimeError(f"Could not internalize {symbol}")
    path.write_text(s)

# Project/bundle branding.
p = ROOT / "settings.gradle.kts"
p.write_text(p.read_text().replace('rootProject.name = "uyu"', 'rootProject.name = "kizu"'))

p = ROOT / "patches/build.gradle.kts"
s = p.read_text()
s = s.replace('group = "io.github.bakwudo.uyu"', 'group = "io.github.k8r8to.kizu"')
if not s.startswith('version = "0.1.0"'):
    s = 'version = "0.1.0"\n\n' + s
s = s.replace('name = "uyu"', 'name = "Kizu"')
s = s.replace(
    'description = "Patches for Twitch: channel points auto claim, Niconico-style scrolling comments and ad blocking."',
    'description = "Kizu enhancements for the Android Twitch app, based on uyu."'
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
