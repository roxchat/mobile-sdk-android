package chat.rox.android.sdk;

import androidx.annotation.NonNull;

/**
 * @see Rox.SessionBuilder#setErrorHandler(FatalErrorHandler)
 */
public interface FatalErrorHandler {
    /**
     * This method is called when a fatal error occurs. Notice that the session will be destroyed <b>before</b> this method is called.
     * @param error
     */
    void onError(@NonNull RoxError<FatalErrorType> error);

    enum FatalErrorType {
        /**
		 * Indicates the occurrence of an unknown error.
		 * The recommended response is to send an automatic bug report (for example, Crashlytics) and show the user
		 * an error message with the recommendation to try using the chat later. 
         * @see RoxError#getErrorString()
         */
        UNKNOWN,

        /**
		 * This error means that the account in the rox service has been disabled. It can be disabled, for instance, for non-payment.
         * The error is unrelated to the user’s actions.
         * The recommended response is to show the user an error message with a recommendation to try using the chat later.

         */
        ACCOUNT_BLOCKED,

        /**
         * This error indicates that a visitor is disabled by an operator and can't send messages to a chat. The error occurs when 
         * a user tries to open the chat or write a message, if this user had been previously disabled by an operator. 
         * The recommended response is to show the user an error message with the recommendation to try using the chat later.
		 */
        VISITOR_BANNED,

        /**
		 * This error occurs when trying to authorize a visitor with a non-valid signature. The error indicates a problem
         * of your application authorization mechanism and is unrelated to the user’s actions.
         * The recommended response is to send an automatic bug report (for example, Crashlytics) and show the user 
         * an error message with the recommendation to try using the chat later.
         * @see Rox.SessionBuilder#setVisitorFieldsJson
         */
        WRONG_PROVIDED_VISITOR_HASH,

        /**
		 * This error indicates an expired authorization of a visitor. 
         * The recommended response is to reauthorize and to recreate a session.
         * @see Rox.SessionBuilder#setVisitorFieldsJson
         */
        PROVIDED_VISITOR_EXPIRED,

        /**
         * This error occurs when server has returned an unexpected response after initialization
         * request.
         */
        INCORRECT_SERVER_ANSWER,

        /*
        * This error occurs when any security-related exception has happened while creating encrypted preference file.
        * It means you cannot use the session in secure mode, which means the data in SharedPreferences will not be encrypted.
        * */
        GENERAL_SECURITY_ERROR
    }
}
