package com.discnet.noise;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * Reusable hammered-glass UI material. For the Home, each material samples a
 * downsampled/blurred copy of the authored backdrop directly behind its view.
 * Microscopic multi-octave displacement alters transmitted light, not the
 * edited photo. A single cached Bitmap is rebuilt only when size or the
 * underlying composition changes; drawing itself is hardware-accelerated.
 */
final class TexturedGlass extends Drawable {
    private final OrganicBackdrop backdrop;
    private final View host;
    private final float radiusPx,transmission;
    private final int tint;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Matrix matrix=new Matrix();
    private final RectF rect=new RectF();
    private final int[] hostLocation=new int[2],sourceLocation=new int[2];
    private Bitmap texture;
    private BitmapShader materialShader;
    private int lastW,lastH,lastSourceVersion=-1,lastX=Integer.MIN_VALUE,lastY=Integer.MIN_VALUE;
    private int drawableAlpha=255;
    private LinearGradient reflection;
    TexturedGlass(Context c,OrganicBackdrop backdrop,View host,float radiusDp,
                  int tint,float transmission){
        this.backdrop=backdrop;this.host=host;this.tint=tint;
        this.radiusPx=EditorTheme.dp(c,radiusDp);
        this.transmission=Math.max(.08f,Math.min(.70f,transmission));
        if(backdrop!=null)backdrop.subscribe(this);
    }
    float radiusDp(){return radiusPx/host.getResources().getDisplayMetrics().density;}
    boolean samplesBackground(){return backdrop!=null;}
    private static int hash(int x,int y){
        int n=x*374761393+y*668265263;
        n=(n^(n>>>13))*1274126177;
        return n^(n>>>16);
    }
    private static float noise(float x,float y){
        int ix=(int)Math.floor(x),iy=(int)Math.floor(y);
        float fx=x-ix,fy=y-iy;
        fx=fx*fx*(3f-2f*fx);fy=fy*fy*(3f-2f*fy);
        float a=(hash(ix,iy)&0xffff)/65535f;
        float b=(hash(ix+1,iy)&0xffff)/65535f;
        float c=(hash(ix,iy+1)&0xffff)/65535f;
        float d=(hash(ix+1,iy+1)&0xffff)/65535f;
        return (a+(b-a)*fx)*(1f-fy)+(c+(d-c)*fx)*fy;
    }
    private static int clamp(int v){return Math.max(0,Math.min(255,v));}
    private int[] position(){
        if(host.isAttachedToWindow()&&backdrop!=null&&backdrop.isAttachedToWindow()){
            host.getLocationOnScreen(hostLocation);
            backdrop.getLocationOnScreen(sourceLocation);
            return new int[]{hostLocation[0]-sourceLocation[0],
                hostLocation[1]-sourceLocation[1]};
        }
        return new int[]{Math.round(host.getX()),Math.round(host.getY())};
    }
    private void ensureTexture(int width,int height){
        int[] pos=position();
        Bitmap source=backdrop==null?null:backdrop.blurredSnapshot();
        int generation=backdrop==null?0:backdrop.materialVersion();
        if(texture!=null&&!texture.isRecycled()&&lastW==width&&lastH==height
            &&lastSourceVersion==generation&&lastX==pos[0]&&lastY==pos[1])return;
        int w=Math.max(1,Math.min(288,(width+2)/3));
        int h=Math.max(1,Math.min(216,(height+2)/3));
        Bitmap next=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        int[] pixels=new int[w*h];
        float sx=width/(float)w,sy=height/(float)h;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float gx=pos[0]+(x+.5f)*sx,gy=pos[1]+(y+.5f)*sy;
            // Broad hammered-cell contours + a finer surface ripple.
            float low=noise(gx*.040f+23.1f,gy*.040f+7.8f);
            float mid=noise(gx*.125f+15.6f,gy*.125f+49.2f);
            float micro=noise(gx*.47f+11.3f,gy*.47f+5.7f);
            float around=noise(gx*.040f+23.9f,gy*.040f+8.1f);
            float shade=(low-around)*27f+(mid-.5f)*7f+(micro-.5f)*4f;
            int color;
            if(source!=null&&!source.isRecycled()){
                int px=Math.max(0,Math.min(source.getWidth()-1,
                    Math.round((gx+(low-.5f)*4f+(mid-.5f)*1.8f)/8f)));
                int py=Math.max(0,Math.min(source.getHeight()-1,
                    Math.round((gy+(around-.5f)*4f)/8f)));
                color=source.getPixel(px,py);
            }else color=0xff25202e;
            float passing=transmission*(.88f+low*.21f);
            int red=clamp(Math.round(Color.red(tint)*(1-passing)+Color.red(color)*passing+shade));
            int green=clamp(Math.round(Color.green(tint)*(1-passing)+Color.green(color)*passing+shade));
            int blue=clamp(Math.round(Color.blue(tint)*(1-passing)+Color.blue(color)*passing+shade*1.1f));
            pixels[y*w+x]=Color.rgb(red,green,blue);
        }
        next.setPixels(pixels,0,w,0,0,w,h);
        if(texture!=null&&!texture.isRecycled())texture.recycle();
        texture=next;materialShader=new BitmapShader(texture,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
        lastW=width;lastH=height;lastSourceVersion=generation;lastX=pos[0];lastY=pos[1];
        reflection=new LinearGradient(0,0,0,height,
            new int[]{0x1cffffff,0x05ffffff,0x00000000,0x0cffffff},
            new float[]{0f,.19f,.67f,1f},Shader.TileMode.CLAMP);
    }
    @Override protected void onBoundsChange(Rect bounds){
        lastW=0;lastH=0;reflection=null;
    }
    @Override public void draw(Canvas canvas){
        Rect b=getBounds();
        if(b.width()<1||b.height()<1)return;
        ensureTexture(b.width(),b.height());
        rect.set(b);
        float radius=Math.min(radiusPx,Math.min(b.width(),b.height())*.5f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);paint.setAlpha(drawableAlpha);
        float scaleX=b.width()/(float)texture.getWidth();
        float scaleY=b.height()/(float)texture.getHeight();
        matrix.reset();matrix.setScale(scaleX,scaleY);
        matrix.postTranslate(b.left,b.top);
        materialShader.setLocalMatrix(matrix);
        paint.setShader(materialShader);
        canvas.drawRoundRect(rect,radius,radius,paint);
        paint.setShader(reflection);paint.setAlpha(drawableAlpha);
        canvas.drawRoundRect(rect,radius,radius,paint);
        paint.setShader(null);paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(.7f,host.getResources().getDisplayMetrics().density*.75f));
        paint.setColor(0x6bdbcede);paint.setAlpha(drawableAlpha);
        canvas.drawRoundRect(rect,radius,radius,paint);
        paint.setStyle(Paint.Style.FILL);paint.setShader(null);
    }
    @Override public void setAlpha(int alpha){drawableAlpha=alpha;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter colorFilter){
        paint.setColorFilter(colorFilter);invalidateSelf();
    }
    @Override public int getOpacity(){return PixelFormat.OPAQUE;}
}