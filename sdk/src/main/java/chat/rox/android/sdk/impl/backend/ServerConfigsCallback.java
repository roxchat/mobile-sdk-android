package chat.rox.android.sdk.impl.backend;

import androidx.annotation.Nullable;

import chat.rox.android.sdk.impl.items.AccountConfigItem;
import chat.rox.android.sdk.impl.items.LocationSettingsItem;

public interface ServerConfigsCallback {
    void onServerConfigs(
        @Nullable AccountConfigItem accountConfigItem,
        @Nullable LocationSettingsItem locationSettingsItem
    );
}