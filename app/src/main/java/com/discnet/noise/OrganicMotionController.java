package com.discnet.noise;

/** Continuous, deterministic motion; no frame-random positions and no looping animator. */
final class OrganicMotionController {
    private double seconds;
    private long previousNanos;
    double seconds() { return seconds; }
    void restore(double value) { seconds = value; previousNanos = 0; }
    void pause() { previousNanos = 0; }
    float advance(long nanos) {
        float dt = previousNanos == 0 ? 0f : (float)Math.min(.1, Math.max(0, (nanos - previousNanos) * 1e-9));
        previousNanos = nanos;
        seconds += dt;
        return dt;
    }
    // Quintic interpolation gives continuous velocity AND acceleration at lattice boundaries.
    static float noise(double x, int seed) {
        int n = (int)Math.floor(x);
        double f = x - n, u = f * f * f * (f * (f * 6 - 15) + 10);
        return (float)(hash(n, seed) * (1 - u) + hash(n + 1, seed) * u);
    }
    private static float hash(int x, int seed) {
        int v = x * 374761393 + seed * 668265263;
        v = (v ^ (v >>> 13)) * 1274126177;
        return ((v ^ (v >>> 16)) & 65535) / 32767.5f - 1;
    }
    static void sample(FluidField field, double t, float width, float height, float detail) {
        for (int group = 0; group < 3; group++) {
            double time = t + group * 17.31;
            float nx = noise(time * .043, 11 + group);
            float ny = noise(time * .037, 29 + group);
            float ax = (group == 0 ? .19f : group == 1 ? .84f : .42f) * width + nx * .13f;
            float ay = (group == 0 ? .34f : group == 1 ? .56f : .80f) * height + ny * .12f;
            // A breathing neck crosses the scalar threshold, while nonperiodic flow
            // and domain warping prevent a repeated composition or synchronized pairs.
            float separation = .47f + .32f * (float)Math.sin(time * .19 + noise(t * .021, group + 7));
            float angle = (group == 1 ? -.85f : 1.1f) + noise(time * .027, 37) * .65f;
            float dx = (float)Math.cos(angle) * separation * .5f;
            float dy = (float)Math.sin(angle) * separation * .5f;
            for (int side = 0; side < 2; side++) {
                int i = group * 2 + side;
                float sign = side == 0 ? -1 : 1;
                float stretch = noise(time * .051 + side * 2.7, 53);
                field.mass(i, ax + sign * dx, ay + sign * dy,
                    .32f + .05f * stretch, .44f - .07f * stretch,
                    angle - 1.1f + sign * .30f + ny * .4f, 1.65f);
            }
        }
        for (int i = 6; i < FluidField.CAPACITY; i++) {
            int k = ((i - 6) % 3) * 8;
            float life = .5f + .5f * noise(t * .083 + i * 1.37, 71);
            float weight = Math.max(0, Math.min(1, detail - (i < 8 ? 0 : 1)));
            field.mass(i, field.masses[k] + .23f + .17f * noise(t * .061, 83 + i),
                field.masses[k + 1] + .19f * noise(t * .071, 103 + i),
                .11f + life * .08f, .16f + life * .11f,
                noise(t * .052, i) * 3f, weight * (1.15f + life * .5f));
        }
    }
}
