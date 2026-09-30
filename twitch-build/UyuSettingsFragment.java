package io.github.bakwudo.uyu.extension.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

@SuppressWarnings("deprecation")
public class UyuSettingsFragment extends PreferenceFragment {
    static final String SECTION_ADS = "ads";
    static final String SECTION_EMOTES = "emotes";
    static final String SECTION_CHAT = "chat";
    static final String SECTION_PLAYER = "player";
    static final String SECTION_PRIVACY = "privacy";

    private static final String ARG_SECTION = "section";
    private String section;
    private CharSequence previousTitle;

    static UyuSettingsFragment create(String section) {
        UyuSettingsFragment fragment = new UyuSettingsFragment();
        Bundle arguments = new Bundle();
        arguments.putString(ARG_SECTION, section);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getPreferenceManager().setSharedPreferencesName(Setting.PREFERENCES_NAME);

        Bundle arguments = getArguments();
        section = arguments == null ? null : arguments.getString(ARG_SECTION);

        PreferenceScreen screen =
                getPreferenceManager().createPreferenceScreen(getActivity());
        setPreferenceScreen(screen);

        if (section == null) {
            addSectionLinks(screen);
        } else if (SECTION_ADS.equals(section)) {
            addAdsSettings(screen);
        } else if (SECTION_EMOTES.equals(section)) {
            addEmoteSettings(screen);
        } else if (SECTION_CHAT.equals(section)) {
            addChatSettings(screen);
        } else if (SECTION_PLAYER.equals(section)) {
            addPlayerSettings(screen);
        } else if (SECTION_PRIVACY.equals(section)) {
            addPrivacySettings(screen);
        }
    }

    private void addSectionLinks(PreferenceScreen screen) {
        addSectionLink(screen, SECTION_ADS, "Ad blocking",
                "Live ad blocking through Kizu's manifest proxy.");
        addSectionLink(screen, SECTION_EMOTES, "Emotes",
                "7TV, BTTV, FFZ, animated emotes, picker and autocomplete.");
        addSectionLink(screen, SECTION_CHAT, "Chat",
                "Deleted messages, timestamps and landscape chat controls.");
        addSectionLink(screen, SECTION_PLAYER, "Player",
                "Refresh, swipe gestures, fast-seek amounts and sleep timer.");
        addSectionLink(screen, SECTION_PRIVACY, "Privacy",
                "Disable Twitch measurement and crash reporting components.");
    }

    private void addSectionLink(
            PreferenceScreen screen,
            String linkedSection,
            String title,
            String summary
    ) {
        Preference preference = new Preference(screen.getContext());
        preference.setTitle(title);
        preference.setSummary(summary);
        preference.setOnPreferenceClickListener(clicked -> {
            Activity activity = getActivity();
            if (activity != null) {
                SettingsPatch.showScreen(activity, linkedSection);
            }
            return true;
        });
        screen.addPreference(preference);
    }

    private void addAdsSettings(PreferenceScreen screen) {
        addSwitch(screen, Settings.BLOCK_ADS, "Block ads",
                "Uses Kizu's live manifest proxy. Turn this off to restore normal ad behavior.");
    }

    private void addEmoteSettings(PreferenceScreen screen) {
        addSwitch(screen, Settings.EMOTES_7TV, "7TV emotes",
                "Show global and channel 7TV emotes in live chat.");
        addSwitch(screen, Settings.EMOTES_BTTV, "BTTV emotes",
                "Show global and channel BetterTTV emotes in live chat.");
        addSwitch(screen, Settings.EMOTES_FFZ, "FFZ emotes",
                "Show global and channel FrankerFaceZ emotes in live chat.");
        addSwitch(screen, Settings.EMOTES_ANIMATED, "Animated emotes",
                "Play animated third-party emotes. Turn this off to render a static frame.");
        addSwitch(screen, Settings.EMOTES_PICKER, "Third-party emote picker",
                "Show Kizu's emote picker button beside the chat input.");
        addSwitch(screen, Settings.EMOTES_AUTOCOMPLETE, "Third-party autocomplete",
                "Suggest 7TV, BTTV and FFZ emotes as you type in chat.");
        addSwitch(screen, Settings.EMOTES_ZERO_WIDTH, "Zero-width emotes",
                "Allow 7TV zero-width emotes to overlay the previous character or emote.");
    }

    private void addChatSettings(PreferenceScreen screen) {
        addSwitch(screen, Settings.CHAT_DELETED_MESSAGES, "Show deleted messages",
                "Keep moderated/deleted messages readable when Twitch provides the original text.");
        addSwitch(screen, Settings.CHAT_TIMESTAMPS, "Chat timestamps",
                "Prefix chat messages with the message time.");
        addTimestampFormat(screen);
        addSwitch(screen, Settings.LANDSCAPE_CHAT_SIZE_ENABLED,
                "Landscape chat size", "Apply the configurable landscape chat width.");
        addInt(screen, Settings.LANDSCAPE_CHAT_SIZE, "Landscape chat width reduction",
                "Reduce the chat width in landscape by this percentage.");
        addSwitch(screen, Settings.LANDSCAPE_CHAT_OPACITY_ENABLED,
                "Landscape chat opacity", "Apply the configurable landscape chat opacity.");
        addInt(screen, Settings.LANDSCAPE_CHAT_OPACITY, "Landscape chat opacity",
                "Opacity percentage for the landscape chat panel.");
        addSwitch(screen, Settings.HIDE_CHAT_BITS_BUTTON, "Hide Bits button",
                "Hide the Bits button beside Twitch's chat controls.");
    }

