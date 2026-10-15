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
    private final Runnable onImport,onProjects,onResume;
    HomeScreen(Context c,Runnable importAction,Runnable projectAction,Runnable resumeAction){
        super(c);
        onImport=importAction;onProjects=projectAction;onResume=resumeAction;
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
        lower.setBackground(EditorTheme.surface(c,30));
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
        TextView add=text("Importar imagem",16,0xff17121d);
        add.setTypeface(NoiseTypography.bold(c));
        add.setGravity(Gravity.CENTER);add.setBackground(EditorTheme.buttonSurface(c,11,true));
        add.setClickable(true);add.setFocusable(true);
        lower.addView(add,new LinearLayout.LayoutParams(-1,dp(54)));
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
                float t=phase*6.2831853f*(1+i*.13f)+i*1.6f;
                float cx=w*(dx[i]+.105f*(float)Math.sin(t));
                float cy=h*(dy[i]+.077f*(float)Math.cos(t*.87f));
                float rx=w*sx[i],ry=h*sy[i];
                matrix.reset();matrix.setScale(rx,ry);matrix.postTranslate(cx,cy);
                lights[i].setLocalMatrix(matrix);paint.setShader(lights[i]);
                canvas.save();canvas.rotate((float)Math.sin(t*.83f)*22f,cx,cy);
                canvas.drawOval(cx-rx,cy-ry,cx+rx,cy+ry,paint);
                canvas.restore();
            }
            paint.setShader(null);
        }
    }
    private static final class Sphere extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private RadialGradient sphereShader,aura;
        private ValueAnimator animator;
        private float phase,radius;
        Sphere(Context c){super(c);setLayerType(View.LAYER_TYPE_HARDWARE,null);}
        @Override public void onVisibilityAggregated(boolean visible){
            super.onVisibilityAggregated(visible);
            if(visible)resumeMotion();else pauseMotion();
        }
        @Override protected void onAttachedToWindow(){
            super.onAttachedToWindow();if(isShown())resumeMotion();
        }
        @Override protected void onDetachedFromWindow(){pauseMotion();super.onDetachedFromWindow();}
        private void resumeMotion(){
            if(!ValueAnimator.areAnimatorsEnabled()||!isShown())return;
            if(animator==null){
                animator=ValueAnimator.ofFloat(0f,1f);
                animator.setDuration(13500);
                animator.setRepeatCount(ValueAnimator.INFINITE);
                animator.setInterpolator(new android.view.animation.LinearInterpolator());
                animator.addUpdateListener(a->{phase=(Float)a.getAnimatedValue();postInvalidateOnAnimation();});
            }
            if(!animator.isStarted())animator.start();
            else if(animator.isPaused())animator.resume();
        }
        private void pauseMotion(){if(animator!=null&&animator.isStarted())animator.pause();}
        float phase(){return phase;}
        @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){
            super.onSizeChanged(w,h,oldW,oldH);
            float d=getResources().getDisplayMetrics().density;
            radius=Math.min(Math.min(w*.40f,h*.39f),190*d);
            float cx=w*.5f,cy=h*.5f;
            if(radius>0){
                sphereShader=new RadialGradient(cx-radius*.25f,cy-radius*.36f,
                    radius*1.68f,new int[]{0xff82769a,0xff443753,0xff17101e,0xff080711},
                    new float[]{0,.33f,.71f,1},Shader.TileMode.CLAMP);
                aura=new RadialGradient(cx,cy,radius*1.32f,
                    new int[]{0x4db17bed,0x239766cd,0x00452c79},
                    new float[]{0,.56f,1},Shader.TileMode.CLAMP);
            }
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float cx=getWidth()*.5f,cy=getHeight()*.5f,r=radius;
            if(r<=0||sphereShader==null)return;
            float angle=phase*360f;
            paint.setStyle(Paint.Style.FILL);paint.setShader(aura);
            canvas.drawCircle(cx,cy,r*1.32f,paint);
            paint.setShader(sphereShader);
            canvas.drawCircle(cx,cy,r,paint);paint.setShader(null);
            // Kinetic double-orbit contour and travelling luminous nodes.
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(Math.max(1f,r*.008f));
            paint.setColor(0xa6dac3f2);canvas.drawCircle(cx,cy,r*.992f,paint);
            paint.setColor(0x62a38cd1);canvas.drawCircle(cx,cy,r*.83f,paint);
            paint.setColor(0x40dfbfff);canvas.drawCircle(cx,cy,r*.67f,paint);
            paint.setStrokeWidth(Math.max(2f,r*.022f));paint.setColor(0xa8d4a2ff);
            canvas.drawArc(cx-r*.995f,cy-r*.995f,cx+r*.995f,cy+r*.995f,
                angle,61,false,paint);
            paint.setColor(0x8096b6ff);paint.setStrokeWidth(Math.max(1f,r*.012f));
            canvas.drawArc(cx-r*.83f,cy-r*.83f,cx+r*.83f,cy+r*.83f,
                -angle*.67f,107,false,paint);
            paint.setColor(0xa8e3b8f5);paint.setStrokeWidth(Math.max(1f,r*.016f));
            canvas.drawArc(cx-r*.67f,cy-r*.67f,cx+r*.67f,cy+r*.67f,
                angle*.39f+90,37,false,paint);
            paint.setStyle(Paint.Style.FILL);
            for(int i=0;i<12;i++){
                double t=Math.toRadians(angle*.18f+i*30);
                float px=cx+(float)Math.cos(t)*r*.97f;
                float py=cy+(float)Math.sin(t)*r*.97f;
                paint.setColor(i%3==0?0xd7e5c3ff:0x668e74b0);
                canvas.drawCircle(px,py,Math.max(1.2f,r*(i%3==0?.014f:.007f)),paint);
            }
            for(int i=0;i<3;i++){
                double t=Math.toRadians(angle*(i%2==0?1.0f:-.63f)+i*114);
                float rr=r*(i==0?.83f:i==1?.67f:.99f);
                float px=cx+(float)Math.cos(t)*rr,py=cy+(float)Math.sin(t)*rr;
                paint.setColor(i==0?0xffe7d1ff:0xffa8b1fa);
                canvas.drawCircle(px,py,r*.023f,paint);
            }
        }
    }
}
