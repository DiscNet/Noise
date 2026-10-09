package com.discnet.noise;

import android.app.*;
import android.content.Intent;
import android.graphics.*;
import android.os.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import android.net.Uri;

/** Runs on a real Android emulator, including the production GLES shader and export path. */
public class NoiseSmokeTest extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    private void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private Object field(Object object, String name) throws Exception { Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); }
    private void closeColor(int actual, int expected, String name) {
        for (int shift : new int[]{0, 8, 16, 24}) require(Math.abs(((actual >>> shift) & 255) - ((expected >>> shift) & 255)) <= 2, name + " channel " + shift + ": " + Integer.toHexString(actual) + " / " + Integer.toHexString(expected));
    }
    private Bitmap render(Bitmap source, float[] values, boolean invert, boolean before) throws Exception {
        return GpuExporter.render(getTargetContext(), source, new EditState(values, invert, before));
    }
    private void stage(String name) { Bundle b = new Bundle(); b.putString("stream", "Stage: " + name + "\n"); sendStatus(1, b); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            stage("launch");
            Intent launch = new Intent(getTargetContext(), MainActivity.class); launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            MainActivity activity = (MainActivity) startActivitySync(launch); waitForIdleSync();
            SystemClock.sleep(800);
            stage("shader checks");
            Bitmap source = Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888);
            int[] colors = {0xff103050, 0xffff0000, 0xff00ff00, 0xff0000ff, 0xff404040, 0xff808080, 0xffcccccc, 0xffffffff, 0xff000000, 0x804080c0, 0x00000000, 0xffb88060};
            source.setPixels(colors, 0, 4, 0, 0, 4, 3);
            Bitmap neutral = render(source, new float[9], false, false);
            Bitmap inverted = render(source, new float[9], true, false);
            for (int y = 0; y < 3; y++) for (int x = 0; x < 4; x++) {
                int c = source.getPixel(x, y); closeColor(neutral.getPixel(x,y), c, "Neutral/orientation");
                if (Color.alpha(c) > 0) closeColor(inverted.getPixel(x,y), (c & 0xff000000) | ((~c) & 0xffffff), "Invert");
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); inverted.compress(Bitmap.CompressFormat.PNG, 100, bytes);
            byte[] png = bytes.toByteArray(); Bitmap decoded = BitmapFactory.decodeByteArray(png, 0, png.length);
            closeColor(decoded.getPixel(1, 2), inverted.getPixel(1, 2), "PNG alpha"); decoded.recycle();
            for (int index = 0; index < 9; index++) {
                float[] values = new float[9]; values[index] = .75f;
                Bitmap changed = render(source, values, false, false);
                boolean differs = false;
                for (int y = 0; y < 3; y++) for (int x = 0; x < 4; x++) if (changed.getPixel(x, y) != neutral.getPixel(x, y)) differs = true;
                require(differs, "Adjustment " + index + " must affect pixels"); changed.recycle();
                Bitmap before = render(source, values, true, true);
                for (int y = 0; y < 3; y++) for (int x = 0; x < 4; x++) closeColor(before.getPixel(x, y), source.getPixel(x, y), "Before bypass"); before.recycle();
            }
            float[] desaturate = new float[9]; desaturate[0] = -1;
            Bitmap grey = render(source, desaturate, false, false); int grayPixel = grey.getPixel(1, 0);
            require(Math.abs(Color.red(grayPixel) - Color.green(grayPixel)) <= 1 && Math.abs(Color.green(grayPixel) - Color.blue(grayPixel)) <= 1, "Full desaturation"); grey.recycle();
            Bitmap speck = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888); speck.eraseColor(0xff808080); speck.setPixel(1, 1, 0xff909090);
            float[] reduce = new float[9]; reduce[7] = -1; Bitmap smooth = render(speck, reduce, false, false);
            require(Color.red(smooth.getPixel(1, 1)) < 144, "Denoise reduces isolated noise"); smooth.recycle(); speck.recycle();
            neutral.recycle(); inverted.recycle(); source.recycle();

            stage("import");
            Bitmap demo = scene();
            File input = new File(getTargetContext().getFilesDir(), "fixture.png");
            try (FileOutputStream stream = new FileOutputStream(input)) { demo.compress(Bitmap.CompressFormat.PNG, 100, stream); }
            demo.recycle();
            Method load = MainActivity.class.getDeclaredMethod("load", Uri.class); load.setAccessible(true);
            runOnMainSync(() -> { try { load.invoke(activity, Uri.fromFile(input)); } catch (Exception e) { throw new RuntimeException(e); } });
            long deadline = SystemClock.uptimeMillis() + 10000;
            while ((Boolean)field(activity, "loading") && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(20);
            require(field(activity, "original") != null, "Image import through ContentResolver/ImageDecoder");
            EditorSurface surface = (EditorSurface) field(activity, "preview");
            SeekBar[] sliders = (SeekBar[]) field(activity, "sliders");
            SystemClock.sleep(400);
            stage("live preview");
            long startFrames = surface.frameCount;
            for (int i = 0; i < 45; i++) {
                final int progress = 60 + i * 3;
                runOnMainSync(() -> sliders[0].setProgress(progress));
                SystemClock.sleep(16);
            }
            long drawn = surface.frameCount - startFrames;
            require(drawn >= 3, "Preview must redraw DURING slider movement, frames=" + drawn);
            runOnMainSync(() -> { sliders[0].setProgress(125); sliders[1].setProgress(118); });
            Switch invert = (Switch) field(activity, "invertSwitch");
            runOnMainSync(() -> invert.setChecked(true)); SystemClock.sleep(80);
            require(((EditState)field(surface, "state")).invert, "Switch enables inversion");
            runOnMainSync(() -> invert.setChecked(false)); SystemClock.sleep(150);
            require(!((EditState)field(surface, "state")).invert, "Switch disables inversion");
            require(surface.frameCount > startFrames + drawn, "Preview survives separate EGL exports");
            stage("PNG export");
            File pngFile = new File(getTargetContext().getFilesDir(), "export.png");
            Method export = MainActivity.class.getDeclaredMethod("export", Uri.class); export.setAccessible(true);
            runOnMainSync(() -> { try { export.invoke(activity, Uri.fromFile(pngFile)); } catch (Exception e) { throw new RuntimeException(e); } });
            deadline = SystemClock.uptimeMillis() + 15000;
            while ((Boolean)field(activity, "exporting") && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(20);
            require(pngFile.length() > 1000, "Image export file");
            Bitmap saved = BitmapFactory.decodeFile(pngFile.getPath());
            require(saved != null && saved.getWidth() == 900 && saved.getHeight() == 900, "PNG dimensions");
            require(Color.blue(saved.getPixel(100,100)) > Color.blue(saved.getPixel(100,800)), "PNG strips stay upright"); saved.recycle();
            long framesBeforeExportUpdate = surface.frameCount;
            runOnMainSync(() -> sliders[0].setProgress(126)); SystemClock.sleep(150);
            require(surface.frameCount > framesBeforeExportUpdate, "Preview alive after PNG export");
            stage("screenshot");
            Bitmap screenshot = getUiAutomation().takeScreenshot();
            require(screenshot != null, "Screenshot");
            try (FileOutputStream out = new FileOutputStream(new File(getTargetContext().getFilesDir(), "noise-ui.png"))) { screenshot.compress(Bitmap.CompressFormat.PNG, 100, out); }
            screenshot.recycle();
            result.putString("stream", "NOISE_SMOKE_OK: GPU neutral/orientation, import/export files, alpha PNG, all 9 adjustments, compare bypass, denoise, inversion toggle, live slider frames=" + drawn + "\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            StringWriter stack = new StringWriter(); error.printStackTrace(new PrintWriter(stack)); result.putString("stream", "NOISE_SMOKE_FAILED\n" + stack); finish(Activity.RESULT_CANCELED, result);
        }
    }
    private Bitmap scene() {
        Bitmap image = Bitmap.createBitmap(900, 900, Bitmap.Config.ARGB_8888); Canvas c = new Canvas(image); Paint p = new Paint(3);
        p.setShader(new LinearGradient(0, 0, 900, 900, new int[]{0xff1a4775, 0xff688dc2, 0xffe29b98}, null, Shader.TileMode.CLAMP)); c.drawPaint(p);
        p.setShader(new RadialGradient(650, 270, 170, 0xffffe3b4, 0x00ffe3b4, Shader.TileMode.CLAMP)); c.drawCircle(650, 270, 170, p);
        p.setShader(null); p.setColor(0xffffecc2); c.drawCircle(650, 270, 67, p);
        Path hills = new Path(); hills.moveTo(0, 590); hills.cubicTo(170, 460, 180, 330, 310, 510); hills.cubicTo(470, 630, 640, 360, 900, 490); hills.lineTo(900, 900); hills.lineTo(0, 900); hills.close();
        p.setShader(new LinearGradient(0, 400, 0, 900, 0xff486a8d, 0xff243b59, Shader.TileMode.CLAMP)); c.drawPath(hills, p);
        Path front = new Path(); front.moveTo(0, 720); front.cubicTo(280, 560, 350, 790, 570, 660); front.cubicTo(760, 540, 790, 670, 900, 600); front.lineTo(900,900); front.lineTo(0,900); front.close();
        p.setShader(new LinearGradient(0, 590, 0, 900, 0xff224d58, 0xff071e35, Shader.TileMode.CLAMP)); c.drawPath(front,p);
        return image;
    }
}
