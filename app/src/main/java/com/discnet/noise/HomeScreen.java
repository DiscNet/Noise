package com.discnet.noise;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.animation.ValueAnimator;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;

/** Compact, responsive opening screen inspired by a softly lit physical sphere. */
final class HomeScreen extends FrameLayout {
    private final TextView recent;
    private final TextView resume;
    private final LinearLayout resumeGroup;
    private final StockAtmosphere backdrop;
    private final Runnable onImport,onCamera,onProjects,onSettings,onResume;
    HomeScreen(Context c,Runnable importAction,Runnable cameraAction,Runnable projectAction,Runnable settingsAction,Runnable resumeAction){
        super(c);
        onImport=importAction;onCamera=cameraAction;onProjects=projectAction;onSettings=settingsAction;onResume=resumeAction;
        setBackgroundColor(0xff0c0b10);
        // Decorative animated stock-art: reusable radial shaders simulate
        // defocused light smears and floating color clouds, no bitmaps/video.
        backdrop=new StockAtmosphere(c);
        addView(backdrop,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout page=new LinearLayout(c);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20),dp(11),dp(20),dp(8));
        addView(page,new FrameLayout.LayoutParams(-1,-1));

        // The identity is built from one visual vocabulary:
        // deep graphite, lilac photographic light, thin orbital traces and warm-white type.
        LinearLayout header=new LinearLayout(c);
        header.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(header,new LinearLayout.LayoutParams(-1,dp(82)));
        LinearLayout brand=new LinearLayout(c);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(brand,new LinearLayout.LayoutParams(0,-1,1));
        TextView eyebrow=text("FOTOGRAFIA  ·  EDIÇÃO  ·  EXPERIMENTAÇÃO",9,0xffbba7cc);
        eyebrow.setLetterSpacing(.12f);
        brand.addView(eyebrow);
        TextView logo=text("Noise!",34,EditorTheme.INK);
        logo.setTypeface(NoiseTypography.bold(c));logo.setLetterSpacing(-.055f);
        brand.addView(logo);
        TextView subtitle=text("Ferramentas criativas para sua imagem.",12,0xffb1a8b8);
        brand.addView(subtitle);
        ImageView folders=IconArt.button(c,IconArt.FOLDER,"Meus projetos");
        LinearLayout.LayoutParams folderParams=new LinearLayout.LayoutParams(dp(44),dp(44));
        folderParams.rightMargin=dp(4);
        header.addView(folders,folderParams);
        homeHeaderButton(folders);
        folders.setOnClickListener(v->onProjects.run());
        ImageView settings=IconArt.button(c,IconArt.SETTINGS,"Configurações");
        header.addView(settings,new LinearLayout.LayoutParams(dp(44),dp(44)));
        homeHeaderButton(settings);
        settings.setOnClickListener(v->onSettings.run());

        FrameLayout orbArea=new FrameLayout(c);
        LinearLayout.LayoutParams orbParams=new LinearLayout.LayoutParams(-1,0,1);
        orbParams.topMargin=dp(4);
        orbParams.bottomMargin=dp(4);
        page.addView(orbArea,orbParams);
        Sphere sphere=new Sphere(c);
        orbArea.addView(sphere,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout sphLabels=new LinearLayout(c);
        sphLabels.setOrientation(LinearLayout.VERTICAL);
        sphLabels.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams centerLabels=new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER);
        orbArea.addView(sphLabels,centerLabels);
        TextView small=text("NOISE! STUDIO",10,0xffd8c2df);
        small.setLetterSpacing(.13f);small.setGravity(Gravity.CENTER);
        sphLabels.addView(small);
        TextView start=text("Nova\ncriação",31,EditorTheme.INK);
        start.setGravity(Gravity.CENTER);
        start.setTypeface(NoiseTypography.bold(c));start.setLetterSpacing(-.05f);
        sphLabels.addView(start);
        TextView hint=text("Toque para importar",12,0xffd6c3de);
        hint.setGravity(Gravity.CENTER);hint.setPadding(0,dp(9),0,0);
        sphLabels.addView(hint);

        // On compact displays keep the artwork legible rather than packing
        // ornamental labels and large type around a smaller hero circle.
        orbArea.addOnLayoutChangeListener((view,l,t,r,b,ol,ot,oright,obottom)->{
            int available=b-t;
            boolean compact=available<dp(245);
            boolean veryCompact=available<dp(175);
            // Keep decoration out of the hero; scale only meaningful labels.
            start.setTextSize(veryCompact?23:compact?27:31);
            small.setVisibility(veryCompact?View.GONE:View.VISIBLE);
            hint.setVisibility(veryCompact?View.GONE:View.VISIBLE);
        });

        orbArea.setClickable(true);orbArea.setFocusable(true);
        orbArea.setContentDescription("Nova criação: importar imagem para edição");
        orbArea.setOnClickListener(v->{
            orbArea.animate().cancel();
            orbArea.animate().scaleX(.975f).scaleY(.975f).setDuration(95).withEndAction(()->{
                orbArea.animate().scaleX(1f).scaleY(1f).setDuration(180).start();
                onImport.run();
            }).start();
        });

