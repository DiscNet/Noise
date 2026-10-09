package com.discnet.noise;

/**
 * Immutable collection of shader uniforms.
 * Every slider movement atomically publishes one state; no bitmap processing on UI thread.
 */
public final class EditState {
    public static final EditState NEUTRAL =
            new EditState(new float[9], new float[3], new float[7], false, false);

    // Original tonal controls, backward compatible with the 1.3 editor.
    final float[] color, tone, hue, style;
    // Effects page: Fade / Tom de pele / Poeira / Vinheta / Aberrações / Névoa / Nitidez.
    // Glow already exists as style[1] and is now exposed independently under Efeitos.
    final float[] fxA, fxB;
    final boolean invert, original;

    public EditState(float[] values, boolean invert, boolean original) {
        this(values, new float[3], new float[7], invert, original);
    }

    public EditState(float[] values, float[] styles, boolean invert, boolean original) {
        this(values, styles, new float[7], invert, original);
    }

    public EditState(float[] values, float[] styles, float[] filters, boolean invert, boolean original) {
        if (values == null || values.length != 9 || styles == null || styles.length != 3
                || filters == null || filters.length != 7)
            throw new IllegalArgumentException("Invalid adjustment count");

        float[] v = values.clone();
        for (int i = 0; i < v.length; i++)
            v[i] = Float.isFinite(v[i]) ? Math.max(-1f, Math.min(1f, v[i])) : 0f;
        float[] safeStyle = styles.clone();
        for (int i = 0; i < safeStyle.length; i++)
            safeStyle[i] = clamp(safeStyle[i]);
        float[] safeFilters = filters.clone();
        for (int i = 0; i < safeFilters.length; i++)
            safeFilters[i] = clamp(safeFilters[i]);

        color = new float[]{1f + v[0], v[1], (float) Math.pow(2, 3 * v[2]),
                (float) Math.pow(2, v[3])};
        tone = new float[]{v[4], v[5], v[6], v[7]};
        hue = new float[]{(float) Math.cos(v[8] * Math.PI),
                (float) Math.sin(v[8] * Math.PI) / (float) Math.sqrt(3)};
        style = safeStyle;
        fxA = new float[]{safeFilters[0], safeFilters[1], safeFilters[2], safeFilters[3]};
        fxB = new float[]{safeFilters[4], safeFilters[5], safeFilters[6]};
        this.invert = invert;
        this.original = original;
    }

    private static float clamp(float value) {
        return Float.isFinite(value) ? Math.max(0f, Math.min(1f, value)) : 0f;
    }
}
