package chat.rox.android.sdk.impl.backend;

import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import chat.rox.android.sdk.BuildConfig;
import chat.rox.android.sdk.ProvidedAuthorizationTokenStateListener;
import chat.rox.android.sdk.Rox;
import chat.rox.android.sdk.RoxSession;
import chat.rox.android.sdk.impl.backend.callbacks.DeltaCallback;
import chat.rox.android.sdk.impl.items.delta.DeltaFullUpdate;
import chat.rox.android.sdk.impl.items.delta.DeltaItem;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RoxClientBuilder {

    private static final int ACTION_TIMEOUT_READ_IN_SECONDS = 60;
    private static final int DELTA_TIMEOUT_READ_IN_SECONDS = 44;
    private static final int TIMEOUT_WRITE_IN_SECONDS = 60;
    private static final int TIMEOUT_CALL_IN_SECONDS = 60;
    private static final String USER_AGENT_FORMAT = "Android: Rox-Client/%s (%s; Android %s)";
    private static final String USER_AGENT_STRING
            = String.format(USER_AGENT_FORMAT, BuildConfig.VERSION_NAME,
            Build.MODEL, Build.VERSION.RELEASE);
    private String appVersion;
    private AuthData authData;
    private String baseUrl;
    private Executor callbackExecutor;
    private DeltaCallback deltaCallback;
    private String deviceId;
    private InternalErrorListener errorListener;
    private String location;
    private String platform;
    private String providedAuthorizationToken;
    private ProvidedAuthorizationTokenStateListener providedAuthorizationTokenStateListener;
    private Rox.PushSystem pushSystem;
    private String pushToken;
    private String sessionId;
    private SessionParamsListener sessionParamsListener;
    private String title;
    private String visitorFieldsJson;
    private String visitorJson;
    private String prechatFields;
    private SSLSocketFactory sslSocketFactory;
    private X509TrustManager trustManager;
    private RoxSession.SessionCallback sessionCallback;


    public RoxClientBuilder() {

    }

    public RoxClientBuilder setBaseUrl(@NonNull String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    public RoxClientBuilder setLocation(@NonNull String location) {
        this.location = location;
        return this;
    }

    public RoxClientBuilder setAppVersion(@Nullable String appVersion) {
        this.appVersion = appVersion;
        return this;
    }

    public RoxClientBuilder setVisitorFieldsJson(@Nullable String visitorFieldsJson) {
        this.visitorFieldsJson = visitorFieldsJson;
        return this;
    }

    public RoxClientBuilder setPushToken(@NonNull Rox.PushSystem pushSystem,
                                           @Nullable String pushToken) {
        this.pushSystem = pushSystem;
        this.pushToken = pushToken;
        return this;
    }

    public RoxClientBuilder setVisitorJson(@Nullable String visitorJson) {
        this.visitorJson = visitorJson;
        return this;
    }

    public RoxClientBuilder setProvidedAuthorizationListener(
            @Nullable ProvidedAuthorizationTokenStateListener
                    providedAuthorizationTokenStateListener) {
        this.providedAuthorizationTokenStateListener = providedAuthorizationTokenStateListener;
        return this;
    }

    public RoxClientBuilder setProvidedAuthorizationToken(
            @Nullable String providedAuthorizationToken) {
        this.providedAuthorizationToken = providedAuthorizationToken;
        return this;
    }

    public RoxClientBuilder setSessionId(@Nullable String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    public RoxClientBuilder setAuthData(@Nullable AuthData authData) {
        this.authData = authData;
        return this;
    }

    public RoxClientBuilder setCallbackExecutor(@Nullable Executor callbackExecutor) {
        this.callbackExecutor = callbackExecutor;
        return this;
    }

    public RoxClientBuilder setDeltaCallback(@NonNull DeltaCallback deltaCallback) {
        this.deltaCallback = deltaCallback;
        return this;
    }

    public RoxClientBuilder setSessionParamsListener(SessionParamsListener sessionParamsListener) {
        this.sessionParamsListener = sessionParamsListener;
        return this;
    }

    public RoxClientBuilder setErrorListener(@NonNull InternalErrorListener errorListener) {
        this.errorListener = errorListener;
        return this;
    }

    public RoxClientBuilder setPlatform(String platform) {
        this.platform = platform;
        return this;
    }

    public RoxClientBuilder setTitle(String title) {
        this.title = title;
        return this;
    }

    public RoxClientBuilder setDeviceId(String deviceId) {
        this.deviceId = deviceId;
        return this;
    }

    public RoxClientBuilder setPrechatFields(String prechatFields) {
        this.prechatFields = prechatFields;
        return this;
    }

    private static RoxService setupRoxService(String url,
                                                  boolean isDelta,
                                                  SSLSocketFactory sslSocketFactory,
                                                  X509TrustManager trustManager) {
        return new Retrofit.Builder()
                .baseUrl(url)
                .client(setupHttpClient(isDelta, sslSocketFactory, trustManager))
                .addConverterFactory(GsonConverterFactory.create(setupGson()))
                .build()
                .create(RoxService.class);
    }

    private static OkHttpClient setupHttpClient(final boolean isDelta,
                                                final SSLSocketFactory sslSocketFactory,
                                                final X509TrustManager trustManager) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.addInterceptor(new Interceptor() {
            @Override
            public Response intercept(Interceptor.Chain chain) throws IOException {
                Request request = chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT_STRING)
                        .build();
                return chain.proceed(request);
            }
        });
        builder.readTimeout(isDelta
                        ? DELTA_TIMEOUT_READ_IN_SECONDS
                        : ACTION_TIMEOUT_READ_IN_SECONDS,
                TimeUnit.SECONDS);
        builder.writeTimeout(TIMEOUT_WRITE_IN_SECONDS, TimeUnit.SECONDS);
        builder.callTimeout(TIMEOUT_CALL_IN_SECONDS, TimeUnit.SECONDS);
        if (sslSocketFactory != null && trustManager != null) {
            builder.sslSocketFactory(sslSocketFactory, trustManager);
        }
        if (RoxInternalLog.getInstance() != null) {
            builder.addInterceptor(new RoxHttpLoggingInterceptor(new RoxHttpLoggingInterceptor.Logger() {
                @Override
                public void log(String message) {
                    if (isDelta) {
                        RoxInternalLog.getInstance().setLastDeltaResponseJSON(message);
                    } else {
                        RoxInternalLog.getInstance().setLastActionResponseJSON(message);
                    }
                }
            }));
        }

        return builder.build();
    }

    public RoxClientBuilder setSslSocketFactoryAndTrustManager(SSLSocketFactory sslSocketFactory,
                                                                 X509TrustManager trustManager) {
        this.sslSocketFactory = sslSocketFactory;
        this.trustManager = trustManager;
        return this;
    }

    public RoxClientBuilder setSessionCallback(RoxSession.SessionCallback sessionCallback) {
        this.sessionCallback = sessionCallback;
        return this;
    }

    public RoxClient build() {
        if ((baseUrl == null)
                || (location == null)
                || (deltaCallback == null)
                || (platform == null)
                || (title == null)
                || (errorListener == null)) {
            throw new IllegalStateException("baseUrl, location, deltaCallback, platform, title " +
                    "and errorListener must be set to non-null value.");
        }
        if (callbackExecutor == null) {
            throw new IllegalStateException("callbackExecutor must be set to non-null value.");
        }

        RoxService deltaService = setupRoxService(baseUrl, true,
                sslSocketFactory, trustManager);
        RoxService actionsService = setupRoxService(baseUrl, false,
                sslSocketFactory, trustManager);

        ActionRequestLoop requestLoop = new ActionRequestLoop(callbackExecutor, errorListener);
        requestLoop.setAuthData(authData);

        ActionRequestLoop pollerLoop = new ActionRequestLoop(callbackExecutor, errorListener);
        pollerLoop.setAuthData(authData);

        DeltaRequestLoop delta = new DeltaRequestLoop(
                deltaCallback,
                new SessionParamsListenerWrapper(sessionParamsListener, requestLoop, pollerLoop),
                callbackExecutor,
                errorListener,
                deltaService,
                platform,
                title,
                location,
                appVersion,
                visitorFieldsJson,
                providedAuthorizationTokenStateListener,
                providedAuthorizationToken,
                deviceId,
                prechatFields,
                pushSystem,
                pushToken,
                visitorJson,
                sessionId,
                authData,
                sessionCallback
        );

        return new RoxClientImpl(requestLoop, delta, pollerLoop,
                new RoxActionsImpl(actionsService, requestLoop, pollerLoop));
    }

    private static Gson setupGson() {
        return new GsonBuilder()
                .registerTypeAdapter(DeltaItem.class, new DeltaDeserializer())
                .registerTypeAdapter(DeltaFullUpdate.class, new DeltaFullUpdateDeserializer())
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create();
    }

    /**
     * Need to update pushToken in DeltaRequestLoop on update in RoxActions
     */
    private static class RoxClientImpl implements RoxClient {
        @NonNull
        private final RoxActionsImpl actions;
        @NonNull
        private final DeltaRequestLoop deltaLoop;
        @NonNull
        private final ActionRequestLoop requestLoop;
        @NonNull
        private final ActionRequestLoop pollerLoop;

        private RoxClientImpl(@NonNull ActionRequestLoop requestLoop,
                                @NonNull DeltaRequestLoop deltaLoop,
                                @NonNull ActionRequestLoop pollerLoop,
                                @NonNull RoxActionsImpl actions) {
            this.requestLoop = requestLoop;
            this.deltaLoop = deltaLoop;
            this.pollerLoop = pollerLoop;
            this.actions = actions;
        }

        @Override
        public void start() {
            requestLoop.start();
            deltaLoop.start();
            pollerLoop.start();
        }

        @Override
        public void pause() {
            requestLoop.pause();
            deltaLoop.pause();
            pollerLoop.pause();
        }

        @Override
        public void resume() {
            requestLoop.resume();
            deltaLoop.resume();
            pollerLoop.resume();

        }

        @Override
        public void stop() {
            deltaLoop.stop();
            requestLoop.stop();
            pollerLoop.stop();
        }

        @NonNull
        @Override
        public RoxActions getActions() {
            return actions;
        }

        @Nullable
        @Override
        public AuthData getAuthData() {
            return deltaLoop.getAuthData();
        }

        @NonNull
        @Override
        public DeltaRequestLoop getDeltaRequestLoop() {
            return deltaLoop;
        }

        @Override
        public void setPushToken(@NonNull String token, @Nullable RoxSession.TokenCallback callback) {
            deltaLoop.setPushToken(token);
            actions.updatePushToken(token, callback);
        }
    }

    /**
     * Need to update AuthData in ActionRequestLoop on update in DeltaRequestLoop
     */
    private static class SessionParamsListenerWrapper implements SessionParamsListener {
        @Nullable
        private final SessionParamsListener wrapped;
        @NonNull
        private final ActionRequestLoop requestLoop;
        @NonNull
        private final ActionRequestLoop pollerLoop;

        private SessionParamsListenerWrapper(@Nullable SessionParamsListener wrapped,
                                             @NonNull ActionRequestLoop requestLoop,
                                             @NonNull ActionRequestLoop pollerLoop) {
            this.wrapped = wrapped;
            this.requestLoop = requestLoop;
            this.pollerLoop = pollerLoop;
        }

        @Override
        public void onSessionParamsChanged(@NonNull String visitorJson,
                                           @NonNull String sessionId,
                                           @NonNull AuthData authData) {
            requestLoop.setAuthData(authData);
            pollerLoop.setAuthData(authData);
            if (wrapped != null) {
                wrapped.onSessionParamsChanged(visitorJson, sessionId, authData);
            }
        }
    }
}
