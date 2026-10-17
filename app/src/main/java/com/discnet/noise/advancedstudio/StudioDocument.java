package com.discnet.noise.advancedstudio;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Canvas;
import android.graphics.BlendMode;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Independent, layer-based studio document. Version 1 edits transforms and
 * layer metadata non-destructively; a raster source is never overwritten.
 * All mutations and Canvas calls are performed on the UI thread in phase 1.
 */
public final class StudioDocument {
    public static final int FORMAT_VERSION=1;
    public static final int MAX_SIDE=8192,MAX_PIXELS=16_777_216,MAX_LAYERS=40;
    public enum Blend { NORMAL, MULTIPLY, SCREEN }
    public static final class Layer {
        public final String id;
        public String name;
        public Bitmap bitmap;
        public int x,y;
        public float opacity=1f;
        public boolean visible=true,locked=false;
        public Blend blend=Blend.NORMAL;
        Layer(String id,String name,Bitmap bitmap) {
            this.id=id;this.name=name;this.bitmap=bitmap;
        }
        Layer copy(){
            Layer l=new Layer(id,name,bitmap);
            l.x=x;l.y=y;l.opacity=opacity;l.visible=visible;
            l.locked=locked;l.blend=blend;return l;
        }
        public JSONObject toJson()throws Exception{
            return new JSONObject().put("id",id).put("name",name).put("x",x)
                .put("y",y).put("opacity",opacity).put("visible",visible)
                .put("locked",locked).put("blend",blend.name())
                .put("image","layers/"+id+".png");
        }
    }
    public final String id;
    public String name;
    public final int width,height;
    public final int dpi;
    public int background=Color.TRANSPARENT;
    public float zoom=1f,panX,panY;
    public String activeId;
    public boolean dirty;
    public final ArrayList<Layer> layers=new ArrayList<>();
    private final ArrayList<State> undo=new ArrayList<>(),redo=new ArrayList<>();
    private static final int MAX_HISTORY=24;
    private static final class State {
        final ArrayList<Layer> layers=new ArrayList<>();
        String activeId;
        State(StudioDocument d){
            for(Layer l:d.layers)layers.add(l.copy());
            activeId=d.activeId;
        }
    }
    public StudioDocument(String id,String name,int width,int height,int dpi) {
        if(!validSize(width,height)||dpi<1||dpi>2400)
            throw new IllegalArgumentException("Dimensões ou resolução inválidas");
        this.id=id;this.name=name;this.width=width;this.height=height;this.dpi=dpi;
    }
    public static StudioDocument blank(String name,int w,int h,boolean transparent) {
        StudioDocument d=new StudioDocument(UUID.randomUUID().toString(),
            name==null||name.trim().isEmpty()?"Sem título":name,w,h,72);
        d.background=transparent?Color.TRANSPARENT:Color.WHITE;
        return d;
    }
    public static boolean validSize(int w,int h){
        return w>=16&&h>=16&&w<=MAX_SIDE&&h<=MAX_SIDE
            &&(long)w*h<=MAX_PIXELS;
    }
    public Layer active() {
        for(Layer layer:layers)if(layer.id.equals(activeId))return layer;
        return null;
    }
    public void checkpoint() {
        undo.add(new State(this));
        if(undo.size()>MAX_HISTORY)undo.remove(0);
        redo.clear();dirty=true;
    }
    private void restore(State s){
        layers.clear();
        for(Layer l:s.layers)layers.add(l.copy());
        activeId=s.activeId;dirty=true;
    }
    public boolean canUndo(){return !undo.isEmpty();}
    public boolean canRedo(){return !redo.isEmpty();}
    public boolean undo(){
        if(undo.isEmpty())return false;
        redo.add(new State(this));restore(undo.remove(undo.size()-1));
        return true;
    }
    public boolean redo(){
        if(redo.isEmpty())return false;
        undo.add(new State(this));restore(redo.remove(redo.size()-1));
        return true;
    }
    public Layer add(String label,Bitmap source){
        if(layers.size()>=MAX_LAYERS)throw new IllegalStateException("Limite de camadas");
        // A new raster layer is real pixel storage, not an empty UI row.
        // Bound decoded data to control RAM on mid-range Android devices.
        long occupied=0;
        for(Layer old:layers)if(old.bitmap!=null)
            occupied+=(long)old.bitmap.getWidth()*old.bitmap.getHeight();
        Bitmap pixels=source==null?
            Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888):source;
        if(occupied+(long)pixels.getWidth()*pixels.getHeight()>36_000_000){
            if(source==null)pixels.recycle();
            throw new IllegalStateException("Memória de camadas: limite desta fase atingido");
        }
        checkpoint();
        Layer layer=new Layer(UUID.randomUUID().toString(),label,pixels);
        layers.add(layer);activeId=layer.id;
        return layer;
    }
    public Layer duplicate(){
        Layer current=active();if(current==null||layers.size()>=MAX_LAYERS)return null;
        checkpoint();
        Layer copy=new Layer(UUID.randomUUID().toString(),current.name+" cópia",current.bitmap);
        copy.x=current.x+18;copy.y=current.y+18;copy.visible=current.visible;
        copy.opacity=current.opacity;copy.blend=current.blend;
        layers.add(layers.indexOf(current)+1,copy);activeId=copy.id;
        return copy;
    }
    public void remove(){
        Layer selected=active();if(selected==null||selected.locked)return;
        checkpoint();int i=layers.indexOf(selected);layers.remove(i);
        activeId=layers.isEmpty()?null:layers.get(Math.min(i,layers.size()-1)).id;
    }
    public void moveOrder(int direction){
        Layer selected=active();if(selected==null||selected.locked)return;
        int i=layers.indexOf(selected),target=i+direction;
        if(target<0||target>=layers.size())return;
        checkpoint();layers.remove(i);layers.add(target,selected);
    }
    public void render(Canvas canvas) {
        // Compositing uses the same method for preview and bitmap export.
        if(Color.alpha(background)!=0)canvas.drawColor(background);
        for(Layer l:layers){
            if(!l.visible||l.bitmap==null||l.bitmap.isRecycled())continue;
            Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            paint.setAlpha(Math.round(Math.max(0,Math.min(1,l.opacity))*255));
            if(Build.VERSION.SDK_INT>=29){
                paint.setBlendMode(l.blend==Blend.MULTIPLY?BlendMode.MULTIPLY:
                    l.blend==Blend.SCREEN?BlendMode.SCREEN:BlendMode.SRC_OVER);
            }else if(l.blend!=Blend.NORMAL){
                paint.setXfermode(new PorterDuffXfermode(
                    l.blend==Blend.MULTIPLY?PorterDuff.Mode.MULTIPLY:PorterDuff.Mode.SCREEN));
            }
            canvas.drawBitmap(l.bitmap,l.x,l.y,paint);
        }
    }
    public Bitmap composite() {
        Bitmap result=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        render(new Canvas(result));return result;
    }
    public JSONObject toJson() throws Exception {
        JSONObject doc=new JSONObject();
        doc.put("format",FORMAT_VERSION).put("id",id).put("name",name)
            .put("width",width).put("height",height).put("dpi",dpi)
            .put("background",background).put("active",activeId)
            .put("zoom",zoom).put("panX",panX).put("panY",panY);
        JSONArray items=new JSONArray();
        for(Layer layer:layers)items.put(layer.toJson());
        return doc.put("layers",items);
    }
    static StudioDocument fromJson(JSONObject data) throws Exception{
        if(data.getInt("format")!=FORMAT_VERSION)
            throw new java.io.IOException("Versão .noise incompatível");
        StudioDocument d=new StudioDocument(data.getString("id"),
            data.getString("name"),data.getInt("width"),
            data.getInt("height"),data.optInt("dpi",72));
        d.background=data.optInt("background",Color.TRANSPARENT);
        d.activeId=data.optString("active",null);
        d.zoom=(float)data.optDouble("zoom",1);
        d.panX=(float)data.optDouble("panX",0);
        d.panY=(float)data.optDouble("panY",0);
        return d;
    }
    static Layer decodeLayer(JSONObject data,Bitmap source)throws Exception{
        Layer l=new Layer(data.getString("id"),data.getString("name"),source);
        l.x=data.optInt("x");l.y=data.optInt("y");
        l.opacity=(float)Math.max(0,Math.min(1,data.optDouble("opacity",1)));
        l.visible=data.optBoolean("visible",true);
        l.locked=data.optBoolean("locked");
        try{l.blend=Blend.valueOf(data.optString("blend","NORMAL"));}
        catch(IllegalArgumentException e){throw new java.io.IOException("Mesclagem não suportada",e);}
        return l;
    }
    public StudioDocument snapshot(){
        StudioDocument d=new StudioDocument(id,name,width,height,dpi);
        d.background=background;d.zoom=zoom;d.panX=panX;d.panY=panY;
        d.activeId=activeId;
        for(Layer layer:layers)d.layers.add(layer.copy());
        d.dirty=dirty;return d;
    }
    public void markSaved(){dirty=false;}
}
