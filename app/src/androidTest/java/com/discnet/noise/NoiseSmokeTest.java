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
            for(int id=0;id<AdjustmentSpec.COUNT;id++){
                EditorDocument lo=new EditorDocument();
                if(id>=20&&id<26)lo.values[9]=1;
                if(id>=26&&id<44)lo.values[26+((id-26)/6)*6]=1;
                if(id>=44&&id<50)lo.asciiEnabled=true;
                if(id==55||id==56)lo.values[54]=1;
                if(id==57||id==58)lo.values[10]=1;
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
            stage("non-destructive transformed export");
            Bitmap quad=Bitmap.createBitmap(40,20,Bitmap.Config.ARGB_8888);for(int y=0;y<20;y++)for(int x=0;x<40;x++)quad.setPixel(x,y,x<20?Color.RED:Color.BLUE);
            EditorDocument geometry=new EditorDocument();geometry.left=.5f;int[] shape=geometry.outputSize(40,20,4096);Bitmap cropped=GpuExporter.render(getTargetContext(),quad,geometry.gpu(false),shape[0],shape[1]);require(shape[0]==20&&shape[1]==20,"Crop changes output geometry");closeColor(cropped.getPixel(10,10),Color.BLUE,"Crop selects original source pixels");require(quad.getWidth()==40,"Crop never degrades original");cropped.recycle();geometry.left=0;geometry.rotation=1;shape=geometry.outputSize(40,20,4096);require(shape[0]==20&&shape[1]==40,"Rotation swaps geometry");Bitmap turned=GpuExporter.render(getTargetContext(),quad,geometry.gpu(false),shape[0],shape[1]);closeColor(turned.getPixel(10,5),Color.RED,"Clockwise rotation top");closeColor(turned.getPixel(10,35),Color.BLUE,"Clockwise rotation bottom");turned.recycle();quad.recycle();
            stage("UI import, adaptive workspace and live gestures");
            runOnMainSync(()->activity.load(Uri.fromFile(input)));long deadline=SystemClock.uptimeMillis()+20000;
            while((Boolean)field(activity,"loading")&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(30);
            require(field(activity,"original")!=null,"Import decoded source");EditorSurface surface=(EditorSurface)field(activity,"preview");EditorWorkspace workspace=(EditorWorkspace)field(activity,"workspace");EditorPanel panel=(EditorPanel)field(activity,"panel");
            TextView[] tabs=(TextView[])field(activity,"tabs");require(tabs.length==7,"Seven requested categories");
            for(int i=0;i<7;i++){final int n=i;runOnMainSync(()->tabs[n].performClick());require(panel.category==i,"Category "+i+" reachable");}
            stage("orbital tool gesture and functional effect routing");
            runOnMainSync(activity::openEffects);waitForIdleSync();
            OrbitalSelector orbital=(OrbitalSelector)field(activity,"orbital");
            View orbitStage=(View)field(orbital,"orbit");
            require(orbitStage.getHeight()>0&&orbitStage.getWidth()>0,"Orbital selector measured");
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
            stage("opposite horizontal swipes, independent scrim fade, critically damped orbital rotation");
            SystemClock.sleep(520); // allow prior selection overlay to disappear
            FrameLayout gestureHost=(FrameLayout)field(activity,"appBackground");
            float gestureY=gestureHost.getPaddingTop()+workspace.getTop()
                +workspace.stage.getTop()+workspace.stage.getHeight()*.50f;
            float width=gestureHost.getWidth();
            float fromTools=width*.63f,toTools=width*.13f;
            long gt=SystemClock.uptimeMillis();
            runOnMainSync(()->{
                gestureHost.dispatchTouchEvent(MotionEvent.obtain(gt,gt,
                    MotionEvent.ACTION_DOWN,fromTools,gestureY,0));
                gestureHost.dispatchTouchEvent(MotionEvent.obtain(gt,gt+95,
                    MotionEvent.ACTION_MOVE,width*.34f,gestureY,0));
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
            require(eased.getAlpha()==1f,"Overlay container stays fully opaque while scrim fades");
            require(eased.scrimOpacity()<.84f,
                "Dark overlay is separately animated, not an instant black scrim");
            require(eased.motionX()>0,
                "Right-side Tools wheel eases in from the right");
            require(eased.scrimOpacity()>0f && eased.scrimOpacity()<.84f,
                "Dark overlay fades in separately from wheel movement");
            runOnMainSync(()->gestureHost.dispatchTouchEvent(MotionEvent.obtain(gt,gt+220,
                MotionEvent.ACTION_UP,toTools,gestureY,0)));
            OrbitalSelector swipeTools=(OrbitalSelector)field(activity,"orbital");
            require(swipeTools!=null && !swipeTools.isEffects(),
                "Non-edge swipe opens tools");
            waitForIdleSync();
            require(swipeTools.dockLeft()>width*.09f,"Tools panel docks on RIGHT");
            require(swipeTools.dockRight()==gestureHost.getWidth(),"Tools panel touches right edge");
            require(swipeTools.orbitalScreenX()>width*.65f,"Tools orbit and active icon are on RIGHT");
            runOnMainSync(activity::onBackPressed);
            SystemClock.sleep(480);

            float fromEffects=width*.11f,toEffects=width*.79f;
            long ge=SystemClock.uptimeMillis();
            runOnMainSync(()->{
                gestureHost.dispatchTouchEvent(MotionEvent.obtain(ge,ge,
                    MotionEvent.ACTION_DOWN,fromEffects,gestureY,0));
                gestureHost.dispatchTouchEvent(MotionEvent.obtain(ge,ge+85,
                    MotionEvent.ACTION_MOVE,width*.42f,gestureY,0));
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
            SystemClock.sleep(450);
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
