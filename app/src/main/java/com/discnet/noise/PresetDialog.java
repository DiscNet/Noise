package com.discnet.noise;

import android.app.*;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Real GPU previews, isolated from the current document until Apply. */
final class PresetDialog {
    interface Listener{void apply(EditorDocument look);void save();}
    private final Activity activity;private final ProjectStore store;private final Bitmap original;
    private final EditorDocument base;private final ArrayList<ProjectStore.Preset> presets;private final Listener listener;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private final Handler ui=new Handler(Looper.getMainLooper());
    private volatile boolean closed;private int selection=-1;private AlertDialog dialog;private ImageView preview;private TextView caption;
    private final ArrayList<Bitmap> bitmaps=new ArrayList<>();private final HashMap<String,Bitmap> thumbnails=new HashMap<>();
    PresetDialog(Activity a,ProjectStore store,Bitmap image,EditorDocument base,ArrayList<ProjectStore.Preset> presets,Listener listener){activity=a;this.store=store;original=image;this.base=base.copy();this.presets=presets;this.listener=listener;}
    private int dp(float n){return EditorTheme.dp(activity,n);}
    void show(){LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(16),dp(8),dp(16),0);
        preview=new ImageView(activity);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setImageBitmap(original);body.addView(preview,new LinearLayout.LayoutParams(-1,dp(144)));
        caption=new TextView(activity);caption.setText("Selecione um estilo para comparar");caption.setTextColor(EditorTheme.MUTED);caption.setTextSize(12);caption.setMinHeight(dp(36));body.addView(caption);
        for(int i=0;i<presets.size();i++){final int index=i;ProjectStore.Preset p=presets.get(i);LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(4),0,dp(4));body.addView(row,new LinearLayout.LayoutParams(-1,dp(72)));
            ImageView thumb=new ImageView(activity);thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);thumb.setBackgroundColor(0xff262930);row.addView(thumb,new LinearLayout.LayoutParams(dp(80),dp(60)));
            TextView name=new TextView(activity);name.setText((p.favorite?"★ ":"")+p.name);name.setTextSize(14);name.setTextColor(EditorTheme.INK);name.setPadding(dp(12),0,dp(4),0);row.addView(name,new LinearLayout.LayoutParams(0,-1,1));row.setFocusable(true);row.setContentDescription("Prévia do preset "+p.name);row.setOnClickListener(v->{selection=index;caption.setText(p.name+" · toque em Aplicar para confirmar");Bitmap b=thumbnails.get(p.id);if(b!=null)preview.setImageBitmap(b);dialog.getButton(-1).setEnabled(b!=null);});
            if(!p.id.startsWith("builtin")){ImageView menu=IconArt.button(activity,IconArt.MORE,"Gerenciar "+p.name);row.addView(menu,new LinearLayout.LayoutParams(dp(48),dp(48)));menu.setOnClickListener(v->manage(p,name,menu));}
            worker.execute(()->{Bitmap result=null;try{if(closed)return;EditorDocument d=this.base.copy();d.copyLook(p.document);int w=420,h=Math.max(1,Math.round(w/d.aspect(original.getWidth(),original.getHeight())));if(h>420){w=Math.max(1,Math.round(w*420f/h));h=420;}
                result=GpuExporter.render(activity.getApplicationContext(),original,d.gpu(false),w,h);final Bitmap ready=result;ui.post(()->{if(closed){ready.recycle();return;}bitmaps.add(ready);thumbnails.put(p.id,ready);thumb.setImageBitmap(ready);if(selection==index){preview.setImageBitmap(ready);dialog.getButton(-1).setEnabled(true);}});
            }catch(Exception|OutOfMemoryError e){if(result!=null)result.recycle();ui.post(()->{if(!closed)thumb.setContentDescription("Não foi possível gerar a prévia");});}});
        }
        ScrollView scroll=new ScrollView(activity);scroll.addView(body);
        dialog=new AlertDialog.Builder(activity).setTitle("Presets").setView(scroll).setNegativeButton("Cancelar",null).setNeutralButton("Salvar atual",(d,w)->listener.save()).setPositiveButton("Aplicar",(d,w)->{if(selection>=0)listener.apply(presets.get(selection).document);}).create();
        dialog.setOnDismissListener(d->{closed=true;worker.shutdownNow();preview.setImageDrawable(null);for(Bitmap b:bitmaps)b.recycle();bitmaps.clear();});dialog.show();dialog.getButton(-1).setEnabled(false);
    }
    private void manage(ProjectStore.Preset p,TextView label,View anchor){PopupMenu menu=new PopupMenu(activity,anchor);menu.getMenu().add("Renomear");menu.getMenu().add(p.favorite?"Desfavoritar":"Favoritar");menu.getMenu().add("Excluir");menu.setOnMenuItemClickListener(item->{String t=item.getTitle().toString();if(t.equals("Renomear")){EditText input=new EditText(activity);input.setText(p.name);input.setSingleLine(true);new AlertDialog.Builder(activity).setTitle("Nome do preset").setView(input).setNegativeButton("Cancelar",null).setPositiveButton("Salvar",(d,w)->{String n=input.getText().toString().trim();if(!n.isEmpty()&&n.length()<=60){p.name=n;persist(p,label);}}).show();}else if(t.equals("Excluir")){new AlertDialog.Builder(activity).setMessage("Excluir "+p.name+"?").setNegativeButton("Cancelar",null).setPositiveButton("Excluir",(d,w)->{worker.execute(()->{try{store.deletePreset(p);ui.post(()->{if(!closed)dialog.dismiss();});}catch(Exception e){error();}});}).show();}else{p.favorite=!p.favorite;persist(p,label);}return true;});menu.show();}
    private void persist(ProjectStore.Preset p,TextView label){worker.execute(()->{try{store.savePreset(p);ui.post(()->{if(!closed)label.setText((p.favorite?"★ ":"")+p.name);});}catch(Exception e){error();}});}
    private void error(){ui.post(()->{if(!closed)Toast.makeText(activity,"Não foi possível salvar a alteração",Toast.LENGTH_SHORT).show();});}
}
