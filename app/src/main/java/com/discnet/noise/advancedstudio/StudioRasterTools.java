package com.discnet.noise.advancedstudio;

import android.graphics.*;
import java.util.*;

/** Actual raster operations. Every transaction replaces the bitmap rather
 * than editing historical bitmaps, preserving independent Undo/Redo pixels. */
public final class StudioRasterTools {
    private StudioRasterTools(){}
    private static StudioDocument.Layer editable(StudioDocument d){
        StudioDocument.Layer l=d.active();
        if(l==null||l.locked||l.type!=StudioDocument.Type.RASTER||l.bitmap==null)
            throw new IllegalStateException("Selecione uma camada raster desbloqueada");
        return l;
    }
    public static final class Stroke {
        private final StudioDocument doc;
        public final StudioDocument.Layer layer;
        private final Canvas canvas;
        private final Bitmap original,painted;
        private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
        private float previousX,previousY;
        Stroke(StudioDocument doc,StudioDocument.Layer layer,Bitmap backing,Bitmap original,
            int color,float size,float opacity,boolean erase){
            this.doc=doc;this.layer=layer;this.canvas=new Canvas(backing);
            this.original=original;this.painted=backing;
            pen.setStrokeWidth(Math.max(1f,size));
            pen.setStrokeCap(Paint.Cap.ROUND);pen.setStrokeJoin(Paint.Join.ROUND);
            pen.setStyle(Paint.Style.STROKE);
            pen.setColor(color);pen.setAlpha(Math.round(Math.max(0,Math.min(1,opacity))*255));
            if(erase)pen.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        }
        private void clip(){
            // Selection is enforced pixel-accurately on finish, including
            // rotated, skewed and non-rectangular alpha selections.
        }
        public void start(float x,float y,float pressure){
            previousX=x;previousY=y;
            canvas.save();clip();
            pen.setStrokeWidth(pen.getStrokeWidth()*Math.max(.15f,Math.min(1.3f,pressure)));
            canvas.drawPoint(x,y,pen);
            canvas.restore();
            StudioLayerEffects.invalidate(layer);
        }
        public void move(float x,float y){
            canvas.save();clip();
            canvas.drawLine(previousX,previousY,x,y,pen);
            canvas.restore();
            previousX=x;previousY=y;
            StudioLayerEffects.invalidate(layer);
        }
        public void finish(){
            if(doc.hasSelection())
                restrictToSelection(doc,layer,original,painted);
        } // One undo checkpoint covers the complete gesture.
    }
    public static Stroke begin(StudioDocument doc,int color,float size,float opacity,
                               boolean erase,float x,float y,float pressure){
        StudioDocument.Layer selected=editable(doc);
        Bitmap next=selected.bitmap.copy(Bitmap.Config.ARGB_8888,true);
        if(next==null)throw new IllegalStateException("Memória insuficiente para pincel");
        Bitmap before=selected.bitmap;
        doc.checkpoint();
        selected.bitmap=next;
        Stroke s=new Stroke(doc,selected,next,before,color,size,opacity,erase);
        s.start(x,y,pressure);return s;
    }
    private static void restrictToSelection(StudioDocument d,StudioDocument.Layer l,
                                            Bitmap before,Bitmap after){
        if(!d.hasSelection()||before==null)return;
        int w=after.getWidth(),h=after.getHeight();
        int[] old=new int[w*h],now=new int[w*h];
        before.getPixels(old,0,w,0,0,w,h);after.getPixels(now,0,w,0,0,w,h);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float[] p=StudioTransformMath.point(l,x+.5f,y+.5f);
            if(!d.containsSelection(p[0],p[1]))now[y*w+x]=old[y*w+x];
        }
        after.setPixels(now,0,w,0,0,w,h);
    }
    public static void clearOrFill(StudioDocument d,int color,boolean clear,boolean maskTarget){
        StudioDocument.Layer l=d.active();
        if(l==null||l.locked)throw new IllegalStateException("Selecione uma camada desbloqueada");
        if(l.type==StudioDocument.Type.SMART&&!maskTarget)
            throw new IllegalStateException("Edite o conteúdo inteligente ou rasterize a camada");
        if(!maskTarget&&l.type!=StudioDocument.Type.RASTER)
            throw new IllegalStateException("Pinte somente em camadas rasterizadas");
        if(maskTarget&&l.mask==null)throw new IllegalStateException("A camada não possui máscara");
        Bitmap original=maskTarget?l.mask:l.bitmap;
        if(original==null)throw new IllegalStateException("Sem pixels");
        int w=original.getWidth(),h=original.getHeight();
        int[] pixels=new int[w*h];original.getPixels(pixels,0,w,0,0,w,h);
        int result=maskTarget?
            Color.rgb(Color.red(color),Color.red(color),Color.red(color)):
            clear?Color.TRANSPARENT:color;
        boolean changed=false;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float[] coord=StudioTransformMath.point(l,x+.5f,y+.5f);
            if(d.hasSelection()&&!d.containsSelection(coord[0],coord[1]))continue;
            pixels[y*w+x]=maskTarget&&clear?Color.BLACK:result;
            changed=true;
        }
        if(!changed)return;
        Bitmap next=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        next.setPixels(pixels,0,w,0,0,w,h);
        d.checkpoint();if(maskTarget)l.mask=next;else l.bitmap=next;
        StudioLayerEffects.invalidate(l);
    }
    /** Contiguous tolerance flood fill. Hard limited to 4M pixels per tap. */
    public static void bucket(StudioDocument d,int x,int y,int fill,int tolerance){
        StudioDocument.Layer l=editable(d);
        Bitmap source=l.bitmap;int w=source.getWidth(),h=source.getHeight();
        if((long)w*h>4_194_304)
            throw new IllegalStateException("Balde limitado a 4 megapixels nesta fase");
        if(x<0||y<0||x>=w||y>=h)return;
        int[] pix=new int[w*h];source.getPixels(pix,0,w,0,0,w,h);
        int target=pix[y*w+x];if(target==fill)return;
        boolean[] visited=new boolean[pix.length];
        int[] queue=new int[pix.length];int head=0,tail=0;
        int start=y*w+x;queue[tail++]=start;visited[start]=true;
        int changed=0;
        while(head<tail){
            int idx=queue[head++],px=idx%w,py=idx/w;
            if(!matches(pix[idx],target,tolerance))continue;
            float[] coord=StudioTransformMath.point(l,px+.5f,py+.5f);
            if(d.hasSelection()&&!d.containsSelection(coord[0],coord[1]))continue;
            pix[idx]=fill;changed++;
            if(px>0)tail=enqueue(idx-1,visited,queue,tail);
            if(px<w-1)tail=enqueue(idx+1,visited,queue,tail);
            if(py>0)tail=enqueue(idx-w,visited,queue,tail);
            if(py<h-1)tail=enqueue(idx+w,visited,queue,tail);
        }
        if(changed==0)return;
        Bitmap next=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        next.setPixels(pix,0,w,0,0,w,h);
        if(d.selectionPixels!=null)restrictToSelection(d,l,source,next);
        d.checkpoint();l.bitmap=next;
    }
    private static int enqueue(int at,boolean[] seen,int[] queue,int count){
        if(!seen[at]){seen[at]=true;queue[count++]=at;}
        return count;
    }
    private static boolean matches(int a,int b,int t){
        return Math.abs(Color.alpha(a)-Color.alpha(b))<=t &&
            Math.abs(Color.red(a)-Color.red(b))<=t &&
            Math.abs(Color.green(a)-Color.green(b))<=t &&
            Math.abs(Color.blue(a)-Color.blue(b))<=t;
    }
    /** Persistent linear gradient on the selected layer; clips to selection. */
    public static void gradient(StudioDocument d,float x1,float y1,float x2,float y2,
                                int start,int end){
        StudioDocument.Layer l=editable(d);
        Bitmap copy=l.bitmap.copy(Bitmap.Config.ARGB_8888,true);
        if(copy==null)throw new IllegalStateException("Memória insuficiente");
        Canvas c=new Canvas(copy);

        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        if(Math.hypot(x2-x1,y2-y1)<1){x2=x1+1;y2=y1;}
        p.setShader(new LinearGradient(x1,y1,x2,y2,start,end,Shader.TileMode.CLAMP));
        c.drawRect(0,0,copy.getWidth(),copy.getHeight(),p);
        if(d.hasSelection())restrictToSelection(d,l,l.bitmap,copy);
        d.checkpoint();l.bitmap=copy;
    }
    /** Explicit raster adjustment, not a collection of duplicate sliders. */
    public static void adjust(StudioDocument d,int brightness,int contrast,int saturation){
        StudioDocument.Layer l=editable(d);
        brightness=Math.max(-100,Math.min(100,brightness));
        contrast=Math.max(-100,Math.min(100,contrast));
        saturation=Math.max(-100,Math.min(100,saturation));
        float c=(100f+contrast)/100f,shift=128*(1-c)+brightness*2.55f;
        ColorMatrix matrix=new ColorMatrix();
        matrix.setSaturation(1f+saturation/100f);
        ColorMatrix light=new ColorMatrix(new float[]{
            c,0,0,0,shift,0,c,0,0,shift,0,0,c,0,shift,0,0,0,1,0
        });
        matrix.postConcat(light);
        Bitmap next=Bitmap.createBitmap(l.bitmap.getWidth(),l.bitmap.getHeight(),
            Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(next);
        Paint p=new Paint(Paint.FILTER_BITMAP_FLAG);
        p.setColorFilter(new ColorMatrixColorFilter(matrix));
        canvas.drawBitmap(l.bitmap,0,0,p);
        // Adjust only selected pixels; retain original outside the selection.
        if(d.hasSelection())restrictToSelection(d,l,l.bitmap,next);
        d.checkpoint();l.bitmap=next;
    }
}
