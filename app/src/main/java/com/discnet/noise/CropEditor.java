package com.discnet.noise;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;

/** Fullscreen direct-manipulation crop editor with draggable image corners.
 * Coordinates remain normalized; confirmation updates recipe geometry only.
 */
final class CropEditor extends FrameLayout {
    interface Listener { void apply(Rect cropPixels); void cancel(); }
    private final CropView canvas;
    private final Listener listener;
    CropEditor(Context context,Bitmap source,Listener listener){this(context,source,new EditorDocument(),listener);}
    CropEditor(Context context,Bitmap source,EditorDocument document,Listener listener){
        super(context);
        this.listener=listener;
        setBackgroundColor(0xff101216);
        LinearLayout root=new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        setPadding(dp(10),dp(12),dp(10),dp(12));
        addView(root,new LayoutParams(-1,-1));
        LinearLayout top=new LinearLayout(context);top.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(top,new LinearLayout.LayoutParams(-1,dp(52)));
        ImageView back=IconArt.button(context,IconArt.BACK,"Cancelar recorte");
        top.addView(back,new LinearLayout.LayoutParams(dp(46),dp(46)));
        back.setOnClickListener(v->listener.cancel());
        TextView title=label("Recortar",18,EditorTheme.INK);
        title.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        title.setGravity(Gravity.CENTER);
        top.addView(title,new LinearLayout.LayoutParams(0,dp(52),1));
        ImageView restore=IconArt.button(context,IconArt.RESET,"Reiniciar recorte");
        top.addView(restore,new LinearLayout.LayoutParams(dp(46),dp(46)));
        canvas=new CropView(context,source);
        canvas.region.set(document.left,document.top,document.right,document.bottom);
        restore.setOnClickListener(v->canvas.reset());
        TextView hint=label("Arraste alças ou centro · o original permanece intacto",12,EditorTheme.MUTED);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint,new LinearLayout.LayoutParams(-1,dp(40)));
        root.addView(canvas,new LinearLayout.LayoutParams(-1,0,1));
        HorizontalScrollView ratios=new HorizontalScrollView(context);ratios.setHorizontalScrollBarEnabled(false);
        LinearLayout options=new LinearLayout(context);ratios.addView(options);root.addView(ratios,new LinearLayout.LayoutParams(-1,dp(48)));
        String[] names={"Livre","1:1","4:3","3:4","16:9","9:16"};float[] values={0,1,4f/3,3f/4,16f/9,9f/16};
        for(int i=0;i<names.length;i++){final float ratio=values[i];TextView b=action(names[i],false);options.addView(b,new LinearLayout.LayoutParams(dp(72),dp(48)));b.setOnClickListener(v->{canvas.setRatio(document.rotation%2==0?ratio:ratio==0?0:1/ratio);});}
        LinearLayout foot=new LinearLayout(context);foot.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,dp(64));
        root.addView(foot,fp);
        TextView cancel=action("Cancelar",false);
        foot.addView(cancel,new LinearLayout.LayoutParams(0,dp(54),1));
        cancel.setOnClickListener(v->listener.cancel());
        TextView confirm=action("Aplicar recorte",true);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(54),2);
        cp.leftMargin=dp(10);foot.addView(confirm,cp);
        confirm.setOnClickListener(v->listener.apply(canvas.getCropPixels()));
    }
    private int dp(float x){return EditorTheme.dp(getContext(),x);}
    private TextView label(String t,int size,int col){
        TextView x=new TextView(getContext());x.setText(t);x.setTextSize(size);
        x.setTextColor(col);x.setGravity(Gravity.CENTER_VERTICAL);return x;
    }
    private TextView action(String s,boolean primary){
        TextView b=label(s,15,primary?0xff130d1b:EditorTheme.INK);
        b.setGravity(Gravity.CENTER);EditorTheme.button(b,primary);
        return b;
    }
    Rect currentRect(){return canvas.getCropPixels();}
    RectF normalizedRect(){return new RectF(canvas.region);}

    private static final class CropView extends View {
        private final Bitmap bitmap;
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private final RectF picture=new RectF();
        private final RectF region=new RectF(0f,0f,1f,1f);
        private final RectF screenCrop=new RectF();
        private int dragging=-1;
        private float previousX,previousY,ratio;
        CropView(Context c,Bitmap bitmap){super(c);this.bitmap=bitmap;
            setContentDescription("Área de recorte: arraste cantos, laterais ou o centro. Proporções disponíveis abaixo.");}
        void reset(){ratio=0;region.set(0f,0f,1f,1f);invalidate();}
        void setRatio(float physical){ratio=physical==0?0:physical*bitmap.getHeight()/bitmap.getWidth();fitRatio();invalidate();}
        private void fitRatio(){
            if(ratio<=0)return;float cx=region.centerX(),cy=region.centerY();
            float w=region.width(),h=w/ratio;if(h>region.height()){h=region.height();w=h*ratio;}
            w=Math.min(1,Math.max(.01f,w));h=w/ratio;if(h>1){h=1;w=ratio;}
            region.set(cx-w/2,cy-h/2,cx+w/2,cy+h/2);
            if(region.left<0)region.offset(-region.left,0);if(region.right>1)region.offset(1-region.right,0);
            if(region.top<0)region.offset(0,-region.top);if(region.bottom>1)region.offset(0,1-region.bottom);
        }
        Rect getCropPixels(){
            int w=bitmap.getWidth(),h=bitmap.getHeight();
            int left=Math.max(0,Math.min(w-1,Math.round(region.left*w)));
            int top=Math.max(0,Math.min(h-1,Math.round(region.top*h)));
            int right=Math.min(w,Math.max(left+1,Math.round(region.right*w)));
            int bottom=Math.min(h,Math.max(top+1,Math.round(region.bottom*h)));
            return new Rect(left,top,right,bottom);
        }
        private void dimensions(){
            float width=Math.max(1,getWidth()-20f),height=Math.max(1,getHeight()-40f);
            float factor=Math.min(width/bitmap.getWidth(),height/bitmap.getHeight());
            float w=bitmap.getWidth()*factor,h=bitmap.getHeight()*factor;
            picture.set((getWidth()-w)/2f,(getHeight()-h)/2f,
                (getWidth()+w)/2f,(getHeight()+h)/2f);
            screenCrop.set(picture.left+region.left*picture.width(),
                picture.top+region.top*picture.height(),
                picture.left+region.right*picture.width(),
                picture.top+region.bottom*picture.height());
        }
        @Override protected void onDraw(Canvas c){
            dimensions();
            c.drawColor(0xff101216);
            p.setShader(null);p.setColor(Color.WHITE);p.setStyle(Paint.Style.FILL);
            c.drawBitmap(bitmap,null,picture,p);
            // Draw translucent outside mask, not obscuring crop selection.
            p.setColor(0xa5000000);
            c.drawRect(picture.left,picture.top,picture.right,screenCrop.top,p);
            c.drawRect(picture.left,screenCrop.bottom,picture.right,picture.bottom,p);
            c.drawRect(picture.left,screenCrop.top,screenCrop.left,screenCrop.bottom,p);
            c.drawRect(screenCrop.right,screenCrop.top,picture.right,screenCrop.bottom,p);
            p.setShader(null);p.setColor(0xfffaf0ff);p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1.5f));c.drawRect(screenCrop,p);
            p.setColor(0x88ffffff);p.setStrokeWidth(dp(.8f));
            for(int i=1;i<3;i++){
                float x=screenCrop.left+screenCrop.width()*i/3f;
                float y=screenCrop.top+screenCrop.height()*i/3f;
                c.drawLine(x,screenCrop.top,x,screenCrop.bottom,p);
                c.drawLine(screenCrop.left,y,screenCrop.right,y,p);
            }
            p.setColor(0xfff8eaff);p.setStrokeWidth(dp(3.4f));
            float len=dp(22);
            float[] xs={screenCrop.left,screenCrop.right,screenCrop.left,screenCrop.right};
            float[] ys={screenCrop.top,screenCrop.top,screenCrop.bottom,screenCrop.bottom};
            for(int i=0;i<4;i++){
                float signX=(i%2==0)?1f:-1f,signY=(i<2)?1f:-1f;
                c.drawLine(xs[i],ys[i],xs[i]+signX*len,ys[i],p);
                c.drawLine(xs[i],ys[i],xs[i],ys[i]+signY*len,p);
            }
            for(int i=0;i<4;i++){
                float x=i==0?screenCrop.left:i==1?screenCrop.right:screenCrop.centerX();
                float y=i==2?screenCrop.top:i==3?screenCrop.bottom:screenCrop.centerY();
                if(i<2)c.drawLine(x,y-dp(10),x,y+dp(10),p);else c.drawLine(x-dp(10),y,x+dp(10),y,p);
            }
            p.setStyle(Paint.Style.FILL);
            int croppedW=getCropPixels().width(),croppedH=getCropPixels().height();
            p.setTextAlign(Paint.Align.CENTER);p.setColor(0xfff5ebff);p.setTextSize(dp(13));
            c.drawText(croppedW+" × "+croppedH+" px",getWidth()/2f,Math.max(dp(20),picture.top-dp(9)),p);
        }
        private float dp(float v){return EditorTheme.dp(getContext(),v);}
        @Override public boolean onTouchEvent(android.view.MotionEvent event){
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
                dimensions();
                float x=event.getX(),y=event.getY(),closest=dp(38);
                dragging=-1;
                float[] xs={screenCrop.left,screenCrop.right,screenCrop.left,screenCrop.right};
                float[] ys={screenCrop.top,screenCrop.top,screenCrop.bottom,screenCrop.bottom};
                for(int i=0;i<4;i++){
                    float distance=(float)Math.hypot(x-xs[i],y-ys[i]);
                    if(distance<closest){closest=distance;dragging=i;}
                }
                if(dragging<0){
                    if(Math.abs(x-screenCrop.left)<dp(20)&&Math.abs(y-screenCrop.centerY())<dp(32))dragging=5;
                    else if(Math.abs(x-screenCrop.right)<dp(20)&&Math.abs(y-screenCrop.centerY())<dp(32))dragging=6;
                    else if(Math.abs(y-screenCrop.top)<dp(20)&&Math.abs(x-screenCrop.centerX())<dp(32))dragging=7;
                    else if(Math.abs(y-screenCrop.bottom)<dp(20)&&Math.abs(x-screenCrop.centerX())<dp(32))dragging=8;
                }
                if(dragging<0&&screenCrop.contains(x,y))dragging=4; // move
                previousX=x;previousY=y;return dragging>=0;
            }
            if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&dragging>=0){
                float x=event.getX(),y=event.getY();
                float dx=(x-previousX)/picture.width(),dy=(y-previousY)/picture.height();
                float minX=Math.min(.25f,Math.max(.01f,dp(24)/picture.width()));
                float minY=Math.min(.25f,Math.max(.01f,dp(24)/picture.height()));
                if(dragging==4){
                    float ox=Math.max(-region.left,Math.min(1f-region.right,dx));
                    float oy=Math.max(-region.top,Math.min(1f-region.bottom,dy));
                    region.offset(ox,oy);
                }else{
                    if(dragging==0||dragging==2||dragging==5)region.left=Math.max(0f,Math.min(region.right-minX,region.left+dx));
                    if(dragging==1||dragging==3||dragging==6)region.right=Math.min(1f,Math.max(region.left+minX,region.right+dx));
                    if(dragging==0||dragging==1||dragging==7)region.top=Math.max(0f,Math.min(region.bottom-minY,region.top+dy));
                    if(dragging==2||dragging==3||dragging==8)region.bottom=Math.min(1f,Math.max(region.top+minY,region.bottom+dy));
                    fitRatio();
                }
                previousX=x;previousY=y;invalidate();return true;
            }
            if(event.getActionMasked()==MotionEvent.ACTION_UP||
               event.getActionMasked()==MotionEvent.ACTION_CANCEL){
                dragging=-1;performClick();return true;
            }
            return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
