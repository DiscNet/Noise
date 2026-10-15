package com.discnet.noise;

import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import org.json.JSONObject;
import java.io.*;
import java.util.*;

/**
 * User-authorized Storage Access Framework project vault.
 * Files live in the chosen shared folder, outside app-private storage.
 * Android clears grants on uninstall; users select the same folder again
 * to recover projects. Never assume app-specific storage survives uninstall.
 */
final class ProjectVault {
    private static final String PREFS="noise_projects_external";
    private static final String TREE="vault_tree";
    private static final String FOLDER="Noise! Projects";
    private static final String DIRECTORY=DocumentsContract.Document.MIME_TYPE_DIR;
    private static final String ID_PREFIX="Noise-";
    private final Context context;
    private final ContentResolver resolver;
    ProjectVault(Context c){context=c.getApplicationContext();resolver=context.getContentResolver();}
    boolean attached(){return tree()!=null;}
    private Uri tree(){
        String encoded=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .getString(TREE,null);
        if(encoded==null)return null;
        Uri uri=Uri.parse(encoded);
        for(android.content.UriPermission p:resolver.getPersistedUriPermissions())
            if(p.isReadPermission()&&p.isWritePermission()&&p.getUri().equals(uri))return uri;
        return null;
    }
    void attach(Uri uri)throws Exception{
        resolver.takePersistableUriPermission(uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putString(TREE,uri.toString()).apply();
        if(vaultRoot()==null)throw new IOException("Pasta de projetos não acessível");
    }
    private Uri vaultRoot()throws Exception{
        Uri selected=tree();if(selected==null)return null;
        String id=DocumentsContract.getTreeDocumentId(selected);
        Uri root=DocumentsContract.buildDocumentUriUsingTree(selected,id);
        Uri projects=find(root,FOLDER);
        return projects!=null?projects:DocumentsContract.createDocument(resolver,root,DIRECTORY,FOLDER);
    }
    private Uri find(Uri folder,String filename)throws Exception{
        Uri selected=tree();if(selected==null)return null;
        String id=DocumentsContract.getDocumentId(folder);
        Uri list=DocumentsContract.buildChildDocumentsUriUsingTree(selected,id);
        try(Cursor cursor=resolver.query(list,new String[]{
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){
            if(cursor==null)return null;
            while(cursor.moveToNext())if(filename.equals(cursor.getString(1)))
                return DocumentsContract.buildDocumentUriUsingTree(selected,cursor.getString(0));
        }
        return null;
    }
    private Uri make(Uri folder,String name,String mime)throws Exception{
        Uri current=find(folder,name);
        if(current!=null)return current;
        Uri added=DocumentsContract.createDocument(resolver,folder,mime,name);
        if(added==null)throw new IOException("Falha ao criar "+name);
        return added;
    }
    private static String fileName(String id){return ID_PREFIX+id;}
    static boolean validId(String id){return id!=null&&id.matches("[a-f0-9-]{36}");}
    private static void copy(InputStream in,OutputStream out,long max)throws IOException{
        if(in==null||out==null)throw new IOException("Não foi possível abrir o arquivo");
        byte[] buf=new byte[65536];long total=0;int count;
        while((count=in.read(buf))!=-1){
            if(Thread.currentThread().isInterrupted())throw new IOException("Operação interrompida");
            total+=count;
            if(total>max)throw new IOException("Arquivo excede limite seguro");
            out.write(buf,0,count);
        }
        out.flush();
    }
    private void upload(File source,Uri target,long max)throws Exception{
        try(InputStream in=new FileInputStream(source);
            OutputStream out=resolver.openOutputStream(target,"wt")){
            copy(in,out,max);
        }
    }
    private void download(Uri source,File target,long max)throws Exception{
        File pending=new File(target.getParentFile(),target.getName()+".part");
        try(InputStream in=resolver.openInputStream(source);
            FileOutputStream out=new FileOutputStream(pending)){
            copy(in,out,max);out.getFD().sync();
        }catch(Exception e){pending.delete();throw e;}
        if(!pending.renameTo(target)){pending.delete();throw new IOException("Falha ao restaurar original");}
    }
    void backup(ProjectStore store,String id)throws Exception{
        if(!validId(id))throw new IOException("ID de projeto inválido");
        Uri root=vaultRoot();if(root==null)return;
        ProjectStore.Project project=store.read(id);
        Uri directory=make(root,fileName(id),DIRECTORY);
        Uri photo=find(directory,"original");
        if(photo==null){
            photo=make(directory,"original","application/octet-stream");
            upload(project.source,photo,256L*1024*1024);
        }
        // JSON is stored after the original so a complete record is never
        // advertised until the full image has been written.
        File metadata=new File(project.source.getParentFile(),"project.json");
        Uri json=make(directory,"project.json","application/json");
        upload(metadata,json,4L*1024*1024);
    }
    int recover(ProjectStore store)throws Exception{
        Uri root=vaultRoot();if(root==null)return 0;
        Uri selected=tree();
        Uri list=DocumentsContract.buildChildDocumentsUriUsingTree(selected,
            DocumentsContract.getDocumentId(root));
        int count=0;
        try(Cursor cursor=resolver.query(list,new String[]{
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE},null,null,null)){
            if(cursor==null)return 0;
            while(cursor.moveToNext()){
                String folderName=cursor.getString(1),mime=cursor.getString(2);
                if(!DIRECTORY.equals(mime)||folderName==null
                    ||!folderName.startsWith(ID_PREFIX))continue;
                String id=folderName.substring(ID_PREFIX.length());
                if(!validId(id))continue;
                try{
                    store.read(id);
                    continue; // never overwrite newer local edits
                }catch(Exception ignored){}
                Uri folder=DocumentsContract.buildDocumentUriUsingTree(selected,cursor.getString(0));
                Uri image=find(folder,"original"),meta=find(folder,"project.json");
                if(image==null||meta==null)continue;
                File dir=store.directory(id);
                File source=new File(dir,"original"),json=new File(dir,"project.json");
                if(source.exists()&&!json.exists()){ // possibly interrupted local save
                    source.delete();
                }
                try{
                    download(image,source,256L*1024*1024);
                    download(meta,json,4L*1024*1024);
                    store.read(id); // validates document schema and image presence
                    count++;
                }catch(Exception error){
                    source.delete();json.delete();
                }
            }
        }
        return count;
    }
    int backupAll(ProjectStore store)throws Exception{
        if(!attached())return 0;
        int count=0;
        for(ProjectStore.Project p:store.list()){backup(store,p.id);count++;}
        return count;
    }
    void delete(String id)throws Exception{
        Uri root=vaultRoot();
        if(root==null)return;
        Uri folder=find(root,fileName(id));
        if(folder!=null&&!DocumentsContract.deleteDocument(resolver,folder))
            throw new IOException("Não foi possível excluir o projeto na pasta compartilhada");
    }
}
