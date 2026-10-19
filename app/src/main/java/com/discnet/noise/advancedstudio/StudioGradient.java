package com.discnet.noise.advancedstudio;

import android.graphics.Color;
import org.json.*;
import java.util.*;

/** Portable, editable gradient definition shared by raster and layer styles.
 * Geometry follows the five Photoshop / PhotoCraft families. Color and opacity
 * are interpolated in straight-alpha RGBA; callers composite with SRC_OVER.
 */
public final class StudioGradient {
    public static final String[] TYPES={"LINEAR","RADIAL","ANGLE","REFLECTED","DIAMOND"};
    public String type="LINEAR";
    public boolean reverse=false,dither=false,align=true;
    public float angle=90,scale=100,offsetX=0,offsetY=0;
    public float[] positions={0,1};
    public int[] colors={0xff9158db,0xffffb878};
    public float[] midpoints={.5f};
    public StudioGradient copy(){
        StudioGradient g=new StudioGradient();
        g.type=type;g.reverse=reverse;g.dither=dither;g.align=align;
        g.angle=angle;g.scale=scale;g.offsetX=offsetX;g.offsetY=offsetY;
        g.positions=positions.clone();g.colors=colors.clone();g.midpoints=midpoints.clone();
        return g;
    }
    public static StudioGradient of(int from,int to){
        StudioGradient g=new StudioGradient();g.colors=new int[]{from,to};return g;
    }
    public void validate(){
        if(!Arrays.asList(TYPES).contains(type)||!Float.isFinite(angle)||
            !Float.isFinite(scale)||scale<1||scale>1000||
            !Float.isFinite(offsetX)||!Float.isFinite(offsetY)||
            Math.abs(offsetX)>200||Math.abs(offsetY)>200||
            positions.length!=colors.length||colors.length<2||colors.length>12||
            midpoints.length!=colors.length-1)
            throw new IllegalArgumentException("Degradê inválido");
        for(int i=0;i<positions.length;i++){
            float v=positions[i];
            if(!Float.isFinite(v)||v<0||v>1||(i>0&&v<=positions[i-1]))
                throw new IllegalArgumentException("Posições de degradê inválidas");
            if(i<midpoints.length&&(!Float.isFinite(midpoints[i])||
                midpoints[i]<.05f||midpoints[i]>.95f))
                throw new IllegalArgumentException("Pontos médios inválidos");
        }
    }
    public JSONObject toJson()throws JSONException{
        JSONObject j=new JSONObject().put("type",type).put("reverse",reverse)
            .put("dither",dither).put("align",align).put("angle",angle)
            .put("scale",scale).put("offsetX",offsetX).put("offsetY",offsetY);
        JSONArray stops=new JSONArray(),mids=new JSONArray();
        for(int i=0;i<colors.length;i++)
            stops.put(new JSONObject().put("at",positions[i]).put("color",colors[i]));
        for(float midpoint:midpoints)mids.put(midpoint);
        return j.put("stops",stops).put("midpoints",mids);
    }
    public static StudioGradient fromJson(JSONObject j)throws JSONException{
        StudioGradient g=new StudioGradient();
        g.type=j.optString("type","LINEAR");
        g.reverse=j.optBoolean("reverse",false);
        g.dither=j.optBoolean("dither",false);g.align=j.optBoolean("align",true);
        g.angle=(float)j.optDouble("angle",90);g.scale=(float)j.optDouble("scale",100);
        g.offsetX=(float)j.optDouble("offsetX",0);g.offsetY=(float)j.optDouble("offsetY",0);
        JSONArray st=j.optJSONArray("stops");
        if(st!=null){
            if(st.length()<2||st.length()>12)throw new JSONException("Pontos demais");
            g.positions=new float[st.length()];g.colors=new int[st.length()];
            for(int i=0;i<st.length();i++){
                JSONObject o=st.getJSONObject(i);
                g.positions[i]=(float)o.getDouble("at");g.colors[i]=o.getInt("color");
            }
        }
        JSONArray ms=j.optJSONArray("midpoints");
        if(ms!=null){
            if(ms.length()!=g.colors.length-1)throw new JSONException("Pontos médios inválidos");
            g.midpoints=new float[ms.length()];
            for(int i=0;i<ms.length();i++)g.midpoints[i]=(float)ms.getDouble(i);
        }else{
            g.midpoints=new float[g.colors.length-1];Arrays.fill(g.midpoints,.5f);
        }
        g.validate();return g;
    }
    public void insert(float position,int color){
        if(colors.length>=12)throw new IllegalStateException("Máximo de 12 pontos");
        position=clamp(position);
        int index=0;while(index<positions.length&&positions[index]<position)index++;
        float low=index==0?0:positions[index-1],high=index==positions.length?1:positions[index];
        position=Math.max(low+.001f,Math.min(high-.001f,position));
        if(position<=low||position>=high)throw new IllegalArgumentException("Sem espaço");
        float[] np=new float[positions.length+1],nm=new float[midpoints.length+1];
        int[] nc=new int[colors.length+1];
        for(int i=0,j=0;i<np.length;i++){
            if(i==index){np[i]=position;nc[i]=color;}
            else{np[i]=positions[j];nc[i]=colors[j++];}
        }
        Arrays.fill(nm,.5f);
        positions=np;colors=nc;midpoints=nm;
    }
    public void remove(int index){
        if(colors.length<=2)throw new IllegalStateException("Mínimo de 2 pontos");
        float[] np=new float[positions.length-1],nm=new float[midpoints.length-1];
        int[] nc=new int[colors.length-1];
        for(int i=0,j=0;i<positions.length;i++){
            if(i==index)continue;
            np[j]=positions[i];nc[j]=colors[i];j++;
        }
        Arrays.fill(nm,.5f);positions=np;colors=nc;midpoints=nm;
    }
    private static float clamp(float x){return Math.max(0,Math.min(1,x));}
    private static int interpolate(int a,int b,float t){
        return Color.argb(
            Math.round(Color.alpha(a)*(1-t)+Color.alpha(b)*t),
            Math.round(Color.red(a)*(1-t)+Color.red(b)*t),
            Math.round(Color.green(a)*(1-t)+Color.green(b)*t),
            Math.round(Color.blue(a)*(1-t)+Color.blue(b)*t));
    }
    public int color(float t,int x,int y){
        t=clamp(reverse?1-t:t);
        int left=0;
        while(left+1<positions.length-1&&t>positions[left+1])left++;
        float start=positions[left],stop=positions[left+1];
        float u=clamp((t-start)/Math.max(.0001f,stop-start));
        float mid=midpoints[left];
        float amount=u<=mid?u*.5f/mid:.5f+(u-mid)*.5f/(1-mid);
        int c=interpolate(colors[left],colors[left+1],amount);
        if(!dither)return c;
        int noise=((x*73856093)^(y*19349663))&255;
        int shift=noise>=192?1:noise<64?-1:0;
        return Color.argb(Color.alpha(c),
            Math.max(0,Math.min(255,Color.red(c)+shift)),
            Math.max(0,Math.min(255,Color.green(c)+shift)),
            Math.max(0,Math.min(255,Color.blue(c)+shift)));
    }
    /** Drag geometry: the start/end points are editable handles, like PhotoCraft.
     * Radial/angle/reflected/diamond use the start point as the centre.
     */
    public float coordinateFromDrag(float x,float y,float startX,float startY,
                                    float endX,float endY){
        float dx=endX-startX,dy=endY-startY;
        float length=Math.max(.001f,(float)Math.hypot(dx,dy));
        float ux=dx/length,uy=dy/length;
        float px=x-startX-offsetX*length/100f;
        float py=y-startY-offsetY*length/100f;
        float along=px*ux+py*uy,across=px*(-uy)+py*ux;
        float reach=length*Math.max(.01f,scale/100f);
        switch(type){
            case "RADIAL":return clamp((float)Math.hypot(px,py)/reach);
            case "ANGLE":return clamp((float)((Math.atan2(across,along)/
                (2*Math.PI)+1)%1));
            case "REFLECTED":return clamp(Math.abs(along)/reach);
            case "DIAMOND":return clamp((Math.abs(along)+Math.abs(across))/reach);
            default:return clamp(along/reach);
        }
    }
    /** A normalized point in a layer/object rectangle. Offset is percent
     * of dimensions; linear projection spans the full center chord. */
    public float coordinate(float x,float y,float width,float height){
        float w=Math.max(1,width),h=Math.max(1,height);
        float cx=w*(.5f+offsetX/100f),cy=h*(.5f+offsetY/100f);
        float dx=x-cx,dy=y-cy;
        float radians=(float)Math.toRadians(angle);
        float cs=(float)Math.cos(radians),sn=(float)Math.sin(radians);
        float along=dx*cs-dy*sn,across=dx*sn+dy*cs;
        float chord=Math.min(w/Math.max(.0001f,Math.abs(cs)),
                             h/Math.max(.0001f,Math.abs(sn)));
        float reach=(float)Math.hypot(w*cs,h*sn);
        float zoom=Math.max(.01f,scale/100f);
        switch(type){
            case "RADIAL":return clamp((float)Math.hypot(dx,dy)/(reach*.5f*zoom));
            case "ANGLE":return clamp((float)((radians-Math.atan2(-dy,dx))/
                          (2*Math.PI)+2)%1f);
            case "REFLECTED":return clamp(Math.abs(along)/(chord*.5f*zoom));
            case "DIAMOND":return clamp((Math.abs(along)+Math.abs(across))/(reach*.5f*zoom));
            default:return clamp(.5f+along/(chord*zoom));
        }
    }
}
