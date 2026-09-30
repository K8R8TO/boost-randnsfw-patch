package app.morphe.extension.twitch.emotes;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;
import java.util.WeakHashMap;

import io.github.bakwudo.uyu.extension.settings.Settings;

public final class EmoteInputSupport {
    private static final WeakHashMap<EditText, Binding> BINDINGS = new WeakHashMap<>();
    private static final int MAX_PICKER_EMOTES = 120;
    private static final int MAX_SUGGESTIONS = 6;

    private EmoteInputSupport() {}

    public static void onViewCreated(View root) {
        if (root == null) return;
        try {
            scan(root);
        } catch (Throwable ignored) {}
    }

    private static void scan(View view) {
        if (view instanceof EditText && isChatInput((EditText) view)) {
            register((EditText) view);
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            scan(group.getChildAt(i));
        }
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

    private static void register(EditText input) {
        synchronized (BINDINGS) {
            if (BINDINGS.containsKey(input)) return;
            Binding binding = new Binding(input);
            BINDINGS.put(input, binding);
            binding.attach();
        }
    }

    private static FrameLayout findFrameParent(EditText input) {
        ViewParent parent = input.getParent();
        while (parent instanceof ViewGroup) {
            if (parent instanceof FrameLayout) return (FrameLayout) parent;
            parent = parent.getParent();
        }
        return null;
    }

    private static int dp(View view, int value) {
        return Math.max(1, Math.round(value * view.getResources().getDisplayMetrics().density));
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

    private static final class Binding {
        final EditText input;
        FrameLayout parent;
        TextView pickerButton;
        PopupWindow autocompleteWindow;
        PopupWindow pickerWindow;

        final TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                try {
                    updateAutocomplete();
                } catch (Throwable ignored) {}
            }
        };

        Binding(EditText input) {
            this.input = input;
        }

        void attach() {
            EmoteSupport.ensureCatalogLoaded(input.getContext());
            input.addTextChangedListener(watcher);
            input.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) {
                    positionButton();
                }
                @Override public void onViewDetachedFromWindow(View v) {
                    dismissWindows();
                }
            });
            parent = findFrameParent(input);
            if (parent != null) {
                createPickerButton();
                parent.getViewTreeObserver().addOnGlobalLayoutListener(
                        this::positionButton
                );
                positionButton();
            }
        }

        void createPickerButton() {
            if (!Settings.EMOTES_PICKER.get()) return;
            pickerButton = new TextView(input.getContext());
            pickerButton.setText("K");
            pickerButton.setTextSize(16f);
            pickerButton.setTextColor(Color.WHITE);
            pickerButton.setGravity(Gravity.CENTER);
            pickerButton.setContentDescription("Kizu emotes");
            pickerButton.setBackgroundColor(Color.TRANSPARENT);
            pickerButton.setClickable(true);
            pickerButton.setOnClickListener(v -> showPicker());
            parent.addView(
                    pickerButton,
                    new FrameLayout.LayoutParams(dp(input, 40), dp(input, 40))
            );
        }

        void positionButton() {
            if (pickerButton == null || parent == null ||
                    input.getWidth() <= 0 || input.getHeight() <= 0) return;
            int[] inputLoc = new int[2];
            int[] parentLoc = new int[2];
            input.getLocationOnScreen(inputLoc);
            parent.getLocationOnScreen(parentLoc);

            FrameLayout.LayoutParams lp =
                    (FrameLayout.LayoutParams) pickerButton.getLayoutParams();
            lp.width = dp(input, 40);
            lp.height = dp(input, 40);
            lp.leftMargin = inputLoc[0] - parentLoc[0] +
                    Math.max(0, input.getWidth() - lp.width - dp(input, 2));
            lp.topMargin = inputLoc[1] - parentLoc[1] +
                    Math.max(0, (input.getHeight() - lp.height) / 2);
            pickerButton.setLayoutParams(lp);
            pickerButton.setVisibility(
                    Settings.EMOTES_PICKER.get() ? View.VISIBLE : View.GONE
            );
        }

        void updateAutocomplete() {
            if (!Settings.EMOTES_AUTOCOMPLETE.get()) {
                dismissAutocomplete();
                return;
            }
            int cursor = input.getSelectionStart();
            if (cursor < 0) {
                dismissAutocomplete();
                return;
            }
            CharSequence text = input.getText();
            if (text == null || cursor > text.length()) {
                dismissAutocomplete();
                return;
            }
            int start = cursor;
            while (start > 0 && !Character.isWhitespace(text.charAt(start - 1))) {
                start--;
            }
            String prefix = text.subSequence(start, cursor).toString();
            if (prefix.length() == 0 || prefix.length() > 32) {
                dismissAutocomplete();
                return;
            }

            List<Emote> suggestions = EmoteSupport.suggest(prefix, MAX_SUGGESTIONS);
            if (suggestions.isEmpty()) {
                dismissAutocomplete();
                return;
            }

            LinearLayout list = new LinearLayout(input.getContext());
            list.setOrientation(LinearLayout.VERTICAL);
            list.setPadding(dp(input, 6), dp(input, 4), dp(input, 6), dp(input, 4));
            list.setBackgroundColor(Color.rgb(30, 30, 30));

            final int replaceStart = start;
            final int replaceEnd = cursor;
            for (Emote emote : suggestions) {
                TextView item = new TextView(input.getContext());
                item.setText(emote.name);
                item.setTextColor(Color.WHITE);
                item.setTextSize(14f);
                item.setGravity(Gravity.CENTER_VERTICAL);
                item.setPadding(
                        dp(input, 10), dp(input, 8),
                        dp(input, 10), dp(input, 8)
                );
                item.setOnClickListener(v -> {
                    try {
                        Editable editable = input.getText();
                        editable.replace(
                                replaceStart,
                                replaceEnd,
                                emote.name + " "
                        );
                        int pos = replaceStart + emote.name.length() + 1;
                        input.setSelection(Math.min(pos, editable.length()));
                        dismissAutocomplete();
                    } catch (Throwable ignored) {}
                });
                list.addView(item, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));
            }

            dismissAutocomplete();
            autocompleteWindow = new PopupWindow(
                    list,
                    Math.min(
                            dp(input, 320),
                            Math.max(dp(input, 200), input.getWidth())
                    ),
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    false
            );
            autocompleteWindow.setBackgroundDrawable(
                    new ColorDrawable(Color.rgb(30, 30, 30))
            );
            autocompleteWindow.setOutsideTouchable(true);
            autocompleteWindow.setElevation(dp(input, 8));
            autocompleteWindow.setInputMethodMode(
                    PopupWindow.INPUT_METHOD_NOT_NEEDED
            );
            autocompleteWindow.showAsDropDown(input, 0, dp(input, 2));
        }

        void showPicker() {
            if (!Settings.EMOTES_PICKER.get()) return;
            dismissAutocomplete();
            List<Emote> emotes = EmoteSupport.forPicker(MAX_PICKER_EMOTES);
            if (emotes.isEmpty()) return;

            LinearLayout outer = new LinearLayout(input.getContext());
            outer.setOrientation(LinearLayout.VERTICAL);
            outer.setBackgroundColor(Color.rgb(24, 24, 24));

            TextView title = new TextView(input.getContext());
            title.setText("Kizu emotes");
            title.setTextColor(Color.WHITE);
            title.setTextSize(15f);
            title.setPadding(
                    dp(input, 12), dp(input, 10),
                    dp(input, 12), dp(input, 8)
            );
            outer.addView(title);

            GridLayout grid = new GridLayout(input.getContext());
            grid.setColumnCount(5);
            grid.setPadding(
                    dp(input, 6), dp(input, 2),
                    dp(input, 6), dp(input, 8)
            );

            for (Emote emote : emotes) {
                TextView item = new TextView(input.getContext());
                item.setText(emote.name);
                item.setTextColor(Color.WHITE);
                item.setTextSize(12f);
                item.setGravity(Gravity.CENTER);
                item.setPadding(
                        dp(input, 4), dp(input, 10),
                        dp(input, 4), dp(input, 10)
                );
                item.setBackgroundColor(Color.TRANSPARENT);
                item.setOnClickListener(v -> {
                    try {
                        int cursor = input.getSelectionStart();
                        if (cursor < 0) cursor = input.length();
                        Editable editable = input.getText();
                        editable.insert(cursor, emote.name + " ");
                        input.setSelection(
                                Math.min(
                                        cursor + emote.name.length() + 1,
                                        editable.length()
                                )
                        );
                        dismissPicker();
                    } catch (Throwable ignored) {}
                });
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = 0;
                lp.height = dp(input, 44);
                lp.columnSpec = GridLayout.spec(
                        GridLayout.UNDEFINED, 1f
                );
                grid.addView(item, lp);
            }

            ScrollView scroll = new ScrollView(input.getContext());
            scroll.addView(grid);
            outer.addView(
                    scroll,
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(input, 320)
                    )
            );

            dismissPicker();
            pickerWindow = new PopupWindow(
                    outer,
                    Math.min(
                            dp(input, 380),
                            Math.max(
                                    dp(input, 280),
                                    input.getResources().getDisplayMetrics().widthPixels
                                            - dp(input, 24)
                            )
                    ),
                    dp(input, 390),
                    false
            );
            pickerWindow.setBackgroundDrawable(
                    new ColorDrawable(Color.rgb(24, 24, 24))
            );
            pickerWindow.setOutsideTouchable(true);
            pickerWindow.setElevation(dp(input, 10));
            pickerWindow.setInputMethodMode(
                    PopupWindow.INPUT_METHOD_NOT_NEEDED
            );
            pickerWindow.showAsDropDown(input, 0, -dp(input, 392));
        }

        void dismissAutocomplete() {
            if (autocompleteWindow != null) {
                autocompleteWindow.dismiss();
                autocompleteWindow = null;
            }
        }

        void dismissPicker() {
            if (pickerWindow != null) {
                pickerWindow.dismiss();
                pickerWindow = null;
            }
        }

        void dismissWindows() {
            dismissAutocomplete();
            dismissPicker();
        }
    }
}
