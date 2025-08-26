package chat.rox.android.sdk.impl.items.responses;

import com.google.gson.annotations.SerializedName;

import chat.rox.android.sdk.impl.items.AccountConfigItem;
import chat.rox.android.sdk.impl.items.LocationSettingsItem;

public class ServerConfigsResponse extends ErrorResponse {
    @SerializedName("locationSettings")
    private LocationSettingsItem locationSettings;
    @SerializedName("accountConfig")
    private AccountConfigItem accountConfig;

    public LocationSettingsItem getLocationSettings() {
        return locationSettings;
    }

    public AccountConfigItem getAccountConfig() {
        return accountConfig;
    }
}
