package io.github.bakwudo.uyu.extension.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.WeakHashMap;

import io.github.bakwudo.uyu.extension.settings.Settings;

public final class PlayerSupport {
    private static final int TAG_KEY = 0x4B495A55;
    private static final WeakHashMap<ViewGroup, PlayerInstallation> INSTALLATIONS =
            new WeakHashMap<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private PlayerSupport() {}

    public static void onViewCreated(View root) {
        if (root == null) return;
        try {
            root.post(() -> install(root));
        } catch (Throwable ignored) {}
    }

    public static int getForwardSeek() {
        try {
            return Settings.CUSTOM_FORWARD_SEEK.get()
                    ? Math.max(1, Settings.FORWARD_SEEK_SECONDS.get())
                    : 30;
        } catch (Throwable ignored) {
            return 30;
        }
    }

    public static int getRewindSeek() {
        try {
            return Settings.CUSTOM_REWIND_SEEK.get()
                    ? -Math.max(1, Settings.REWIND_SEEK_SECONDS.get())
                    : -10;
        } catch (Throwable ignored) {
            return -10;
        }
    }

    private static void install(View root) {
        if (root.getResources().getConfiguration().orientation
                != Configuration.ORIENTATION_LANDSCAPE) return;

        FrameLayout pane = findPlayerPane(root);
        if (pane == null) return;

        PlayerInstallation existing = INSTALLATIONS.get(pane);
        if (existing != null) {
            existing.update();
            return;
        }

        try {
            PlayerInstallation installation = new PlayerInstallation(pane);
            INSTALLATIONS.put(pane, installation);
            installation.attach();
        } catch (Throwable ignored) {}
    }

    private static FrameLayout findPlayerPane(View root) {
        View named = findByResource(root, "player_pane");
        if (named instanceof FrameLayout) return (FrameLayout) named;
        named = findByResource(root, "player_view");
        if (named instanceof FrameLayout) return (FrameLayout) named;
        return findMediaFrame(root);
    }

