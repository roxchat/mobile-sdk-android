package chat.rox.android.sdk.impl.backend;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import chat.rox.android.sdk.BuildConfig;
import chat.rox.android.sdk.ProvidedAuthorizationTokenStateListener;
import chat.rox.android.sdk.Rox;
import chat.rox.android.sdk.RoxSession;
import chat.rox.android.sdk.impl.InternalUtils;
import chat.rox.android.sdk.impl.RoxErrorImpl;
import chat.rox.android.sdk.impl.backend.callbacks.DeltaCallback;
import chat.rox.android.sdk.impl.items.delta.DeltaFullUpdate;
import chat.rox.android.sdk.impl.items.delta.DeltaItem;
import chat.rox.android.sdk.impl.items.responses.DeltaResponse;

import java.io.FileNotFoundException;
import java.io.InterruptedIOException;
import java.util.List;
import java.util.concurrent.Executor;

import retrofit2.Call;

public class DeltaRequestLoop extends AbstractRequestLoop {
    public static final String INCORRECT_SERVER_ANSWER = "Incorrect server answer";
    private static int providedAuthTokenErrorCount = 0;
    private final @Nullable String appVersion;
    private final @NonNull
    DeltaCallback callback;
    private final @NonNull String deviceId;
    private final @NonNull String platform;
    private final @Nullable String prechatFields;
    private final @Nullable SessionParamsListener sessionParamsListener;
    private final @NonNull String title;
    private final @Nullable String visitorFieldsJson;
    private final @NonNull RoxService rox;
    private @Nullable Rox.PushSystem pushSystem;
    private volatile @Nullable String pushToken;
    private @Nullable AuthData authData;
    private @NonNull String location;
    private @Nullable ProvidedAuthorizationTokenStateListener providedAuthorizationTokenStateListener;
    private @Nullable String providedAuthorizationToken;
    private @Nullable String visitorJson;
    private @Nullable String sessionId;
    private @Nullable RoxSession.SessionCallback sessionCallback;
    private long since = 0;

    public DeltaRequestLoop(@NonNull DeltaCallback callback,
                            @Nullable SessionParamsListener sessionParamsListener,
                            @NonNull Executor callbackExecutor,
                            @NonNull InternalErrorListener errorListener,
                            @NonNull RoxService rox,
                            @NonNull String platform,
                            @NonNull String title,
                            @NonNull String location,
                            @Nullable String appVersion,
                            @Nullable String visitorFieldsJson,
                            @Nullable ProvidedAuthorizationTokenStateListener
                                    providedAuthorizationTokenStateListener,
                            @Nullable String providedAuthorizationToken,
                            @NonNull String deviceId,
                            @Nullable String prechatFields,
                            @Nullable RoxSession.SessionCallback sessionCallback) {
        super(callbackExecutor, errorListener);
        this.callback = callback;
        this.sessionParamsListener = sessionParamsListener;
        this.rox = rox;
        this.platform = platform;
        this.title = title;
        this.location = location;
        this.appVersion = appVersion;
        this.visitorFieldsJson = visitorFieldsJson;
        this.providedAuthorizationTokenStateListener = providedAuthorizationTokenStateListener;
        this.providedAuthorizationToken = providedAuthorizationToken;
        this.deviceId = deviceId;
        this.prechatFields = prechatFields;
        this.sessionCallback = sessionCallback;
    }

    public DeltaRequestLoop(@NonNull DeltaCallback callback,
                            @Nullable SessionParamsListener sessionParamsListener,
                            @NonNull Executor callbackExecutor,
                            @NonNull InternalErrorListener errorListener,
                            @NonNull RoxService rox,
                            @NonNull String platform,
                            @NonNull String title,
                            @NonNull String location,
                            @NonNull String appVersion,
                            @Nullable String visitorFieldsJson,
                            @Nullable ProvidedAuthorizationTokenStateListener
                                    providedAuthorizationTokenStateListener,
                            @Nullable String providedAuthorizationToken,
                            @NonNull String deviceId,
                            @Nullable String prechatFields,
                            @Nullable Rox.PushSystem pushSystem,
                            @Nullable String pushToken,
                            @Nullable String visitorJson,
                            @Nullable String sessionId,
                            @Nullable AuthData authData,
                            @Nullable RoxSession.SessionCallback sessionCallback) {
        this(
                callback,
                sessionParamsListener,
                callbackExecutor,
                errorListener,
                rox,
                platform,
                title,
                location,
                appVersion,
                visitorFieldsJson,
                providedAuthorizationTokenStateListener,
                providedAuthorizationToken,
                deviceId,
                prechatFields,
                sessionCallback
        );

        this.pushSystem = pushSystem;
        this.pushToken = pushToken;
        this.visitorJson = visitorJson;
        this.sessionId = sessionId;
        this.authData = authData;
    }

    @Nullable
    public AuthData getAuthData() {
        return this.authData;
    }

    public void setPushToken(@Nullable String pushToken) {
        this.pushToken = pushToken;
    }

    public void changeLocation(@NonNull String location) {
        this.location = location;

        authData = null;
        since = 0;

        new Thread(this::runIteration).start();
    }

    @Override
    protected void run() {
        try {
            while (isRunning()) {
                try {
                    runIteration();
                } catch (final AbortByRoxErrorException exception) {
                    if (RoxInternalError.REINIT_REQUIRED.equals(exception.getError())) {
                        authData = null;
                        since = 0;
                    } else if (RoxInternalError.PROVIDED_AUTHORIZATION_TOKEN_NOT_FOUND
                            .equals(exception.getError())) {
                        handleProvidedAuthorizationTokenError();
                    } else {
                        running = false;
                        callbackExecutor.execute(() -> errorListener.onError(
                                exception.getRequest().request().url().toString(),
                                exception.getError(),
                                exception.getHttpCode()));
                    }
                } catch (InterruptedRuntimeException ignored) { }
            }
        } catch (final Throwable t) {
            running = false;
            throw t;
        }
    }

