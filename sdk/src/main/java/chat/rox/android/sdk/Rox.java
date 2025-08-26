package chat.rox.android.sdk;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonObject;
import chat.rox.android.sdk.impl.FAQImpl;
import chat.rox.android.sdk.impl.InternalUtils;
import chat.rox.android.sdk.impl.MagicConstants;
import chat.rox.android.sdk.impl.MessageParsingErrorHandler;
import chat.rox.android.sdk.impl.ProvidedVisitorFields;
import chat.rox.android.sdk.impl.StringId;
import chat.rox.android.sdk.impl.RoxErrorImpl;
import chat.rox.android.sdk.impl.RoxSessionImpl;
import chat.rox.android.sdk.impl.backend.RoxInternalLog;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

public final class Rox {
    private Rox() {
    }

    /**
     * @return new builder of Rox session
     */
    @NonNull
    public static SessionBuilder newSessionBuilder() {
        return new SessionBuilder();
    }

    /**
     * @return new builder of FAQ session
     */
    public static FAQBuilder newFAQBuilder() {
        return new FAQBuilder();
    }

    /**
     * If GCM push-notifications ({@link SessionBuilder#setPushSystem}) are enabled for the session, then via {@link android.content.BroadcastReceiver}
     * you can receive pushes belonging to this session. This method is used to deserialize the received push-notifications:
     * <code><pre>
     *     RoxPushNotification push = Rox.parseGcmPushNotification(intent.getExtras());
     * </pre></code>
     *
     * @param bundle serialized push data (obtained via {@link android.content.Intent#getExtras})
     * @return a push notification object
     * @see SessionBuilder#setPushSystem
     * @see Rox#getGcmSenderId
     */
    @Nullable
    @Deprecated
    public static RoxPushNotification parseGcmPushNotification(@NonNull Bundle bundle) {
        return null;
    }

    /**
     * If FCM push-notifications ({@link SessionBuilder#setPushSystem}) are enabled for the session, then via {@link android.content.BroadcastReceiver}
     * you can receive pushes belonging to this session. This method is used to deserialize the received push-notifications:
     * <code><pre>
     *     RoxPushNotification push = Rox.parseFcmPushNotification(remoteMessage.getData());
     * </pre></code>
     *
     * @param message push data
     * @return a push notification object
     * @see SessionBuilder#setPushSystem
     */
    @Nullable
    public static RoxPushNotification parseFcmPushNotification(@NonNull String message) {
        return InternalUtils.parseFcmPushNotification(message);
    }

    /**
     * If FCM push-notifications ({@link SessionBuilder#setPushSystem}) are enabled for the session, then via {@link android.content.BroadcastReceiver}
     * you can receive pushes belonging to this session. This method is used to deserialize the received push-notifications:
     * <code><pre>
     *     RoxPushNotification push = Rox.parseFcmPushNotification(remoteMessage.getData(), visitorId);
     * </pre></code>
     *
     * @param message push data
     * @return a push notification object if visitorId equals push visitor id else null
     * @see SessionBuilder#setPushSystem
     */
    @Nullable
    public static RoxPushNotification parseFcmPushNotification(@NonNull String message,
                                                                 @NonNull String visitorId) {
        return InternalUtils.parseFcmPushNotification(message, visitorId);
    }

    /**
     * If push-notifications ({@link SessionBuilder#setPushSystem})are enabled for the session, then via {@link android.content.BroadcastReceiver}
     * you can receive pushes belonging to this session. This method returns "sender id" which is used to separate Rox notifications
     * from the ones of your application.
     * <code><pre>
     *     if(intent.getStringExtra("from").equals(Rox.getGcmSenderId())) {
     *         // process rox push-notification
     *     } else {
     *         // process your application push-notification
     *     }
     * </pre></code>
     *
     * @return GCM sender id of Rox push-notifications
     * @see SessionBuilder#setPushSystem
     * @see Rox#parseGcmPushNotification
     */
    @NonNull
    @Deprecated
    public static String getGcmSenderId() {
        return MagicConstants.ROX_GCM_SENDER_ID;
    }

