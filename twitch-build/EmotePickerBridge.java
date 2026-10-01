package app.morphe.extension.twitch.emotes;

import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.bakwudo.uyu.extension.settings.Settings;

public final class EmotePickerBridge {

    private static final String TAG = "KizuPicker";

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

    private static final String SUPPORT = "app.morphe.extension.twitch.emotes.EmoteSupport";

    private EmotePickerBridge() {
    }

    private static volatile String currentChannelId;

    /** Called when Twitch opens the picker. Prefer the channel captured by the chat connection. */
    public static void onPickerOpened(Object tuid) {
        try {
            Class<?> supportClass = Class.forName(
                    SUPPORT,
                    true,
                    EmotePickerBridge.class.getClassLoader()
            );
            Method current = supportClass.getMethod("getCurrentChannelId");
            Object value = current.invoke(null);
            currentChannelId = value == null ? null : String.valueOf(value);
            if (currentChannelId != null && !currentChannelId.isEmpty()) {
                return;
            }
        } catch (Throwable t) {
            Log.w(TAG, "Could not read current channel ID from EmoteSupport", t);
        }

        if (tuid == null) {
            currentChannelId = null;
            return;
        }
        try {
            Method toInt = tuid.getClass().getMethod("toInt");
            Object result = toInt.invoke(tuid);
            currentChannelId = result == null ? null : String.valueOf(result);
        } catch (Throwable t) {
            Log.w(TAG, "Could not resolve picker channel ID from Tuid", t);
            currentChannelId = null;
        }
    }

    /** Called from the patched EmotePickerPresenter.G2 just before it returns. */
    public static Object mergeGlobal(Object mtf) {
        try {
            if (!Settings.EMOTES_PICKER.get()) return mtf;
            List<Entry> entries = loadForChannel(currentChannelId);
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
            int added = 0;
            for (Entry entry : entries) {
                Object uiModel = buildUiModel(cl, entry);
                if (uiModel != null) {
                    merged.add(uiModel);
                    added++;
                }
            }
            Log.d(TAG, "merged " + added + " of " + entries.size() + " global emotes");
            if (added == 0) {
                return mtf;
            }

            Class<?> zhoClass = Class.forName(ZHO, false, cl);
            Constructor<?> ctor = mtf.getClass().getConstructor(zhoClass, List.class);
            return ctor.newInstance(header, merged);
        } catch (Throwable t) {
            Log.e(TAG, "mergeGlobal failed", t);
            return mtf;
        }
    }

    private static List<Entry> loadForChannel(String channelId) {
        try {
            Class<?> supportClass = Class.forName(
                    CATALOG.replace(".EmotePickerBridge", ".EmoteSupport"),
                    true,
                    EmotePickerBridge.class.getClassLoader()
            );
            Method all = supportClass.getMethod("getAllForChannel", String.class);
            Object result = all.invoke(null, channelId);
            if (!(result instanceof List)) {
                return Collections.emptyList();
            }
            List<Entry> out = new ArrayList<Entry>();
            for (Object item : (List<?>) result) {
                Entry entry = toEntry(item);
                if (entry != null) out.add(entry);
            }
            return out;
        } catch (Throwable t) {
            Log.e(TAG, "loadGlobals failed", t);
            return Collections.emptyList();
        }
    }

    private static Entry toEntry(Object emote) {
        if (!(emote instanceof Emote)) return null;
        try {
            Emote value = (Emote) emote;
            if (value.name == null || value.name.isEmpty() ||
                value.url == null || value.url.isEmpty()) return null;
            return new Entry(value.name, value.url, value.animated);
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

            Object emote = newInstanceMatching(
                    uofClass, entry.url, entry.code, assetType, emoteKind
            );
            Object input = newInstanceMatching(
                    tofClass, entry.code, entry.url, false
            );
            Object unlocked = newInstanceMatching(mb7Class, emote, input);
            return newInstanceMatching(
                    ltfClass, entry.url, unlocked, assetType, displayMode
            );
        } catch (Throwable t) {
            Log.w(TAG, "buildUiModel failed for " + entry.code, t);
            return null;
        }
    }

    private static Object newInstanceMatching(Class<?> cls, Object... args)
            throws Exception {
        for (Constructor<?> c : cls.getDeclaredConstructors()) {
            Class<?>[] types = c.getParameterTypes();
            if (types.length != args.length) continue;

            boolean compatible = true;
            for (int i = 0; i < types.length; i++) {
                if (args[i] == null) {
                    if (types[i].isPrimitive()) {
                        compatible = false;
                        break;
                    }
                    continue;
                }
                Class<?> expected = wrapPrimitive(types[i]);
                if (!expected.isAssignableFrom(args[i].getClass())) {
                    compatible = false;
                    break;
                }
            }
            if (!compatible) continue;

            c.setAccessible(true);
            return c.newInstance(args);
        }

        throw new NoSuchMethodException(
                cls.getName() + " has no compatible constructor"
        );
    }

    private static Class<?> wrapPrimitive(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == char.class) return Character.class;
        return type;
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
