package com.discnet.noise;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.text.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.Locale;

/** Resolution editor shared by Transformar and Exportar.
 * Only downscaling is offered; GPU memory / image bounds are authoritative.
 * Text edits never trigger bitmap decoding or GPU work.
 */
final class ResizeDialog extends FrameLayout {
    private final Activity host;
    private boolean visible;
    interface Result { void apply(int width,int height,boolean jpeg,int quality,boolean export); }
    private final int sourceW,sourceH,maximum;
    private int targetW,targetH;
    private boolean locked=true,editing;
    private final EditText width,height;
    private final TextView result,ratio,lock,png,jpeg,qualityTitle;
    private final LinearLayout qualityGroup;
    private final EditorTheme.Slider quality;
    private boolean jpegEnabled;
    private final Result callback;
    private int dp(float n){return EditorTheme.dp(getContext(),n);}
    ResizeDialog(Context context,Bitmap preview,EditorDocument doc,int originalW,int originalH,
                 int maxTexture,Result callback){
        super(context);this.callback=callback;
        host=(Activity)context;
        setBackgroundColor(0xff09070e);
        setClickable(true);
        setFocusableInTouchMode(true);
        int[] nativeSize=doc.nativeSize(originalW,originalH);
        sourceW=nativeSize[0];sourceH=nativeSize[1];
        maximum=Math.max(1,Math.min(4096,maxTexture));
        int[] size=doc.outputSize(originalW,originalH,maximum);
        targetW=size[0];targetH=size[1];
        jpegEnabled=doc.jpeg;

        LinearLayout body=new LinearLayout(context);body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16),dp(12),dp(16),dp(17));
        GradientDrawable pageMaterial=new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{OrbitalMaterial.DARK_PURPLE,OrbitalMaterial.BLACK});
        pageMaterial.setCornerRadius(0);
        body.setBackground(pageMaterial);
        ScrollView scroller=new ScrollView(context);scroller.setFillViewport(false);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.addView(body);

        TextView title=text("EXPORTAR IMAGEM",21,true);body.addView(title,new LinearLayout.LayoutParams(-1,dp(35)));
        TextView subtitle=text("Redimensionar a resolução",12,false);
        body.addView(subtitle,new LinearLayout.LayoutParams(-1,dp(27)));
        ImageView picture=new ImageView(context);picture.setImageBitmap(preview);
        picture.setScaleType(ImageView.ScaleType.FIT_CENTER);
        picture.setBackground(EditorTheme.panel(context,0xff100e17,0,17,0));
        body.addView(picture,new LinearLayout.LayoutParams(-1,dp(130)));
        TextView original=text("Original  ·  "+sourceW+" × "+sourceH+" px",12,false);
        original.setGravity(Gravity.CENTER);body.addView(original,new LinearLayout.LayoutParams(-1,dp(35)));
        result=text("",16,true);result.setGravity(Gravity.CENTER);
        result.setTextColor(EditorTheme.LIME);
        body.addView(result,new LinearLayout.LayoutParams(-1,dp(33)));
        ratio=text("",11,false);ratio.setGravity(Gravity.CENTER);
        body.addView(ratio,new LinearLayout.LayoutParams(-1,dp(27)));

        LinearLayout fields=new LinearLayout(context);fields.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(fields,new LinearLayout.LayoutParams(-1,dp(68)));
        LinearLayout wbox=fieldBox("LARGURA");width=number(context,targetW,wbox);
        LinearLayout hbox=fieldBox("ALTURA");height=number(context,targetH,hbox);
        fields.addView(wbox,new LinearLayout.LayoutParams(0,-1,1));
        lock=button("FIXA",false);
        lock.setContentDescription("Manter proporção, ativado");
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(48),dp(49));
        lp.setMargins(dp(5),0,dp(5),0);fields.addView(lock,lp);
        fields.addView(hbox,new LinearLayout.LayoutParams(0,-1,1));
        lock.setOnClickListener(v->{
            locked=!locked;lock.setText(locked?"FIXA":"LIVRE");
            lock.setContentDescription("Manter proporção, "+(locked?"ativado":"desativado"));
            lock.setTextColor(locked?EditorTheme.LIME:EditorTheme.MUTED);
            if(locked){targetH=Math.max(1,Math.round(targetW*sourceH/(float)sourceW));sync();}
            updateSummary();
        });
        lock.setTextColor(EditorTheme.LIME);
        attach(width,true);attach(height,false);

        TextView presetTitle=text("TAMANHOS RÁPIDOS",11,false);
        presetTitle.setLetterSpacing(.08f);body.addView(presetTitle,new LinearLayout.LayoutParams(-1,dp(32)));
        HorizontalScrollView presets=new HorizontalScrollView(context);presets.setHorizontalScrollBarEnabled(false);
        LinearLayout chips=new LinearLayout(context);presets.addView(chips);
        body.addView(presets,new LinearLayout.LayoutParams(-1,dp(46)));
        for(int percentage:new int[]{25,50,75,100}){
            TextView chip=button(percentage+"%",false);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(77),dp(42));
            p.setMargins(0,0,dp(7),0);chips.addView(chip,p);
            chip.setOnClickListener(v->{locked=true;lock.setText("FIXA");lock.setTextColor(EditorTheme.LIME);
                targetW=Math.max(1,Math.round(sourceW*percentage/100f));
                targetH=Math.max(1,Math.round(sourceH*percentage/100f));sync();});
        }
        TextView nativeChip=button("Original",false);
        chips.addView(nativeChip,new LinearLayout.LayoutParams(dp(88),dp(42)));
        nativeChip.setOnClickListener(v->{locked=true;lock.setText("FIXA");targetW=sourceW;targetH=sourceH;sync();});

        TextView note=text("Apenas redução nesta versão. Ampliar não recupera detalhes; limites de imagem e GPU serão respeitados.",11,false);
        note.setPadding(dp(3),dp(9),dp(3),dp(10));
        body.addView(note,new LinearLayout.LayoutParams(-1,-2));

        TextView exportTitle=text("EXPORTAÇÃO",11,false);exportTitle.setLetterSpacing(.08f);
        body.addView(exportTitle,new LinearLayout.LayoutParams(-1,dp(32)));
        LinearLayout formats=new LinearLayout(context);
        png=button("PNG · transparência",!jpegEnabled);jpeg=button("JPEG",jpegEnabled);
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,dp(43),1.4f);
        left.rightMargin=dp(8);formats.addView(png,left);
        formats.addView(jpeg,new LinearLayout.LayoutParams(0,dp(43),1));
        body.addView(formats,new LinearLayout.LayoutParams(-1,dp(45)));
        qualityGroup=new LinearLayout(context);qualityGroup.setOrientation(LinearLayout.VERTICAL);
        qualityTitle=text("",12,false);qualityGroup.addView(qualityTitle,new LinearLayout.LayoutParams(-1,dp(25)));
        quality=new EditorTheme.Slider(context,EditorTheme.LIME);
        quality.setMax(99);quality.setProgress(Math.max(1,Math.min(100,doc.jpegQuality))-1);
        quality.neutral(94);qualityGroup.addView(quality,new LinearLayout.LayoutParams(-1,dp(40)));
        quality.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int value,boolean fromUser){
                qualityTitle.setText("Qualidade JPEG  ·  "+(value+1)+"%");
            }
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){}
        });
        qualityTitle.setText("Qualidade JPEG  ·  "+(quality.getProgress()+1)+"%");
        body.addView(qualityGroup,new LinearLayout.LayoutParams(-1,dp(66)));
        png.setOnClickListener(v->changeFormat(false));
        jpeg.setOnClickListener(v->changeFormat(true));changeFormat(jpegEnabled);

        TextView folder=text("Destino: Pictures/Noise!  ·  Original preservado",11,false);
        folder.setPadding(dp(3),dp(7),0,dp(7));
        body.addView(folder,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout actions=new LinearLayout(context);
        TextView cancel=button("Cancelar",false),apply=button("Aplicar",false),export=button("Exportar",true);
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,dp(49),1);
        ap.rightMargin=dp(6);actions.addView(cancel,ap);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(49),1);
        bp.rightMargin=dp(6);actions.addView(apply,bp);
        actions.addView(export,new LinearLayout.LayoutParams(0,dp(49),1.25f));
        // Footer stays fixed when the keyboard opens or on compact Android displays.
        cancel.setOnClickListener(v->dismiss());
        apply.setOnClickListener(v->commit(false));
        export.setOnClickListener(v->commit(true));

        LinearLayout screen=new LinearLayout(context);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(OrbitalMaterial.BLACK);
        screen.addView(scroller,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout footer=new LinearLayout(context);
        footer.setPadding(dp(12),dp(8),dp(12),dp(12));
        footer.setBackgroundColor(OrbitalMaterial.DARK_PURPLE);
        footer.addView(actions,new LinearLayout.LayoutParams(-1,dp(52)));
        screen.addView(footer,new LinearLayout.LayoutParams(-1,-2));
        addView(screen,new FrameLayout.LayoutParams(-1,-1));
        updateSummary();
    }
    void show(){
        if(visible)return;
        visible=true;
        FrameLayout root=host.findViewById(android.R.id.content);
        root.addView(this,new FrameLayout.LayoutParams(-1,-1));
        bringToFront();
    }
    void dismiss(){
        if(!visible)return;
        visible=false;
        if(getParent() instanceof android.view.ViewGroup)
            ((android.view.ViewGroup)getParent()).removeView(this);
    }
    private TextView text(String s,float size,boolean heading){
        TextView t=new TextView(getContext());t.setText(s);t.setTextSize(size);
        t.setTextColor(heading?EditorTheme.INK:EditorTheme.MUTED);
        t.setTypeface(heading?NoiseTypography.bold(getContext()):NoiseTypography.regular(getContext()));
        t.setGravity(Gravity.CENTER_VERTICAL);return t;
    }
    private TextView button(String name,boolean primary){
        TextView t=text(name,12.5f,true);t.setGravity(Gravity.CENTER);
        t.setTextColor(primary?0xff1e1325:EditorTheme.INK);
        t.setBackground(EditorTheme.buttonSurface(getContext(),13,primary));
        t.setContentDescription(name);return t;
    }
    private LinearLayout fieldBox(String title){
        LinearLayout box=new LinearLayout(getContext());box.setOrientation(LinearLayout.VERTICAL);
        TextView name=text(title,10,false);name.setLetterSpacing(.07f);
        box.addView(name,new LinearLayout.LayoutParams(-1,dp(21)));return box;
    }
    private EditText number(Context c,int start,LinearLayout box){
        EditText value=new EditText(c);value.setSingleLine(true);
        value.setInputType(InputType.TYPE_CLASS_NUMBER);
        value.setText(String.valueOf(start));value.setTextSize(16);
        value.setTextColor(EditorTheme.INK);value.setTypeface(NoiseTypography.medium(c));
        value.setBackground(EditorTheme.panel(c,0xff272330,0,11,0));
        value.setPadding(dp(11),0,dp(5),0);box.addView(value,new LinearLayout.LayoutParams(-1,dp(43)));
        return value;
    }
    private void attach(EditText field,boolean isWidth){
        field.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){
                if(editing)return;
                try{
                    int n=Integer.parseInt(s.toString());
                    if(n<1)return;
                    if(isWidth){targetW=n;if(locked)targetH=Math.max(1,Math.round(n*sourceH/(float)sourceW));}
                    else{targetH=n;if(locked)targetW=Math.max(1,Math.round(n*sourceW/(float)sourceH));}
                    editing=true;
                    if(locked)(isWidth?height:width).setText(String.valueOf(isWidth?targetH:targetW));
                    editing=false;updateSummary();
                }catch(NumberFormatException ignored){}
            }
            public void afterTextChanged(Editable e){}
        });
    }
    private void sync(){
        editing=true;width.setText(String.valueOf(targetW));height.setText(String.valueOf(targetH));
        editing=false;updateSummary();
    }
    private void changeFormat(boolean jpegOn){
        jpegEnabled=jpegOn;
        png.setBackground(EditorTheme.buttonSurface(getContext(),13,!jpegOn));
        jpeg.setBackground(EditorTheme.buttonSurface(getContext(),13,jpegOn));
        png.setTextColor(!jpegOn?0xff1e1325:EditorTheme.INK);
        jpeg.setTextColor(jpegOn?0xff1e1325:EditorTheme.INK);
        qualityGroup.setVisibility(jpegOn?View.VISIBLE:View.GONE);
    }
    private boolean valid(){
        try{
            targetW=Integer.parseInt(width.getText().toString().trim());
            targetH=Integer.parseInt(height.getText().toString().trim());
            if(targetW<1||targetW>Math.min(sourceW,maximum)){width.setError("De 1 até "+Math.min(sourceW,maximum)+" px");return false;}
            if(targetH<1||targetH>Math.min(sourceH,maximum)){height.setError("De 1 até "+Math.min(sourceH,maximum)+" px");return false;}
            return true;
        }catch(NumberFormatException e){width.setError("Informe dimensões válidas");return false;}
    }
    private void updateSummary(){
        result.setText("Nova resolução  ·  "+targetW+" × "+targetH+" px");
        ratio.setText("Original: "+sourceW+" × "+sourceH+
            "  •  "+(locked?"Proporção preservada":"Proporção livre")+
            "  •  limite "+maximum+" px");
    }
    private void commit(boolean export){
        if(!valid())return;
        callback.apply(targetW,targetH,jpegEnabled,quality.getProgress()+1,export);
        dismiss();
    }
}