    /**
     * @see SessionBuilder#setPushSystem
     */
    public enum PushSystem {
        /**
         * Do not use any push system
         */
        NONE("none"),
        /**
         * Use Firebase Cloud Messaging as push system
         */
        FCM("fcm"),
        /**
         * Use Huawei Push Kit as push system
         */
        HPK("hpk"),
        /**
         * Use Google Cloud Messaging as push system
         */
        @Deprecated
        GCM("gcm");

        @NonNull
        private final String pushName;

        PushSystem(@NonNull String pushName) {
            this.pushName = pushName;
        }

        @NonNull
        public String getPushName() {
            return pushName;
        }
    }

    /**
     * A {@link RoxSession} builder
     *
     * @see Rox#newSessionBuilder()
     */
    public static class SessionBuilder {

        @Nullable
        private String accountName;
        @Nullable
        private String appVersion;
        private boolean clearVisitorData;
        @Nullable
        private Context context;
        @Nullable
        private FatalErrorHandler errorHandler;
        @Nullable
        private String location;
        @NonNull
        String multivisitorSection = "";
        @Nullable
        private NotFatalErrorHandler notFatalErrorHandler;
        @Nullable
        private SharedPreferences preferences;
        @Nullable
        private ProvidedAuthorizationTokenStateListener providedAuthorizationTokenStateListener;
        @Nullable
        private String providedAuthorizationToken;
        @NonNull
        private PushSystem pushSystem = PushSystem.NONE;
        @Nullable
        private String pushToken;
        private boolean storeHistoryLocally = true;
        @Nullable
        private String title;
        @Nullable
        private ProvidedVisitorFields visitorFields;
        @Nullable
        private String prechatFields;
        private long requestFrequency;
        private List<String> extraDomains = Arrays.asList(
            "rox2.ru",
            "rox.chat"
        );


        @Nullable
        private Map<String, String> requestHeader;

        @Nullable
        private SSLSocketFactory sslSocketFactory;

        @Nullable
        private X509TrustManager trustManager;

        @Nullable
        private RoxSession.SessionCallback sessionCallback;
        @Nullable
        private MessageParsingErrorHandler messageParsingErrorHandler;

        private SessionBuilder() {
        }

        private static String hexToAscii(String hexStr) {
            int len = hexStr.length();
            byte[] data = new byte[len / 2];
            for (int i = 0; i < len; i += 2) {
                data[i / 2] = (byte) ((Character.digit(hexStr.charAt(i), 16) << 4)
                        + Character.digit(hexStr.charAt(i + 1), 16));
            }

            try {
                return new String(data, "UTF-8");
            } catch (UnsupportedEncodingException ignored) {
                return null;
            }
        }

        /**
         * Context is used to create a DB (to store the message history), to obtain a push-token, and also to get SharedPreferences
         * to store user's data ( if SharedPreferences are not clearly indicated by a call {@link #setVisitorDataPreferences}).
         *
         * @param context Your {@link android.app.Activity} or {@link android.app.Application}
         * @return this builder object
         * @see android.app.Activity
         * @see android.app.Application
         */
        public SessionBuilder setContext(@NonNull Context context) {
            context.getClass(); // NPE
            this.context = context;
            return this;
        }

        /**
         * When creating a Rox account you are given an account name. For example <i>"demo"</i>.
         * The account name is subsequently used to form a server domain name, for instance, <i>"https://demo.rox.chat"</i>.
         * As an account name you can also transmit the full address of the server (it begins with "https://")
         *
         * @param accountName name of Rox account (<i>"demo"</i>) or the full address of the server (<i>"https://demo.rox.chat"</i>)
         * @return this builder object
         */
        public SessionBuilder setAccountName(@NonNull String accountName) {
            accountName.getClass(); // NPE
            this.accountName = accountName;
            return this;
        }

