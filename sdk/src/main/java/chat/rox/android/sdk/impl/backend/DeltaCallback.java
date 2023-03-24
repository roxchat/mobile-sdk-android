package chat.rox.android.sdk.impl.backend;

import androidx.annotation.NonNull;

import java.util.List;

import chat.rox.android.sdk.impl.items.delta.DeltaFullUpdate;
import chat.rox.android.sdk.impl.items.delta.DeltaItem;

public interface DeltaCallback {
    void onFullUpdate(@NonNull DeltaFullUpdate fullUpdate);

    void onDeltaList(@NonNull List<DeltaItem<?>> list);
}
