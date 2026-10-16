package com.discnet.noise;

import android.app.Instrumentation;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.MotionEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;

/** Exercises the actual GLES2 TextureView, not a CPU approximation of its shader. */
final class FluidBackgroundTest {
    private static void require(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
    static void run(Instrumentation test, MainActivity activity, HomeScreen home) throws Exception {
        OrganicBackdrop backdrop = (OrganicBackdrop)home.getChildAt(0);
        long deadline = SystemClock.uptimeMillis() + 15000;
        while ((backdrop.renderer() == null || backdrop.renderer().frames < 2) && SystemClock.uptimeMillis() < deadline)
            SystemClock.sleep(40);
        FluidBackgroundRenderer renderer = backdrop.renderer();
        require(renderer != null && renderer.frames > 0, "Production fluid shader must compile and present");
        Bitmap first = capture(test, backdrop);
        int light = 0, dark = 0;
        for (int y = 0; y < first.getHeight(); y += 3) for (int x = 0; x < first.getWidth(); x += 3) {
            int red = Color.red(first.getPixel(x, y)); if (red > 205) light++; if (red < 95) dark++;
        }
        require(light > 100 && dark > 100, "Ink must contain dark masses and white negative space");
        double time = renderer.seconds;
        SystemClock.sleep(2200);
        Bitmap next = capture(test, backdrop);
        int changed = 0;
        for (int y = 0; y < first.getHeight(); y += 3) for (int x = 0; x < first.getWidth(); x += 3)
            if (Math.abs(Color.red(first.getPixel(x,y)) - Color.red(next.getPixel(x,y))) > 12) changed++;
        require(renderer.seconds > time && changed > 80, "Ink geometry must evolve, not only its clock");
        save(activity, first, "noise-fluid-start.png"); save(activity, next, "noise-fluid-later.png");
        first.recycle(); next.recycle();

        // Observe a real down/move/cancel sequence without navigating or intercepting buttons.
        test.runOnMainSync(() -> {
            long now = SystemClock.uptimeMillis();
            for (int i=0;i<3;i++) {
                MotionEvent event = MotionEvent.obtain(now, now+i*20,
                    i==0?MotionEvent.ACTION_DOWN:i==1?MotionEvent.ACTION_MOVE:MotionEvent.ACTION_CANCEL,
                    home.getWidth()*.12f+i*12, home.getHeight()*.40f, 0);
                home.dispatchTouchEvent(event); event.recycle();
            }
        });
        require(!backdrop.isClickable() && !backdrop.texture().isClickable(), "Fluid layer cannot take over navigation");

        test.runOnMainSync(() -> home.covered(true));
        SystemClock.sleep(300); long paused = renderer.frames; double pausedTime = renderer.seconds;
        SystemClock.sleep(400);
        require(renderer.frames == paused && renderer.seconds == pausedTime, "Covered Home must stop GPU work and time");
        test.runOnMainSync(() -> home.covered(false));
        SystemClock.sleep(500);
        require(renderer.frames > paused && renderer.seconds >= pausedTime && renderer.seconds < pausedTime+1,
            "Uncover must resume without reset or a wall-clock jump");
        test.runOnMainSync(() -> home.hostResumed(false));
        SystemClock.sleep(300); paused = renderer.frames;
        SystemClock.sleep(300);
        require(renderer.frames == paused, "Paused activity must stop fluid rendering");
        test.runOnMainSync(() -> home.hostResumed(true));

        SharedPreferences prefs = activity.getSharedPreferences(SettingsScreen.PREFS, 0);
        boolean originalReduced = prefs.getBoolean(OrganicBackdrop.REDUCE_MOTION, false);
        String originalQuality = prefs.getString(OrganicBackdrop.QUALITY, "auto");
        try {
            test.runOnMainSync(() -> prefs.edit().putBoolean(OrganicBackdrop.REDUCE_MOTION,true).apply());
            SystemClock.sleep(500); paused = renderer.frames;
            SystemClock.sleep(400);
            require(renderer.frames == paused, "Reduced motion must freeze the GPU scene");
            test.runOnMainSync(() -> prefs.edit().putBoolean(OrganicBackdrop.REDUCE_MOTION,false)
                .putString(OrganicBackdrop.QUALITY,"eco").apply());
            SystemClock.sleep(600);
            require(renderer.profile == FluidQualityController.ECONOMY && renderer.frames > paused,
                "Economy still animates the scalar field");
        } finally {
            test.runOnMainSync(() -> prefs.edit().putBoolean(OrganicBackdrop.REDUCE_MOTION, originalReduced)
                .putString(OrganicBackdrop.QUALITY, originalQuality).apply());
        }
        // A bounded diagnostic capture; no phone-FPS assertion on a software emulator.
        double[] costs = new double[50]; long before = renderer.frames, started = SystemClock.elapsedRealtime();
        for (int i=0;i<costs.length;i++) { SystemClock.sleep(30); costs[i]=renderer.frameCostMs; }
        Arrays.sort(costs);
        String report = "renderer=Android GLES2 TextureView\nframes="+(renderer.frames-before)
            +"\nwindow_ms="+(SystemClock.elapsedRealtime()-started)+"\ncpu_egl_sample_p50_ms="+costs[25]
            +"\ncpu_egl_sample_p95_ms="+costs[47]+"\nprofile="+renderer.profile
            +"\ncoalesced_requests="+renderer.coalescedFrames
            +"\nThese samples include draw submission and EGL backpressure; they are not hardware GPU timer queries.\n";
        try (FileOutputStream out = new FileOutputStream(new File(activity.getFilesDir(),"noise-fluid-metrics.txt"))) {
            out.write(report.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
    private static Bitmap capture(Instrumentation test, OrganicBackdrop backdrop) {
        final Bitmap[] image = new Bitmap[1];
        test.runOnMainSync(() -> image[0] = backdrop.texture().getBitmap(360, 640));
        require(image[0] != null, "GPU surface capture available"); return image[0];
    }
    private static void save(MainActivity activity, Bitmap bitmap, String name) throws Exception {
        try (FileOutputStream out = new FileOutputStream(new File(activity.getFilesDir(), name))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }
}
