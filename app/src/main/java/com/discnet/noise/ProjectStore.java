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
        d=new EditorDocument();d.asciiEnabled=true;d.asciiDither=true;
        p.add(new Preset("builtin-ascii","ASCII Nonqual",d));

        // Original analog-camera inspired looks. Not copies of proprietary
        // filters: every result is produced by Noise!'s own GPU parameters.
        d=new EditorDocument();d.values[3]=-.13f;d.values[12]=.26f;d.values[13]=.24f;
        d.values[54]=.31f;d.values[55]=.56f;d.values[15]=.29f;
        d.values[0]=-.09f;d.values[4]=-.19f;d.values[52]=.14f;
        d.values[65]=0.69f;d.values[66]=0.72f;d.values[67]=0.35f;d.values[68]=0.16f;
        p.add(new Preset("builtin-camera-pocket35","Pocket 35",d));

        d=new EditorDocument();d.values[12]=.28f;d.values[15]=.42f;
        d.values[0]=-.12f;d.values[53]=.06f;d.values[54]=.25f;
        d.values[52]=-.15f;d.values[6]=.17f;d.values[3]=.13f;
        d.values[65]=0.52f;d.values[66]=0.61f;d.values[67]=0.65f;d.values[68]=0.24f;
        p.add(new Preset("builtin-camera-instant","Instantânea",d));

        d=new EditorDocument();d.values[52]=.24f;d.values[13]=.27f;
        d.values[12]=.31f;d.values[4]=-.21f;d.values[54]=.42f;
        d.values[15]=.21f;d.values[0]=-.20f;d.values[3]=-.14f;
        d.values[65]=0.66f;d.values[66]=0.85f;d.values[67]=0.59f;d.values[68]=0.73f;
        p.add(new Preset("builtin-camera-summer98","Verão 98",d));

        d=new EditorDocument();d.values[3]=.25f;d.values[6]=-.20f;
        d.values[54]=.66f;d.values[55]=.38f;d.values[56]=.63f;
        d.values[0]=-.16f;d.values[15]=.31f;d.values[52]=-.06f;
        d.values[65]=0.98f;d.values[66]=0.53f;d.values[67]=0.3f;d.values[68]=0.11f;
        p.add(new Preset("builtin-camera-film400","Filme 400",d));

        d=new EditorDocument();d.values[0]=-.19f;d.values[12]=.32f;
        d.values[3]=.17f;d.values[52]=-.24f;d.values[53]=.18f;
        d.values[54]=.29f;d.values[15]=.44f;d.values[51]=.19f;
        d.colorWheels[3]=-.18f;d.colorWheels[5]=.12f;
        d.values[65]=0.83f;d.values[66]=0.72f;d.values[67]=0.91f;d.values[68]=0.17f;
        p.add(new Preset("builtin-camera-nightflash","Flash Noturno",d));

        d=new EditorDocument();d.values[0]=-.48f;d.values[12]=.46f;
        d.values[51]=.24f;d.values[6]=.26f;d.values[52]=.04f;
        d.values[54]=.20f;d.values[15]=.22f;d.values[3]=-.28f;
        d.values[65]=0.72f;d.values[66]=0.35f;d.values[67]=0.21f;d.values[68]=0.56f;
        p.add(new Preset("builtin-camera-worn","Filme Desbotado",d));

        d=new EditorDocument();d.values[0]=-.86f;d.values[3]=.20f;
        d.values[54]=.44f;d.values[55]=.71f;d.values[12]=.11f;
        d.values[15]=.36f;d.values[6]=-.24f;d.values[4]=-.12f;
        d.values[65]=0.91f;d.values[66]=0.2f;d.values[67]=0.3f;d.values[68]=0.1f;
        p.add(new Preset("builtin-camera-monosilver","Prata 1976",d));

        d=new EditorDocument();d.values[52]=.32f;d.values[53]=.16f;
        d.values[0]=.08f;d.values[12]=.11f;d.values[10]=.18f;
        d.values[57]=.69f;d.values[58]=.12f;d.values[54]=.24f;
        d.values[4]=-.12f;d.values[15]=.19f;
        d.values[65]=0.75f;d.values[66]=0.96f;d.values[67]=0.88f;d.values[68]=0.48f;
        p.add(new Preset("builtin-camera-amber","Negativo Âmbar",d));

        d=new EditorDocument();d.values[0]=-.22f;d.values[52]=-.35f;
        d.values[53]=-.11f;d.values[12]=.14f;d.values[54]=.27f;
        d.values[15]=.29f;d.values[3]=.09f;d.values[5]=-.11f;
        d.colorWheels[1]=.12f;d.colorWheels[5]=-.14f;
        d.values[65]=0.72f;d.values[66]=0.3f;d.values[67]=0.33f;d.values[68]=0.23f;
        p.add(new Preset("builtin-camera-bluehour","Hora Azul",d));

        d=new EditorDocument();d.values[52]=.22f;d.values[13]=.29f;
        d.values[12]=.36f;d.values[54]=.47f;d.values[15]=.56f;
        d.values[3]=-.19f;d.values[6]=.13f;d.values[4]=-.20f;
        d.values[65]=0.97f;d.values[66]=0.54f;d.values[67]=0.5f;d.values[68]=0.9f;
        p.add(new Preset("builtin-camera-toy","Câmera de Brinquedo",d));

        d=new EditorDocument();d.values[3]=.24f;d.values[0]=.08f;
        d.values[12]=.15f;d.values[54]=.21f;d.values[52]=-.15f;
        d.values[51]=-.06f;d.values[15]=.18f;d.values[1]=.08f;
        d.values[65]=0.78f;d.values[66]=0.48f;d.values[67]=0.83f;d.values[68]=0.25f;
        p.add(new Preset("builtin-camera-citynight","Cidade 2002",d));

        d=new EditorDocument();d.values[0]=-.12f;d.values[12]=.35f;
        d.values[54]=.31f;d.values[51]=.18f;d.values[52]=.17f;
        d.values[3]=-.11f;d.values[15]=.29f;d.values[4]=-.25f;
        d.colorWheels[0]=.16f;d.colorWheels[4]=-.18f;
        d.values[65]=0.61f;d.values[66]=0.87f;d.values[67]=0.44f;d.values[68]=0.26f;
        p.add(new Preset("builtin-camera-softprint","Papel Fosco",d));
        // A deeper copper-toned stock inspired by the *character* of rich
        // warm retro photography, not a proprietary preset reproduction.
        d=new EditorDocument();
        d.values[3]=.21f;d.values[4]=-.32f;d.values[5]=-.14f;
        d.values[12]=.21f;d.values[13]=.42f;d.values[15]=.23f;
        d.values[52]=.23f;d.values[53]=.19f;d.values[54]=.52f;
        d.values[55]=.29f;d.values[56]=.73f;
        d.values[65]=.86f;d.values[66]=.98f;d.values[67]=.78f;d.values[68]=.28f;
        d.colorWheels[0]=-.12f;d.colorWheels[1]=.09f;
        d.colorWheels[4]=.26f;d.colorWheels[5]=-.12f;
        p.add(new Preset("builtin-camera-copper-rich","Cobre R · Rich Film",d));
        // Clean retro presets: film tone, halation and light leaks remain.
        // Remove generated grain, dust and film texture for EVERY camera
        // preset, including Cobre R. Individual editors can add grain later.
        for(Preset look:p){
            if(!look.id.startsWith("builtin-camera-")
                &&!look.id.equals("builtin-film")
                &&!look.id.equals("builtin-mono"))continue;
            look.document.values[7]=0f;   // generated noise
            look.document.values[14]=0f;  // dust, flecks and scratches
            look.document.values[54]=0f;  // normal grain
            look.document.values[65]=0f;  // multiscale film grain
        }
        return p;
    }
}
