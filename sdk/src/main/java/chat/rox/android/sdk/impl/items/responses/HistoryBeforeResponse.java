package chat.rox.android.sdk.impl.items.responses;

import com.google.gson.annotations.SerializedName;
import chat.rox.android.sdk.impl.items.MessageItem;

import java.util.List;

public class HistoryBeforeResponse extends ErrorResponse {
    @SerializedName("result")
    private String result;
    @SerializedName("data")
    private HistoryResponseData data;

    public static class HistoryResponseData {
        @SerializedName("hasMore")
        private Boolean hasMore;
        @SerializedName("messages")
        private List<MessageItem> messages;

        public Boolean getHasMore() {
            return hasMore;
        }

        public List<MessageItem> getMessages() {
            return messages;
        }
    }

    public HistoryResponseData getData() {
        return data;
    }
}
