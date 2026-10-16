package com.discnet.noise;

import java.io.DataOutputStream;

/** Host-side probe uses production motion code, not a translated model. */
public final class FluidFieldProbe {
    public static void main(String[] args) throws Exception {
        OrganicMotionController clock = new OrganicMotionController();
        clock.advance(1_000_000_000L); clock.advance(1_016_666_667L);
        double before = clock.seconds(); clock.pause(); clock.advance(900_000_000_000L);
        check(clock.seconds() == before, "Paused time must not be accumulated");
        check(before > .016 && before < .017, "Vsync clock");
        for (int i = 1; i < 100; i++) {
            float a = OrganicMotionController.noise(i - 1e-5, 7);
            float b = OrganicMotionController.noise(i + 1e-5, 7);
            check(Math.abs(a-b) < .0001, "Noise continuity at lattice boundary");
        }
        FluidQualityController quality = new FluidQualityController();
        quality.configure("high", false, 1);
        check(quality.level() == 2, "Explicit high profile");
        for (int i=0;i<100;i++) quality.record(40, 10_000_000_000L+i*16_666_667L);
        check(quality.level() < 2, "Sustained overload lowers quality");
        quality.configure("auto", true, 20_000_000_000L);
        check(quality.level() == 0 && quality.frameIntervalNanos() > 30_000_000L, "Power-save budget");
        FluidField field = new FluidField();
        int frames = Integer.parseInt(args[0]); float fps = Float.parseFloat(args[1]);
        float width = Float.parseFloat(args[2]), height = Float.parseFloat(args[3]);
        float detail = Float.parseFloat(args[4]);
        DataOutputStream out = new DataOutputStream(System.out);
        for (int f = 0; f < frames; f++) {
            OrganicMotionController.sample(field, f/(double)fps, width, height, detail);
            for (float x : field.masses) { check(Float.isFinite(x), "Finite mass"); out.writeFloat(x); }
            for (float x : field.shapes) { check(Float.isFinite(x), "Finite shape"); out.writeFloat(x); }
        }
        out.flush();
    }
    private static void check(boolean ok, String reason) { if (!ok) throw new AssertionError(reason); }
}
