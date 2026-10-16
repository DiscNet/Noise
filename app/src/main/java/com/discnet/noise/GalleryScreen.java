package com.discnet.noise;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import java.util.*;

/** All-device MediaStore gallery; pickerMode has an intentionally separate route. */
final class GalleryScreen extends FrameLayout {
    interface Listener {
        void onPhoto(Uri uri);
        void onSystemGallery();
        void onGrantPermission();
        void onClose();
        void onProjects();
        void onShare(Uri uri);
        default void onViewerState(boolean showing){}
    }
    private final Activity activity;
    private final Listener listener;
    private final boolean pickerMode;
    private final MediaLibrary media;
    private final RecyclerView grid;
    private final GridLayoutManager layout;
    private final GalleryAdapter adapter=new GalleryAdapter();
    private final Spinner albums;
    private final TextView notice;
    private final LinearLayout actions;
    private final ArrayList<MediaLibrary.Photo> photos=new ArrayList<>();
    private final ArrayList<Item> rows=new ArrayList<>();
    private final ArrayList<String> keys=new ArrayList<>();
    private final ArrayList<String> titles=new ArrayList<>();
    private PhotoViewer viewer;
    private MediaLibrary.Photo last;
    private String selectedBucket="";
    private int generation=0,columns=4;
    private boolean loading,hasMore=true,ready,permission,closed,settingAlbums;
    private final Handler handler=new Handler(Looper.getMainLooper());

