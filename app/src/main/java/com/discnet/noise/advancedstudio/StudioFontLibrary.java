package com.discnet.noise.advancedstudio;

import android.content.Context;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.database.Cursor;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** App-private, stable font IDs. Font files are never placed in .noise zip files. */
public final class StudioFontLibrary {
    private static File folder;
    private static final Map<String,Typeface> CACHE=new HashMap<>();
    private StudioFontLibrary(){}
    public static synchronized void init(Context context){
        if(folder==null){folder=new File(context.getApplicationContext().getFilesDir(),"studio_fonts");folder.mkdirs();}
    }
    public static synchronized String importFont(Context context,Uri uri)throws Exception{
        init(context);
        String display="font.ttf";
        try(Cursor c=context.getContentResolver().query(uri,
            new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null&&c.moveToFirst())display=c.getString(0);
        }catch(Exception ignored){}
        if(display==null||!(display.toLowerCase(Locale.ROOT).endsWith(".ttf")||
                display.toLowerCase(Locale.ROOT).endsWith(".otf")))
            throw new IOException("Selecione um arquivo .ttf ou .otf");
        String ext=display.toLowerCase(Locale.ROOT).endsWith(".otf")?".otf":".ttf";
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        File temp=File.createTempFile("font-",ext,folder);
        boolean installed=false;
        try{
            long total=0;
            try(InputStream in=context.getContentResolver().openInputStream(uri);
                OutputStream out=new FileOutputStream(temp)){
                if(in==null)throw new IOException("Fonte não encontrada");
                byte[] buffer=new byte[8192];int count;
                while((count=in.read(buffer))!=-1){
                    total+=count;if(total>16*1024*1024)throw new IOException("Fonte maior que 16 MB");
                    hash.update(buffer,0,count);out.write(buffer,0,count);
                }
                if(total<200)throw new IOException("Arquivo de fonte inválido");
            }
            Typeface type=Typeface.createFromFile(temp);
            if(type==null)throw new IOException("Fonte incompatível");
            StringBuilder key=new StringBuilder();
            for(byte b:hash.digest())key.append(String.format(Locale.ROOT,"%02x",b&255));
            String filename=key.substring(0,32)+ext;
            File destination=new File(folder,filename);
            if(!destination.exists()&&!temp.renameTo(destination))
                throw new IOException("Não foi possível armazenar a fonte");
            CACHE.put(filename,Typeface.createFromFile(destination));
            installed=true;
            return "custom:"+filename;
        }finally{
            if(!installed||temp.exists())temp.delete();
        }
    }
    public static synchronized Typeface resolve(String family,int style){
        if(folder==null||family==null||!family.startsWith("custom:"))return null;
        String file=family.substring(7);
        if(!file.matches("[a-f0-9]{32}\\.(ttf|otf)"))return null;
        File path=new File(folder,file);
        if(!path.isFile())return null;
        try{
            Typeface type=CACHE.get(file);
            if(type==null){type=Typeface.createFromFile(path);CACHE.put(file,type);}
            return Typeface.create(type,style);
        }catch(RuntimeException ignored){return null;}
    }
    public static String label(String family){
        return family!=null&&family.startsWith("custom:")?
            "Fonte personalizada · "+family.substring(7,Math.min(15,family.length())):family;
    }
}
