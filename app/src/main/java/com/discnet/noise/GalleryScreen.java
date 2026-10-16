package com.discnet.noise;

import android.app.Activity;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.util.LruCache;
import android.util.Size;
import android.view.*;
import android.widget.*;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;

/**
 * Native MediaStore gallery: recent device images, album/folder dropdown, photo
 * grid with async thumbnails, and a direct platform photo-picker fallback.
 * No Internet access, no third-party gallery/thumbnail library.
 */
final class GalleryScreen extends FrameLayout {
    interface Listener {
        void onPhoto(Uri uri);
        void onSystemGallery();
        void onGrantPermission();
        void onClose();
        void onProjects();
        void onShare(Uri uri);
    }
    private final Set<String> pendingThumbnails=Collections.synchronizedSet(new HashSet<>());
    private final Activity activity;
    private final Listener listener;
    private final ExecutorService work=Executors.newFixedThreadPool(3);
    private final LruCache<String,Bitmap> thumbnails=new LruCache<String,Bitmap>(14*1024*1024){
        @Override protected int sizeOf(String k,Bitmap v){return v.getByteCount();}
    };
    private final ArrayList<Photo> photos=new ArrayList<>();
    private final ArrayList<Photo> filtered=new ArrayList<>();
    private int folderRequest=0;
    private final ArrayList<String> albumNames=new ArrayList<>();
    private final ArrayList<String> bucketKeys=new ArrayList<>();
    private final Spinner albums;
    private final GridView grid;
    private final TextView notice;
    private final LinearLayout actions;
    private final PhotoAdapter adapter;
    private View choiceOverlay;
    private int generation=0;
    private volatile boolean released=false;
    private final Handler ui=new Handler(Looper.getMainLooper());

