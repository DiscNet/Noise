package com.discnet.noise.advancedstudio;

import android.graphics.*;
import android.os.Build;
import org.json.*;
import java.util.*;

/**
 * Editable multi-layer document. Raster bitmaps use copy-on-write so the command
 * history retains independent pixel versions. Vectors/text remain parametric.
 */
public final class StudioDocument {
    public static final int FORMAT_VERSION=4, MAX_SIDE=8192, MAX_PIXELS=16_777_216, MAX_LAYERS=40;
    public enum Type { RASTER,SMART,TEXT,RECTANGLE,ELLIPSE }
    public enum Blend { NORMAL,MULTIPLY,SCREEN,OVERLAY,DARKEN,LIGHTEN }
    public static final class Layer {
        public final String id;
        public String name;
        public Type type=Type.RASTER;
        public Bitmap bitmap;
        public float x,y,scaleX=1f,scaleY=1f,rotation,skewX,skewY;
        public float opacity=1f;
        public boolean visible=true,locked=false;
        public Blend blend=Blend.NORMAL;
        public String text="";
        public float fontSize=64f,letterSpacing=0f,lineSpacing=1.16f;
        public String fontFamily="sans-serif",textAlign="LEFT";
        public boolean fontBold,fontItalic;
        public int color=Color.WHITE,objectWidth=240,objectHeight=160;
        public Bitmap mask;
        public boolean maskEnabled=true,shadowEnabled=false;
        public int maskDensity=100;
        public float shadowRadius=5,shadowX=5,shadowY=5,shadowOpacity=.65f;
        public int shadowColor=Color.BLACK;
        public final ArrayList<StudioLayerEffects.Effect> effects=new ArrayList<>();
        Layer(String id,String name,Bitmap bitmap){
            this.id=id;this.name=name;this.bitmap=bitmap;
        }
        public int naturalWidth(){
            return (type==Type.RASTER||type==Type.SMART)?(bitmap==null?0:bitmap.getWidth()):
                type==Type.TEXT?StudioTypography.width(this):objectWidth;
        }
        public int naturalHeight(){
            return (type==Type.RASTER||type==Type.SMART)?(bitmap==null?0:bitmap.getHeight()):
                type==Type.TEXT?StudioTypography.height(this):objectHeight;
        }
        Layer copyWithNewId(){
            Layer l=copy();Layer newer=new Layer(UUID.randomUUID().toString(),name+" cópia",l.bitmap);
            newer.type=l.type;newer.x=l.x+18;newer.y=l.y+18;
            newer.scaleX=l.scaleX;newer.scaleY=l.scaleY;newer.rotation=l.rotation;
            newer.skewX=l.skewX;newer.skewY=l.skewY;
            newer.opacity=l.opacity;newer.visible=l.visible;newer.locked=l.locked;
            newer.blend=l.blend;newer.text=l.text;newer.fontSize=l.fontSize;
            newer.fontFamily=l.fontFamily;newer.fontBold=l.fontBold;newer.fontItalic=l.fontItalic;
            newer.letterSpacing=l.letterSpacing;newer.lineSpacing=l.lineSpacing;
            newer.textAlign=l.textAlign;
            newer.color=l.color;newer.objectWidth=l.objectWidth;
            newer.objectHeight=l.objectHeight;
            newer.mask=l.mask;newer.maskEnabled=l.maskEnabled;
            newer.maskDensity=l.maskDensity;
            newer.shadowEnabled=l.shadowEnabled;newer.shadowRadius=l.shadowRadius;
            newer.shadowX=l.shadowX;newer.shadowY=l.shadowY;
            newer.shadowColor=l.shadowColor;newer.shadowOpacity=l.shadowOpacity;
            for(StudioLayerEffects.Effect e:l.effects)newer.effects.add(e.copy());
            return newer;
        }
        Layer copy(){
            Layer l=new Layer(id,name,bitmap);l.type=type;
            l.x=x;l.y=y;l.scaleX=scaleX;l.scaleY=scaleY;l.rotation=rotation;
            l.skewX=skewX;l.skewY=skewY;
            l.opacity=opacity;l.visible=visible;l.locked=locked;l.blend=blend;
            l.text=text;l.fontSize=fontSize;l.color=color;
            l.fontFamily=fontFamily;l.fontBold=fontBold;l.fontItalic=fontItalic;
            l.letterSpacing=letterSpacing;l.lineSpacing=lineSpacing;l.textAlign=textAlign;
            l.objectWidth=objectWidth;l.objectHeight=objectHeight;
            l.mask=mask;l.maskEnabled=maskEnabled;l.maskDensity=maskDensity;
            l.shadowEnabled=shadowEnabled;l.shadowRadius=shadowRadius;
            l.shadowX=shadowX;l.shadowY=shadowY;
            l.shadowOpacity=shadowOpacity;l.shadowColor=shadowColor;
            for(StudioLayerEffects.Effect e:effects)l.effects.add(e.copy());
            return l;
        }
        JSONObject toJson()throws JSONException{
            JSONObject j=new JSONObject().put("id",id).put("name",name)
                .put("type",type.name()).put("x",x).put("y",y)
                .put("scaleX",scaleX).put("scaleY",scaleY).put("rotation",rotation)
                .put("skewX",skewX).put("skewY",skewY)
                .put("opacity",opacity).put("visible",visible).put("locked",locked)
                .put("blend",blend.name()).put("text",text).put("fontSize",fontSize)
                .put("fontFamily",fontFamily).put("fontBold",fontBold)
                .put("fontItalic",fontItalic).put("letterSpacing",letterSpacing)
                .put("lineSpacing",lineSpacing).put("textAlign",textAlign)
                .put("color",color).put("objectWidth",objectWidth)
                .put("objectHeight",objectHeight);
            if(type==Type.RASTER||type==Type.SMART)j.put("image","layers/"+id+".png");
            if(mask!=null)j.put("mask","masks/"+id+".png");
            j.put("maskEnabled",maskEnabled).put("maskDensity",maskDensity);
            j.put("shadowEnabled",shadowEnabled).put("shadowRadius",shadowRadius)
                .put("shadowX",shadowX).put("shadowY",shadowY)
                .put("shadowColor",shadowColor).put("shadowOpacity",shadowOpacity);
            JSONArray stack=new JSONArray();
            for(StudioLayerEffects.Effect e:effects)stack.put(e.toJson());
            j.put("effects",stack);
            return j;
        }
    }
    public final String id;
    public String name;
    public int width,height;
    public final int dpi;
    public int background=Color.TRANSPARENT;
    public RectF selection;
    public float zoom=1f,panX,panY;
    public String activeId;
    public boolean dirty;
    public final ArrayList<Layer> layers=new ArrayList<>();
    private final ArrayList<State> undo=new ArrayList<>(),redo=new ArrayList<>();
    private static final int MAX_HISTORY=18;
    private static final class State {
        final ArrayList<Layer> layers=new ArrayList<>();
        final int width,height,background;
        final RectF selection;
        final String activeId;
        State(StudioDocument d){
            for(Layer l:d.layers)layers.add(l.copy());
            width=d.width;height=d.height;background=d.background;
            selection=d.selection==null?null:new RectF(d.selection);
            activeId=d.activeId;
        }
    }
    public StudioDocument(String id,String name,int width,int height,int dpi){
        if(!validSize(width,height)||dpi<1||dpi>2400)
            throw new IllegalArgumentException("Documento inválido");
        this.id=id;this.name=name;this.width=width;this.height=height;this.dpi=dpi;
    }
    public static StudioDocument blank(String name,int w,int h,boolean transparent){
        StudioDocument d=new StudioDocument(UUID.randomUUID().toString(),
            name==null||name.trim().isEmpty()?"Sem título":name,w,h,72);
        d.background=transparent?Color.TRANSPARENT:Color.WHITE;return d;
    }
    public static boolean validSize(int w,int h){
        return w>=16&&h>=16&&w<=MAX_SIDE&&h<=MAX_SIDE&&(long)w*h<=MAX_PIXELS;
    }
    public Layer active(){
        if(activeId==null)return null;
        for(Layer layer:layers)if(layer.id.equals(activeId))return layer;
        return null;
    }
    public void checkpoint(){
        undo.add(new State(this));if(undo.size()>MAX_HISTORY)undo.remove(0);
        redo.clear();dirty=true;
    }
    private void restore(State s){
        layers.clear();for(Layer l:s.layers)layers.add(l.copy());
        width=s.width;height=s.height;background=s.background;
        selection=s.selection==null?null:new RectF(s.selection);
        activeId=s.activeId;dirty=true;
    }
    public boolean canUndo(){return !undo.isEmpty();}
    public boolean canRedo(){return !redo.isEmpty();}
    public boolean undo(){
        if(undo.isEmpty())return false;redo.add(new State(this));
        restore(undo.remove(undo.size()-1));return true;
    }
    public boolean redo(){
        if(redo.isEmpty())return false;undo.add(new State(this));
        restore(redo.remove(redo.size()-1));return true;
    }
    private void ensureCapacity(Bitmap source){
        if(layers.size()>=MAX_LAYERS)throw new IllegalStateException("Limite de camadas");
        long occupied=0;
        for(Layer l:layers)if(l.bitmap!=null)
            occupied+=(long)l.bitmap.getWidth()*l.bitmap.getHeight();
        if(source!=null&&occupied+(long)source.getWidth()*source.getHeight()>36_000_000)
            throw new IllegalStateException("Memória de camadas: limite desta fase");
    }
    public Layer add(String label,Bitmap source){
        Bitmap pixels=source==null?Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888):source;
        try{ensureCapacity(pixels);}catch(RuntimeException e){
            if(source==null)pixels.recycle();throw e;
        }
        checkpoint();
        Layer layer=new Layer(UUID.randomUUID().toString(),label,pixels);
        layers.add(layer);activeId=layer.id;return layer;
    }
    public Layer addText(String value,int argb,float size){
        ensureCapacity(null);checkpoint();
        Layer l=new Layer(UUID.randomUUID().toString(),"Texto",null);
        l.type=Type.TEXT;l.text=value;l.color=argb;l.fontSize=Math.max(8,Math.min(600,size));
        l.x=width*.12f;l.y=height*.22f;
        layers.add(l);activeId=l.id;return l;
    }
    public Layer addShape(Type kind,int argb,int w,int h){
        if(kind!=Type.RECTANGLE&&kind!=Type.ELLIPSE)throw new IllegalArgumentException("Forma");
        ensureCapacity(null);checkpoint();
        Layer l=new Layer(UUID.randomUUID().toString(),
            kind==Type.RECTANGLE?"Retângulo":"Elipse",null);
        l.type=kind;l.color=argb;l.objectWidth=Math.max(1,w);l.objectHeight=Math.max(1,h);
        l.x=width*.15f;l.y=height*.15f;
        layers.add(l);activeId=l.id;return l;
    }
    public Layer duplicate(){
        Layer current=active();if(current==null||layers.size()>=MAX_LAYERS)return null;
        checkpoint();Layer copy=current.copyWithNewId();
        layers.add(layers.indexOf(current)+1,copy);activeId=copy.id;return copy;
    }
    // Duplicate clones metadata but uses the same immutable bitmap until a brush edits it.
    public void remove(){
        Layer l=active();if(l==null||l.locked)return;
        checkpoint();int i=layers.indexOf(l);layers.remove(i);
        activeId=layers.isEmpty()?null:layers.get(Math.min(i,layers.size()-1)).id;
    }
    public void moveOrder(int direction){
        Layer l=active();if(l==null||l.locked)return;
        int i=layers.indexOf(l),target=i+direction;
        if(target<0||target>=layers.size())return;
        checkpoint();layers.remove(i);layers.add(target,l);
    }
    public void select(RectF rect){
        RectF next=new RectF(rect);next.sort();
        next.intersect(0,0,width,height);
        checkpoint();selection=next.isEmpty()?null:next;
    }
    public void deselect(){if(selection!=null){checkpoint();selection=null;}}
    public void crop(RectF rectangle){
        RectF target=new RectF(rectangle);target.sort();
        target.intersect(0,0,width,height);
        int left=(int)Math.floor(target.left),top=(int)Math.floor(target.top);
        int right=(int)Math.ceil(target.right),bottom=(int)Math.ceil(target.bottom);
        if(!validSize(right-left,bottom-top))
            throw new IllegalArgumentException("Recorte inválido");
        checkpoint();width=right-left;height=bottom-top;
        for(Layer l:layers){l.x-=left;l.y-=top;}
        selection=null;
    }
    public void render(Canvas canvas){
        canvas.save();canvas.clipRect(0,0,width,height);
        if(Color.alpha(background)!=0)canvas.drawColor(background);
        for(Layer l:layers){
            if(!l.visible)continue;
            canvas.save();
            canvas.translate(l.x,l.y);
            canvas.rotate(l.rotation);
            canvas.skew((float)Math.tan(Math.toRadians(l.skewX)),
                (float)Math.tan(Math.toRadians(l.skewY)));
            canvas.scale(l.scaleX,l.scaleY);
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            p.setColor(l.color);
            p.setAlpha(Math.round(Math.max(0,Math.min(1,l.opacity))*255));
            if(Build.VERSION.SDK_INT>=29){
                BlendMode blend=l.blend==Blend.MULTIPLY?BlendMode.MULTIPLY:
                    l.blend==Blend.SCREEN?BlendMode.SCREEN:
                    l.blend==Blend.OVERLAY?BlendMode.OVERLAY:
                    l.blend==Blend.DARKEN?BlendMode.DARKEN:
                    l.blend==Blend.LIGHTEN?BlendMode.LIGHTEN:BlendMode.SRC_OVER;
                p.setBlendMode(blend);
            }else if(l.blend==Blend.MULTIPLY||l.blend==Blend.SCREEN){
                p.setXfermode(new PorterDuffXfermode(l.blend==Blend.MULTIPLY?
                    PorterDuff.Mode.MULTIPLY:PorterDuff.Mode.SCREEN));
            }
            if(StudioLayerEffects.requiresProcessing(l)||l.shadowEnabled){
                StudioLayerEffects.draw(canvas,l,p);
            }else switch(l.type){
                case SMART:case RASTER:if(l.bitmap!=null&&!l.bitmap.isRecycled())
                    canvas.drawBitmap(l.bitmap,0,0,p);break;
                case TEXT:
                    StudioTypography.draw(canvas,l,p);
                    break;
                case RECTANGLE:canvas.drawRect(0,0,l.objectWidth,l.objectHeight,p);break;
                case ELLIPSE:canvas.drawOval(0,0,l.objectWidth,l.objectHeight,p);break;
            }
            canvas.restore();
        }
        canvas.restore();
    }
    public Bitmap composite(){
        Bitmap result=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        render(new Canvas(result));return result;
    }
    public JSONObject toJson()throws JSONException{
        JSONObject j=new JSONObject().put("format",FORMAT_VERSION)
            .put("id",id).put("name",name).put("width",width).put("height",height)
            .put("dpi",dpi).put("background",background).put("active",activeId)
            .put("zoom",zoom).put("panX",panX).put("panY",panY);
        if(selection!=null)j.put("selection",new JSONArray()
            .put(selection.left).put(selection.top).put(selection.right).put(selection.bottom));
        JSONArray items=new JSONArray();for(Layer l:layers)items.put(l.toJson());
        return j.put("layers",items);
    }
    static StudioDocument fromJson(JSONObject j)throws Exception{
        int format=j.getInt("format");
        if(format!=1&&format!=2&&format!=3&&format!=FORMAT_VERSION)
            throw new java.io.IOException("Versão .noise incompatível");
        StudioDocument d=new StudioDocument(j.getString("id"),j.getString("name"),
            j.getInt("width"),j.getInt("height"),j.optInt("dpi",72));
        d.background=j.optInt("background",Color.TRANSPARENT);
        d.activeId=j.optString("active",null);
        d.zoom=(float)j.optDouble("zoom",1);
        d.panX=(float)j.optDouble("panX",0);d.panY=(float)j.optDouble("panY",0);
        JSONArray s=j.optJSONArray("selection");
        if(s!=null&&s.length()==4)d.selection=new RectF((float)s.getDouble(0),
            (float)s.getDouble(1),(float)s.getDouble(2),(float)s.getDouble(3));
        return d;
    }
    static Layer decodeLayer(JSONObject j,Bitmap source)throws Exception{
        Layer l=new Layer(j.getString("id"),j.getString("name"),source);
        try{l.type=Type.valueOf(j.optString("type","RASTER"));}
        catch(IllegalArgumentException e){throw new java.io.IOException("Camada desconhecida",e);}
        l.x=(float)j.optDouble("x");l.y=(float)j.optDouble("y");
        l.scaleX=(float)j.optDouble("scaleX",1);
        l.scaleY=(float)j.optDouble("scaleY",1);
        l.rotation=(float)j.optDouble("rotation",0);
        l.skewX=(float)j.optDouble("skewX",0);l.skewY=(float)j.optDouble("skewY",0);
        if(!Float.isFinite(l.x)||!Float.isFinite(l.y)||
           !Float.isFinite(l.scaleX)||!Float.isFinite(l.scaleY)||
           Math.abs(l.scaleX)>64||Math.abs(l.scaleY)>64||
            !Float.isFinite(l.rotation)||!Float.isFinite(l.skewX)||
            !Float.isFinite(l.skewY)||Math.abs(l.skewX)>70||Math.abs(l.skewY)>70||
           l.scaleX==0||l.scaleY==0)
            throw new java.io.IOException("Transformação inválida");
        l.opacity=(float)Math.max(0,Math.min(1,j.optDouble("opacity",1)));
        l.visible=j.optBoolean("visible",true);l.locked=j.optBoolean("locked");
        try{l.blend=Blend.valueOf(j.optString("blend","NORMAL"));}
        catch(IllegalArgumentException e){throw new java.io.IOException("Mesclagem não suportada",e);}
        l.text=j.optString("text","");
        if(l.text.length()>8192)throw new java.io.IOException("Texto muito longo");
        l.fontSize=(float)j.optDouble("fontSize",64);
        l.fontFamily=j.optString("fontFamily","sans-serif");
        l.fontBold=j.optBoolean("fontBold",false);
        l.fontItalic=j.optBoolean("fontItalic",false);
        l.letterSpacing=(float)j.optDouble("letterSpacing",0);
        l.lineSpacing=(float)j.optDouble("lineSpacing",1.16);
        l.textAlign=j.optString("textAlign","LEFT");
        if(!Float.isFinite(l.fontSize)||l.fontSize<1||l.fontSize>600||
            l.fontFamily.length()>64||!Float.isFinite(l.letterSpacing)||
            l.letterSpacing<-.2f||l.letterSpacing>1.5f||
            !Float.isFinite(l.lineSpacing)||l.lineSpacing<.5f||l.lineSpacing>3f||
            !(l.textAlign.equals("LEFT")||l.textAlign.equals("CENTER")||
              l.textAlign.equals("RIGHT")))
            throw new java.io.IOException("Parâmetros de texto inválidos");
        l.color=j.optInt("color",Color.WHITE);
        l.objectWidth=j.optInt("objectWidth",240);l.objectHeight=j.optInt("objectHeight",160);
        if(l.objectWidth<1||l.objectWidth>MAX_SIDE||l.objectHeight<1||l.objectHeight>MAX_SIDE)
            throw new java.io.IOException("Objeto inválido");
        if((l.type==Type.RASTER||l.type==Type.SMART)&&source==null)
            throw new java.io.IOException("Camada raster sem pixels");
        l.maskEnabled=j.optBoolean("maskEnabled",true);
        l.maskDensity=j.optInt("maskDensity",100);
        if(l.maskDensity<0||l.maskDensity>100)
            throw new java.io.IOException("Densidade de máscara inválida");
        l.shadowEnabled=j.optBoolean("shadowEnabled",false);
        l.shadowRadius=(float)j.optDouble("shadowRadius",5);
        l.shadowX=(float)j.optDouble("shadowX",5);
        l.shadowY=(float)j.optDouble("shadowY",5);
        l.shadowColor=j.optInt("shadowColor",Color.BLACK);
        l.shadowOpacity=(float)j.optDouble("shadowOpacity",.65);
        if(!Float.isFinite(l.shadowRadius)||l.shadowRadius<1||l.shadowRadius>16||
            !Float.isFinite(l.shadowX)||!Float.isFinite(l.shadowY)||
            Math.abs(l.shadowX)>512||Math.abs(l.shadowY)>512||
            !Float.isFinite(l.shadowOpacity)||l.shadowOpacity<0||l.shadowOpacity>1)
            throw new java.io.IOException("Sombra inválida");
        JSONArray stack=j.optJSONArray("effects");
        if(stack!=null){
            if(stack.length()>8)throw new java.io.IOException("Filtros demais");
            for(int k=0;k<stack.length();k++)
                l.effects.add(StudioLayerEffects.Effect.fromJson(stack.getJSONObject(k)));
        }
        return l;
    }
    public StudioDocument snapshot(){
        StudioDocument d=new StudioDocument(id,name,width,height,dpi);
        d.background=background;d.zoom=zoom;d.panX=panX;d.panY=panY;d.activeId=activeId;
        d.selection=selection==null?null:new RectF(selection);
        for(Layer l:layers)d.layers.add(l.copy());
        d.dirty=dirty;return d;
    }
    public void markSaved(){dirty=false;}
}
