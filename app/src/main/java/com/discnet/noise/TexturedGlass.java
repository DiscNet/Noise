package com.discnet.noise;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * Fine hammered-glass UI material. A shared, pre-blurred background is
 * transmitted through a coherent micro-relief field, not enlarged cloudy
 * patches. The relief alters refraction and diffuse specular response only.
 *
 * Rendering is confined to the UI and cannot change edited/captured pixels.
 */
final class TexturedGlass extends Drawable {
    private final OrganicBackdrop backdrop;
    private final View host;
    private final float radiusPx, transmission;
    private final int tint;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Matrix matrix=new Matrix();
    private final RectF rect=new RectF();
    private final int[] hostLocation=new int[2], sourceLocation=new int[2];
    private Bitmap texture;
    private BitmapShader materialShader;
    private LinearGradient reflection;
    private int lastW,lastH,lastSourceVersion=-1,lastX=Integer.MIN_VALUE,lastY=Integer.MIN_VALUE;
    private int posX,posY,drawableAlpha=255;

    TexturedGlass(Context context,OrganicBackdrop backdrop,View host,
                  float radiusDp,int tint,float transmission){
        this.backdrop=backdrop;this.host=host;this.tint=tint;
        radiusPx=EditorTheme.dp(context,radiusDp);
        this.transmission=Math.max(.14f,Math.min(.76f,transmission));
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
        float dx=x-ix,dy=y-iy;
        dx=dx*dx*(3f-2f*dx);dy=dy*dy*(3f-2f*dy);
        float a=(hash(ix,iy)&65535)/65535f;
        float b=(hash(ix+1,iy)&65535)/65535f;
        float c=(hash(ix,iy+1)&65535)/65535f;
        float d=(hash(ix+1,iy+1)&65535)/65535f;
        return (a+(b-a)*dx)*(1f-dy)+(c+(d-c)*dx)*dy;
    }
    private static int clamp(int v){return Math.max(0,Math.min(255,v));}
    private void resolvePosition(){
        if(host.isAttachedToWindow()&&backdrop!=null&&backdrop.isAttachedToWindow()){
            host.getLocationOnScreen(hostLocation);
            backdrop.getLocationOnScreen(sourceLocation);
            posX=hostLocation[0]-sourceLocation[0];
            posY=hostLocation[1]-sourceLocation[1];
        }else{
            posX=Math.round(host.getX());
            posY=Math.round(host.getY());
        }
    }
    private static int bilinear(int[] pixels,int width,int height,float x,float y){
        x=Math.max(0f,Math.min(width-1.001f,x));
        y=Math.max(0f,Math.min(height-1.001f,y));
        int left=(int)x,top=(int)y;
        int right=Math.min(width-1,left+1),bottom=Math.min(height-1,top+1);
        float fx=x-left,fy=y-top;
        int a=pixels[top*width+left],b=pixels[top*width+right];
        int c=pixels[bottom*width+left],d=pixels[bottom*width+right];
        int red=Math.round((Color.red(a)*(1-fx)+Color.red(b)*fx)*(1-fy)
            +(Color.red(c)*(1-fx)+Color.red(d)*fx)*fy);
        int green=Math.round((Color.green(a)*(1-fx)+Color.green(b)*fx)*(1-fy)
            +(Color.green(c)*(1-fx)+Color.green(d)*fx)*fy);
        int blue=Math.round((Color.blue(a)*(1-fx)+Color.blue(b)*fx)*(1-fy)
            +(Color.blue(c)*(1-fx)+Color.blue(d)*fx)*fy);
        return Color.rgb(clamp(red),clamp(green),clamp(blue));
    }
    private void ensureTexture(int width,int height){
        resolvePosition();
        Bitmap blur=backdrop==null?null:backdrop.blurredSnapshot();
        int sourceVersion=backdrop==null?0:backdrop.materialVersion();
        if(texture!=null&&!texture.isRecycled()
            &&lastW==width&&lastH==height&&lastSourceVersion==sourceVersion
            &&lastX==posX&&lastY==posY)return;

        // 2x downsampled material, max ~390x270: fine microrelief without
        // allocating a full-screen bitmap or recalculating every animation frame.
        int w=Math.max(1,Math.min(390,(width+1)/2));
        int h=Math.max(1,Math.min(270,(height+1)/2));
        int[] output=new int[w*h];
        int bw=0,bh=0;int[] backdropPixels=null;
        if(blur!=null&&!blur.isRecycled()){
            bw=blur.getWidth();bh=blur.getHeight();
            backdropPixels=new int[bw*bh];
            blur.getPixels(backdropPixels,0,bw,0,0,bw,bh);
        }
        float sx=width/(float)w,sy=height/(float)h;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float px=posX+(x+.5f)*sx,py=posY+(y+.5f)*sy;
            // Sub-4px irregular relief; no coarse 25px cloudy blotches.
            float large=noise(px*.185f+17.1f,py*.185f+8.4f);
            float fine=noise(px*.43f+33.5f,py*.43f+13.7f);
            float dx=noise((px+1.4f)*.185f+17.1f,py*.185f+8.4f)
                -noise((px-1.4f)*.185f+17.1f,py*.185f+8.4f);
            float dy=noise(px*.185f+17.1f,(py+1.4f)*.185f+8.4f)
                -noise(px*.185f+17.1f,(py-1.4f)*.185f+8.4f);
            float specular=Math.max(0f,dx*.80f-dy*.48f);
            float displacement=.90f; // under one physical display pixel
            int sampled=backdropPixels==null?0xff292431:
                bilinear(backdropPixels,bw,bh,
                    (px+dx*displacement)/4f,(py+dy*displacement)/4f);
            float mixing=transmission*(.96f+.07f*(large-.5f));
            float relief=(large-.5f)*4.5f+(fine-.5f)*3.0f
                +specular*13f;
            int red=clamp(Math.round(Color.red(tint)*(1-mixing)
                +Color.red(sampled)*mixing+relief));
            int green=clamp(Math.round(Color.green(tint)*(1-mixing)
                +Color.green(sampled)*mixing+relief));
            int blue=clamp(Math.round(Color.blue(tint)*(1-mixing)
                +Color.blue(sampled)*mixing+relief*1.08f));
            output[y*w+x]=Color.rgb(red,green,blue);
        }
        Bitmap next=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        next.setPixels(output,0,w,0,0,w,h);
        if(texture!=null&&!texture.isRecycled())texture.recycle();
        texture=next;
        materialShader=new BitmapShader(texture,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
        lastW=width;lastH=height;lastSourceVersion=sourceVersion;
        lastX=posX;lastY=posY;
        reflection=new LinearGradient(0,0,width,height,
            new int[]{0x1affffff,0x05f0e4ff,0x00000000,0x07ffffff},
            new float[]{0f,.22f,.68f,1f},Shader.TileMode.CLAMP);
    }
    @Override protected void onBoundsChange(Rect bounds){
        lastW=0;lastH=0;reflection=null;
    }
    @Override public void draw(Canvas canvas){
        Rect bounds=getBounds();
        if(bounds.width()<1||bounds.height()<1)return;
        ensureTexture(bounds.width(),bounds.height());
        rect.set(bounds);
        float radius=Math.min(radiusPx,Math.min(bounds.width(),bounds.height())*.5f);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(drawableAlpha);
        matrix.reset();
        matrix.setScale(bounds.width()/(float)texture.getWidth(),
            bounds.height()/(float)texture.getHeight());
        matrix.postTranslate(bounds.left,bounds.top);
        materialShader.setLocalMatrix(matrix);
        paint.setShader(materialShader);
        canvas.drawRoundRect(rect,radius,radius,paint);
        paint.setColor(Color.WHITE);
        paint.setShader(reflection);
        canvas.drawRoundRect(rect,radius,radius,paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(.6f,
            host.getResources().getDisplayMetrics().density*.62f));
        paint.setColor(0x53ddd6e2);
        canvas.drawRoundRect(rect,radius,radius,paint);
        paint.setStyle(Paint.Style.FILL);
    }
    @Override public void setAlpha(int alpha){drawableAlpha=alpha;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
