package io.github.bakwudo.uyu.extension.settings;

import android.app.Activity;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

@SuppressWarnings("deprecation")
public class UyuSettingsFragment extends PreferenceFragment {
    static final String SECTION_ADS = "ads";
    static final String SECTION_EMOTES = "emotes";
    static final String SECTION_CHAT = "chat";
    static final String SECTION_PLAYER = "player";
    static final String SECTION_INTERFACE = "interface";
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

        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(getActivity());
        setPreferenceScreen(screen);

        if (section == null) addSectionLinks(screen);
        else if (SECTION_ADS.equals(section)) addAdsSettings(screen);
        else if (SECTION_EMOTES.equals(section)) addEmoteSettings(screen);
        else if (SECTION_CHAT.equals(section)) addChatSettings(screen);
        else if (SECTION_PLAYER.equals(section)) addPlayerSettings(screen);
        else if (SECTION_INTERFACE.equals(section)) addInterfaceSettings(screen);
        else if (SECTION_PRIVACY.equals(section)) addPrivacySettings(screen);
    }

    private void addSectionLinks(PreferenceScreen screen) {
        addSectionLink(screen, SECTION_ADS, "Ad blocking and optional stream proxy");
        addSectionLink(screen, SECTION_EMOTES, "7TV, BTTV, animation, picker, autocomplete and zero-width emotes");
        addSectionLink(screen, SECTION_CHAT, "Deleted messages, timestamps, Bits button and landscape chat");
        addSectionLink(screen, SECTION_PLAYER, "Refresh, gestures, seek controls and sleep timer");
        addSectionLink(screen, SECTION_INTERFACE, "Stories, recommendations, featured clips and Search");
        addSectionLink(screen, SECTION_PRIVACY, "Comscore and Bugsnag controls");
    }

    private void addSectionLink(PreferenceScreen screen, String linkedSection, String summary) {
        Preference preference = new Preference(screen.getContext());
        preference.setTitle(title(linkedSection));
        preference.setSummary(summary);
        preference.setOnPreferenceClickListener(clicked -> {
            Activity activity = getActivity();
            if (activity != null) SettingsPatch.showScreen(activity, linkedSection);
            return true;
        });
        screen.addPreference(preference);
    }

    private void addAdsSettings(PreferenceScreen screen) {
        addSwitch(screen, Settings.BLOCK_ADS, "Adblock / proxy system",
                "Master switch for client-side blocking and the optional proxy path.");
        Preference proxy = new TextPreference(screen.getContext(), Settings.ADS_PROXY_URL,
                "https://example.com/live/{channel}", "Not set. Ads are blocked on the device only.");
        proxy.setTitle("Proxy URL");
        proxy.setDependency(Settings.BLOCK_ADS.key);
        screen.addPreference(proxy);
    }

    private void addEmoteSettings(PreferenceScreen screen) {
        addSwitch(screen, Settings.EMOTES_7TV, "7TV emotes", "Show global and channel 7TV emotes.");
        addSwitch(screen, Settings.EMOTES_BTTV, "BTTV emotes", "Show global and channel BetterTTV emotes.");
        addSwitch(screen, Settings.EMOTES_ANIMATED, "Animated emotes", "Play animated third-party emotes.");
        addSwitch(screen, Settings.EMOTES_PICKER, "Third-party emote picker", "Add enabled providers to Twitch's picker.");
        addSwitch(screen, Settings.EMOTES_AUTOCOMPLETE, "Third-party autocomplete", "Autocomplete third-party emote names.");
        addSwitch(screen, Settings.EMOTES_ZERO_WIDTH, "Zero-width emotes", "Support 7TV overlay emotes.");
    }

    private void addChatSettings(PreferenceScreen screen) {
        SwitchPreference deleted = addSwitch(screen, Settings.CHAT_DELETED_MESSAGES,
                "Deleted-message display", "Keep deleted chat messages visible.");
        Preference deletedStyle = addList(screen, Settings.CHAT_DELETED_MESSAGES_STYLE,
                "Deleted-message style",
                new String[]{"Default", "Moderator style", "Strikethrough", "Greyed out"},
                new String[]{"default", "mod", "strikethrough", "grey"});
        deletedStyle.setDependency(deleted.getKey());

        SwitchPreference timestamps = addSwitch(screen, Settings.CHAT_TIMESTAMPS,
                "Chat timestamps", "Show a timestamp beside messages.");
        Preference timestampFormat = addList(screen, Settings.CHAT_TIMESTAMP_FORMAT,
                "Timestamp format",
                new String[]{"12-hour", "12-hour + seconds", "24-hour", "24-hour + seconds"},
                new String[]{"h12", "h12s", "h24", "h24s"});
        timestampFormat.setDependency(timestamps.getKey());

        addSwitch(screen, Settings.HIDE_CHAT_BITS_BUTTON, "Hide Bits button",
                "Hide the Bits button beside Twitch's chat controls.");

        SwitchPreference size = addSwitch(screen, Settings.LANDSCAPE_CHAT_SIZE_ENABLED,
                "Custom landscape chat size", "Use a custom landscape chat width.");
        Preference sizeValue = addSlider(screen, Settings.LANDSCAPE_CHAT_SIZE, 5,
                "Landscape chat size", value -> value + "%");
        sizeValue.setDependency(size.getKey());

        SwitchPreference opacity = addSwitch(screen, Settings.LANDSCAPE_CHAT_OPACITY_ENABLED,
                "Custom landscape chat opacity", "Use a custom landscape chat transparency.");
        Preference opacityValue = addSlider(screen, Settings.LANDSCAPE_CHAT_OPACITY, 5,
                "Landscape chat opacity", value -> value + "%");
        opacityValue.setDependency(opacity.getKey());
    }

    private void addPlayerSettings(PreferenceScreen screen) {
        addSwitch(screen, Settings.SHOW_REFRESH_BUTTON, "Refresh stream button",
                "Show a player control that retries the current stream.");
        addSwitch(screen, Settings.VOLUME_GESTURE, "Volume swipe gesture", "Vertical swipe for volume.");
        addSwitch(screen, Settings.BRIGHTNESS_GESTURE, "Brightness swipe gesture", "Vertical swipe for brightness.");
        addSwitch(screen, Settings.GESTURE_OSD, "Gesture OSD", "Show bar and numeric feedback for gestures.");

        SwitchPreference forward = addSwitch(screen, Settings.CUSTOM_FORWARD_SEEK,
                "Custom forward seek", "Use a custom forward-skip duration.");
        Preference forwardValue = addSlider(screen, Settings.FORWARD_SEEK_SECONDS, 5,
                "Forward seek amount", value -> value + " seconds");
        forwardValue.setDependency(forward.getKey());

        SwitchPreference rewind = addSwitch(screen, Settings.CUSTOM_REWIND_SEEK,
                "Custom rewind seek", "Use a custom rewind duration.");
        Preference rewindValue = addSlider(screen, Settings.REWIND_SEEK_SECONDS, 5,
                "Rewind seek amount", value -> value + " seconds");
        rewindValue.setDependency(rewind.getKey());

        addSwitch(screen, Settings.SHOW_SLEEP_TIMER, "Sleep timer", "Show the sleep-timer control in the player.");
    }

    private void addInterfaceSettings(PreferenceScreen screen) {
        addSwitch(screen, Settings.HIDE_STORIES, "Hide Stories", "Remove Twitch Stories.");
        addSwitch(screen, Settings.HIDE_RECOMMENDATIONS, "Hide recommendations", "Remove recommendation sections.");
        addSwitch(screen, Settings.HIDE_FEATURED_CLIPS, "Hide Featured Clips", "Remove Featured Clips.");
        addSwitch(screen, Settings.FORCE_SEARCH_BUTTON, "Force Search button", "Keep Search available in the toolbar.");
    }

    private void addPrivacySettings(PreferenceScreen screen) {
        addSwitch(screen, Settings.DISABLE_COMSCORE, "Disable Comscore", "Disable Comscore analytics when the hook is active.");
        addSwitch(screen, Settings.DISABLE_BUGSNAG, "Disable Bugsnag", "Disable Bugsnag crash reporting when the hook is active.");
        Preference note = new Preference(screen.getContext());
        note.setSummary("Privacy SDK changes may require restarting Twitch.");
        note.setSelectable(false);
        screen.addPreference(note);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return super.onCreateView(inflater, container, savedInstanceState);
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
        TextView title = activity == null ? null : SettingsPatch.findToolbarTitle(activity);
        if (title == null) return;
        if (previousTitle == null) previousTitle = title.getText();
        title.setText(title(section));
    }

    @Override
    public void onDestroyView() {
        Activity activity = getActivity();
        TextView title = activity == null ? null : SettingsPatch.findToolbarTitle(activity);
        if (title != null && previousTitle != null) title.setText(previousTitle);
        super.onDestroyView();
    }

    private static String title(String section) {
        if (section == null) return SettingsPatch.TITLE;
        switch (section) {
            case SECTION_ADS: return "Ads";
            case SECTION_EMOTES: return "Emotes";
            case SECTION_CHAT: return "Chat";
            case SECTION_PLAYER: return "Player";
            case SECTION_INTERFACE: return "Interface";
            case SECTION_PRIVACY: return "Privacy";
            default: return SettingsPatch.TITLE;
        }
    }

    private static SwitchPreference addSwitch(PreferenceGroup group, BooleanSetting setting,
                                              String title, String summary) {
        SwitchPreference preference = new SwitchPreference(group.getContext());
        preference.setKey(setting.key);
        preference.setDefaultValue(setting.defaultValue);
        preference.setTitle(title);
        preference.setSummary(summary);
        group.addPreference(preference);
        return preference;
    }

    private static Preference addSlider(PreferenceGroup group, IntSetting setting, int step,
                                        String title, SliderPreference.Formatter formatter) {
        Preference preference = new SliderPreference(group.getContext(), setting, step, formatter);
        preference.setTitle(title);
        group.addPreference(preference);
        return preference;
    }

    private static Preference addList(PreferenceGroup group, StringSetting setting, String title,
                                      String[] entries, String[] values) {
        ListPreference preference = new ListPreference(group.getContext());
        preference.setKey(setting.key);
        preference.setDefaultValue(setting.defaultValue);
        preference.setTitle(title);
        preference.setEntries(entries);
        preference.setEntryValues(values);
        updateListSummary(preference, setting.get(), entries, values);
        preference.setOnPreferenceChangeListener((changed, newValue) -> {
            updateListSummary(preference, String.valueOf(newValue), entries, values);
            return true;
        });
        group.addPreference(preference);
        return preference;
    }

    private static void updateListSummary(ListPreference preference, String value,
                                          String[] entries, String[] values) {
        for (int i = 0; i < values.length && i < entries.length; i++) {
            if (values[i].equals(value)) {
                preference.setSummary(entries[i]);
                return;
            }
        }
        preference.setSummary(entries.length == 0 ? "" : entries[0]);
    }
}
