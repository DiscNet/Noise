package com.discnet.noise;

import android.animation.ValueAnimator;
import android.content.*;
import android.database.ContentObserver;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.FrameLayout;

/** Passive Home decoration. TextureView keeps native controls above the GPU composition. */
final class OrganicBackdrop extends FrameLayout implements TextureView.SurfaceTextureListener {
    static final String QUALITY = "fluid_quality", REDUCE_MOTION = "fluid_reduce_motion";
    private final TextureView texture;
    private final FluidBackgroundLifecycle lifecycle = new FluidBackgroundLifecycle();
    private final FluidInteractionController interaction = new FluidInteractionController();
    private FluidBackgroundRenderer renderer;
    private boolean resumed, covered, attached, gpuFailed;
    private double retainedTime;
    private final SharedPreferences preferences;
    private final PowerManager power;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener = (p, key) -> refresh();
    private final ContentObserver animationObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override public void onChange(boolean selfChange) { refresh(); }
    };
    private final BroadcastReceiver powerReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { refresh(); }
    };
    private PowerManager.OnThermalStatusChangedListener thermalListener;
    OrganicBackdrop(Context c) {
        super(c);
        setClickable(false); setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        // Visible during surface creation; retained only if the GPU actually fails.
        setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xfff6f4f2, 0xffdedde5}));
        texture = new TextureView(c); texture.setOpaque(true);
        texture.setClickable(false); texture.setFocusable(false);
        texture.setSurfaceTextureListener(this);
        addView(texture, new LayoutParams(-1, -1));
        preferences = c.getSharedPreferences(SettingsScreen.PREFS, Context.MODE_PRIVATE);
        power = (PowerManager)c.getSystemService(Context.POWER_SERVICE);
    }
    void hostResumed(boolean value) { resumed = value; refresh(); }
    void covered(boolean value) { covered = value; refresh(); }
    void observe(MotionEvent event) {
        if (resumed && !covered && !reducedMotion()) interaction.observe(event, getWidth(), getHeight());
    }
    private boolean reducedMotion() {
        return preferences.getBoolean(REDUCE_MOTION, false) || !ValueAnimator.areAnimatorsEnabled();
    }
    private void refresh() {
        boolean lowPower = power != null && (power.isPowerSaveMode()
            || (Build.VERSION.SDK_INT >= 29 && power.getCurrentThermalStatus() >= PowerManager.THERMAL_STATUS_MODERATE));
        if (renderer != null) renderer.configure(preferences.getString(QUALITY, "auto"), lowPower);
        boolean visible = attached && resumed && !covered && isShown()
            && getWindowVisibility() == VISIBLE && !gpuFailed;
        if (!visible || reducedMotion()) interaction.clear();
        lifecycle.update(visible, !reducedMotion());
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow(); attached = true;
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
        getContext().getContentResolver().registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, animationObserver);
        getContext().registerReceiver(powerReceiver, new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED));
        if (Build.VERSION.SDK_INT >= 29 && power != null) {
            thermalListener = status -> post(this::refresh);
            power.addThermalStatusListener(thermalListener);
        }
        refresh();
    }
    @Override protected void onDetachedFromWindow() {
        attached = false; lifecycle.stop(); interaction.clear();
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener);
        getContext().getContentResolver().unregisterContentObserver(animationObserver);
        getContext().unregisterReceiver(powerReceiver);
        if (Build.VERSION.SDK_INT >= 29 && power != null && thermalListener != null) {
            power.removeThermalStatusListener(thermalListener); thermalListener = null;
        }
        super.onDetachedFromWindow(); // Texture listener transfers release to the EGL owner.
    }
    @Override public void onVisibilityAggregated(boolean visible) { super.onVisibilityAggregated(visible); refresh(); }
    @Override protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(visibility); if (preferences != null) refresh(); }
    @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int w, int h) {
        gpuFailed = false;
        renderer = new FluidBackgroundRenderer(getContext(), surface, w, h, interaction, retainedTime, () -> post(() -> {
            if (texture.getSurfaceTexture() != surface) return;
            gpuFailed = true; texture.setAlpha(0); lifecycle.stop();
        }));
        texture.setAlpha(1); lifecycle.bind(renderer); refresh();
    }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int w, int h) {
        if (renderer != null) renderer.resize(w, h); refresh();
    }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        lifecycle.bind(null);
        if (renderer != null) { retainedTime = renderer.seconds; renderer.close(); renderer = null; return false; }
        return true;
    }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) { }
    // Package-visible diagnostics are also used by the Android instrumentation gate.
    FluidBackgroundRenderer renderer() { return renderer; }
    TextureView texture() { return texture; }
}
