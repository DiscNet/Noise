package com.discnet.noise;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.*;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/** GLES2, one draw/pass on an owned EGL thread. No CPU pixel loop during animation. */
final class FluidBackgroundRenderer {
    private final Context context;
    private final SurfaceTexture texture;
    private final HandlerThread thread = new HandlerThread("Noise fluid");
    private final Handler worker;
    private final Runnable failure;
    private final AtomicBoolean queued = new AtomicBoolean();
    private final OrganicMotionController motion = new OrganicMotionController();
    private final FluidField field = new FluidField();
    private final FluidQualityController quality = new FluidQualityController();
    private final FluidInteractionController interaction;
    private final float[] touch = new float[5];
    private final FloatBuffer quad = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder())
        .asFloatBuffer().put(new float[]{-1,-1, 1,-1, -1,1, 1,1});
    private volatile boolean active, closed, failed;
    private volatile long requestedNanos;
    private volatile boolean requestedMotion;
    private volatile int width, height;
    private volatile String preference = "auto";
    private volatile boolean constrained;
    private EGLDisplay display = EGL14.EGL_NO_DISPLAY;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface surface = EGL14.EGL_NO_SURFACE;
    private int program, noiseTexture, count, bufferW, bufferH;
    private int massLocation, shapeLocation, viewportLocation, touchLocation, stateLocation;
    private float detail = 1;
    volatile long frames, coalescedFrames, intervalNanos = 16_666_667L;
    volatile double seconds, frameCostMs;
    volatile int profile = FluidQualityController.BALANCED;
    private final Runnable draw = this::drawRequestedFrame;

    FluidBackgroundRenderer(Context c, SurfaceTexture texture, int w, int h,
                            FluidInteractionController interaction, double initialTime, Runnable failure) {
        context = c.getApplicationContext(); this.texture = texture;
        width = w; height = h; this.interaction = interaction; this.failure = failure;
        motion.restore(initialTime); seconds = initialTime;
        quad.position(0);
        thread.start(); worker = new Handler(thread.getLooper());
    }
    void configure(String mode, boolean lowPower) { preference = mode; constrained = lowPower; }
    void resize(int w, int h) { width = w; height = h; worker.post(() -> bufferW = 0); }
    void setActive(boolean value) {
        active = value;
        if (!value) worker.post(motion::pause);
    }
    void requestFrame(long nanos, boolean animate) {
        if (closed || failed || !active) return;
        if (!queued.compareAndSet(false, true)) { coalescedFrames++; return; }
        requestedNanos = nanos; requestedMotion = animate;
        worker.post(draw);
    }
    private void drawRequestedFrame() {
        try {
            if (closed || !active || failed || width < 1 || height < 1) return;
            if (display == EGL14.EGL_NO_DISPLAY) initialize();
            long start = System.nanoTime();
            int w = width, h = height;
            quality.configure(preference, constrained, start);
            float dt = requestedMotion ? motion.advance(requestedNanos) : 0;
            if (!requestedMotion) motion.pause();
            seconds = motion.seconds();
            detail += (quality.level() - detail) * (1f - (float)Math.exp(-dt * 1.6f));
            if (frames == 0) detail = quality.level();
            if (count == 6) detail = 0;
            double scale = Math.min(1, Math.sqrt(quality.pixelBudget() / (double)(w * h)));
            int rw = Math.max(2, (int)Math.round(w * scale));
            int rh = Math.max(2, (int)Math.round(h * scale));
            if (rw != bufferW || rh != bufferH) {
                texture.setDefaultBufferSize(rw, rh);
                bufferW = rw; bufferH = rh;
            }
            // Query after resizing: a driver may apply a new buffer size on the next swap.
            EGL14.eglQuerySurface(display, surface, EGL14.EGL_WIDTH, surfaceSize, 0);
            EGL14.eglQuerySurface(display, surface, EGL14.EGL_HEIGHT, surfaceSize, 1);
            GLES20.glViewport(0, 0, surfaceSize[0], surfaceSize[1]);
            float unit = Math.min(w, h);
            OrganicMotionController.sample(field, seconds, w / unit, h / unit, detail);
            interaction.sample(dt, touch);
            GLES20.glUseProgram(program);
            GLES20.glUniform4fv(massLocation, count, field.masses, 0);
            GLES20.glUniform4fv(shapeLocation, count, field.shapes, 0);
            GLES20.glUniform4f(viewportLocation, w / unit, h / unit, (float)seconds, detail);
            GLES20.glUniform4fv(touchLocation, 1, touch, 0);
            GLES20.glUniform4f(stateLocation, touch[4], Math.max(.009f, 3f / Math.min(rw, rh)), 0, 0);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            if (!EGL14.eglSwapBuffers(display, surface)) {
                int error = EGL14.eglGetError();
                if (error == EGL14.EGL_CONTEXT_LOST) {
                    // Retain the CPU timeline and recreate only GPU resources next frame.
                    releaseEgl(); motion.pause(); return;
                }
                throw new IllegalStateException("eglSwapBuffers: 0x" + Integer.toHexString(error));
            }
            frames++;
            frameCostMs = (System.nanoTime() - start) * 1e-6;
            quality.record(frameCostMs, System.nanoTime());
            profile = quality.level(); intervalNanos = quality.frameIntervalNanos();
        } catch (Exception error) {
            failed = true; active = false;
            Log.e("NoiseFluid", "GPU background unavailable", error);
            releaseEgl(); failure.run();
        } finally { queued.set(false); }
    }
    private final int[] surfaceSize = new int[2];

    private void initialize() throws Exception {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] version = new int[2];
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) throw new IllegalStateException("eglInitialize");
        EGLConfig[] configs = new EGLConfig[1]; int[] found = new int[1];
        int[] config = {EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT, EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 0, EGL14.EGL_STENCIL_SIZE, 0, EGL14.EGL_NONE};
        if (!EGL14.eglChooseConfig(display, config, 0, configs, 0, 1, found, 0) || found[0] == 0)
            throw new IllegalStateException("No GLES2 window config");
        eglContext = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
            new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
        surface = EGL14.eglCreateWindowSurface(display, configs[0], texture,
            new int[]{EGL14.EGL_NONE}, 0);
        if (!EGL14.eglMakeCurrent(display, surface, surface, eglContext)) throw new IllegalStateException("eglMakeCurrent");
        EGL14.eglSwapInterval(display, 1);
        int[] uniforms = new int[1]; GLES20.glGetIntegerv(GLES20.GL_MAX_FRAGMENT_UNIFORM_VECTORS, uniforms, 0);
        count = uniforms[0] >= 24 ? 10 : 6; // Six masses fit the GLES2 minimum of 16 vectors.
        int vertex = 0, fragment = 0;
        try {
            vertex = compile(GLES20.GL_VERTEX_SHADER, asset("fluid.vert"));
            fragment = compile(GLES20.GL_FRAGMENT_SHADER, "#define MASS_COUNT " + count + "\n" + asset("fluid.frag"));
            program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, vertex); GLES20.glAttachShader(program, fragment);
            GLES20.glBindAttribLocation(program, 0, "aPosition"); GLES20.glLinkProgram(program);
            int[] ok = new int[1]; GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, ok, 0);
            if (ok[0] == 0) throw new IllegalStateException(GLES20.glGetProgramInfoLog(program));
        } finally {
            if (vertex != 0) GLES20.glDeleteShader(vertex);
            if (fragment != 0) GLES20.glDeleteShader(fragment);
        }
        GLES20.glUseProgram(program);
        massLocation = GLES20.glGetUniformLocation(program, "uMass[0]");
        shapeLocation = GLES20.glGetUniformLocation(program, "uShape[0]");
        viewportLocation = GLES20.glGetUniformLocation(program, "uViewport");
        touchLocation = GLES20.glGetUniformLocation(program, "uTouch");
        stateLocation = GLES20.glGetUniformLocation(program, "uState");
        GLES20.glEnableVertexAttribArray(0);
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, quad);
        GLES20.glDisable(GLES20.GL_BLEND); GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        int[] id = new int[1]; GLES20.glGenTextures(1, id, 0); noiseTexture = id[0];
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, noiseTexture);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
        ByteBuffer lattice = ByteBuffer.allocateDirect(64 * 64 * 4);
        int seed = 0x4e4f4953;
        for (int i = 0; i < 64 * 64 * 4; i++) {
            seed ^= seed << 13; seed ^= seed >>> 17; seed ^= seed << 5;
            lattice.put((byte)(seed >>> 24));
        }
        lattice.flip();
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 64, 64, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, lattice);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uNoise"), 0);
        bufferW = bufferH = 0;
    }
    private String asset(String name) throws Exception {
        try (InputStream input = context.getAssets().open(name)) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] bytes = new byte[4096]; int n;
            while ((n = input.read(bytes)) != -1) output.write(bytes, 0, n);
            return output.toString("UTF-8");
        }
    }
    private static int compile(int kind, String source) {
        int shader = GLES20.glCreateShader(kind);
        GLES20.glShaderSource(shader, source); GLES20.glCompileShader(shader);
        int[] ok = new int[1]; GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            String error = GLES20.glGetShaderInfoLog(shader); GLES20.glDeleteShader(shader);
            throw new IllegalStateException(error);
        }
        return shader;
    }
    void close() {
        if (closed) return;
        closed = true; active = false;
        worker.post(() -> {
            try { releaseEgl(); } finally { texture.release(); thread.quitSafely(); }
        });
    }
    private void releaseEgl() {
        if (display == EGL14.EGL_NO_DISPLAY) return;
        if (program != 0) GLES20.glDeleteProgram(program);
        if (noiseTexture != 0) GLES20.glDeleteTextures(1, new int[]{noiseTexture}, 0);
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
        if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface);
        if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, eglContext);
        EGL14.eglTerminate(display); EGL14.eglReleaseThread();
        display = EGL14.EGL_NO_DISPLAY; surface = EGL14.EGL_NO_SURFACE; eglContext = EGL14.EGL_NO_CONTEXT;
        program = noiseTexture = 0;
    }
}