        /**
         * Location on server. Use "mobile" or contact support for more info.
         *
         * @return this builder object
         * @see <a href="https://rox.chat/help/help-terms/#location">more info</a>
         */
        public SessionBuilder setLocation(@NonNull String location) {
            location.getClass(); // NPE
            this.location = location;
            return this;
        }

        /**
         * If is set, SDK will request location status every 60 seconds and fire listener.
         * @param requestFrequency - request frequency to server in millis.
         * @return this builder object
         * @see MessageStream#setOnlineStatusChangeListener(MessageStream.OnlineStatusChangeListener)
         */
        public SessionBuilder setOnlineStatusRequestFrequencyInMillis(long requestFrequency) {
            this.requestFrequency = requestFrequency;
            return this;
        }

        /**
         * Set spare domains in case the main domain is not available
         * @param extraDomains - list of extra domains
         * @return this builder object
         */
        public SessionBuilder setExtraDomains(List<String> extraDomains) {
            this.extraDomains = extraDomains;
            return this;
        }

        /**
         * You can differentiate your app versions on server by setting this parameter.
         * E.g. "2.9.11".
         * This is optional.
         *
         * @param appVersion Client app version name
         * @return This builder object.
         */
        public SessionBuilder setAppVersion(@NonNull String appVersion) {
            appVersion.getClass(); // NPE

            this.appVersion = appVersion;
            return this;
        }

        /**
         * Rox-session must store some information about current visitor.
         * For instance, the id of the visitor, name of the DB, where the local message history is
         * stored. For these purposes {@link SharedPreferences} are used.
         * If this method isn't clearly called when creating a session, SDK creates encrypted preferences
         * which are received from the context {@link Context} defined by method
         * {@link #setContext}, and besides for <b>different</b> visitors are used different
         * preferences. Different visitors are those
         * who are authorized with different id's.
         * Can't be used simultaneously with
         * {@link #setProvidedAuthorizationTokenStateListener(ProvidedAuthorizationTokenStateListener, String)}
         *
         * @param preferences Android {@link SharedPreferences} object to store visitor data
         * @return this builder object
         * @see #setVisitorFieldsJson подробнее об авторизации
         */
        public SessionBuilder setVisitorDataPreferences(@NonNull SharedPreferences preferences) {
            preferences.getClass(); // NPE
            this.preferences = preferences;
            return this;
        }

        private static boolean isJSON(String input) {
            try {
                new JSONObject(input);
                return true;
            } catch (JSONException ignored) {
            }

            return false;
        }

        /**
         * A visitor can be anonymous or authorized. Without calling this method when creating a
         * session a visitor is anonymous.
         * In this case a visitor is given a random id, which is written in
         * {@link SharedPreferences} (defined by method
         * {@link #setVisitorDataPreferences} or received from {@link Context}). If the data is
         * lost (for example when reinstalling the application),
         * the user's id is also lost, as well as the message history.
         * Authorizing of a visitor can be useful when in your application there are internal
         * mechanisms of authorization
         * and you want the message history to exist regardless of a device communication occurs
         * from.
         * <br />
         * This method takes as a parameter a string containing the signed fields of a user in JSON
         * format. Since the fields are necessary to be signed
         * with a private key that can never be included into the code of a client's application,
         * this string must
         * be formed and signed somewhere on your backend. Read more about forming a string and a
         * signature at
         * <a href=https://rox.chat/help/identification/>official documentation<a/>
         * Can't be used simultaneously with
         * {@link #setProvidedAuthorizationTokenStateListener(ProvidedAuthorizationTokenStateListener, String)}
         *
         * @param visitorFieldsJson JSON-string containing the signed field of a visitor
         * @return this builder object
         * @see <a href=https://rox.chat/help/identification/>Identification doc<a/>
         */
        public SessionBuilder setVisitorFieldsJson(@NonNull String visitorFieldsJson) {
            visitorFieldsJson.getClass(); // NPE
            this.visitorFields = new ProvidedVisitorFields(visitorFieldsJson);
            return this;
        }

