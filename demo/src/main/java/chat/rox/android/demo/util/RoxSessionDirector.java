package chat.rox.android.demo.util;

import static chat.rox.android.demo.SettingsFragment.KEY_ACCOUNT;
import static chat.rox.android.demo.SettingsFragment.KEY_FILE_LOGGER;
import static chat.rox.android.demo.SettingsFragment.KEY_LOCATION;
import static chat.rox.android.demo.SettingsFragment.KEY_NOTIFICATION;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessaging;

import chat.rox.android.demo.BuildConfig;
import chat.rox.android.sdk.Rox;
import chat.rox.android.sdk.RoxLog;
import chat.rox.android.sdk.RoxLogEntity;

public class RoxSessionDirector {

    public static void createSessionBuilderWithAnonymousVisitor(Context context, OnSessionBuilderCreatedListener listener) {
        Rox.SessionBuilder sessionBuilder = newSessionBuilder(context);
        retrieveFirebaseToken(context, sessionBuilder, listener);
    }

    public static void createSessionBuilderWithAuth1Visitor(Context context, OnSessionBuilderCreatedListener listener) {
        String visitorFieldAuthVersion1 =
            "{\"id\":\"1234567890987654321\"," +
                    "\"display_name\":\"Никита\"," +
                    "\"crc\":\"ffadeb6aa3c788200824e311b9aa44cb\"}";

        Rox.SessionBuilder sessionBuilder = newSessionBuilder(context).setVisitorFieldsJson(visitorFieldAuthVersion1);
        retrieveFirebaseToken(context, sessionBuilder, listener);
    }

    public static void createSessionBuilderWithAuth2Visitor(Context context, OnSessionBuilderCreatedListener listener) {
        String visitorFieldAuthVersion2 =
            "{\"fields\":{" +
                    "\"display_name\": \"Fedor\"," +
                    "\"email\": \"fedor@rox.chat\"," +
                    "\"phone\": \"123-3243\"," +
                    "\"id\": \"2113\"," +
                    "\"comment\": \"some\\ncomment\"," +
                    "\"info\": \"some\\ninfo\"," +
                    "\"icq\": \"12345678\"," +
                    "\"profile_url\": \"http:\\\\\\\\some-crm.ru\\\\id12345\"}," +
                    "\"expires\": 1605109076," +
                    "\"hash\": \"87233cd41af79e3736e73bfb12b803fb\"}";
        Rox.SessionBuilder sessionBuilder = newSessionBuilder(context).setVisitorFieldsJson(visitorFieldAuthVersion2);
        retrieveFirebaseToken(context, sessionBuilder, listener);
    }

    private static void retrieveFirebaseToken(Context context, Rox.SessionBuilder builder, OnSessionBuilderCreatedListener listener) {

        boolean hasFirebaseToken = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY_NOTIFICATION, true);

        if (!hasFirebaseToken) {
            listener.onSessionBuilderCreated(builder);
            return;
        }

        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                listener.onError(SessionBuilderError.FIREBASE_TOKEN_ERROR);
                return;
            }

            builder.setPushToken(task.getResult());
            listener.onSessionBuilderCreated(builder);
        });
    }

    private static Rox.SessionBuilder newSessionBuilder(Context context) {
        String DEFAULT_ACCOUNT_NAME = "demo";
        String DEFAULT_LOCATION = "mobile";
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        RoxLog roxLog = null;
        if (BuildConfig.DEBUG) {
            String roxLogTag = "rox_demo_log";
            if (sharedPref.getBoolean(KEY_FILE_LOGGER, false)) {
                roxLog = FileLogger.getAppSpecificDirLogger(context, roxLogTag + ".txt");
            } else {
                roxLog = log -> Log.i(roxLogTag, log);
            }
        }

        return Rox.newSessionBuilder()
            .setContext(context)
            .setAccountName(sharedPref.getString(KEY_ACCOUNT, DEFAULT_ACCOUNT_NAME))
            .setLocation(sharedPref.getString(KEY_LOCATION, DEFAULT_LOCATION))
            .setPushSystem(Rox.PushSystem.FCM)
            .setLoggerEntities(RoxLogEntity.SERVER)
            .setLogger(roxLog, Rox.SessionBuilder.RoxLogVerbosityLevel.VERBOSE);
    }

    public interface OnSessionBuilderCreatedListener {

        void onSessionBuilderCreated(Rox.SessionBuilder sessionBuilder);

        void onError(SessionBuilderError error);
    }

    public enum SessionBuilderError {
        FIREBASE_TOKEN_ERROR,
        AUTH_USER_ERROR
    }
}
