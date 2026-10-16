package com.discnet.noise;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.animation.ValueAnimator;
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
    private boolean closing;
    private final Runnable closeWatchdog=()->{
        // A paused frame callback must never leave an invisible interception
        // layer over the editor after the wheel has been dismissed.
        if(closing&&getParent()!=null)removeFromParent();
    };
    private boolean effectsMode;
    private boolean motionScheduled;
    private long lastMotionFrame;
    private float targetX,targetAlpha,targetScale=1f,targetScrim,targetFeather;
    private float horizontalVelocity;
    private float revealFraction=1f;
    enum MenuState { CLOSED, OPENING, OPEN, CLOSING }
    private MenuState state=MenuState.CLOSED;
    MenuState menuState(){return state;}
    private final View scrim;
    private final View edgeFeather;
    private final LinearLayout content;
    private final TextView headerTitle,headerSubtitle;
    private final OrbitStage orbit;
    private final TextView continueButton,favorite;
    private final Selection callback;
    private final String[] names;
    private final int[] symbols;

    OrbitalSelector(Context c,String title,String subtitle,String[] names,int[] symbols,int initial,Selection callback){
        super(c);
        this.names=names.clone();this.symbols=symbols.clone();this.callback=callback;
        // Dim the ENTIRE editor, then layer a feathered edge tint to give
        // the orbit contrast without creating a sharply bounded rectangle.
        // Both overlays have independent, time-based fade animations.
        setBackgroundColor(Color.TRANSPARENT);
        // Allow the orbit to bend beyond its dock into the dimmed photo.
        // The root still clips naturally at the physical screen boundary.
        setClipChildren(false);setClipToPadding(false);
        setClickable(true);setFocusableInTouchMode(true);
        scrim=new View(c);
        scrim.setBackgroundColor(OrbitalMaterial.BLACK);
        scrim.setAlpha(0f);
        addView(scrim,new FrameLayout.LayoutParams(-1,-1));
        edgeFeather=new View(c);
        edgeFeather.setAlpha(0f);
        addView(edgeFeather,new FrameLayout.LayoutParams(-1,-1));
        content=new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        // The circular orbit legitimately extends past its narrow dock.
        // Clipping children here made the wheel look like a flat list and
        // produced straight cutoffs near the outer menu edges.
        content.setClipChildren(false);content.setClipToPadding(false);
        content.setPadding(dp(20),dp(12),dp(20),dp(16));
        // Solid, subtly stippled purple dock: no glass or transparency layer.
        content.setBackground(new OrbitalMaterial(c,OrbitalMaterial.DARK_PURPLE,6f,false));
        content.setAlpha(0f); // First frame is hidden: prevent a one-frame pop before swipe reveal.
        content.setScaleX(1f);content.setScaleY(1f);
        addView(content,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout column=content;

        LinearLayout header=new LinearLayout(c);
        header.setGravity(Gravity.TOP|Gravity.CENTER_VERTICAL);
        header.setPadding(dp(11),dp(9),dp(7),dp(9));
        header.setBackground(new OrbitalMaterial(c,OrbitalMaterial.BLACK,4f,false));
        column.addView(header,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout heading=new LinearLayout(c);heading.setOrientation(LinearLayout.VERTICAL);
        header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        favorite=label(c,"☆",29,OrbitalMaterial.BLACK);
        OrbitalMaterial favBlock=new OrbitalMaterial(c,OrbitalMaterial.WHITE,4f,true);
        favBlock.setStrength(1f);
        favorite.setBackground(favBlock);
        favorite.setGravity(Gravity.CENTER);favorite.setContentDescription("Marcar favorito");
        favorite.setFocusable(true);
        header.addView(favorite,new LinearLayout.LayoutParams(dp(48),dp(48)));
        favorite.setOnClickListener(v->toggleFavorite());
        favorite.setOnLongClickListener(v->{
            PopupMenu popup=new PopupMenu(c,favorite);
            boolean any=false;
            for(int i=0;i<this.names.length;i++)if(isFavorite(i)){
                final int index=i;popup.getMenu().add(this.names[i]).setOnMenuItemClickListener(item->{
                    selectFavorite(index);return true;
                });any=true;
            }
            if(any)popup.show();
            else favorite.setContentDescription("Nenhum favorito. Toque para favoritar a seleção.");
            return true;
        });
        headerTitle=label(c,title,30,OrbitalMaterial.WHITE);
        headerTitle.setTypeface(NoiseTypography.bold(c));
        headerTitle.setLetterSpacing(-.035f);headerTitle.setMaxLines(2);
        heading.addView(headerTitle);
        headerSubtitle=label(c,subtitle,13,OrbitalMaterial.MUTED);
        headerSubtitle.setPadding(0,dp(8),0,0);
        headerSubtitle.setMaxLines(3);heading.addView(headerSubtitle);
        // A roda nao tem botao de abertura/fechamento: deslize horizontal,
        // toque para escolher, ou o gesto Voltar do Android.

        orbit=new OrbitStage(c,initial);
        column.addView(orbit,new LinearLayout.LayoutParams(-1,0,1));
        TextView current=label(c,"↕  DESLIZE SUAVEMENTE   ·   TOQUE PARA SELECIONAR",10,OrbitalMaterial.WHITE);
        current.setBackground(new OrbitalMaterial(c,OrbitalMaterial.PURPLE,4f,false));
        current.setGravity(Gravity.CENTER);
        current.setLetterSpacing(.07f);
        column.addView(current,new LinearLayout.LayoutParams(-1,dp(32)));
        continueButton=label(c,"Selecionar "+names[orbit.selected()],16,OrbitalMaterial.BLACK);
        continueButton.setTypeface(NoiseTypography.bold(c));
        continueButton.setGravity(Gravity.CENTER);
        OrbitalMaterial selectSurface=new OrbitalMaterial(c,OrbitalMaterial.WHITE,4f,true);
        selectSurface.setStrength(1f);
        continueButton.setBackground(new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x2a7235ae),selectSurface,null));
        continueButton.setClickable(true);continueButton.setFocusable(true);
        column.addView(continueButton,new LinearLayout.LayoutParams(-1,dp(58)));
        continueButton.setOnClickListener(v->commit());
        orbit.onSelection=()->{
            continueButton.setText("Selecionar "+this.names[orbit.selected()]);
            updateFavorite();
        };
        updateFavorite();
    }
    private String favoriteKey(int index){return "orbital_fav_"+(effectsMode?"effect_":"tool_")+index;}
    private boolean isFavorite(int index){
        return getContext().getSharedPreferences("noise_favorites",Context.MODE_PRIVATE)
            .getBoolean(favoriteKey(index),false);
    }
    private void updateFavorite(){
        if(favorite==null||orbit==null)return;
        favorite.setText(isFavorite(orbit.selected())?"★":"☆");
        favorite.setContentDescription(isFavorite(orbit.selected())?"Remover dos favoritos":"Adicionar aos favoritos");
    }
    private void selectFavorite(int index){if(orbit!=null)orbit.goTo(index);}
    private void toggleFavorite(){
        int index=orbit.selected();boolean next=!isFavorite(index);
        getContext().getSharedPreferences("noise_favorites",Context.MODE_PRIVATE)
            .edit().putBoolean(favoriteKey(index),next).apply();
        favorite.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        updateFavorite();
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
            // Opaque menu dock plus a stronger purple-to-black boundary,
            // still feathered over the photograph outside the dock.
            new int[]{0xf01c0d30,0xdb1c0d30,0xaa30144e,0x6209070e,0x0009070e});
        gradient.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        edgeFeather.setBackground(gradient);
        headerTitle.setGravity(effects?Gravity.LEFT:Gravity.RIGHT);
        headerSubtitle.setGravity(effects?Gravity.LEFT:Gravity.RIGHT);
        updateDock(getWidth());
        orbit.updateItemOrientation();
        orbit.render();
        updateFavorite();
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
    float featherOpacity(){return edgeFeather.getAlpha();}
    boolean wholeScreenDim(){return scrim.getBackground() instanceof android.graphics.drawable.ColorDrawable;}
    int dockLeft(){return content.getLeft();}
    int dockRight(){return content.getRight();}
    float orbitalScreenX(){return orbitalIconScreenX(orbit.selected());}
    int orbitalSelected(){return orbit.selected();}
    int orbitalCount(){return names.length;}
    int orbitalHitIndex(float x,float y){return orbit.itemAt(x,y);}
    float orbitalIconScreenX(int index){
        LinearLayout row=orbit.rows[index];
        ImageView icon=orbit.icons[index];
        if(icon.getWidth()==0)return content.getLeft()+content.getPaddingLeft()+orbit.arcX(0f);
        return content.getLeft()+content.getPaddingLeft()+orbit.getLeft()
            +row.getX()+icon.getX()+icon.getWidth()/2f;
    }
    private float travelDistance(){
        // A restrained slide brings the wheel into view; a full-width
        // translation made the animation appear to jump into the screen.
        // Short, almost imperceptible drift. The old 116-164dp leap was
        // perceived as a "jump" when a gesture began or ended.
        return Math.min(dp(46),Math.max(dp(28),getWidth()*.085f));
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
        removeCallbacks(closeWatchdog);
        state=MenuState.OPENING;
        closing=false;revealFraction=1f;
        targetX=0f;
        targetAlpha=1f;
        targetScrim=.79f;
        targetFeather=.96f;
        targetScale=1f;
        requestMotion();
    }
    private void targetDrag(float fraction){
        if(closing||dismissed)return;
        removeCallbacks(closeWatchdog);
        state=MenuState.OPENING;
        revealFraction=Math.max(0f,Math.min(1f,fraction));
        // A minimum progressive darkening is present as soon as the edge
        // gesture is accepted; the remainder follows the hand with lag.
        float p=.12f+.88f*revealFraction;
        targetX=entrance()*(1f-p);
        targetAlpha=p;
        targetScrim=.79f*p;
        targetFeather=.96f*p;
        targetScale=1f; // no zoom/pop on finger down or release
        requestMotion();
    }
    private void targetClosed(){
        state=MenuState.CLOSING;
        closing=true;
        targetX=exitDirection();
        targetAlpha=0f;
        targetScrim=0f;
        targetFeather=0f;
        targetScale=1f;revealFraction=0f;
        removeCallbacks(closeWatchdog);
        postDelayed(closeWatchdog,1200);
        if(!ValueAnimator.areAnimatorsEnabled()){state=MenuState.CLOSED;removeFromParent();return;}
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
        content.animate().cancel();scrim.animate().cancel();edgeFeather.animate().cancel();
        horizontalVelocity=0f;
        content.setTranslationX(entrance());
        content.setAlpha(0f);
        content.setScaleX(.985f);content.setScaleY(.985f);
        scrim.setAlpha(0f);edgeFeather.setAlpha(0f);
        if(!ValueAnimator.areAnimatorsEnabled()){
            targetOpen();stopTracking();
            content.setTranslationX(0);content.setAlpha(1);content.setScaleX(1);content.setScaleY(1);
            scrim.setAlpha(targetScrim);edgeFeather.setAlpha(targetFeather);
            state=MenuState.OPEN;return;
        }
        // Dragging sets a moving target, not a pixel-perfect translation.
        targetDrag(0f);
    }
    @Override public void doFrame(long frameTimeNanos){
        motionScheduled=false;
        if(getParent()==null){lastMotionFrame=0;return;}
        float dt=lastMotionFrame==0?.016f:
            Math.min(.045f,Math.max(.001f,(frameTimeNanos-lastMotionFrame)*1e-9f));
        lastMotionFrame=frameTimeNanos;

        // Analytic critically damped spring. Unlike a view animation tied
        // to the pointer, this never jumps when a touch MOVE or UP arrives.
        final float omega=9.5f; // longer continuous settle for the whole menu
        float displacement=content.getTranslationX()-targetX;
        float carry=horizontalVelocity+omega*displacement;
        float decay=(float)Math.exp(-omega*dt);
        float x=targetX+(displacement+carry*dt)*decay;
        horizontalVelocity=(horizontalVelocity-omega*carry*dt)*decay;
        float fadeBlend=1f-(float)Math.exp(-dt/.205f);
        float scrimBlend=1f-(float)Math.exp(-dt/.240f);
        float alpha=content.getAlpha()+(targetAlpha-content.getAlpha())*fadeBlend;
        float dim=scrim.getAlpha()+(targetScrim-scrim.getAlpha())*scrimBlend;
        float feather=edgeFeather.getAlpha()+(targetFeather-edgeFeather.getAlpha())*scrimBlend;
        float scale=content.getScaleX()+(targetScale-content.getScaleX())*fadeBlend;
        content.setTranslationX(x);
        content.setAlpha(alpha);
        content.setScaleX(scale);content.setScaleY(scale);
        scrim.setAlpha(dim);edgeFeather.setAlpha(feather);

        boolean settled=Math.abs(x-targetX)<.6f&&Math.abs(horizontalVelocity)<4f
            &&Math.abs(alpha-targetAlpha)<.005f&&Math.abs(dim-targetScrim)<.005f
            &&Math.abs(feather-targetFeather)<.005f
            &&Math.abs(scale-targetScale)<.0003f;
        if(settled){
            state=closing?MenuState.CLOSED:
                revealFraction>=.999f?MenuState.OPEN:MenuState.OPENING;
            content.setTranslationX(targetX);content.setAlpha(targetAlpha);
            content.setScaleX(targetScale);content.setScaleY(targetScale);
            scrim.setAlpha(targetScrim);edgeFeather.setAlpha(targetFeather);
            lastMotionFrame=0;horizontalVelocity=0f;
            if(closing)removeFromParent();
        }else requestMotion();
    }
    /** Edge movement changes the target, while Choreographer supplies lag. */
    void setReveal(float progress){
        if(getParent()==null||closing||dismissed)return;
        targetDrag(progress);
    }
    float revealTargetX(){return targetX;}
    float revealTargetAlpha(){return targetAlpha;}
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
        if(!horizontal){
            if(e.getActionMasked()==MotionEvent.ACTION_UP){
                float x=e.getX();float left=content.getLeft(),right=content.getRight();
                boolean outside=effectsMode?x>right+dp(7):x<left-dp(7);
                if(outside&&navigate!=null&&state!=MenuState.CLOSING){
                    navigate.navigate(0);return true;
                }
            }
            return true; // consume taps: never click through into the preview
        }
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
        removeCallbacks(closeWatchdog);
        stopTracking();content.animate().cancel();scrim.animate().cancel();
        edgeFeather.animate().cancel();orbit.cancelMotion();orbit.cancelLiquid();
        super.onDetachedFromWindow();
    }

    /**
     * Drawable reused for each wheel icon. Both the selected hairline and
     * icon material opacity vary continuously with the fractional wheel offset.
     * There is no instant background replacement when the rounded index flips.
     */
    private static final class IconGlowFrame extends android.graphics.drawable.Drawable {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path=new Path();
        private final float scale;
        private final OrbitalMaterial box;
        private final boolean active;
        private float liquid=1f;
        private float strength;
        IconGlowFrame(Context c,boolean active){
            this.active=active;
            scale=c.getResources().getDisplayMetrics().density;
            box=new OrbitalMaterial(c,OrbitalMaterial.WHITE,4f,true);
        }
        boolean outlined(){return active&&strength>=.97f;}
        float strength(){return strength;}
        float liquidProgress(){return liquid;}
        void setStrength(float value){
            float next=Math.max(0f,Math.min(1f,value));
            if(Math.abs(next-strength)>.003f){
                strength=next;
                invalidateSelf();
            }
        }
        void setLiquid(float progress){
            float next=Math.max(0f,Math.min(1f,progress));
            if(Math.abs(liquid-next)>.003f){
                liquid=next;
                invalidateSelf();
            }
        }
        @Override public void draw(Canvas canvas){
            Rect b=getBounds();
            if(b.isEmpty())return;
            float l=b.left+scale*2.2f,t=b.top+scale*2.2f;
            float right=b.right-scale*2.2f,bottom=b.bottom-scale*2.2f;
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            // White filled square-corner button, dark glyph provided by IconArt.
            box.setBounds(b);
            box.setStrength(active?strength:0f);
            box.draw(canvas);
            if(!active||strength<=.001f)return;
            // The classic meniscus is retained but wraps the new near-square
            // button instead of drawing an oversized rounded liquid disk.
            float pulse=(float)Math.sin(Math.PI*liquid);
            float wave=scale*2.8f*pulse;
            float corner=scale*4f;
            path.reset();
            path.moveTo(l+corner,t-wave*.18f);
            path.lineTo(right-corner,t-wave*.18f);
            path.quadTo(right+wave*.28f,t-wave*.16f,right+wave*.30f,t+corner);
            path.lineTo(right+wave*.55f,bottom-corner);
            path.quadTo(right+wave*.25f,bottom+wave*.16f,right-corner,bottom+wave*.18f);
            path.lineTo(l+corner,bottom+wave*.18f);
            path.quadTo(l-wave*.25f,bottom+wave*.16f,l-wave*.30f,bottom-corner);
            path.lineTo(l-wave*.30f,t+corner);
            path.quadTo(l-wave*.25f,t-wave*.16f,l+corner,t-wave*.18f);
            path.close();
            // Premultiplied alpha: a neighboring icon gains its highlight
            // as it approaches center, while the previous fades out.
            p.setColor(Color.argb(Math.round(28f*strength),114,53,174));
            canvas.drawPath(path,p);
            p.setStyle(Paint.Style.STROKE);
            // Two soft translucent strokes form a visible white glow without blur.
            p.setStrokeWidth(scale*4.0f);
            p.setColor(Color.argb(Math.round(72f*strength),255,255,255));
            canvas.drawPath(path,p);
            p.setStrokeWidth(scale*1.25f);
            p.setColor(Color.argb(Math.round(255f*strength),255,255,255));
            canvas.drawPath(path,p);
            p.setStrokeWidth(scale*.75f);
            p.setColor(Color.argb(Math.round(90f*strength),255,255,255));
            canvas.drawArc(l+scale*6,t+scale*6,right-scale*6,bottom-scale*6,
                203,105,false,p);
            p.setStyle(Paint.Style.FILL);
        }
        @Override public void setAlpha(int alpha){p.setAlpha(alpha);invalidateSelf();}
        @Override public void setColorFilter(android.graphics.ColorFilter filter){
            p.setColorFilter(filter);invalidateSelf();
        }
        @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
    }

    private final class OrbitStage extends FrameLayout implements Choreographer.FrameCallback {
        private static final float STEP=.29f;
        private final Paint stroke=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint light=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final LiquidSelectorLayer liquidBridge=new LiquidSelectorLayer(getContext());
        private RadialGradient halo;
        private final LinearLayout[] rows=new LinearLayout[names.length];
        private final TextView[] captions=new TextView[names.length];
        private final ImageView[] icons=new ImageView[names.length];
        private final IconGlowFrame[] idleFrames=new IconGlowFrame[names.length];
        private final IconGlowFrame[] focusFrames=new IconGlowFrame[names.length];
        private final OrbitalMaterial[] captionBlocks=new OrbitalMaterial[names.length];
        private IconGlowFrame focusedFrame;
        private int framedIndex=-1;
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
                TextView text=label(context,names[i],15,OrbitalMaterial.WHITE);
                captionBlocks[i]=new OrbitalMaterial(context,
                    (i%2==0)?OrbitalMaterial.DEEP_PURPLE:OrbitalMaterial.PURPLE,4f,true);
                text.setBackground(captionBlocks[i]);
                text.setSingleLine(true);text.setEllipsize(android.text.TextUtils.TruncateAt.END);
                ImageView icon=new ImageView(context);
                icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                icon.setPadding(dp(20),dp(20),dp(20),dp(20));
                captions[i]=text;icons[i]=icon;
                idleFrames[i]=new IconGlowFrame(context,false);
                focusFrames[i]=new IconGlowFrame(context,true);
                icon.setBackground(idleFrames[i]);
                icon.setImageDrawable(new IconArt(symbols[Math.min(i,symbols.length-1)],OrbitalMaterial.BLACK));
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
                caption.setPadding(dp(12),0,dp(12),0);
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
        @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){
            super.onSizeChanged(w,h,oldW,oldH);
            // Stable radius preserves the round profile on narrow phones
            // without throwing most of the outer rim beyond the display.
            radius=Math.max(dp(175),Math.min(dp(235),w*.68f));
            halo=new RadialGradient(arcX(0f),arcY(0f),dp(96),
                new int[]{0x54ffffff,0x2948227c,0x0030184d},null,Shader.TileMode.CLAMP);
            render();
        }
        private float arcX(float theta){
            // Convex outward wheel: its selected item stays by its side,
            // while the items above and below curve TOWARD that side.
            // The previous signs made both wheel profiles face backward.
            // Keep true circular coordinates (no horizontal clamping).
            float bend=radius*(1f-(float)Math.cos(theta));
            float anchor=Math.min(dp(89),getWidth()*.29f);
            return effectsMode?anchor-bend:getWidth()-anchor+bend;
        }
        private float arcY(float theta){return getHeight()*.50f+radius*(float)Math.sin(theta);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            if(radius<=0)return;
            stroke.setShader(null);stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(dp(1));
            // Do not let the circle line terminate at the clip boundary:
            // the previous single Path created a conspicuous hard vertical
            // seam on the external edge of either menu.
            for(int j=1;j<=112;j++){
                float t0=-1.40f+2.80f*(j-1)/112f;
                float t1=-1.40f+2.80f*j/112f;
                float theta=(t0+t1)*.5f;
                float xm=arcX(theta),ym=arcY(theta);
                float edgeDistance=Math.min(xm,getWidth()-xm);
                float verticalDistance=Math.min(ym,getHeight()-ym);
                float xFade=Math.max(0f,Math.min(1f,edgeDistance/Math.max(1,dp(65))));
                float yFade=Math.max(0f,Math.min(1f,verticalDistance/Math.max(1,dp(70))));
                float endFade=Math.max(0f,Math.min(1f,(1.40f-Math.abs(theta))/.27f));
                float opacity=xFade*yFade*endFade;
                if(opacity<.01f)continue;
                stroke.setColor(Color.argb(Math.round(169f*opacity),164,113,209));
                canvas.drawLine(arcX(t0),arcY(t0),arcX(t1),arcY(t1),stroke);
            }
            float x=arcX(0),y=arcY(0);
            light.setStyle(Paint.Style.FILL);
            light.setShader(halo);
            canvas.drawCircle(x,y,dp(96),light);
            light.setShader(null);
            // Place the locator OUTSIDE the icon, not between icon and text:
            // Effects: left of icon; Tools: right of icon.
            // Short, low-contrast stroke should not dominate the selection.
            light.setColor(0xeaffffff);
            float markerX=x+(effectsMode?-dp(54):dp(54));
            canvas.drawRoundRect(markerX-dp(5),y-dp(.75f),
                markerX+dp(5),y+dp(.75f),dp(1),dp(1),light);
            // Project the two moving icon centers onto the real orbital curve.
            // One connected contour flows between them while the finger drags
            // or the wheel springs into place; reversing offset reverses flow.
            int from=(int)Math.floor(offset);
            if(from>=0&&from<names.length-1){
                float p=offset-from;
                liquidBridge.draw(canvas,
                    arcX((from-offset)*STEP),arcY((from-offset)*STEP),
                    arcX((from+1-offset)*STEP),arcY((from+1-offset)*STEP),
                    p,dp(27));
            }
        }
        private void render(){
            if(getWidth()==0||getHeight()==0)return;
            int active=selected();
            if(active!=highlighted){
                highlighted=active;
                for(int i=0;i<names.length;i++){
                    boolean on=i==active;
                    // Label color and icon material interpolate with offset
                    // below. No sudden typeface/paint swap at the halfway mark.
                    rows[i].setContentDescription(names[i]+(on?", selecionado":"")+". Toque para selecionar.");
                }
                if(onSelection!=null)onSelection.run();
            }
            updateAlignedFrame();
            for(int i=0;i<rows.length;i++){
                float theta=(i-offset)*STEP;
                float distance=Math.abs(i-offset);
                boolean visible=Math.abs(theta)<1.42f;
                LinearLayout row=rows[i];
                row.setVisibility(visible?VISIBLE:INVISIBLE);
                if(!visible)continue;
                // Anchor each icon to its real circular trajectory. The
                // caption travels with the icon; neither is clamped to a
                // constant X. Fade is used at the extremes instead of clipping.
                float x=arcX(theta)-(effectsMode?dp(32):dp(176));
                float y=arcY(theta)-dp(32);
                row.setTranslationX(x);row.setTranslationY(y);
                float proximity=Math.max(0f,1f-Math.min(1f,distance));
                float emphasis=proximity*proximity*(3f-2f*proximity);
                // Crossfade labels and icons continuously rather than switching
                // styles at selected()'s midpoint.
                captionBlocks[i].setStrength(emphasis);
                // Foreground reverses continuously from white to near-black
                // as its box changes from purple to white.
                captions[i].setTextColor(Color.rgb(
                    Math.round(248-236*emphasis),
                    Math.round(244-233*emphasis),
                    Math.round(253-238*emphasis)));
                icons[i].setImageAlpha(Math.round(192+63*emphasis));
                float terminalFade=Math.max(0f,Math.min(1f,(1.42f-Math.abs(theta))/.32f));
                float topBottom=Math.min(y,getHeight()-y-dp(64));
                float viewportFade=Math.max(0f,Math.min(1f,(topBottom+dp(32))/dp(72)));
                // Fade when the icon approaches the physical edge; its
                // horizontal path still follows the circle exactly.
                float iconX=arcX(theta);
                float distanceToSide=effectsMode?iconX:getWidth()-iconX;
                float sideFade=Math.max(0f,Math.min(1f,(distanceToSide+dp(30))/dp(90)));
                row.setAlpha(Math.max(0f,1-distance*.18f)
                    *terminalFade*viewportFade*sideFade);
                float scale=.83f+.17f*emphasis;
                row.setScaleX(scale);row.setScaleY(scale);
            }
            invalidate();
        }
        /** At rest only the exactly centered icon has a full white rim.
         *  In motion the source fades out as the receiver fades in.
         */
        private int alignedIndex(){
            int nearest=selected();
            return Math.abs(offset-nearest)<=.10f?nearest:-1;
        }
        private void cancelLiquid(){
            // No independent animator to cancel: material and metaball follow
            // the same continuous wheel coordinate.
        }
        private static float fadeStrength(float distance){
            float t=Math.max(0f,Math.min(1f,1f-distance));
            return t*t*(3f-2f*t);
        }
        private void updateAlignedFrame(){
            framedIndex=alignedIndex();
            focusedFrame=framedIndex>=0?focusFrames[framedIndex]:null;
            for(int i=0;i<icons.length;i++){
                float intensity=fadeStrength(Math.abs(i-offset));
                IconGlowFrame desired=intensity>.003f?focusFrames[i]:idleFrames[i];
                if(icons[i].getBackground()!=desired)icons[i].setBackground(desired);
                focusFrames[i].setStrength(intensity);
                // Keep geometry attached to wheel movement rather than
                // restarting a 455ms animator at each snapped icon.
                focusFrames[i].setLiquid(1f);
            }
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
            float omega=settling?8.1f:10.6f; // viscous, velocity-continuous settling
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
            float momentum=Math.max(-1.65f,Math.min(1.65f,
                -velocityY*.10f/pixelsPerItem));
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
            return true; // unify orbit drag and icon hit-testing
        }
        /** Real transformed icon bounds: layout + translation + scale + pivot. */
        private int hitIndex(float x,float y){
            int hit=-1;float best=Float.MAX_VALUE;
            for(int i=0;i<rows.length;i++){
                LinearLayout row=rows[i];
                if(row.getVisibility()!=VISIBLE||row.getAlpha()<.12f)continue;
                ImageView icon=icons[i];float sx=row.getScaleX(),sy=row.getScaleY();
                float px=row.getPivotX(),py=row.getPivotY();
                float left=row.getX()+px+(icon.getX()-px)*sx;
                float right=left+icon.getWidth()*sx;
                float top=row.getY()+py+(icon.getY()-py)*sy;
                float bottom=top+icon.getHeight()*sy;
                float margin=dp(11);
                if(x>=left-margin&&x<=right+margin&&y>=top-margin&&y<=bottom+margin){
                    float cx=(left+right)*.5f,cy=(top+bottom)*.5f;
                    float distance=(x-cx)*(x-cx)+(y-cy)*(y-cy);
                    if(distance<best){hit=i;best=distance;}
                }
            }
            return hit;
        }
        int itemAt(float x,float y){return hitIndex(x,y);}
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
                        int hit=hitIndex(ev.getX(),ev.getY());
                        if(hit>=0)goTo(hit);else settle(0);
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
