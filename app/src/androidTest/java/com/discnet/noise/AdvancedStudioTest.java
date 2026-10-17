package com.discnet.noise;

import android.app.Instrumentation;
import android.graphics.*;
import android.os.*;
import android.view.*;
import java.io.File;
import com.discnet.noise.advancedstudio.*;

/** Tests real model/serialized layer pixels and interaction routes, not placeholders. */
final class AdvancedStudioTest {
    private static void check(boolean value,String message){
        if(!value)throw new AssertionError(message);
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
        red.locked=true;
        doc.remove();
        check(doc.layers.size()==3,"Locked layer cannot be removed");
        red.locked=false;
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
        test.runOnMainSync(()->{
            ((android.view.ViewGroup)opened[0].getParent()).removeView(opened[0]);
            opened[0].dispose();
        });
    }
}
