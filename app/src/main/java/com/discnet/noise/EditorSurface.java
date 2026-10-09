package com.discnet.noise;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLSurfaceView;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** No bitmap loops, task queue or debounce on the slider path. Latest state wins. */
public final class EditorSurface extends GLSurfaceView implements GLSurfaceView.Renderer {
    public interface Listener { void ready(int maxTexture); void failed(String message); }
    private volatile EditState state = EditState.NEUTRAL;
    private volatile Bitmap source;
    private Bitmap uploaded;
    private GlPipeline pipeline;
    private final Listener listener;
    private int width = 1, height = 1;
    volatile long frameCount;
    public EditorSurface(Context context, Listener listener) {
        super(context); this.listener = listener;
        setEGLContextClientVersion(2); setEGLConfigChooser(8, 8, 8, 8, 0, 0);
        setPreserveEGLContextOnPause(true); setRenderer(this); setRenderMode(RENDERMODE_WHEN_DIRTY);
        setContentDescription("Prévia da imagem. Os ajustes aparecem enquanto você arrasta.");
    }
    public void setImage(Bitmap bitmap) { source = bitmap; requestRender(); }
    public void setEditState(EditState next) { state = next; requestRender(); }
    @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        uploaded = null;
        try { pipeline = new GlPipeline(getContext()); int limit = GlPipeline.maximumTextureSize(); post(() -> listener.ready(limit)); }
        catch (Exception e) { pipeline = null; post(() -> listener.failed("A GPU não conseguiu iniciar o editor.")); }
    }
    @Override public void onSurfaceChanged(GL10 gl, int w, int h) { width = w; height = h; }
    @Override public void onDrawFrame(GL10 gl) {
        if (pipeline == null) return;
        try {
            Bitmap next = source;
            if (next != null && next != uploaded) { pipeline.upload(next); uploaded = next; }
            pipeline.draw(width, height, state, false);
            frameCount++;
        } catch (Exception | OutOfMemoryError e) {
            pipeline = null; post(() -> listener.failed("Não foi possível exibir esta imagem. Abra uma imagem menor."));
        }
    }
}
