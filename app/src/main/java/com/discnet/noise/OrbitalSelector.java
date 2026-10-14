package com.discnet.noise;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
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
    private float targetX,targetAlpha,targetScale=1f,targetScrim;
    private float horizontalVelocity;
    private boolean closing;
    private final View scrim;
    private final LinearLayout content;
    private final TextView headerTitle,headerSubtitle;
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
        scrim=new View(c);scrim.setAlpha(0f);
        // The darkening is a translucent feathered gradient, not a solid
        // black rectangle that snaps over the picture.
        addView(scrim,new FrameLayout.LayoutParams(-1,-1));
        content=new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20),dp(12),dp(20),dp(16));
        // No opaque panel rectangle: the soft scrim behind the wheel provides
        // contrast without a hard black dividing edge.
        content.setBackgroundColor(Color.TRANSPARENT);
        content.setAlpha(0f); // First frame is hidden: prevent a one-frame pop before swipe reveal.
        content.setScaleX(.985f);content.setScaleY(.985f);
        addView(content,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout column=content;

        LinearLayout header=new LinearLayout(c);
        header.setGravity(Gravity.TOP|Gravity.CENTER_VERTICAL);
        column.addView(header,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout heading=new LinearLayout(c);heading.setOrientation(LinearLayout.VERTICAL);
        header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        headerTitle=label(c,title,30,EditorTheme.INK);
        headerTitle.setTypeface(NoiseTypography.bold(c));
        headerTitle.setLetterSpacing(-.035f);headerTitle.setMaxLines(2);
        heading.addView(headerTitle);
        headerSubtitle=label(c,subtitle,13,EditorTheme.MUTED);
        headerSubtitle.setPadding(0,dp(8),0,0);
        headerSubtitle.setMaxLines(3);heading.addView(headerSubtitle);
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
    void setMode(boolean effects){
        effectsMode=effects;
        GradientDrawable gradient=new GradientDrawable(
            effects?GradientDrawable.Orientation.LEFT_RIGHT:GradientDrawable.Orientation.RIGHT_LEFT,
            new int[]{0xb90c0b10,0x770c0b10,0x2a0c0b10,0x000c0b10});
        gradient.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        scrim.setBackground(gradient);
        headerTitle.setGravity(effects?Gravity.LEFT:Gravity.RIGHT);
        headerSubtitle.setGravity(effects?Gravity.LEFT:Gravity.RIGHT);
        updateDock(getWidth());
        orbit.updateItemOrientation();
        orbit.render();
    }
    private void updateDock(int availableWidth){
        int screenWidth=availableWidth>0?availableWidth:getResources().getDisplayMetrics().widthPixels;
        int dock=Math.min(screenWidth,Math.min(dp(440),Math.max(dp(248),Math.round(screenWidth*.84f))));
        FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)content.getLayoutParams();
        int gravity=Gravity.TOP|(effectsMode?Gravity.LEFT:Gravity.RIGHT);
        if(params.width!=dock||params.gravity!=gravity){
            params.width=dock;params.height=LayoutParams.MATCH_PARENT;
            params.gravity=gravity;content.setLayoutParams(params);
        }
    }
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){
        super.onSizeChanged(w,h,oldW,oldH);
        updateDock(w);
    }
    boolean isEffects(){return effectsMode;}

    float motionX(){return content.getTranslationX();}
    float motionAlpha(){return content.getAlpha();}
    float scrimOpacity(){return scrim.getAlpha();}
    int dockLeft(){return content.getLeft();}
    int dockRight(){return content.getRight();}
    float orbitalScreenX(){
        int index=orbit.selected();
        LinearLayout row=orbit.rows[index];
        ImageView active=orbit.icons[index];
        if(active.getWidth()==0)return content.getLeft()+content.getPaddingLeft()+orbit.arcX(0f);
        return content.getLeft()+content.getPaddingLeft()+row.getX()
            +active.getX()+active.getWidth()/2f;
    }
    private float travelDistance(){
        // A restrained slide brings the wheel into view; a full-width
        // translation made the animation appear to jump into the screen.
        return Math.min(dp(164),Math.max(dp(116),getWidth()*.38f));
    }
    private float entrance(){return effectsMode?-travelDistance():travelDistance();}
    private float exitDirection(){return entrance();}
    private void removeFromParent(){
        if(getParent() instanceof ViewGroup)((ViewGroup)getParent()).removeView(this);
    }
    private void stopTracking(){
        if(motionScheduled){
            Choreographer.getInstance().removeFrameCallback(this);
            motionScheduled=false;
        }
        lastMotionFrame=0;
    }
    private void requestMotion(){
        if(!motionScheduled){
            motionScheduled=true;
            Choreographer.getInstance().postFrameCallback(this);
        }
    }
    private void targetOpen(){
        closing=false;
        targetX=0f;
        targetAlpha=1f;
        targetScrim=.90f;
        targetScale=1f;
        requestMotion();
    }
    private void targetClosed(){
        closing=true;
        targetX=exitDirection();
        targetAlpha=0f;
        targetScrim=0f;
        targetScale=.985f;
        requestMotion();
    }
    /**
     * The gesture starts a self-running animation. Neither its distance nor
     * finger velocity drives menu position, fade, scale, or overlay opacity.
     *
     * Position uses the same critically-damped, velocity-continuous spring as
     * orbital scroll. Fade is integrated independently, every display frame.
     */
    void beginReveal(){
        stopTracking();
        closing=false;dismissed=false;
        content.animate().cancel();scrim.animate().cancel();
        horizontalVelocity=0f;
        content.setTranslationX(entrance());
        content.setAlpha(0f);
        content.setScaleX(.985f);content.setScaleY(.985f);
        scrim.setAlpha(0f);
        targetOpen();
    }
    @Override public void doFrame(long frameTimeNanos){
        motionScheduled=false;
        if(getParent()==null){lastMotionFrame=0;return;}
        float dt=lastMotionFrame==0?.016f:
            Math.min(.045f,Math.max(.001f,(frameTimeNanos-lastMotionFrame)*1e-9f));
        lastMotionFrame=frameTimeNanos;

        // Analytic critically damped spring. Unlike a view animation tied
        // to the pointer, this never jumps when a touch MOVE or UP arrives.
        final float omega=12f;
        float displacement=content.getTranslationX()-targetX;
        float carry=horizontalVelocity+omega*displacement;
        float decay=(float)Math.exp(-omega*dt);
        float x=targetX+(displacement+carry*dt)*decay;
        horizontalVelocity=(horizontalVelocity-omega*carry*dt)*decay;
        float fadeBlend=1f-(float)Math.exp(-dt/.160f);
        float scrimBlend=1f-(float)Math.exp(-dt/.200f);
        float alpha=content.getAlpha()+(targetAlpha-content.getAlpha())*fadeBlend;
        float dim=scrim.getAlpha()+(targetScrim-scrim.getAlpha())*scrimBlend;
        float scale=content.getScaleX()+(targetScale-content.getScaleX())*fadeBlend;
        content.setTranslationX(x);
        content.setAlpha(alpha);
        content.setScaleX(scale);content.setScaleY(scale);
        scrim.setAlpha(dim);

        boolean settled=Math.abs(x-targetX)<.6f&&Math.abs(horizontalVelocity)<4f
            &&Math.abs(alpha-targetAlpha)<.005f&&Math.abs(dim-targetScrim)<.005f
            &&Math.abs(scale-targetScale)<.0003f;
        if(settled){
            content.setTranslationX(targetX);content.setAlpha(targetAlpha);
            content.setScaleX(targetScale);content.setScaleY(targetScale);
            scrim.setAlpha(targetScrim);
            lastMotionFrame=0;horizontalVelocity=0f;
            if(closing)removeFromParent();
        }else requestMotion();
    }
    /** The opening is never mapped to the position of the finger. */
    void setReveal(float ignoredProgress){
        // Retained for source compatibility; gesture progress does not
        // control the visual reveal. Opening starts with beginReveal().
    }
    void finishReveal(boolean open){
        if(open)targetOpen();
        else{
            dismissed=true;
            targetClosed();
        }
    }
    /** Programmatic launch uses exactly the same continuous fade/slide. */
    void enter(){beginReveal();}
    void enterFrom(int direction){beginReveal();}
    void exitTo(int direction){
        if(dismissed)return;
        dismissed=true;targetClosed();
    }
    void dismiss(){exitTo(effectsMode?-1:1);}
    private void restorePosition(){targetOpen();}
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
            // A drag on an already-open wheel is only a navigation intent;
            // the menu never translates rigidly with the finger.
            return true;
        }
        if(e.getActionMasked()==MotionEvent.ACTION_UP){
            float dx=e.getX()-swipeStartX;
            boolean strong=Math.abs(dx)>Math.min(dp(64),getWidth()*.18f)
                && Math.abs(dx)>Math.abs(e.getY()-swipeStartY)*1.10f;
            horizontal=false;
            // Closing is separate: overlay gestures do not open another wheel.
            if(strong&&(effectsMode?dx<0:dx>0)&&navigate!=null)navigate.navigate(effectsMode?-1:1);
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
        private float angularVelocity;
        private boolean settling;
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
                text.setSingleLine(true);text.setEllipsize(android.text.TextUtils.TruncateAt.END);
                ImageView icon=new ImageView(context);
                icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                icon.setPadding(dp(20),dp(20),dp(20),dp(20));
                captions[i]=text;icons[i]=icon;
                // Insert after setMode so tools put the icon first (left),
                // effects put the icon last (right).
                row.setContentDescription(names[i]+", ferramenta orbital");
                row.setFocusable(true);row.setClickable(true);
                row.setOnClickListener(v->goTo(index));
                addView(row,new FrameLayout.LayoutParams(dp(220),dp(64)));
                rows[i]=row;
            }
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            post(this::render);
        }

        void updateItemOrientation(){
            for(int i=0;i<rows.length;i++){
                LinearLayout row=rows[i];
                TextView caption=captions[i];
                ImageView icon=icons[i];
                row.removeAllViews();
                caption.setGravity((effectsMode?Gravity.LEFT:Gravity.RIGHT)|Gravity.CENTER_VERTICAL);
                caption.setPadding(effectsMode?dp(9):0,0,effectsMode?0:dp(9),0);
                LinearLayout.LayoutParams textParams=new LinearLayout.LayoutParams(dp(136),dp(64));
                LinearLayout.LayoutParams iconParams=new LinearLayout.LayoutParams(dp(64),dp(64));
                if(effectsMode){
                    row.addView(icon,iconParams);
                    textParams.leftMargin=dp(8);row.addView(caption,textParams);
                }else{
                    row.addView(caption,textParams);
                    iconParams.leftMargin=dp(8);row.addView(icon,iconParams);
                }
            }
        }
        int selected(){return Math.max(0,Math.min(names.length-1,Math.round(offset)));}
        @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){super.onSizeChanged(w,h,oldW,oldH);radius=Math.max(w*.94f,dp(250));render();}
        private float arcX(float theta){
            float bend=radius*(1f-(float)Math.cos(theta));
            return effectsMode?dp(36)-bend:getWidth()-dp(44)+bend;
        }
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
            light.setShader(new RadialGradient(x,y,dp(96),
                new int[]{0x3bd8b4f8,0x14b58bcf,0x00b58bcf},null,Shader.TileMode.CLAMP));
            canvas.drawCircle(x,y,dp(96),light);
            light.setShader(null);light.setColor(EditorTheme.LIME);
            float markerX=x+(effectsMode?dp(40):-dp(40));
            canvas.drawRoundRect(markerX-dp(8),y-dp(2),markerX+dp(8),y+dp(2),dp(3),dp(3),light);
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
                // The selected icon remains adjacent to the actual arc on
                // its side; the complete row mirrors around the viewport.
                float x=arcX(theta)-(effectsMode?dp(32):dp(176));
                float y=arcY(theta)-dp(32);
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
            angularVelocity=0f;
            settling=false;
        }
        private void followOffset(){
            if(!rotationalFrameScheduled){
                rotationalFrameScheduled=true;
                Choreographer.getInstance().postFrameCallback(this);
            }
        }
        @Override public void doFrame(long nanos){
            rotationalFrameScheduled=false;
            if(getParent()==null){lastRotationalFrame=0;return;}
            float dt=lastRotationalFrame==0?.016f:
                Math.min(.05f,Math.max(.001f,(nanos-lastRotationalFrame)*1e-9f));
            lastRotationalFrame=nanos;
            // Analytic critically-damped spring, smooth in both drag and
            // coast-to-item phases. Unlike an interpolator reset at ACTION_UP,
            // it preserves angular velocity across the transition.
            float omega=settling?13.5f:19f;
            float displacement=offset-targetOffset;
            float carry=angularVelocity+omega*displacement;
            float decay=(float)Math.exp(-omega*dt);
            float newOffset=targetOffset+(displacement+carry*dt)*decay;
            float newVelocity=(angularVelocity-omega*carry*dt)*decay;
            offset=Math.max(0f,Math.min(names.length-1,newOffset));
            angularVelocity=newVelocity;
            render();
            if(Math.abs(offset-targetOffset)<.0025f&&Math.abs(angularVelocity)<.02f){
                offset=targetOffset;angularVelocity=0f;lastRotationalFrame=0;
                render();
            }else followOffset();
        }
        private void settle(float velocityY){
            float pixelsPerItem=Math.max(dp(60),radius*STEP);
            float momentum=Math.max(-2.3f,Math.min(2.3f,
                -velocityY*.14f/pixelsPerItem));
            targetOffset=Math.max(0,Math.min(names.length-1,
                Math.round(targetOffset+momentum)));
            settling=true;
            followOffset();
        }
        private void goTo(int index){
            if(index!=selected())performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            targetOffset=Math.max(0,Math.min(names.length-1,index));
            settling=true;
            followOffset();
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
