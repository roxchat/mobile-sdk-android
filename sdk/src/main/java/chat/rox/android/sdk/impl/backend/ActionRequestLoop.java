package chat.rox.android.sdk.impl.backend;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import chat.rox.android.sdk.NotFatalErrorHandler;
import chat.rox.android.sdk.Rox;
import chat.rox.android.sdk.impl.items.responses.ErrorResponse;

import java.io.FileNotFoundException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;

import retrofit2.Call;

public class ActionRequestLoop extends AbstractRequestLoop {
    private final BlockingQueue<RoxRequest<?>> queue = new ArrayBlockingQueue<>(128);
    @Nullable
    private volatile AuthData authData;
    @Nullable
    private RoxRequest<?> lastRequest;

    public ActionRequestLoop(@NonNull Executor callbackExecutor,
                             @NonNull InternalErrorListener errorListener) {
        super(callbackExecutor, errorListener);
    }

    public void setAuthData(@Nullable AuthData pageId) {
        this.authData = pageId;
    }

    @Override
    protected void run() {
        try {
            while (isRunning()) {
                AuthData currentAuthData = authData;
                if (currentAuthData == null) {
                    currentAuthData = awaitNewPageId(null);
                }

                try {
                    if (!isRunning()) {
                        return;
                    }

                    runIteration(currentAuthData);
                } catch (final AbortByRoxErrorException e) {
                    if (RoxInternalError.WRONG_ARGUMENT_VALUE.equals(e.getError())) {
                        RoxInternalLog.getInstance().log("Error: " + "\""+ e.getError() + "\""
                                + ", argumentName: " + "\"" + e.getArgumentName() + "\"",
                                Rox.SessionBuilder.RoxLogVerbosityLevel.ERROR);
                        this.lastRequest = null;
                    } else if (RoxInternalError.REINIT_REQUIRED.equals(e.getError())) {
                        awaitNewPageId(currentAuthData);
                    } else {
                        running = false;

                        callbackExecutor.execute(() -> errorListener.onError(
                                e.getRequest().request().url().toString(),
                                e.getError(),
                                e.getHttpCode()
                        ));
                    }
                } catch (InterruptedIOException e) {
                    RoxInternalLog.getInstance().log(e.toString(),
                            Rox.SessionBuilder.RoxLogVerbosityLevel.DEBUG);
                    final RoxRequest<?> request = lastRequest;
                    callbackExecutor.execute(() -> {
                        if (request != null) {
                            request.handleError(NotFatalErrorHandler.NotFatalErrorType.SOCKET_TIMEOUT_EXPIRED.toString());
                        }
                    });
                    this.lastRequest = null;
                } catch (InterruptedRuntimeException ignored) { }
            }
        } catch (final Throwable t) {
            running = false;

            throw t;
        }
    }

    private AuthData awaitNewPageId(@Nullable AuthData lastAuthData) {
        //noinspection StringEquality
        while (isRunning() && lastAuthData == authData) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) { }
        }
        return authData;
    }

    private void runIteration(AuthData currentAuthData) throws InterruptedIOException {
        RoxRequest<?> currentRequest = this.lastRequest;
        if (currentRequest == null) {
            try {
                this.lastRequest = currentRequest = queue.take();
            } catch (InterruptedException e) {
                return;
            }
        }

        try {
            performRequestAndCallback(currentAuthData, currentRequest);
        } catch (final FileNotFoundException exception) {
            final RoxRequest<?> callback = currentRequest;
            callbackExecutor.execute(() -> callback.handleError(RoxInternalError.FILE_NOT_FOUND));
        } catch (InterruptedIOException exception) {
            if (exception instanceof SocketTimeoutException) {
                throw exception;
            } else {
                final RoxRequest<?> callback = currentRequest;
                callbackExecutor.execute(() -> callback.handleError(RoxInternalError.CONNECTION_TIMEOUT));
            }
        } catch (AbortByRoxErrorException exception) {
            if ((exception.getError() != null)
                    && currentRequest.isHandleError(exception.getError())) {
                if (currentRequest.hasCallback) {
                    final RoxRequest<?> callback = currentRequest;
                    final String error = exception.getError();
                    callbackExecutor.execute(() -> callback.handleError(error));
                } // Else ignore.
            } else {
                throw exception;
            }
        }

        this.lastRequest = null;
    }

    private <T extends ErrorResponse> void performRequestAndCallback(
        AuthData currentAuthData, RoxRequest<T> currentRequest) throws InterruptedIOException, FileNotFoundException {
        final T response = performRequest(currentRequest.makeRequest(currentAuthData));

        if (currentRequest.hasCallback) {
            final RoxRequest<T> callback = currentRequest;
            callbackExecutor.execute(() -> callback.runCallback(response));
        }
    }

    void enqueue(RoxRequest<?> request) {
        try {
            queue.put(request);
        } catch (InterruptedException ignored) { }
    }

    static abstract class RoxRequest<T extends ErrorResponse> {
        private final boolean hasCallback;

        protected RoxRequest(boolean hasCallback) {
            this.hasCallback = hasCallback;
        }

        public abstract Call<T> makeRequest(AuthData authData);

        public void runCallback(T response) { }

        public boolean isHandleError(@NonNull String error) {
            return false;
        }

        public void handleError(@NonNull String error) { }
    }
}
