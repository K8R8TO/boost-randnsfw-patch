package app.morphe.extension.twitch.emotes;

import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    private static final String CATALOG = "app.morphe.extension.twitch.emotes.EmoteCatalog";

    private EmotePickerBridge() {
    }

    private static volatile String currentChannelId;

    /** Called when Twitch opens the picker so channel-specific third-party emotes can be included. */
    public static void onPickerOpened(Object tuid) {
        if (tuid == null) {
            currentChannelId = null;
            return;
        }
        try {
            Method toInt = tuid.getClass().getMethod("toInt");
            Object result = toInt.invoke(tuid);
            currentChannelId = result == null ? null : String.valueOf(result);
        } catch (Throwable t) {
            Log.w(TAG, "Could not resolve picker channel ID", t);
            currentChannelId = null;
        }
    }

    /** Called from the patched EmotePickerPresenter.G2 just before it returns. */
    public static Object mergeGlobal(Object mtf) {
        try {
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

            Object emote = uofClass
                    .getConstructor(String.class, String.class, xofClass, zofClass)
                    .newInstance(entry.url, entry.code, assetType, emoteKind);
            Object input = tofClass
                    .getConstructor(String.class, String.class, boolean.class)
                    .newInstance(entry.code, entry.url, false);
            Object unlocked = newInstanceByArity(mb7Class, 2, emote, input);
            return newInstanceByArity(ltfClass, 4, entry.url, unlocked, assetType, displayMode);
        } catch (Throwable t) {
            Log.w(TAG, "buildUiModel failed for " + entry.code, t);
            return null;
        }
    }

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
