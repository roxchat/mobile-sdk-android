package chat.rox.android.sdk.impl;

import androidx.annotation.Nullable;

public interface MessageComposingHandler {
    void setComposingMessage(@Nullable String draftMessage);
}
