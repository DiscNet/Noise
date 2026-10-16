package com.discnet.noise;

import android.content.*;
import android.graphics.*;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore.Images;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Background export with transactional MediaStore publication and cleanup. */
final class ExportManager {
    static Uri save(Context c,Bitmap source,EditorDocument d,int sourceWidth,int sourceHeight,int max,Uri supplied)throws Exception{
        int[] size=d.outputSize(sourceWidth,sourceHeight,max);Bitmap output=null;Uri inserted=null;File legacy=null;
        String format=d.exportFormat;
        if(!format.equals("webp")&&!format.equals("jpeg")&&!format.equals("png"))
            format=d.jpeg?"jpeg":"png";
        boolean jpeg="jpeg".equals(format);
        String type=jpeg?"image/jpeg":format.equals("webp")?"image/webp":"image/png";
        Bitmap.CompressFormat encoder=jpeg?Bitmap.CompressFormat.JPEG:
            format.equals("webp")?(Build.VERSION.SDK_INT>=30?
                Bitmap.CompressFormat.WEBP_LOSSY:Bitmap.CompressFormat.WEBP):
                Bitmap.CompressFormat.PNG;
        try{
            output=GpuExporter.render(c,source,d.gpu(false),size[0],size[1],sourceWidth,sourceHeight);
            if(jpeg){Bitmap flat=Bitmap.createBitmap(size[0],size[1],Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(flat);canvas.drawColor(Color.WHITE);canvas.drawBitmap(output,0,0,null);output.recycle();output=flat;}
            String name="Noise-"+new SimpleDateFormat("yyyyMMdd-HHmmss-SSS",Locale.US).format(new Date())+(jpeg?".jpg":format.equals("webp")?".webp":".png");
            Uri uri=supplied;
            if(uri==null&&Build.VERSION.SDK_INT>=29){ContentValues v=new ContentValues();v.put(Images.Media.DISPLAY_NAME,name);v.put(Images.Media.MIME_TYPE,type);v.put(Images.Media.RELATIVE_PATH,"Pictures/Noise!");v.put(Images.Media.IS_PENDING,1);uri=inserted=c.getContentResolver().insert(Images.Media.EXTERNAL_CONTENT_URI,v);if(uri==null)throw new IOException("Armazenamento indisponível");}
            else if(uri==null){File dir=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),"Noise!");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Não foi possível criar Pictures/Noise!");legacy=new File(dir,name);}
            try(OutputStream out=legacy!=null?new FileOutputStream(legacy):c.getContentResolver().openOutputStream(uri,"w")){
                if(out==null||!output.compress(encoder,format.equals("png")?100:d.jpegQuality,out))throw new IOException("Falha ao codificar imagem");
            }
            if(inserted!=null){ContentValues v=new ContentValues();v.put(Images.Media.IS_PENDING,0);if(c.getContentResolver().update(inserted,v,null,null)<1)throw new IOException("Falha ao concluir exportação");}
            if(legacy!=null){CountDownLatch done=new CountDownLatch(1);Uri[] scanned=new Uri[1];MediaScannerConnection.scanFile(c,new String[]{legacy.getPath()},new String[]{type},(path,result)->{scanned[0]=result;done.countDown();});done.await(5,TimeUnit.SECONDS);uri=scanned[0];}
            return uri;
        }catch(Exception|OutOfMemoryError e){if(inserted!=null)try{c.getContentResolver().delete(inserted,null,null);}catch(Exception ignored){}if(legacy!=null)legacy.delete();throw e;}
        finally{if(output!=null)output.recycle();}
    }
    private ExportManager(){}
}
