package com.discnet.noise;

/** Reusable scalar-field inputs. All dimensions are in units of the short screen edge. */
final class FluidField {
    static final int CAPACITY = 10;
    final float[] masses = new float[CAPACITY * 4];
    final float[] shapes = new float[CAPACITY * 4];

    void mass(int i, float x, float y, float rx, float ry, float angle, float weight) {
        int k = i * 4;
        masses[k] = x; masses[k + 1] = y;
        masses[k + 2] = 1f / rx; masses[k + 3] = 1f / ry;
        shapes[k] = (float)Math.cos(angle); shapes[k + 1] = (float)Math.sin(angle);
        shapes[k + 2] = weight; shapes[k + 3] = i / 9f;
    }
}
