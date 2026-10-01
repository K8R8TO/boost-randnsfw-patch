package app.morphe.extension.twitch.emotes;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime bridge between the patched Twitch emote picker and Kizu's third-party
 * emote catalogue.
 *
 * All access to Twitch's obfuscated picker-model classes is via reflection, using
 * the same names as EmotePickerFingerprints.kt. This mirrors EmoteSupport.java,
 * which only refers to framework types at compile time.
 *
 * Scope: this iteration injects third-party emotes into the picker's data model
 * so they appear in search and click-to-insert. Rendering the third-party image
 * in the grid requires a separate hook on the picker's image loader.
 */
public final class EmotePickerBridge {

    private static final String MTF_CLASS = "defpackage.mtf";
    private static final String ZHO_CLASS = "defpackage.zho";
    private static final String UOF_CLASS = "defpackage.uof";
    private static final String WOF_CLASS = "defpackage.wof";
    private static final String TOF_CLASS = "defpackage.tof";
    private static final String MB7_CLASS = "defpackage.mb7";
    private static final String R93_CLASS = "defpackage.r93";
    private static final String LTF_CLASS = "defpackage.ltf";
    private static final String XOF_CLASS = "defpackage.xof";
    private static final String ZOF_CLASS = "defpackage.zof";
    private static final String QOF_CLASS = "defpackage.qof";

    private static final String CATALOG_CLASS = "app.morphe.extension.twitch.emotes.EmoteCatalog";

    private static final Map<String, List<Entry>> CHANNEL_EMOTES = new ConcurrentHashMap<>();
    private static volatile String currentChannelId;

    private EmotePickerBridge() {
    }

    /** Called from the patched EmotePickerPresenter.H2(Tuid, section). */
    public static void onPickerOpened(Object tuid) {
        if (tuid == null) {
            currentChannelId = null;
            return;
        }
        try {
            Method toInt = tuid.getClass().getMethod("toInt");
            Object result = toInt.invoke(tuid);
            currentChannelId = result == null ? null : String.valueOf(result);
        } catch (Throwable ignored) {
            currentChannelId = null;
        }
    }

    /** Called immediately before the patched state builder returns its mtf. */
    public static Object mergeInto(Object mtf) {
        if (mtf == null) {
            return null;
        }
        try {
            List<Entry> entries = lookupEntries(currentChannelId);
            if (entries.isEmpty()) {
                return mtf;
            }

            ClassLoader cl = EmotePickerBridge.class.getClassLoader();

            Method getEmotes = mtf.getClass().getMethod("b");
            Method getHeader = mtf.getClass().getMethod("c");
            Object existing = getEmotes.invoke(mtf);
            Object header = getHeader.invoke(mtf);
            if (!(existing instanceof List) || header == null) {
                return mtf;
            }

            List<Object> merged = new ArrayList<Object>((List<?>) existing);
            for (Entry entry : entries) {
                Object uiModel = buildUiModel(cl, entry);
                if (uiModel != null) {
                    merged.add(uiModel);
                }
            }
            if (merged.size() == ((List<?>) existing).size()) {
                return mtf;
            }

            Class<?> zhoClass = Class.forName(ZHO_CLASS, false, cl);
            Constructor<?> ctor = mtf.getClass().getConstructor(zhoClass, List.class);
            return ctor.newInstance(header, merged);
        } catch (Throwable ignored) {
            return mtf;
        }
    }

    private static List<Entry> lookupEntries(String channelId) {
        if (channelId == null) {
            return Collections.emptyList();
        }
        List<Entry> cached = CHANNEL_EMOTES.get(channelId);
        if (cached != null) {
            return cached;
        }
        try {
            Class<?> catalogClass = Class.forName(CATALOG_CLASS);
            Method all = catalogClass.getMethod("getAllForChannel", String.class);
            Object result = all.invoke(null, channelId);
            if (!(result instanceof List)) {
                return Collections.emptyList();
            }
            List<Entry> converted = new ArrayList<Entry>();
            for (Object item : (List<?>) result) {
                Entry entry = toEntry(item);
                if (entry != null) {
                    converted.add(entry);
                }
            }
            CHANNEL_EMOTES.put(channelId, converted);
            return converted;
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
    }

    private static Entry toEntry(Object emote) {
        if (emote == null) {
            return null;
        }
        try {
            Class<?> cls = emote.getClass();
            Field codeField = cls.getField("code");
            Field urlField = cls.getField("url");
            Field animatedField = cls.getField("animated");
            String code = String.valueOf(codeField.get(emote));
            String url = String.valueOf(urlField.get(emote));
            boolean animated = animatedField.getBoolean(emote);
            if (code.isEmpty() || url.isEmpty()) {
                return null;
            }
            return new Entry(code, url, animated);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object buildUiModel(ClassLoader cl, Entry entry) {
        try {
            Class<?> xofClass = Class.forName(XOF_CLASS, false, cl);
            Class<?> zofClass = Class.forName(ZOF_CLASS, false, cl);
            Class<?> qofClass = Class.forName(QOF_CLASS, false, cl);
            Class<?> wofClass = Class.forName(WOF_CLASS, false, cl);
            Class<?> uofClass = Class.forName(UOF_CLASS, false, cl);
            Class<?> tofClass = Class.forName(TOF_CLASS, false, cl);
            Class<?> mb7Class = Class.forName(MB7_CLASS, false, cl);
            Class<?> r93Class = Class.forName(R93_CLASS, false, cl);
            Class<?> ltfClass = Class.forName(LTF_CLASS, false, cl);

            Object assetType = enumConstant(xofClass, entry.animated ? "ANIMATED" : "STATIC");
            Object emoteKind = enumConstant(zofClass, "OTHER");
            Object displayMode = enumConstant(qofClass, "NONE");

            Object emote = uofClass
                    .getConstructor(String.class, String.class, xofClass, zofClass)
                    .newInstance(entry.url, entry.code, assetType, emoteKind);

            Object messageInput = tofClass
                    .getConstructor(String.class, String.class, boolean.class)
                    .newInstance(entry.code, entry.url, false);

            Object unlocked = mb7Class
                    .getConstructor(wofClass, tofClass)
                    .newInstance(emote, messageInput);

            return ltfClass
                    .getConstructor(String.class, r93Class, xofClass, qofClass)
                    .newInstance(entry.url, unlocked, assetType, displayMode);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object enumConstant(Class<?> enumClass, String name) throws Exception {
        @SuppressWarnings({"unchecked", "rawtypes"})
        Object value = Enum.valueOf((Class<? extends Enum>) enumClass, name);
        return value;
    }

    public static final class Entry {
        public final String code;
        public final String url;
        public final boolean animated;

        public Entry(String code, String url, boolean animated) {
            this.code = code;
            this.url = url;
            this.animated = animated;
        }
    }
}
