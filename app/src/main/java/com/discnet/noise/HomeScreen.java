package com.discnet.noise;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.animation.ValueAnimator;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;

/** Compact, responsive opening screen inspired by a softly lit physical sphere. */
final class HomeScreen extends FrameLayout {
    private final TextView recent;
    private final TextView resume;
    private final Runnable onImport,onCamera,onProjects,onSettings,onResume;
    HomeScreen(Context c,Runnable importAction,Runnable cameraAction,Runnable projectAction,Runnable settingsAction,Runnable resumeAction){
        super(c);
        onImport=importAction;onCamera=cameraAction;onProjects=projectAction;onSettings=settingsAction;onResume=resumeAction;
        setBackgroundColor(0xff0c0b10);
        // Decorative animated stock-art: reusable radial shaders simulate
        // defocused light smears and floating color clouds, no bitmaps/video.
        addView(new StockAtmosphere(c),new FrameLayout.LayoutParams(-1,-1));
        LinearLayout page=new LinearLayout(c);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24),dp(16),dp(24),dp(14));
        addView(page,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout header=new LinearLayout(c);
        header.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(header,new LinearLayout.LayoutParams(-1,dp(76)));
        LinearLayout titles=new LinearLayout(c);titles.setOrientation(LinearLayout.VERTICAL);
        header.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        TextView logo=text("Noise!",32,EditorTheme.INK);
        logo.setTypeface(NoiseTypography.bold(c));logo.setLetterSpacing(-.045f);
        titles.addView(logo);
        TextView subtitle=text("Create without limits.",13,EditorTheme.MUTED);
        titles.addView(subtitle);
        ImageView settings=IconArt.button(c,IconArt.SETTINGS,"Configurações");
        header.addView(settings,new LinearLayout.LayoutParams(dp(48),dp(48)));
        settings.setOnClickListener(v->onSettings.run());
        ImageView folders=IconArt.button(c,IconArt.FOLDER,"Meus projetos");
        header.addView(folders,new LinearLayout.LayoutParams(dp(48),dp(48)));
        folders.setOnClickListener(v->onProjects.run());

        FrameLayout orbArea=new FrameLayout(c);
        page.addView(orbArea,new LinearLayout.LayoutParams(-1,0,1));
        Sphere sphere=new Sphere(c);
        orbArea.addView(sphere,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout sphLabels=new LinearLayout(c);sphLabels.setOrientation(LinearLayout.VERTICAL);sphLabels.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER);
        orbArea.addView(sphLabels,lp);
        TextView small=text("NOISE! STUDIO",11,EditorTheme.MUTED);small.setLetterSpacing(.17f);small.setGravity(Gravity.CENTER);
        sphLabels.addView(small);
        TextView start=text("Nova\ncriação",32,EditorTheme.INK);start.setGravity(Gravity.CENTER);
        start.setTypeface(NoiseTypography.bold(c));start.setLetterSpacing(-.04f);
        sphLabels.addView(start);
        TextView hint=text("Toque para importar",12,EditorTheme.MUTED);hint.setGravity(Gravity.CENTER);
        hint.setPadding(0,dp(8),0,0);sphLabels.addView(hint);
        orbArea.setClickable(true);orbArea.setFocusable(true);
        orbArea.setContentDescription("Criar edição, importar uma imagem");
        orbArea.setOnClickListener(v->{orbArea.animate().scaleX(.97f).scaleY(.97f).setDuration(110).withEndAction(()->{
            orbArea.animate().scaleX(1).scaleY(1).setDuration(190).start();onImport.run();
        }).start();});

        LinearLayout lower=new LinearLayout(c);
        lower.setOrientation(LinearLayout.VERTICAL);
        lower.setPadding(dp(16),dp(16),dp(16),dp(14));
        lower.setBackground(EditorTheme.panel(c,0xb01b1721,0,20,0));
        page.addView(lower,new LinearLayout.LayoutParams(-1,-2));
        TextView recentTitle=text("Continue criando",19,EditorTheme.INK);
        recentTitle.setTypeface(NoiseTypography.bold(c));
        lower.addView(recentTitle);
        recent=text("Seu projeto recente aparecerá aqui.",13,EditorTheme.MUTED);
        recent.setMaxLines(1);recent.setEllipsize(android.text.TextUtils.TruncateAt.END);
        lower.addView(recent,new LinearLayout.LayoutParams(-1,dp(28)));
        resume=text("Nenhum projeto recente",13,EditorTheme.MUTED);
        resume.setGravity(Gravity.CENTER_VERTICAL);
        resume.setPadding(dp(16),0,dp(16),0);
        resume.setBackground(EditorTheme.buttonSurface(c,18,false));
        resume.setMinHeight(dp(48));
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(48));rp.bottomMargin=dp(12);
        lower.addView(resume,rp);
        resume.setEnabled(false);
        resume.setOnClickListener(v->onResume.run());
        FrameLayout add=new FrameLayout(c);
        android.graphics.drawable.GradientDrawable glass=new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            new int[]{0x82d2c0e8,0x476e617e,0x65847a95});
        glass.setCornerRadius(dp(3));glass.setStroke(dp(1),0xacc5b8d8);
        add.setBackground(glass);add.setClipToOutline(true);
        FrostedLight blurred=new FrostedLight(c);
        if(android.os.Build.VERSION.SDK_INT>=31)blurred.setRenderEffect(
            android.graphics.RenderEffect.createBlurEffect(dp(12),dp(12),
                Shader.TileMode.CLAMP));
        add.addView(blurred,new FrameLayout.LayoutParams(-1,-1));
        TextView importText=text("Importar imagem",15,0xfff7efff);
        importText.setTypeface(NoiseTypography.bold(c));importText.setGravity(Gravity.CENTER);
        add.addView(importText,new FrameLayout.LayoutParams(-1,-1));
        add.setClickable(true);add.setFocusable(true);
        add.setContentDescription("Importar imagem");
        // Two compact actions share the original import row.
        LinearLayout captureActions=new LinearLayout(c);
        captureActions.setGravity(Gravity.CENTER_VERTICAL);
        lower.addView(captureActions,new LinearLayout.LayoutParams(-1,dp(54)));
        captureActions.addView(add,new LinearLayout.LayoutParams(0,dp(54),1));
        TextView camera=text("Câmera",14,EditorTheme.INK);
        camera.setGravity(Gravity.CENTER);camera.setTypeface(NoiseTypography.bold(c));
        EditorTheme.button(camera,false);
        camera.setContentDescription("Abrir câmera com ajustes ao vivo");
        LinearLayout.LayoutParams camParams=new LinearLayout.LayoutParams(dp(104),dp(54));
        camParams.leftMargin=dp(10);captureActions.addView(camera,camParams);
        camera.setOnClickListener(v->onCamera.run());
        add.setOnClickListener(v->onImport.run());

        LinearLayout nav=new LinearLayout(c);nav.setGravity(Gravity.CENTER);
        page.addView(nav,new LinearLayout.LayoutParams(-1,dp(58)));
        navButton(nav,"Início",IconArt.PHOTO,()->{},true);
        navButton(nav,"Projetos",IconArt.FOLDER,onProjects,false);
        navButton(nav,"Editar",IconArt.RESIZE,onImport,false);
    }
    private int dp(float d){return EditorTheme.dp(getContext(),d);}
    private TextView text(String s,int size,int color){TextView t=new TextView(getContext());t.setTypeface(NoiseTypography.regular(getContext()));t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
    private void navButton(LinearLayout nav,String name,int icon,Runnable r,boolean selected){
        LinearLayout b=new LinearLayout(getContext());b.setGravity(Gravity.CENTER);b.setOrientation(LinearLayout.VERTICAL);
        ImageView image=new ImageView(getContext());
        image.setImageDrawable(new IconArt(icon,selected?EditorTheme.LIME:EditorTheme.MUTED));
        b.addView(image,new LinearLayout.LayoutParams(dp(19),dp(19)));
        TextView label=text(name,11,selected?EditorTheme.INK:EditorTheme.MUTED);label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(24));b.addView(label,p);
        nav.addView(b,new LinearLayout.LayoutParams(0,dp(54),1));
        b.setClickable(true);b.setFocusable(true);b.setContentDescription(name);
        b.setOnClickListener(v->r.run());
    }
    void setRecent(String name){
        boolean ready=name!=null&&!name.isEmpty();
        recent.setText(ready?"Seu último projeto está pronto para continuar.":"Importe uma imagem para começar.");
        resume.setText(ready?name+"   ›":"Nenhum projeto recente");
        resume.setEnabled(ready);resume.setTextColor(ready?EditorTheme.INK:EditorTheme.MUTED);
    }
    /** Soft light diffused under the translucent import button. */
    private static final class FrostedLight extends View{
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        FrostedLight(Context c){super(c);setClickable(false);}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            float w=getWidth(),h=getHeight();
            if(w<=0||h<=0)return;
            p.setShader(new RadialGradient(w*.28f,h*.25f,w*.70f,
                new int[]{0x88c7a3d7,0x406c5f90,0x00705a94},null,Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,p);
            p.setShader(null);
        }
    }
    /** Time-driven atmospheric composition. Animators resume when Home reappears. */
    private static final class StockAtmosphere extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix matrix=new Matrix();
        private final RadialGradient[] lights={
            new RadialGradient(0,0,1,new int[]{0xa37c4ead,0x67563885,0x16302049,0x00302049},
                new float[]{0,.32f,.71f,1},Shader.TileMode.CLAMP),
            new RadialGradient(0,0,1,new int[]{0xa37c52d9,0x5e7841a5,0x18392b62,0x00392b62},
                new float[]{0,.30f,.71f,1},Shader.TileMode.CLAMP),
            new RadialGradient(0,0,1,new int[]{0x818c6da1,0x487b5f90,0x133d314c,0x003d314c},
                new float[]{0,.28f,.72f,1},Shader.TileMode.CLAMP),
            new RadialGradient(0,0,1,new int[]{0x938c56bd,0x506c4591,0x152c233d,0x002c233d},
                new float[]{0,.31f,.70f,1},Shader.TileMode.CLAMP)
        };
        private final float[] dx={.10f,.83f,.21f,.84f};
        private final float[] dy={.22f,.23f,.72f,.72f};
        private final float[] sx={.49f,.46f,.40f,.50f};
        private final float[] sy={.24f,.31f,.28f,.29f};
        private ValueAnimator animator;
        private float phase;
        StockAtmosphere(Context c){super(c);setClickable(false);setFocusable(false);}
        @Override public void onVisibilityAggregated(boolean visible){
            super.onVisibilityAggregated(visible);
            if(visible)resumeMotion();else pauseMotion();
        }
        @Override protected void onAttachedToWindow(){
            super.onAttachedToWindow();if(isShown())resumeMotion();
        }
        @Override protected void onDetachedFromWindow(){
            pauseMotion();super.onDetachedFromWindow();
        }
        private void resumeMotion(){
            if(!ValueAnimator.areAnimatorsEnabled()||!isShown())return;
            if(animator==null){
                animator=ValueAnimator.ofFloat(0f,1f);
                animator.setDuration(18000);
                animator.setRepeatCount(ValueAnimator.INFINITE);
                animator.setInterpolator(new android.view.animation.LinearInterpolator());
                animator.addUpdateListener(a->{
                    phase=(Float)a.getAnimatedValue();postInvalidateOnAnimation();
                });
            }
            if(!animator.isStarted())animator.start();
            else if(animator.isPaused())animator.resume();
        }
        private void pauseMotion(){if(animator!=null&&animator.isStarted())animator.pause();}
        float phase(){return phase;}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float w=getWidth(),h=getHeight();
            if(w<=0||h<=0)return;
            for(int i=0;i<lights.length;i++){
                float t=phase*6.2831853f+i*1.6f;
                float cx=w*(dx[i]+.105f*(float)Math.sin(t));
                float cy=h*(dy[i]+.077f*(float)Math.cos(t*2f+i*.4f));
                float rx=w*sx[i],ry=h*sy[i];
                matrix.reset();matrix.setScale(rx,ry);matrix.postTranslate(cx,cy);
                lights[i].setLocalMatrix(matrix);paint.setShader(lights[i]);
                canvas.save();canvas.rotate((float)Math.sin(t+i*.31f)*22f,cx,cy);
                canvas.drawOval(cx-rx,cy-ry,cx+rx,cy+ry,paint);
                canvas.restore();
            }
            paint.setShader(null);
        }
    }
    /** A softly lit circular surface with one continuous rim and restrained motion. */
    private static final class Sphere extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix lightMatrix=new Matrix();
        private final Paint contour=new Paint(Paint.ANTI_ALIAS_FLAG);
        private RadialGradient base,atmosphere,highlight,reflectedLight;
        private SweepGradient rim;
        private ValueAnimator animator;
        private float phase,radius,rimWidth;
        Sphere(Context c){super(c);setLayerType(View.LAYER_TYPE_HARDWARE,null);}
        @Override public void onVisibilityAggregated(boolean visible){
            super.onVisibilityAggregated(visible);
            if(visible)resumeMotion();else pauseMotion();
        }
        @Override protected void onAttachedToWindow(){
            super.onAttachedToWindow();if(isShown())resumeMotion();
        }
        @Override protected void onDetachedFromWindow(){
            pauseMotion();super.onDetachedFromWindow();
        }
        private void resumeMotion(){
            if(!ValueAnimator.areAnimatorsEnabled()||!isShown())return;
            if(animator==null){
                animator=ValueAnimator.ofFloat(0f,1f);
                animator.setDuration(14000);
                animator.setRepeatCount(ValueAnimator.INFINITE);
                animator.setInterpolator(new android.view.animation.LinearInterpolator());
                animator.addUpdateListener(a->{phase=(Float)a.getAnimatedValue();
                    postInvalidateOnAnimation();});
            }
            if(!animator.isStarted())animator.start();
            else if(animator.isPaused())animator.resume();
        }
        private void pauseMotion(){if(animator!=null&&animator.isStarted())animator.pause();}
        float phase(){return phase;}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){
            super.onSizeChanged(w,h,ow,oh);
            float density=getResources().getDisplayMetrics().density;
            radius=Math.min(Math.min(w*.40f,h*.40f),188*density);
            if(radius<=0)return;
            float x=w*.5f,y=h*.5f,r=radius;
            rimWidth=Math.max(density*.9f,r*.008f);
            base=new RadialGradient(x-r*.25f,y-r*.43f,r*1.72f,
                new int[]{0xff896c9b,0xff483254,0xff211729,0xff100d17},
                new float[]{0f,.36f,.74f,1f},Shader.TileMode.CLAMP);
            // Concentrate the glow at the silhouette instead of washing out
            // the entire area around the circle. Cached shaders avoid blur work.
            atmosphere=new RadialGradient(x,y,r*1.25f,
                new int[]{0x00855ebf,0x00855ebf,0x609a60df,0x28855ebf,0x00855ebf},
                new float[]{0f,.66f,.80f,.89f,1f},Shader.TileMode.CLAMP);
            highlight=new RadialGradient(x-r*.42f,y-r*.40f,r*1.12f,
                new int[]{0x9ddfc4ff,0x4ab294dc,0x008767ba},
                new float[]{0f,.38f,1f},Shader.TileMode.CLAMP);
            reflectedLight=new RadialGradient(x+r*.43f,y+r*.46f,r*.97f,
                new int[]{0x65b77ea7,0x286e548c,0x006e548c},
                new float[]{0f,.42f,1f},Shader.TileMode.CLAMP);
            rim=new SweepGradient(x,y,
                new int[]{0xffab85d1,0xff9c77c1,0xffdfb5d5,0xfff0e2ff,0xffab85d1},
                new float[]{0f,.27f,.48f,.68f,1f});
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float x=getWidth()*.5f,y=getHeight()*.5f,r=radius;
            if(base==null||r<=0)return;
            float t=phase*6.2831853f;
            float breath=.5f-.5f*(float)Math.cos(t);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(atmosphere);
            paint.setAlpha(202+(int)(28*breath));
            canvas.drawCircle(x,y,r*1.25f,paint);
            paint.setShader(base);
            paint.setAlpha(255);
            canvas.drawCircle(x,y,r,paint);
            canvas.save();
            canvas.clipPath(circleClip(x,y,r));
            // Whole-period motion makes the loop seamless. The surface moves
            // gently while the circle and its text remain completely steady.
            float dx=(float)Math.sin(t)*r*.08f,dy=(float)Math.cos(t)*r*.06f;
            lightMatrix.reset();lightMatrix.setTranslate(dx,dy);
            highlight.setLocalMatrix(lightMatrix);
            paint.setShader(highlight);
            paint.setAlpha(198+(int)(22*breath));
            canvas.drawCircle(x,y,r,paint);
            lightMatrix.reset();lightMatrix.setTranslate(-dx*.62f,dy*.66f);
            reflectedLight.setLocalMatrix(lightMatrix);
            paint.setShader(reflectedLight);
            paint.setAlpha(154+(int)(16*breath));
            canvas.drawCircle(x,y,r,paint);
            canvas.restore();
            paint.setShader(null);paint.setAlpha(255);
            // One thin, continuous border; the soft halo is drawn behind it.
            contour.setStyle(Paint.Style.STROKE);
            contour.setPathEffect(null);
            lightMatrix.reset();lightMatrix.setRotate((float)Math.sin(t)*7f,x,y);
            rim.setLocalMatrix(lightMatrix);
            contour.setShader(rim);
            contour.setAlpha(230+(int)(12*breath));
            contour.setStrokeWidth(rimWidth);
            canvas.drawCircle(x,y,r,contour);
            contour.setShader(null);contour.setAlpha(255);
            contour.setStyle(Paint.Style.FILL);
        }
        private Path circleClip(float x,float y,float r){
            clip.reset();clip.addCircle(x,y,r,Path.Direction.CW);
            return clip;
        }
        private final Path clip=new Path();
    }
}
