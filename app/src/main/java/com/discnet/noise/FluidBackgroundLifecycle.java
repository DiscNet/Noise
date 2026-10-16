package com.discnet.noise;

import android.view.Choreographer;

/** UI-vsync producer with at most one in-flight GL frame, no timers while hidden. */
final class FluidBackgroundLifecycle implements Choreographer.FrameCallback {
    private FluidBackgroundRenderer renderer;
    private boolean running;
    private long lastFrame;
    void bind(FluidBackgroundRenderer next) { stop(); renderer = next; }
    void update(boolean visible, boolean animate) {
        if (!visible || renderer == null) { stop(); return; }
        renderer.setActive(true);
        if (!animate) {
            stopCallbacks();
            renderer.requestFrame(System.nanoTime(), false);
        } else if (!running) {
            running = true; lastFrame = 0;
            Choreographer.getInstance().postFrameCallback(this);
        }
    }
    @Override public void doFrame(long nanos) {
        if (!running || renderer == null) return;
        // 0.8ms tolerance avoids halving 60Hz because of rounded vsync periods.
        if (lastFrame == 0 || nanos - lastFrame >= renderer.intervalNanos - 800_000) {
            renderer.requestFrame(nanos, true); lastFrame = nanos;
        }
        Choreographer.getInstance().postFrameCallback(this);
    }
    private void stopCallbacks() {
        if (running) Choreographer.getInstance().removeFrameCallback(this);
        running = false; lastFrame = 0;
    }
    void stop() { stopCallbacks(); if (renderer != null) renderer.setActive(false); }
}
