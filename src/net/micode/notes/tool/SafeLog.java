package net.micode.notes.tool;

import android.util.Log;

/** Logs failures without exception messages, stack traces, or note/account content. */
public final class SafeLog {
    private SafeLog() {
    }

    public static void error(String tag, String operation, Exception failure) {
        Log.e(tag, operation + ": " + failure.getClass().getSimpleName());
    }
}
