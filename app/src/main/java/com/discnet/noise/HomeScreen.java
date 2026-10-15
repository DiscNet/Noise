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
    /** Ambient, blurred-looking abstract motion graphics behind home UI. */
    private static final class StockAtmosphere extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private final Matrix transform=new Matrix();
        private final RadialGradient[] lights={
            new RadialGradient(0,0,1,
                new int[]{0x827b58a6,0x32554074,0x0b2d203e,0x002d203e},
                new float[]{0,.32f,.68f,1},Shader.TileMode.CLAMP),
            new RadialGradient(0,0,1,
                new int[]{0x677f50d1,0x287e49a0,0x0843315d,0x0043315d},
                new float[]{0,.28f,.70f,1},Shader.TileMode.CLAMP),
            new RadialGradient(0,0,1,
                new int[]{0x647f617f,0x286e5473,0x0b3e344d,0x003e344d},
                new float[]{0,.3f,.71f,1},Shader.TileMode.CLAMP),
            new RadialGradient(0,0,1,
                new int[]{0x5b9f76b1,0x20835f8b,0x082d273e,0x002d273e},
                new float[]{0,.27f,.66f,1},Shader.TileMode.CLAMP),
            new RadialGradient(0,0,1,
                new int[]{0x285d5276,0x125a4269,0x00332249},
                new float[]{0,.45f,1},Shader.TileMode.CLAMP)
        };
        private final float[] dx={.13f,.84f,.26f,.79f,.47f};
        private final float[] dy={.27f,.18f,.61f,.69f,.49f};
        private final float[] sx={.40f,.45f,.41f,.50f,.67f};
        private final float[] sy={.23f,.27f,.28f,.20f,.18f};
        private long begin;
        StockAtmosphere(Context c){super(c);setClickable(false);setFocusable(false);}
        @Override protected void onAttachedToWindow(){
            super.onAttachedToWindow();begin=SystemClock.uptimeMillis();
            postInvalidateOnAnimation();
        }
        @Override protected void onDetachedFromWindow(){
            removeCallbacks(refresh);super.onDetachedFromWindow();
        }
        private final Runnable refresh=this::postInvalidateOnAnimation;
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float w=getWidth(),h=getHeight();
            if(w<=0||h<=0)return;
            boolean animate=ValueAnimator.areAnimatorsEnabled()&&isShown();
            float time=animate?(SystemClock.uptimeMillis()-begin)/1000f:0f;
            for(int i=0;i<lights.length;i++){
                float t=time*(.16f+i*.034f)+i*1.31f;
                float cx=w*(dx[i]+.052f*(float)Math.sin(t));
                float cy=h*(dy[i]+.037f*(float)Math.cos(t*.78f));
                float rx=w*sx[i],ry=h*sy[i];
                transform.reset();transform.setScale(rx,ry);transform.postTranslate(cx,cy);
                lights[i].setLocalMatrix(transform);
                paint.setShader(lights[i]);
                canvas.save();canvas.rotate((float)Math.sin(t*.53f)*17f,cx,cy);
                canvas.drawOval(cx-rx,cy-ry,cx+rx,cy+ry,paint);
                canvas.restore();
            }
            paint.setShader(null);
            // Refresh at a restrained rate: lightweight ambient motion should
            // not compete with GPU processing or interactive editor controls.
            if(animate&&getWindowVisibility()==VISIBLE){
                removeCallbacks(refresh);
                postDelayed(refresh,40);
            }
        }
    }
    private static final class Sphere extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Sphere(Context c){super(c);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float cx=getWidth()/2f,cy=getHeight()/2f;
            float density=getResources().getDisplayMetrics().density;
            float r=Math.min(Math.min(getWidth()*.45f,getHeight()*.44f),210*density);
            if(r<=0)return;
            p.setStyle(Paint.Style.FILL);
            p.setShader(new RadialGradient(cx-r*.3f,cy-r*.55f,r*1.5f,
                new int[]{0xff595063,0xff2c2838,0xff15141b},
                new float[]{0f,.48f,1f},Shader.TileMode.CLAMP));
            canvas.drawCircle(cx,cy,r,p);
            p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.2f*density);
            p.setColor(0x88998eac);canvas.drawCircle(cx,cy,r-.5f*density,p);
            p.setStrokeWidth(4f*density);p.setColor(0x247f6e9b);
            canvas.drawArc(cx-r,cy-r,cx+r,cy+r,205,148,false,p);
            p.setStyle(Paint.Style.FILL);
        }
    }
}
