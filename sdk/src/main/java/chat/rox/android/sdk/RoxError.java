package chat.rox.android.sdk;

import androidx.annotation.NonNull;

/**
 * A generic rox error type
 * @param <T> the type of the error
 * @see FatalErrorHandler
 */
public interface RoxError<T extends Enum> {
    /**
     * @return the parsed type of the error
     */
    @NonNull T getErrorType();

    /**
     * @return string representation of the error. Mostly useful if the error type is unknown
     */
    @NonNull String getErrorString();
}
