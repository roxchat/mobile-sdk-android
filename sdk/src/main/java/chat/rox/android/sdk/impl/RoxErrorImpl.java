package chat.rox.android.sdk.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import chat.rox.android.sdk.RoxError;

public class RoxErrorImpl<T extends Enum> implements RoxError<T> {
    private final @NonNull T errorType;
    private final @Nullable String errorString;

    public RoxErrorImpl(@NonNull T errorType, @Nullable String errorString) {
        this.errorType = errorType;
        this.errorString = errorString;
    }

    @NonNull
    @Override
    public T getErrorType() {
        return errorType;
    }

    @NonNull
    @Override
    public String getErrorString() {
        return errorString == null ? errorType.toString() : errorString;
    }
}
