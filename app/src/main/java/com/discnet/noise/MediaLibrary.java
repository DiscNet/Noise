package com.discnet.noise;

import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.util.Size;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;

/** Shared media infrastructure. No originals are decoded for gallery thumbnails. */
final class MediaLibrary {
    static final int PAGE_SIZE=96;
    static final class Photo {
        final long id, added;
        final String folder,bucket,mime,name;
        final Uri uri;
        Photo(long id,long added,String folder,String bucket,String mime,String name){
            this.id=id;this.added=added;this.folder=folder==null?"":folder;
            this.bucket=bucket==null?"":bucket;
            this.mime=mime==null?"image/jpeg":mime;this.name=name==null?"Imagem":name;
            uri=ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,id);
        }
    }
    static final class Page {
        final ArrayList<Photo> items;
        final boolean hasMore;
        Page(ArrayList<Photo> list,boolean more){items=list;hasMore=more;}
    }
    private final Context context;
    private final ExecutorService work=Executors.newFixedThreadPool(3);
    private final android.util.LruCache<String,Bitmap> thumbnails=new android.util.LruCache<String,Bitmap>(18*1024*1024){
        @Override protected int sizeOf(String key,Bitmap bmp){return bmp.getByteCount();}
    };
    private final Set<String> pending=Collections.synchronizedSet(new HashSet<>());
    private volatile boolean closed;
    MediaLibrary(Context c){context=c.getApplicationContext();}
    void execute(Runnable r){if(!closed)work.execute(r);}
    boolean closed(){return closed;}
    static String dateTitle(long unixSeconds){
        Calendar now=Calendar.getInstance(), item=Calendar.getInstance();
        item.setTimeInMillis(unixSeconds*1000L);
        if(sameDay(now,item))return "Hoje";
        now.add(Calendar.DAY_OF_YEAR,-1);
        if(sameDay(now,item))return "Ontem";
        return new SimpleDateFormat("dd 'de' MMMM 'de' yyyy",
            new Locale("pt","BR")).format(item.getTime());
    }
    private static boolean sameDay(Calendar a,Calendar b){
        return a.get(Calendar.ERA)==b.get(Calendar.ERA)
            &&a.get(Calendar.YEAR)==b.get(Calendar.YEAR)
            &&a.get(Calendar.DAY_OF_YEAR)==b.get(Calendar.DAY_OF_YEAR);
    }
    Page page(String bucket,Photo after){
        final String id=MediaStore.Images.Media._ID;
        final String date=MediaStore.Images.Media.DATE_ADDED;
        final String[] projection={id,date,MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,MediaStore.Images.Media.DISPLAY_NAME};
        ArrayList<String> args=new ArrayList<>();
        StringBuilder where=new StringBuilder("1=1");
        if(bucket!=null&&!bucket.isEmpty()){
            where.append(" AND ").append(MediaStore.Images.Media.BUCKET_ID).append("=?");
            args.add(bucket);
        }
        if(after!=null){
            where.append(" AND (").append(date).append("<? OR (")
                .append(date).append("=? AND ").append(id).append("<?))");
            args.add(String.valueOf(after.added));
            args.add(String.valueOf(after.added));
            args.add(String.valueOf(after.id));
        }
        ArrayList<Photo> found=new ArrayList<>();
        boolean more=false;
        try(Cursor cursor=context.getContentResolver().query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,projection,where.toString(),
            args.toArray(new String[0]),date+" DESC, "+id+" DESC")){
            if(cursor!=null){
                while(cursor.moveToNext()){
                    if(closed||Thread.currentThread().isInterrupted())break;
                    Photo p=new Photo(cursor.getLong(0),cursor.getLong(1),
                        cursor.getString(3),cursor.getString(2),
                        cursor.getString(4),cursor.getString(5));
                    if(found.size()>=PAGE_SIZE){more=true;break;}
                    found.add(p);
                }
            }
        }catch(SecurityException|IllegalArgumentException error){
            android.util.Log.w("NoiseGallery","Media access unavailable",error);
        }
        return new Page(found,more);
    }
    LinkedHashMap<String,String> buckets(){
        LinkedHashMap<String,String> data=new LinkedHashMap<>();
        // Enumerate bucket names only. MediaStore protects private/partial items.
        String[] fields={MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME};
        try(Cursor cursor=context.getContentResolver().query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,fields,null,null,
            MediaStore.Images.Media.DATE_ADDED+" DESC")){
            if(cursor!=null)while(cursor.moveToNext()&&!closed){
                String key=cursor.getString(0),label=cursor.getString(1);
                if(key!=null&&!data.containsKey(key))
                    data.put(key,label==null?"Álbum":label);
            }
        }catch(Exception error){android.util.Log.w("NoiseGallery","Albums",error);}
        return data;
    }
    interface ImageReady {void loaded(Bitmap bitmap);}
    void thumbnail(Photo photo,int size,ImageReady reply){
        final String key=photo.uri.toString()+"#"+size;
        Bitmap cached=thumbnails.get(key);
        if(cached!=null){reply.loaded(cached);return;}
        if(!pending.add(key)||closed)return;
        execute(()->{
            Bitmap bitmap=null;
            try{
                if(Build.VERSION.SDK_INT>=29)
                    bitmap=context.getContentResolver().loadThumbnail(
                        photo.uri,new Size(size,size),null);
                else bitmap=decode(photo.uri,size);
            }catch(Exception | OutOfMemoryError ignored){}
            pending.remove(key);
            if(closed){if(bitmap!=null)bitmap.recycle();return;}
            if(bitmap!=null){
                thumbnails.put(key,bitmap);
                final Bitmap result=bitmap;
                new Handler(Looper.getMainLooper()).post(()->{
                    if(!closed)reply.loaded(result);
                });
            }
        });
    }
    Bitmap decode(Uri uri,int maxEdge)throws IOException {
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inJustDecodeBounds=true;
        try(InputStream in=context.getContentResolver().openInputStream(uri)){
            if(in==null)throw new IOException("Imagem indisponível");
            BitmapFactory.decodeStream(in,null,options);
        }
        options.inJustDecodeBounds=false;options.inPreferredConfig=Bitmap.Config.ARGB_8888;
        options.inSampleSize=1;
        while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>maxEdge*1.4f)
            options.inSampleSize*=2;
        try(InputStream in=context.getContentResolver().openInputStream(uri)){
            if(in==null)throw new IOException("Imagem indisponível");
            Bitmap bitmap=BitmapFactory.decodeStream(in,null,options);
            if(bitmap==null)throw new IOException("Formato não suportado");
            try(InputStream meta=context.getContentResolver().openInputStream(uri)){
                if(meta!=null){
                    android.media.ExifInterface exif=new android.media.ExifInterface(meta);
                    int orientation=exif.getAttributeInt(
                        android.media.ExifInterface.TAG_ORIENTATION,
                        android.media.ExifInterface.ORIENTATION_NORMAL);
                    Matrix matrix=new Matrix();
                    if(orientation==android.media.ExifInterface.ORIENTATION_ROTATE_90)
                        matrix.postRotate(90);
                    else if(orientation==android.media.ExifInterface.ORIENTATION_ROTATE_180)
                        matrix.postRotate(180);
                    else if(orientation==android.media.ExifInterface.ORIENTATION_ROTATE_270)
                        matrix.postRotate(270);
                    else if(orientation==android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL)
                        matrix.postScale(-1,1);
                    else if(orientation==android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL)
                        matrix.postScale(1,-1);
                    if(!matrix.isIdentity()){
                        Bitmap upright=Bitmap.createBitmap(bitmap,0,0,
                            bitmap.getWidth(),bitmap.getHeight(),matrix,true);
                        if(upright!=bitmap){bitmap.recycle();bitmap=upright;}
                    }
                }
            }catch(Exception error){
                android.util.Log.d("NoiseGallery","EXIF indisponível",error);
            }
            return bitmap;
        }
    }
    static Uri duplicate(Context context,Photo photo)throws IOException {
        String mime=photo.mime;
        String extension=null;
        int dot=photo.name.lastIndexOf('.');
        if(dot>=0&&dot<photo.name.length()-2){
            String suffix=photo.name.substring(dot).toLowerCase(Locale.ROOT);
            if(suffix.matches("\\.[a-z0-9]{2,7}"))extension=suffix;
        }
        if(extension==null){
            String detected=android.webkit.MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(mime);
            extension=detected==null?".jpg":"."+detected;
        }
        String name="Noise-copy-"+System.currentTimeMillis()+extension;
        Uri created=null;
        File fallback=null;
        try{
            if(Build.VERSION.SDK_INT>=29){
                ContentValues v=new ContentValues();
                v.put(MediaStore.Images.Media.DISPLAY_NAME,name);
                v.put(MediaStore.Images.Media.MIME_TYPE,mime);
                v.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Noise!/Saved");
                v.put(MediaStore.Images.Media.IS_PENDING,1);
                created=context.getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);
                if(created==null)throw new IOException("Não foi possível criar a cópia");
            }else{
                File folder=new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES),"Noise!/Saved");
                if(!folder.exists()&&!folder.mkdirs())throw new IOException("Sem acesso à pasta");
                fallback=new File(folder,name);
            }
            try(InputStream in=context.getContentResolver().openInputStream(photo.uri);
                OutputStream out=fallback!=null?new FileOutputStream(fallback):
                    context.getContentResolver().openOutputStream(created,"w")){
                if(in==null||out==null)throw new IOException("Imagem não pode ser copiada");
                byte[] buffer=new byte[65536];int n;
                while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                out.flush();
            }
            if(created!=null){
                ContentValues ready=new ContentValues();
                ready.put(MediaStore.Images.Media.IS_PENDING,0);
                if(context.getContentResolver().update(created,ready,null,null)<1)
                    throw new IOException("Não foi possível finalizar a cópia");
            }else if(fallback!=null){
                android.media.MediaScannerConnection.scanFile(context,
                    new String[]{fallback.getPath()},new String[]{mime},null);
            }
            return created!=null?created:Uri.fromFile(fallback);
        }catch(Exception e){
            if(created!=null)context.getContentResolver().delete(created,null,null);
            if(fallback!=null)fallback.delete();
            if(e instanceof IOException)throw (IOException)e;
            throw new IOException("Erro ao copiar imagem",e);
        }
    }
    void close(){
        closed=true;work.shutdownNow();pending.clear();
        thumbnails.evictAll();
    }
}
