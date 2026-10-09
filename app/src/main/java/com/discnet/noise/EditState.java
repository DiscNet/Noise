package com.discnet.noise;

/** Immutable uniform snapshot. The GPU receives the latest slider state immediately. */
public final class EditState {
    public static final EditState NEUTRAL = new EditState(new float[9], new float[3], false, false);
    final float[] color, tone, hue, style;
    final boolean invert, original;

    /** Kept for compatibility with existing editor callers and instrumentation. */
    public EditState(float[] values, boolean invert, boolean original) {
        this(values, new float[3], invert, original);
    }

    /** style: Dither (0..1), Glow (0..1), RGB Shift (0..1). */
    public EditState(float[] values, float[] effects, boolean invert, boolean original) {
        if (values.length != 9 || effects.length != 3) throw new IllegalArgumentException("Invalid adjustments");
        float[] v = values.clone();
        for (int i = 0; i < v.length; i++)
            v[i] = Float.isFinite(v[i]) ? Math.max(-1f, Math.min(1f, v[i])) : 0f;
        style = effects.clone();
        for (int i = 0; i < style.length; i++)
            style[i] = Float.isFinite(style[i]) ? Math.max(0f, Math.min(1f, style[i])) : 0f;
        color = new float[]{1 + v[0], v[1], (float)Math.pow(2, 3 * v[2]), (float)Math.pow(2, v[3])};
        tone = new float[]{v[4], v[5], v[6], v[7]};
        hue = new float[]{(float)Math.cos(v[8] * Math.PI), (float)Math.sin(v[8] * Math.PI) / (float)Math.sqrt(3)};
        this.invert = invert;
        this.original = original;
    }
}
