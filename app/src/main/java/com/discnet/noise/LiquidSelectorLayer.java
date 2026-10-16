package com.discnet.noise;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;

/**
 * Orbit-only ink field. Two density masses and a temporary neck are warped and
 * eroded before thresholding, just like Home's implicit fluid geometry.
 *
 * We rasterize the small LOCAL bridge as one anti-aliased Canvas path. The
 * contour is derived from the same sampled scalar field (marching squares),
 * not a fixed Bezier capsule with a noise/alpha decoration over it.
 * No Home renderer, shader, preferences, or gesture handler is touched.
 */
final class LiquidSelectorLayer {
    private final Paint pigment = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fringe = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path inkPath = new Path();
    private final Path edgePath = new Path();
    private final float density;
    private float[] samples = new float[0];
    private final float[] cornerX = new float[4], cornerY = new float[4], cornerV = new float[4];
    private final float[] crossX = new float[4], crossY = new float[4];
    private boolean connected;
    private float displayedProgress;

    LiquidSelectorLayer(Context context) {
        density = context.getResources().getDisplayMetrics().density;
        pigment.setStyle(Paint.Style.FILL);
        fringe.setStyle(Paint.Style.STROKE);
        fringe.setStrokeWidth(2.6f * density);
        fringe.setStrokeJoin(Paint.Join.ROUND);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(.95f * density);
        rim.setStrokeCap(Paint.Cap.ROUND);
        rim.setStrokeJoin(Paint.Join.ROUND);
    }

    boolean connected() { return connected; }
    float progress() { return displayedProgress; }

    static float smooth(float a, float b, float x) {
        float p = Math.max(0f, Math.min(1f, (x-a)/(b-a)));
        return p*p*(3f-2f*p);
    }
    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
    // A fixed seeded, interpolated lattice gives coherent material coordinates:
    // reversing the wheel follows the same contour rather than changing noise.
    private static float hash(int x, int y) {
        int n = x*0x1f123bb5 ^ y*0x5f356495 ^ 0x4e4f4953;
        n ^= n >>> 16; n *= 0x7feb352d;
        n ^= n >>> 15; n *= 0x846ca68b;
        n ^= n >>> 16;
        return (n & 0x7fffffff) / 1073741824f - 1f;
    }
    static float coherentNoise(float x, float y) {
        int ix=(int)Math.floor(x), iy=(int)Math.floor(y);
        float fx=x-ix, fy=y-iy;
        float u=fx*fx*fx*(fx*(fx*6f-15f)+10f);
        float v=fy*fy*fy*(fy*(fy*6f-15f)+10f);
        float top=hash(ix,iy)+(hash(ix+1,iy)-hash(ix,iy))*u;
        float bottom=hash(ix,iy+1)+(hash(ix+1,iy+1)-hash(ix,iy+1))*u;
        return top+(bottom-top)*v;
    }
    private static float blob(float x,float y,float center,float rx,float ry) {
        float u=(x-center)/rx, v=y/ry;
        float support=Math.max(0f,1f-u*u-v*v);
        return support*support*support*1.35f;
    }

    private float densityAt(float x,float y,float distance,float radius,float p,float time) {
        float unitX=x/radius, unitY=y/radius;
        float macroX=coherentNoise(unitX*2.0f+3.7f+time*.075f,unitY*2.0f-11.0f-time*.046f);
        float macroY=coherentNoise(unitX*2.0f-9.3f-time*.064f,unitY*2.0f+5.1f+time*.058f);
        float mesoX=coherentNoise(unitX*5.3f+macroX*.65f+time*.14f,unitY*5.3f+1.7f);
        float mesoY=coherentNoise(unitX*5.3f+8.5f,unitY*5.3f+macroY*.63f-time*.13f);
        float qx=x+radius*(.095f*macroX+.035f*mesoX);
        float qy=y+radius*(.095f*macroY+.035f*mesoY);

        float a=1f-smooth(0f,1f,p), b=1f-a;
        float ra=radius*(.58f+.42f*a), rb=radius*(.58f+.42f*b);
        float field=blob(qx,qy,0f,ra,ra*.92f)+blob(qx,qy,distance,rb,rb*.92f);

        // The connecting neck is density, not a painted connecting line.
        // Its amplitude builds and dissolves smoothly as the selection transfers.
        float neckGate=smooth(.045f,.24f,p)*(1f-smooth(.76f,.955f,p));
        float s=qx/distance;
        float ends=smooth(-.14f,.11f,s)*(1f-smooth(.89f,1.14f,s));
        float neckWidth=radius*(.26f+.20f*(float)Math.sin(Math.PI*p));
        float transverse=qy/(neckWidth*(.80f+.20f*(1f-Math.abs(2f*clamp(s,0f,1f)-1f))));
        field+=.91f*neckGate*ends*(float)Math.exp(-1.75f*transverse*transverse);

        // Genuine chipped silhouette: perturb the density isovalue BEFORE
        // choosing ink/paper. Notches and surviving peaks are real geometry.
        float wave=coherentNoise(qx/radius*7.0f+12.7f,qy/radius*7.0f-6.3f);
        float chip=coherentNoise(qx/radius*16.0f-4.8f,qy/radius*16.0f+9.1f);
        float pits=smooth(.10f,.73f,coherentNoise(qx/radius*24.0f+3.1f,
                                                  qy/radius*24.0f-7.7f));
        float isovalue=.355f+.017f+.044f*wave+.030f*chip+.035f*pits*pits;
        return field-isovalue;
    }

