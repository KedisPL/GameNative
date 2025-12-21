package app.gamenative;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import timber.log.Timber;

/**
 * A log manager instance for release mode.
 * Debug mode uses {@link timber.log.Timber.DebugTree}
 */
public class ReleaseTree extends Timber.Tree {
    @Override
    protected boolean isLoggable(@Nullable String tag, int priority) {
        // Ignore Verbose and Debug logs.
        return priority >= Log.INFO;
    }

    @Override
    protected void log(int priority, @Nullable String tag, @NonNull String message, @Nullable Throwable t) {
        if (!isLoggable(tag, priority)) {
            return;
        }

        String priorityChar = switch (priority) {
            case Log.INFO -> "I";
            case Log.WARN -> "W";
            case Log.ERROR -> "E";
            case Log.ASSERT -> "A";
            default -> "V"; // Treat anything else as Verbose
        };

        Log.println(priority, tag, priorityChar + ": " + message);

        if (t != null) {
            Log.println(priority, tag, Log.getStackTraceString(t));
        }
    }
}
