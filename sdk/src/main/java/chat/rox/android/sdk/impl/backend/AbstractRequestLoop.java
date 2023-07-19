package chat.rox.android.sdk.impl.backend;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import chat.rox.android.sdk.NotFatalErrorHandler;
import chat.rox.android.sdk.Rox;
import chat.rox.android.sdk.impl.InternalUtils;
import chat.rox.android.sdk.impl.items.responses.ErrorResponse;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.UnknownHostException;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLHandshakeException;

import okhttp3.FormBody;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okio.Buffer;
import retrofit2.Call;
import retrofit2.Response;

public abstract class AbstractRequestLoop {

    protected volatile boolean running = true;
    @Nullable
    private Thread thread;
    @Nullable
    private volatile Call<?> currentRequest;
    @NonNull
    protected final InternalErrorListener errorListener;
    @NonNull
    protected final Executor callbackExecutor;

    private /* non-volatile */ boolean paused = true;
    private final Lock pauseLock = new ReentrantLock();
    private final Condition pauseCond = pauseLock.newCondition();

    public AbstractRequestLoop(@NonNull Executor callbackExecutor,
                               @NonNull InternalErrorListener errorListener) {
        this.callbackExecutor = callbackExecutor;
        this.errorListener = errorListener;
    }

    protected void cancelRequest() {
        Call<?> request = currentRequest;
        if (request != null) {
            request.cancel();
        }
    }

    public void start() {
        if (thread != null) {
            throw new IllegalStateException("Already started");
        }
        thread = new Thread("Rox IO executor") {
            @Override
            public void run() {
                AbstractRequestLoop.this.run();
            }
        };
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        if (thread != null) {
            running = false;
            resume();
            try {
                cancelRequest();
            } catch (Exception ignored) { }
            thread.interrupt();
            thread = null;
        }
    }

    public void pause() {
        pauseLock.lock();
        try {
            if (!paused) {
                paused = true;
            }
        } finally {
            pauseLock.unlock();
        }
    }

    public void resume() {
        pauseLock.lock();
        try {
            if (paused) {
                paused = false;
                pauseCond.signal();
            }
        } finally {
            pauseLock.unlock();
        }
    }

    private void blockUntilPaused() {
        pauseLock.lock();

        try {
            while (paused) {
                try {
                    pauseCond.await();
                } catch (InterruptedException e) {
                    throw new InterruptedRuntimeException();
                }
            }
        } finally {
            pauseLock.unlock();
        }
    }

    protected abstract void run();

    protected boolean isRunning() {
        return running;
    }

