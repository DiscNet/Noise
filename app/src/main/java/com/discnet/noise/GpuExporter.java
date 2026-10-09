package com.discnet.noise;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.*;
import java.nio.*;
import static android.opengl.EGL14.*;
import static android.opengl.GLES20.*;

/** Separate EGL context: saving a PNG never blocks the preview GL thread. */
final class GpuExporter {
    static Bitmap render(Context context, Bitmap source, EditState state) throws Exception {
        return render(context, source, state, source.getWidth(), source.getHeight());
    }

    /** Resize at export time on GPU: the source bitmap is never destructively scaled. */
    static Bitmap render(Context context, Bitmap source, EditState state,
                         int targetWidth, int targetHeight) throws Exception {
        if(targetWidth < 1 || targetHeight < 1
                || targetWidth > 4096 || targetHeight > 4096
                || (long)targetWidth*targetHeight > 16777216L)
            throw new IllegalArgumentException("Tamanho de exportação fora do limite (1 a 4096 px).");
        EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
        EGLContext eglContext = EGL_NO_CONTEXT;
        EGLSurface surface = EGL_NO_SURFACE;
        GlPipeline pipeline = null;
        int[] framebuffer = new int[1], colorTexture = new int[1];
        Bitmap output = null;
        try {
            if (!eglInitialize(display, new int[2], 0, new int[2], 0)) throw new IllegalStateException("EGL initialize");
            EGLConfig[] configs = new EGLConfig[1]; int[] count = new int[1];
            int[] attributes = {EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
                EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8, EGL_NONE};
            if (!eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) || count[0] == 0)
                throw new IllegalStateException("EGL config");
            eglContext = eglCreateContext(display, configs[0], EGL_NO_CONTEXT, new int[]{EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE}, 0);
            surface = eglCreatePbufferSurface(display, configs[0], new int[]{EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE}, 0);
            if (!eglMakeCurrent(display, surface, surface, eglContext)) throw new IllegalStateException("EGL current");
            pipeline = new GlPipeline(context); pipeline.upload(source);
            int w = targetWidth, h = targetHeight;
            glGenTextures(1, colorTexture, 0); glBindTexture(GL_TEXTURE_2D, colorTexture[0]);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, null);
            glGenFramebuffers(1, framebuffer, 0); glBindFramebuffer(GL_FRAMEBUFFER, framebuffer[0]);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTexture[0], 0);
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("Framebuffer");
            pipeline.draw(w, h, state, true); GlPipeline.check("render export");
            output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            // Read in small strips: avoid allocating another full-resolution RGBA array.
            int strip = 32; ByteBuffer pixels = ByteBuffer.allocateDirect(w * strip * 4);
            int[] argb = new int[w * strip];
            for (int y = 0; y < h; y += strip) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                int rows = Math.min(strip, h - y); pixels.clear();
                glReadPixels(0, y, w, rows, GL_RGBA, GL_UNSIGNED_BYTE, pixels); GlPipeline.check("read export");
                for (int row = 0; row < rows; row++) for (int x = 0; x < w; x++) {
                    int i = (row * w + x) * 4;
                    argb[(rows - 1 - row) * w + x] = ((pixels.get(i + 3) & 255) << 24)
                        | ((pixels.get(i) & 255) << 16) | ((pixels.get(i + 1) & 255) << 8) | (pixels.get(i + 2) & 255);
                }
                output.setPixels(argb, 0, w, 0, h - y - rows, w, rows);
            }
            Bitmap result = output; output = null; return result;
        } finally {
            if (output != null) output.recycle();
            if (pipeline != null) pipeline.release();
            glDeleteFramebuffers(1, framebuffer, 0); glDeleteTextures(1, colorTexture, 0);
            if (display != EGL_NO_DISPLAY) {
                eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
                if (surface != EGL_NO_SURFACE) eglDestroySurface(display, surface);
                if (eglContext != EGL_NO_CONTEXT) eglDestroyContext(display, eglContext);
                eglReleaseThread(); eglTerminate(display);
            }
        }
    }
}
