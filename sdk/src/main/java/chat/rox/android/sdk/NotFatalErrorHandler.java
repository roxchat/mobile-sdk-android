package chat.rox.android.sdk;

import androidx.annotation.NonNull;

/**
 * @see Rox.SessionBuilder#setNotFatalErrorHandler(FatalErrorHandler)
 */
public interface NotFatalErrorHandler {
    /**
     * This method is called when a fatal error occurs.
     * @param error
     */
    void onNotFatalError(@NonNull RoxError<NotFatalErrorType> error);

    enum NotFatalErrorType {

        /**
         * This error indicates no network connection.
         */
        NO_NETWORK_CONNECTION,

        /**
         * This error occurs when server is not available or another reason for SocketTimeoutException.
         */
        SOCKET_TIMEOUT_EXPIRED,

        /**
         * This error occurs when host unavailable with UnknownHostException.
         */
        UNKNOWN_HOST
    }
}
