package chat.rox.android.sdk.impl;

import java.util.List;

public interface HistoryBeforeCallback {
    void onSuccess(List<? extends MessageImpl> messages, boolean hasMore);
}
