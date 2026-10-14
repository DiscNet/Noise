package com.discnet.noise;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.*;

/**
 * True circular tool navigation. Items are placed using an angular coordinate,
 * not a vertically scrolling list. Drag/fling move a fractional selection;
 * the closest item is magnetically snapped into the focal position.
 */
final class OrbitalSelector extends FrameLayout {
    interface Selection { void chosen(int index); }
    private final OrbitStage orbit;
    private final TextView continueButton;
    private final Selection callback;
    private final String[] names;
    private final int[] symbols;

    OrbitalSelector(Context c,String title,String subtitle,String[] names,int[] symbols,int initial,Selection callback){
        super(c);
        this.names=names.clone();this.symbols=symbols.clone();this.callback=callback;
        setBackgroundColor(0xff0c0b10);
        setClickable(true);setFocusableInTouchMode(true);
        LinearLayout column=new LinearLayout(c);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(24),dp(12),dp(24),dp(16));
        addView(column,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout header=new LinearLayout(c);
        header.setGravity(Gravity.TOP|Gravity.CENTER_VERTICAL);
        column.addView(header,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout heading=new LinearLayout(c);heading.setOrientation(LinearLayout.VERTICAL);
        header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        TextView label=label(c,title,30,EditorTheme.INK);
        label.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        label.setLetterSpacing(-.035f);label.setMaxLines(2);
        heading.addView(label);
        TextView description=label(c,subtitle,13,EditorTheme.MUTED);
        description.setPadding(0,dp(8),dp(12),0);
        description.setMaxLines(3);heading.addView(description);
        ImageView close=IconArt.button(c,IconArt.BACK,"Voltar ao editor");
        header.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));
        close.setOnClickListener(v->dismiss());

