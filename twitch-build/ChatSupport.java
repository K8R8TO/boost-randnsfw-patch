package io.github.bakwudo.uyu.extension.chat;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;

import app.morphe.extension.twitch.emotes.EmoteInputSupport;
import io.github.bakwudo.uyu.extension.settings.Settings;

public final class ChatSupport {
    private static final WeakHashMap<View, LayoutState> LANDSCAPE_STATE = new WeakHashMap<>();
    private static final WeakHashMap<TextView, String> TIMESTAMP_STATE = new WeakHashMap<>();
    private static final int TAG_MAX_DEPTH = 4;

    private ChatSupport() {}

    public static void onViewCreated(View root) {
        if (root == null) return;
        try {
            EmoteInputSupport.onViewCreated(root);
            root.post(() -> {
                try {
                    EmoteInputSupport.onViewCreated(root);
                    applyLandscape(root);
                } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {}
    }

    public static boolean shouldShowDeletedMessages() {
        try {
            return Settings.CHAT_DELETED_MESSAGES.get();
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static void decorateTimestamp(TextView textView, Object messageItem) {
        if (textView == null || !Settings.CHAT_TIMESTAMPS.get()) return;
        try {
            long timestamp = findTimestamp(
                    messageItem,
                    Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>()),
                    0
            );
            if (timestamp <= 0L) return;

            String format = "h12".equalsIgnoreCase(Settings.CHAT_TIMESTAMP_FORMAT.get())
                    ? "h:mm a"
                    : "HH:mm";
            String stamp = new SimpleDateFormat(format, Locale.getDefault())
                    .format(new Date(timestamp));

            CharSequence current = textView.getText();
            String state = stamp + "\n" + String.valueOf(current);
            synchronized (TIMESTAMP_STATE) {
                if (state.equals(TIMESTAMP_STATE.get(textView))) return;
                TIMESTAMP_STATE.put(textView, state);
            }
            if (current == null) return;

            SpannableStringBuilder builder = new SpannableStringBuilder();
            builder.append("[");
            int start = builder.length();
            builder.append(stamp);
            int end = builder.length();
            builder.append("] ");
            builder.append(current);
            builder.setSpan(
                    new ForegroundColorSpan(Color.GRAY),
                    0,
                    end + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            textView.setText(new SpannedString(builder), TextView.BufferType.SPANNABLE);
        } catch (Throwable ignored) {}
    }

    private static void applyLandscape(View root) {
        if (root == null) return;
        if (root.getResources().getConfiguration().orientation
                != Configuration.ORIENTATION_LANDSCAPE) {
            restoreLandscape(root);
            return;
        }

        ViewGroup chat = findChatContainer(root);
        if (chat == null || chat.getParent() == null) return;

        LayoutState state = LANDSCAPE_STATE.get(chat);
        if (state == null) {
            state = new LayoutState(chat.getLayoutParams().width, chat.getAlpha());
            LANDSCAPE_STATE.put(chat, state);
        }

        View parent = (View) chat.getParent();
        if (Settings.LANDSCAPE_CHAT_SIZE_ENABLED.get()) {
            int parentWidth = parent.getWidth();
            if (parentWidth > 0) {
                float fraction =
                        1f - (Settings.LANDSCAPE_CHAT_SIZE.get() / 100f);
                fraction = Math.max(0.1f, Math.min(1f, fraction));
                ViewGroup.LayoutParams lp = chat.getLayoutParams();
                lp.width = Math.max(1, Math.round(parentWidth * fraction));
                chat.setLayoutParams(lp);
            }
        } else {
            ViewGroup.LayoutParams lp = chat.getLayoutParams();
            lp.width = state.originalWidth;
            chat.setLayoutParams(lp);
        }

        chat.setAlpha(Settings.LANDSCAPE_CHAT_OPACITY_ENABLED.get()
                ? Math.max(0f, Math.min(1f,
                        Settings.LANDSCAPE_CHAT_OPACITY.get() / 100f))
                : state.originalAlpha);
    }

    private static void restoreLandscape(View root) {
        ViewGroup chat = findChatContainer(root);
        if (chat == null) return;
        LayoutState state = LANDSCAPE_STATE.get(chat);
        if (state == null) return;
        ViewGroup.LayoutParams lp = chat.getLayoutParams();
        lp.width = state.originalWidth;
        chat.setLayoutParams(lp);
        chat.setAlpha(state.originalAlpha);
    }

    private static ViewGroup findChatContainer(View root) {
        if (!(root instanceof ViewGroup)) return null;
        View recycler = findRecycler(root);
        if (recycler == null) return null;

        View cursor = recycler;
        ViewGroup candidate = null;
        for (int depth = 0; depth < 10 && cursor != null; depth++) {
            if (cursor instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) cursor;
                if (hasChatInput(group) ||
                        hasResourceName(group, "chat") ||
                        hasResourceName(group, "chomments")) {
                    candidate = group;
                    if (hasChatInput(group)) break;
                }
            }
            if (cursor.getParent() instanceof View) {
                cursor = (View) cursor.getParent();
            } else {
                break;
            }
        }
        return candidate;
    }

    private static View findRecycler(View view) {
        if (view != null && "androidx.recyclerview.widget.RecyclerView".equals(
                view.getClass().getName())) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findRecycler(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private static boolean hasChatInput(View view) {
        if (!(view instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof EditText && isChatInput((EditText) child)) return true;
            if (child instanceof ViewGroup && hasChatInput(child)) return true;
        }
        return false;
    }

    private static boolean isChatInput(EditText input) {
        String resource = resourceName(input);
        String hint = input.getHint() == null ? "" : input.getHint().toString();
        String description = input.getContentDescription() == null
                ? "" : input.getContentDescription().toString();
        String all = (resource + " " + hint + " " + description + " " +
                input.getClass().getName()).toLowerCase(Locale.ROOT);
        return all.contains("chat") || all.contains("message");
    }

    private static boolean hasResourceName(View view, String needle) {
        return resourceName(view).contains(needle);
    }

    private static String resourceName(View view) {
        try {
            if (view.getId() == View.NO_ID) return "";
            return view.getResources().getResourceEntryName(view.getId())
                    .toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static long findTimestamp(
            Object value,
            Set<Object> seen,
            int depth
    ) {
        if (value == null || depth > TAG_MAX_DEPTH) return 0L;
        Class<?> type = value.getClass();
        if (type.isPrimitive() || type == String.class || type.isEnum()) return 0L;
        if (!seen.add(value)) return 0L;

        for (Class<?> current = type;
             current != null && current != Object.class;
             current = current.getSuperclass()) {
            Field[] fields;
            try {
                fields = current.getDeclaredFields();
            } catch (Throwable ignored) {
                continue;
            }
            for (Field field : fields) {
                try {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    field.setAccessible(true);
                    Object fieldValue = field.get(value);
                    if (!(fieldValue instanceof Number)) continue;
                    Class<?> fieldType = field.getType();
                    if (fieldType != long.class && fieldType != Long.class &&
                            fieldType != int.class && fieldType != Integer.class) {
                        continue;
                    }
                    long number = ((Number) fieldValue).longValue();
                    if (!looksLikeTime(number)) continue;
                    String name = field.getName().toLowerCase(Locale.ROOT);
                    if (name.contains("time") || name.contains("stamp") ||
                            name.contains("sent") || name.contains("created") ||
                            name.contains("date")) {
                        return normalizeTime(number);
                    }
                } catch (Throwable ignored) {}
            }
            for (Field field : fields) {
                try {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    field.setAccessible(true);
                    Object child = field.get(value);
                    if (child == null || child == value) continue;
                    long nested = findTimestamp(child, seen, depth + 1);
                    if (nested > 0L) return nested;
                } catch (Throwable ignored) {}
            }
        }
        return 0L;
    }

    private static boolean looksLikeTime(long number) {
        return (number >= 1_500_000_000L && number <= 4_500_000_000L) ||
                (number >= 1_500_000_000_000L && number <= 4_500_000_000_000L);
    }

    private static long normalizeTime(long value) {
        return value < 10_000_000_000L ? value * 1000L : value;
    }

    private static final class LayoutState {
        final int originalWidth;
        final float originalAlpha;

        LayoutState(int originalWidth, float originalAlpha) {
            this.originalWidth = originalWidth;
            this.originalAlpha = originalAlpha;
        }
    }
}
