package com.discnet.noise;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.view.animation.PathInterpolator;
import android.widget.*;

/**
 * True circular tool navigation. Items are placed using an angular coordinate,
 * not a vertically scrolling list. Drag/fling move a fractional selection;
 * the closest item is magnetically snapped into the focal position.
 */
final class OrbitalSelector extends FrameLayout implements Choreographer.FrameCallback {
    interface Selection { void chosen(int index); }
    interface Navigation { void navigate(int direction); }
    private Navigation navigate;
    private float swipeStartX,swipeStartY;
    private boolean horizontal;
    private boolean dismissed;
    private boolean effectsMode;
    private boolean motionScheduled;
    private long lastMotionFrame;
    private float targetX,targetAlpha;
    private final View scrim;
    private final LinearLayout content;
    private boolean scrimShown;
    private final OrbitStage orbit;
    private final TextView continueButton;
    private final Selection callback;
    private final String[] names;
    private final int[] symbols;

    OrbitalSelector(Context c,String title,String subtitle,String[] names,int[] symbols,int initial,Selection callback){
        super(c);
        this.names=names.clone();this.symbols=symbols.clone();this.callback=callback;
        // A dedicated overlay fades independently from the orbit's sliding panel:
        // the photo dims smoothly, without being dragged horizontally.
        setBackgroundColor(Color.TRANSPARENT);
        setClickable(true);setFocusableInTouchMode(true);
        scrim=new View(c);scrim.setBackgroundColor(0xff0c0b10);scrim.setAlpha(0f);
        addView(scrim,new FrameLayout.LayoutParams(-1,-1));
        content=new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24),dp(12),dp(24),dp(16));
        addView(content,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout column=content;

        LinearLayout header=new LinearLayout(c);
        header.setGravity(Gravity.TOP|Gravity.CENTER_VERTICAL);
        column.addView(header,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout heading=new LinearLayout(c);heading.setOrientation(LinearLayout.VERTICAL);
        header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        TextView label=label(c,title,30,EditorTheme.INK);
        label.setTypeface(NoiseTypography.bold(c));
        label.setLetterSpacing(-.035f);label.setMaxLines(2);
        heading.addView(label);
        TextView description=label(c,subtitle,13,EditorTheme.MUTED);
        description.setPadding(0,dp(8),dp(12),0);
        description.setMaxLines(3);heading.addView(description);
        // A roda nao tem botao de abertura/fechamento: deslize horizontal,
        // toque para escolher, ou o gesto Voltar do Android.

        orbit=new OrbitStage(c,initial);
        column.addView(orbit,new LinearLayout.LayoutParams(-1,0,1));
        TextView current=label(c,"↕  DESLIZE SUAVEMENTE   ·   TOQUE PARA SELECIONAR",10,EditorTheme.MUTED);
        current.setGravity(Gravity.CENTER);
        current.setLetterSpacing(.07f);
        column.addView(current,new LinearLayout.LayoutParams(-1,dp(32)));
        continueButton=label(c,"Selecionar "+names[orbit.selected()],16,0xff17121d);
        continueButton.setTypeface(NoiseTypography.bold(c));
        continueButton.setGravity(Gravity.CENTER);
        continueButton.setBackground(EditorTheme.buttonSurface(c,32,true));
        continueButton.setClickable(true);continueButton.setFocusable(true);
        column.addView(continueButton,new LinearLayout.LayoutParams(-1,dp(58)));
        continueButton.setOnClickListener(v->commit());
        orbit.onSelection=()->continueButton.setText("Selecionar "+this.names[orbit.selected()]);
    }
    private int dp(float value){return EditorTheme.dp(getContext(),value);}
    private static TextView label(Context c,String value,int size,int color){
        TextView t=new TextView(c);t.setTypeface(NoiseTypography.regular(c));t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;
    }
    int selected(){return orbit.selected();}
    void setNavigate(Navigation callback){navigate=callback;}
    void setMode(boolean effects){effectsMode=effects;}
    boolean isEffects(){return effectsMode;}

    float motionX(){return content.getTranslationX();}
    float motionAlpha(){return content.getAlpha();}
    float scrimOpacity(){return scrim.getAlpha();}
    private float travelDistance(){
        return Math.max(dp(210),Math.min(
            getWidth()>0?getWidth():getResources().getDisplayMetrics().widthPixels,
            dp(530))*.86f);
    }
    private float entrance(){return effectsMode?-travelDistance():travelDistance();}
    private PathInterpolator glide(){
        return new PathInterpolator(.18f,.82f,.22f,1f);
    }
    private void fadeScrimIn(){
        if(scrimShown)return;
        scrimShown=true;
        scrim.animate().cancel();
        scrim.animate().alpha(.84f).setDuration(420)
            .setInterpolator(glide()).start();
    }
    private void fadeScrimOut(){
        scrimShown=false;
        scrim.animate().cancel();
        scrim.animate().alpha(0f).setDuration(360)
            .setInterpolator(glide()).start();
    }
    private void removeFromParent(){
        if(getParent() instanceof ViewGroup)((ViewGroup)getParent()).removeView(this);
    }
    /** An exponential 108 ms low-pass filter smooths the gesture at frame rate. */
    private void stopTracking(){
        if(motionScheduled){
            Choreographer.getInstance().removeFrameCallback(this);
            motionScheduled=false;
        }
        lastMotionFrame=0;
    }
    private void setMotionTarget(float x,float alpha){
        targetX=x;
        targetAlpha=Math.max(0f,Math.min(1f,alpha));
        if(!motionScheduled){
            motionScheduled=true;
            Choreographer.getInstance().postFrameCallback(this);
        }
    }
    @Override public void doFrame(long frameTimeNanos){
        motionScheduled=false;
        if(getParent()==null){lastMotionFrame=0;return;}
        float dt=lastMotionFrame==0?.016f:
            Math.min(.05f,Math.max(.001f,(frameTimeNanos-lastMotionFrame)*1e-9f));
        lastMotionFrame=frameTimeNanos;
        float blend=1f-(float)Math.exp(-dt/.108f);
        float x=content.getTranslationX()+(targetX-content.getTranslationX())*blend;
        float alpha=content.getAlpha()+(targetAlpha-content.getAlpha())*blend;
        if(Math.abs(x-targetX)<.65f&&Math.abs(alpha-targetAlpha)<.005f){
            content.setTranslationX(targetX);
            content.setAlpha(targetAlpha);
            lastMotionFrame=0;
        }else{
            content.setTranslationX(x);content.setAlpha(alpha);
            motionScheduled=true;
            Choreographer.getInstance().postFrameCallback(this);
        }
    }
    private float smoothstep(float p){
        p=Math.max(0f,Math.min(1f,p));return p*p*(3f-2f*p);
    }
    /** Finger sets target; the panel follows softly and scrim fades independently. */
    void setReveal(float fraction){
        float eased=smoothstep(fraction);
        if(fraction<=0f){
            stopTracking();content.animate().cancel();
            content.setTranslationX(entrance());content.setAlpha(0f);
            scrimShown=false;scrim.animate().cancel();scrim.setAlpha(0f);
            return;
        }
        fadeScrimIn();
        setMotionTarget(entrance()*(1f-eased),eased);
    }
    void finishReveal(boolean open){
        stopTracking();content.animate().cancel();
        if(open){
            fadeScrimIn();
            content.animate().translationX(0f).alpha(1f).setDuration(440)
                .setInterpolator(glide()).start();
        }else{
            fadeScrimOut();
            content.animate().translationX(entrance()).alpha(0f).setDuration(370)
                .setInterpolator(glide()).withEndAction(this::removeFromParent).start();
        }
    }
    void enter(){enterFrom(effectsMode?1:-1);}
    void enterFrom(int direction){
        stopTracking();content.animate().cancel();
        content.setTranslationX(direction<0?travelDistance():-travelDistance());
        content.setAlpha(0f);
        scrimShown=false;scrim.setAlpha(0f);fadeScrimIn();
        content.animate().translationX(0f).alpha(1f).setDuration(480)
            .setInterpolator(glide()).start();
    }
    void exitTo(int direction){
        if(dismissed)return;
        dismissed=true;
        stopTracking();
        fadeScrimOut();
        content.animate().cancel();
        content.animate().translationX(direction<0?-travelDistance():travelDistance())
            .alpha(0f).setDuration(390).setInterpolator(glide())
            .withEndAction(this::removeFromParent).start();
    }
    void dismiss(){exitTo(effectsMode?-1:1);}
    private void restorePosition(){
        stopTracking();
        content.animate().cancel();
        content.animate().translationX(0f).alpha(1f).setDuration(390)
            .setInterpolator(glide()).start();
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent e){
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                swipeStartX=e.getX();swipeStartY=e.getY();horizontal=false;
                return false;
            case MotionEvent.ACTION_POINTER_DOWN:
                horizontal=false;return false;
            case MotionEvent.ACTION_MOVE:
                if(e.getPointerCount()!=1)return false;
                float dx=e.getX()-swipeStartX,dy=e.getY()-swipeStartY;
                if(Math.abs(dx)>dp(11)&&Math.abs(dx)>Math.abs(dy)*1.16f){
                    horizontal=true;
                    return true;
                }
                return false;
            default:return false;
        }
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(!horizontal)return super.onTouchEvent(e);
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE){
            float dx=e.getX()-swipeStartX;
            float travel=Math.max(-travelDistance()*.8f,Math.min(travelDistance()*.8f,dx*.88f));
            content.animate().cancel();
            setMotionTarget(travel,Math.max(.43f,1f-Math.abs(travel)/(travelDistance()*1.28f)));
            return true;
        }
        if(e.getActionMasked()==MotionEvent.ACTION_UP){
            float dx=e.getX()-swipeStartX;
            boolean strong=Math.abs(dx)>Math.min(dp(64),getWidth()*.18f)
                && Math.abs(dx)>Math.abs(e.getY()-swipeStartY)*1.10f;
            horizontal=false;
            // Closing is separate: overlay gestures do not open another wheel.
            if(strong&&dx>0&&navigate!=null)navigate.navigate(1);
            else restorePosition();
            return true;
        }
        if(e.getActionMasked()==MotionEvent.ACTION_CANCEL){
            horizontal=false;restorePosition();return true;
        }
        return true;
    }
    private void commit(){int n=selected();dismiss();callback.chosen(n);}
    @Override protected void onDetachedFromWindow(){
        stopTracking();content.animate().cancel();scrim.animate().cancel();
        orbit.cancelMotion();super.onDetachedFromWindow();
    }

    private final class OrbitStage extends FrameLayout implements Choreographer.FrameCallback {
        private static final float STEP=.29f;
        private final Paint stroke=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint light=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final LinearLayout[] rows=new LinearLayout[names.length];
        private final TextView[] captions=new TextView[names.length];
        private final ImageView[] icons=new ImageView[names.length];
        private VelocityTracker velocity;
        private ValueAnimator snap;
        private float offset,targetOffset,downY,lastY,downX,radius;
        private boolean rotationalFrameScheduled;
        private long lastRotationalFrame;
        private boolean dragged;
        private int highlighted=-1;
        private Runnable onSelection;
        private final int slop=ViewConfiguration.get(getContext()).getScaledTouchSlop();

        OrbitStage(Context context,int start){
            super(context);
            setWillNotDraw(false);setClipChildren(false);setClipToPadding(false);
            offset=Math.max(0,Math.min(names.length-1,start));targetOffset=offset;
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
                    captions[i].setTypeface(on?NoiseTypography.bold(getContext()):NoiseTypography.medium(getContext()));
                    icons[i].setImageDrawable(new IconArt(symbols[Math.min(i,symbols.length-1)],on?0xff1a1421:EditorTheme.INK));
                    icons[i].setBackground(EditorTheme.buttonSurface(getContext(),24,on));
                    rows[i].setContentDescription(names[i]+(on?", selecionado":"")+". Toque para selecionar.");
                }
                if(onSelection!=null)onSelection.run();
            }
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
            if(rotationalFrameScheduled){
                Choreographer.getInstance().removeFrameCallback(this);
                rotationalFrameScheduled=false;
            }
            lastRotationalFrame=0;
            targetOffset=offset;
            if(snap!=null){snap.cancel();snap=null;}
        }
        private void followOffset(){
            if(!rotationalFrameScheduled){
                rotationalFrameScheduled=true;
                Choreographer.getInstance().postFrameCallback(this);
            }
        }
        @Override public void doFrame(long nanos){
            rotationalFrameScheduled=false;
            float dt=lastRotationalFrame==0?.016f:
                Math.min(.050f,Math.max(.001f,(nanos-lastRotationalFrame)*1e-9f));
            lastRotationalFrame=nanos;
            // Angular list glides ~90ms behind touch, not rigidly attached.
            float factor=1f-(float)Math.exp(-dt/.090f);
            offset+=(targetOffset-offset)*factor;
            if(Math.abs(targetOffset-offset)<.004f){
                offset=targetOffset;lastRotationalFrame=0;
            }else followOffset();
            render();
        }
        private void glideTo(int index,long duration){
            cancelMotion();
            targetOffset=index;
            snap=ValueAnimator.ofFloat(offset,index);
            snap.setDuration(duration);
            // Critically damped visual response without rubber-band oscillations.
            snap.setInterpolator(new PathInterpolator(.19f,.82f,.22f,1f));
            snap.addUpdateListener(a->{offset=(Float)a.getAnimatedValue();render();});
            snap.start();
        }
        private void settle(float velocityY){
            float pixelsPerItem=Math.max(dp(60),radius*STEP);
            float momentum=Math.max(-2.9f,Math.min(2.9f,-velocityY*.16f/pixelsPerItem));
            int destination=Math.max(0,Math.min(names.length-1,Math.round(targetOffset+momentum)));
            long duration=(long)Math.min(520,280+Math.abs(destination-offset)*65);
            glideTo(destination,duration);
        }
        private void goTo(int index){
            cancelMotion();
            if(index!=selected())performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            glideTo(index,320);
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
                    if(dragged){
                        targetOffset=Math.max(0,Math.min(names.length-1,
                            targetOffset-dy/Math.max(dp(60),radius*STEP)));
                        followOffset();
                    }
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
                        if(hit>=0&&closest<dp(82))goTo(hit);else settle(0);
                    }else if(velocity!=null){
                        velocity.computeCurrentVelocity(1000);
                        settle(velocity.getYVelocity());
                    }else settle(0);
                    if(velocity!=null){velocity.recycle();velocity=null;}
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if(velocity!=null){velocity.recycle();velocity=null;}settle(0);return true;
            }
            return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