        private static String hexToJson(String hexStr) {
            String asciiStr = hexToAscii(hexStr);

            if (asciiStr != null) {
                String result = asciiStr.replaceAll(
                        "([^:]*):\\s*([^:]*)(\\\\n|$)",
                        "\"$1\":\"$2\", ");
                return "{" + result.substring(0, result.length() - 2) + "}";
            } else {
                return null;
            }
        }

        /**
         * Set prechat fields with extra information.
         *
         * @param prechatFields extra information in JSON format
         * @return this builder object
         */
        public SessionBuilder setPrechatFields(@NonNull String prechatFields) {
            prechatFields.getClass(); // NPE

            if (isJSON(prechatFields)) {
                this.prechatFields = prechatFields;
                return this;
            }

            String prechatFieldsJson = hexToJson(prechatFields);
            if (isJSON(prechatFieldsJson)) {
                this.prechatFields = prechatFieldsJson;
            } else {
                this.prechatFields = null;
            }

            return this;
        }

        /**
         * @see #setVisitorFieldsJson(String)
         */
        public SessionBuilder setVisitorFieldsJson(@NonNull JsonObject visitorFieldsJson) {
            visitorFieldsJson.getClass(); // NPE
            this.visitorFields = new ProvidedVisitorFields(visitorFieldsJson);
            return this;
        }

        /**
         * When client provides custom visitor authorization mechanism, it can be realised by
         * providing custom authorization token which is used instead of visitor fields.
         * Can't be used simultaneously with {@link #setVisitorFieldsJson(String)}
         * or {@link #setVisitorFieldsJson(JsonObject)}
         *
         * @param providedAuthorizationTokenStateListener {@link ProvidedAuthorizationTokenStateListener} object
         * @param providedAuthorizationToken              Client generated provided authorization token.
         *                                                If it is not passed, library generates its own
         * @return
         * @see ProvidedAuthorizationTokenStateListener
         */
        public SessionBuilder setProvidedAuthorizationTokenStateListener(
                @NonNull ProvidedAuthorizationTokenStateListener
                        providedAuthorizationTokenStateListener,
                @Nullable String
                        providedAuthorizationToken) {
            this.providedAuthorizationTokenStateListener = providedAuthorizationTokenStateListener;
            this.providedAuthorizationToken = providedAuthorizationToken;

            return this;
        }

        /**
         * Sets the 'page title' visible to an operator. In the web-version of a chat it is a title of a web-page a user opens a chat from.
         * By default "Android Client"
         *
         * @param title page title, visible to an operator
         * @return this builder object
         */
        public SessionBuilder setTitle(@NonNull String title) {
            title.getClass(); // NPE
            this.title = title;
            return this;
        }

        /**
         * Installs a fatal error handler. An error is fatal if after processing it the session can not be continued anymore.
         *
         * @param errorHandler fatal error handler
         * @return this builder object
         */
        public SessionBuilder setErrorHandler(@NonNull FatalErrorHandler errorHandler) {
            errorHandler.getClass(); // NPE
            this.errorHandler = errorHandler;
            return this;
        }

        public SessionBuilder setNotFatalErrorHandler(@NonNull NotFatalErrorHandler notFatalErrorHandler) {
            this.notFatalErrorHandler = notFatalErrorHandler;
            return this;
        }

        /**
         * Rox-service can send push-notifications about new messages in a chat. By default pushes are not sent.
         * You have to receive push notifications by yourself via {@link android.content.BroadcastReceiver}.
         *
         * @param pushSystem enum parameter indicating which system of pushes to use. By default  NONE (pushes are not sent).
         * @return this builder object
         */
        public SessionBuilder setPushSystem(@NonNull PushSystem pushSystem) {
            pushSystem.getClass(); // NPE
            this.pushSystem = pushSystem == PushSystem.GCM ? PushSystem.NONE : pushSystem;
            return this;
        }

