package com.sxsbsxs.videofetch;

import android.app.Application;

/** Application bootstrap kept free of heavyweight runtime initialization. */
public final class VideoDlApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashReporter.install(this);
    }
}