        LinearLayout lower=new LinearLayout(c);
        lower.setOrientation(LinearLayout.VERTICAL);
        lower.setPadding(dp(15),dp(12),dp(15),dp(13));
        lower.setBackground(new TexturedGlass(c,backdrop,lower,12,0xff211b29,.43f));
        page.addView(lower,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout sectionHeader=new LinearLayout(c);
        sectionHeader.setGravity(Gravity.CENTER_VERTICAL);
        lower.addView(sectionHeader,new LinearLayout.LayoutParams(-1,dp(33)));
        TextView section=text("Comece por aqui",17,EditorTheme.INK);
        section.setTypeface(NoiseTypography.bold(c));
        sectionHeader.addView(section,new LinearLayout.LayoutParams(0,-2,1));
        TextView tag=eyebrow("STUDIO / 01",9);
        tag.setBackgroundColor(Color.TRANSPARENT);
        sectionHeader.addView(tag);

        // These two surfaces are real paths into the editor and camera.
        LinearLayout entryRow=new LinearLayout(c);
        entryRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams entryParams=new LinearLayout.LayoutParams(-1,dp(77));
        entryParams.topMargin=dp(4);
        lower.addView(entryRow,entryParams);
        FrameLayout importTile=actionTile(c,IconArt.PHOTO,"Editar foto",
            "Luz, cor e textura",true,onImport);
        LinearLayout.LayoutParams importParams=new LinearLayout.LayoutParams(0,-1,1);
        importParams.rightMargin=dp(5);
        entryRow.addView(importTile,importParams);
        FrameLayout cameraTile=actionTile(c,IconArt.CAMERA,"Câmera",
            "Capture e experimente",false,onCamera);
        LinearLayout.LayoutParams cameraParams=new LinearLayout.LayoutParams(0,-1,1);
        cameraParams.leftMargin=dp(5);
        entryRow.addView(cameraTile,cameraParams);

        resumeGroup=new LinearLayout(c);
        resumeGroup.setOrientation(LinearLayout.VERTICAL);
        resumeGroup.setVisibility(View.GONE);
        LinearLayout.LayoutParams recentParams=new LinearLayout.LayoutParams(-1,-2);
        recentParams.topMargin=dp(9);
        lower.addView(resumeGroup,recentParams);
        LinearLayout resumeHead=new LinearLayout(c);
        resumeHead.setGravity(Gravity.CENTER_VERTICAL);
        resumeGroup.addView(resumeHead,new LinearLayout.LayoutParams(-1,dp(24)));
        TextView resumeTitle=text("CONTINUE CRIANDO",10,0xffc6b3d5);
        resumeTitle.setLetterSpacing(.15f);
        resumeHead.addView(resumeTitle,new LinearLayout.LayoutParams(0,-2,1));
        recent=text("Seus projetos locais",11,EditorTheme.MUTED);
        recent.setMaxLines(1);recent.setEllipsize(android.text.TextUtils.TruncateAt.END);
        resumeHead.addView(recent);
        resume=text("",13,EditorTheme.INK);
        resume.setTypeface(NoiseTypography.medium(c));
        resume.setGravity(Gravity.CENTER_VERTICAL);
        resume.setPadding(dp(13),0,dp(13),0);
        resume.setBackground(new TexturedGlass(c,backdrop,resume,8,0xff24202c,.39f));
        resume.setMinHeight(dp(43));
        resume.setSingleLine(true);
        resume.setEllipsize(android.text.TextUtils.TruncateAt.END);
        resumeGroup.addView(resume,new LinearLayout.LayoutParams(-1,dp(43)));
        resume.setOnClickListener(v->onResume.run());
        resume.setContentDescription("Retomar último projeto");

        LinearLayout nav=new LinearLayout(c);
        nav.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams navParams=new LinearLayout.LayoutParams(-1,dp(49));
        navParams.topMargin=dp(5);
        page.addView(nav,navParams);
        navButton(nav,"Início",IconArt.PHOTO,()->{},true);
        navButton(nav,"Biblioteca",IconArt.GALLERY,onImport,false);
        navButton(nav,"Projetos",IconArt.FOLDER,onProjects,false);
    }
    /** All noncircular Home surfaces have the same 12dp optical radius. */
    private GradientDrawable homeSurface(int color,float radius){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);d.setCornerRadius(dp(radius));
        d.setStroke(dp(1),0x66433a4b);
        return d;
    }
    private void homeHeaderButton(ImageView icon){
        icon.setBackground(new RippleDrawable(ColorStateList.valueOf(0x24ffffff),
            new TexturedGlass(icon.getContext(),backdrop,icon,8,0xff201d28,.41f),null));
    }
    private FrameLayout actionTile(Context c,int icon,String title,String subtitle,
                                   boolean primary,Runnable action){
        FrameLayout tile=new FrameLayout(c);
        // A shared material grammar: sampled blurred artwork, micro-refraction,
        // subdued warm/cool reflections. No unrelated translucent rectangles.
        TexturedGlass material=new TexturedGlass(c,backdrop,tile,10,
            primary?0xff695577:0xff24202b,primary?.36f:.50f);
        tile.setBackground(new RippleDrawable(
            ColorStateList.valueOf(primary?0x29ffffff:0x1bffffff),material,null));
        tile.setClickable(true);
        tile.setFocusable(true);
        tile.setContentDescription(title+": "+subtitle);
        LinearLayout column=new LinearLayout(c);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_VERTICAL);
        column.setPadding(dp(12),dp(7),dp(12),dp(7));
        tile.addView(column,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout line=new LinearLayout(c);line.setGravity(Gravity.CENTER_VERTICAL);
        column.addView(line,new LinearLayout.LayoutParams(-1,dp(25)));
        ImageView glyph=new ImageView(c);
        glyph.setImageDrawable(new IconArt(icon,primary?Color.WHITE:0xffd9bedf));
        glyph.setContentDescription(title);
        line.addView(glyph,new LinearLayout.LayoutParams(dp(18),dp(18)));
        TextView arrow=text("↗",15,primary?0xfff4e9fa:0xffb89ec9);
        arrow.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        line.addView(arrow,new LinearLayout.LayoutParams(0,-1,1));
        TextView label=text(title,14,EditorTheme.INK);
        label.setTypeface(NoiseTypography.bold(c));
        column.addView(label);
        TextView detail=text(subtitle,10,primary?0xfff4e8fb:0xffb8aebf);
        detail.setSingleLine(true);
        detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
        column.addView(detail);
        tile.setOnClickListener(v->action.run());
        return tile;
    }
    private TextView eyebrow(String value,int size){
        TextView t=text(value,size,0xffc1a9d1);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setLetterSpacing(.12f);
        t.setPadding(dp(7),0,dp(7),0);
        return t;
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
        boolean ready=name!=null&&!name.trim().isEmpty();
        // No large disabled "empty project" card on a first launch.
        resumeGroup.setVisibility(ready?View.VISIBLE:View.GONE);
        recent.setText(ready?"Seu último projeto":"");
        resume.setText(ready?name+"    ›":"");
        resume.setEnabled(ready);
        resume.setContentDescription(ready?"Retomar projeto "+name:"Nenhum projeto recente");
    }
    /**
     * Historical name is kept for compatibility with older home-state tests.
     * The actual multi-layer ink renderer lives in the reusable OrganicBackdrop.
     */
    private static final class StockAtmosphere extends OrganicBackdrop {
        private float phase;
        StockAtmosphere(Context c){super(c);}
        @Override protected void onDraw(Canvas canvas){
            phase=phaseValue();
            super.onDraw(canvas);
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
            rimWidth=Math.max(density*.85f,r*.0065f);
            base=new RadialGradient(x-r*.25f,y-r*.43f,r*1.72f,
                new int[]{0xff74657c,0xff3c3047,0xff211b29,0xff100e16},
                new float[]{0f,.36f,.74f,1f},Shader.TileMode.CLAMP);
            // Concentrate the glow at the silhouette instead of washing out
            // the entire area around the circle. Cached shaders avoid blur work.
            atmosphere=new RadialGradient(x,y,r*1.25f,
                new int[]{0x00855ebf,0x00855ebf,0x358c76b5,0x16855ebf,0x00855ebf},
                new float[]{0f,.66f,.80f,.89f,1f},Shader.TileMode.CLAMP);
            highlight=new RadialGradient(x-r*.42f,y-r*.40f,r*1.12f,
                new int[]{0x79e2d5ec,0x39b5a7cc,0x008767ba},
                new float[]{0f,.38f,1f},Shader.TileMode.CLAMP);
            reflectedLight=new RadialGradient(x+r*.43f,y+r*.46f,r*.97f,
                new int[]{0x42bba9c0,0x186e548c,0x006e548c},
                new float[]{0f,.42f,1f},Shader.TileMode.CLAMP);
            rim=new SweepGradient(x,y,
                new int[]{0xff91849e,0xff9280a8,0xffb7a1c4,0xffdfd2e6,0xff91849e},
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
            float dx=(float)Math.sin(t)*r*.035f,dy=(float)Math.cos(t)*r*.025f;
            lightMatrix.reset();lightMatrix.setTranslate(dx,dy);
            highlight.setLocalMatrix(lightMatrix);
            paint.setShader(highlight);
            paint.setAlpha(158+(int)(13*breath));
            canvas.drawCircle(x,y,r,paint);
            lightMatrix.reset();lightMatrix.setTranslate(-dx*.62f,dy*.66f);
            reflectedLight.setLocalMatrix(lightMatrix);
            paint.setShader(reflectedLight);
            paint.setAlpha(138+(int)(12*breath));
            canvas.drawCircle(x,y,r,paint);
            canvas.restore();
            paint.setShader(null);paint.setAlpha(255);
            // One thin, continuous border; the soft halo is drawn behind it.
            contour.setStyle(Paint.Style.STROKE);
            contour.setPathEffect(null);
            lightMatrix.reset();lightMatrix.setRotate((float)Math.sin(t)*7f,x,y);
            rim.setLocalMatrix(lightMatrix);
            contour.setShader(rim);
            contour.setAlpha(202+(int)(13*breath));
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
