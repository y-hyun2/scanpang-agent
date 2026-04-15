package com.hufs.arnavigation_com.util;

import android.view.Choreographer;

/**
 * Choreographer.FrameCallback을 Java에서 관리.
 * Kotlin에서는 start()/stop()만 호출.
 */
public class ArFrameCallback {

    private final Runnable action;
    private boolean running = false;

    private final Choreographer.FrameCallback internalCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (running) {
                action.run();
                Choreographer.getInstance().postFrameCallback(this);
            }
        }
    };

    public ArFrameCallback(Runnable action) {
        this.action = action;
    }

    public void start() {
        running = true;
        Choreographer.getInstance().postFrameCallback(internalCallback);
    }

    public void stop() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(internalCallback);
    }
}
