package com.discnet.noise;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.*;
import android.media.ExifInterface;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Single high-resolution camera JPEG pipeline. Preview pixels are never used for export. */
final class CameraPhotoPipeline {
    static final class Settings {
        float sat=1f,warm=0f,exposure=0f,hue=0f,contrast=1f,sharpness=0f,alignment=0f;
        int orientation,jpegRotation=-1,jpegQuality=98;
        boolean mirror;
        android.graphics.RectF frame=new android.graphics.RectF();
        int viewWidth,viewHeight;
        Settings copy(){
            Settings c=new Settings();
            c.sat=sat;c.warm=warm;c.exposure=exposure;c.hue=hue;
            c.contrast=contrast;c.sharpness=sharpness;c.alignment=alignment;
            c.orientation=orientation;c.jpegRotation=jpegRotation;c.jpegQuality=jpegQuality;
            c.mirror=mirror;c.frame=new RectF(frame);
            c.viewWidth=viewWidth;c.viewHeight=viewHeight;return c;
        }
    }
    static ColorMatrix colors(float saturation,float warmth,float exposure,float hue,float contrast){
        ColorMatrix base=new ColorMatrix();
        base.setSaturation(Math.max(0f,Math.min(2f,saturation)));
        float t=Math.max(-1f,Math.min(1f,warmth));
        base.postConcat(new ColorMatrix(new float[]{
            1+.16f*t,0,0,0,11*t, 0,1+.01f*t,0,0,0, 0,0,1-.18f*t,0,-11*t,
            0,0,0,1,0
        }));
        float radians=(float)Math.toRadians(hue);
        float cos=(float)Math.cos(radians),sin=(float)Math.sin(radians);
        base.postConcat(new ColorMatrix(new float[]{
            .213f+cos*.787f-sin*.213f,.715f-cos*.715f-sin*.715f,.072f-cos*.072f+sin*.928f,0,0,
            .213f-cos*.213f+sin*.143f,.715f+cos*.285f+sin*.140f,.072f-cos*.072f-sin*.283f,0,0,
            .213f-cos*.213f-sin*.787f,.715f-cos*.715f+sin*.715f,.072f+cos*.928f+sin*.072f,0,0,
            0,0,0,1,0
        }));
        float c=Math.max(.5f,Math.min(1.5f,contrast));
        float ev=(float)Math.pow(2,Math.max(-2f,Math.min(2f,exposure)));
        float gain=c*ev;
        float offset=128f*(1-c)*ev;
        base.postConcat(new ColorMatrix(new float[]{
            gain,0,0,0,offset,0,gain,0,0,offset,0,0,gain,0,offset,0,0,0,1,0
        }));
        return base;
    }
    static Bitmap processPhoto(Bitmap source,float sat,float warmth,float exposure,
                               float hue,float contrast,float sharpness){
        Bitmap rendered=Bitmap.createBitmap(source.getWidth(),source.getHeight(),Bitmap.Config.ARGB_8888);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        p.setColorFilter(new ColorMatrixColorFilter(colors(sat,warmth,exposure,hue,contrast)));
        new Canvas(rendered).drawBitmap(source,0,0,p);
        if(sharpness<=0||rendered.getWidth()<3||rendered.getHeight()<3)return rendered;
        Bitmap output=Bitmap.createBitmap(rendered.getWidth(),rendered.getHeight(),Bitmap.Config.ARGB_8888);
        int width=rendered.getWidth(),height=rendered.getHeight();
        int[] prev=new int[width],mid=new int[width],next=new int[width],row=new int[width];
        rendered.getPixels(prev,0,width,0,0,width,1);
        rendered.getPixels(mid,0,width,0,0,width,1);
        float strength=Math.min(.25f,Math.max(0f,sharpness))*.75f;
        for(int y=0;y<height;y++){
            rendered.getPixels(next,0,width,0,Math.min(height-1,y+1),width,1);
            for(int x=0;x<width;x++){
                int center=mid[x],left=mid[Math.max(0,x-1)],right=mid[Math.min(width-1,x+1)],
                    top=prev[x],bottom=next[x];
                int r=clampChannel(channel(center,16)*(1+4*strength)-strength*(channel(left,16)+channel(right,16)+channel(top,16)+channel(bottom,16)));
                int g=clampChannel(channel(center,8)*(1+4*strength)-strength*(channel(left,8)+channel(right,8)+channel(top,8)+channel(bottom,8)));
                int b=clampChannel(channel(center,0)*(1+4*strength)-strength*(channel(left,0)+channel(right,0)+channel(top,0)+channel(bottom,0)));
                row[x]=0xff000000|(r<<16)|(g<<8)|b;
            }
            output.setPixels(row,0,width,0,y,width,1);
            int[] reused=prev;prev=mid;mid=next;next=reused;
        }
        rendered.recycle();
        return output;
    }
    private static int channel(int argb,int shift){return (argb>>>shift)&255;}
    private static int clampChannel(float v){return Math.max(0,Math.min(255,Math.round(v)));}
    static Rect mapOrientedCropToSensor(Rect r,int rawW,int rawH,int degrees){
        switch((degrees+360)%360){
            case 90:return new Rect(r.top,rawH-r.right,r.bottom,rawH-r.left);
            case 180:return new Rect(rawW-r.right,rawH-r.bottom,rawW-r.left,rawH-r.top);
            case 270:return new Rect(rawW-r.bottom,r.left,rawW-r.top,r.right);
            default:return new Rect(r);
        }
    }
    private static int exifDegrees(int orientation){
        if(orientation==ExifInterface.ORIENTATION_ROTATE_90)return 90;
        if(orientation==ExifInterface.ORIENTATION_ROTATE_180)return 180;
        if(orientation==ExifInterface.ORIENTATION_ROTATE_270)return 270;
        return 0;
    }
    static Bitmap rotate(Bitmap bitmap,float angle){
        if(Math.abs(angle)<.01f)return bitmap;
        Matrix m=new Matrix();m.postRotate(angle);
        return Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),m,true);
    }
    static Uri save(Context context,byte[] jpeg,Settings settings)throws Exception{
        if(jpeg==null||jpeg.length==0)throw new IOException("Captura vazia");
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(jpeg,0,jpeg.length,bounds);
        if(bounds.outWidth<1||bounds.outHeight<1)throw new IOException("JPEG inválido");
        ExifInterface exif=new ExifInterface(new ByteArrayInputStream(jpeg));
        int o=exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);
        int degrees=settings.jpegRotation>=0?settings.jpegRotation:exifDegrees(o);
        int orientedW=degrees%180!=0?bounds.outHeight:bounds.outWidth;
        int orientedH=degrees%180!=0?bounds.outWidth:bounds.outHeight;
        Rect desired=CameraFrameGuide.photoCrop(orientedW,orientedH,
            settings.viewWidth,settings.viewHeight,settings.frame,settings.mirror);
        Rect raw=mapOrientedCropToSensor(desired,bounds.outWidth,bounds.outHeight,degrees);
        raw.intersect(0,0,bounds.outWidth,bounds.outHeight);
        if(raw.isEmpty())throw new IOException("Área de recorte inválida");
        Bitmap region=null,turned=null,mirrored=null,aligned=null,processed=null;
        File temp=null;File legacy=null;Uri pending=null;
        try{
            // Decode only the sensor region, preserving every available source pixel.
            BitmapRegionDecoder decoder=BitmapRegionDecoder.newInstance(jpeg,0,jpeg.length,false);
            try{
                BitmapFactory.Options options=new BitmapFactory.Options();
                options.inPreferredConfig=Bitmap.Config.ARGB_8888;
                options.inSampleSize=1;
                region=decoder.decodeRegion(raw,options);
            }finally{decoder.recycle();}
            if(region==null)throw new IOException("Não foi possível decodificar o recorte");
            turned=rotate(region,degrees);
            if(settings.mirror){
                Matrix m=new Matrix();m.setScale(-1f,1f);
                mirrored=Bitmap.createBitmap(turned,0,0,turned.getWidth(),turned.getHeight(),m,true);
            }else mirrored=turned;
            aligned=rotate(mirrored,settings.alignment);
            // App UI remains portrait; only the saved photo rotates with the phone.
            Bitmap deviceOriented=CameraScreen.rotateForDevice(aligned,settings.orientation);
            if(deviceOriented!=aligned){
                if(aligned!=mirrored)aligned.recycle();
                aligned=deviceOriented;
            }
            processed=processPhoto(aligned,settings.sat,settings.warm,settings.exposure,
                settings.hue,settings.contrast,settings.sharpness);
            temp=File.createTempFile("noise-camera-",".jpg",context.getCacheDir());
            try(OutputStream out=new BufferedOutputStream(new FileOutputStream(temp))){
                if(!processed.compress(Bitmap.CompressFormat.JPEG,Math.max(90,Math.min(100,settings.jpegQuality)),out))
                    throw new IOException("Codificação JPEG falhou");
            }
            ExifInterface outputExif=new ExifInterface(temp.getAbsolutePath());
            String[] fields={
                ExifInterface.TAG_DATETIME,ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_MAKE,ExifInterface.TAG_MODEL,
                ExifInterface.TAG_F_NUMBER,ExifInterface.TAG_FOCAL_LENGTH,
                ExifInterface.TAG_EXPOSURE_TIME,ExifInterface.TAG_ISO_SPEED_RATINGS
            };
            for(String field:fields){
                String value=exif.getAttribute(field);
                if(value!=null)outputExif.setAttribute(field,value);
            }
            outputExif.setAttribute(ExifInterface.TAG_ORIENTATION,
                String.valueOf(ExifInterface.ORIENTATION_NORMAL));
            outputExif.saveAttributes();
            String name="Noise-Camera-"+new SimpleDateFormat("yyyyMMdd-HHmmss-SSS",Locale.US).format(new Date())+".jpg";
            Uri uri;
            if(Build.VERSION.SDK_INT>=29){
                ContentValues values=new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME,name);
                values.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Noise!");
                values.put(MediaStore.Images.Media.IS_PENDING,1);
                uri=pending=context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
                if(uri==null)throw new IOException("MediaStore não disponível");
            }else{
                File dir=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),"Noise!");
                if(!dir.exists()&&!dir.mkdirs())throw new IOException("Pasta de fotos indisponível");
                legacy=new File(dir,name);uri=Uri.fromFile(legacy);
            }
            try(InputStream input=new BufferedInputStream(new FileInputStream(temp));
                OutputStream output=legacy!=null?new FileOutputStream(legacy):
                    context.getContentResolver().openOutputStream(uri,"w")){
                if(output==null)throw new IOException("Falha ao gravar");
                byte[] buffer=new byte[65536];int n;
                while((n=input.read(buffer))!=-1)output.write(buffer,0,n);
            }
            if(pending!=null){
                ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.IS_PENDING,0);
                if(context.getContentResolver().update(pending,values,null,null)<1)
                    throw new IOException("Falha ao publicar foto");
            }else MediaScannerConnection.scanFile(context,new String[]{legacy.getAbsolutePath()},
                new String[]{"image/jpeg"},null);
            return uri;
        }catch(Exception|OutOfMemoryError error){
            if(pending!=null)try{context.getContentResolver().delete(pending,null,null);}catch(Exception ignored){}
            if(legacy!=null)legacy.delete();
            throw error;
        }finally{
            if(processed!=null)processed.recycle();
            if(aligned!=null&&aligned!=mirrored)aligned.recycle();
            if(mirrored!=null&&mirrored!=turned)mirrored.recycle();
            if(turned!=null&&turned!=region)turned.recycle();
            if(region!=null)region.recycle();
            if(temp!=null)temp.delete();
        }
    }
}
