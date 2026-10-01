package app.morphe.extension.twitch.emotes;

import android.util.Log;

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
 * All access to Twitch's obfuscated picker-model classes is via reflection.
 * Classes in the default package have no prefix (the "defpackage" folder in a
 * jadx dump is a display convention).
 */
public final class EmotePickerBridge {

    private static final String TAG = "KizuPicker";

    // Twitch 31.3.1 obfuscated model classes (default package, no prefix).
    private static final String MTF = "mtf";
    private static final String ZHO = "zho";
    private static final String UOF = "uof";
    private static final String WOF = "wof";
    private static final String TOF = "tof";
    private static final String MB7 = "mb7";
    private static final String R93 = "r93";
    private static final String LTF = "ltf";
    private static final String XOF = "xof";
    private static final String ZOF = "zof";
    private static final String QOF = "qof";

    // Kizu's catalogue class (uses its real application package).
    private static final String CATALOG = "app.morphe.extension.twitch.emotes.EmoteCatalog";

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
            Log.d(TAG, "picker opened for channel " + currentChannelId);
        } catch (Throwable t) {
            Log.w(TAG, "onPickerOpened failed", t);
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
                Log.d(TAG, "no third-party emotes for channel " + currentChannelId);
                return mtf;
            }

            ClassLoader cl = EmotePickerBridge.class.getClassLoader();

            Method getEmotes = mtf.getClass().getMethod("b");
            Method getHeader = mtf.getClass().getMethod("c");
            Object existing = getEmotes.invoke(mtf);
            Object header = getHeader.invoke(mtf);
            if (!(existing instanceof List) || header == null) {
                Log.w(TAG, "mtf shape unexpected");
                return mtf;
            }

            List<Object> merged = new ArrayList<Object>((List<?>) existing);
            int added = 0;
            for (Entry entry : entries) {
                Object uiModel = buildUiModel(cl, entry);
                if (uiModel != null) {
                    merged.add(uiModel);
                    added++;
                }
            }
            Log.d(TAG, "merged " + added + " of " + entries.size() + " third-party emotes");
            if (added == 0) {
                return mtf;
            }

            Class<?> zhoClass = Class.forName(ZHO, false, cl);
            Constructor<?> ctor = mtf.getClass().getConstructor(zhoClass, List.class);
            return ctor.newInstance(header, merged);
        } catch (Throwable t) {
            Log.e(TAG, "mergeInto failed", t);
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
            Class<?> catalogClass = Class.forName(CATALOG);
            Method all = catalogClass.getMethod("getAllForChannel", String.class);
            Object result = all.invoke(null, channelId);
            if (!(result instanceof List)) {
                Log.w(TAG, "catalog.getAllForChannel returned non-list");
                return Collections.emptyList();
            }
            List<Entry> converted = new ArrayList<Entry>();
            for (Object item : (List<?>) result) {
                Entry entry = toEntry(item);
                if (entry != null) {
                    converted.add(entry);
                }
            }
            Log.d(TAG, "catalog returned " + converted.size() + " entries for " + channelId);
            CHANNEL_EMOTES.put(channelId, converted);
            return converted;
        } catch (Throwable t) {
            Log.e(TAG, "lookupEntries failed", t);
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
        } catch (Throwable t) {
            Log.w(TAG, "toEntry failed", t);
            return null;
        }
    }

    private static Object buildUiModel(ClassLoader cl, Entry entry) {
        try {
            Class<?> xofClass = Class.forName(XOF, false, cl);
            Class<?> zofClass = Class.forName(ZOF, false, cl);
            Class<?> qofClass = Class.forName(QOF, false, cl);
            Class<?> wofClass = Class.forName(WOF, false, cl);
            Class<?> uofClass = Class.forName(UOF, false, cl);
            Class<?> tofClass = Class.forName(TOF, false, cl);
            Class<?> mb7Class = Class.forName(MB7, false, cl);
            Class<?> r93Class = Class.forName(R93, false, cl);
            Class<?> ltfClass = Class.forName(LTF, false, cl);

            Object assetType = enumConstant(xofClass, entry.animated ? "ANIMATED" : "STATIC");
            Object emoteKind = enumConstant(zofClass, "OTHER");
            Object displayMode = enumConstant(qofClass, "NONE");

            Object emote = uofClass
                    .getConstructor(String.class, String.class, xofClass, zofClass)
                    .newInstance(entry.url, entry.code, assetType, emoteKind);

            Object messageInput = tofClass
                    .getConstructor(String.class, String.class, boolean.class)
                    .newInstance(entry.code, entry.url, false);

            Object unlocked = newInstanceByArity(mb7Class, 2, emote, messageInput);

            Object uiModel = newInstanceByArity(
                    ltfClass, 4, entry.url, unlocked, assetType, displayMode
            );
            return uiModel;
        } catch (Throwable t) {
            Log.w(TAG, "buildUiModel failed for " + entry.code, t);
            return null;
        }
    }

    /**
     * Kotlin data classes with default arguments expose a synthetic constructor
     * whose arity reflects only the required arguments. Those constructors are
     * not always returned by {@link Class#getConstructor}, so we scan all
     * declared constructors and pick the one with the matching parameter count.
     */
    private static Object newInstanceByArity(Class<?> cls, int arity, Object... args)
            throws Exception {
        for (Constructor<?> c : cls.getDeclaredConstructors()) {
            if (c.getParameterCount() == arity) {
                c.setAccessible(true);
                return c.newInstance(args);
            }
        }
        throw new NoSuchMethodException(
                cls.getName() + " has no constructor with arity " + arity);
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
