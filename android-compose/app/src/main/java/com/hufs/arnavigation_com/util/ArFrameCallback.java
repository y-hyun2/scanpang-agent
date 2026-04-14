package com.hufs.arnavigation_com.util;

import android.view.Choreographer;

/**
 * Java로 작성한 FrameCallback.
 * Kotlin 2.0 K2 컴파일러의 Choreographer.FrameCallback SAM 변환 버그를 우회.
 */
public class ArFrameCallback implements Choreographer.FrameCallback {

    public interface FrameAction {
        void onFrame();
    }

    private final FrameAction action;

    public ArFrameCallback(FrameAction action) {
        this.action = action;
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        action.onFrame();
        Choreographer.getInstance().postFrameCallback(this);
    }
}