        orbit=new OrbitStage(c,initial);
        column.addView(orbit,new LinearLayout.LayoutParams(-1,0,1));
        TextView current=label(c,"DESLIZE PARA GIRAR  ·  TOQUE PARA SELECIONAR",10,EditorTheme.MUTED);
        current.setGravity(Gravity.CENTER);
        current.setLetterSpacing(.07f);
        column.addView(current,new LinearLayout.LayoutParams(-1,dp(32)));
        continueButton=label(c,"Abrir "+names[orbit.selected()],16,0xff17121d);
        continueButton.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        continueButton.setGravity(Gravity.CENTER);
        continueButton.setBackground(EditorTheme.buttonSurface(c,32,true));
        continueButton.setClickable(true);continueButton.setFocusable(true);
        column.addView(continueButton,new LinearLayout.LayoutParams(-1,dp(58)));
        continueButton.setOnClickListener(v->commit());
        orbit.onSelection=()->continueButton.setText("Abrir "+this.names[orbit.selected()]);
    }
    private int dp(float value){return EditorTheme.dp(getContext(),value);}
    private static TextView label(Context c,String value,int size,int color){
        TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;
    }
    int selected(){return orbit.selected();}
    void dismiss(){if(getParent() instanceof ViewGroup)((ViewGroup)getParent()).removeView(this);}
    private void commit(){int n=selected();dismiss();callback.chosen(n);}
    @Override protected void onDetachedFromWindow(){orbit.cancelMotion();super.onDetachedFromWindow();}

    private final class OrbitStage extends FrameLayout {
        private static final float STEP=.29f;
        private final Paint stroke=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint light=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final LinearLayout[] rows=new LinearLayout[names.length];
        private final TextView[] captions=new TextView[names.length];
        private final ImageView[] icons=new ImageView[names.length];
        private final OverScroller fling;
        private VelocityTracker velocity;
        private ValueAnimator snap;
        private float offset,downY,lastY,downX,radius;
        private boolean dragged;
        private int highlighted=-1;
        private Runnable onSelection;
        private final int slop=ViewConfiguration.get(getContext()).getScaledTouchSlop();

        OrbitStage(Context context,int start){
            super(context);
            setWillNotDraw(false);setClipChildren(false);setClipToPadding(false);
            fling=new OverScroller(context,new DecelerateInterpolator(1.8f));
            offset=Math.max(0,Math.min(names.length-1,start));
            setLayerType(View.LAYER_TYPE_HARDWARE,null);
            for(int i=0;i<names.length;i++){
                final int index=i;
                LinearLayout row=new LinearLayout(context);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setOrientation(LinearLayout.HORIZONTAL);
                TextView text=label(context,names[i],15,EditorTheme.MUTED);
                text.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
                text.setSingleLine(true);text.setEllipsize(android.text.TextUtils.TruncateAt.END);
                text.setPadding(0,0,dp(9),0);
                row.addView(text,new LinearLayout.LayoutParams(dp(136),dp(64)));
                ImageView icon=new ImageView(context);
                icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                icon.setPadding(dp(20),dp(20),dp(20),dp(20));
                LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(64),dp(64));
                ip.leftMargin=dp(8);row.addView(icon,ip);
                row.setContentDescription(names[i]+", ferramenta orbital");
                row.setFocusable(true);row.setClickable(true);
                row.setOnClickListener(v->goTo(index));
                addView(row,new FrameLayout.LayoutParams(dp(220),dp(64)));
                rows[i]=row;captions[i]=text;icons[i]=icon;
            }
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            post(this::render);
        }

        int selected(){return Math.max(0,Math.min(names.length-1,Math.round(offset)));}
        @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){super.onSizeChanged(w,h,oldW,oldH);radius=Math.max(w*.94f,dp(250));render();}
        private float arcX(float theta){return getWidth()*.82f+radius-radius*(float)Math.cos(theta);}
        private float arcY(float theta){return getHeight()*.50f+radius*(float)Math.sin(theta);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            if(radius<=0)return;
            stroke.setShader(null);stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(dp(1));
            stroke.setColor(0xff514958);
            Path path=new Path();
            for(int j=0;j<=80;j++){
                float theta=-1.37f+(2.74f*j/80f);
                if(j==0)path.moveTo(arcX(theta),arcY(theta));
                else path.lineTo(arcX(theta),arcY(theta));
            }
            canvas.drawPath(path,stroke);
            float x=arcX(0),y=arcY(0);
            light.setStyle(Paint.Style.FILL);
            light.setShader(new RadialGradient(x-dp(74),y,dp(96),
                new int[]{0x3bd8b4f8,0x14b58bcf,0x00b58bcf},null,Shader.TileMode.CLAMP));
            canvas.drawCircle(x-dp(74),y,dp(96),light);
            light.setShader(null);light.setColor(EditorTheme.LIME);
            canvas.drawRoundRect(x-dp(17),y-dp(3),x+dp(17),y+dp(3),dp(4),dp(4),light);
        }
        private void render(){
            if(getWidth()==0||getHeight()==0)return;
            int active=selected();
            if(active!=highlighted){
                highlighted=active;
                for(int i=0;i<names.length;i++){
                    boolean on=i==active;
                    captions[i].setTextColor(on?EditorTheme.INK:EditorTheme.MUTED);
                    captions[i].setTypeface(Typeface.create("sans-serif-medium",on?Typeface.BOLD:Typeface.NORMAL));
                    icons[i].setImageDrawable(new IconArt(symbols[Math.min(i,symbols.length-1)],on?0xff1a1421:EditorTheme.INK));
                    icons[i].setBackground(EditorTheme.buttonSurface(getContext(),24,on));
                    rows[i].setContentDescription(names[i]+(on?", selecionado":"")+". Toque para selecionar.");
                }
                if(onSelection!=null)onSelection.run();
            }
            float cx=arcX(0);
            for(int i=0;i<rows.length;i++){
                float theta=(i-offset)*STEP;
                float distance=Math.abs(i-offset);
                boolean visible=Math.abs(theta)<1.42f;
                LinearLayout row=rows[i];
                row.setVisibility(visible?VISIBLE:INVISIBLE);
                if(!visible)continue;
                float x=arcX(theta)-dp(254),y=arcY(theta)-dp(32);
                row.setTranslationX(x);row.setTranslationY(y);
                float emphasis=Math.max(0,1-Math.min(1,distance));
                row.setAlpha(Math.max(.12f,1-distance*.19f));
                float scale=.83f+.17f*emphasis;
                row.setScaleX(scale);row.setScaleY(scale);
            }
            invalidate();
        }
        private void cancelMotion(){
            fling.forceFinished(true);
            if(snap!=null){snap.cancel();snap=null;}
        }
        private void settle(){
            final float destination=selected();
            if(snap!=null)snap.cancel();
            snap=ValueAnimator.ofFloat(offset,destination);
            snap.setDuration(260);
            snap.setInterpolator(new OvershootInterpolator(.38f));
            snap.addUpdateListener(a->{offset=(Float)a.getAnimatedValue();render();});
            snap.start();
        }
        private void goTo(int index){
            cancelMotion();
            if(index!=selected())performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            snap=ValueAnimator.ofFloat(offset,index);
            snap.setDuration(300);snap.setInterpolator(new DecelerateInterpolator(1.6f));
            snap.addUpdateListener(a->{offset=(Float)a.getAnimatedValue();render();});
            snap.start();
        }
        private void tick(){
            if(fling.computeScrollOffset()){
                offset=Math.max(0,Math.min(names.length-1,fling.getCurrY()/1000f));
                render();postOnAnimation(this::tick);
            }else settle();
        }
        @Override public boolean onInterceptTouchEvent(MotionEvent ev){
            if(ev.getActionMasked()==MotionEvent.ACTION_DOWN)return true;
            return true;
        }
        @Override public boolean onTouchEvent(MotionEvent ev){
            switch(ev.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    cancelMotion();if(velocity!=null)velocity.recycle();
                    velocity=VelocityTracker.obtain();velocity.addMovement(ev);
                    downY=lastY=ev.getY();downX=ev.getX();dragged=false;return true;
                case MotionEvent.ACTION_MOVE:
                    if(velocity!=null)velocity.addMovement(ev);
                    float dy=ev.getY()-lastY;
                    if(Math.abs(ev.getY()-downY)>slop||Math.abs(ev.getX()-downX)>slop)dragged=true;
                    if(dragged){offset=Math.max(0,Math.min(names.length-1,offset-dy/Math.max(dp(60),radius*STEP)));render();}
                    lastY=ev.getY();return true;
                case MotionEvent.ACTION_UP:
                    if(velocity!=null)velocity.addMovement(ev);
                    if(!dragged){
                        float y=ev.getY(),x=ev.getX();int hit=-1;float closest=Float.MAX_VALUE;
                        for(int i=0;i<names.length;i++){
                            float angle=(i-offset)*STEP;
                            if(Math.abs(angle)>1.42f)continue;
                            float ix=arcX(angle)-dp(75),iy=arcY(angle);
                            float delta=Math.abs(iy-y)+Math.abs(ix-x)*.3f;
                            if(delta<closest){closest=delta;hit=i;}
                        }
                        if(hit>=0&&closest<dp(82))goTo(hit);else settle();
                    }else if(velocity!=null){
                        velocity.computeCurrentVelocity(1000);
                        float vy=velocity.getYVelocity();
                        if(Math.abs(vy)>dp(360)){
                            fling.fling(0,Math.round(offset*1000),0,
                                Math.round(-vy*1000/Math.max(dp(60),radius*STEP)),
                                0,0,0,(names.length-1)*1000);
                            postOnAnimation(this::tick);
                        }else settle();
                    }else settle();
                    if(velocity!=null){velocity.recycle();velocity=null;}
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if(velocity!=null){velocity.recycle();velocity=null;}settle();return true;
            }
            return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