    static final class Item {
        final String section;
        final MediaLibrary.Photo photo;
        final boolean featured;
        Item(String heading){section=heading;photo=null;featured=false;}
        Item(MediaLibrary.Photo p,boolean featured){
            section=null;photo=p;this.featured=featured;
        }
    }
    GalleryScreen(Activity a,Listener listener){this(a,listener,false);}
    GalleryScreen(Activity a,Listener listener,boolean picker){
        super(a);activity=a;this.listener=listener;pickerMode=picker;
        media=new MediaLibrary(a);
        setBackgroundColor(0xff080808);
        LinearLayout page=new LinearLayout(a);
        page.setPadding(dp(10),dp(7),dp(10),dp(picker?12:75));
        page.setOrientation(LinearLayout.VERTICAL);
        addView(page,new LayoutParams(-1,-1));
        LinearLayout head=new LinearLayout(a);head.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(head,new LinearLayout.LayoutParams(-1,dp(55)));
        ImageView back=IconArt.button(a,IconArt.BACK,"Voltar");
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setImageDrawable(new IconArt(IconArt.BACK,0xffffffff));
        head.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));
        back.setOnClickListener(v->listener.onClose());
        TextView title=label(picker?"Escolher imagem":"Galeria",24,0xffffffff);
        title.setTypeface(NoiseTypography.bold(a));
        LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(0,-1,1);
        titleLp.leftMargin=dp(9);head.addView(title,titleLp);
        ImageView sys=IconArt.button(a,IconArt.GALLERY,"Selecionar pelo Android");
        sys.setBackgroundColor(Color.TRANSPARENT);
        sys.setImageDrawable(new IconArt(IconArt.GALLERY,0xffdedede));
        head.addView(sys,new LinearLayout.LayoutParams(dp(44),dp(44)));
        sys.setOnClickListener(v->listener.onSystemGallery());

        albums=new Spinner(a);
        albums.setContentDescription("Álbuns e pastas");
        GradientDrawable pick=new GradientDrawable();
        pick.setColor(0xff202020);pick.setCornerRadius(dp(8));
        albums.setBackground(pick);
        LinearLayout.LayoutParams al=new LinearLayout.LayoutParams(-1,dp(45));
        al.bottomMargin=dp(7);page.addView(albums,al);
        titles.add("Todas as fotos");keys.add("");
        showAlbums();
        albums.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onNothingSelected(android.widget.AdapterView<?> p){}
            @Override public void onItemSelected(android.widget.AdapterView<?> p,View view,int position,long id){
                if(settingAlbums||position<0||position>=keys.size())return;
                String bucket=keys.get(position);
                if(!bucket.equals(selectedBucket)){selectedBucket=bucket;loadFirstPage(true);}
            }
        });
        notice=label("Carregando fotografias…",13,0xffb4b4b4);
        notice.setGravity(Gravity.CENTER);
        page.addView(notice,new LinearLayout.LayoutParams(-1,dp(35)));
        grid=new RecyclerView(a);
        grid.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        grid.setItemAnimator(null);
        grid.setClipToPadding(false);
        layout=new GridLayoutManager(a,columns);
        layout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup(){
            @Override public int getSpanSize(int position){
                if(position<0||position>=rows.size())return 1;
                Item item=rows.get(position);
                if(item.photo==null)return columns;
                return item.featured&&columns>=3?2:1;
            }
        });
        grid.setLayoutManager(layout);grid.setAdapter(adapter);
        grid.addOnScrollListener(new RecyclerView.OnScrollListener(){
            @Override public void onScrolled(RecyclerView v,int dx,int dy){
                if(dy>=0&&layout.findLastVisibleItemPosition()>=rows.size()-13)
                    loadNextPage();
            }
        });
        page.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
        actions=new LinearLayout(a);actions.setGravity(Gravity.CENTER_VERTICAL);
        TextView grant=button("Permitir acesso");
        TextView external=button("Seletor do Android");
        LinearLayout.LayoutParams weight=new LinearLayout.LayoutParams(0,dp(50),1);
        actions.addView(grant,weight);
        LinearLayout.LayoutParams wt2=new LinearLayout.LayoutParams(0,dp(50),1);
        wt2.leftMargin=dp(7);actions.addView(external,wt2);
        grant.setOnClickListener(v->listener.onGrantPermission());
        external.setOnClickListener(v->listener.onSystemGallery());
        page.addView(actions,new LinearLayout.LayoutParams(-1,dp(55)));
        refresh(false);
    }
    static boolean isNoisePhoto(String path,String album){
        String folder=path==null?"":path.toLowerCase(Locale.ROOT);
        String label=album==null?"":album.toLowerCase(Locale.ROOT);
        return folder.startsWith("pictures/noise!/")||folder.startsWith("dcim/noise!/")
            ||label.equals("noise!");
    }
    private TextView label(String value,int size,int color){
        TextView text=new TextView(activity);
        text.setText(value);text.setTextSize(size);text.setTextColor(color);
        text.setGravity(Gravity.CENTER_VERTICAL);
        return text;
    }
    private TextView button(String title){
        TextView t=label(title,13,0xffffffff);
        t.setGravity(Gravity.CENTER);t.setBackgroundColor(0xff242424);
        return t;
    }
    private int dp(float n){return EditorTheme.dp(activity,n);}
    static boolean isFeatured(int imageIndex,boolean picker,int columns){
        return !picker&&columns>=3&&imageIndex%11==0;
    }
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){
        super.onSizeChanged(w,h,ow,oh);
        int cols=w>=dp(375)?4:w>=dp(295)?3:2;
        if(cols!=columns){
            columns=cols;layout.setSpanCount(cols);
            adapter.notifyDataSetChanged();
        }
    }
    private void showAlbums(){
        settingAlbums=true;
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(activity,
            android.R.layout.simple_spinner_dropdown_item,titles){
            @Override public View getView(int pos,View v,android.view.ViewGroup parent){
                TextView label=(TextView)super.getView(pos,v,parent);
                label.setTextColor(Color.WHITE);
                label.setPadding(dp(13),0,dp(10),0);
                return label;
            }
        };
        albums.setAdapter(adapter);
        albums.setSelection(Math.max(0,keys.indexOf(selectedBucket)),false);
        settingAlbums=false;
    }
    void refresh(boolean hasAccess){
        if(closed)return;
        boolean changed=!ready||permission!=hasAccess;
        permission=hasAccess;
        if(!changed)return; // preserves scroll position and decoded thumbnails between tabs
        ready=true;actions.setVisibility(hasAccess?GONE:VISIBLE);
        if(!hasAccess){
            rows.clear();photos.clear();adapter.notifyDataSetChanged();
            notice.setText("Permita acesso às fotografias ou use o seletor Android.");
            notice.setVisibility(VISIBLE);return;
        }
        media.execute(()->{
            LinkedHashMap<String,String> found=media.buckets();
            handler.post(()->{
                if(closed||!permission)return;
                keys.clear();titles.clear();
                keys.add("");titles.add("Todas as fotos");
                for(Map.Entry<String,String> b:found.entrySet()){
                    keys.add(b.getKey());titles.add(b.getValue());
                }
                showAlbums();
            });
        });
        loadFirstPage(false);
    }
    private void loadFirstPage(boolean scrollTop){
        ++generation;loading=false;hasMore=true;last=null;
        photos.clear();rows.clear();adapter.notifyDataSetChanged();
        if(scrollTop)grid.scrollToPosition(0);
        notice.setText("Carregando fotografias…");notice.setVisibility(VISIBLE);
        loadNextPage();
    }
    private void loadNextPage(){
        if(closed||!permission||!hasMore||loading)return;
        loading=true;
        final int token=generation;
        final MediaLibrary.Photo after=last;
        final String bucket=selectedBucket;
        media.execute(()->{
            MediaLibrary.Page page=media.page(bucket,after);
            handler.post(()->{
                if(closed||token!=generation)return;
                loading=false;hasMore=page.hasMore;
                if(!page.items.isEmpty())last=page.items.get(page.items.size()-1);
                HashSet<Long> ids=new HashSet<>();
                for(MediaLibrary.Photo p:photos)ids.add(p.id);
                String prev=photos.isEmpty()?null:
                    MediaLibrary.dateTitle(photos.get(photos.size()-1).added);
                int start=rows.size();
                ArrayList<MediaLibrary.Photo> unique=new ArrayList<>();
                for(MediaLibrary.Photo p:page.items){
                    if(!ids.add(p.id))continue;
                    String section=MediaLibrary.dateTitle(p.added);
                    if(!section.equals(prev)){rows.add(new Item(section));prev=section;}
                    // Magazine rhythm: one larger photograph in each compact
                    // sequence. Still a recycling grid, not a giant full bitmap.
                    boolean featured=isFeatured(photos.size(),pickerMode,columns);
                    photos.add(p);unique.add(p);rows.add(new Item(p,featured));
                }
                if(rows.size()>start)adapter.notifyItemRangeInserted(start,rows.size()-start);
                if(viewer!=null)viewer.append(unique);
                notice.setText(photos.isEmpty()?
                    "Nenhuma fotografia disponível para as permissões atuais.":"");
                notice.setVisibility(photos.isEmpty()?VISIBLE:GONE);
            });
        });
    }
    private void open(int index){
        if(index<0||index>=photos.size())return;
        if(pickerMode){
            listener.onPhoto(photos.get(index).uri);
            return;
        }
        viewer=new PhotoViewer(activity,media,new ArrayList<>(photos),index,
            new PhotoViewer.Actions(){
                @Override public void back(){closeViewer();}
                @Override public void edit(Uri uri){closeViewer();listener.onPhoto(uri);}
                @Override public void share(Uri uri){listener.onShare(uri);}
                @Override public void download(MediaLibrary.Photo photo){downloadPhoto(photo);}
                @Override public void nearingEnd(){loadNextPage();}
            });
        addView(viewer,new LayoutParams(-1,-1));viewer.bringToFront();
        listener.onViewerState(true);
    }
    void downloadPhoto(MediaLibrary.Photo p){
        if(Build.VERSION.SDK_INT<29&&activity.checkSelfPermission(
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE)!=
                android.content.pm.PackageManager.PERMISSION_GRANTED){
            if(activity instanceof MainActivity)
                ((MainActivity)activity).requestGalleryDownloadPermission(p);
            return;
        }
        media.execute(()->{
            String message;
            try{
                MediaLibrary.duplicate(activity.getApplicationContext(),p);
                message="Cópia salva em Pictures/Noise!/Saved";
            }catch(Exception error){
                message="Não foi possível salvar: verifique espaço e permissões";
            }
            final String finalMessage=message;
            handler.post(()->{
                if(viewer!=null)viewer.showMessage(finalMessage);
            });
        });
    }
    private void closeViewer(){
        if(viewer==null)return;
        viewer.close();removeView(viewer);viewer=null;
        listener.onViewerState(false);
    }
    boolean dismissPhotoActionsPending(){return viewer!=null;}
    boolean dismissPhotoActions(){
        if(viewer==null)return false;
        closeViewer();return true;
    }
    void hidePhotoActions(){dismissPhotoActions();}
    private final class GalleryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>{
        @Override public int getItemViewType(int p){return rows.get(p).photo==null?0:1;}
        @Override public RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup parent,int type){
            if(type==0){
                TextView t=label("",14,0xfff4f4f4);
                t.setTypeface(NoiseTypography.medium(activity));
                t.setPadding(dp(3),dp(10),0,dp(5));
                return new RecyclerView.ViewHolder(t){};
            }
            ImageView view=new ImageView(activity);
            view.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable bg=new GradientDrawable();
            bg.setColor(0xff222222);bg.setCornerRadius(dp(7));
            view.setBackground(bg);view.setClipToOutline(true);
            return new RecyclerView.ViewHolder(view){};
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder h,int pos){
            Item item=rows.get(pos);
            if(item.photo==null){
                ((TextView)h.itemView).setText(item.section);
                h.itemView.setLayoutParams(new RecyclerView.LayoutParams(-1,dp(42)));
                return;
            }
            ImageView cell=(ImageView)h.itemView;
            int tileSize=Math.max(dp(68),(getWidth()-dp(25))/Math.max(1,columns)-dp(5));
            int span=item.featured&&columns>=3?2:1;
            int height=span==2?Math.round(tileSize*1.42f):tileSize;
            RecyclerView.LayoutParams p=new RecyclerView.LayoutParams(-1,height);
            p.setMargins(dp(2),dp(2),dp(2),dp(2));
            cell.setLayoutParams(p);
            final MediaLibrary.Photo photo=item.photo;
            final String key=photo.uri.toString();
            cell.setTag(key);cell.setImageDrawable(null);
            cell.setContentDescription("Visualizar "+photo.name);
            cell.setOnClickListener(v->open(photos.indexOf(photo)));
            media.thumbnail(photo,260,bitmap->{
                if(!closed&&key.equals(cell.getTag()))cell.setImageBitmap(bitmap);
            });
        }
        @Override public int getItemCount(){return rows.size();}
    }
    void dispose(){
        closed=true;++generation;closeViewer();
        media.close();handler.removeCallbacksAndMessages(null);
    }
}