    private static final class Photo {
        final Uri uri;
        final String bucket, album;
        Photo(Uri uri,String bucket,String album){this.uri=uri;this.bucket=bucket;this.album=album;}
    }
    GalleryScreen(Activity activity,Listener listener){
        super(activity);this.activity=activity;this.listener=listener;
        setBackground(EditorTheme.panel(activity,0xff101216,0xff101216,0,0));
        setPadding(dp(15),dp(8),dp(15),dp(75));
        LinearLayout root=new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        addView(root,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout heading=new LinearLayout(activity);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(heading,new LinearLayout.LayoutParams(-1,dp(56)));
        ImageView close=IconArt.button(activity,IconArt.BACK,"Voltar para edição");
        heading.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));
        close.setOnClickListener(v->listener.onClose());
        TextView title=txt("Galeria",20,EditorTheme.INK);
        title.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,dp(48),1);
        tp.leftMargin=dp(12);heading.addView(title,tp);
        ImageView system=IconArt.button(activity,IconArt.GALLERY,"Abrir galeria padrão do celular");
        heading.addView(system,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView projects=button("Projetos");heading.addView(projects,new LinearLayout.LayoutParams(dp(80),dp(48)));projects.setOnClickListener(v->listener.onProjects());
        system.setOnClickListener(v->listener.onSystemGallery());
        TextView label=txt("FOTOS / ÁLBUNS E PASTAS",11,EditorTheme.MUTED);
        label.setLetterSpacing(.10f);root.addView(label,new LinearLayout.LayoutParams(-1,dp(28)));
        albums=new Spinner(activity);
        albums.setContentDescription("Escolher álbum ou pasta");
        albums.setBackground(EditorTheme.surface(activity,18));
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(48));
        ap.bottomMargin=dp(12);root.addView(albums,ap);
        albums.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> parent){}
            public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){
                selectAlbum(position);
            }
        });
        notice=txt("Carregando imagens...",14,EditorTheme.MUTED);
        notice.setGravity(Gravity.CENTER);notice.setMinHeight(dp(44));root.addView(notice,new LinearLayout.LayoutParams(-1,-2));
        grid=new GridView(activity);
        grid.setNumColumns(GridView.AUTO_FIT);grid.setColumnWidth(dp(108));grid.setVerticalSpacing(dp(5));grid.setHorizontalSpacing(dp(5));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setClipToPadding(false);grid.setPadding(0,dp(5),0,dp(12));
        grid.setVerticalScrollBarEnabled(false);
        adapter=new PhotoAdapter();grid.setAdapter(adapter);
        root.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
        grid.setOnItemClickListener((parent,view,position,id)->{
            if(position>=0&&position<filtered.size())showPhotoActions(filtered.get(position).uri);
        });
        actions=new LinearLayout(activity);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(actions,new LinearLayout.LayoutParams(-1,dp(61)));
        TextView permit=button("Permitir acesso às fotos");
        actions.addView(permit,new LinearLayout.LayoutParams(0,dp(48),1));
        permit.setOnClickListener(v->listener.onGrantPermission());
        TextView openPicker=button("Galeria do celular");
        LinearLayout.LayoutParams pickParams=new LinearLayout.LayoutParams(0,dp(48),1);
        pickParams.leftMargin=dp(8);actions.addView(openPicker,pickParams);
        openPicker.setOnClickListener(v->listener.onSystemGallery());
        refresh(false);
    }
    private int dp(float size){return EditorTheme.dp(activity,size);}
    private void showPhotoActions(Uri uri){
        hidePhotoActions();
        FrameLayout overlay=new FrameLayout(activity);
        overlay.setBackgroundColor(0xe909070e);
        overlay.setClickable(true);
        overlay.setOnClickListener(v->hidePhotoActions());
        LinearLayout panel=new LinearLayout(activity);
        panel.setPadding(dp(22),dp(19),dp(22),dp(19));
        panel.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable surface=new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            new int[]{OrbitalMaterial.DEEP_PURPLE,OrbitalMaterial.BLACK});
        surface.setCornerRadius(dp(6));
        panel.setBackground(surface);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER);
        lp.leftMargin=dp(22);lp.rightMargin=dp(22);
        overlay.addView(panel,lp);
        panel.setOnClickListener(v->{});
        TextView heading=txt("Imagem selecionada",19,OrbitalMaterial.WHITE);
        panel.addView(heading,new LinearLayout.LayoutParams(-1,dp(46)));
        TextView edit=button("Editar imagem");
        edit.setTextColor(OrbitalMaterial.BLACK);
        edit.setBackground(EditorTheme.buttonSurface(activity,4,true));
        panel.addView(edit,new LinearLayout.LayoutParams(-1,dp(50)));
        edit.setOnClickListener(v->{hidePhotoActions();listener.onPhoto(uri);});
        TextView share=button("Compartilhar");
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(50));
        sp.topMargin=dp(10);panel.addView(share,sp);
        share.setOnClickListener(v->{hidePhotoActions();listener.onShare(uri);});
        TextView cancel=button("Cancelar");
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(43));
        cp.topMargin=dp(10);panel.addView(cancel,cp);
        cancel.setOnClickListener(v->hidePhotoActions());
        choiceOverlay=overlay;addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        overlay.bringToFront();
    }
    void hidePhotoActions(){if(choiceOverlay!=null){removeView(choiceOverlay);choiceOverlay=null;}}
    boolean dismissPhotoActions(){if(choiceOverlay==null)return false;hidePhotoActions();return true;}
    boolean dismissPhotoActionsPending(){return choiceOverlay!=null;}
    private TextView txt(String s,int size,int color){
        TextView v=new TextView(activity);v.setText(s);v.setTextSize(size);v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);return v;
    }
    private TextView button(String name){
        TextView v=txt(name,13,EditorTheme.INK);v.setGravity(Gravity.CENTER);
        EditorTheme.button(v,false);return v;
    }
    void refresh(boolean hasAccess){
        if(released)return;
        int token=++generation;
        folderRequest++;
        if(!hasAccess){
            photos.clear();filtered.clear();adapter.notifyDataSetChanged();
            albumNames.clear();bucketKeys.clear();albumNames.add("Todas as fotos");
            bucketKeys.add("");
            updateAlbums();
            notice.setText("Permita o acesso para ver suas fotos e pastas aqui.\nOu use a galeria padrão.");
            notice.setVisibility(VISIBLE);
            actions.setVisibility(VISIBLE);return;
        }
        actions.setVisibility(GONE);
        notice.setText("Carregando imagens...");
        notice.setVisibility(VISIBLE);
        work.execute(()->{
            ArrayList<Photo> found=new ArrayList<>();
            LinkedHashMap<String,String> discovered=new LinkedHashMap<>();
            try{
                String[] columns=Build.VERSION.SDK_INT>=29?
                    new String[]{MediaStore.Images.Media._ID,MediaStore.Images.Media.BUCKET_ID,
                        MediaStore.Images.Media.BUCKET_DISPLAY_NAME,MediaStore.Images.Media.RELATIVE_PATH}:
                    new String[]{MediaStore.Images.Media._ID,MediaStore.Images.Media.BUCKET_ID,
                        MediaStore.Images.Media.BUCKET_DISPLAY_NAME};
                try(Cursor cursor=activity.getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,columns,null,null,
                    MediaStore.Images.Media.DATE_ADDED+" DESC")){
                    if(cursor!=null){
                        int id=cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                        int bucket=cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID);
                        int name=cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME);
                        int path=cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH);
                        // Iterate all rows to discover every album, even ones
                        // whose latest image falls outside the recent-500 view.
                        while(cursor.moveToNext()){
                            String key=cursor.getString(bucket);
                            String label=cursor.getString(name);
                            String location=path<0?null:cursor.getString(path);
                            if(!isNoisePhoto(location,label))continue;
                            Uri uri=ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,cursor.getLong(id));
                            Photo photo=new Photo(uri,key==null?"":key,
                                label==null||label.isEmpty()?"Sem álbum":label);
                            found.add(photo);
                            discovered.put(photo.bucket,photo.album);
                        }
                    }
                }
            }catch(SecurityException | IllegalArgumentException ignored){}
            if(released)return;
            ui.post(()->{
                if(released||token!=generation)return;
                photos.clear();photos.addAll(found);
                albumNames.clear();bucketKeys.clear();
                albumNames.add("Todas as fotos");bucketKeys.add("");
                for(Map.Entry<String,String> item:discovered.entrySet()){
                    bucketKeys.add(item.getKey());
                    albumNames.add(item.getValue());
                }
                updateAlbums();
                showRecent();
                notice.setText(found.isEmpty()?"Nenhuma foto editada ou capturada no Noise! encontrada.":"");
                notice.setVisibility(found.isEmpty()?VISIBLE:GONE);
            });
        });
    }
    private void updateAlbums(){
        ArrayAdapter<String> list=new ArrayAdapter<String>(activity,
            android.R.layout.simple_spinner_dropdown_item,albumNames){
            @Override public View getView(int pos,View convert,android.view.ViewGroup parent){
                TextView v=(TextView)super.getView(pos,convert,parent);
                v.setTextColor(EditorTheme.INK);v.setPadding(dp(14),0,dp(12),0);
                return v;
            }
        };
        albums.setAdapter(list);
    }
    /** All images in a selected folder, not the recent subset. */
    private void selectAlbum(int index){
        if(index<0||index>=bucketKeys.size())return;
        if(index==0){showRecent();return;}
        final String selectedBucket=bucketKeys.get(index);
        final int token=++folderRequest;
        final int galleryToken=generation;
        notice.setVisibility(VISIBLE);
        notice.setText("Carregando todas as fotos da pasta...");
        work.execute(()->{
            ArrayList<Photo> folder=new ArrayList<>();
            try{
                String[] projection=Build.VERSION.SDK_INT>=29?
                    new String[]{MediaStore.Images.Media._ID,
                        MediaStore.Images.Media.BUCKET_DISPLAY_NAME,MediaStore.Images.Media.RELATIVE_PATH}:
                    new String[]{MediaStore.Images.Media._ID,
                        MediaStore.Images.Media.BUCKET_DISPLAY_NAME};
                try(Cursor cursor=activity.getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    MediaStore.Images.Media.BUCKET_ID+"=?",
                    new String[]{selectedBucket},
                    MediaStore.Images.Media.DATE_ADDED+" DESC")){
                    if(cursor!=null){
                        int id=cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                        int name=cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME);
                        int path=cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH);
                        while(cursor.moveToNext()){
                            String album=cursor.getString(name);
                            if(!isNoisePhoto(path<0?null:cursor.getString(path),album))continue;
                            folder.add(new Photo(ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,cursor.getLong(id)),
                                selectedBucket,album==null?"Sem álbum":album));
                        }
                    }
                }
            }catch(SecurityException | IllegalArgumentException ignored){}
            ui.post(()->{
                if(released||galleryToken!=generation||token!=folderRequest)return;
                filtered.clear();filtered.addAll(folder);adapter.notifyDataSetChanged();
                notice.setText(folder.isEmpty()?"Esta pasta não tem fotos acessíveis.":"");
                notice.setVisibility(folder.isEmpty()?VISIBLE:GONE);
            });
        });
    }
    static boolean isNoisePhoto(String path,String album){
        // ExportManager and CameraPhotoPipeline both publish to Pictures/Noise!.
        String normalized=path==null?"":path.toLowerCase(java.util.Locale.ROOT);
        String bucket=album==null?"":album.toLowerCase(java.util.Locale.ROOT);
        return normalized.startsWith("pictures/noise!/") ||
            normalized.startsWith("dcim/noise!/") ||
            bucket.equals("noise!");
    }
    private void showRecent(){
        folderRequest++;
        filtered.clear();filtered.addAll(photos);adapter.notifyDataSetChanged();
        notice.setVisibility(filtered.isEmpty()?VISIBLE:GONE);
        notice.setText(filtered.isEmpty()?"Nenhuma foto do Noise! encontrada.":"");
    }
    private final class PhotoAdapter extends BaseAdapter {
        @Override public int getCount(){return filtered.size();}
        @Override public Object getItem(int pos){return filtered.get(pos);}
        @Override public long getItemId(int pos){return pos;}
        @Override public View getView(int pos,View old,android.view.ViewGroup parent){
            ImageView view=old instanceof ImageView?(ImageView)old:new ImageView(activity);
            view.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1,dp(124)));
            view.setScaleType(ImageView.ScaleType.CENTER_CROP);
            view.setBackground(EditorTheme.panel(activity,0xff251f35,0xff181523,9,0x28ffffff));
            if(pos>=filtered.size())return view;
            Uri uri=filtered.get(pos).uri;
            String key=uri.toString();
            view.setTag(key);view.setContentDescription("Abrir imagem de "+filtered.get(pos).album);
            Bitmap cached=thumbnails.get(key);
            view.setImageBitmap(cached);
            if(cached==null&&!released&&pendingThumbnails.add(key)){
                work.execute(()->{
                    Bitmap bmp=null;
                    try{
                        if(Build.VERSION.SDK_INT>=29){
                            bmp=activity.getContentResolver().loadThumbnail(uri,new Size(240,240),null);
                        } else {
                            android.graphics.BitmapFactory.Options bounds=new BitmapFactory.Options();
                            bounds.inJustDecodeBounds=true;
                            try(InputStream stream=activity.getContentResolver().openInputStream(uri)){
                                BitmapFactory.decodeStream(stream,null,bounds);
                            }
                            bounds.inJustDecodeBounds=false;
                            bounds.inSampleSize=1;
                            while(Math.max(bounds.outWidth,bounds.outHeight)/bounds.inSampleSize>256)
                                bounds.inSampleSize*=2;
                            try(InputStream stream=activity.getContentResolver().openInputStream(uri)){
                                bmp=BitmapFactory.decodeStream(stream,null,bounds);
                            }
                        }
                    }catch(Exception | OutOfMemoryError ignored){}
                    pendingThumbnails.remove(key);
                    final Bitmap thumbnail=bmp;
                    if(thumbnail!=null)ui.post(()->{
                        if(released){thumbnail.recycle();return;}
                        thumbnails.put(key,thumbnail);
                        if(key.equals(view.getTag()))view.setImageBitmap(thumbnail);
                    });
                });
            }
            return view;
        }
    }
    void dispose(){
        hidePhotoActions();released=true;generation++;
        work.shutdownNow();thumbnails.evictAll();
    }
}