    private static View findByResource(View root, String needle) {
        if (resourceName(root).contains(needle)) return root;
        if (!(root instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findByResource(group.getChildAt(i), needle);
            if (found != null) return found;
        }
        return null;
    }

    private static FrameLayout findMediaFrame(View view) {
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        if (looksLikeMediaContainer(group) && group instanceof FrameLayout) {
            return (FrameLayout) group;
        }
        for (int i = 0; i < group.getChildCount(); i++) {
            FrameLayout found = findMediaFrame(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private static boolean looksLikeMediaContainer(ViewGroup group) {
        String name = resourceName(group);
        String type = group.getClass().getName().toLowerCase(Locale.ROOT);
        if (name.contains("player") || name.contains("video") ||
                type.contains("player") || type.contains("exoplayer")) return true;

        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            String childType = child.getClass().getName().toLowerCase(Locale.ROOT);
            if (child instanceof android.view.SurfaceView ||
                    child instanceof android.view.TextureView ||
                    childType.contains("player")) {
                return true;
            }
        }
        return false;
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

    private static final class PlayerInstallation {
        final FrameLayout pane;
        final GestureLayer gestures;
        final LinearLayout controls;
        final TextView refresh;
        final TextView timer;
        Runnable timerRunnable;

        PlayerInstallation(FrameLayout pane) {
            this.pane = pane;
            this.gestures = new GestureLayer(pane.getContext(), pane);
            this.controls = new LinearLayout(pane.getContext());
            this.refresh = makeButton("R", "Kizu refresh");
            this.timer = makeButton("T", "Kizu sleep timer");
        }

        TextView makeButton(String text, String description) {
            TextView button = new TextView(pane.getContext());
            button.setText(text);
            button.setTextColor(Color.WHITE);
            button.setTextSize(15f);
            button.setGravity(Gravity.CENTER);
            button.setBackgroundColor(Color.argb(150, 0, 0, 0));
            button.setClickable(true);
            button.setContentDescription(description);
            return button;
        }

        void attach() {
            pane.post(() -> {
                try {
                    FrameLayout.LayoutParams gestureParams =
                            new FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                            );
                    int insertion = Math.min(1, pane.getChildCount());
                    pane.addView(gestures, insertion, gestureParams);

                    controls.setOrientation(LinearLayout.HORIZONTAL);
                    controls.setPadding(dp(2), dp(2), dp(2), dp(2));
                    controls.setBackgroundColor(Color.argb(90, 0, 0, 0));

                    controls.addView(refresh, new LinearLayout.LayoutParams(dp(42), dp(42)));
                    controls.addView(timer, new LinearLayout.LayoutParams(dp(42), dp(42)));

                    refresh.setOnClickListener(v -> refreshPlayer());
                    timer.setOnClickListener(v -> showTimer());

                    FrameLayout.LayoutParams controlParams =
                            new FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                            );
                    controlParams.gravity = Gravity.BOTTOM | Gravity.START;
                    controlParams.leftMargin = dp(8);
                    controlParams.bottomMargin = dp(8);
                    pane.addView(controls, controlParams);
                    update();
                } catch (Throwable ignored) {}
            });
        }

        void update() {
            controls.setVisibility(
                    pane.getResources().getConfiguration().orientation
                            == Configuration.ORIENTATION_LANDSCAPE
                            ? View.VISIBLE
                            : View.GONE
            );
            refresh.setVisibility(
                    Settings.SHOW_REFRESH_BUTTON.get() ? View.VISIBLE : View.GONE
            );
            timer.setVisibility(
                    Settings.SHOW_SLEEP_TIMER.get() ? View.VISIBLE : View.GONE
            );
        }

        int dp(int value) {
            return Math.max(
                    1,
                    Math.round(value * pane.getResources().getDisplayMetrics().density)
            );
        }

        void refreshPlayer() {
            try {
                Object player = findPlayer(pane);
                if (player != null) {
                    if (invokeNoArg(player, "prepare")) {
                        invokeNoArg(player, "play");
                        return;
                    }
                    if (invokeNoArg(player, "retry") ||
                            invokeNoArg(player, "reload") ||
                            invokeNoArg(player, "refresh")) {
                        return;
                    }
                }
                Activity activity = findActivity(pane.getContext());
                if (activity != null) activity.recreate();
            } catch (Throwable ignored) {}
        }

        void showTimer() {
            if (!Settings.SHOW_SLEEP_TIMER.get()) return;
            try {
                final String[] choices = {
                        "15 minutes", "30 minutes", "1 hour", "2 hours", "Cancel timer"
                };
                new AlertDialog.Builder(pane.getContext())
                        .setTitle("Kizu sleep timer")
                        .setItems(choices, (dialog, which) -> {
                            if (which == choices.length - 1) {
                                cancelTimer();
                            } else {
                                int minutes = which == 0 ? 15 :
                                        which == 1 ? 30 :
                                        which == 2 ? 60 : 120;
                                scheduleTimer(minutes);
                            }
                        })
                        .show();
            } catch (Throwable ignored) {}
        }

        void scheduleTimer(int minutes) {
            cancelTimer();
            final long delay = minutes * 60_000L;
            timerRunnable = () -> {
                try {
                    Object player = findPlayer(pane);
                    if (player != null) invokeNoArg(player, "pause");
                } catch (Throwable ignored) {}
                timerRunnable = null;
            };
            MAIN.postDelayed(timerRunnable, delay);
        }

        void cancelTimer() {
            if (timerRunnable != null) {
                MAIN.removeCallbacks(timerRunnable);
                timerRunnable = null;
            }
        }
    }

    private static final class GestureLayer extends View {
        final Context context;
        final ViewGroup pane;
        final AudioManager audioManager;
        float downX;
        float downY;
        int startVolume;
        float startBrightness;
        int mode;
        boolean active;
        boolean seekDone;
        TextView osd;
        Runnable hideOsd;

        GestureLayer(Context context, ViewGroup pane) {
            super(context);
            this.context = context;
            this.pane = pane;
            audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            setBackgroundColor(Color.TRANSPARENT);
            setClickable(true);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            try {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getX();
                        downY = event.getY();
                        startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                        startBrightness = getBrightness();
                        mode = 0;
                        active = false;
                        seekDone = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getX() - downX;
                        float dy = downY - event.getY();
                        if (!active && Math.hypot(dx, dy) < dp(12)) return true;

                        if (!active) {
                            active = true;
                            if (Math.abs(dx) > Math.abs(dy)) {
                                mode = Settings.CUSTOM_FORWARD_SEEK.get() ||
                                        Settings.CUSTOM_REWIND_SEEK.get() ? 3 : 0;
                            } else if (downX < getWidth() / 2f) {
                                mode = Settings.BRIGHTNESS_GESTURE.get() ? 1 : 0;
                            } else {
                                mode = Settings.VOLUME_GESTURE.get() ? 2 : 0;
                            }
                        }

                        if (mode == 1) updateBrightness(dy);
                        else if (mode == 2) updateVolume(dy);
                        else if (mode == 3 && !seekDone) {
                            updateSeek(dx);
                            seekDone = true;
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        active = false;
                        mode = 0;
                        seekDone = false;
                        return true;

                    default:
                        return true;
                }
            } catch (Throwable ignored) {
                return true;
            }
        }

        void updateVolume(float delta) {
            int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            if (max <= 0) return;
            int value = clamp(
                    startVolume +
                            Math.round(delta / Math.max(1f, getHeight() * 0.7f) * max),
                    0,
                    max
            );
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0);
            showOwd("Volume " + Math.round(value * 100f / max) + "%");
        }

        void updateBrightness(float delta) {
            int value = clamp(
                    Math.round(startBrightness * 100f) +
                            Math.round(
                                    delta / Math.max(1f, getHeight() * 0.7f) * 100f
                            ),
                    1,
                    100
            );
            setBrightness(value / 100f);
            showOwd("Brightness " + value + "%");
        }

        void updateSeek(float delta) {
            Object player = findPlayer(pane);
            if (player == null) return;
            int seconds = delta < 0
                    ? getForwardSeek()
                    : Math.abs(getRewindSeek());
            long amount = seconds * 1000L;
            try {
                Object currentObject = invoke(player, "getCurrentPosition");
                if (!(currentObject instanceof Number)) return;
                long current = ((Number) currentObject).longValue();
                long target = Math.max(
                        0L,
                        current + (delta < 0 ? amount : -amount)
                );
                if (invokeSeek(player, target)) {
                    showOwd((delta < 0 ? "+" : "-") + seconds + "s");
                }
            } catch (Throwable ignored) {}
        }

        void showOwd(String text) {
            if (!Settings.GESTURE_OSD.get()) return;
            if (!(pane instanceof FrameLayout)) return;

            if (osd == null) {
                osd = new TextView(context);
                osd.setTextColor(Color.WHITE);
                osd.setTextSize(18f);
                osd.setGravity(Gravity.CENTER);
                osd.setPadding(dp(18), dp(10), dp(18), dp(10));
                osd.setBackgroundColor(Color.argb(190, 0, 0, 0));
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                lp.gravity = Gravity.CENTER;
                pane.addView(osd, lp);
            }

            osd.setText(text);
            osd.setVisibility(View.VISIBLE);
            if (hideOsd != null) MAIN.removeCallbacks(hideOsd);
            hideOsd = () -> {
                if (osd != null) osd.setVisibility(View.GONE);
            };
            MAIN.postDelayed(hideOsd, 700L);
        }

        float getBrightness() {
            Activity activity = findActivity(context);
            if (activity == null) return 1f;
            float value = activity.getWindow().getAttributes().screenBrightness;
            return value <= 0f ? 1f : value;
        }

        void setBrightness(float value) {
            Activity activity = findActivity(context);
            if (activity == null) return;
            android.view.WindowManager.LayoutParams params =
                    activity.getWindow().getAttributes();
            params.screenBrightness = Math.max(0.01f, Math.min(1f, value));
            activity.getWindow().setAttributes(params);
        }

        int dp(int value) {
            return Math.max(
                    1,
                    Math.round(value * getResources().getDisplayMetrics().density)
            );
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Object findPlayer(ViewGroup root) {
        try {
            return findPlayerInView(root, 0);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object findPlayerInView(View view, int depth) {
        if (view == null || depth > 5) return null;

        Object fromGetter = tryGetter(view, "getPlayer");
        if (isPlayer(fromGetter)) return fromGetter;
        if (view.getTag() != null && isPlayer(view.getTag())) {
            return view.getTag();
        }

        for (Class<?> current = view.getClass();
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
                    Object candidate = field.get(view);
                    if (isPlayer(candidate)) return candidate;
                } catch (Throwable ignored) {}
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Object child = findPlayerInView(group.getChildAt(i), depth + 1);
                if (child != null) return child;
            }
        }
        return null;
    }

    private static Object tryGetter(Object object, String name) {
        if (object == null) return null;
        try {
            Method method = object.getClass().getMethod(name);
            method.setAccessible(true);
            return method.invoke(object);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isPlayer(Object object) {
        return object != null &&
                hasMethod(object, "seekTo") &&
                hasMethod(object, "getCurrentPosition");
    }

    private static boolean hasMethod(Object object, String name) {
        for (Class<?> current = object.getClass();
             current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name)) return true;
            }
        }
        return false;
    }

    private static Object invoke(Object object, String name) throws Exception {
        for (Class<?> current = object.getClass();
             current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) &&
                        method.getParameterTypes().length == 0) {
                    method.setAccessible(true);
                    return method.invoke(object);
                }
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static boolean invokeNoArg(Object object, String name) {
        try {
            invoke(object, name);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean invokeSeek(Object player, long position) {
        for (Class<?> current = player.getClass();
             current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (!"seekTo".equals(method.getName()) ||
                        method.getParameterTypes().length != 1) continue;
                try {
                    method.setAccessible(true);
                    Class<?> parameter = method.getParameterTypes()[0];
                    if (parameter == long.class || parameter == Long.class) {
                        method.invoke(player, position);
                        return true;
                    }
                    if (parameter == int.class || parameter == Integer.class) {
                        method.invoke(
                                player,
                                (int) Math.min(Integer.MAX_VALUE, position)
                        );
                        return true;
                    }
                } catch (Throwable ignored) {}
            }
        }
        return false;
    }

    private static Activity findActivity(Context context) {
        Context current = context;
        while (current instanceof android.content.ContextWrapper) {
            if (current instanceof Activity) return (Activity) current;
            Context base = ((android.content.ContextWrapper) current).getBaseContext();
            if (base == current) break;
            current = base;
        }
        return current instanceof Activity ? (Activity) current : null;
    }
}
