package com.discnet.noise;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Camera;
import android.media.ExifInterface;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Camera-first Noise workspace. Preview and saved JPEG use the same ColorMatrix,
 * applied to a hardware TextureView layer and to full-resolution pixels.
 * No third-party camera SDK, network access, or per-frame Bitmap allocations.
 */
@SuppressWarnings("deprecation")
final class CameraScreen extends FrameLayout implements TextureView.SurfaceTextureListener {
    interface Listener {
        void close();
        void edit(Uri photo);
    }

    private final Activity activity;
    private final Listener listener;
    private final TextureView preview;
    private final CameraFrameGuide frameGuide;
    private final LinearLayout cameraBody;
    private final FrameLayout imageArea;
    private final ScrollView controlsScroll;
    private EditorTheme.Slider zoomBar;
    private TextView zoomValue;
    private final ArrayList<TextView> ratioButtons=new ArrayList<>();
    private final TextView status, shutter, edit, switchLens;
    private final ExecutorService photos = Executors.newSingleThreadExecutor();
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private Camera camera;
    private int cameraId=-1;
    private int preferredFacing=Camera.CameraInfo.CAMERA_FACING_BACK;
    private volatile int generation=0;
    private volatile boolean active,disposed,cameraReady;
    private boolean capturing;
    private Uri latestPhoto;
    private float saturation=1f, temperature=0f, exposure=0f, digitalZoom=1f;
    private volatile int previewBufferWidth,previewBufferHeight,previewDisplayOrientation;

    CameraScreen(Activity activity, Listener listener) {
        super(activity);
        this.activity=activity;
        this.listener=listener;
        setBackgroundColor(0xff0c0b10);
        setFocusableInTouchMode(true);

        LinearLayout page=new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        addView(page,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout header=new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16),dp(5),dp(16),dp(5));
        page.addView(header,new LinearLayout.LayoutParams(-1,dp(62)));
        TextView close=action("Fechar",false);
        header.addView(close,new LinearLayout.LayoutParams(dp(82),dp(44)));
        close.setOnClickListener(v->listener.close());
        TextView title=label("Câmera",20,EditorTheme.INK);
        title.setTypeface(NoiseTypography.bold(activity));title.setGravity(Gravity.CENTER);
        header.addView(title,new LinearLayout.LayoutParams(0,-1,1));
        switchLens=action("Virar",false);
        header.addView(switchLens,new LinearLayout.LayoutParams(dp(82),dp(44)));
        switchLens.setOnClickListener(v->switchCamera());

