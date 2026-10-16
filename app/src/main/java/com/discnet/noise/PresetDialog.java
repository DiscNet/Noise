package com.discnet.noise;

import android.app.Activity;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Entirely Noise-owned preset library: GPU previews, no platform Dialog/PopupMenu. */
final class PresetDialog extends FrameLayout {
    interface Listener { void apply(EditorDocument look); void save(String name); }
    private final Activity activity;
    private final ProjectStore store;
    private final Bitmap original;
    private final EditorDocument base;
    private final ArrayList<ProjectStore.Preset> presets;
    private final Listener listener;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ExecutorService previewWorker=Executors.newSingleThreadExecutor();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final HashMap<String,Bitmap> thumbnails=new HashMap<>();
    private final ArrayList<Bitmap> owned=new ArrayList<>();
    private volatile boolean closed;
    private int selection=-1;
    private FrameLayout owner;
    private final ImageView preview;
    private final TextView caption,apply,strengthLabel;
    private final SeekBar strength;
    private Bitmap intensityPreview;
    private int previewToken;
    private View modal;
    private final boolean compact;
    private final LinearLayout cards;

    PresetDialog(Activity activity,ProjectStore store,Bitmap original,EditorDocument base,
                 ArrayList<ProjectStore.Preset> presets,Listener listener){
        super(activity);
        this.activity=activity;this.store=store;this.original=original;
        this.base=base.copy();this.presets=presets;this.listener=listener;
        setBackgroundColor(0xff0c0b10);setClickable(true);setFocusableInTouchMode(true);
        LinearLayout page=new LinearLayout(activity);page.setOrientation(LinearLayout.VERTICAL);
        compact=getResources().getDisplayMetrics().heightPixels<dp(690);
        page.setPadding(dp(12),dp(8),dp(12),dp(10));
        addView(page,new LayoutParams(-1,-1));
        LinearLayout top=new LinearLayout(activity);top.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(top,new LinearLayout.LayoutParams(-1,dp(compact?48:58)));
        ImageView back=IconArt.button(activity,IconArt.BACK,"Voltar ao editor");
        top.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        back.setOnClickListener(v->dismiss());
        TextView title=text("Presets",21,true);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.leftMargin=dp(8);
        top.addView(title,tp);
        TextView save=button("+  Salvar",true);
        top.addView(save,new LinearLayout.LayoutParams(dp(92),dp(44)));
        save.setOnClickListener(v->editName(null));

        preview=new ImageView(activity);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setImageBitmap(original);preview.setBackground(EditorTheme.panel(activity,0xff1d1a24,0,18,0));
        page.addView(preview,new LinearLayout.LayoutParams(-1,dp(compact?94:174)));
        caption=text("Selecione um estilo para visualizar e aplicar",12,false);
        caption.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(caption,new LinearLayout.LayoutParams(-1,dp(compact?29:42)));
        LinearLayout intensityRow=new LinearLayout(activity);
        intensityRow.setGravity(Gravity.CENTER_VERTICAL);
        strengthLabel=text("Intensidade 100%",12,false);
        strengthLabel.setTextColor(OrbitalMaterial.WHITE);
        intensityRow.addView(strengthLabel,new LinearLayout.LayoutParams(-1,dp(24)));
        page.addView(intensityRow,new LinearLayout.LayoutParams(-1,dp(24)));
        strength=new SeekBar(activity);
        strength.setMax(100);strength.setProgress(100);
        strength.setEnabled(false);
        page.addView(strength,new LinearLayout.LayoutParams(-1,dp(38)));
        strength.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int progress,boolean user){
                strengthLabel.setText("Intensidade "+progress+"%");
                if(user)scheduleIntensityPreview();
            }
            @Override public void onStartTrackingTouch(SeekBar bar){}
            @Override public void onStopTrackingTouch(SeekBar bar){}
        });
        ScrollView scroll=new ScrollView(activity);scroll.setVerticalScrollBarEnabled(false);
        page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        cards=new LinearLayout(activity);cards.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(cards);
        apply=button("Selecionar um preset",true);
        apply.setAlpha(.55f);apply.setEnabled(false);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(52));
        bp.topMargin=dp(8);page.addView(apply,bp);
        apply.setOnClickListener(v->{
            if(selection<0)return;
            EditorDocument mixed=EditorDocument.blendLook(base,
                presets.get(selection).document,strength.getProgress()/100f);
            listener.apply(mixed);dismiss();
        });
        // The photo preview, instructions and intensity all belong to the
        // SAME vertically scrolling region as the cards. On 5-inch phones
        // none can push the presets off-screen or obscure the footer.
        page.removeView(preview);
        page.removeView(caption);
        page.removeView(intensityRow);
        page.removeView(strength);
        page.removeView(scroll);
        scroll.removeView(cards);
        LinearLayout scrollingContent=new LinearLayout(activity);
        scrollingContent.setOrientation(LinearLayout.VERTICAL);
        scrollingContent.addView(preview,new LinearLayout.LayoutParams(
            -1,dp(compact?106:136)));
        scrollingContent.addView(caption,new LinearLayout.LayoutParams(-1,dp(33)));
        scrollingContent.addView(intensityRow,new LinearLayout.LayoutParams(-1,dp(24)));
        scrollingContent.addView(strength,new LinearLayout.LayoutParams(-1,dp(38)));
        scrollingContent.addView(cards,new LinearLayout.LayoutParams(-1,-2));
        scroll.setFillViewport(false);
        scroll.addView(scrollingContent,new ScrollView.LayoutParams(-1,-2));
        page.addView(scroll,1,new LinearLayout.LayoutParams(-1,0,1));
        setOnApplyWindowInsetsListener((v,insets)->{
            setPadding(insets.getSystemWindowInsetLeft(),
                insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(),
                insets.getSystemWindowInsetBottom());
            return insets;
        });
    }
    private int dp(float x){return EditorTheme.dp(activity,x);}
    private TextView text(String label,float size,boolean strong){
        TextView v=new TextView(activity);v.setText(label);v.setTextSize(size);
        v.setTypeface(strong?NoiseTypography.bold(activity):NoiseTypography.regular(activity));
        v.setTextColor(strong?EditorTheme.INK:EditorTheme.MUTED);
        v.setGravity(Gravity.CENTER_VERTICAL);return v;
    }
    private TextView button(String title,boolean primary){
        TextView v=text(title,13,true);v.setGravity(Gravity.CENTER);
        v.setBackground(EditorTheme.buttonSurface(activity,15,primary));
        v.setTextColor(primary?0xff211525:EditorTheme.INK);v.setClickable(true);
        v.setFocusable(true);v.setContentDescription(title);return v;
    }
    void show(){
        owner=activity.findViewById(android.R.id.content);
        owner.addView(this,new FrameLayout.LayoutParams(-1,-1));
        bringToFront();requestApplyInsets();reload();
    }
    private void reload(){
        cards.removeAllViews();
        boolean listedCameras=false,listedCustom=false,listedStyles=false;
        for(int i=0;i<presets.size();i++){
            final int index=i;ProjectStore.Preset p=presets.get(i);
            if(!p.id.startsWith("builtin")&&!listedCustom){
                listedCustom=true;
                TextView category=text("MEUS PRESETS",12,true);
                category.setTextColor(OrbitalMaterial.WHITE);
                cards.addView(category,new LinearLayout.LayoutParams(-1,dp(37)));
            }
            if(p.id.startsWith("builtin")&&!p.id.startsWith("builtin-camera-")
                &&!listedStyles){
                listedStyles=true;
                TextView category=text("ESTILOS",12,true);
                category.setTextColor(OrbitalMaterial.WHITE);
                cards.addView(category,new LinearLayout.LayoutParams(-1,dp(37)));
            }
            if(p.id.startsWith("builtin-camera-")&&!listedCameras){
                listedCameras=true;
                TextView category=text("CÂMERAS & FILMES",12,true);
                category.setLetterSpacing(.09f);
                category.setTextColor(EditorTheme.LIME);
                LinearLayout.LayoutParams titleParams=new LinearLayout.LayoutParams(-1,dp(46));
                cards.addView(category,titleParams);
            }
            LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8),dp(8),dp(8),dp(8));
            row.setBackground(EditorTheme.panel(activity,0xff1c1a24,0,16,0));
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(compact?76:92));
            rp.bottomMargin=dp(8);cards.addView(row,rp);
            ImageView thumb=new ImageView(activity);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setBackground(EditorTheme.panel(activity,0xff292232,0,12,0));
            row.addView(thumb,new LinearLayout.LayoutParams(dp(compact?70:94),dp(compact?60:74)));
            LinearLayout info=new LinearLayout(activity);info.setOrientation(LinearLayout.VERTICAL);
            info.setGravity(Gravity.CENTER_VERTICAL);info.setPadding(dp(12),0,dp(6),0);
            row.addView(info,new LinearLayout.LayoutParams(0,-1,1));
            TextView name=text((p.favorite?"★  ":"")+p.name,14,true);
            name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);
            info.addView(name);
            TextView hint=text(p.id.startsWith("builtin-camera-")?
                "ESTÉTICA DE CÂMERA ANALÓGICA":
                p.id.startsWith("builtin")?"INCLUSO NO NOISE!":"PERSONALIZADO",10,false);
            info.addView(hint);
            row.setOnClickListener(v->select(index));
            thumb.setOnClickListener(v->select(index));
            row.setContentDescription("Prévia do preset "+p.name);
            if(!p.id.startsWith("builtin")){
                TextView more=button("···",false);
                row.addView(more,new LinearLayout.LayoutParams(dp(44),dp(46)));
                more.setOnClickListener(v->manage(p));
            }
            Bitmap cached=thumbnails.get(p.id);
            if(cached!=null){thumb.setImageBitmap(cached);continue;}
            worker.execute(()->{
                Bitmap b=null;
                try{
                    if(closed)return;
                    EditorDocument d=base.copy();d.copyLook(p.document);
                    int w=240,h=Math.max(1,Math.round(w/d.aspect(original.getWidth(),original.getHeight())));
                    if(h>240){w=Math.max(1,Math.round(w*240f/h));h=240;}
                    b=GpuExporter.render(activity.getApplicationContext(),original,d.gpu(false),w,h);
                    Bitmap ready=b;
                    ui.post(()->{
                        if(closed){ready.recycle();return;}
                        owned.add(ready);thumbnails.put(p.id,ready);thumb.setImageBitmap(ready);
                        if(selection==index)select(index);
                    });
                }catch(Exception|OutOfMemoryError error){
                    if(b!=null)b.recycle();
                    ui.post(()->{if(!closed)thumb.setContentDescription("Prévia não disponível");});
                }
            });
        }
    }
    private void select(int index){
        if(closed||index<0||index>=presets.size())return;
        selection=index;
        ProjectStore.Preset p=presets.get(index);
        strength.setEnabled(true);
        apply.setEnabled(true);apply.setAlpha(1f);
        apply.setText("Aplicar "+p.name);
        caption.setText("Prévia · "+p.name);
        scheduleIntensityPreview();
    }
    private final Runnable intensityTask=()->renderIntensity();
    private void scheduleIntensityPreview(){
        if(closed||selection<0)return;
        ++previewToken;
        ui.removeCallbacks(intensityTask);
        ui.postDelayed(intensityTask,140);
    }
    private void renderIntensity(){
        if(closed||selection<0)return;
        int token=previewToken;
        int selected=selection;
        EditorDocument mix=EditorDocument.blendLook(base,
            presets.get(selected).document,strength.getProgress()/100f);
        previewWorker.execute(()->{
            Bitmap rendered=null;
            try{
                int w=360,h=Math.max(1,Math.round(
                    w/mix.aspect(original.getWidth(),original.getHeight())));
                if(h>360){w=Math.max(1,Math.round(w*360f/h));h=360;}
                rendered=GpuExporter.render(activity.getApplicationContext(),
                    original,mix.gpu(false),w,h);
            }catch(Exception|OutOfMemoryError ignored){}
            final Bitmap bitmap=rendered;
            ui.post(()->{
                if(closed||token!=previewToken||selected!=selection){
                    if(bitmap!=null)bitmap.recycle();return;
                }
                if(bitmap==null){caption.setText("Prévia indisponível");return;}
                preview.setImageBitmap(bitmap);
                Bitmap old=intensityPreview;
                intensityPreview=bitmap;
                if(old!=null&&!old.isRecycled())old.recycle();
            });
        });
    }
    private LinearLayout modal(String title,String detail){
        hideModal();
        FrameLayout shade=new FrameLayout(activity);shade.setBackgroundColor(0xd0080710);
        shade.setClickable(true);addView(shade,new LayoutParams(-1,-1));modal=shade;
        shade.setOnClickListener(v->hideModal());
        LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20),dp(18),dp(20),dp(20));
        content.setBackground(EditorTheme.panel(activity,0xff201b29,0,6,0));
        ScrollView modalScroll=new ScrollView(activity);
        modalScroll.setFillViewport(false);
        modalScroll.setVerticalScrollBarEnabled(false);
        FrameLayout.LayoutParams layout=new FrameLayout.LayoutParams(-1,
            Math.min(dp(430),Math.max(dp(165),
                getResources().getDisplayMetrics().heightPixels-dp(95))),Gravity.CENTER);
        layout.leftMargin=dp(16);layout.rightMargin=dp(16);
        shade.addView(modalScroll,layout);
        modalScroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        content.setClickable(true);content.setOnClickListener(v->{});
        TextView head=text(title,19,true);content.addView(head,new LinearLayout.LayoutParams(-1,dp(37)));
        TextView subtitle=text(detail,12,false);subtitle.setPadding(0,0,0,dp(13));
        content.addView(subtitle,new LinearLayout.LayoutParams(-1,-2));
        return content;
    }
    private void addButton(LinearLayout content,String title,boolean primary,Runnable run){
        TextView b=button(title,primary);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(47));p.bottomMargin=dp(7);
        content.addView(b,p);b.setOnClickListener(v->run.run());
    }
    private void manage(ProjectStore.Preset p){
        LinearLayout sheet=modal("Gerenciar preset",p.name);
        addButton(sheet,"Substituir pelos ajustes atuais",false,()->{
            p.document=base.copy();
            persist(p);
            selection=-1;
            preview.setImageBitmap(original);
            apply.setEnabled(false);
            apply.setAlpha(.55f);
            reload();
        });
        addButton(sheet,"Renomear",false,()->editName(p));
        addButton(sheet,p.favorite?"Remover favorito":"Marcar favorito",false,()->{
            p.favorite=!p.favorite;persist(p);
        });
        addButton(sheet,"Excluir preset",false,()->confirmDelete(p));
        addButton(sheet,"Cancelar",false,this::hideModal);
    }
    private void editName(ProjectStore.Preset p){
        LinearLayout sheet=modal(p==null?"Salvar preset":"Renomear preset",
            p==null?"Salve os ajustes atuais com um nome.":"Altere o nome sem perder os ajustes.");
        EditText input=new EditText(activity);input.setSingleLine(true);input.setText(p==null?"Meu estilo":p.name);
        input.setTextSize(16);input.setTextColor(EditorTheme.INK);
        input.setTypeface(NoiseTypography.medium(activity));
        input.setBackground(EditorTheme.panel(activity,0xff30273b,0,13,0));
        input.setPadding(dp(12),0,dp(12),0);
        sheet.addView(input,new LinearLayout.LayoutParams(-1,dp(53)));
        addButton(sheet,"Salvar",true,()->{
            String name=input.getText().toString().trim();
            if(name.length()<1||name.length()>60){input.setError("Use entre 1 e 60 caracteres");return;}
            hideModal();
            if(p==null){dismiss();listener.save(name);}
            else{p.name=name;persist(p);}
        });
        addButton(sheet,"Cancelar",false,this::hideModal);
    }
    private void confirmDelete(ProjectStore.Preset p){
        LinearLayout sheet=modal("Excluir preset?",p.name+" será removido da biblioteca.");
        addButton(sheet,"Excluir definitivamente",false,()->{
            hideModal();
            worker.execute(()->{
                try{store.deletePreset(p);
                    ui.post(()->{if(!closed){presets.remove(p);selection=-1;
                        preview.setImageBitmap(original);apply.setEnabled(false);apply.setAlpha(.55f);
                        apply.setText("Selecionar um preset");reload();}});
                }catch(Exception e){showError("Falha ao excluir preset");}
            });
        });
        addButton(sheet,"Cancelar",false,this::hideModal);
    }
    private void persist(ProjectStore.Preset p){
        hideModal();
        worker.execute(()->{
            try{store.savePreset(p);ui.post(()->{if(!closed)reload();});}
            catch(Exception e){showError("Não foi possível salvar o preset");}
        });
    }
    private void showError(String message){
        ui.post(()->{if(!closed)caption.setText(message);});
    }
    private void hideModal(){if(modal!=null){removeView(modal);modal=null;}}
    boolean onBack(){if(modal!=null){hideModal();return true;}dismiss();return true;}
    void dismiss(){
        if(closed)return;
        closed=true;++previewToken;
        ui.removeCallbacks(intensityTask);
        previewWorker.shutdownNow();worker.shutdownNow();hideModal();
        if(owner!=null)owner.removeView(this);
        preview.setImageDrawable(null);
        if(intensityPreview!=null&&!intensityPreview.isRecycled())intensityPreview.recycle();
        for(Bitmap b:owned)if(!b.isRecycled())b.recycle();
        owned.clear();thumbnails.clear();
    }
}
