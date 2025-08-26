package chat.rox.android.sdk.impl.items.responses;

import com.google.gson.annotations.SerializedName;

import java.util.List;

import chat.rox.android.sdk.impl.items.delta.DeltaFullUpdate;
import chat.rox.android.sdk.impl.items.delta.DeltaItem;

final public class DeltaResponse extends ErrorResponse {
    @SerializedName("revision")
    private String revision;
    @SerializedName("fullUpdate")
    private DeltaFullUpdate fullUpdate;
    @SerializedName("deltaList")
    private List<DeltaItem<?>> deltaList;

    public String getRevision() {
        return revision;
    }

    public DeltaFullUpdate getFullUpdate() {
        return fullUpdate;
    }

    public List<DeltaItem<?>> getDeltaList() {
        return deltaList;
    }
}
