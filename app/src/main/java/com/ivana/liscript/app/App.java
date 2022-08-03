package com.ivana.liscript.app;

import android.app.Application;

public class App extends Application {

    private static App sInstance;
    public static App getInstance() {
        return sInstance;
    }

    public Env env;
    public volatile EvalThread thread;

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;

        this.env = new Env();
        this.thread = null;
    }
}
