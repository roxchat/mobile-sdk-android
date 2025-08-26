package chat.rox.android.sdk.impl.backend;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

import chat.rox.android.sdk.RoxSession;

public interface RoxClient {

    void start();

    void pause();

    void resume();

    void stop();

    @NonNull RoxActions getActions();

    @Nullable AuthData getAuthData();

    @NonNull DeltaRequestLoop getDeltaRequestLoop();

    void setPushToken(@NonNull String token, @Nullable RoxSession.TokenCallback callback);

    void setRequestHeaders(@NonNull Map<String, String> headers);

    void switchHost();
}