        /**
         * Installs GCMSenderId.
         *
         * @param senderId
         * @return this builder object
         */
        @Deprecated
        public SessionBuilder setGCMSenderId(@NonNull String senderId) {
            senderId.getClass();
            MagicConstants.ROX_GCM_SENDER_ID = senderId;
            return this;
        }

        /**
         * This method allows you to manually set the push token.
         *
         * @param pushToken The push token, used
         * @return this builder object
         * @see SessionBuilder#setPushSystem
         */
        public SessionBuilder setPushToken(@Nullable String pushToken) {
            this.pushToken = pushToken;
            return this;
        }

        /**
         * @param logger         RoxLog object
         * @param verbosityLevel log level
         * @return this builder object
         * @see RoxLog
         */
        public SessionBuilder setLogger(RoxLog logger, RoxLogVerbosityLevel verbosityLevel) {
            RoxInternalLog.getInstance().setLogger(logger);
            RoxInternalLog.getInstance().setVerbosityLevel(verbosityLevel);
            return this;
        }

        /**
         * Method sets logging areas. This should be considered a logging area.
         * By default SERVER area is set. This means that sdk will only log the interaction
         * with the server. But for testing and debug purposes it can useful to log other areas.
         * @return this builder object
         * @see RoxLogEntity
         * */
        public SessionBuilder setLoggerEntities(RoxLogEntity... logEntities) {
            RoxInternalLog.getInstance().setLogEntities(new HashSet<>(Arrays.asList(logEntities)));
            return this;
        }

        /**
         * By default a session <b>stores</b> a message history locally. This method allows to disable history storage.
         * Use only for debugging.
         *
         * @return this builder object
         */
        public SessionBuilder setStoreHistoryLocally(boolean storeHistoryLocally) {
            this.storeHistoryLocally = storeHistoryLocally;
            return this;
        }

        /**
         * If set to true, all the visitor data is cleared before the session starts. Use only for debugging
         *
         * @return this builder object
         */
        public SessionBuilder setClearVisitorData(boolean clearVisitorData) {
            this.clearVisitorData = clearVisitorData;
            return this;
        }

        /**
         * Sets sslFactory and trustedManager for okHttp client
         * @param sslSocketFactory ssl factory
         * @param trustManager trust manager
         * @return this builder object
         */
        public SessionBuilder setSslSocketFactoryAndTrustManager(SSLSocketFactory sslSocketFactory,
                                                                 X509TrustManager trustManager) {
            this.sslSocketFactory = sslSocketFactory;
            this.trustManager = trustManager;
            return this;
        }

        /**
         * If set different section for visitors, different visitors can receive remote notifications on one device.
         *
         * @return this builder object
         */
        public SessionBuilder setMultivisitorSection(String multivisitorSection) {
            this.multivisitorSection = multivisitorSection;
            return this;
        }

        /**
         * Sets message parsing listener that handles error while trying deserialize raw message
         *
         * @return this builder object
         */
        public SessionBuilder setMessageParsingErrorHandler(MessageParsingErrorHandler messageParsingErrorHandler) {
            this.messageParsingErrorHandler = messageParsingErrorHandler;
            return this;
        }

        /**
         * Adds header to network requests
         *
         * @return this builder object
         */
        public SessionBuilder setRequestHeader(Map<String, String> requestHeader) {
            this.requestHeader = requestHeader;
            return this;
        }

