package chat.rox.android.sdk.impl.backend;

public interface SendOrDeleteMessageInternalCallback {
    void onSuccess(String response);

    void onFailure(String error);
}
