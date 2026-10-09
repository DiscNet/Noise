package com.discnet.noise;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.SeekBar;

final class Glass {
    static final int INK = 0xfff3f6ff, MUTED = 0xffa7b3cc, LIME = 0xffcaff70, CYAN = 0xff46ddff, VIOLET = 0xffb8a0ff;
    static int dp(Context c, float n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }
    static GradientDrawable panel(Context c, int first, int second, float radius, int border) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{first, second});
        d.setCornerRadius(dp(c, radius)); d.setStroke(dp(c, 1), border); return d;
    }
    static void button(View view, boolean primary) {
        Context c = view.getContext();
        GradientDrawable background = primary ? panel(c, LIME, 0xff96ef76, 18, 0xffdeffae)
            : panel(c, 0x45364467, 0x25232e49, 18, 0x40c0d1f4);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33ffffff), background, null));
        view.setMinimumHeight(dp(c, 48));
        view.setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8));
    }
    static final class Backdrop extends FrameLayout {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Backdrop(Context c) { super(c); setWillNotDraw(false); setBackgroundColor(0xff080e1d); }
        private void glow(Canvas canvas, float x, float y, float radius, int color) {
            paint.setShader(new RadialGradient(x, y, radius, color, Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawCircle(x, y, radius, paint);
        }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); float w = getWidth(), h = getHeight();
            glow(c, w * .98f, h * .10f, w * .9f, 0x7526bcd6);
            glow(c, w * .06f, h * .53f, w * .95f, 0x694b26d6);
            glow(c, w * .92f, h * .92f, w * .75f, 0x363ada77);
            paint.setShader(null);
        }
    }
    /** Native SeekBar input/accessibility, custom bipolar track and line details. */
    static final class Slider extends SeekBar {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int accent;
        Slider(Context c, int accent) {
            super(c); this.accent = accent; setMax(200); setProgress(100);
            setPadding(dp(c, 12), 0, dp(c, 12), 0);
            GradientDrawable thumb = new GradientDrawable(); thumb.setColor(Color.TRANSPARENT);
            thumb.setSize(dp(c, 24), dp(c, 24)); setThumb(thumb); setThumbOffset(dp(c, 12));
            setSplitTrack(false); setMinimumHeight(dp(c, 40));
        }
        @Override protected synchronized void onDraw(Canvas canvas) {
            float left = getPaddingLeft(), right = getWidth() - getPaddingRight(), y = getHeight() / 2f;
            float value = left + (right - left) * getProgress() / 200f, middle = (left + right) / 2;
            paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeWidth(dp(getContext(), 4));
            paint.setColor(0x35d1dcff); canvas.drawLine(left, y, right, y, paint);
            paint.setColor(isEnabled() ? accent : 0xff65718a); canvas.drawLine(middle, y, value, y, paint);
            paint.setStrokeWidth(dp(getContext(), 1)); paint.setColor(0x88e2eaff);
            for (int i = 0; i <= 4; i++) { float x = left + (right - left) * i / 4; canvas.drawLine(x, y + dp(getContext(), 8), x, y + dp(getContext(), i == 2 ? 13 : 10), paint); }
            paint.setColor(isEnabled() ? accent : 0xff65718a); canvas.drawCircle(value, y, dp(getContext(), isPressed() ? 11 : 9), paint);
            paint.setColor(0xff0d1628); canvas.drawCircle(value, y, dp(getContext(), 3), paint);
            if (isFocused()) { paint.setColor(0x44ffffff); canvas.drawCircle(value, y, dp(getContext(), 14), paint); }
        }
    }
}
