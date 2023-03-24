package chat.rox.android.sdk.impl.backend;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import chat.rox.android.sdk.Rox;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;

import retrofit2.Call;

public class FAQRequestLoop extends AbstractRequestLoop {
    private final BlockingQueue<FAQRequestLoop.RoxRequest> queue = new ArrayBlockingQueue<>(128);
    @Nullable
    private FAQRequestLoop.RoxRequest<?> lastRequest;

    public FAQRequestLoop(@NonNull Executor callbackExecutor) {
        super(callbackExecutor, null);
    }

    @Override
    protected void run() {
        try {
            while (isRunning()) {
                try {
                    if (!isRunning()) {
                        return;
                    }
                    runIteration();
                } catch (final AbortByRoxErrorException e) {
                    if (RoxInternalError.WRONG_ARGUMENT_VALUE.equals(e.getError())) {
                        RoxInternalLog.getInstance().log("Error: " + "\""+ e.getError() + "\""
                                        + ", argumentName: " + "\"" + e.getArgumentName() + "\"",
                                Rox.SessionBuilder.RoxLogVerbosityLevel.ERROR);
                        this.lastRequest = null;
                    } else {
                        running = false;
                    }
                } catch (InterruptedRuntimeException ignored) { }
            }
        } catch (final Throwable t) {
            running = false;

            throw t;
        }
    }

    @SuppressWarnings("unchecked")
    private void runIteration() {
        FAQRequestLoop.RoxRequest<?> currentRequest = this.lastRequest;
        if (currentRequest == null) {
            try {
                this.lastRequest = currentRequest = queue.take();
            } catch (InterruptedException e) {
                return;
            }
        }

        try {
            performRequestAndCallback(currentRequest);
        } catch (AbortByRoxErrorException exception) {
            if ((exception.getError() != null)
                    && currentRequest.isHandleError(exception.getError())) {
                if (currentRequest.hasCallback) {
                    final FAQRequestLoop.RoxRequest<?> callback = currentRequest;
                    final String error = exception.getError();
                    callbackExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            callback.handleError(error);
                        }
                    });
                } // Else ignore.
            } else {
                throw exception;
            }
        }

        this.lastRequest = null;
    }

    private <T> void performRequestAndCallback(FAQRequestLoop.RoxRequest<T> currentRequest) {
        final T response = performFAQRequest(currentRequest.makeRequest());

        if (currentRequest.hasCallback) {
            final FAQRequestLoop.RoxRequest<T> callback = currentRequest;
            callbackExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    callback.runCallback(response);
                }
            });
        }
    }

    void enqueue(FAQRequestLoop.RoxRequest<?> request) {
        try {
            queue.put(request);
        } catch (InterruptedException ignored) { }
    }

    static abstract class RoxRequest<T> {
        private final boolean hasCallback;

        protected RoxRequest(boolean hasCallback) {
            this.hasCallback = hasCallback;
        }

        public abstract Call<T> makeRequest();

        public void runCallback(T response) { }

        public boolean isHandleError(@NonNull String error) {
            return false;
        }

        public void handleError(@NonNull String error) { }
    }
}