        /**
         * Builds new {@link RoxSession} object. This method must be called from the thread
         * {@link android.os.Looper}
         * (for instance, from the main thread of the application), and all the follow-up work with
         * the session must be implemented from the same stream.
         * Notice that a session is created as a paused, i.e. to start using it
         * the first thing to do is to call {@link RoxSession#resume()}.
         *
         * @param callback - callback that is called when session was successfully created on server.
         * @return new {@link RoxSession} object or null if error was occurred while creating new
         * session.
         */
        @Nullable
        public RoxSession build(final @NonNull RoxSession.SessionCallback callback) {
            this.sessionCallback = callback;
            try {
                return build();
            } catch (Exception exception) {
                callback.onFailure(new RoxErrorImpl<>(
                        RoxSession.SessionCallback.SessionError.INVALID_PARAMETER_VALUE,
                        null
                ));
                return null;
            }
        }

        /**
         * Builds new {@link RoxSession} object. This method must be called from the thread
         * {@link android.os.Looper}
         * (for instance, from the main thread of the application), and all the follow-up work with
         * the session must be implemented from the same stream.
         * Notice that a session is created as a paused, i.e. to start using it
         * the first thing to do is to call {@link RoxSession#resume()}.
         *
         * @return new {@link RoxSession} object
         */
        public RoxSession build() {
            if (context == null) {
                throw new IllegalArgumentException("context can't be null! " +
                        "Use setContext() to set appropriate context");
            }

            if (accountName == null) {
                throw new IllegalArgumentException("account name can't be null! " +
                        "Use setAccountName() to set appropriate account name");
            }

            if (location == null) {
                throw new IllegalArgumentException("location can't be null! " +
                        "Use setLocation() to set appropriate location");
            }

            if ((pushToken != null) && (pushSystem == PushSystem.NONE)) {
                throw new IllegalArgumentException("can't set push token with disabled pushes. " +
                        "Use setPushSystem() to enable pushes");
            }

            if ((visitorFields != null) && (providedAuthorizationTokenStateListener != null)) {
                throw new IllegalStateException("Tried to use standard and custom visitor fields " +
                        "authentication simultaneously.");
            }

            if (providedAuthorizationTokenStateListener != null) {
                if (providedAuthorizationToken == null) {
                    providedAuthorizationToken = StringId.generateClientSide();
                }

                providedAuthorizationTokenStateListener
                        .updateProvidedAuthorizationToken(providedAuthorizationToken);
            }

            return RoxSessionImpl.newInstance(
                    context,
                    preferences,
                    accountName,
                    location,
                    appVersion,
                    visitorFields,
                    prechatFields,
                    providedAuthorizationTokenStateListener,
                    providedAuthorizationToken,
                    title,
                    errorHandler,
                    notFatalErrorHandler,
                    pushSystem,
                    pushToken,
                    storeHistoryLocally,
                    clearVisitorData,
                    sslSocketFactory,
                    trustManager,
                    multivisitorSection,
                    sessionCallback,
                    requestFrequency,
                    messageParsingErrorHandler,
                    requestHeader,
                    extraDomains
            );
        }

        public enum RoxLogVerbosityLevel {
            /**
             * All available information will be delivered to {@link RoxLog} instance with maximum verbosity level:
             * <ul>
             * <li>session network setup parameters;</li>
             * <li>network requests' URLs, HTTP method and parameters;</li>
             * <li>network responses' HTTP codes, received data and errors;</li>
             * <li>SQL queries and errors;</li>
             * <li>full debug information and additional notes.</li>
             * </ul>
             */
            VERBOSE,

            /**
             * All information which is useful when debugging will be delivered to {@link RoxLog} instance with necessary verbosity level:
             * <ul>
             * <li>session network setup parameters;</li>
             * <li>network requests' URLs, HTTP method and parameters;</li>
             * <li>network responses' HTTP codes, received data and errors;</li>
             * <li>SQL queries and errors;</li>
             * <li> moderate debug information.</li>
             * </ul>
             */
            DEBUG,

            /**
             * Reference information and all warnings and errors will be delivered to {@link RoxLog} instance:
             * <ul>
             * <li>network requests' URLS, HTTP method and parameters;</li>
             * <li>HTTP codes and errors descriptions of failed requests.</li>
             * <li>SQL errors.</li>
             * </ul>
             */
            INFO,

