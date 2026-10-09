package com.discnet.noise;

/** Immutable snapshot: a slider event changes only a few shader uniforms. */
public final class EditState {
    public static final EditState NEUTRAL = new EditState(new float[9], false, false);
    final float[] color, tone, hue;
    final boolean invert, original;
    public EditState(float[] values, boolean invert, boolean original) {
        if (values.length != 9) throw new IllegalArgumentException("Nine adjustments required");
        float[] v = values.clone();
        for (int i = 0; i < v.length; i++) v[i] = Float.isFinite(v[i]) ? Math.max(-1, Math.min(1, v[i])) : 0;
        color = new float[]{1 + v[0], v[1], (float)Math.pow(2, 3 * v[2]), (float)Math.pow(2, v[3])};
        tone = new float[]{v[4], v[5], v[6], v[7]};
        hue = new float[]{(float)Math.cos(v[8] * Math.PI), (float)Math.sin(v[8] * Math.PI) / (float)Math.sqrt(3)};
        this.invert = invert;
        this.original = original;
    }
}
