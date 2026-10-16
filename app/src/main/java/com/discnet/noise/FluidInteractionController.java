package com.discnet.noise;

import android.view.MotionEvent;

/** Observes events without taking ownership of the navigation gesture. */
final class FluidInteractionController {
    private float x = -10, y = -10, vx, vy, strength;
    private long lastEvent;
    private int pointer = -1;
    synchronized void observe(MotionEvent event, int width, int height) {
        float unit = Math.max(1, Math.min(width, height));
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            pointer = event.getPointerId(0);
            x = event.getX() / unit; y = event.getY() / unit;
            vx = vy = 0; strength = .042f; lastEvent = event.getEventTime();
        } else if (action == MotionEvent.ACTION_MOVE && pointer != -1) {
            int p = event.findPointerIndex(pointer);
            if (p < 0) { pointer = -1; return; }
            float nx = event.getX(p) / unit, ny = event.getY(p) / unit;
            float dt = Math.max(.008f, (event.getEventTime() - lastEvent) * .001f);
            vx = .65f * vx + .35f * clamp((nx - x) / dt * .025f, -.065f, .065f);
            vy = .65f * vy + .35f * clamp((ny - y) / dt * .025f, -.065f, .065f);
            strength = Math.min(.085f, .035f + (float)Math.hypot(vx, vy));
            x = nx; y = ny; lastEvent = event.getEventTime();
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
                || (action == MotionEvent.ACTION_POINTER_UP && event.getPointerId(event.getActionIndex()) == pointer)) {
            pointer = -1;
        }
    }
    synchronized void sample(float dt, float[] output) {
        float decay = (float)Math.exp(-dt * (pointer == -1 ? 3.2 : 1.5));
        strength *= decay; vx *= decay; vy *= decay;
        output[0] = x; output[1] = y; output[2] = vx; output[3] = vy; output[4] = strength;
    }
    synchronized void clear() { pointer = -1; strength = vx = vy = 0; }
    private static float clamp(float x, float lo, float hi) { return Math.max(lo, Math.min(hi, x)); }
}
