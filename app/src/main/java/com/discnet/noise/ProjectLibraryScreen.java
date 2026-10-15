package com.discnet.noise;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * Noise-owned project browser. All cards, confirmations and name editors are
 * custom Views; only Android's keyboard is used for text input.
 * Thumbnail decoding and GPU processing happen off the main thread.
 */
final class ProjectLibraryScreen extends FrameLayout {
    interface Listener {
        void close();
        void open(ProjectStore.Project project);
        void rename(ProjectStore.Project project,String name);
        void delete(ProjectStore.Project project);
        void create();
    }
    private final Listener listener;
    private final LinearLayout cards;
    private final TextView countLabel;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService previews=Executors.newSingleThreadExecutor();
    private final ArrayList<Bitmap> owned=new ArrayList<>();
    private volatile boolean disposed;
    private int generation;
    private int displayedProjects;
    private View modal;
    private final File cache;

    ProjectLibraryScreen(Context context,Listener listener){
        super(context);this.listener=listener;
        cache=new File(context.getCacheDir(),"noise-project-covers");
        cache.mkdirs();
        setBackgroundColor(0xff0c0b10);
        setClickable(true);setFocusableInTouchMode(true);
        LinearLayout page=new LinearLayout(context);page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16),dp(10),dp(16),dp(8));
        addView(page,new LayoutParams(-1,-1));

        LinearLayout bar=new LinearLayout(context);bar.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(bar,new LinearLayout.LayoutParams(-1,dp(62)));
        ImageView back=IconArt.button(context,IconArt.BACK,"Fechar meus projetos");
        bar.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        back.setOnClickListener(v->listener.close());
        LinearLayout titleBox=new LinearLayout(context);titleBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleParams=new LinearLayout.LayoutParams(0,-2,1);
        titleParams.leftMargin=dp(8);bar.addView(titleBox,titleParams);
        TextView heading=text("Meus projetos",21,true);
        titleBox.addView(heading);
        countLabel=text("Biblioteca local",11,false);
        titleBox.addView(countLabel);
        TextView add=button("+  Novo",true);
        bar.addView(add,new LinearLayout.LayoutParams(dp(84),dp(43)));
        add.setOnClickListener(v->listener.create());

        TextView subtitle=text("EDITE DE ONDE PAROU",10,false);
        subtitle.setLetterSpacing(.14f);
        LinearLayout.LayoutParams subtitleParams=new LinearLayout.LayoutParams(-1,dp(37));
        page.addView(subtitle,subtitleParams);

        ScrollView scroll=new ScrollView(context);
        scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        cards=new LinearLayout(context);cards.setOrientation(LinearLayout.VERTICAL);
        cards.setPadding(0,0,0,dp(24));scroll.addView(cards);
        TextView footer=text("Arquivos preservados no dispositivo  ·  Noise! Studio",11,false);
        footer.setGravity(Gravity.CENTER);
        page.addView(footer,new LinearLayout.LayoutParams(-1,dp(34)));
        showLoading();
    }
    private int dp(float n){return EditorTheme.dp(getContext(),n);}
    private TextView text(String value,float size,boolean prominent){
        TextView t=new TextView(getContext());t.setText(value);t.setTextSize(size);
        t.setTextColor(prominent?EditorTheme.INK:EditorTheme.MUTED);
        t.setTypeface(prominent?NoiseTypography.bold(getContext()):NoiseTypography.regular(getContext()));
        t.setGravity(Gravity.CENTER_VERTICAL);return t;
    }
    private TextView button(String text,boolean primary){
        TextView t=text(text,12.5f,true);t.setGravity(Gravity.CENTER);
        t.setTextColor(primary?0xff211526:EditorTheme.INK);
        t.setBackground(EditorTheme.buttonSurface(getContext(),14,primary));
        t.setClickable(true);t.setFocusable(true);t.setContentDescription(text);return t;
    }
    void showLoading(){
        cards.removeAllViews();
        TextView loading=text("Carregando projetos…",14,false);
        loading.setGravity(Gravity.CENTER);
        cards.addView(loading,new LinearLayout.LayoutParams(-1,dp(180)));
    }
    int projectCount(){return displayedProjects;}
    int previewCount(){return owned.size();}
    void setProjects(ArrayList<ProjectStore.Project> projects){
        if(disposed)return;
        displayedProjects=projects.size();
        generation++;final int request=generation;
        cards.removeAllViews();
        countLabel.setText(projects.size()==1?"1 projeto salvo":projects.size()+" projetos salvos");
        if(projects.isEmpty()){
            LinearLayout empty=new LinearLayout(getContext());empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(22),dp(40),dp(22),dp(40));
            empty.setBackground(EditorTheme.surface(getContext(),23));
            TextView mark=text("◈",44,true);mark.setTextColor(EditorTheme.LIME);
            mark.setGravity(Gravity.CENTER);empty.addView(mark);
            TextView title=text("Sua biblioteca começa aqui",17,true);
            title.setGravity(Gravity.CENTER);empty.addView(title);
            TextView hint=text("Importe uma imagem e suas edições serão salvas como projetos.",12,false);
            hint.setGravity(Gravity.CENTER);hint.setPadding(0,dp(8),0,dp(14));
            empty.addView(hint);
            TextView create=button("Importar imagem",true);
            empty.addView(create,new LinearLayout.LayoutParams(-1,dp(48)));
            create.setOnClickListener(v->listener.create());
            cards.addView(empty,new LinearLayout.LayoutParams(-1,-2));
            return;
        }
        int cols=getResources().getConfiguration().orientation==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE?3:2;
        LinearLayout row=null;
        for(int i=0;i<projects.size();i++){
            if(i%cols==0){row=new LinearLayout(getContext());row.setOrientation(LinearLayout.HORIZONTAL);
                cards.addView(row,new LinearLayout.LayoutParams(-1,-2));}
            final ProjectStore.Project p=projects.get(i);
            LinearLayout card=new LinearLayout(getContext());card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(8),dp(8),dp(8),dp(8));
            card.setBackground(EditorTheme.panel(getContext(),0xff1a1821,0,18,0));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);
            cp.setMargins(dp(3),dp(3),dp(3),dp(7));row.addView(card,cp);
            FrameLayout cover=new FrameLayout(getContext());
            cover.setBackground(EditorTheme.panel(getContext(),0xff2a2233,0,14,0));
            cover.setClipToOutline(true);
            ImageView image=new ImageView(getContext());image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.addView(image,new LayoutParams(-1,-1));
            TextView placeholder=text("◈",28,true);placeholder.setGravity(Gravity.CENTER);
            placeholder.setTextColor(0xffac8abb);cover.addView(placeholder,new LayoutParams(-1,-1));
            LinearLayout.LayoutParams imageParams=new LinearLayout.LayoutParams(-1,dp(133));
            card.addView(cover,imageParams);
            TextView name=text(p.name,13,true);name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,dp(29));np.topMargin=dp(6);
            card.addView(name,np);
            File metadata=new File(p.source.getParentFile(),"project.json");
            String updated=DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(metadata.lastModified()));
            TextView info=text(updated,10.5f,false);
            card.addView(info,new LinearLayout.LayoutParams(-1,dp(22)));
            LinearLayout actions=new LinearLayout(getContext());actions.setGravity(Gravity.CENTER_VERTICAL);
            card.addView(actions,new LinearLayout.LayoutParams(-1,dp(39)));
            TextView open=button("Abrir",true);
            actions.addView(open,new LinearLayout.LayoutParams(0,dp(37),1));
            TextView more=button("···",false);
            LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(dp(42),dp(37));mp.leftMargin=dp(5);
            actions.addView(more,mp);
            open.setOnClickListener(v->listener.open(p));
            cover.setOnClickListener(v->listener.open(p));
            cover.setContentDescription("Prévia de "+p.name+". Toque para abrir");
            more.setOnClickListener(v->showActions(p));
            image.setContentDescription("Prévia do projeto "+p.name);
            previews.execute(()->{
                Bitmap thumbnail=loadCover(p);
                ui.post(()->{
                    if(disposed||request!=generation||image.getParent()==null){
                        if(thumbnail!=null)thumbnail.recycle();return;
                    }
                    if(thumbnail!=null){
                        owned.add(thumbnail);image.setImageBitmap(thumbnail);placeholder.setVisibility(View.GONE);
                    }else placeholder.setText("Imagem indisponível");
                });
            });
        }
    }
    private Bitmap loadCover(ProjectStore.Project p){
        if(disposed||Thread.currentThread().isInterrupted())return null;
        File json=new File(p.source.getParentFile(),"project.json");
        File output=new File(cache,p.id+".png");
        try{
            if(output.isFile()&&output.lastModified()>=json.lastModified()){
                Bitmap old=android.graphics.BitmapFactory.decodeFile(output.getAbsolutePath());
                if(old!=null)return old;
            }
            Bitmap source=ImageSource.decode(p.source,480);
            try{
                int[] nativeSize=p.document.nativeSize(source.getWidth(),source.getHeight());
                float factor=Math.min(1f,256f/Math.max(nativeSize[0],nativeSize[1]));
                int w=Math.max(1,Math.round(nativeSize[0]*factor));
                int h=Math.max(1,Math.round(nativeSize[1]*factor));
                try{
                    Bitmap rendered=GpuExporter.render(getContext().getApplicationContext(),source,
                        p.document.gpu(false),w,h,source.getWidth(),source.getHeight());
                    try(FileOutputStream stream=new FileOutputStream(output)){
                        rendered.compress(Bitmap.CompressFormat.PNG,87,stream);
                    }catch(Exception ignored){}
                    return rendered;
                }catch(Exception|OutOfMemoryError gpu){
                    Bitmap fallback=Bitmap.createScaledBitmap(source,w,h,false);
                    return fallback==source?source.copy(Bitmap.Config.ARGB_8888,false):fallback;
                }
            }finally{source.recycle();}
        }catch(Exception|OutOfMemoryError e){return null;}
    }

    private void showActions(ProjectStore.Project p){
        LinearLayout content=modal("Gerenciar projeto",p.name);
        TextView open=button("Abrir projeto",true);
        content.addView(open,new LinearLayout.LayoutParams(-1,dp(48)));
        open.setOnClickListener(v->{hideModal();listener.open(p);});
        TextView rename=button("Renomear",false);
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(48));
        rp.topMargin=dp(8);content.addView(rename,rp);
        rename.setOnClickListener(v->showRename(p));
        TextView remove=button("Excluir projeto",false);
        remove.setTextColor(0xfff19da9);
        LinearLayout.LayoutParams dp2=new LinearLayout.LayoutParams(-1,dp(48));
        dp2.topMargin=dp(8);content.addView(remove,dp2);
        remove.setOnClickListener(v->showDelete(p));
        addDismiss(content);
    }
    private void showRename(ProjectStore.Project project){
        LinearLayout content=modal("Renomear projeto","Escolha um nome de até 60 caracteres.");
        EditText name=new EditText(getContext());
        name.setSingleLine(true);name.setText(project.name);
        name.setSelectAllOnFocus(true);name.setTextColor(EditorTheme.INK);
        name.setTypeface(NoiseTypography.medium(getContext()));name.setTextSize(16);
        name.setBackground(EditorTheme.panel(getContext(),0xff292332,0,12,0));
        name.setPadding(dp(12),0,dp(12),0);
        content.addView(name,new LinearLayout.LayoutParams(-1,dp(53)));
        TextView confirm=button("Salvar nome",true);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(47));p.topMargin=dp(12);
        content.addView(confirm,p);
        confirm.setOnClickListener(v->{
            String value=name.getText().toString().trim();
            if(value.length()<1||value.length()>60){name.setError("Use de 1 a 60 caracteres");return;}
            hideModal();listener.rename(project,value);
        });
        addDismiss(content);
    }
    private void showDelete(ProjectStore.Project project){
        LinearLayout content=modal("Excluir projeto?","A imagem original e as edições salvas deste projeto serão apagadas.");
        TextView delete=button("Excluir definitivamente",false);
        delete.setTextColor(0xfff19da9);
        content.addView(delete,new LinearLayout.LayoutParams(-1,dp(48)));
        delete.setOnClickListener(v->{hideModal();listener.delete(project);});
        addDismiss(content);
    }
    private LinearLayout modal(String title,String detail){
        hideModal();
        FrameLayout layer=new FrameLayout(getContext());layer.setBackgroundColor(0xc708070c);
        layer.setClickable(true);
        addView(layer,new LayoutParams(-1,-1));modal=layer;
        layer.setOnClickListener(v->hideModal());
        LinearLayout content=new LinearLayout(getContext());content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20),dp(20),dp(20),dp(20));
        content.setBackground(EditorTheme.panel(getContext(),0xff1c1924,0,24,0));
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER);
        lp.leftMargin=dp(19);lp.rightMargin=dp(19);layer.addView(content,lp);
        content.setClickable(true);content.setOnClickListener(v->{});
        TextView heading=text(title,20,true);
        content.addView(heading,new LinearLayout.LayoutParams(-1,dp(34)));
        TextView message=text(detail,12,false);message.setPadding(0,0,0,dp(13));
        content.addView(message,new LinearLayout.LayoutParams(-1,-2));
        return content;
    }
    private void addDismiss(LinearLayout content){
        TextView cancel=button("Cancelar",false);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(46));
        lp.topMargin=dp(10);content.addView(cancel,lp);
        cancel.setOnClickListener(v->hideModal());
    }
    private void hideModal(){if(modal!=null){removeView(modal);modal=null;}}
    boolean dismissModal(){if(modal==null)return false;hideModal();return true;}
    void dispose(){
        if(disposed)return;disposed=true;generation++;previews.shutdownNow();
        hideModal();for(Bitmap b:owned)if(!b.isRecycled())b.recycle();owned.clear();
    }
}