    private void runIteration() {
        try {
            while (true) {
                try {
                    if ((authData != null) && (since != 0)) {
                        requestDelta();
                    } else {
                        requestInit();
                    }
                    break;
                } catch (InterruptedIOException | FileNotFoundException ignored) { }
            }
        } catch (final AbortByRoxErrorException exception) {
            throw exception;
        }
    }

    private void requestInit() throws InterruptedIOException, FileNotFoundException {
        final Call<DeltaResponse> request = makeInitRequest();
        try {
            final DeltaResponse delta = performRequest(request);

            if ((delta.getDeltaList() != null) && (delta.getDeltaList().size() != 0)
                    || (delta.getFullUpdate() == null)) {
                callbackExecutor.execute(() -> {
                    if (sessionCallback != null) {
                        sessionCallback.onFailure(new RoxErrorImpl<>(
                                RoxSession.SessionCallback.SessionError.REQUEST_ERROR,
                                null)
                        );
                    }
                    errorListener.onError(
                            request.request().url().toString(),
                            INCORRECT_SERVER_ANSWER,
                            200
                    );
                });

                return;
            }

            Long revision = delta.getRevision();
            if (revision != null) {
                since = delta.getRevision();
            }

            callbackExecutor.execute(() -> {
                if (sessionCallback != null) {
                    sessionCallback.onSuccess();
                }
            });
            processFullUpdate(delta.getFullUpdate());
        } catch (Exception exception) {
            callbackExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    if (sessionCallback != null) {
                        sessionCallback.onFailure(new RoxErrorImpl<>(
                                RoxSession.SessionCallback.SessionError.REQUEST_ERROR,
                                null)
                        );
                    }
                }
            });
            throw exception;
        }
    }

    private void requestDelta() throws InterruptedIOException, FileNotFoundException {
        try {
            DeltaResponse delta = performRequest(makeDeltaRequest());

            if (delta.getRevision() == null) {
                return; // Delta timeout.
            }

            since = delta.getRevision();

            if (delta.getFullUpdate() != null) {
                processFullUpdate(delta.getFullUpdate());
            } else {
                final List<DeltaItem<?>> list = delta.getDeltaList();
                if (list != null && list.size() != 0) {
                    callbackExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            callback.onDeltaList(list);
                        }
                    });
                }
            }
        } catch (final AbortByRoxErrorException exception) {
            throw exception;
        }
    }

    private Call<DeltaResponse> makeInitRequest() {
        return rox.getLogin(
                BuildConfig.VERSION_NAME,
                "init",
                (pushSystem == Rox.PushSystem.FCM) ? "fcm" : "gcm",
                pushToken,
                platform,
                visitorFieldsJson,
                visitorJson,
                providedAuthorizationToken,
                location,
                appVersion,
                sessionId,
                title,
                0,
                true,
                deviceId,
                prechatFields
        );
    }

    private Call<DeltaResponse> makeDeltaRequest() {
        return rox.getDelta(
                since,
                authData == null ? null : authData.getPageId(),
                authData == null ? null : authData.getAuthToken(),
                System.currentTimeMillis()
        );
    }

    private void processFullUpdate(final DeltaFullUpdate fullUpdate) {
        if ((visitorJson == null)
                || !visitorJson.equals(fullUpdate.getVisitorJson())
                || (sessionId == null)
                || !sessionId.equals(fullUpdate.getVisitSessionId())
                || (authData == null)
                || !authData.getPageId().equals(fullUpdate.getPageId())
                || !InternalUtils.equals(authData.getAuthToken(), fullUpdate.getAuthToken())) {
            final String visitorJsonString = fullUpdate.getVisitorJson();
            final String visitSessionId = fullUpdate.getVisitSessionId();
            String pageId = fullUpdate.getPageId();
            String authToken = fullUpdate.getAuthToken();

            this.visitorJson = visitorJsonString;
            this.sessionId = visitSessionId;

            if (pageId != null) {
                final AuthData authData = new AuthData(pageId, authToken);
                this.authData = authData;

                if ((sessionParamsListener != null)
                        && (visitorJsonString != null)
                        && (visitSessionId != null)) {
                    callbackExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            sessionParamsListener.onSessionParamsChanged(visitorJsonString,
                                    visitSessionId,
                                    authData);
                        }
                    });
                }
            }
        }

        callbackExecutor.execute(new Runnable() {
            @Override
            public void run() {
                callback.onFullUpdate(fullUpdate);
            }
        });
    }

    private void handleProvidedAuthorizationTokenError() {
        providedAuthTokenErrorCount += 1;

        if (providedAuthTokenErrorCount < 5) {
            sleepBetweenInitializationAttempts();
        } else {
            if (providedAuthorizationTokenStateListener != null) {
                providedAuthorizationTokenStateListener
                        .updateProvidedAuthorizationToken(
                                providedAuthorizationToken
                        );
            }

            providedAuthTokenErrorCount = 0;

            sleepBetweenInitializationAttempts();
        }
    }

    private void sleepBetweenInitializationAttempts() {
        authData = null;
        since = 0;

        try {
            Thread.sleep(1000); // 1 s
        } catch (InterruptedException ignored) { }
    }
}
