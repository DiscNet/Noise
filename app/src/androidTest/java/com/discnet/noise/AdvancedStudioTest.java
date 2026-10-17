package com.discnet.noise;

import android.app.Instrumentation;
import android.graphics.*;
import android.os.*;
import android.view.*;
import java.io.File;
import android.widget.*;
import java.lang.reflect.*;
import com.discnet.noise.advancedstudio.*;

/** Tests real model/serialized layer pixels and interaction routes, not placeholders. */
final class AdvancedStudioTest {
    private static void check(boolean value,String message){
        if(!value)throw new AssertionError(message);
    }
    private static View label(View root,String text){
        if(root instanceof TextView&&text.contentEquals(((TextView)root).getText()))
            return root;
        if(root instanceof ViewGroup){
            ViewGroup group=(ViewGroup)root;
            for(int i=0;i<group.getChildCount();i++){
                View match=label(group.getChildAt(i),text);
                if(match!=null)return match;
            }
        }
        return null;
    }
    private static StudioInspectorField firstInspector(View root){
        if(root instanceof StudioInspectorField)return (StudioInspectorField)root;
        if(root instanceof ViewGroup){
            ViewGroup group=(ViewGroup)root;
            for(int i=0;i<group.getChildCount();i++){
                StudioInspectorField match=firstInspector(group.getChildAt(i));
                if(match!=null)return match;
            }
        }
        return null;
    }
    private static void incrementFirstInspector(View root){
        StudioInspectorField field=firstInspector(root);
        if(field==null)throw new AssertionError("Touch-first inspector missing");
        ViewGroup row=(ViewGroup)field.getChildAt(0);
        row.getChildAt(4).performClick();
    }
    static void run(Instrumentation test,MainActivity activity)throws Exception{
        StudioDocument doc=StudioDocument.blank("Advanced test",128,128,true);
        Bitmap background=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888);
        background.eraseColor(Color.RED);
        StudioDocument.Layer red=doc.add("Vermelho",background);
        Bitmap top=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888);
        top.eraseColor(Color.BLUE);
        StudioDocument.Layer blue=doc.add("Azul",top);
        blue.x=12;blue.y=18;
        Bitmap composited=doc.composite();
        check(composited.getPixel(2,2)==Color.RED,"Layer below is visible");
        check(composited.getPixel(18,24)==Color.BLUE,"Layer above is truly composited");
        composited.recycle();
        doc.checkpoint();blue.visible=false;
        check(doc.composite().getPixel(18,24)==Color.RED,
            "Layer visibility changes real pixels");
        check(doc.undo()&&doc.composite().getPixel(18,24)==Color.BLUE,
            "Undo restores previous composition");
        check(doc.redo()&&doc.composite().getPixel(18,24)==Color.RED,
            "Redo reapplies layer state");
        doc.undo();
        StudioDocument.Layer copy=doc.duplicate();
        check(copy!=null&&doc.layers.size()==3,"Duplicate creates independent layer entity");
        doc.moveOrder(-1);
        check(doc.layers.indexOf(copy)==1,"Layer reorder changes document z-order");
        doc.activeId=red.id;
        doc.active().locked=true;
        doc.remove();
        check(doc.layers.size()==3,"Locked layer cannot be removed");
        doc.active().locked=false;
        StudioProjectStore storage=new StudioProjectStore(activity);
        storage.save(doc);
        StudioDocument restored=storage.load(doc.id);
        check(restored.layers.size()==3 && restored.width==128&&restored.height==128,
            "Native project keeps real layer count and canvas resolution");
        check(restored.layers.get(0).bitmap.getPixel(5,5)==Color.RED,
            "Layer image survives lossless PNG archive round trip");
        check(restored.layers.get(1).blend==copy.blend,
            "Native project retains layer blend metadata");
        check(restored.id.equals(doc.id)&&restored.layers.get(2).x==blue.x,
            "Native project retains IDs and transforms");
        File persisted=new File(activity.getFilesDir(),"advanced_studio/"+doc.id+".noise");
        check(persisted.isFile()&&persisted.length()>200,
            "Advanced project is a durable .noise ZIP file, not a screenshot");
        check(!StudioDocument.validSize(8192,8192)&&
            !StudioDocument.validSize(-1,64),
            "Invalid and unbounded document dimensions are rejected");
        persisted.delete();
        // Phase 2: raster strokes must modify pixels and undo as a whole.
        StudioDocument drawing=StudioDocument.blank("Brush",64,64,true);
        drawing.add("Paint",null);
        StudioRasterTools.Stroke brush=StudioRasterTools.begin(
            drawing,Color.RED,12,1f,false,12,12,1f);
        brush.move(30,12);brush.finish();
        check(Color.red(drawing.active().bitmap.getPixel(20,12))>240,
            "Brush creates real red raster pixels");
        check(drawing.undo()&&drawing.active().bitmap.getPixel(20,12)==Color.TRANSPARENT,
            "Undo restores actual raster pixels, not only layer properties");
        check(drawing.redo()&&Color.red(drawing.active().bitmap.getPixel(20,12))>240,
            "Redo restores the full brush stroke");
        StudioRasterTools.Stroke eraser=StudioRasterTools.begin(
            drawing,Color.BLACK,15,1f,true,20,12,1f);
        eraser.finish();
        check(Color.alpha(drawing.active().bitmap.getPixel(20,12))==0,
            "Eraser removes alpha rather than painting over with background color");
        check(drawing.undo()&&Color.red(drawing.active().bitmap.getPixel(20,12))>240,
            "Erase is reversible without damaging source pixels");
        drawing.select(new RectF(0,0,20,20));
        StudioRasterTools.Stroke selected=StudioRasterTools.begin(
            drawing,Color.GREEN,10,1f,false,19,19,1f);
        selected.move(45,45);selected.finish();
        check(Color.green(drawing.active().bitmap.getPixel(17,17))>80,
            "Selected area accepts real brush pixels");
        check(drawing.active().bitmap.getPixel(42,42)==Color.TRANSPARENT,
            "Selection clips paint, preserving outside pixels");
        drawing.deselect();
        StudioRasterTools.bucket(drawing,60,60,Color.BLUE,16);
        check(drawing.active().bitmap.getPixel(60,60)==Color.BLUE,
            "Flood fill operates on contiguous pixels");
        StudioRasterTools.gradient(drawing,0,0,63,0,Color.YELLOW,Color.TRANSPARENT);
        check(drawing.active().bitmap.getPixel(2,50)!=Color.BLUE,
            "Linear gradient is applied to raster pixels");
        StudioRasterTools.adjust(drawing,0,0,-100);
        check(drawing.undo(),"Tonality adjustment is separately undoable");

        StudioDocument vectors=StudioDocument.blank("Vectors",256,256,true);
        StudioDocument.Layer txt=vectors.addText("NOISE",Color.WHITE,48);
        StudioDocument.Layer rect=vectors.addShape(StudioDocument.Type.RECTANGLE,
            Color.RED,80,50);
        rect.x=20;rect.y=30;rect.rotation=12;rect.scaleX=1.25f;
        StudioDocument.Layer ellipse=vectors.addShape(StudioDocument.Type.ELLIPSE,
            Color.GREEN,42,64);
        ellipse.x=120;ellipse.y=120;
        vectors.select(new RectF(0,0,150,150));
        Bitmap preview=vectors.composite();
        check(Color.alpha(preview.getPixel(30,40))!=0,
            "Parametric rectangle renders as geometry");
        preview.recycle();
        storage.save(vectors);
        StudioDocument reopened=storage.load(vectors.id);
        check(reopened.layers.size()==3&&
            reopened.layers.get(0).type==StudioDocument.Type.TEXT&&
            reopened.layers.get(0).text.equals("NOISE")&&
            reopened.layers.get(0).bitmap==null,
            "Text remains editable, not rasterized, after native save and reopen");
        check(reopened.layers.get(1).type==StudioDocument.Type.RECTANGLE &&
            reopened.layers.get(1).rotation==12f &&
            reopened.layers.get(1).scaleX==1.25f,
            "Vector shape and non-destructive transforms survive save/reopen");
        check(reopened.layers.get(2).type==StudioDocument.Type.ELLIPSE &&
            reopened.selection!=null,
            "Shape types and selection coordinates are persistent");
        reopened.crop(new RectF(10,10,160,160));
        check(reopened.width==150&&reopened.height==150,
            "Crop updates actual document geometry");
        check(reopened.undo()&&reopened.width==256&&reopened.height==256,
            "Non-destructive crop is fully reversible");
        new File(activity.getFilesDir(),"advanced_studio/"+vectors.id+".noise").delete();

        // Phase 3: masks are grayscale alpha sources, independent from raster.
        StudioDocument masked=StudioDocument.blank("Professional layers",64,64,true);
        Bitmap opaque=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);
        opaque.eraseColor(Color.RED);
        StudioDocument.Layer source=masked.add("Original preservado",opaque);
        StudioLayerEffects.createMask(masked,true);
        Bitmap unmodified=masked.composite();
        check(unmodified.getPixel(30,30)==Color.RED,
            "White mask reveals the original content without modifying it");
        unmodified.recycle();
        StudioLayerEffects.MaskStroke hiding=StudioLayerEffects.beginMaskStroke(
            masked,20,20,false,16f);
        hiding.move(28,20);
        Bitmap disappeared=masked.composite();
        check(Color.alpha(disappeared.getPixel(24,20))==0,
            "Black mask brush hides pixels in actual layer compositor");
        disappeared.recycle();
        check(source.bitmap.getPixel(24,20)==Color.RED,
            "Mask never damages the source raster image");
        check(masked.undo()&&masked.composite().getPixel(24,20)==Color.RED,
            "Undo restores previous mask bitmap without losing original");
        check(masked.redo()&&Color.alpha(masked.composite().getPixel(24,20))==0,
            "Redo re-applies painted mask");
        StudioLayerEffects.invertMask(masked);
        check(Color.alpha(masked.composite().getPixel(24,20))>200,
            "Inverting a mask truly changes visible alpha");
        masked.undo();
        masked.active().maskEnabled=false;
        check(masked.composite().getPixel(24,20)==Color.RED,
            "Disabling mask restores all original pixels");
        masked.active().maskEnabled=true;
        StudioLayerEffects.Effect bright=new StudioLayerEffects.Effect(
            StudioLayerEffects.Kind.BRIGHTNESS,40);
        masked.checkpoint();masked.active().effects.add(bright);
        StudioLayerEffects.Effect soft=new StudioLayerEffects.Effect(
            StudioLayerEffects.Kind.GAUSSIAN_BLUR,3);
        masked.checkpoint();masked.active().effects.add(soft);
        Bitmap compositedFx=masked.composite();
        check(compositedFx.getPixel(30,30)!=Color.TRANSPARENT,
            "Stacked effects produce visible output after masks");
        compositedFx.recycle();
        check(masked.active().bitmap.getPixel(30,30)==Color.RED,
            "Non-destructive filter chain does not modify original bitmap");
        masked.active().shadowEnabled=true;
        masked.active().shadowX=8f;masked.active().shadowY=8f;
        masked.active().shadowRadius=4f;
        storage.save(masked);
        StudioDocument restoredFx=storage.load(masked.id);
        check(restoredFx.layers.size()==1&&restoredFx.layers.get(0).mask!=null,
            "Layer mask remains separate and readable in .noise v3");
        check(restoredFx.layers.get(0).effects.size()==2 &&
            restoredFx.layers.get(0).effects.get(1).kind==
                StudioLayerEffects.Kind.GAUSSIAN_BLUR,
            "Filter stack order and parameters survive closing/reopening");
        check(restoredFx.layers.get(0).shadowEnabled&&
            restoredFx.layers.get(0).bitmap.getPixel(24,20)==Color.RED,
            "Drop shadow and untouched source pixels survive project persistence");
        check(restoredFx.toJson().getInt("format")==5,
            "Advanced Studio writes native project format version 5");
        StudioDocument enhanced=StudioDocument.blank("Typography and smart",256,256,true);
        StudioDocument.Layer styled=enhanced.addText("Noise!\\nStudio",Color.MAGENTA,34);
        styled.fontFamily="serif";styled.fontBold=true;styled.fontItalic=true;
        styled.letterSpacing=.08f;styled.lineSpacing=1.5f;styled.textAlign="CENTER";
        styled.rotation=15;styled.skewX=12;styled.skewY=-4;
        Bitmap embedded=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888);
        embedded.eraseColor(Color.CYAN);
        StudioDocument.Layer smart=enhanced.add("Smart image",embedded);
        smart.type=StudioDocument.Type.SMART;smart.scaleX=1.5f;
        storage.save(enhanced);
        StudioDocument restoredEnhanced=storage.load(enhanced.id);
        StudioDocument.Layer restoredText=restoredEnhanced.layers.get(0);
        check(restoredText.type==StudioDocument.Type.TEXT&&
            restoredText.fontBold&&restoredText.fontItalic&&
            restoredText.fontFamily.equals("serif")&&
            restoredText.textAlign.equals("CENTER")&&
            restoredText.letterSpacing==.08f&&
            restoredText.lineSpacing==1.5f&&restoredText.skewX==12f,
            "Character, paragraph and skew settings survive project round trip");
        check(restoredEnhanced.layers.get(1).type==StudioDocument.Type.SMART&&
            restoredEnhanced.layers.get(1).bitmap.getPixel(8,8)==Color.CYAN,
            "Smart embedded layer retains independent bitmap data");
        new File(activity.getFilesDir(),"advanced_studio/"+
            enhanced.id+".noise").delete();
        StudioDocument styledEffects=StudioDocument.blank("Layer style roundtrip",128,128,true);
        StudioDocument.Layer decorated=styledEffects.addShape(
            StudioDocument.Type.RECTANGLE,0xfff7ede8,52,44);
        decorated.x=27;decorated.y=30;
        for(StudioLayerStyles.Kind kind:StudioLayerStyles.Kind.values()){
            StudioLayerStyles.Style fx=new StudioLayerStyles.Style(kind);
            fx.size=4;fx.opacity=.82f;fx.contour="CUSTOM";
            fx.customCurve=new float[]{0,.1f,.75f,.95f,1};
            if(kind==StudioLayerStyles.Kind.INNER_GLOW)fx.sourceCenter=true;
            decorated.styles.add(fx);
        }
        check(decorated.styles.size()==8,"All eight independent layer styles are available");
        Bitmap stylePreview=styledEffects.composite();
        check(stylePreview.getPixel(36,38)!=Color.TRANSPARENT,
            "Layer style pipeline renders actual composited pixels");
        stylePreview.recycle();
        storage.save(styledEffects);
        StudioDocument styledReloaded=storage.load(styledEffects.id);
        StudioDocument.Layer loadedStyled=styledReloaded.active();
        check(loadedStyled!=null&&loadedStyled.styles.size()==8,
            "All editable layer effects persist to native .noise");
        check(loadedStyled.styles.get(4).sourceCenter,
            "Inner glow source Center survives project roundtrip");
        check(loadedStyled.styles.get(0).customCurve[2]==.75f,
            "Bevel custom contour points persist");
        styledReloaded.checkpoint();loadedStyled.styles.get(0).opacity=.33f;
        check(styledReloaded.undo()&&
            Math.abs(styledReloaded.active().styles.get(0).opacity-.82f)<.001f,
            "Undo restores previous layer style parameters");
        new File(activity.getFilesDir(),"advanced_studio/"+
            styledEffects.id+".noise").delete();
        // Rotation must pivot at visual center for raster, text and vector layers.
        StudioDocument centers=StudioDocument.blank("Pivot checks",256,256,true);
        StudioDocument.Layer pivotText=centers.addText("Noise!",Color.CYAN,48);
        pivotText.x=34;pivotText.y=41;
        float cx=StudioTransformMath.centerX(pivotText);
        float cy=StudioTransformMath.centerY(pivotText);
        float[] positionBefore=StudioTransformMath.point(pivotText,
            pivotText.naturalWidth()/2f,pivotText.naturalHeight()/2f);
        pivotText.rotation=90;
        float[] positionAfter=StudioTransformMath.point(pivotText,
            pivotText.naturalWidth()/2f,pivotText.naturalHeight()/2f);
        check(Math.abs(cx-positionAfter[0])<.01f &&
            Math.abs(cy-positionAfter[1])<.01f &&
            Math.abs(positionBefore[0]-positionAfter[0])<.01f &&
            Math.abs(positionBefore[1]-positionAfter[1])<.01f,
            "Text rotation keeps geometric center pinned, not top-left point");
        StudioDocument.Layer box=centers.addShape(StudioDocument.Type.RECTANGLE,
            Color.RED,48,32);
        box.x=27;box.y=59;box.scaleX=1.7f;box.scaleY=1.3f;
        float[] boxCenter0=StudioTransformMath.point(box,24,16);
        box.rotation=135;box.skewX=15;
        float[] boxCenter1=StudioTransformMath.point(box,24,16);
        check(Math.abs(boxCenter0[0]-boxCenter1[0])<.01f &&
            Math.abs(boxCenter0[1]-boxCenter1[1])<.01f,
            "Vector transform and skew keep the same rotation pivot");
        Bitmap roundTrip=centers.composite();
        check(roundTrip.getWidth()==256&&roundTrip.getHeight()==256,
            "Center-pivot compositor renders document at requested size");
        roundTrip.recycle();
        new File(activity.getFilesDir(),"advanced_studio/"+masked.id+".noise").delete();
        final AdvancedStudioScreen[] opened={null};
        test.runOnMainSync(()->{
            opened[0]=new AdvancedStudioScreen(activity,new AdvancedStudioScreen.Host(){
                public void exit(){}
                public void importImage(){}
            },null);
            ((android.view.ViewGroup)activity.findViewById(android.R.id.content))
                .addView(opened[0],new android.view.ViewGroup.LayoutParams(-1,-1));
        });
        test.waitForIdleSync();
        check(opened[0].getChildCount()>0,
            "Native Advanced Studio uses own canvas and property panels");
        final StudioDocument[] interactive={null};
        test.runOnMainSync(()->{
            try{
                Field docField=AdvancedStudioScreen.class.getDeclaredField("document");
                docField.setAccessible(true);
                interactive[0]=(StudioDocument)docField.get(opened[0]);
                StudioDocument.Layer live=interactive[0].addText("Alive",Color.WHITE,36);
                live.shadowEnabled=true;live.shadowRadius=2;
                Method update=AdvancedStudioScreen.class.getDeclaredMethod("updateUi");
                update.setAccessible(true);update.invoke(opened[0]);
                View textTab=label(opened[0],"TEXTO");
                check(textTab!=null,"Text panel tab is mounted");
                textTab.performClick();
                incrementFirstInspector(opened[0]);
                View layerTab=label(opened[0],"CAMADAS");
                check(layerTab!=null,"Layer panel tab is mounted");
                layerTab.performClick();
                View shadow=label(opened[0],"☑ Sombra");
                check(shadow!=null,"Shadow controls remain available below layers");
                shadow.performClick();
                incrementFirstInspector(opened[0]);
            }catch(Exception error){throw new RuntimeException(error);}
        });
        test.waitForIdleSync();
        // Real delayed save + panel refresh executes after editing; this path
        // was previously absent from the smoke suite.
        SystemClock.sleep(2400);
        test.waitForIdleSync();
        File interactiveFile=new File(activity.getFilesDir(),
            "advanced_studio/"+interactive[0].id+".noise");
        check(interactiveFile.isFile(),
            "Continuous adjustments and delayed autosave do not terminate Studio");
        StudioDocument reloaded=storage.load(interactive[0].id);
        check(reloaded.active()!=null&&reloaded.active().shadowEnabled,
            "Saved interactive session can be reopened after debounce");
        test.runOnMainSync(()->{
            check(opened[0].isAttachedToWindow(),"Studio remains attached after delayed UI refresh");
            ((android.view.ViewGroup)opened[0].getParent()).removeView(opened[0]);
            opened[0].dispose();
        });
        interactiveFile.delete();
    }
}