    private void addPlayerSettings(PreferenceScreen screen) {
        addSwitch(screen, Settings.SHOW_REFRESH_BUTTON, "Refresh button",
                "Add a Kizu refresh control to the player.");
        addSwitch(screen, Settings.VOLUME_GESTURE, "Volume swipe",
                "Swipe vertically on the right side of the player to change media volume.");
        addSwitch(screen, Settings.BRIGHTNESS_GESTURE, "Brightness swipe",
                "Swipe vertically on the left side of the player to change screen brightness.");
        addSwitch(screen, Settings.GESTURE_OSD, "Gesture OSD",
                "Show the current volume, brightness or seek adjustment.");
        addSwitch(screen, Settings.CUSTOM_FORWARD_SEEK, "Custom forward seek",
                "Use Kizu's forward seek amount for native fast-seek controls.");
        addInt(screen, Settings.FORWARD_SEEK_SECONDS, "Forward seek seconds",
                "Native fast-forward amount.");
        addSwitch(screen, Settings.CUSTOM_REWIND_SEEK, "Custom rewind seek",
                "Use Kizu's rewind seek amount for native fast-seek controls.");
        addInt(screen, Settings.REWIND_SEEK_SECONDS, "Rewind seek seconds",
                "Native rewind amount.");
        addSwitch(screen, Settings.SHOW_SLEEP_TIMER, "Sleep timer",
                "Add a player timer that pauses playback when it expires.");
    }

    private void addTimestampFormat(PreferenceScreen screen) {
        Preference preference = new Preference(screen.getContext());
        preference.setTitle("Timestamp format");
        updateTimestampSummary(preference);
        preference.setOnPreferenceClickListener(clicked -> {
            String current = Settings.CHAT_TIMESTAMP_FORMAT.get();
            Settings.CHAT_TIMESTAMP_FORMAT.save(
                    "h12".equalsIgnoreCase(current) ? "h24" : "h12"
            );
            updateTimestampSummary(preference);
            return true;
        });
        screen.addPreference(preference);
    }

    private void updateTimestampSummary(Preference preference) {
        String current = Settings.CHAT_TIMESTAMP_FORMAT.get();
        preference.setSummary(
                "h12".equalsIgnoreCase(current) ? "12-hour format" : "24-hour format"
        );
    }

    private void addPrivacySettings(PreferenceScreen screen) {
        addSwitch(screen, Settings.DISABLE_COMSCORE, "Disable Comscore",
                "Prevent Twitch's Comscore measurement component from starting. Restart Twitch after changing this.");
        addSwitch(screen, Settings.DISABLE_BUGSNAG, "Disable crash reporting",
                "Prevent Twitch 31.3.1's Firebase Crashlytics crash reporting from collecting reports. Restart Twitch after changing this.");
    }

    private static SwitchPreference addSwitch(
            PreferenceGroup group,
            BooleanSetting setting,
            String title,
            String summary
    ) {
        SwitchPreference preference = new SwitchPreference(group.getContext());
        preference.setKey(setting.key);
        preference.setDefaultValue(setting.defaultValue);
        preference.setTitle(title);
        preference.setSummary(summary);
        group.addPreference(preference);
        return preference;
    }

    private static Preference addInt(
            PreferenceGroup group,
            IntSetting setting,
            String title,
            String description
    ) {
        Preference preference = new Preference(group.getContext());
        preference.setTitle(title);
        updateIntSummary(preference, setting, description);
        preference.setOnPreferenceClickListener(clicked -> {
            Activity activity = group.getContext() instanceof Activity
                    ? (Activity) group.getContext()
                    : null;
            if (activity == null) return true;

            LinearLayout container = new LinearLayout(activity);
            container.setGravity(Gravity.CENTER);
            container.setPadding(24, 0, 24, 0);

            NumberPicker picker = new NumberPicker(activity);
            picker.setMinValue(setting.min);
            picker.setMaxValue(setting.max);
            picker.setValue(Math.max(setting.min,
                    Math.min(setting.max, setting.get())));
            picker.setWrapSelectorWheel(false);
            container.addView(picker);

            new AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setView(container)
                    .setPositiveButton("OK", (dialog, which) -> {
                        setting.save(picker.getValue());
                        updateIntSummary(preference, setting, description);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        });
        group.addPreference(preference);
        return preference;
    }

    private static void updateIntSummary(
            Preference preference,
            IntSetting setting,
            String description
    ) {
        preference.setSummary(description + " Current: " + setting.get());
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setBackgroundColor(SettingsUi.backgroundColor(view.getContext()));
        view.setClickable(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        Activity activity = getActivity();
        TextView title = activity == null
                ? null
                : SettingsPatch.findToolbarTitle(activity);
        if (title == null) return;
        if (previousTitle == null) previousTitle = title.getText();
        title.setText(title(section));
    }

    @Override
    public void onDestroyView() {
        Activity activity = getActivity();
        TextView title = activity == null
                ? null
                : SettingsPatch.findToolbarTitle(activity);
        if (title != null && previousTitle != null) {
            title.setText(previousTitle);
        }
        super.onDestroyView();
    }

    private static String title(String section) {
        if (section == null) return SettingsPatch.TITLE;
        switch (section) {
            case SECTION_ADS: return "Ads";
            case SECTION_EMOTES: return "Emotes";
            case SECTION_CHAT: return "Chat";
            case SECTION_PLAYER: return "Player";
            case SECTION_PRIVACY: return "Privacy";
            default: return SettingsPatch.TITLE;
        }
    }
}