        cameraBody=new LinearLayout(activity);
        cameraBody.setOrientation(LinearLayout.VERTICAL);
        page.addView(cameraBody,new LinearLayout.LayoutParams(-1,0,1));
        imageArea=new FrameLayout(activity);
        imageArea.setBackgroundColor(Color.BLACK);
        cameraBody.addView(imageArea,new LinearLayout.LayoutParams(-1,0,1));
        preview=new TextureView(activity);
        preview.setSurfaceTextureListener(this);
        // TextureView's hardware layer paint is composited by the GPU.
        // It changes the displayed camera frame without copying pixels to the CPU.
        preview.setLayerType(View.LAYER_TYPE_HARDWARE,null);
        imageArea.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        frameGuide=new CameraFrameGuide(activity,new CameraFrameGuide.Gestures(){
            @Override public void focus(float x,float y){tapToFocus(x,y);}
            @Override public void zoom(float factor){setZoom(digitalZoom*factor);}
        });
        imageArea.addView(frameGuide,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout ratios=new LinearLayout(activity);
        ratios.setPadding(dp(12),dp(12),dp(12),dp(8));
        ratios.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams ratioParams=new FrameLayout.LayoutParams(-1,dp(60),Gravity.TOP);
        imageArea.addView(ratios,ratioParams);
        addRatio(ratios,"1:1",1f);
        addRatio(ratios,"4:3",4f/3f);
        addRatio(ratios,"16:9",16f/9f);
        setRatio(1f);
        status=label("Conectando à câmera…",13,0xffded8e6);
        status.setGravity(Gravity.CENTER);
        status.setBackground(EditorTheme.panel(activity,0xd01b1721,0,12,0));
        FrameLayout.LayoutParams msg=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        msg.setMargins(dp(14),0,dp(14),dp(12));
        status.setPadding(dp(12),dp(10),dp(12),dp(10));
        imageArea.addView(status,msg);

        controlsScroll=new ScrollView(activity);
        controlsScroll.setFillViewport(false);
        controlsScroll.setVerticalScrollBarEnabled(false);
        cameraBody.addView(controlsScroll,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout controls=new LinearLayout(activity);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(18),dp(12),dp(18),dp(12));
        controls.setBackgroundColor(0xff17161d);
        controlsScroll.addView(controls,new ScrollView.LayoutParams(-1,-2));

        addSlider(controls,"Saturação","0%","200%",100, progress->{
            saturation=progress/100f;applyLiveAdjustments();
        });
        addSlider(controls,"Temperatura","Frio","Quente",100, progress->{
            temperature=(progress-100)/100f;applyLiveAdjustments();
        });
        addSlider(controls,"Exposição","−2 EV","+2 EV",100, progress->{
            exposure=(progress-100)/50f;applyLiveAdjustments();
        });
        LinearLayout zoomRow=new LinearLayout(activity);zoomRow.setGravity(Gravity.CENTER_VERTICAL);
        controls.addView(zoomRow,new LinearLayout.LayoutParams(-1,dp(26)));
        TextView zoomTitle=label("Zoom / enquadramento",13,EditorTheme.INK);
        zoomRow.addView(zoomTitle,new LinearLayout.LayoutParams(0,-2,1));
        zoomValue=label("1.0×",13,EditorTheme.LIME);
        zoomValue.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        zoomRow.addView(zoomValue,new LinearLayout.LayoutParams(dp(65),-2));
        zoomBar=new EditorTheme.Slider(activity,EditorTheme.LIME);
        zoomBar.setMax(300);zoomBar.setProgress(0);zoomBar.neutral(0);
        zoomBar.setContentDescription("Zoom digital de 1 a 4 vezes. A moldura indica a foto final");
        controls.addView(zoomBar,new LinearLayout.LayoutParams(-1,dp(36)));
        zoomBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int progress,boolean user){
                setZoom(1f+progress/100f);
            }
            @Override public void onStartTrackingTouch(SeekBar bar){}
            @Override public void onStopTrackingTouch(SeekBar bar){}
        });

        LinearLayout bottom=new LinearLayout(activity);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(dp(14),dp(4),dp(14),dp(4));
        page.addView(bottom,new LinearLayout.LayoutParams(-1,dp(78)));
        TextView reset=action("Zerar",false);
        bottom.addView(reset,new LinearLayout.LayoutParams(dp(78),dp(48)));
        reset.setOnClickListener(v->resetAdjustments());
        shutter=action("●  Capturar",true);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(54),1);sp.setMargins(dp(8),0,dp(8),0);
        bottom.addView(shutter,sp);
        shutter.setContentDescription("Capturar foto com os ajustes ao vivo");
        shutter.setOnClickListener(v->takePhoto());
        edit=action("Editar",false);
        edit.setEnabled(false);edit.setAlpha(.40f);
        bottom.addView(edit,new LinearLayout.LayoutParams(dp(78),dp(48)));
        edit.setOnClickListener(v->{if(latestPhoto!=null)listener.edit(latestPhoto);});
        applyLiveAdjustments();
        post(this::updateCameraLayout);
    }

    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){
        super.onSizeChanged(w,h,oldW,oldH);
        post(this::updateCameraLayout);
    }
    /** A side-panel keeps the live viewfinder visible on landscape phones. */
    private void updateCameraLayout(){
        if(disposed||getWidth()<=0||getHeight()<=0)return;
        boolean landscape=getWidth()>getHeight();
        int desiredOrientation=landscape?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL;
        if(cameraBody.getOrientation()!=desiredOrientation)
            cameraBody.setOrientation(desiredOrientation);
        if(landscape){
            imageArea.setLayoutParams(new LinearLayout.LayoutParams(0,-1,1));
            controlsScroll.setLayoutParams(new LinearLayout.LayoutParams(
                Math.min(dp(255),Math.round(getWidth()*.43f)),-1));
        }else{
            imageArea.setLayoutParams(new LinearLayout.LayoutParams(-1,0,1));
            controlsScroll.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));
        }
    }

    private int dp(float d){return EditorTheme.dp(activity,d);}
    private TextView label(String value,int size,int color){
        TextView t=new TextView(activity);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setTypeface(NoiseTypography.regular(activity));return t;
    }
    private TextView action(String value,boolean primary){
        TextView t=label(value,14,primary?0xff18121d:EditorTheme.INK);
        t.setGravity(Gravity.CENTER);t.setTypeface(NoiseTypography.bold(activity));
        EditorTheme.button(t,primary);t.setFocusable(true);return t;
    }

    private final ArrayList<SeekBar> bars=new ArrayList<>();
    private interface SliderListener {void change(int progress);}
    private void addSlider(LinearLayout parent,String name,String minimum,String maximum,int neutral,SliderListener listener) {
        LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);
        parent.addView(row,new LinearLayout.LayoutParams(-1,dp(27)));
        TextView title=label(name,13,EditorTheme.INK);
        row.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView value=label(name.equals("Saturação")?"100%":name.equals("Temperatura")?"Neutro":"0.00 EV",12,EditorTheme.MUTED);
        value.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        row.addView(value,new LinearLayout.LayoutParams(dp(94),-2));
        EditorTheme.Slider bar=new EditorTheme.Slider(activity,EditorTheme.LIME);
        bar.setMax(200);bar.setProgress(neutral);bar.neutral(100);
        bar.setContentDescription("Ajustar "+name);
        parent.addView(bar,new LinearLayout.LayoutParams(-1,dp(36)));
        bars.add(bar);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar b,int progress,boolean fromUser){
                if(name.equals("Saturação"))value.setText(progress+"%");
                else if(name.equals("Temperatura"))value.setText(progress==100?"Neutro":
                    (progress<100?"Frio ":"Quente ")+Math.abs(progress-100)+"%");
                else value.setText(String.format(Locale.getDefault(),"%+.2f EV",(progress-100)/50f));
                listener.change(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar b){}
            @Override public void onStopTrackingTouch(SeekBar b){}
        });
    }
    private void resetAdjustments(){for(SeekBar b:bars)b.setProgress(100);setZoom(1f);}
    private void addRatio(LinearLayout row,String text,float ratio){
        TextView chip=action(text,false);
        chip.setTag(ratio);
        chip.setContentDescription("Proporção da fotografia "+text);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(40),1);
        lp.setMargins(dp(3),0,dp(3),0);
        row.addView(chip,lp);ratioButtons.add(chip);
        chip.setOnClickListener(v->setRatio(ratio));
    }
    private void setRatio(float ratio){
        frameGuide.ratio(ratio);
        for(TextView chip:ratioButtons){
            boolean selected=Math.abs((Float)chip.getTag()-ratio)<.01f;
            chip.setTextColor(selected?0xff18121d:EditorTheme.INK);
            EditorTheme.button(chip,selected);
        }
    }
    private void setZoom(float value){
        digitalZoom=Math.max(1f,Math.min(4f,value));
        frameGuide.zoom(digitalZoom);
        if(zoomValue!=null)zoomValue.setText(String.format(Locale.getDefault(),"%.1f×",digitalZoom));
        if(zoomBar!=null){
            int p=Math.round((digitalZoom-1f)*100);
            if(zoomBar.getProgress()!=p)zoomBar.setProgress(p);
        }
        if(previewBufferWidth>0&&previewBufferHeight>0)
            fitPreview(previewBufferWidth,previewBufferHeight,previewDisplayOrientation);
    }
    static ColorMatrix colorAdjustments(float saturation,float temperature,float exposure){
        // Common transform shared by preview and captured JPEG.
        float s=Math.max(0f,Math.min(2f,saturation));
        float warmth=Math.max(-1f,Math.min(1f,temperature));
        float gain=(float)Math.pow(2d,Math.max(-2f,Math.min(2f,exposure)));
        ColorMatrix filter=new ColorMatrix();filter.setSaturation(s);
        ColorMatrix tint=new ColorMatrix(new float[]{
            1f+.16f*warmth,0,0,0,11f*warmth,
            0,1f+.01f*warmth,0,0,0,
            0,0,1f-.18f*warmth,0,-11f*warmth,
            0,0,0,1,0
        });
        filter.postConcat(tint);
        ColorMatrix brightness=new ColorMatrix(new float[]{
            gain,0,0,0,0,0,gain,0,0,0,0,0,gain,0,0,0,0,0,1,0
        });
        filter.postConcat(brightness);
        return filter;
    }
    private void applyLiveAdjustments(){
        if(preview==null)return;
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        paint.setColorFilter(new ColorMatrixColorFilter(colorAdjustments(saturation,temperature,exposure)));
        preview.setLayerPaint(paint);
        preview.invalidate();
    }
    private void message(String value){if(!disposed)status.setText(value);}
    void start(){
        if(disposed)return;
        active=true;
        if(activity.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            message("Autorize o uso da câmera para fotografar");return;
        }
        if(preview.isAvailable())openCamera();
    }
    void stop(){
        active=false;
        ++generation;
        if(cameraHandler!=null)cameraHandler.post(this::closeCamera);
    }
    void dispose(){
        disposed=true;stop();photos.shutdown();
        if(cameraHandler!=null)cameraHandler.post(()->{
            closeCamera();
            if(cameraThread!=null)cameraThread.quitSafely();
        });
    }
    private void prepareThread(){
        if(cameraThread==null){
            cameraThread=new HandlerThread("NoiseCamera");
            cameraThread.start();
            cameraHandler=new Handler(cameraThread.getLooper());
        }
    }
    private int chooseCamera(){
        int count=Camera.getNumberOfCameras(),fallback=-1;
        for(int i=0;i<count;i++){
            Camera.CameraInfo info=new Camera.CameraInfo();Camera.getCameraInfo(i,info);
            if(fallback<0)fallback=i;
            if(info.facing==preferredFacing)return i;
        }
        return fallback;
    }
    private void openCamera(){
        if(!active||disposed||cameraReady||!preview.isAvailable())return;
        if(activity.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)return;
        prepareThread();
        final int n=++generation;
        cameraHandler.post(()->{
            if(n!=generation||!active||disposed||cameraReady)return;
            try{
                if(camera!=null)closeCamera();
                cameraId=chooseCamera();
                if(cameraId<0)throw new IOException("Nenhuma câmera encontrada");
                Camera opened=Camera.open(cameraId);
                if(n!=generation||!active||disposed){opened.release();return;}
                camera=opened;
                Camera.Parameters p=opened.getParameters();
                Camera.Size size=previewSize(p,preview.getWidth(),preview.getHeight());
                if(size!=null){
                    p.setPreviewSize(size.width,size.height);
                    Camera.Size picture=pictureSize(p,size);
                    if(picture!=null)p.setPictureSize(picture.width,picture.height);
                }
                if(p.getSupportedFocusModes()!=null&&p.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
                    p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                opened.setParameters(p);
                Camera.CameraInfo info=new Camera.CameraInfo();Camera.getCameraInfo(cameraId,info);
                int displayDegrees=rotationDegrees(activity.getWindowManager().getDefaultDisplay().getRotation());
                int orientation=info.facing==Camera.CameraInfo.CAMERA_FACING_FRONT
                    ?(360-(info.orientation+displayDegrees)%360)%360
                    :(info.orientation-displayDegrees+360)%360;
                opened.setDisplayOrientation(orientation);
                if(size!=null){
                    previewBufferWidth=size.width;previewBufferHeight=size.height;
                    previewDisplayOrientation=orientation;
                    activity.runOnUiThread(()->{if(!disposed&&active)
                        fitPreview(size.width,size.height,orientation);});
                }
                opened.setPreviewTexture(preview.getSurfaceTexture());
                opened.startPreview();
                cameraReady=true;
                activity.runOnUiThread(()->{
                    if(!disposed&&active){message("Pronto para fotografar");shutter.setEnabled(true);}
                });
            }catch(Exception error){
                closeCamera();
                activity.runOnUiThread(()->{if(!disposed){message("Câmera indisponível. Confira as permissões.");shutter.setEnabled(false);}});
            }
        });
    }
    /** Center crop the camera feed without nonuniform stretching. */
    private void fitPreview(int bufferWidth,int bufferHeight,int displayOrientation){
        if(disposed||preview.getWidth()<=0||preview.getHeight()<=0)return;
        int viewWidth=preview.getWidth(),viewHeight=preview.getHeight();
        float displayedWidth=(displayOrientation%180==0)?bufferWidth:bufferHeight;
        float displayedHeight=(displayOrientation%180==0)?bufferHeight:bufferWidth;
        float scale=Math.max(viewWidth/displayedWidth,viewHeight/displayedHeight)*digitalZoom;
        Matrix matrix=new Matrix();
        matrix.setScale(displayedWidth*scale/viewWidth,displayedHeight*scale/viewHeight,
            viewWidth*.5f,viewHeight*.5f);
        preview.setTransform(matrix);
    }
    /** Match preview and capture sensor aspect ratios to keep the crop predictable. */
    private static Camera.Size pictureSize(Camera.Parameters p,Camera.Size preview){
        List<Camera.Size> sizes=p.getSupportedPictureSizes();
        if(sizes==null||sizes.isEmpty())return null;
        float previewRatio=preview.width/(float)preview.height;
        Camera.Size best=null;double score=Double.MAX_VALUE;
        for(Camera.Size candidate:sizes){
            float ratio=candidate.width/(float)candidate.height;
            double mismatch=Math.abs(ratio-previewRatio);
            int longSide=Math.max(candidate.width,candidate.height);
            double cost=mismatch*100d + (longSide>4096?2d:0d)
                +1d/Math.max(1d,candidate.width*(double)candidate.height)*1000000d;
            if(cost<score){score=cost;best=candidate;}
        }
        return best;
    }
    private static Camera.Size previewSize(Camera.Parameters p,int width,int height){
        List<Camera.Size> list=p.getSupportedPreviewSizes();
        if(list==null||list.isEmpty())return null;
        float target=height>0&&width>0?Math.max(width,height)/(float)Math.min(width,height):16f/9f;
        Camera.Size best=null;double score=Double.MAX_VALUE;
        for(Camera.Size size:list){
            float ratio=Math.max(size.width,size.height)/(float)Math.min(size.width,size.height);
            double cost=Math.abs(ratio-target)*3+Math.abs(Math.log((size.width*(double)size.height)/(1280d*720d)))*.16;
            if(size.width>1920||size.height>1920)cost+=3;
            if(cost<score){score=cost;best=size;}
        }
        return best;
    }
    private static int rotationDegrees(int rotation){
        switch(rotation){case Surface.ROTATION_90:return 90;case Surface.ROTATION_180:return 180;
            case Surface.ROTATION_270:return 270;default:return 0;}
    }
    /** Autofocus at the tapped point; the tap never opens the media gallery. */
    private void tapToFocus(float x,float y){
        if(!active||disposed||capturing||!cameraReady||cameraHandler==null)return;
        final int width=Math.max(1,frameGuide.getWidth()),height=Math.max(1,frameGuide.getHeight());
        final int orientation=previewDisplayOrientation;
        final float zoom=digitalZoom;
        final boolean front=preferredFacing==Camera.CameraInfo.CAMERA_FACING_FRONT;
        cameraHandler.post(()->{
            try{
                if(camera==null||!active||disposed)return;
                Camera.Parameters p=camera.getParameters();
                List<String> modes=p.getSupportedFocusModes();
                boolean auto=modes!=null&&modes.contains(Camera.Parameters.FOCUS_MODE_AUTO);
                boolean point=p.getMaxNumFocusAreas()>0||p.getMaxNumMeteringAreas()>0;
                if(!auto&&!point){
                    activity.runOnUiThread(()->message("Este aparelho mantém o foco automático contínuo"));
                    return;
                }
                float dx=((x/width)-.5f)*2000f/zoom;
                float dy=((y/height)-.5f)*2000f/zoom;
                double angle=Math.toRadians(-orientation);
                float rx=(float)(dx*Math.cos(angle)-dy*Math.sin(angle));
                float ry=(float)(dx*Math.sin(angle)+dy*Math.cos(angle));
                if(front)rx=-rx;
                int cx=Math.max(-850,Math.min(850,Math.round(rx)));
                int cy=Math.max(-850,Math.min(850,Math.round(ry)));
                Camera.Area area=new Camera.Area(new Rect(cx-145,cy-145,cx+145,cy+145),1000);
                if(p.getMaxNumFocusAreas()>0)
                    p.setFocusAreas(Collections.singletonList(area));
                if(p.getMaxNumMeteringAreas()>0)
                    p.setMeteringAreas(Collections.singletonList(area));
                camera.cancelAutoFocus();
                if(auto)p.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                camera.setParameters(p);
                if(auto)camera.autoFocus((ok,device)->activity.runOnUiThread(()->{
                    if(!disposed)message(ok?"Foco ajustado":"Ajustando foco…");
                }));
                else activity.runOnUiThread(()->{if(!disposed)message("Área de foco ajustada");});
            }catch(RuntimeException error){
                activity.runOnUiThread(()->{if(!disposed)message("Foco automático indisponível");});
            }
        });
    }
    void orientationChanged(){
        if(!active||disposed)return;
        stop();
        preview.postDelayed(()->{if(!disposed)start();},100);
    }
    private void closeCamera(){
        cameraReady=false;
        if(camera!=null){
            try{camera.stopPreview();}catch(RuntimeException ignored){}
            try{camera.release();}catch(RuntimeException ignored){}
            camera=null;
        }
    }
    private void switchCamera(){
        if(capturing)return;
        preferredFacing=preferredFacing==Camera.CameraInfo.CAMERA_FACING_BACK
            ?Camera.CameraInfo.CAMERA_FACING_FRONT:Camera.CameraInfo.CAMERA_FACING_BACK;
        stop();
        message("Alternando câmera…");
        if(cameraHandler!=null)cameraHandler.post(()->activity.runOnUiThread(()->{if(!disposed)start();}));
    }
    private void takePhoto(){
        if(!active||disposed||capturing||cameraHandler==null)return;
        if(Build.VERSION.SDK_INT<29&&activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){
            message("Autorize o armazenamento para salvar a foto");
            activity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                MainActivity.CAMERA_STORAGE_PERMISSION);
            return;
        }
        capturing=true;shutter.setEnabled(false);
        final float sat=saturation,warm=temperature,ev=exposure;
        final float zoom=digitalZoom;
        final RectF cropFrame=frameGuide.frame();
        final int frameWidth=frameGuide.getWidth(),frameHeight=frameGuide.getHeight();
        message("Capturando…");
        cameraHandler.post(()->{
            try{
                if(camera==null)throw new IOException("Câmera desconectada");
                Camera.Parameters parameters=camera.getParameters();
                Camera.CameraInfo info=new Camera.CameraInfo();Camera.getCameraInfo(cameraId,info);
                int degrees=rotationDegrees(activity.getWindowManager().getDefaultDisplay().getRotation());
                int jpegRotation=info.facing==Camera.CameraInfo.CAMERA_FACING_FRONT?
                    (info.orientation+degrees)%360:(info.orientation-degrees+360)%360;
                parameters.setRotation(jpegRotation);
                camera.setParameters(parameters);
                camera.takePicture(null,null,(bytes,device)->{
                    try{device.startPreview();}catch(Exception ignored){}
                    if(disposed)return;
                    photos.execute(()->{
                        try{
                            Uri uri=savePicture(activity.getApplicationContext(),bytes,sat,warm,ev,zoom,cropFrame,frameWidth,frameHeight);
                            activity.runOnUiThread(()->{
                                if(disposed)return;latestPhoto=uri;
                                edit.setEnabled(true);edit.setAlpha(1f);
                                capturing=false;shutter.setEnabled(true);
                                message("Foto salva em Pictures/Noise! · Toque em Editar");
                            });
                        }catch(Exception|OutOfMemoryError e){
                            android.util.Log.e("NoiseCamera","capture",e);
                            activity.runOnUiThread(()->{
                                if(!disposed){capturing=false;shutter.setEnabled(true);
                                    message("Falha ao salvar foto. Confira o espaço livre.");}
                            });
                        }
                    });
                });
            }catch(Exception e){
                activity.runOnUiThread(()->{if(!disposed){capturing=false;shutter.setEnabled(camera!=null);
                    message("Não foi possível capturar a foto");}});
            }
        });
    }
    static Bitmap processPhoto(Bitmap source,float saturation,float temperature,float exposure){
        Bitmap output=Bitmap.createBitmap(source.getWidth(),source.getHeight(),Bitmap.Config.ARGB_8888);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        paint.setColorFilter(new ColorMatrixColorFilter(colorAdjustments(saturation,temperature,exposure)));
        new Canvas(output).drawBitmap(source,0,0,paint);
        return output;
    }
    private static Bitmap decodeAndOrient(byte[] jpeg)throws Exception{
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(jpeg,0,jpeg.length,options);
        if(options.outWidth<=0||options.outHeight<=0)throw new IOException("JPEG inválido");
        int sample=1;
        while(Math.max(options.outWidth,options.outHeight)/sample>4096)sample*=2;
        options.inJustDecodeBounds=false;options.inSampleSize=sample;
        Bitmap decoded=BitmapFactory.decodeByteArray(jpeg,0,jpeg.length,options);
        if(decoded==null)throw new IOException("Falha ao decodificar JPEG");
        ExifInterface exif=new ExifInterface(new ByteArrayInputStream(jpeg));
        int orientation=exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);
        Matrix transform=new Matrix();
        switch(orientation){
            case ExifInterface.ORIENTATION_ROTATE_90:transform.postRotate(90);break;
            case ExifInterface.ORIENTATION_ROTATE_180:transform.postRotate(180);break;
            case ExifInterface.ORIENTATION_ROTATE_270:transform.postRotate(270);break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:transform.postScale(-1,1);break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:transform.postScale(1,-1);break;
            case ExifInterface.ORIENTATION_TRANSPOSE:transform.postRotate(90);transform.postScale(-1,1);break;
            case ExifInterface.ORIENTATION_TRANSVERSE:transform.postRotate(270);transform.postScale(-1,1);break;
        }
        if(transform.isIdentity())return decoded;
        Bitmap oriented=Bitmap.createBitmap(decoded,0,0,decoded.getWidth(),decoded.getHeight(),transform,true);
        if(oriented!=decoded)decoded.recycle();
        return oriented;
    }
    private static Uri savePicture(Context c,byte[] jpeg,float saturation,float temperature,float exposure,
                                   float zoom,RectF frame,int frameWidth,int frameHeight)throws Exception{
        Bitmap decoded=null,cropped=null,filtered=null;Uri pending=null;File legacy=null;
        try{
            decoded=decodeAndOrient(jpeg);
            Rect rect=CameraFrameGuide.photoCrop(decoded.getWidth(),decoded.getHeight(),
                frameWidth,frameHeight,frame,zoom);
            cropped=Bitmap.createBitmap(decoded,rect.left,rect.top,rect.width(),rect.height());
            filtered=processPhoto(cropped,saturation,temperature,exposure);
            String name="Noise-Camera-"+new SimpleDateFormat("yyyyMMdd-HHmmss-SSS",Locale.US).format(new Date())+".jpg";
            Uri uri;
            if(Build.VERSION.SDK_INT>=29){
                ContentValues v=new ContentValues();
                v.put(MediaStore.Images.Media.DISPLAY_NAME,name);
                v.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");
                v.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Noise!");
                v.put(MediaStore.Images.Media.IS_PENDING,1);
                uri=pending=c.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);
                if(uri==null)throw new IOException("Falha ao criar imagem");
            }else{
                File folder=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),"Noise!");
                if(!folder.exists()&&!folder.mkdirs())throw new IOException("Pasta indisponível");
                legacy=new File(folder,name);
                uri=Uri.fromFile(legacy);
            }
            try(OutputStream stream=legacy!=null?new FileOutputStream(legacy):
                    c.getContentResolver().openOutputStream(uri,"w")){
                if(stream==null||!filtered.compress(Bitmap.CompressFormat.JPEG,94,stream))
                    throw new IOException("Falha ao codificar foto");
            }
            if(pending!=null){
                ContentValues ready=new ContentValues();ready.put(MediaStore.Images.Media.IS_PENDING,0);
                if(c.getContentResolver().update(pending,ready,null,null)<1)throw new IOException("Falha ao publicar foto");
            }else{
                MediaScannerConnection.scanFile(c,new String[]{legacy.getAbsolutePath()},
                    new String[]{"image/jpeg"},null);
            }
            return uri;
        }catch(Exception|OutOfMemoryError e){
            if(pending!=null)try{c.getContentResolver().delete(pending,null,null);}catch(Exception ignored){}
            if(legacy!=null)legacy.delete();throw e;
        }finally{
            if(filtered!=null)filtered.recycle();
            if(cropped!=null&&cropped!=decoded)cropped.recycle();
            if(decoded!=null)decoded.recycle();
        }
    }
    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture,int width,int height){
        if(active)openCamera();
    }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture,int width,int height){
        if(previewBufferWidth>0&&previewBufferHeight>0)
            fitPreview(previewBufferWidth,previewBufferHeight,previewDisplayOrientation);
    }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture){stop();return true;}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture){}
}
