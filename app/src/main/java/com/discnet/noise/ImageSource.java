package com.discnet.noise;

import android.graphics.*;
import android.media.ExifInterface;
import android.os.Build;
import java.io.*;

/** Decode off the UI thread; retain the original file for independent export. */
final class ImageSource {
    final File file;final int width,height;final Bitmap preview;
    private ImageSource(File f,int w,int h,Bitmap b){file=f;width=w;height=h;preview=b;}
    static ImageSource open(File file,int previewLimit)throws IOException{
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),bounds);
        if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IOException("Formato de imagem inválido ou não suportado");
        int w=bounds.outWidth,h=bounds.outHeight;int orientation=orientation(file);
        if(orientation>=5&&orientation<=8){int t=w;w=h;h=t;}
        Bitmap preview=decode(file,Math.min(previewLimit,1600));return new ImageSource(file,w,h,preview);
    }
    static Bitmap decode(File file,int limit)throws IOException{
        if(Build.VERSION.SDK_INT>=28)return ImageDecoder.decodeBitmap(ImageDecoder.createSource(file),(decoder,info,source)->{
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
            int max=Math.max(info.getSize().getWidth(),info.getSize().getHeight());if(max>limit){float f=(float)limit/max;decoder.setTargetSize(Math.max(1,Math.round(info.getSize().getWidth()*f)),Math.max(1,Math.round(info.getSize().getHeight()*f)));}
        });
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),o);o.inJustDecodeBounds=false;o.inSampleSize=1;o.inPreferredConfig=Bitmap.Config.ARGB_8888;
        while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>limit)o.inSampleSize*=2;
        Bitmap b=BitmapFactory.decodeFile(file.getPath(),o);if(b==null)throw new IOException("Imagem inválida");
        Matrix m=new Matrix();switch(orientation(file)){case 2:m.setScale(-1,1);break;case 3:m.setRotate(180);break;case 4:m.setScale(1,-1);break;case 5:m.setRotate(90);m.postScale(-1,1);break;case 6:m.setRotate(90);break;case 7:m.setRotate(-90);m.postScale(-1,1);break;case 8:m.setRotate(-90);break;}
        if(!m.isIdentity()){Bitmap r=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),m,true);if(r!=b)b.recycle();b=r;}return b;
    }
    private static int orientation(File f){try{return new ExifInterface(f).getAttributeInt(ExifInterface.TAG_ORIENTATION,1);}catch(IOException e){return 1;}}
}
