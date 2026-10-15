package com.discnet.noise;

import android.app.*;
import android.content.Intent;
import android.graphics.*;
import android.os.*;
import android.widget.*;
import android.view.*;
import java.io.*;
import java.util.ArrayList;
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
            require(activity.getPreferences(0).getBoolean("gallery_permission_first_launch_asked",false),
                "First app launch records the initial permission request");
            HomeScreen home=(HomeScreen)field(activity,"homeScreen");
            require(home!=null,"Noise home screen exists");
            require(home.getChildAt(0).getClass().getSimpleName().equals("StockAtmosphere"),
                "Home renders ambient blurred stock-art behind its content");
            saveScreenshot("noise-home.png");
            View atmosphere=home.getChildAt(0);
            if(android.animation.ValueAnimator.areAnimatorsEnabled()){
                float previous=(Float)field(atmosphere,"phase");
                SystemClock.sleep(115);
                float current=(Float)field(atmosphere,"phase");
                require(current!=previous,"Home gradient must advance animation frames");
            }
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
            stage("settings: opt-in panel resize and independent custom page");
            java.lang.reflect.Method showSettings=MainActivity.class.getDeclaredMethod("showSettings");
            showSettings.setAccessible(true);
            runOnMainSync(()->{
                try{showSettings.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}
            });
            SettingsScreen settings=(SettingsScreen)field(activity,"settingsScreen");
            require(settings!=null,"Settings accessible from the app");
            TextView settingToggle=(TextView)field(settings,"toggle");
            boolean initial=SettingsScreen.resizePanelEnabled(activity);
            runOnMainSync(()->settingToggle.performClick());
            require(SettingsScreen.resizePanelEnabled(activity)!=initial,
                "Preference toggles and persists to device preferences");
            runOnMainSync(()->settingToggle.performClick());
            require(SettingsScreen.resizePanelEnabled(activity)==initial,
                "Settings can be restored");
            runOnMainSync(activity::onBackPressed);
            require(field(activity,"settingsScreen")==null,"Settings closes with Android Back");
            stage("Whites slider GPU response");
            Bitmap whitesSource=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);
            whitesSource.eraseColor(0xffb8b8b8);
            for(int y=4;y<8;y++)for(int x=0;x<8;x++)whitesSource.setPixel(x,y,0xff282828);
            EditorDocument whitesDefault=new EditorDocument();
            Bitmap whitesNeutral=GpuExporter.render(getTargetContext(),whitesSource,whitesDefault.gpu(false));
            EditorDocument whitesPlus=whitesDefault.copy();whitesPlus.values[5]=1f;
            EditorDocument whitesMinus=whitesDefault.copy();whitesMinus.values[5]=-1f;
            Bitmap whitesBright=GpuExporter.render(getTargetContext(),whitesSource,whitesPlus.gpu(false));
            Bitmap whitesDark=GpuExporter.render(getTargetContext(),whitesSource,whitesMinus.gpu(false));
            int neutralUpper=Color.red(whitesNeutral.getPixel(2,2));
            require(Color.red(whitesBright.getPixel(2,2))>neutralUpper+10,
                "Whites +100% visibly increases near-white luminosity");
            require(Color.red(whitesDark.getPixel(2,2))<neutralUpper-10,
                "Whites -100% visibly reduces near-white luminosity");
            require(Math.abs(Color.red(whitesBright.getPixel(2,6))-Color.red(whitesNeutral.getPixel(2,6)))<=3,
                "Whites do not wash out dark shadows");
            Bitmap whitesOriginal=GpuExporter.render(getTargetContext(),whitesSource,whitesPlus.gpu(true));
            closeColor(whitesOriginal.getPixel(2,2),whitesSource.getPixel(2,2),
                "Original comparison bypasses Whites");
            whitesOriginal.recycle();whitesNeutral.recycle();whitesBright.recycle();whitesDark.recycle();whitesSource.recycle();

            // Low-contrast and colored photography must respond to Whites,
            // rather than only very bright, near-white image pixels.
            stage("Whites and Glow on dim and colored images");
            Bitmap lowKey=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);
            lowKey.eraseColor(0xff35323e);
            for(int y=10;y<54;y++)for(int x=12;x<52;x++)
                lowKey.setPixel(x,y,0xff69584e);
            EditorDocument lowNeutral=new EditorDocument();
            EditorDocument midWhites=lowNeutral.copy();
            midWhites.values[5]=1f;
            Bitmap baseline=GpuExporter.render(getTargetContext(),lowKey,lowNeutral.gpu(false));
            Bitmap midBright=GpuExporter.render(getTargetContext(),lowKey,midWhites.gpu(false));
            int sumBright=0, sumNeutral=0;
            for(int y=16;y<48;y+=4)for(int x=16;x<48;x+=4){
                sumBright+=Color.red(midBright.getPixel(x,y))
                    +Color.green(midBright.getPixel(x,y))+Color.blue(midBright.getPixel(x,y));
                sumNeutral+=Color.red(baseline.getPixel(x,y))
                    +Color.green(baseline.getPixel(x,y))+Color.blue(baseline.getPixel(x,y));
            }
            require(sumBright>sumNeutral+320,
                "Whites +100% must affect medium-toned colored images");
            EditorDocument lowGlow=lowNeutral.copy();
            lowGlow.values[10]=1.5f;
            lowGlow.values[57]=.95f; // old hard gate hid Glow at this threshold
            lowGlow.values[58]=.65f;
            Bitmap glowing=GpuExporter.render(getTargetContext(),lowKey,lowGlow.gpu(false));
            int lumGlow=Color.red(glowing.getPixel(32,32))
                +Color.green(glowing.getPixel(32,32))+Color.blue(glowing.getPixel(32,32));
            int lumBase=Color.red(baseline.getPixel(32,32))
                +Color.green(baseline.getPixel(32,32))+Color.blue(baseline.getPixel(32,32));
            require(lumGlow>lumBase+15,
                "Glow needs visible energy on a dark low-contrast colored image");
            // Threshold 100% must remain numerically well-defined.
            lowGlow.values[57]=1f;
            Bitmap maxThreshold=GpuExporter.render(getTargetContext(),lowKey,lowGlow.gpu(false));
            require(Color.red(maxThreshold.getPixel(32,32))>Color.red(baseline.getPixel(32,32)),
                "Glow threshold 100% must not disappear or become undefined");
            Bitmap bypassGlow=GpuExporter.render(getTargetContext(),lowKey,lowGlow.gpu(true));
            closeColor(bypassGlow.getPixel(32,32),lowKey.getPixel(32,32),
                "Original compare bypasses Glow");
            baseline.recycle();midBright.recycle();glowing.recycle();
            maxThreshold.recycle();bypassGlow.recycle();lowKey.recycle();

            stage("GPU three wheels, color curves, blend, HDR and real denoise");
            Bitmap colorPlate=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);
            for(int y=0;y<96;y++)for(int x=0;x<96;x++){
                int red=35+x*2,green=25+y*2,blue=40+(x+y)/2;
                colorPlate.setPixel(x,y,Color.rgb(Math.min(255,red),Math.min(255,green),Math.min(255,blue)));
            }
            EditorDocument colorDefault=new EditorDocument();
            Bitmap colorBase=GpuExporter.render(getTargetContext(),colorPlate,colorDefault.gpu(false));
            EditorDocument colored=colorDefault.copy();
            colored.colorWheels[0]=.9f;colored.colorWheels[3]=-.8f;
            colored.colorCurves[0]=.8f;colored.colorCurves[7]=-.9f;
            colored.colorCurves[15]=.7f;
            Bitmap colorNew=GpuExporter.render(getTargetContext(),colorPlate,colored.gpu(false));
            int changedColor=0;
            for(int y=8;y<88;y+=3)for(int x=8;x<88;x+=3)
                if(colorBase.getPixel(x,y)!=colorNew.getPixel(x,y))changedColor++;
            require(changedColor>300,"Three color wheels and Hue/Saturation curves change GPU pixels");
            EditorDocument colorRoundtrip=EditorDocument.fromJson(colored.toJson());
            require(colored.same(colorRoundtrip),"Curve and wheel edits survive project JSON round trip");
            EditorDocument blendTest=colored.copy();blendTest.blendMode=1;
            Bitmap multiply=GpuExporter.render(getTargetContext(),colorPlate,blendTest.gpu(false));
            require(multiply.getPixel(40,40)!=colorNew.getPixel(40,40),
                "Multiply is an actual graded image blend mode");
            blendTest.blendMode=2;
            Bitmap screen=GpuExporter.render(getTargetContext(),colorPlate,blendTest.gpu(false));
            require(screen.getPixel(40,40)!=multiply.getPixel(40,40),
                "Screen differs from Multiply");
            blendTest.values[64]=0f;
            Bitmap blendOff=GpuExporter.render(getTargetContext(),colorPlate,blendTest.gpu(false));
            closeColor(blendOff.getPixel(40,40),colorPlate.getPixel(40,40),
                "Zero grading opacity preserves the original pixels");
            EditorDocument hdrDoc=colorDefault.copy();hdrDoc.values[60]=.9f;hdrDoc.values[61]=.8f;
            Bitmap hdrOutput=GpuExporter.render(getTargetContext(),colorPlate,hdrDoc.gpu(false));
            require(hdrOutput.getPixel(40,40)!=colorBase.getPixel(40,40),
                "HDR range and microcontrast must affect actual pixels");
            Bitmap rawNoise=Bitmap.createBitmap(48,48,Bitmap.Config.ARGB_8888);
            for(int y=0;y<48;y++)for(int x=0;x<48;x++){
                int v=(((x*17+y*23)%7)<3)?153:103;
                rawNoise.setPixel(x,y,Color.rgb(v,v,v));
            }
            EditorDocument noiseDoc=new EditorDocument();
            noiseDoc.values[62]=1f;noiseDoc.values[63]=.3f;
            Bitmap filtered=GpuExporter.render(getTargetContext(),rawNoise,noiseDoc.gpu(false));
            Bitmap noisy=GpuExporter.render(getTargetContext(),rawNoise,colorDefault.gpu(false));
            int noiseChanged=0;
            for(int y=4;y<44;y++)for(int x=4;x<44;x++)
                if(filtered.getPixel(x,y)!=noisy.getPixel(x,y))noiseChanged++;
            require(noiseChanged>800,"Reduction of noise must visibly change noisy pixels");
            Bitmap originalGrade=GpuExporter.render(getTargetContext(),colorPlate,colored.gpu(true));
            closeColor(originalGrade.getPixel(40,40),colorPlate.getPixel(40,40),
                "Original comparison bypasses all color grades");
            for(Bitmap tmp:new Bitmap[]{colorBase,colorNew,multiply,screen,blendOff,hdrOutput,
                filtered,noisy,originalGrade,colorPlate,rawNoise})tmp.recycle();

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
            // Eighth filter now enhances outlines without rotating/blurring pixels.
            float[] rotation = new float[8];rotation[7]=0.93f;
            Bitmap rotatedEdges=GpuExporter.render(getTargetContext(),effectsImage,
                new EditState(new float[9],new float[3],rotation,false,false));
            int outlinePixels=0;
            for(int yy=0;yy<96;yy++)for(int xx=0;xx<128;xx++)
                if(rotatedEdges.getPixel(xx,yy)!=plainFx.getPixel(xx,yy))outlinePixels++;
            require(outlinePixels>50,"Edge enhancement changes outlines without rotating the image");
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
            stage("200 percent glow and no distant ghosting");
            Bitmap hardEdge=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);
            hardEdge.eraseColor(0xff000000);
            Canvas hardCanvas=new Canvas(hardEdge);
            Paint middle=new Paint(); middle.setColor(0xff888888);
            hardCanvas.drawRect(30,30,65,65,middle);
            float[] normalGlow={0f,1f,0f}, doubledGlow={0f,2f,0f};
            EditState doubledState=new EditState(new float[9],doubledGlow,false,false);
            require(doubledState.style[1]>1.99f,"GPU state retains 200% glow");
            Bitmap glow100=GpuExporter.render(getTargetContext(),hardEdge,
                new EditState(new float[9],normalGlow,false,false));
            Bitmap glow200=GpuExporter.render(getTargetContext(),hardEdge,doubledState);
            require(Color.red(glow200.getPixel(48,48)) > Color.red(glow100.getPixel(48,48)),
                "200% glow must brighten original pixels beyond 100%");
            closeColor(glow200.getPixel(12,48),0xff000000,
                "No motion-like light ghosting far from high contrast edge");
            glow100.recycle(); glow200.recycle();hardEdge.recycle();
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
            int black=0,visibleInk=0,whiteInk=0;
            for(int yy=0;yy<128;yy++)for(int xx=0;xx<192;xx++){
                int pixel=textA.getPixel(xx,yy);
                require(pixel==textB.getPixel(xx,yy),"ASCII deterministic PNG");
                require(Color.alpha(pixel)==255,"ASCII image is opaque PNG");
                if(Color.red(pixel)<8)black++;
                if(Color.red(pixel)>64)visibleInk++;
                if(Color.red(pixel)>240)whiteInk++;
                require(Math.abs(Color.red(pixel)-Color.blue(pixel))<=2,
                    "Monochrome ASCII glyphs");
            }
            stage("ASCII pixel coverage: black="+black+" visible="+visibleInk+" white="+whiteInk);
            // GL_LINEAR reduces peak glyph intensity on tiny 192x128 test images;
            // validate glyph visibility, not a fixed near-white pixel count.
            require(black>9000&&visibleInk>150,
                "ASCII must draw characters over black paper: black="+black+
                " visible="+visibleInk+" white="+whiteInk);
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

            stage("ASCII Dither palette GPU");
            Bitmap palettePhoto=Bitmap.createBitmap(288,144,Bitmap.Config.ARGB_8888);
            palettePhoto.eraseColor(Color.BLACK);
            Canvas paletteCanvas=new Canvas(palettePhoto);
            Paint palettePaint=new Paint();
            int[] paletteColors={0xff347fff,0xffff7840,0xffffb932};
            for(int region=0;region<3;region++){
                palettePaint.setColor(paletteColors[region]);
                paletteCanvas.drawRect(region*96+8,12,region*96+88,132,palettePaint);
            }
            EditState paletteState=new EditState(new float[9],new float[3],new float[8],
                EditState.DITHER_DEFAULTS,EditState.ART_DEFAULTS,
                EditState.ASCII_DEFAULTS,true,false,true,false,false,false);
            Bitmap paletteText=GpuExporter.render(getTargetContext(),palettePhoto,paletteState);
            Bitmap photoText=GpuExporter.render(getTargetContext(),palettePhoto,
                new EditState(new float[9],new float[3],new float[8],
                    EditState.DITHER_DEFAULTS,EditState.ART_DEFAULTS,
                    EditState.ASCII_DEFAULTS,true,true,false,false,false));
            int blueGlyphs=0,redGlyphs=0,orangeGlyphs=0,paletteChanges=0,blackPaper=0;
            for(int yy=0;yy<144;yy++)for(int xx=0;xx<288;xx++){
                int pixel=paletteText.getPixel(xx,yy);
                int r=Color.red(pixel),g=Color.green(pixel),b=Color.blue(pixel);
                require(Color.alpha(pixel)==255,"Dither ASCII remains an opaque image");
                if(r+g+b<12)blackPaper++;
                if(pixel!=photoText.getPixel(xx,yy))paletteChanges++;
                if(yy<20||yy>124)continue;
                if(xx>16&&xx<80&&b>r+35&&b>g+20)blueGlyphs++;
                if(xx>112&&xx<176&&r>g+55&&r>b+55)redGlyphs++;
                if(xx>208&&xx<272&&r>g+20&&g>b+20)orangeGlyphs++;
            }
            require(blueGlyphs>100&&redGlyphs>100&&orangeGlyphs>100,
                "ASCII needs blue, red and orange glyphs: "+blueGlyphs+"/"+redGlyphs+"/"+orangeGlyphs);
            require(blackPaper>8000,"Dither palette preserves ASCII black paper");
            require(paletteChanges>500,"Dither palette differs from original photo colors");
            for(boolean enabled:new boolean[]{false,true}){
                Bitmap bypass=GpuExporter.render(getTargetContext(),palettePhoto,
                    new EditState(new float[9],new float[3],new float[8],
                        EditState.DITHER_DEFAULTS,EditState.ART_DEFAULTS,
                        EditState.ASCII_DEFAULTS,enabled,false,true,false,enabled,enabled));
                for(int yy=0;yy<144;yy+=11)for(int xx=0;xx<288;xx+=11)
                    closeColor(bypass.getPixel(xx,yy),palettePhoto.getPixel(xx,yy),
                        enabled?"Original bypasses ASCII Dither colors":"Disabled ASCII ignores palette");
                bypass.recycle();
            }
            paletteText.recycle();photoText.recycle();palettePhoto.recycle();
            luminous.recycle(); plainFx.recycle(); effectsImage.recycle();
            neutral.recycle(); inverted.recycle(); source.recycle();

            stage("rich film stocks and thirteen analog camera looks");
            ArrayList<ProjectStore.Preset> builtinLooks=ProjectStore.builtins();
            require(builtinLooks.size()>=18,"13 analog camera presets added without removing old presets");
            require(builtinLooks.get(3).id.equals("builtin-neon"),
                "Prior preset indices preserved for existing Neon tool");
            java.util.HashSet<String> analogIds=new java.util.HashSet<>();
            for(ProjectStore.Preset look:builtinLooks){
                if(!look.id.startsWith("builtin-camera-"))continue;
                require(analogIds.add(look.id),"Analog camera preset names/IDs must be unique");
                require(look.document!=null,"Every analog look has an editable document");
                require(look.document.same(EditorDocument.fromJson(look.document.toJson())),
                    "Analog look survives JSON serialization");
            }
            require(analogIds.size()==13,"Thirteen unique analog styles available");
            ProjectStore.Preset copper=null;
            for(ProjectStore.Preset look:builtinLooks)
                if(look.id.equals("builtin-camera-copper-rich"))copper=look;
            require(copper!=null&&copper.document.values[65]==0f
                &&copper.document.values[66]>.90f
                &&copper.document.values[67]>.70f,
                "Rich copper film keeps warm response and halation, WITHOUT grain");
            for(ProjectStore.Preset camera:builtinLooks){
                if(!camera.id.startsWith("builtin-camera-"))continue;
                for(int noiseIndex:new int[]{7,14,54,65})
                    require(camera.document.values[noiseIndex]==0f,
                        camera.name+" must not generate any noise, grain or dust");
            }
            require(ProjectVault.validId(ProjectStore.newId())&&!ProjectVault.validId("../bad"),
                "Shared vault rejects invalid project document IDs");
            stage("document transactions and persistence");
            EditorDocument doc=new EditorDocument();EditHistory history=new EditHistory();history.begin(doc);
            for(int i=0;i<50;i++)doc.values[2]=i/100f;history.commit(doc);
            require(history.undoCount()==1,"A continuous drag creates one undo transaction");
            doc=history.undo(doc);require(doc.values[2]==0&&history.canRedo(),"Undo restores neutral and supports redo");
            doc=history.redo(doc);require(Math.abs(doc.values[2]-.49f)<.001,"Redo restores final gesture value");
            history.begin(doc);doc.values[50]=.2f;history.commit(doc);doc=history.undo(doc);history.begin(doc);doc.values[51]=.3f;history.commit(doc);require(!history.canRedo(),"New edit invalidates redo");
            doc.left=.12f;doc.top=.2f;doc.right=.9f;doc.rotation=1;doc.flipX=true;doc.disabled[10]=true;doc.values[10]=1.8f;doc.grainColored=true;
            ProjectStore storage=new ProjectStore(getTargetContext());String project=ProjectStore.newId();
            Bitmap fixture=scene();File input=new File(getTargetContext().getFilesDir(),"fixture.png");try(FileOutputStream out=new FileOutputStream(input)){fixture.compress(Bitmap.CompressFormat.PNG,100,out);}fixture.recycle();
            File preserved=storage.importOriginal(Uri.fromFile(input),project);long originalBytes=preserved.length();storage.save(project,"Teste",doc,history.toJson());
            ProjectStore.Project loaded=storage.read(project);require(doc.same(loaded.document),"Recipe round trip includes disabled controls, crop, rotation and grain");require(preserved.length()==originalBytes,"Original bytes remain intact");
            ProjectStore.Preset preset=new ProjectStore.Preset(ProjectStore.newId(),"Teste de preset",doc.copy());storage.savePreset(preset);preset.name="Renomeado";preset.favorite=true;storage.savePreset(preset);boolean found=false;for(ProjectStore.Preset pp:storage.presets())if(pp.id.equals(preset.id)){found=true;require(pp.favorite&&pp.name.equals("Renomeado")&&pp.document.same(doc),"Preset rename/favorite/parameters persist");}require(found,"Custom preset exists");storage.deletePreset(preset);
            EditorDocument paste=new EditorDocument();paste.left=.3f;paste.copyLook(doc);require(paste.left==.3f&&paste.values[10]==1.8f,"Copy/preset preserves target image geometry");
            stage("every control changes real pixels");
            Bitmap controlsSource=scene();
            Bitmap controlsFixture=Bitmap.createScaledBitmap(controlsSource,128,96,true);
            controlsSource.recycle();
            stage("film stock changes real pixels and compares with bypass");
            EditorDocument richFilm=copper.document.copy();
            Bitmap analog=GpuExporter.render(getTargetContext(),controlsFixture,richFilm.gpu(false));
            Bitmap clean=GpuExporter.render(getTargetContext(),controlsFixture,new EditorDocument().gpu(false));
            Bitmap bypassFilm=GpuExporter.render(getTargetContext(),controlsFixture,richFilm.gpu(true));
            int analogChanged=0;
            for(int y=6;y<90;y+=2)for(int x=6;x<122;x+=2){
                if(analog.getPixel(x,y)!=clean.getPixel(x,y))analogChanged++;
                closeColor(bypassFilm.getPixel(x,y),controlsFixture.getPixel(x,y),
                    "Original bypasses film emulsion");
            }
            require(analogChanged>1200,"Film emulsion should be visibly different across image pixels");
            analog.recycle();clean.recycle();bypassFilm.recycle();
            for(int id=0;id<AdjustmentSpec.COUNT;id++){
                EditorDocument lo=new EditorDocument();
                if(id>=20&&id<26)lo.values[9]=1;
                if(id>=26&&id<44)lo.values[26+((id-26)/6)*6]=1;
                if(id>=44&&id<50)lo.asciiEnabled=true;
                if(id==55||id==56)lo.values[54]=1;
                if(id==57||id==58)lo.values[10]=1;
                if(id==63)lo.values[62]=1f; // edge preservation needs active denoise
                if(id==64)lo.values[60]=.8f; // blend opacity needs an altered grade
                EditorDocument hi=lo.copy();// Hue is periodic: -180° and +180° are visually identical.
                lo.values[id]=id==8?-.5f:AdjustmentSpec.min(id);
                hi.values[id]=id==8?.5f:AdjustmentSpec.max(id);
                Bitmap a=GpuExporter.render(getTargetContext(),controlsFixture,lo.gpu(false));Bitmap b=GpuExporter.render(getTargetContext(),controlsFixture,hi.gpu(false));int changed=0;
                for(int y=0;y<96;y++)for(int x=0;x<128;x++)if(a.getPixel(x,y)!=b.getPixel(x,y))changed++;
                require(changed>0,"Control "+id+" "+AdjustmentSpec.NAMES[id]+" changes pixels");a.recycle();b.recycle();
            }
            stage("Bayer, threshold and tonal ASCII");
            EditorDocument ordered=new EditorDocument();ordered.values[9]=1;ordered.ditherMethod=1;ordered.ditherPalette=1;
            Bitmap bayer=GpuExporter.render(getTargetContext(),controlsFixture,ordered.gpu(false));ordered.ditherMethod=2;
            Bitmap threshold=GpuExporter.render(getTargetContext(),controlsFixture,ordered.gpu(false));int delta=0;
            for(int y=0;y<96;y++)for(int x=0;x<128;x++){int pixel=bayer.getPixel(x,y)&0xffffff;require(pixel==0||pixel==0xffffff,"Bayer monochrome is genuinely binary");if(pixel!=(threshold.getPixel(x,y)&0xffffff))delta++;}
            require(delta>50,"Bayer differs from threshold");bayer.recycle();threshold.recycle();
            EditorDocument asc=new EditorDocument();asc.asciiEnabled=true;Bitmap darkAscii=GpuExporter.render(getTargetContext(),controlsFixture,asc.gpu(false));asc.values[2]=.8f;Bitmap brightAscii=GpuExporter.render(getTargetContext(),controlsFixture,asc.gpu(false));delta=0;
            for(int y=0;y<96;y++)for(int x=0;x<128;x++)if(darkAscii.getPixel(x,y)!=brightAscii.getPixel(x,y))delta++;
            require(delta>100,"Exposure must feed ASCII tonal sampling");darkAscii.recycle();brightAscii.recycle();
            stage("V1.28 explicit width/height resize, no upscale and old project compatibility");
            EditorDocument resizedDoc=new EditorDocument();
            resizedDoc.outputWidth=320;resizedDoc.outputHeight=180;
            int[] requested=resizedDoc.outputSize(1280,720,4096);
            require(requested[0]==320&&requested[1]==180,"Custom width and height apply precisely");
            EditorDocument serialized=EditorDocument.fromJson(resizedDoc.toJson());
            require(serialized.outputWidth==320&&serialized.outputHeight==180,
                "Explicit resolution persists after project round trip");
            resizedDoc.outputWidth=3000;resizedDoc.outputHeight=1700;
            requested=resizedDoc.outputSize(1280,720,4096);
            require(requested[0]<=1280&&requested[1]<=720,
                "Upscaling explicitly disallowed, including asymmetric sizes");
            resizedDoc.outputWidth=640;resizedDoc.outputHeight=360;
            requested=resizedDoc.outputSize(1280,720,256);
            require(requested[0]<=256&&requested[1]<=256,"GPU texture limit enforced");
            EditorDocument legacy=new EditorDocument();
            legacy.outputLongEdge=640;
            EditorDocument restoredLegacy=EditorDocument.fromJson(legacy.toJson());
            requested=restoredLegacy.outputSize(1280,720,4096);
            require(requested[0]==640&&requested[1]==360,
                "V1.27 size field remains compatible");
            stage("non-destructive transformed export");
            Bitmap quad=Bitmap.createBitmap(40,20,Bitmap.Config.ARGB_8888);for(int y=0;y<20;y++)for(int x=0;x<40;x++)quad.setPixel(x,y,x<20?Color.RED:Color.BLUE);
            EditorDocument geometry=new EditorDocument();geometry.left=.5f;int[] shape=geometry.outputSize(40,20,4096);Bitmap cropped=GpuExporter.render(getTargetContext(),quad,geometry.gpu(false),shape[0],shape[1]);require(shape[0]==20&&shape[1]==20,"Crop changes output geometry");closeColor(cropped.getPixel(10,10),Color.BLUE,"Crop selects original source pixels");require(quad.getWidth()==40,"Crop never degrades original");cropped.recycle();geometry.left=0;geometry.rotation=1;shape=geometry.outputSize(40,20,4096);require(shape[0]==20&&shape[1]==40,"Rotation swaps geometry");Bitmap turned=GpuExporter.render(getTargetContext(),quad,geometry.gpu(false),shape[0],shape[1]);closeColor(turned.getPixel(10,5),Color.RED,"Clockwise rotation top");closeColor(turned.getPixel(10,35),Color.BLUE,"Clockwise rotation bottom");turned.recycle();quad.recycle();
            stage("UI import, adaptive workspace and live gestures");
            // System SAF folder picker requires user authorization and is
            // verified manually. Disable only its first-import prompt in CI.
            runOnMainSync(()->activity.getPreferences(0).edit()
                .putBoolean("project_vault_setup_asked",true).apply());
            runOnMainSync(()->activity.load(Uri.fromFile(input)));long deadline=SystemClock.uptimeMillis()+20000;
            while((Boolean)field(activity,"loading")&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(30);
            require(field(activity,"original")!=null,"Import decoded source");EditorSurface surface=(EditorSurface)field(activity,"preview");EditorWorkspace workspace=(EditorWorkspace)field(activity,"workspace");EditorPanel panel=(EditorPanel)field(activity,"panel");
            TextView[] tabs=(TextView[])field(activity,"tabs");require(tabs.length==7,"Seven requested categories");
            for(int i=0;i<7;i++){final int n=i;runOnMainSync(()->tabs[n].performClick());
                require(panel.category==i,"Category "+i+" reachable");
                require(findDescendant(panel,android.widget.Switch.class)==null,
                    "No on/off switch is shown for category "+i);
            }
            stage("panel resize is blocked when option disabled");
            runOnMainSync(()->{
                activity.getSharedPreferences(SettingsScreen.PREFS,0).edit()
                    .putBoolean(SettingsScreen.RESIZE_PANEL,false).apply();
                workspace.setSheetResizeEnabled(false);
                panel.panelResizeEnabled(false);
            });
            int panelBefore=workspace.sheetHeight();
            runOnMainSync(()->{workspace.resizeSheet(panelBefore+EditorTheme.dp(activity,120));
                workspace.cycleSheet();});
            require(workspace.sheetHeight()==panelBefore,
                "Resizing panel is impossible while the setting is off");
            runOnMainSync(()->{
                try{showSettings.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}
            });
            SettingsScreen enableSettings=(SettingsScreen)field(activity,"settingsScreen");
            runOnMainSync(()->((TextView)fieldUnchecked(enableSettings,"toggle")).performClick());
            require(workspace.isSheetResizeEnabled(),"Setting enables panel resizing immediately");
            runOnMainSync(()->workspace.resizeSheet(panelBefore+EditorTheme.dp(activity,70)));
            waitForIdleSync();
            require(workspace.sheetHeight()!=panelBefore,"Enabled panel permits resizing");
            runOnMainSync(()->{
                ((TextView)fieldUnchecked(enableSettings,"toggle")).performClick();
                activity.onBackPressed();
            });
            stage("gestures do not steal HSL curves or color-wheel drags");
            runOnMainSync(()->panel.select(1));waitForIdleSync();
            ColorCurveControl curve=(ColorCurveControl)findDescendant(panel,ColorCurveControl.class);
            ColorWheelControl wheel=(ColorWheelControl)findDescendant(panel,ColorWheelControl.class);
            require(curve!=null&&wheel!=null,"All custom color editors are present");
            runOnMainSync(()->ensureVisible(panel.scroll,curve));
            waitForIdleSync();
            android.graphics.Rect colorRect=new android.graphics.Rect();
            require(curve.getGlobalVisibleRect(colorRect)&&colorRect.height()>EditorTheme.dp(activity,32),
                "Curve is fully available for direct editing");
            require(panel.isOverColorEditor(colorRect.centerX(),colorRect.centerY()),
                "Curve hit bounds take priority over orbital swipes");
            int[] hostXY=new int[2];appBackgroundLocation(activity,hostXY);
            FrameLayout rootGesture=(FrameLayout)field(activity,"appBackground");
            float cx=colorRect.centerX()-hostXY[0],cy=colorRect.centerY()-hostXY[1];
            long touchTime=SystemClock.uptimeMillis();
            runOnMainSync(()->{
                rootGesture.dispatchTouchEvent(MotionEvent.obtain(touchTime,touchTime,
                    MotionEvent.ACTION_DOWN,cx,cy,0));
                rootGesture.dispatchTouchEvent(MotionEvent.obtain(touchTime,touchTime+70,
                    MotionEvent.ACTION_MOVE,cx+EditorTheme.dp(activity,100),cy,0));
                rootGesture.dispatchTouchEvent(MotionEvent.obtain(touchTime,touchTime+110,
                    MotionEvent.ACTION_UP,cx+EditorTheme.dp(activity,100),cy,0));
            });
            require(field(activity,"orbital")==null,
                "Dragging inside a color curve never opens an orbital menu");
            runOnMainSync(()->ensureVisible(panel.scroll,wheel));waitForIdleSync();
            require(wheel.getGlobalVisibleRect(colorRect)&&colorRect.height()>EditorTheme.dp(activity,24),
                "Color wheel visible in panel");
            require(panel.isOverColorEditor(colorRect.centerX(),colorRect.centerY()),
                "Color wheel hit bounds take priority over orbital swipes");
            stage("V1.28 resize dialog precision and context panel");
            runOnMainSync(()->tabs[0].performClick());waitForIdleSync();
            saveScreenshot("noise-panel.png");
            require(workspace.sheetState()!=null,"Workspace supports explicit compact/intermediate/expanded states");
            EditorDocument sheetDoc=new EditorDocument();
            final ResizeDialog[] sheet={null};
            Bitmap resizePhoto=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888);
            runOnMainSync(()->{sheet[0]=new ResizeDialog(activity,resizePhoto,
                sheetDoc,900,900,4096,(w,h,j,q,doExport)->{
                    sheetDoc.outputWidth=w;sheetDoc.outputHeight=h;
                });sheet[0].show();});
            waitForIdleSync();
            saveScreenshot("noise-transform.png");
            EditText widthInput=(EditText)field(sheet[0],"width");
            EditText heightInput=(EditText)field(sheet[0],"height");
            runOnMainSync(()->widthInput.setText("450"));
            require("450".contentEquals(heightInput.getText()),
                "Aspect-lock synchronizes opposite dimension without a feedback cycle");
            runOnMainSync(()->sheet[0].dismiss());resizePhoto.recycle();
            stage("project library and preset library use Noise-owned screens");
            runOnMainSync(()->activity.action("Meus projetos"));
            long libraryDeadline=SystemClock.uptimeMillis()+12000;
            ProjectLibraryScreen library=(ProjectLibraryScreen)field(activity,"projectLibrary");
            require(library!=null,"Custom project library overlay opens");
            require(field(library,"vaultButton") instanceof TextView,
                "Shared-folder backup and restore available from project library");
            while(library.projectCount()==0&&SystemClock.uptimeMillis()<libraryDeadline)
                SystemClock.sleep(90);
            require(library.projectCount()>0,"Imported project appears in custom library");
            while(library.previewCount()==0&&SystemClock.uptimeMillis()<libraryDeadline)
                SystemClock.sleep(90);
            require(library.previewCount()>0,"At least one real project image preview decoded");
            saveScreenshot("noise-projects.png");
            runOnMainSync(activity::onBackPressed);
            require(field(activity,"projectLibrary")==null,"Project browser closes without stale touch overlay");
            runOnMainSync(()->activity.action("Presets"));
            long presetDeadline=SystemClock.uptimeMillis()+12000;
            while(field(activity,"presetLibrary")==null&&SystemClock.uptimeMillis()<presetDeadline)
                SystemClock.sleep(90);
            require(field(activity,"presetLibrary") instanceof PresetDialog,
                "Preset library uses custom Noise overlay, not AlertDialog");
            saveScreenshot("noise-presets.png");
            runOnMainSync(activity::onBackPressed);
            stage("orbital tool gesture and functional effect routing");
            runOnMainSync(activity::openEffects);waitForIdleSync();
            OrbitalSelector orbital=(OrbitalSelector)field(activity,"orbital");
            View orbitStage=(View)field(orbital,"orbit");
            require(orbitStage.getHeight()>0&&orbitStage.getWidth()>0,"Orbital selector measured");
            LinearLayout[] hitRows=(LinearLayout[])field(orbitStage,"rows");
            ImageView[] hitIcons=(ImageView[])field(orbitStage,"icons");
            int activeIndex=orbital.selected();
            float ix=hitRows[activeIndex].getX()+hitIcons[activeIndex].getX()
                +hitIcons[activeIndex].getWidth()*.5f;
            float iy=hitRows[activeIndex].getY()+hitIcons[activeIndex].getY()
                +hitIcons[activeIndex].getHeight()*.5f;
            require(orbital.orbitalHitIndex(ix,iy)==activeIndex,
                "Hit-test identifies exact icon center with transformed coordinates");
            require(hitIcons[activeIndex].getBackground().getClass()
                .getSimpleName().equals("IconGlowFrame"),
                "Selected orbital icon has a custom white-glow outline");
            float gestureX=orbitStage.getWidth()*.60f,from=orbitStage.getHeight()*.72f,to=orbitStage.getHeight()*.22f;
            long time=SystemClock.uptimeMillis();
            runOnMainSync(()->{
                orbitStage.dispatchTouchEvent(MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,gestureX,from,0));
                orbitStage.dispatchTouchEvent(MotionEvent.obtain(time,time+110,MotionEvent.ACTION_MOVE,gestureX,(from+to)/2,0));
                orbitStage.dispatchTouchEvent(MotionEvent.obtain(time,time+180,MotionEvent.ACTION_MOVE,gestureX,to,0));
                orbitStage.dispatchTouchEvent(MotionEvent.obtain(time,time+210,MotionEvent.ACTION_UP,gestureX,to,0));
            });
            SystemClock.sleep(800);
            int orbitResult=orbital.selected();
            require(orbitResult>0,"Orbital drag scrolls to a different effect");
            TextView openEffect=(TextView)field(orbital,"continueButton");
            runOnMainSync(openEffect::performClick);
            require(panel.category==3&&panel.effect==orbitResult,"Orbital selection opens real effect controls");
            stage("genuine circular wheels, mirrored side arcs, smooth scroll and fades");
            SystemClock.sleep(520); // allow prior selection overlay to disappear
            FrameLayout gestureHost=(FrameLayout)field(activity,"appBackground");
            float gestureY=gestureHost.getPaddingTop()+workspace.getTop()
                +workspace.stage.getTop()+workspace.stage.getHeight()*.50f;
            float width=gestureHost.getWidth();
            // Slider touch regions remain exclusive, even though the rest
            // of the editor now accepts horizontal menu gestures.
            android.graphics.Rect panelBounds=new android.graphics.Rect();
            panel.getGlobalVisibleRect(panelBounds);
            int[] rootXY=new int[2];gestureHost.getLocationOnScreen(rootXY);
            PrecisionControl[] controls=(PrecisionControl[])field(panel,"rows");
            android.graphics.Rect sliderBounds=new android.graphics.Rect();
            boolean foundSlider=false;
            for(PrecisionControl control:controls){
                if(control!=null&&control.slider.isShown()
                    &&control.slider.getGlobalVisibleRect(sliderBounds)){
                    foundSlider=true;break;
                }
            }
            require(foundSlider,"At least one visible adjustment slider for gesture regression");
            float sliderX=sliderBounds.centerX()-rootXY[0];
            float sliderY=sliderBounds.centerY()-rootXY[1];
            long panelGesture=SystemClock.uptimeMillis();
            runOnMainSync(()->{
                gestureHost.dispatchTouchEvent(MotionEvent.obtain(panelGesture,panelGesture,
                    MotionEvent.ACTION_DOWN,sliderX,sliderY,0));
                gestureHost.dispatchTouchEvent(MotionEvent.obtain(panelGesture,panelGesture+110,
                    MotionEvent.ACTION_MOVE,sliderX-width*.33f,sliderY,0));
                gestureHost.dispatchTouchEvent(MotionEvent.obtain(panelGesture,panelGesture+160,
                    MotionEvent.ACTION_UP,sliderX-width*.43f,sliderY,0));
            });
            require(field(activity,"orbital")==null,
                "Dragging through an adjustable slider NEVER opens a wheel");

            // A horizontal swipe from the middle of the panel must work.
            // New policy: every editor area EXCEPT slider touch bounds.
            float headingY=panelBounds.top-rootXY[1]+EditorTheme.dp(getTargetContext(),21);
            long centerTime=SystemClock.uptimeMillis();
            runOnMainSync(()->{
                gestureHost.dispatchTouchEvent(MotionEvent.obtain(centerTime,centerTime,
                    MotionEvent.ACTION_DOWN,width*.50f,headingY,0));
                gestureHost.dispatchTouchEvent(MotionEvent.obtain(centerTime,centerTime+100,
                    MotionEvent.ACTION_MOVE,width*.74f,headingY,0));
                gestureHost.dispatchTouchEvent(MotionEvent.obtain(centerTime,centerTime+175,
                    MotionEvent.ACTION_UP,width*.88f,headingY,0));
            });
            OrbitalSelector fromPanel=(OrbitalSelector)field(activity,"orbital");
            require(fromPanel!=null&&fromPanel.isEffects(),
                "Horizontal gesture starting outside sliders must open Effects");
            runOnMainSync(activity::onBackPressed);
            SystemClock.sleep(480);

            // A tiny swipe is never sufficient to open a wheel.
            long shortTime=SystemClock.uptimeMillis();
            runOnMainSync(()->{
                gestureHost.dispatchTouchEvent(MotionEvent.obtain(shortTime,shortTime,
                    MotionEvent.ACTION_DOWN,width*.50f,gestureY,0));
                gestureHost.dispatchTouchEvent(MotionEvent.obtain(shortTime,shortTime+90,
                    MotionEvent.ACTION_MOVE,width*.50f+EditorTheme.dp(getTargetContext(),4),gestureY,0));
                gestureHost.dispatchTouchEvent(MotionEvent.obtain(shortTime,shortTime+160,
                    MotionEvent.ACTION_UP,width*.50f+EditorTheme.dp(getTargetContext(),4),gestureY,0));
            });
            require(field(activity,"orbital")==null,"Short drag is not a menu gesture");
            float fromTools=width*.96f,toTools=width*.18f;
            long gt=SystemClock.uptimeMillis();
            runOnMainSync(()->{
                gestureHost.dispatchTouchEvent(MotionEvent.obtain(gt,gt,
                    MotionEvent.ACTION_DOWN,fromTools,gestureY,0));
                gestureHost.dispatchTouchEvent(MotionEvent.obtain(gt,gt+95,
                    MotionEvent.ACTION_MOVE,width*.68f,gestureY,0));
            });
            // Frame pacing must be observable: not frozen, and not instantly
            // attached to the finger position without any damping.
            SystemClock.sleep(85);
            OrbitalSelector eased=(OrbitalSelector)field(activity,"orbital");
            require(eased!=null && !eased.isEffects(),
                "Regular swipe opens tools directly");
            float alpha=eased.motionAlpha();
            require(alpha>.01f && alpha<.96f,
                "Wheel gradually fades in on display frames: "+alpha);
            saveScreenshot("noise-opening.png");
            require(eased.getAlpha()==1f,"Overlay container stays fully opaque while scrim fades");
            require(eased.wholeScreenDim(),
                "Dark layer must cover entire screen, not only the menu side");
            require(eased.scrimOpacity()>0f && eased.scrimOpacity()<.40f,
                "Full-screen dimmer fades in gradually from fully transparent");
            require(eased.featherOpacity()>0f && eased.featherOpacity()<.60f,
                "Side-specific feather gradient fades in smoothly");
            require(eased.motionX()>0&&eased.motionX()<EditorTheme.dp(getTargetContext(),48),
                "Tools menu enters with short drift, not a long jump");
            require(Math.abs(((View)field(eased,"content")).getScaleX()-1f)<.005f,
                "Opening must not scale/pop menu content");
            require(eased.scrimOpacity()>0f && eased.scrimOpacity()<.40f,
                "Dark gradient builds slowly instead of immediately covering image");
            require(eased.motionAlpha()<.85f,
                "Wheel content remains translucent during the swipe");
            runOnMainSync(()->{
                // The finger moves the TARGET, not the displayed position.
                // A critically damped wheel must lag instead of snapping.
                float xBefore=eased.motionX();
                float alphaBefore=eased.motionAlpha();
                float scrimBefore=eased.scrimOpacity();
                float previousTarget=eased.revealTargetX();
                gestureHost.dispatchTouchEvent(MotionEvent.obtain(gt,gt+170,
                    MotionEvent.ACTION_MOVE,width*.30f,gestureY,0));
                require(eased.revealTargetX()<previousTarget-EditorTheme.dp(getTargetContext(),6),
                    "Further edge dragging drives the wheel target toward its open position");
                require(Math.abs(eased.motionX()-xBefore)<.15f
                    &&Math.abs(eased.motionAlpha()-alphaBefore)<.005f
                    &&Math.abs(eased.scrimOpacity()-scrimBefore)<.005f,
                    "Spring must lag behind the finger instead of teleporting");
            });
            SystemClock.sleep(75);
            require(eased.motionX()<EditorTheme.dp(getTargetContext(),155),
                "Wheel should animate toward updated drag target with inertia");
            runOnMainSync(()->gestureHost.dispatchTouchEvent(MotionEvent.obtain(gt,gt+220,
                MotionEvent.ACTION_UP,toTools,gestureY,0)));
            // Immediately after release, final animation must still be running.
            OrbitalSelector animated=(OrbitalSelector)field(activity,"orbital");
            require(animated!=null&&animated.motionAlpha()<.99f,
                "Menu must EASE IN after finger release instead of appearing instantly");
            OrbitalSelector swipeTools=(OrbitalSelector)field(activity,"orbital");
            require(swipeTools!=null && !swipeTools.isEffects(),
                "Non-edge swipe opens tools");
            waitForIdleSync();
            require(swipeTools.dockLeft()>width*.09f,"Tools panel docks on RIGHT");
            require(swipeTools.dockRight()==gestureHost.getWidth(),"Tools panel touches right edge");
            require(swipeTools.orbitalScreenX()>width*.65f,"Tools orbit and active icon are on RIGHT");
            int toolCenter=swipeTools.orbitalSelected();
            int toolNeighbor=toolCenter+2<swipeTools.orbitalCount()?toolCenter+2:toolCenter-2;
            require(swipeTools.orbitalIconScreenX(toolNeighbor)
                    -swipeTools.orbitalScreenX()>EditorTheme.dp(getTargetContext(),16),
                "Right-hand Tools wheel bulges toward RIGHT edge, not backward into image");
            SystemClock.sleep(520);
            float dimBeforeClose=swipeTools.scrimOpacity();
            float menuBeforeClose=swipeTools.motionAlpha();
            require(dimBeforeClose>.55f && menuBeforeClose>.65f,
                "Menu and overlay became visible smoothly before close");
            require(swipeTools.wholeScreenDim() && swipeTools.featherOpacity()>.70f,
                "Entire editor is substantially darkened with separate edge gradient");
            saveScreenshot("noise-open.png");
            runOnMainSync(activity::onBackPressed);
            SystemClock.sleep(90);
            saveScreenshot("noise-closing.png");
            require(swipeTools.scrimOpacity()<dimBeforeClose
                &&swipeTools.scrimOpacity()>0f
                &&swipeTools.featherOpacity()>0f
                &&swipeTools.featherOpacity()<.88f
                &&swipeTools.motionAlpha()<menuBeforeClose
                &&swipeTools.motionAlpha()>0f,
                "Close applies gradual fade-out to menu AND background");
            SystemClock.sleep(570);

            float fromEffects=width*.04f,toEffects=width*.82f;
            long ge=SystemClock.uptimeMillis();
            runOnMainSync(()->{
                gestureHost.dispatchTouchEvent(MotionEvent.obtain(ge,ge,
                    MotionEvent.ACTION_DOWN,fromEffects,gestureY,0));
                gestureHost.dispatchTouchEvent(MotionEvent.obtain(ge,ge+85,
                    MotionEvent.ACTION_MOVE,width*.32f,gestureY,0));
                gestureHost.dispatchTouchEvent(MotionEvent.obtain(ge,ge+150,
                    MotionEvent.ACTION_MOVE,toEffects,gestureY,0));
                gestureHost.dispatchTouchEvent(MotionEvent.obtain(ge,ge+190,
                    MotionEvent.ACTION_UP,toEffects,gestureY,0));
            });
            OrbitalSelector swipeEffects=(OrbitalSelector)field(activity,"orbital");
            require(swipeEffects!=null&&swipeEffects.isEffects(),
                "Left-to-right opens effects DIRECTLY, skipping tools");
            waitForIdleSync();
            require(swipeEffects.dockLeft()==0,"Effects panel docks on LEFT");
            require(swipeEffects.dockRight()<width*.95f,"Effects stays clear of right edge");
            require(swipeEffects.orbitalScreenX()<width*.35f,
                "Effects orbit, active icon and labels occupy LEFT side");
            int effectCenter=swipeEffects.orbitalSelected();
            int effectNeighbor=effectCenter+2<swipeEffects.orbitalCount()?effectCenter+2:effectCenter-2;
            require(swipeEffects.orbitalScreenX()
                    -swipeEffects.orbitalIconScreenX(effectNeighbor)>EditorTheme.dp(getTargetContext(),16),
                "Left-hand Effects wheel bulges toward LEFT edge, not backward into image");
            // Another left swipe while a wheel is open must NOT switch modes.
            long repeat=SystemClock.uptimeMillis();
            float midY=swipeEffects.getHeight()*.55f;
            runOnMainSync(()->{
                swipeEffects.dispatchTouchEvent(MotionEvent.obtain(repeat,repeat,
                    MotionEvent.ACTION_DOWN,fromTools,midY,0));
                swipeEffects.dispatchTouchEvent(MotionEvent.obtain(repeat,repeat+120,
                    MotionEvent.ACTION_MOVE,width*.34f,midY,0));
                swipeEffects.dispatchTouchEvent(MotionEvent.obtain(repeat,repeat+180,
                    MotionEvent.ACTION_UP,toTools,midY,0));
            });
            require(field(activity,"orbital")==swipeEffects && swipeEffects.isEffects(),
                "Repeating the left swipe does not chain menus");
            runOnMainSync(activity::onBackPressed);
            SystemClock.sleep(630);
            runOnMainSync(()->tabs[0].performClick());waitForIdleSync();
            int expanded=workspace.sheetHeight();runOnMainSync(()->workspace.resizeSheet(EditorTheme.dp(getTargetContext(),48)));waitForIdleSync();require(workspace.sheetHeight()<expanded,"Sheet collapses");runOnMainSync(()->workspace.resizeSheet(expanded));
            for(int[] dims:new int[][]{{320,568},{800,360},{800,1100}}){runOnMainSync(()->{workspace.measure(View.MeasureSpec.makeMeasureSpec(dims[0],View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(dims[1],View.MeasureSpec.EXACTLY));workspace.layout(0,0,dims[0],dims[1]);});require(workspace.stage.getMeasuredWidth()>0&&workspace.stage.getMeasuredHeight()>0,"Canvas visible at "+dims[0]+"x"+dims[1]);require(workspace.panel.getBottom()<=dims[1],"Panel inside viewport");require(workspace.categories.getBottom()<=dims[1],"Navigation inside viewport");}
            runOnMainSync(()->workspace.requestLayout());waitForIdleSync();
            EditHistory liveHistory=(EditHistory)field(activity,"history");int oldCount=liveHistory.undoCount();long frames=surface.frameCount;
            runOnMainSync(activity::begin);for(int i=0;i<45;i++){final float n=i/60f;runOnMainSync(()->activity.change(2,n));SystemClock.sleep(16);}runOnMainSync(activity::end);SystemClock.sleep(200);
            long drawn=surface.frameCount-frames;require(drawn>=3,"GPU redraws during movement: "+drawn);require(liveHistory.undoCount()==oldCount+1,"One live gesture, one history operation");
            long start=System.nanoTime();runOnMainSync(()->activity.change(50,.2f));long parameterMs=(System.nanoTime()-start)/1000000;
            require(((EditState)field(surface,"state")).light[0]==.2f,"State immediately reaches preview");
            stage("live frames="+drawn+", parameter dispatch ms="+parameterMs+", last GL submission ms="+(surface.renderNanos/1000000));
            // Regression: changing a slider AND selecting a different
            // category must never disable both edge menus.
            runOnMainSync(()->tabs[1].performClick());
            waitForIdleSync();
            require(panel.category==1,"Category changed after editing a parameter");
            SystemClock.sleep(600);
            android.graphics.Rect previewBounds=new android.graphics.Rect();
            ((FrameLayout)field(activity,"editorStage")).getGlobalVisibleRect(previewBounds);
            gestureHost.getLocationOnScreen(rootXY);
            float liveY=previewBounds.centerY()-rootXY[1];
            long tEdited=SystemClock.uptimeMillis();
            runOnMainSync(()->{
                gestureHost.dispatchTouchEvent(MotionEvent.obtain(tEdited,tEdited,
                    MotionEvent.ACTION_DOWN,width*.04f,liveY,0));
                gestureHost.dispatchTouchEvent(MotionEvent.obtain(tEdited,tEdited+110,
                    MotionEvent.ACTION_MOVE,width*.37f,liveY,0));
                gestureHost.dispatchTouchEvent(MotionEvent.obtain(tEdited,tEdited+185,
                    MotionEvent.ACTION_UP,width*.55f,liveY,0));
            });
            OrbitalSelector afterEdit=(OrbitalSelector)field(activity,"orbital");
            require(afterEdit!=null&&afterEdit.isEffects(),
                "After editing a slider, edge gesture MUST reopen effects wheel");
            runOnMainSync(activity::onBackPressed);
            // Immediately reopening must interrupt an older closing overlay.
            long immediate=SystemClock.uptimeMillis();
            runOnMainSync(()->{
                gestureHost.dispatchTouchEvent(MotionEvent.obtain(immediate,immediate,
                    MotionEvent.ACTION_DOWN,width*.96f,liveY,0));
                gestureHost.dispatchTouchEvent(MotionEvent.obtain(immediate,immediate+90,
                    MotionEvent.ACTION_MOVE,width*.55f,liveY,0));
                gestureHost.dispatchTouchEvent(MotionEvent.obtain(immediate,immediate+160,
                    MotionEvent.ACTION_UP,width*.20f,liveY,0));
            });
            OrbitalSelector immediateTools=(OrbitalSelector)field(activity,"orbital");
            require(immediateTools!=null&&!immediateTools.isEffects(),
                "Another wheel opens immediately even while previous one closes");
            runOnMainSync(activity::onBackPressed);
            SystemClock.sleep(670);
            stage("public PNG and JPEG export");
            EditorDocument outDoc=new EditorDocument();outDoc.outputLongEdge=64;outDoc.asciiEnabled=true;outDoc.asciiDither=true;
            Uri savedUri=ExportManager.save(getTargetContext(),controlsFixture,outDoc,128,96,4096,null);require(savedUri!=null,"MediaStore publishes PNG");
            try(Cursor cursor=getTargetContext().getContentResolver().query(savedUri,new String[]{MediaStore.Images.Media.RELATIVE_PATH,MediaStore.Images.Media.IS_PENDING},null,null,null)){require(cursor!=null&&cursor.moveToFirst()&&cursor.getString(0).equals("Pictures/Noise!/")&&cursor.getInt(1)==0,"Public path and completed transaction");}
            try(InputStream in=getTargetContext().getContentResolver().openInputStream(savedUri)){Bitmap saved=BitmapFactory.decodeStream(in);require(saved.getWidth()==64&&saved.getHeight()==48,"PNG selected resolution/aspect");Bitmap expected=GpuExporter.render(getTargetContext(),controlsFixture,outDoc.gpu(false),64,48);for(int y=0;y<48;y+=5)for(int x=0;x<64;x+=5)closeColor(saved.getPixel(x,y),expected.getPixel(x,y),"Export keeps ASCII settings");saved.recycle();expected.recycle();}getTargetContext().getContentResolver().delete(savedUri,null,null);
            outDoc.asciiEnabled=false;outDoc.jpeg=true;outDoc.jpegQuality=75;File jpg=new File(getTargetContext().getFilesDir(),"export.jpg");ExportManager.save(getTargetContext(),controlsFixture,outDoc,128,96,4096,Uri.fromFile(jpg));Bitmap jpeg=BitmapFactory.decodeFile(jpg.getPath());require(jpeg!=null&&jpeg.getWidth()==64&&jpeg.getHeight()==48,"JPEG encoded at selected dimensions");jpeg.recycle();
            stage("crop touch and cancel");runOnMainSync(()->activity.action("Recortar"));waitForIdleSync();CropEditor crop=(CropEditor)field(activity,"cropEditor");require(crop!=null,"Crop opens");Rect beforeCrop=crop.currentRect();View gesture=(View)field(crop,"canvas");float scale=Math.min((gesture.getWidth()-20f)/900f,(gesture.getHeight()-40f)/900f);float sx=(gesture.getWidth()-900*scale)/2+1,sy=(gesture.getHeight()-900*scale)/2+1;long now=SystemClock.uptimeMillis();runOnMainSync(()->{gesture.dispatchTouchEvent(MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,sx,sy,0));gesture.dispatchTouchEvent(MotionEvent.obtain(now,now+80,MotionEvent.ACTION_MOVE,sx+60,sy+60,0));gesture.dispatchTouchEvent(MotionEvent.obtain(now,now+100,MotionEvent.ACTION_UP,sx+60,sy+60,0));});require(crop.currentRect().width()<beforeCrop.width(),"Corner gesture changes selection");runOnMainSync(activity::onBackPressed);require(activity.document().left==0,"Cancel leaves geometry unchanged");
            stage("invalid file keeps working image");File invalid=new File(getTargetContext().getFilesDir(),"invalid.bin");try(FileOutputStream out=new FileOutputStream(invalid)){out.write(new byte[]{1,2,3});}Bitmap old=(Bitmap)field(activity,"original");runOnMainSync(()->activity.load(Uri.fromFile(invalid)));deadline=SystemClock.uptimeMillis()+10000;while((Boolean)field(activity,"loading")&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(20);require(field(activity,"original")==old,"Invalid import preserves working image");
            stage("activity recreation retains recipe and source");EditorDocument retained=activity.document().copy();ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);runOnMainSync(activity::recreate);MainActivity recreated=(MainActivity)monitor.waitForActivityWithTimeout(15000);removeMonitor(monitor);require(recreated!=null,"Activity recreated");deadline=SystemClock.uptimeMillis()+15000;while((Boolean)field(recreated,"loading")&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(30);require(retained.same(recreated.document()),"Recipe survives recreation");EditorSurface restoredSurface=(EditorSurface)field(recreated,"preview");long previous=restoredSurface.frameCount;runOnMainSync(()->recreated.change(3,.3f));long renderDeadline=SystemClock.uptimeMillis()+10000;
            while(restoredSurface.frameCount<=previous && SystemClock.uptimeMillis()<renderDeadline)SystemClock.sleep(100);
            require(restoredSurface.frameCount>previous,"Renderer alive after EGL recreation");
            stage("screenshots");saveScreenshot("noise-ui.png");
            storage.delete(project);controlsFixture.recycle();
            result.putString("stream","NOISE_SMOKE_OK: legacy pixel regression, 60 real controls, tonal ASCII, Bayer/Threshold, history, projects, presets, geometry, public PNG, JPEG, adaptive layout, invalid import, recreation. Live frames="+drawn+"\n");
            finish(Activity.RESULT_OK,result);
        } catch (Throwable error) {
            StringWriter stack = new StringWriter(); error.printStackTrace(new PrintWriter(stack)); result.putString("stream", "NOISE_SMOKE_FAILED\n" + stack); finish(Activity.RESULT_CANCELED, result);
        }
    }
    private Object fieldUnchecked(Object object,String key){
        try{return field(object,key);}catch(Exception e){throw new RuntimeException(e);}
    }
    private View findDescendant(View root,Class<?> type){
        if(type.isInstance(root))return root;
        if(root instanceof ViewGroup){
            ViewGroup g=(ViewGroup)root;
            for(int i=0;i<g.getChildCount();i++){
                View v=findDescendant(g.getChildAt(i),type);
                if(v!=null)return v;
            }
        }
        return null;
    }
    private void ensureVisible(ScrollView scroller,View child){
        int[] y=new int[2],origin=new int[2];
        child.getLocationOnScreen(y);scroller.getLocationOnScreen(origin);
        int scrollBy=y[1]-origin[1]-EditorTheme.dp(scroller.getContext(),45);
        scroller.scrollBy(0,scrollBy);
    }
    private void appBackgroundLocation(MainActivity activity,int[] out){
        try{((View)field(activity,"appBackground")).getLocationOnScreen(out);}
        catch(Exception e){throw new RuntimeException(e);}
    }
    private void saveScreenshot(String name)throws Exception{
        Bitmap shot=getUiAutomation().takeScreenshot();require(shot!=null,"Screenshot");try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),name))){shot.compress(Bitmap.CompressFormat.PNG,100,out);}shot.recycle();
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
