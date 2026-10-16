package com.discnet.noise;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;

/**
 * Palette strictly scoped to the orbital menus. Cached stipple is drawn inside
 * the actual filled geometry, never as a transparent glass overlay.
 */
final class OrbitalMaterial extends Drawable {
    static final int WHITE=0xfffaf8ff;
    static final int BLACK=0xff09070e;
    static final int PURPLE=0xff7235ae;
    static final int DARK_PURPLE=0xff1c0d30;
    static final int DEEP_PURPLE=0xff30144e;
    static final int MUTED=0xffd9c7ee;

    private static BitmapShader grains;
    private static final ColorFilter LIGHT_GRAIN =
        new PorterDuffColorFilter(0xff080612,PorterDuff.Mode.SRC_IN);
    private static final ColorFilter DARK_GRAIN =
        new PorterDuffColorFilter(0xfff7efff,PorterDuff.Mode.SRC_IN);
    private static synchronized BitmapShader grainShader() {
        if(grains==null){
            final int side=64;
            int[] pixels=new int[side*side];
            int seed=0x4e4f4953;
            for(int i=0;i<pixels.length;i++){
                seed^=seed<<13;seed^=seed>>>17;seed^=seed<<5;
                int chance=(seed>>>24)&255;
                int a=chance<46?75:chance<117?37:chance<188?16:0;
                pixels[i]=(a<<24)|0x00ffffff;
            }
            Bitmap bitmap=Bitmap.createBitmap(pixels,side,side,Bitmap.Config.ARGB_8888);
            grains=new BitmapShader(bitmap,Shader.TileMode.REPEAT,Shader.TileMode.REPEAT);
        }
        return grains;
    }

    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density,radius;
    private final int base;
    private final boolean glow;
    private float strength;
    OrbitalMaterial(Context context,int baseColor,float radiusDp,boolean glowing){
        density=context.getResources().getDisplayMetrics().density;
        radius=radiusDp*density;
        base=baseColor;glow=glowing;
    }
    float cornerRadius(){return radius;}
    int baseColor(){return base;}
    void setStrength(float amount){
        float value=Math.max(0f,Math.min(1f,amount));
        if(Math.abs(value-strength)>.002f){strength=value;invalidateSelf();}
    }
    private static int blend(int a,int b,float amount){
        return Color.rgb(
            Math.round(Color.red(a)+(Color.red(b)-Color.red(a))*amount),
            Math.round(Color.green(a)+(Color.green(b)-Color.green(a))*amount),
            Math.round(Color.blue(a)+(Color.blue(b)-Color.blue(a))*amount));
    }
    int filledColor(){return blend(base,WHITE,strength);}
    @Override public void draw(Canvas canvas){
        Rect b=getBounds();if(b.isEmpty())return;
        float left=b.left+2f*density,top=b.top+2f*density;
        float right=b.right-2f*density,bottom=b.bottom-2f*density;
        if(right<=left||bottom<=top)return;
        int fill=filledColor();
        boolean light=Color.red(fill)+Color.green(fill)+Color.blue(fill)>510;
        float emphasis=glow?(.24f+.64f*strength):0f;
        paint.setShader(null);paint.setColorFilter(null);
        paint.setStyle(Paint.Style.STROKE);
        if(glow){
            paint.setColor(Color.argb(Math.round(85f*emphasis),255,255,255));
            paint.setStrokeWidth(4.5f*density);
            canvas.drawRoundRect(left,top,right,bottom,radius,radius,paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(fill);
        canvas.drawRoundRect(left,top,right,bottom,radius,radius,paint);
        // Visible fine grain is stable across frames and shared among buttons.
        paint.setShader(grainShader());
        paint.setColorFilter(light?LIGHT_GRAIN:DARK_GRAIN);
        paint.setAlpha(light?145:210);
        canvas.drawRoundRect(left,top,right,bottom,radius,radius,paint);
        paint.setShader(null);paint.setColorFilter(null);paint.setAlpha(255);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(density);
        paint.setColor(light?0xffe0d5eb:0xff8055aa);
        canvas.drawRoundRect(left,top,right,bottom,radius,radius,paint);
        if(glow&&strength>.01f){
            paint.setStrokeWidth(1.25f*density);
            paint.setColor(Color.argb(Math.round(235f*strength),255,255,255));
            canvas.drawRoundRect(left,top,right,bottom,radius,radius,paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
