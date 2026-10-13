package com.discnet.noise;

import android.content.Context;
import android.net.Uri;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** App-private editable projects with independent durable copies of originals. */
final class ProjectStore {
    static final class Project {
        final String id,name;final File source;final EditorDocument document;final JSONObject history;
        Project(String id,String name,File source,EditorDocument d,JSONObject h){this.id=id;this.name=name;this.source=source;document=d;history=h;}
    }
    static final class Preset {
        String id,name;boolean favorite;EditorDocument document;
        Preset(String id,String name,EditorDocument d){this.id=id;this.name=name;document=d;}
    }
    private final File projects,presets;
    private final Context context;
    ProjectStore(Context c){context=c.getApplicationContext();projects=new File(c.getFilesDir(),"projects");presets=new File(c.getFilesDir(),"presets");projects.mkdirs();presets.mkdirs();}
    static String newId(){return UUID.randomUUID().toString();}
    File directory(String id)throws IOException{if(!id.matches("[a-f0-9-]{36}"))throw new IOException("Identificador inválido");File dir=new File(projects,id);if(!dir.exists()&&!dir.mkdirs())throw new IOException("Sem espaço para criar projeto");return dir;}
    File importOriginal(Uri uri,String id)throws IOException{
        File dir=directory(id),target=new File(dir,"original"),pending=new File(dir,"original.part");
        try(InputStream in=context.getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(pending)){
            if(in==null)throw new IOException("Foto indisponível");byte[] b=new byte[65536];int n;
            while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("Importação cancelada");out.write(b,0,n);}out.getFD().sync();
        }catch(IOException e){pending.delete();throw e;}
        if(!pending.renameTo(target)){pending.delete();throw new IOException("Não foi possível guardar o original");}return target;
    }
    void save(String id,String name,EditorDocument d,JSONObject history)throws Exception{
        JSONObject o=new JSONObject().put("name",name).put("document",d.toJson()).put("history",history).put("updated",System.currentTimeMillis());
        write(new File(directory(id),"project.json"),o);
    }
    Project read(String id)throws Exception{
        File dir=directory(id);JSONObject o=readJson(new File(dir,"project.json"));File src=new File(dir,"original");
        if(!src.isFile())throw new IOException("Original do projeto indisponível");
        return new Project(id,o.optString("name","Sem título"),src,EditorDocument.fromJson(o.getJSONObject("document")),o.optJSONObject("history"));
    }
    ArrayList<Project> list(){ArrayList<Project> all=new ArrayList<>();File[] dirs=projects.listFiles();if(dirs!=null){Arrays.sort(dirs,(a,b)->Long.compare(new File(b,"project.json").lastModified(),new File(a,"project.json").lastModified()));for(File f:dirs)try{all.add(read(f.getName()));}catch(Exception ignored){}}return all;}
    void delete(String id)throws IOException{File dir=directory(id);File[] files=dir.listFiles();if(files!=null)for(File f:files)if(!f.delete())throw new IOException("Não foi possível excluir projeto");dir.delete();}
    void savePreset(Preset p)throws Exception{write(new File(presets,p.id+".json"),new JSONObject().put("id",p.id).put("name",p.name).put("favorite",p.favorite).put("document",p.document.toJson()));}
    ArrayList<Preset> presets(){ArrayList<Preset> all=new ArrayList<>();File[] files=presets.listFiles();if(files!=null)for(File f:files)try{JSONObject o=readJson(f);Preset p=new Preset(o.getString("id"),o.getString("name"),EditorDocument.fromJson(o.getJSONObject("document")));p.favorite=o.optBoolean("favorite");all.add(p);}catch(Exception ignored){}all.sort((a,b)->a.favorite!=b.favorite?(a.favorite?-1:1):a.name.compareToIgnoreCase(b.name));return all;}
    void deletePreset(Preset p)throws IOException{if(!new File(presets,p.id+".json").delete())throw new IOException("Não foi possível excluir preset");}
    static JSONObject readJson(File f)throws Exception{if(f.length()>4*1024*1024)throw new IOException("Projeto inválido");try(InputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return new JSONObject(out.toString("UTF-8"));}}
    private static void write(File f,JSONObject o)throws IOException{
        AtomicFile atomic=new AtomicFile(f);FileOutputStream out=null;
        try{out=atomic.startWrite();out.write(o.toString().getBytes(StandardCharsets.UTF_8));atomic.finishWrite(out);}catch(IOException e){if(out!=null)atomic.failWrite(out);throw e;}
    }
    static ArrayList<Preset> builtins(){ArrayList<Preset> p=new ArrayList<>();EditorDocument d=new EditorDocument();d.values[3]=.16f;d.values[51]=.16f;d.values[1]=.12f;p.add(new Preset("builtin-clean","Essencial",d));
        d=new EditorDocument();d.values[52]=.18f;d.values[12]=.22f;d.values[54]=.22f;d.values[4]=-.22f;p.add(new Preset("builtin-film","Filme suave",d));
        d=new EditorDocument();d.values[0]=-1;d.values[3]=.25f;d.values[54]=.18f;p.add(new Preset("builtin-mono","Prata",d));
        d=new EditorDocument();d.values[9]=.94f;d.values[10]=.5f;p.add(new Preset("builtin-neon","Nonqual",d));
        d=new EditorDocument();d.asciiEnabled=true;d.asciiDither=true;p.add(new Preset("builtin-ascii","ASCII Nonqual",d));return p;}
}