    private void cell(float left,float top,float step,int ix,int iy,int columns) {
        float right=left+step, bottom=top+step;
        int row=iy*columns;
        cornerX[0]=left;  cornerY[0]=top;    cornerV[0]=samples[row+ix];
        cornerX[1]=right; cornerY[1]=top;    cornerV[1]=samples[row+ix+1];
        cornerX[2]=right; cornerY[2]=bottom; cornerV[2]=samples[row+columns+ix+1];
        cornerX[3]=left;  cornerY[3]=bottom; cornerV[3]=samples[row+columns+ix];
        int crossings=0;
        boolean started=false;
        for(int j=0;j<4;j++){
            int k=(j+1)&3;
            boolean inside=cornerV[j]>=0f, nextInside=cornerV[k]>=0f;
            if(inside){
                if(!started){inkPath.moveTo(cornerX[j],cornerY[j]);started=true;}
                else inkPath.lineTo(cornerX[j],cornerY[j]);
            }
            if(inside!=nextInside){
                float t=clamp(cornerV[j]/(cornerV[j]-cornerV[k]),0f,1f);
                float cx=cornerX[j]+(cornerX[k]-cornerX[j])*t;
                float cy=cornerY[j]+(cornerY[k]-cornerY[j])*t;
                if(!started){inkPath.moveTo(cx,cy);started=true;}
                else inkPath.lineTo(cx,cy);
                crossX[crossings]=cx;crossY[crossings]=cy;crossings++;
            }
        }
        if(started)inkPath.close();
        if(crossings==2){
            edgePath.moveTo(crossX[0],crossY[0]);
            edgePath.lineTo(crossX[1],crossY[1]);
        } else if(crossings==4){
            // Ambiguous saddle cells have two arcs; keep them disconnected
            // rather than forcing a topology-breaking diagonal shortcut.
            edgePath.moveTo(crossX[0],crossY[0]);
            edgePath.lineTo(crossX[1],crossY[1]);
            edgePath.moveTo(crossX[2],crossY[2]);
            edgePath.lineTo(crossX[3],crossY[3]);
        }
    }

    void draw(Canvas canvas,float ax,float ay,float bx,float by,
              float progress,float radius){
        displayedProgress=clamp(progress,0f,1f);
        connected=ValueAnimator.areAnimatorsEnabled()
            &&displayedProgress>.002f&&displayedProgress<.998f;
        if(!connected||radius<=0f)return;
        float dx=bx-ax,dy=by-ay;
        float distance=(float)Math.hypot(dx,dy);
        if(distance<1f){connected=false;return;}

        float p=displayedProgress;
        float visible=smooth(0f,.19f,p)*(1f-smooth(.81f,1f,p));
        if(visible<.003f)return;
        float time=SystemClock.uptimeMillis()*.001f;
        float step=Math.max(1.45f*density,radius/19f);
        float minX=-radius*1.48f,minY=-radius*1.55f;
        int columns=Math.max(3,Math.min(180,2+(int)Math.ceil((distance+radius*2.96f)/step)));
        int rows=Math.max(3,Math.min(85,2+(int)Math.ceil(radius*3.10f/step)));
        if(samples.length<columns*rows)samples=new float[columns*rows];
        for(int y=0;y<rows;y++){
            float py=minY+y*step;
            for(int x=0;x<columns;x++){
                samples[y*columns+x]=densityAt(minX+x*step,py,distance,radius,p,time);
            }
        }
        inkPath.reset();edgePath.reset();
        inkPath.setFillType(Path.FillType.WINDING);
        for(int y=0;y<rows-1;y++)
            for(int x=0;x<columns-1;x++)
                cell(minX+x*step,minY+y*step,step,x,y,columns);

        int save=canvas.save();
        canvas.translate(ax,ay);
        canvas.rotate((float)Math.toDegrees(Math.atan2(dy,dx)));
        pigment.setShader(new LinearGradient(-radius,-radius,distance+radius,radius,
            new int[]{0xff30263b,0xff7c648b,0xff392b49},
            new float[]{0f,.53f,1f},Shader.TileMode.CLAMP));
        pigment.setAlpha(Math.round(224f*visible));
        canvas.drawPath(inkPath,pigment);
        pigment.setShader(null);pigment.setAlpha(255);
        fringe.setColor(Color.argb(Math.round(65f*visible),191,169,215));
        canvas.drawPath(edgePath,fringe);
        rim.setColor(Color.argb(Math.round(196f*visible),241,230,247));
        canvas.drawPath(edgePath,rim);
        canvas.restoreToCount(save);
    }
}
