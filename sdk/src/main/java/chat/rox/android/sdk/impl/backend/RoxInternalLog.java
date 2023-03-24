package chat.rox.android.sdk.impl.backend;

import androidx.annotation.Nullable;

import chat.rox.android.sdk.Rox.SessionBuilder.RoxLogVerbosityLevel;
import chat.rox.android.sdk.RoxLog;
import chat.rox.android.sdk.RoxLogEntity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Created by Nikita Kaberov on 01.02.18.
 */

public class RoxInternalLog {
    private static final RoxInternalLog ourInstance = new RoxInternalLog();
    @Nullable
    private RoxLog logger;
    @Nullable
    private RoxLogVerbosityLevel verbosityLevel;
    private String lastActionResponseJSON = "";
    private String lastDeltaResponseJSON = "";
    private Set<RoxLogEntity> logEntities = new HashSet<>();

    public void setLastActionResponseJSON(String json) {
        lastActionResponseJSON = json;
    }

    public void setLastDeltaResponseJSON(String json) {
        lastDeltaResponseJSON = json;
    }

    public static RoxInternalLog getInstance() {
        return ourInstance;
    }

    private RoxInternalLog() {
    }

    public void setLogger(@Nullable RoxLog logger) {
        this.logger = logger;
    }

    public void setVerbosityLevel(@Nullable RoxLogVerbosityLevel verbosityLevel) {
        this.verbosityLevel = verbosityLevel;
    }

    public void setLogEntities(Set<RoxLogEntity> logEntities) {
        this.logEntities = logEntities;
    }

    void logResponse(String log, RoxLogVerbosityLevel verbosityLevel) {
        if (log.contains(RoxService.URL_SUFFIX_DELTA)) {
            log += System.getProperty("line.separator") + lastDeltaResponseJSON;
            lastDeltaResponseJSON = "";
        } else {
            log += System.getProperty("line.separator") + lastActionResponseJSON;
            lastActionResponseJSON = "";
        }
        log(log, verbosityLevel, RoxLogEntity.SERVER);
    }

    public void log(String log, RoxLogVerbosityLevel verbosityLevel) {
        log(log, verbosityLevel, RoxLogEntity.SERVER);
    }

    public void log(String log, RoxLogVerbosityLevel verbosityLevel, RoxLogEntity logArea) {
        if (logger != null && verbosityLevel != null) {
            if (logEntities.contains(logArea)) {
                logInternal(log, verbosityLevel);
            }
        }
    }

    private void logInternal(String log, RoxLogVerbosityLevel verbosityLevel) {
        log = new SimpleDateFormat("dd MMM yyyy HH:mm:ss:SSS z", Locale.getDefault()).format(new Date())
            + " "
            + levelToString(verbosityLevel)
            + "ROX LOG: "
            + System.getProperty("line.separator")
            + log;
        switch (verbosityLevel) {
            case VERBOSE:
                if (isVerbose()) {
                    this.logger.log(log);
                }
                break;
            case DEBUG:
                if (isDebug()) {
                    this.logger.log(log);
                }
                break;
            case INFO:
                if (isInfo()) {
                    this.logger.log(log);
                }
                break;
            case WARNING:
                if (isWarning()) {
                    this.logger.log(log);
                }
                break;
            case ERROR:
                this.logger.log(log);
                break;
        }
    }

    private boolean isVerbose() {
        return this.verbosityLevel.equals(RoxLogVerbosityLevel.VERBOSE);
    }

    private boolean isDebug() {
        return this.verbosityLevel.equals(RoxLogVerbosityLevel.DEBUG) || this.isVerbose();
    }

    private boolean isInfo() {
        return this.verbosityLevel.equals(RoxLogVerbosityLevel.INFO) || this.isDebug();
    }

    private boolean isWarning() {
        return this.verbosityLevel.equals(RoxLogVerbosityLevel.WARNING) || this.isInfo();
    }

    private String levelToString(RoxLogVerbosityLevel level) {
        switch (level) {
            case VERBOSE:
                return "V/";
            case DEBUG:
                return "D/";
            case INFO:
                return "I/";
            case WARNING:
                return "W/";
            case ERROR:
                return "E/";
        }
        return "";
    }
}