    @Nullable
    protected <T extends ErrorResponse> T performRequest(@NonNull Call<T> request)
            throws InterruptedIOException, FileNotFoundException {
        logRequest(request.request());

        int errorCounter = 0;
        int lastHttpCode = -1;

        while (isRunning()) {
            long startTime = System.nanoTime();
            String error = null;
            String argumentName = null;
            int httpCode = 200;

            try {
                Call<T> cloned = request.clone();
                currentRequest = cloned;
                Response<T> response = cloned.execute();

                String log = logResponse(response);

                currentRequest = null;

                blockUntilPaused();
                if (!isRunning()) {
                    break;
                }

                if (response.isSuccessful()) {
                    T body = response.body();

                    if (body != null && body.getError() != null) {
                        error = body.getError();
                        argumentName = body.getArgumentName();
                        RoxInternalLog.getInstance().logResponse(log,
                                Rox.SessionBuilder.RoxLogVerbosityLevel.WARNING
                        );
                    } else {
                        RoxInternalLog.getInstance().logResponse(log,
                                Rox.SessionBuilder.RoxLogVerbosityLevel.DEBUG
                        );
                        return body;
                    }
                } else {
                    try {
                        ErrorResponse errorResponse = InternalUtils.fromJson(
                                response.errorBody().string(),
                                ErrorResponse.class
                        );
                        error = errorResponse.getError();
                        argumentName = errorResponse.getArgumentName();
                    } catch (Exception ignored) { }

                    httpCode = response.code();

                    RoxInternalLog.getInstance().logResponse(log,
                            Rox.SessionBuilder.RoxLogVerbosityLevel.ERROR
                    );
                }
            } catch (FileNotFoundException exception) {
                RoxInternalLog.getInstance().log(exception.toString(),
                        Rox.SessionBuilder.RoxLogVerbosityLevel.DEBUG
                );
            } catch (InterruptedIOException exception) {
                RoxInternalLog.getInstance().log(exception.toString(),
                        Rox.SessionBuilder.RoxLogVerbosityLevel.DEBUG
                );
                callbackExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        errorListener.onNotFatalError(NotFatalErrorHandler.NotFatalErrorType.SOCKET_TIMEOUT_EXPIRED);
                    }
                });
                throw exception;
            } catch (UnknownHostException exception) {
                RoxInternalLog.getInstance().log(exception.toString(),
                        Rox.SessionBuilder.RoxLogVerbosityLevel.DEBUG
                );
            } catch (SSLHandshakeException e) {
                RoxInternalLog.getInstance().log("Error while executing http request. " + e,
                        Rox.SessionBuilder.RoxLogVerbosityLevel.WARNING);
                error = "ssl_error";
                argumentName = null;
            } catch (IOException e) {
                if (!isRunning()) {
                    break;
                }

                RoxInternalLog.getInstance().log("Error while executing http request. " + e,
                        Rox.SessionBuilder.RoxLogVerbosityLevel.WARNING);
            }

            blockUntilPaused();
            if (!isRunning()) {
                break;
            }

            if ((error != null) && !error.equals(RoxInternalError.SERVER_NOT_READY)) {
                throw new AbortByRoxErrorException(request, error, httpCode, argumentName);
            } else if ((httpCode != 200) && (httpCode != 502)) {
                // 502 Bad Gateway - always the same as 'server-not-ready'

                if (httpCode == 400) {
                    throw new AbortByRoxErrorException(
                        request,
                        RoxInternalError.CANNOT_CREATE_RESPONSE,
                        httpCode
                    );
                }
                if (httpCode == 415) {
                    throw new AbortByRoxErrorException(
                            request,
                            RoxInternalError.FILE_TYPE_NOT_ALLOWED,
                            httpCode
                    );
                }
                if (httpCode == 413) {
                    throw new AbortByRoxErrorException(
                            request,
                            RoxInternalError.FILE_SIZE_EXCEEDED,
                            httpCode
                    );
                }
                if (httpCode == lastHttpCode) {
                    throw new AbortByRoxErrorException(request, null, httpCode);
                }

                errorCounter = 10;
            }
            lastHttpCode = httpCode;

            errorCounter++;
            long elapsedMillis = (System.nanoTime() - startTime) / 1000_000;
            long toSleepMillis = ((errorCounter > 4) ? 10000 : errorCounter * 2000);
            if (elapsedMillis < toSleepMillis) {
                try {
                    callbackExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            errorListener.onNotFatalError(NotFatalErrorHandler.NotFatalErrorType.NO_NETWORK_CONNECTION);
                        }
                    });
                    Thread.sleep(toSleepMillis - elapsedMillis);
                } catch (InterruptedException ignored) { }
            }
        }

        throw new InterruptedRuntimeException();
    }

    @Nullable
    protected <T> T performFAQRequest(@NonNull Call<T> request) {

        int errorCounter = 0;
        int lastHttpCode = -1;

        while (isRunning()) {
            long startTime = System.nanoTime();
            String error = null;
            String argumentName = null;
            int httpCode = 200;

            try {
                Call<T> cloned = request.clone();
                currentRequest = cloned;
                Response<T> response = cloned.execute();

                currentRequest = null;

                blockUntilPaused();
                if (!isRunning()) {
                    break;
                }

                if (response.isSuccessful()) {
                    return response.body();
                } else {
                    try {
                        ErrorResponse errorResponse = InternalUtils.fromJson(
                                response.errorBody().string(),
                                ErrorResponse.class
                        );
                        error = errorResponse.getError();
                        argumentName = errorResponse.getArgumentName();
                    } catch (Exception ignored) { }

                    httpCode = response.code();
                }
            } catch (UnknownHostException ignored) {
            } catch (SSLHandshakeException e) {
                error = "ssl_error";
                argumentName = null;
            } catch (IOException e) {
                if (!isRunning()) {
                    break;
                }
            }

            blockUntilPaused();
            if (!isRunning()) {
                break;
            }

            if ((error != null) && !error.equals(RoxInternalError.SERVER_NOT_READY)) {
                throw new AbortByRoxErrorException(request, error, httpCode, argumentName);
            } else if ((httpCode != 200) && (httpCode != 502)) {
                // 502 Bad Gateway - always the same as 'server-not-ready'
                if (httpCode == lastHttpCode) {
                    throw new AbortByRoxErrorException(request, null, httpCode);
                }

                errorCounter = 10;
            }
            lastHttpCode = httpCode;

            errorCounter++;
            long elapsedMillis = (System.nanoTime() - startTime) / 1000_000;
            long toSleepMillis = ((errorCounter >= 5) ? 10000 : errorCounter * 2000);
            if (elapsedMillis < toSleepMillis) {
                try {
                    Thread.sleep(toSleepMillis - elapsedMillis);
                } catch (InterruptedException ignored) { }
            }
        }

        throw new InterruptedRuntimeException();
    }

    private void logRequest(Request request) {
            String ln = System.getProperty("line.separator");
            String log = "Rox request:"
                    + ln + "HTTP method - " + request.method()
                    + ln + "URL - " + request.url()
                    + getRequestParameters(request);
            RoxInternalLog.getInstance().log(log,
                    Rox.SessionBuilder.RoxLogVerbosityLevel.DEBUG);
    }

    private String getRequestParameters(Request request) {
        String ln = System.getProperty("line.separator");
        StringBuilder log = new StringBuilder();
        RequestBody requestBody = request.body();
        if (requestBody != null) {
            log.append(ln).append("Parameters:");
            if (requestBody instanceof FormBody) {
                FormBody formBody = (FormBody) requestBody;
                for (int i = 0; i < formBody.size(); i++) {
                    log.append(ln)
                            .append(formBody.encodedName(i))
                            .append("=")
                            .append(formBody.encodedValue(i));
                }
            } else {
                MultipartBody multipartBody = (MultipartBody) requestBody;
                for (MultipartBody.Part part : multipartBody.parts()) {
                    Buffer buffer = new Buffer();
                    String name = part.headers().value(0);
                    if (!name.contains("file")) {
                        try {
                            part.body().writeTo(buffer);
                            if (name.contains("name=")) {
                                name = name.replaceAll("^.*name=", "")
                                        .replaceAll("\"", "");
                            }
                            log.append(ln)
                                    .append(name)
                                    .append("=")
                                    .append(buffer.readUtf8());

                        } catch (IOException ignored) { }
                    }
                }
            }
        }
        return log.toString();
    }

    private String logResponse(Response response) {
        String ln = System.getProperty("line.separator");
        return "Rox response:" + ln + response.raw().request().url() +
                getRequestParameters(response.raw().request()) +
                ln + "HTTP code - " + response.code() +
                ln + "Message: " + response.message();
    }

    protected class InterruptedRuntimeException extends RuntimeException {
    }

    protected class AbortByRoxErrorException extends RuntimeException {
        private final Call<?> request;
        @Nullable
        private final String argumentName;
        @Nullable
        private final String error;
        private final int httpCode;

        public AbortByRoxErrorException(@NonNull Call<?> request,
                                          @Nullable String error,
                                          int httpCode) {
            super(error);
            this.request = request;
            this.error = error;
            this.httpCode = httpCode;
            this.argumentName = null;
        }

        public AbortByRoxErrorException(@NonNull Call<?> request,
                                          @Nullable String error,
                                          int httpCode,
                                          @Nullable String argumentName) {
            super(error);
            this.request = request;
            this.error = error;
            this.httpCode = httpCode;
            this.argumentName = argumentName;
        }

        public Call<?> getRequest() {
            return request;
        }

        @Nullable
        public String getArgumentName() {
            return argumentName;
        }

        @Nullable
        public String getError() {
            return error;
        }

        public int getHttpCode() {
            return httpCode;
        }
    }
}
