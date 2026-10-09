package com.discnet.noise;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends Activity {
    private final String[] names={"Saturação","Vibração","Exposição","Contraste","Highlights","Branco","Preto","Ruído","Matiz"};
    private final float[] values=new float[9];
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicInteger generation=new AtomicInteger();
    private Bitmap original,preview,rendered;
    private ImageView image;
    private TextView status;
    private Button save;
    private SeekBar[] sliders=new SeekBar[9];
    private boolean comparing=false,exporting=false;
    private Uri input;
    private int color=Color.rgb(196,255,105);
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.WHITE);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextColor(color);return b;}
    @Override public void onCreate(Bundle state){super.onCreate(state);
        getWindow().setStatusBarColor(0xff15151d);getWindow().setNavigationBarColor(0xff15151d);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(16),dp(12),dp(16),dp(8));root.setBackgroundColor(0xff15151d);setContentView(root);
        TextView title=text("Noise!",32);title.setTextColor(color);root.addView(title);
        status=text("Sua imagem. Seu estilo.",13);root.addView(status);
        image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setContentDescription("Prévia da imagem editada");root.addView(image,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout actions=new LinearLayout(this);root.addView(actions);
        Button open=button("Abrir");save=button("Salvar PNG");Button reset=button("Zerar");
        actions.addView(open,new LinearLayout.LayoutParams(0,-2,1));actions.addView(save,new LinearLayout.LayoutParams(0,-2,1));actions.addView(reset,new LinearLayout.LayoutParams(0,-2,1));save.setEnabled(false);
        open.setOnClickListener(v->{if(exporting)return;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,1);});
        save.setOnClickListener(v->{if(original==null||exporting)return;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("image/png");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"Noise-"+System.currentTimeMillis()+".png");startActivityForResult(i,2);});
        reset.setOnClickListener(v->{for(SeekBar s:sliders)s.setProgress(100);schedule();});
        Button compare=button("Segure para ver o original");root.addView(compare);
        compare.setOnTouchListener((v,e)->{if(preview==null)return false;if(e.getAction()==MotionEvent.ACTION_DOWN){comparing=true;image.setImageBitmap(preview);}else if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){comparing=false;image.setImageBitmap(rendered==null?preview:rendered);if(e.getAction()==MotionEvent.ACTION_UP)v.performClick();}return true;});
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,dp(235)));LinearLayout controls=new LinearLayout(this);controls.setOrientation(1);scroll.addView(controls);
        for(int j=0;j<9;j++){final int n=j;TextView label=text(names[j]+"  0",14);controls.addView(label);SeekBar s=new SeekBar(this);sliders[j]=s;s.setMax(200);s.setProgress(100);s.setContentDescription(names[j]);s.setProgressTintList(android.content.res.ColorStateList.valueOf(color));s.setThumbTintList(android.content.res.ColorStateList.valueOf(color));controls.addView(s);
            s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean u){values[n]=(p-100)/100f;label.setText(names[n]+"  "+(p-100)+(n==7?"  (− suaviza / + granula)":""));schedule();}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
        }
        if(state!=null){float[] a=state.getFloatArray("values");if(a!=null)for(int j=0;j<9;j++)sliders[j].setProgress(Math.round(a[j]*100)+100);String uri=state.getString("input");if(uri!=null)load(Uri.parse(uri));}
    }
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putFloatArray("values",values.clone());if(input!=null)b.putString("input",input.toString());}
    private void load(Uri uri){int token=generation.incrementAndGet();status.setText("Abrindo imagem…");save.setEnabled(false);worker.execute(()->{try{
        // API 28 decoder applies EXIF orientation; older Android uses the fallback below.
        Bitmap bitmap;
        if(android.os.Build.VERSION.SDK_INT>=28) bitmap=decodeModern(uri);
        else {BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;try(InputStream s=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(s,null,o);}o.inJustDecodeBounds=false;o.inSampleSize=1;while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>4096){o.inSampleSize=o.inSampleSize==0?2:o.inSampleSize*2;}try(InputStream s=getContentResolver().openInputStream(uri)){bitmap=BitmapFactory.decodeStream(s,null,o);}}
        if(bitmap==null)throw new IOException("Formato não suportado");float scale=Math.min(1,900f/Math.max(bitmap.getWidth(),bitmap.getHeight()));Bitmap small=Bitmap.createScaledBitmap(bitmap,Math.max(1,Math.round(bitmap.getWidth()*scale)),Math.max(1,Math.round(bitmap.getHeight()*scale)),true);
        runOnUiThread(()->{if(token!=generation.get())return;input=uri;original=bitmap;preview=small;rendered=null;image.setImageBitmap(small);save.setEnabled(true);schedule();});
    }catch(Exception|OutOfMemoryError e){runOnUiThread(()->{status.setText("Não foi possível abrir esta imagem.");save.setEnabled(original!=null);});}});}
    @android.annotation.TargetApi(28) private Bitmap decodeModern(Uri uri)throws IOException{return ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(),uri),(d,i,s)->{d.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);d.setMutableRequired(true);int max=Math.max(i.getSize().getWidth(),i.getSize().getHeight());if(max>4096){float k=4096f/max;d.setTargetSize(Math.round(i.getSize().getWidth()*k),Math.round(i.getSize().getHeight()*k));}});}
    private Bitmap edit(Bitmap b,float[] a){int w=b.getWidth(),h=b.getHeight();int[] p=new int[w*h];b.getPixels(p,0,w,0,0,w,h);return Bitmap.createBitmap(Adjustments.apply(p,w,h,a),w,h,Bitmap.Config.ARGB_8888);}
    private void schedule(){if(preview==null)return;int token=generation.incrementAndGet();Bitmap b=preview;float[] a=values.clone();worker.execute(()->{if(token!=generation.get())return;try{Bitmap result=edit(b,a);runOnUiThread(()->{if(token!=generation.get()){result.recycle();return;}rendered=result;if(!comparing)image.setImageBitmap(result);if(!exporting)status.setText("Prévia ao vivo • "+original.getWidth()+" × "+original.getHeight());});}catch(OutOfMemoryError e){runOnUiThread(()->status.setText("Memória insuficiente para a prévia."));}});}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();if(request==1){try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}load(uri);}else if(request==2&&original!=null){Bitmap b=original;float[] a=values.clone();exporting=true;save.setEnabled(false);status.setText("Exportando PNG…");worker.execute(()->{try{Bitmap out=edit(b,a);try(OutputStream s=getContentResolver().openOutputStream(uri,"wt")){if(s==null||!out.compress(Bitmap.CompressFormat.PNG,100,s))throw new IOException();}out.recycle();runOnUiThread(()->{exporting=false;save.setEnabled(true);status.setText("Imagem salva!");Toast.makeText(this,"PNG salvo com sucesso",Toast.LENGTH_LONG).show();});}catch(Exception|OutOfMemoryError e){runOnUiThread(()->{exporting=false;save.setEnabled(true);status.setText("Falha ao salvar. Tente outra pasta ou imagem menor.");});}});}}
    @Override protected void onDestroy(){generation.incrementAndGet();worker.shutdownNow();super.onDestroy();}
}
