package com.discnet.noise.advancedstudio;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Versioned, atomic native documents. Does not touch classic Noise projects. */
public final class StudioProjectStore {
    private static final int MAX_ZIP_BYTES=96*1024*1024;
    private static final long MAX_DECODED_PIXELS=36_000_000;
    private final File folder;
    private final Context context;
    public StudioProjectStore(Context context){
        this.context=context.getApplicationContext();
        folder=new File(this.context.getFilesDir(),"advanced_studio");
        if(!folder.exists())folder.mkdirs();
    }
    private File project(String id)throws IOException{
        if(id==null||!id.matches("[0-9a-fA-F-]{36}"))
            throw new IOException("Identificador de projeto inválido");
        return new File(folder,id+".noise");
    }
    public ArrayList<String> list() {
        ArrayList<String> ids=new ArrayList<>();
        File[] files=folder.listFiles((dir,name)->name.matches("[0-9a-fA-F-]{36}\\.noise"));
        if(files==null)return ids;
        Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        for(File f:files)ids.add(f.getName().substring(0,36));
        return ids;
    }
    public synchronized void save(StudioDocument d)throws Exception{
        File file=project(d.id);
        AtomicFile atomic=new AtomicFile(file);
        FileOutputStream out=null;
        try{
            out=atomic.startWrite();
            ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(out));
            try {
                zip.putNextEntry(new ZipEntry("manifest.json"));
                zip.write(d.toJson().toString().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
                HashSet<String> written=new HashSet<>();
                if(d.selectionPixels!=null){
                    zip.putNextEntry(new ZipEntry("selection.png"));
                    if(!d.selectionPixels.compress(Bitmap.CompressFormat.PNG,100,zip))
                        throw new IOException("Falha ao salvar seleção");
                    zip.closeEntry();
                }
                for(StudioDocument.Layer l:d.layers){
                    if(l.type!=StudioDocument.Type.RASTER&&l.type!=StudioDocument.Type.SMART)continue;
                    if(l.bitmap==null)throw new IOException("Raster sem imagem");
                    String path="layers/"+l.id+".png";
                    if(!written.add(path))throw new IOException("ID de camada duplicado");
                    zip.putNextEntry(new ZipEntry(path));
                    if(l.bitmap!=null&&!l.bitmap.compress(Bitmap.CompressFormat.PNG,100,zip))
                        throw new IOException("Falha ao codificar camada");
                    zip.closeEntry();
                }
                for(StudioDocument.Layer l:d.layers){
                    if(l.mask==null)continue;
                    String path="masks/"+l.id+".png";
                    if(!written.add(path))throw new IOException("Máscara duplicada");
                    zip.putNextEntry(new ZipEntry(path));
                    if(!l.mask.compress(Bitmap.CompressFormat.PNG,100,zip))
                        throw new IOException("Falha ao codificar máscara");
                    zip.closeEntry();
                }
                for(StudioDocument.Layer owner:d.layers){
                    if(owner.type!=StudioDocument.Type.SMART||owner.smartContent==null)continue;
                    StudioDocument child=owner.smartContent;
                    if(child.layers.size()>12)
                        throw new IOException("Objeto inteligente: máximo de 12 camadas internas");
                    for(StudioDocument.Layer inside:child.layers){
                        if(inside.type==StudioDocument.Type.SMART)
                            throw new IOException("Aninhamento inteligente ainda não é suportado");
                        if(inside.type==StudioDocument.Type.RASTER){
                            if(inside.bitmap==null)throw new IOException("Raster interno sem pixels");
                            String name="smart/"+owner.id+"/"+inside.id+".png";
                            zip.putNextEntry(new ZipEntry(name));
                            if(!inside.bitmap.compress(Bitmap.CompressFormat.PNG,100,zip))
                                throw new IOException("Falha ao salvar raster inteligente");
                            zip.closeEntry();
                        }
                        if(inside.mask!=null){
                            String name="smart/"+owner.id+"/masks/"+inside.id+".png";
                            zip.putNextEntry(new ZipEntry(name));
                            if(!inside.mask.compress(Bitmap.CompressFormat.PNG,100,zip))
                                throw new IOException("Falha ao salvar máscara interna");
                            zip.closeEntry();
                        }
                    }
                }
                zip.finish();
            }finally{zip.close();}
            atomic.finishWrite(out);
            d.markSaved();
        }catch(Exception e){
            if(out!=null)atomic.failWrite(out);
            throw e;
        }
    }
    public synchronized StudioDocument load(String id)throws Exception{
        File file=project(id);
        if(!file.isFile()||file.length()>MAX_ZIP_BYTES)
            throw new IOException("Projeto não encontrado ou muito grande");
        try(ZipFile zip=new ZipFile(file)){
            ZipEntry manifest=zip.getEntry("manifest.json");
            if(manifest==null||manifest.getSize()>2*1024*1024)
                throw new IOException("Manifesto inválido");
            JSONObject root;
            try(InputStream in=zip.getInputStream(manifest)){
                root=new JSONObject(new String(readLimited(in,2*1024*1024),StandardCharsets.UTF_8));
            }
            StudioDocument doc=StudioDocument.fromJson(root);
            if(root.optBoolean("pixelSelection")){
                ZipEntry sel=zip.getEntry("selection.png");
                if(sel==null||sel.getSize()>MAX_ZIP_BYTES)
                    throw new IOException("Seleção do documento ausente");
                byte[] bytes;
                try(InputStream in=zip.getInputStream(sel)){
                    bytes=readLimited(in,MAX_ZIP_BYTES);
                }
                BitmapFactory.Options bounds=new BitmapFactory.Options();
                bounds.inJustDecodeBounds=true;
                BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
                if(bounds.outWidth!=doc.width||bounds.outHeight!=doc.height||
                    (long)bounds.outWidth*bounds.outHeight>4_194_304L)
                    throw new IOException("Seleção inválida");
                doc.selectionPixels=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
                if(doc.selectionPixels==null)throw new IOException("Seleção corrompida");
            }
            if(!doc.id.equals(id))throw new IOException("ID incompatível");
            JSONArray layers=root.getJSONArray("layers");
            if(layers.length()>StudioDocument.MAX_LAYERS)throw new IOException("Camadas demais");
            long pixels=0;
            HashSet<String> seen=new HashSet<>();
            for(int i=0;i<layers.length();i++){
                JSONObject item=layers.getJSONObject(i);
                String layerId=item.getString("id");
                if(!layerId.matches("[0-9a-fA-F-]{36}")||!seen.add(layerId))
                    throw new IOException("Camada inválida");
                Bitmap bitmap=null;
                if("RASTER".equals(item.optString("type","RASTER"))||
                    "SMART".equals(item.optString("type","RASTER"))){
                    String relative="layers/"+layerId+".png";
                    if(!relative.equals(item.getString("image")))
                        throw new IOException("Caminho de arquivo proibido");
                    ZipEntry resource=zip.getEntry(relative);
                    if(resource==null||resource.getSize()>MAX_ZIP_BYTES)
                        throw new IOException("Conteúdo da camada ausente");
                    try(InputStream in=zip.getInputStream(resource)){
                    byte[] content=readLimited(in,MAX_ZIP_BYTES);
                    BitmapFactory.Options check=new BitmapFactory.Options();
                    check.inJustDecodeBounds=true;
                    BitmapFactory.decodeByteArray(content,0,content.length,check);
                    if(check.outWidth<1||check.outHeight<1||
                        (long)check.outWidth*check.outHeight>StudioDocument.MAX_PIXELS)
                        throw new IOException("Dimensões de camada inválidas");
                    pixels+=(long)check.outWidth*check.outHeight;
                    if(pixels>MAX_DECODED_PIXELS)
                        throw new IOException("Documento excede orçamento de memória");
                    bitmap=BitmapFactory.decodeByteArray(content,0,content.length);
                    if(bitmap==null)throw new IOException("Imagem da camada inválida");
                }
                }
                StudioDocument.Layer layer=StudioDocument.decodeLayer(item,bitmap);
                if(item.has("mask")){
                    String expected="masks/"+layerId+".png";
                    if(!expected.equals(item.getString("mask")))
                        throw new IOException("Caminho de máscara proibido");
                    ZipEntry resource=zip.getEntry(expected);
                    if(resource==null||resource.getSize()>MAX_ZIP_BYTES)
                        throw new IOException("Máscara ausente");
                    byte[] bytes;
                    try(InputStream in=zip.getInputStream(resource)){
                        bytes=readLimited(in,MAX_ZIP_BYTES);
                    }
                    BitmapFactory.Options bounds=new BitmapFactory.Options();
                    bounds.inJustDecodeBounds=true;
                    BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
                    if(bounds.outWidth!=layer.naturalWidth()||
                       bounds.outHeight!=layer.naturalHeight()||
                       (long)bounds.outWidth*bounds.outHeight>
                           StudioLayerEffects.MAX_EFFECT_PIXELS)
                        throw new IOException("Dimensões de máscara incompatíveis");
                    pixels+=(long)bounds.outWidth*bounds.outHeight;
                    if(pixels>MAX_DECODED_PIXELS)
                        throw new IOException("Orçamento de memória excedido");
                    layer.mask=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
                    if(layer.mask==null)throw new IOException("Máscara corrompida");
                }
                if(layer.type==StudioDocument.Type.SMART&&item.has("smartContent")){
                    JSONObject inner=item.getJSONObject("smartContent");
                    StudioDocument child=StudioDocument.fromJson(inner);
                    JSONArray internals=inner.getJSONArray("layers");
                    if(internals.length()>12||child.width!=layer.naturalWidth()||
                        child.height!=layer.naturalHeight())
                        throw new IOException("Objeto inteligente inválido");
                    for(int k=0;k<internals.length();k++){
                        JSONObject childItem=internals.getJSONObject(k);
                        String childId=childItem.getString("id");
                        if(!childId.matches("[0-9a-fA-F-]{36}"))
                            throw new IOException("ID interno inválido");
                        String childType=childItem.getString("type");
                        if(childType.equals("SMART"))
                            throw new IOException("Objetos aninhados não são suportados");
                        Bitmap childImage=null;
                        if(childType.equals("RASTER")){
                            String key="smart/"+layer.id+"/"+childId+".png";
                            ZipEntry asset=zip.getEntry(key);
                            if(asset==null||asset.getSize()>MAX_ZIP_BYTES)
                                throw new IOException("Pixels internos ausentes");
                            byte[] bytes;
                            try(InputStream in=zip.getInputStream(asset)){
                                bytes=readLimited(in,MAX_ZIP_BYTES);
                            }
                            BitmapFactory.Options bounds=new BitmapFactory.Options();
                            bounds.inJustDecodeBounds=true;
                            BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
                            pixels+=(long)bounds.outWidth*bounds.outHeight;
                            if(bounds.outWidth<1||bounds.outHeight<1||
                                pixels>MAX_DECODED_PIXELS)
                                throw new IOException("Conteúdo interno excede memória");
                            childImage=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
                            if(childImage==null)throw new IOException("Raster interno inválido");
                        }
                        StudioDocument.Layer nested=StudioDocument.decodeLayer(childItem,childImage);
                        if(childItem.has("mask")){
                            String key="smart/"+layer.id+"/masks/"+childId+".png";
                            ZipEntry asset=zip.getEntry(key);
                            if(asset==null)throw new IOException("Máscara interna ausente");
                            try(InputStream in=zip.getInputStream(asset)){
                                byte[] bytes=readLimited(in,MAX_ZIP_BYTES);
                                nested.mask=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
                            }
                            if(nested.mask==null||nested.mask.getWidth()!=nested.naturalWidth()||
                                nested.mask.getHeight()!=nested.naturalHeight())
                                throw new IOException("Máscara interna incompatível");
                        }
                        child.layers.add(nested);
                    }
                    child.markSaved();layer.smartContent=child;
                }
                if((layer.mask!=null||!layer.effects.isEmpty()||layer.shadowEnabled)
                    &&(long)layer.naturalWidth()*layer.naturalHeight()>
                        StudioLayerEffects.MAX_EFFECT_PIXELS)
                    throw new IOException("Camada com efeitos maior que limite de 2 MP");
                doc.layers.add(layer);
            }
            doc.markSaved();return doc;
        }
    }
    private static byte[] readLimited(InputStream in,int limit)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        byte[] buffer=new byte[16384];int n,total=0;
        while((n=in.read(buffer))>=0){
            total+=n;if(total>limit)throw new IOException("Arquivo excede tamanho permitido");
            bytes.write(buffer,0,n);
        }
        return bytes.toByteArray();
    }
    public Uri exportPng(StudioDocument doc)throws Exception{
        Bitmap merged=doc.composite();
        Uri target=null;
        try {
            if(Build.VERSION.SDK_INT<29)
                throw new IOException("Exportação pública requer Android 10+ nesta fase");
            ContentValues values=new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME,
                "Noise-Studio-"+System.currentTimeMillis()+".png");
            values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Noise!");
            values.put(MediaStore.Images.Media.IS_PENDING,1);
            target=context.getContentResolver().insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
            if(target==null)throw new IOException("Não foi possível abrir o MediaStore");
            try(OutputStream out=context.getContentResolver().openOutputStream(target,"w")){
                if(out==null||!merged.compress(Bitmap.CompressFormat.PNG,100,out))
                    throw new IOException("Não foi possível exportar PNG");
            }
            ContentValues ready=new ContentValues();
            ready.put(MediaStore.Images.Media.IS_PENDING,0);
            if(context.getContentResolver().update(target,ready,null,null)<1)
                throw new IOException("Não foi possível publicar a imagem");
            return target;
        }catch(Exception e){
            if(target!=null)context.getContentResolver().delete(target,null,null);
            throw e;
        }finally{merged.recycle();}
    }
}
