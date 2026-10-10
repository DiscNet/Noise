package com.discnet.noise;

import android.app.*;
import android.content.Intent;
import android.graphics.*;
import android.os.*;
import android.widget.*;
import android.view.*;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import android.net.Uri;
import android.provider.MediaStore;
import android.database.Cursor;

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
            // 1.2 styles must alter pixels, be deterministic and bypassed by comparison.
            Bitmap poster = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
            Canvas posterCanvas = new Canvas(poster);
            Paint posterPaint = new Paint();
            posterPaint.setShader(new LinearGradient(0,0,64,64,0xff3045ae,0xffff9c44,Shader.TileMode.CLAMP));
            posterCanvas.drawRect(0,0,64,64,posterPaint);
            Bitmap plainPoster=render(poster,new float[9],false,false);
            for(int effect=0;effect<3;effect++){
                float[] style=new float[3];style[effect]=0.8f;
                Bitmap altered=GpuExporter.render(getTargetContext(),poster,new EditState(new float[9],style,false,false));
                Bitmap bypassed=GpuExporter.render(getTargetContext(),poster,new EditState(new float[9],style,false,true));
                int differences=0;
                for(int yy=0;yy<64;yy+=4)for(int xx=0;xx<64;xx+=4){
                    if(altered.getPixel(xx,yy)!=plainPoster.getPixel(xx,yy))differences++;
                    closeColor(bypassed.getPixel(xx,yy),poster.getPixel(xx,yy),"Dither original bypass "+effect);
                }
                require(differences>5,"Style "+effect+" must change image pixels");
                altered.recycle();bypassed.recycle();
            }
            plainPoster.recycle();poster.recycle();

            // Reference look: black negative space + cool/warm bright contours,
            // stable spatial noise (no frame-to-frame shimmer or changing PNG).
            Bitmap stripes=Bitmap.createBitmap(120,96,Bitmap.Config.ARGB_8888);
            stripes.eraseColor(0xff000000);
            Canvas neonCanvas=new Canvas(stripes);
            Paint neonPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
            neonPaint.setColor(0xff377aff);
            neonCanvas.drawRect(12,14,55,83,neonPaint);
            neonPaint.setColor(0xffff9e32);
            neonCanvas.drawRect(65,14,108,83,neonPaint);
            float[] neonSettings={0.94f,0.66f,0.13f};
            EditState neonState=new EditState(new float[9],neonSettings,false,false);
            Bitmap neonA=GpuExporter.render(getTargetContext(),stripes,neonState);
            Bitmap neonB=GpuExporter.render(getTargetContext(),stripes,neonState);
            int lit=0, cool=0, warm=0;
            for(int yy=0; yy<96; yy++)for(int xx=0; xx<120; xx++){
                int ca=neonA.getPixel(xx,yy), cb=neonB.getPixel(xx,yy);
                require(ca==cb,"Neon halftone deterministic export");
                // A glow halo is allowed to spill into dark surrounding pixels.
                // Verify the substantive neon colors while preserving alpha.
                if (yy>18 && yy<79) {
                    if(Color.red(ca)+Color.green(ca)+Color.blue(ca)>160)lit++;
                    if(xx>17&&xx<49&&Color.blue(ca)>Color.red(ca)+10)cool++;
                    if(xx>71&&xx<102&&Color.red(ca)>Color.blue(ca)+25)warm++;
                }
            }
            require(lit>300,"Dither should produce plenty of luminous traces");
            require(cool>80&&warm>80,"Dither must separate cold blue and warm orange areas");
            neonA.recycle();neonB.recycle();stripes.recycle();

            stage("creative adjustments");
            Bitmap effectsImage = Bitmap.createBitmap(128, 96, Bitmap.Config.ARGB_8888);
            Canvas fxCanvas = new Canvas(effectsImage);
            Paint fxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            fxPaint.setShader(new LinearGradient(0, 0, 128, 96,
                    new int[]{0xff151d33, 0xffa76942, 0xfff0c9ab},
                    null, Shader.TileMode.CLAMP));
            fxCanvas.drawPaint(fxPaint);
            fxPaint.setShader(null);
            fxPaint.setColor(0xffdd764b);
            fxCanvas.drawRect(36, 16, 106, 76, fxPaint);
            fxPaint.setColor(0xff1038bb);
            fxCanvas.drawRect(10, 24, 33, 90, fxPaint);
            Bitmap plainFx = GpuExporter.render(getTargetContext(), effectsImage,
                    new EditState(new float[9], false, false));
            for (int effect = 0; effect < 7; effect++) {
                float[] filters = new float[7]; filters[effect] = 0.95f;
                EditState alteredState = new EditState(new float[9], new float[3],
                        filters, false, false);
                Bitmap changed = GpuExporter.render(getTargetContext(), effectsImage, alteredState);
                int changes = 0;
                for (int y = 0; y < 96; y++) for (int x = 0; x < 128; x++) {
                    if (changed.getPixel(x, y) != plainFx.getPixel(x, y)) changes++;
                }
                require(changes > 10, "New creative effect " + effect +
                        " must change image pixels, changed=" + changes);
                changed.recycle();
            }
            // A single combined bypass render checks the Original switch for
            // all filters without recompiling the large GLES2 shader 7 times.
            float[] allFilters = {0.95f, 0.95f, 0.95f, 0.95f, 0.95f, 0.95f, 0.95f};
            Bitmap bypassAll = GpuExporter.render(getTargetContext(), effectsImage,
                    new EditState(new float[9], new float[3], allFilters, true, true));
            for(int y=0;y<96;y+=13)for(int x=0;x<128;x+=13)
                closeColor(bypassAll.getPixel(x,y),effectsImage.getPixel(x,y),
                        "Original bypasses all creative filters");
            bypassAll.recycle();
            // New eighth filter: rotational rim blur.
            float[] rotation = new float[8];rotation[7]=0.93f;
            Bitmap rotatedEdges=GpuExporter.render(getTargetContext(),effectsImage,
                new EditState(new float[9],new float[3],rotation,false,false));
            int blurPixels=0;
            for(int yy=0;yy<96;yy++)for(int xx=0;xx<128;xx++)
                if(rotatedEdges.getPixel(xx,yy)!=plainFx.getPixel(xx,yy))blurPixels++;
            require(blurPixels>50,"Rotational blur modifies image edges");
            rotatedEdges.recycle();
            // Resize from original 128x96 to 64x48 directly on the render target.
            Bitmap resized=GpuExporter.render(getTargetContext(),effectsImage,
                new EditState(new float[9],false,false),64,48);
            require(resized.getWidth()==64&&resized.getHeight()==48,
                "Non-destructive custom size export");
            resized.recycle();
            float[] soloGlow = new float[]{0f, 0.9f, 0f};
            Bitmap luminous = GpuExporter.render(getTargetContext(), effectsImage,
                    new EditState(new float[9], soloGlow, new float[7], false, false));
            int changedGlow = 0;
            for(int y=0;y<96;y++)for(int x=0;x<128;x++)
                if(luminous.getPixel(x,y)!=plainFx.getPixel(x,y))changedGlow++;
            require(changedGlow>20, "Standalone Glow must work without Dither");
            stage("art effects GPU");
            // Three distinct editable visual styles from the user's references.
            for(int mode=0;mode<3;mode++){
                float[] params=EditState.ART_DEFAULTS.clone();
                params[mode*6]=.94f;
                EditState artState=new EditState(new float[9],new float[3],new float[8],
                    EditState.DITHER_DEFAULTS,params,false,false);
                Bitmap styled=GpuExporter.render(getTargetContext(),effectsImage,artState);
                int changedPixels=0;
                for(int yy=0;yy<effectsImage.getHeight();yy++)
                    for(int xx=0;xx<effectsImage.getWidth();xx++)
                        if(styled.getPixel(xx,yy)!=plainFx.getPixel(xx,yy))changedPixels++;
                require(changedPixels>1500,"Mode "+mode+" must produce distinct, nontrivial output");
                Bitmap originalMode=GpuExporter.render(getTargetContext(),effectsImage,
                    new EditState(new float[9],new float[3],new float[8],
                        EditState.DITHER_DEFAULTS,params,false,true));
                for(int yy=0;yy<96;yy+=13)for(int xx=0;xx<128;xx+=13)
                    closeColor(originalMode.getPixel(xx,yy),effectsImage.getPixel(xx,yy),
                        "Original bypasses art "+mode);
                styled.recycle();originalMode.recycle();
            }
            stage("ASCII character art GPU");
            Bitmap block=Bitmap.createBitmap(192,128,Bitmap.Config.ARGB_8888);
            block.eraseColor(0xff000000);
            Canvas blockCanvas=new Canvas(block);
            Paint white=new Paint();
            white.setColor(0xffffffff);
            blockCanvas.drawRect(26,22,166,104,white);
            EditState ascii=new EditState(new float[9],new float[3],new float[8],
                EditState.DITHER_DEFAULTS,EditState.ART_DEFAULTS,
                EditState.ASCII_DEFAULTS,true,false,false,false,false);
            Bitmap textA=GpuExporter.render(getTargetContext(),block,ascii);
            Bitmap textB=GpuExporter.render(getTargetContext(),block,ascii);
            int black=0,whiteInk=0;
            for(int yy=0;yy<128;yy++)for(int xx=0;xx<192;xx++){
                int pixel=textA.getPixel(xx,yy);
                require(pixel==textB.getPixel(xx,yy),"ASCII deterministic PNG");
                require(Color.alpha(pixel)==255,"ASCII image is opaque PNG");
                if(Color.red(pixel)<8)black++;
                if(Color.red(pixel)>240)whiteInk++;
                require(Math.abs(Color.red(pixel)-Color.blue(pixel))<=2,
                    "Monochrome ASCII glyphs");
            }
            require(black>9000&&whiteInk>300,"ASCII must draw actual glyphs on black paper");
            float[] denser=EditState.ASCII_DEFAULTS.clone(); denser[0]=.95f;
            Bitmap denseText=GpuExporter.render(getTargetContext(),block,
                new EditState(new float[9],new float[3],new float[8],
                    EditState.DITHER_DEFAULTS,EditState.ART_DEFAULTS,
                    denser,true,false,false,false,false));
            int textDifference=0;
            for(int yy=0;yy<128;yy++)for(int xx=0;xx<192;xx++)
                if(textA.getPixel(xx,yy)!=denseText.getPixel(xx,yy))textDifference++;
            require(textDifference>800,"ASCII density updates glyph geometry");
            Bitmap beforeAscii=GpuExporter.render(getTargetContext(),block,
                new EditState(new float[9],new float[3],new float[8],
                    EditState.DITHER_DEFAULTS,EditState.ART_DEFAULTS,
                    denser,true,false,false,false,true));
            for(int yy=0;yy<128;yy+=11)for(int xx=0;xx<192;xx+=11)
                closeColor(beforeAscii.getPixel(xx,yy),block.getPixel(xx,yy),
                    "Original bypasses ASCII");
            textA.recycle();textB.recycle();denseText.recycle();beforeAscii.recycle();
            block.recycle();
            luminous.recycle(); plainFx.recycle(); effectsImage.recycle();
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
            // Direct slider events must immediately reach the matching GPU uniform.
            require(sliders.length == 50, "All 50 controls must be present");
            runOnMainSync(() -> {
                sliders[12].setProgress(150); // Fade
                sliders[13].setProgress(110); // Tom de pele
                sliders[10].setProgress(120); // Glow outside Dither
                sliders[18].setProgress(160); // Nitidez
            });
            EditState activeEffects = (EditState) field(surface, "state");
            require(activeEffects.fxA[0] > .7f && activeEffects.fxA[1] > .5f,
                    "Live Fade and skin-tone uniforms");
            require(activeEffects.fxB[2] > .75f && activeEffects.style[1] > .55f,
                    "Live Sharpen and independent Glow uniforms");
            runOnMainSync(() -> {
                sliders[12].setProgress(0); sliders[13].setProgress(0);
                sliders[10].setProgress(0); sliders[18].setProgress(0);
            });
            runOnMainSync(() -> {
                sliders[19].setProgress(130);
                sliders[20].setProgress(160);sliders[21].setProgress(130);
                sliders[22].setProgress(40);sliders[23].setProgress(170);
                sliders[24].setProgress(90);sliders[25].setProgress(140);
            });
            EditState advanced=(EditState)field(surface,"state");
            require(advanced.fxB[3]>.60f,"Rotational blur uniform");
            require(advanced.ditherA[0]>.79f&&advanced.ditherA[3]>.84f,
                "Dither depth and scale uniforms");
            require(advanced.ditherB[1]>.69f,"Dither wave uniform");
            runOnMainSync(()->{
                sliders[19].setProgress(0);
                for(int i=20;i<26;i++)sliders[i].setProgress(100);
            });
            runOnMainSync(()->{
                sliders[26].setProgress(175); sliders[27].setProgress(32);
                sliders[28].setProgress(180);sliders[29].setProgress(90);
                sliders[30].setProgress(120);sliders[31].setProgress(70);
                sliders[32].setProgress(160);sliders[33].setProgress(80);
                sliders[34].setProgress(170);sliders[35].setProgress(142);
                sliders[36].setProgress(110);sliders[37].setProgress(140);
                sliders[38].setProgress(184);sliders[39].setProgress(100);
                sliders[40].setProgress(150);sliders[41].setProgress(130);
                sliders[42].setProgress(88);sliders[43].setProgress(200);
            });
            EditState analog=(EditState)field(surface,"state");
            require(analog.ringsA[0]>.8f&&analog.ringsA[2]>.85f,
                "Ring amount and thickness update immediately");
            require(analog.crtA[0]>.75f&&analog.crtA[3]>.7f,
                "CRT strength and curvature update immediately");
            require(analog.glitchA[0]>.9f&&analog.glitchB[1]>.99f,
                "Glitch bands and grayscale update immediately");
            runOnMainSync(()->{
                for(int i=26;i<44;i++)sliders[i].setProgress(
                    Math.round(EditState.ART_DEFAULTS[i-26]*200));
            });
            stage("ASCII UI live state");
            Switch asciiToggle=(Switch)field(activity,"asciiSwitch");
            Switch asciiColors=(Switch)field(activity,"asciiColoredSwitch");
            Switch asciiSymbols=(Switch)field(activity,"asciiSymbolsSwitch");
            runOnMainSync(()->{
                sliders[44].setProgress(182);
                sliders[46].setProgress(138);
                asciiToggle.setChecked(true);
                asciiColors.setChecked(true);
                asciiSymbols.setChecked(true);
            });
            EditState asciiUi=(EditState)field(surface,"state");
            require(asciiUi.asciiEnabled&&asciiUi.asciiColored&&asciiUi.asciiSymbols,
                "ASCII options reach the live shader");
            require(asciiUi.asciiA[0]>.90f&&asciiUi.asciiA[2]>.68f,
                "ASCII density and brightness sliders update immediately");
            runOnMainSync(()->{
                asciiToggle.setChecked(false);
                asciiColors.setChecked(false); asciiSymbols.setChecked(false);
                for(int i=44;i<50;i++)
                    sliders[i].setProgress(Math.round(EditState.ASCII_DEFAULTS[i-44]*200));
            });
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
            // The UI uses an explicit export size, preserving the source bitmap.
            Field widthField=MainActivity.class.getDeclaredField("outputWidth");
            Field heightField=MainActivity.class.getDeclaredField("outputHeight");
            widthField.setAccessible(true);heightField.setAccessible(true);
            runOnMainSync(()->{
                try{widthField.setInt(activity,640);heightField.setInt(activity,480);}
                catch(Exception e){throw new RuntimeException(e);}
            });
            stage("PNG export");
            File pngFile = new File(getTargetContext().getFilesDir(), "export.png");
            Method export = MainActivity.class.getDeclaredMethod("export", Uri.class); export.setAccessible(true);
            runOnMainSync(() -> { try { export.invoke(activity, Uri.fromFile(pngFile)); } catch (Exception e) { throw new RuntimeException(e); } });
            deadline = SystemClock.uptimeMillis() + 15000;
            while ((Boolean)field(activity, "exporting") && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(20);
            require(pngFile.length() > 1000, "Image export file");
            Bitmap saved = BitmapFactory.decodeFile(pngFile.getPath());
            require(saved != null && saved.getWidth() == 640 && saved.getHeight() == 480, "PNG dimensions");
            require(Color.blue(saved.getPixel(100,100)) > Color.blue(saved.getPixel(100,380)), "PNG strips stay upright"); saved.recycle();
            long framesBeforeExportUpdate = surface.frameCount;
            runOnMainSync(() -> sliders[0].setProgress(126)); SystemClock.sleep(150);
            require(surface.frameCount > framesBeforeExportUpdate, "Preview alive after PNG export");
            stage("automatic public save");
            // Saving must not open the Android file manager. The resulting image
            // must be a public MediaStore item under Pictures/Noise!, not cache.
            Method automatic=MainActivity.class.getDeclaredMethod("chooseOutput");
            automatic.setAccessible(true);
            runOnMainSync(()->{
                try{automatic.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}
            });
            deadline=SystemClock.uptimeMillis()+18000;
            while((Boolean)field(activity,"exporting")&&SystemClock.uptimeMillis()<deadline)
                SystemClock.sleep(30);
            require(!(Boolean)field(activity,"exporting"),"Public export finishes");
            boolean inPublicAlbum=false;
            try(Cursor records=getTargetContext().getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.RELATIVE_PATH},
                MediaStore.Images.Media.RELATIVE_PATH+"=?",
                new String[]{"Pictures/Noise!/"},null)){
                while(records!=null&&records.moveToNext()){
                    inPublicAlbum=true;
                    Uri publicUri=android.content.ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,records.getLong(0));
                    try(java.io.InputStream read=getTargetContext().getContentResolver().openInputStream(publicUri)){
                        Bitmap exported=BitmapFactory.decodeStream(read);
                        require(exported!=null&&exported.getWidth()==640
                            &&exported.getHeight()==480,"Gallery PNG shape");
                        exported.recycle();
                    }
                    getTargetContext().getContentResolver().delete(publicUri,null,null);
                    break;
                }
            }
            require(inPublicAlbum,"Export stored in uninstall-proof public Pictures/Noise! folder");
            stage("touch crop");
            Method cropMethod=MainActivity.class.getDeclaredMethod("showResizeDialog");
            cropMethod.setAccessible(true);
            runOnMainSync(()->{
                try{cropMethod.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}
            });
            waitForIdleSync();
            CropEditor crop=(CropEditor)field(activity,"cropEditor");
            require(crop!=null,"In-app crop must open (not numeric dialog)");
            Rect selected=crop.currentRect();
            require(selected.width()==900&&selected.height()==900,"Initial crop shows full image");
            // Exercise the actual gesture handler by moving the upper-left handle.
            ViewGroup container=(ViewGroup)crop.getChildAt(0);
            View gesture=container.getChildAt(2);
            float scale=Math.min((gesture.getWidth()-20f)/900f,
                (gesture.getHeight()-40f)/900f);
            float left=(gesture.getWidth()-900*scale)*.5f;
            float top=(gesture.getHeight()-900*scale)*.5f;
            float down=android.os.SystemClock.uptimeMillis();
            final float sx=left+1,sy=top+1;
            runOnMainSync(()->{
                gesture.dispatchTouchEvent(android.view.MotionEvent.obtain(
                    (long)down,(long)down,android.view.MotionEvent.ACTION_DOWN,sx,sy,0));
                gesture.dispatchTouchEvent(android.view.MotionEvent.obtain(
                    (long)down,(long)down+80,android.view.MotionEvent.ACTION_MOVE,sx+90,sy+90,0));
                gesture.dispatchTouchEvent(android.view.MotionEvent.obtain(
                    (long)down,(long)down+100,android.view.MotionEvent.ACTION_UP,sx+90,sy+90,0));
            });
            Rect moved=crop.currentRect();
            require(moved.width()<selected.width()&&moved.height()<selected.height(),
                "Crop corners should change crop area by touch");
            runOnMainSync(activity::onBackPressed);
            require(field(activity,"cropEditor")==null,"Back closes crop overlay");
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