            /**
             * Errors and warnings only will be delivered to {@link RoxLog} instance:
             * <ul>
             * <li>network requests' URLs, HTTP method, parameters, HTTP code and error description.</li>
             * <li>SQL errors.</li>
             * </ul>
             */
            WARNING,

            /**
             * Only errors will be delivered to {@link RoxLog} instance:
             * network requests' URLs, HTTP method, parameters, HTTP code and error description.
             */
            ERROR
        }

    }

    /**
     * A {@link FAQ} builder
     *
     * @see Rox#newFAQBuilder()
     */
    public static class FAQBuilder {

        @Nullable
        private String accountName;
        @Nullable
        private String application;
        @Nullable
        private Context context;
        @Nullable
        private String departmentKey;
        @Nullable
        private String language;
        @Nullable
        private SSLSocketFactory sslSocketFactory;
        @Nullable
        private X509TrustManager trustManager;

        private FAQBuilder() {
        }

        /**
         * When creating a Rox account you are given an account name. For example <i>"demo"</i>.
         * The account name is subsequently used to form a server domain name, for instance, <i>"https://demo.rox.chat"</i>.
         * As an account name you can also transmit the full address of the server (it begins with "https://")
         *
         * @param accountName name of Rox account (<i>"demo"</i>) or the full address of the server (<i>"https://demo.rox.chat"</i>)
         * @return this builder object
         */
        public FAQBuilder setAccountName(@NonNull String accountName) {
            this.accountName = accountName;
            return this;
        }

        /**
         * Sets application
         * @param application application
         * @return this builder object
         */
        public FAQBuilder setApplication(@NonNull String application) {
            this.application = application;
            return this;
        }

        /**
         * Context is used to create a DB (to store cache).
         *
         * @param context Your {@link android.app.Activity} or {@link android.app.Application}
         * @return this builder object
         * @see android.app.Activity
         * @see android.app.Application
         */
        public FAQBuilder setContext(@NonNull Context context) {
            this.context = context;
            return this;
        }

        /**
         * Sets department key
         * @param departmentKey department key
         * @return this builder object
         */
        public FAQBuilder setDepartmentKey(@NonNull String departmentKey) {
            this.departmentKey = departmentKey;
            return this;
        }

        /**
         * Sets language
         * @param language language
         * @return this builder object
         */
        public FAQBuilder setLanguage(@NonNull String language) {
            this.language = language;
            return this;
        }

        /**
         * Sets sslFactory and trustedManager for okHttp client
         * @param sslSocketFactory ssl factory
         * @param trustManager trust manager
         * @return this builder object
         */
        public FAQBuilder setSslSocketFactoryAndTrustManager(SSLSocketFactory sslSocketFactory,
                                                                 X509TrustManager trustManager) {
            this.sslSocketFactory = sslSocketFactory;
            this.trustManager = trustManager;
            return this;
        }

        /**
         * Builds new {@link RoxSession} object. This method must be called from the thread
         * {@link android.os.Looper}
         * (for instance, from the main thread of the application), and all the follow-up work with
         * the session must be implemented from the same stream.
         * Notice that a session is created as a paused, i.e. to start using it
         * the first thing to do is to call {@link RoxSession#resume()}.
         *
         * @return new {@link RoxSession} object
         */

        public FAQ build() {

            if (accountName == null) {
                throw new IllegalArgumentException("account name can't be null! " +
                        "Use setAccountName() to set appropriate account name");
            }

            if (context == null) {
                throw new IllegalArgumentException("context can't be null! " +
                        "Use setContext() to set appropriate context");
            }

            return FAQImpl.newInstance(
                    accountName,
                    application,
                    context,
                    departmentKey,
                    language,
                    sslSocketFactory,
                    trustManager
            );
        }

    }
}
