package chat.rox.android.sdk.impl.backend;

public interface SendKeyboardErrorListener {
    void onSuccess();

    void onFailure(String error);
}
