package chat.rox.android.sdk.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import chat.rox.android.sdk.Message;
import chat.rox.android.sdk.MessageListener;
import chat.rox.android.sdk.MessageTracker;
import chat.rox.android.sdk.impl.items.ChatItem;

import java.util.List;
import java.util.Set;

public interface MessageHolder {
    MessageTracker newMessageTracker(@NonNull MessageListener messageListener);

    void receiveHistoryUpdate(List<? extends MessageImpl> messages,
                              Set<String> deleted,
                              Runnable callback);

    void setReachedEndOfRemoteHistory(boolean reachedEndOfHistory);

    void onFirstFullUpdateReceived();

    void onChatReceive(@Nullable ChatItem oldChat,
                       @Nullable ChatItem newChat,
                       List<? extends MessageImpl> newMessages);

    void onMessageAdded(@NonNull MessageImpl msg);

    void onMessageChanged(@NonNull MessageImpl newMessage);

    void onMessageDeleted(@NonNull String idInCurrentChat);

    void onSendingMessage(@NonNull MessageSending message);

    @Nullable
    String onChangingMessage(@NonNull Message.Id id, @Nullable String text);

    void onMessageSendingCancelled(@NonNull Message.Id id);

    void onMessageChangingCancelled(@NonNull Message.Id id, @NonNull String text);

    void updateReadBeforeTimestamp(Long timestamp);

    boolean historyMessagesEmpty();

    void clearHistory();
}
