package com.discnet.noise;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.camera.core.ImageCapture;
import androidx.camera.view.PreviewView;
import java.util.Locale;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Noise camera studio. All buttons are functional, the live preview never
 * zooms, and the photo is made from CameraX's full-resolution JPEG.
 */
final class CameraScreen extends FrameLayout {
    interface Listener {
        void close();
        void edit(Uri photo);
        default void gallery(){}
    }
    private enum Tool {
        ZOOM("Zoom","◎",1f,4f,.05f,1f),
        EV("Exposição","☼",-2f,2f,.05f,0f),
        SATURATION("Saturação","◉",0f,2f,.025f,1f),
        WARMTH("Temperatura","◐",-1f,1f,.025f,0f),
        HUE("Matiz","◈",-180f,180f,2f,0f),
        CONTRAST("Contraste","◕",.5f,1.5f,.025f,1f),
        SHARPNESS("Nitidez JPEG","◇",0f,.25f,.005f,0f),
        ALIGNMENT("Alinhar","⟳",-15f,15f,.25f,0f);
        final String name,symbol;
        final float min,max,step,neutral;
        Tool(String name,String symbol,float min,float max,float step,float neutral){
            this.name=name;this.symbol=symbol;this.min=min;this.max=max;this.step=step;this.neutral=neutral;
        }
    }
    private final Activity activity;
    private final Listener listener;
    private final PreviewView preview;
    private final CameraFrameGuide frameGuide;
    private NoiseCameraX engine;
    private final CameraRuler ruler;
    private final LinearLayout cameraBody;
    private final TextView valueLabel,titleLabel,status,flashButton,timerButton,ratioButton;
    private final ImageView lastThumbnail;
    private final TextView editButton;
    private final FrameLayout shutterButton;
    private final Handler mainHandler=new Handler(Looper.getMainLooper());
    private final ExecutorService pictures=Executors.newSingleThreadExecutor();
    private final OrientationEventListener deviceOrientationListener;
    private final ArrayList<TextView> toolButtons=new ArrayList<>();
    private Tool selected=Tool.ZOOM;
    private volatile boolean disposed,active,capturing;
    private Uri latestPhoto;
    private float zoom=1f,saturation=1f,temperature=0f,exposure=0f,hue=0f,contrast=1f,sharpness=0f,alignment=0f;
    private int deviceOrientationDegrees,timerSeconds,ratioIndex=0,flashIndex=0;
    private float nativeAspect=.75f,maxFrameZoom=4f;
    private int jpegQuality=98;
    private boolean showGrid=true;
    private boolean nativeEv;
    private float residualExposure;
    private String resolution="MAX";
    private static final float[] RATIOS={1f,4f/3f,3f/4f,16f/9f,9f/16f,3f/2f,3f/4f};
    private static final String[] RATIO_NAMES={"1:1","4:3","3:4","16:9","9:16","3:2","NATIVO"};

    CameraScreen(Activity context,Listener listener){
        super(context);
        this.activity=context;this.listener=listener;
        setBackgroundColor(0xff0b0a0e);
        jpegQuality=context.getSharedPreferences("noise_camera",Context.MODE_PRIVATE).getInt("jpeg_quality",98);
        showGrid=context.getSharedPreferences("noise_camera",Context.MODE_PRIVATE).getBoolean("show_grid",true);
        setFocusableInTouchMode(true);
        deviceOrientationListener=new OrientationEventListener(context,SensorManager.SENSOR_DELAY_UI){
            @Override public void onOrientationChanged(int orientation){
                if(orientation!=ORIENTATION_UNKNOWN)deviceOrientationDegrees=nearestRightAngle(orientation);
            }
        };
        cameraBody=new LinearLayout(context);
        cameraBody.setOrientation(LinearLayout.VERTICAL);
        cameraBody.setPadding(dp(8),dp(5),dp(8),dp(4));
        addView(cameraBody,new FrameLayout.LayoutParams(-1,-1));

        FrameLayout cameraPanel=new FrameLayout(context);
        GradientDrawable cameraCorner=new GradientDrawable();
        cameraCorner.setColor(0xff121014);cameraCorner.setCornerRadius(dp(23));
        cameraPanel.setBackground(cameraCorner);
        cameraPanel.setClipToOutline(true);
        cameraBody.addView(cameraPanel,new LinearLayout.LayoutParams(-1,0,1));
        preview=new PreviewView(context);
        preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        preview.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        cameraPanel.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        frameGuide=new CameraFrameGuide(context,new CameraFrameGuide.Gestures(){
            @Override public void focus(float x,float y){
                engine.focus(x,y);
                status.setText("Ajustando foco…");
            }
            @Override public void zoom(float factor){setToolValue(Tool.ZOOM,zoom*factor,true);}
        });
        cameraPanel.addView(frameGuide,new FrameLayout.LayoutParams(-1,-1));
        frameGuide.exactAspect(1f);
        frameGuide.grid(showGrid);

        LinearLayout top=new LinearLayout(context);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(4),dp(6),dp(4),dp(6));
        FrameLayout.LayoutParams topPosition=new FrameLayout.LayoutParams(-1,dp(62),Gravity.TOP);
        cameraPanel.addView(top,topPosition);
        TextView back=chip("‹",36);
        back.setContentDescription("Fechar câmera");
        top.addView(back,new LinearLayout.LayoutParams(dp(36),dp(40)));
        back.setOnClickListener(v->listener.close());
        addSpace(top);
        flashButton=chip("Off",48);
        top.addView(flashButton,new LinearLayout.LayoutParams(dp(48),dp(38)));
        flashButton.setContentDescription("Flash desligado");
        flashButton.setOnClickListener(v->cycleFlash());
        timerButton=chip("0s",35);
        LinearLayout.LayoutParams timerP=new LinearLayout.LayoutParams(dp(35),dp(38));
        timerP.leftMargin=dp(3);top.addView(timerButton,timerP);
        timerButton.setContentDescription("Temporizador de captura");
        timerButton.setOnClickListener(v->cycleTimer());
        ratioButton=chip("1:1",46);
        LinearLayout.LayoutParams ratioP=new LinearLayout.LayoutParams(dp(46),dp(38));
        ratioP.leftMargin=dp(3);top.addView(ratioButton,ratioP);
        ratioButton.setOnClickListener(v->showRatios(ratioButton));
        TextView flip=chip("⇄",36);
        LinearLayout.LayoutParams flipP=new LinearLayout.LayoutParams(dp(36),dp(38));
        flipP.leftMargin=dp(3);top.addView(flip,flipP);
        flip.setContentDescription("Alternar câmera frontal e traseira");
        flip.setOnClickListener(v->{if(!capturing)engine.flip();});
        TextView options=chip("⋯",34);
        LinearLayout.LayoutParams optionsP=new LinearLayout.LayoutParams(dp(34),dp(38));
        optionsP.leftMargin=dp(3);top.addView(options,optionsP);
        options.setContentDescription("Configurações da câmera");
        options.setOnClickListener(v->showCameraOptions(options));

        status=label("Preparando câmera…",11,0xffeae5ee);
        status.setGravity(Gravity.CENTER);
        status.setMaxLines(2);
        status.setPadding(dp(10),dp(7),dp(10),dp(7));
        status.setBackground(EditorTheme.panel(context,0xaa16151c,0,12,0));
        FrameLayout.LayoutParams toastPosition=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        toastPosition.bottomMargin=dp(9);
        cameraPanel.addView(status,toastPosition);

        LinearLayout lower=new LinearLayout(context);
        lower.setOrientation(LinearLayout.VERTICAL);
        lower.setPadding(dp(4),dp(8),dp(4),0);
        cameraBody.addView(lower,new LinearLayout.LayoutParams(-1,-2));

        HorizontalScrollView tools=new HorizontalScrollView(context);
        tools.setHorizontalScrollBarEnabled(false);
        tools.setFillViewport(false);
        lower.addView(tools,new LinearLayout.LayoutParams(-1,dp(62)));
        LinearLayout row=new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6),0,dp(6),0);
        tools.addView(row);
        for(Tool tool:Tool.values()){
            TextView icon=toolIcon(tool);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(45),dp(45));
            lp.setMargins(dp(5),0,dp(5),0);
            row.addView(icon,lp);
            icon.setOnClickListener(v->selectTool(tool));
            toolButtons.add(icon);
        }
        LinearLayout names=new LinearLayout(context);
        names.setGravity(Gravity.CENTER_VERTICAL);
        lower.addView(names,new LinearLayout.LayoutParams(-1,dp(25)));
        titleLabel=label("Zoom",13,0xffeee9f3);
        titleLabel.setTypeface(NoiseTypography.bold(context));
        names.addView(titleLabel,new LinearLayout.LayoutParams(0,-1,1));
        valueLabel=label("1.00×",14,EditorTheme.LIME);
        valueLabel.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        names.addView(valueLabel,new LinearLayout.LayoutParams(dp(112),-1));
        ruler=new CameraRuler(context);
        ruler.listen(value->setToolValue(selected,value,false));
        lower.addView(ruler,new LinearLayout.LayoutParams(-1,dp(81)));

        LinearLayout actions=new LinearLayout(context);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(dp(4),dp(5),dp(4),0);
        lower.addView(actions,new LinearLayout.LayoutParams(-1,dp(77)));

        FrameLayout galleryArea=new FrameLayout(context);
        actions.addView(galleryArea,new LinearLayout.LayoutParams(0,dp(53),1));
        TextView gallery=sideButton("Galeria");
        gallery.setContentDescription("Abrir galeria do Noise");
        galleryArea.addView(gallery,new FrameLayout.LayoutParams(-1,-1));
        lastThumbnail=new ImageView(context);
        lastThumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
        lastThumbnail.setBackground(EditorTheme.buttonSurface(context,8,false));
        FrameLayout.LayoutParams thumbPosition=new FrameLayout.LayoutParams(
            dp(39),dp(39),Gravity.LEFT|Gravity.CENTER_VERTICAL);
        thumbPosition.leftMargin=dp(4);
        galleryArea.addView(lastThumbnail,thumbPosition);
        galleryArea.setClickable(true);galleryArea.setFocusable(true);
        galleryArea.setContentDescription("Ver fotografias da galeria Noise");
        galleryArea.setOnClickListener(v->listener.gallery());

        shutterButton=new FrameLayout(context);
        shutterButton.setContentDescription("Capturar fotografia");
        shutterButton.setFocusable(true);shutterButton.setClickable(true);
        FrameLayout.LayoutParams shutterInner=new FrameLayout.LayoutParams(dp(66),dp(66),Gravity.CENTER);
        View shutterArt=new View(context);
        GradientDrawable ring=new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(0xfff4eff8);
        ring.setStroke(dp(4),0xff8c8296);
        shutterArt.setBackground(ring);
        shutterButton.addView(shutterArt,shutterInner);
        LinearLayout.LayoutParams mainButton=new LinearLayout.LayoutParams(dp(84),dp(76));
        mainButton.setMargins(dp(2),0,dp(2),0);
        actions.addView(shutterButton,mainButton);
        shutterButton.setOnClickListener(v->scheduleShot());

        editButton=sideButton("Editar");
        editButton.setEnabled(false);editButton.setAlpha(.45f);
        actions.addView(editButton,new LinearLayout.LayoutParams(0,dp(53),1));
        editButton.setOnClickListener(v->{if(latestPhoto!=null)listener.edit(latestPhoto);});

        editButton.setContentDescription("Abrir última fotografia no editor Noise");

        engine=new NoiseCameraX(context,preview,new NoiseCameraX.Listener(){
            @Override public void ready(boolean flash,boolean ev,String resolutionLabel,float aspect,float zoomLimit){
                if(disposed)return;
                flashButton.setVisibility(flash?View.VISIBLE:View.GONE);
                nativeEv=ev;resolution=resolutionLabel;
                nativeAspect=aspect;maxFrameZoom=zoomLimit;
                if(ratioIndex==RATIOS.length-1)frameGuide.exactAspect(nativeAspect);
                if(zoom>maxFrameZoom)setToolValue(Tool.ZOOM,maxFrameZoom,true);
                if(selected==Tool.ZOOM)selectTool(Tool.ZOOM);
                residualExposure=exposure-engine.setExposureEv(exposure);
                applyPreview();
                status.setText("Pronto · "+resolution);
            }
            @Override public void error(String message){
                if(!disposed){status.setText(message);capturing=false;shutterButton.setEnabled(true);}
            }
            @Override public void jpeg(byte[] data,int cameraRotation){saveCapturedPhoto(data,cameraRotation);}
        });
        selectTool(Tool.ZOOM);
        post(this::applyPreview);
    }
    private int dp(float px){return EditorTheme.dp(activity,px);}
    private TextView label(String text,int size,int color){
        TextView view=new TextView(activity);
        view.setText(text);view.setTextColor(color);view.setTextSize(size);
        view.setTypeface(NoiseTypography.regular(activity));
        return view;
    }
    private void addSpace(LinearLayout row){
        row.addView(new View(activity),new LinearLayout.LayoutParams(0,1,1));
    }
    private TextView chip(String text,int width){
        TextView result=label(text,12,0xfff5f2f8);
        result.setGravity(Gravity.CENTER);
        result.setSingleLine();
        result.setBackground(EditorTheme.panel(activity,0xbf1c1b23,0,18,0));
        result.setFocusable(true);
        return result;
    }
    private TextView sideButton(String text){
        TextView button=label(text,13,EditorTheme.INK);
        button.setGravity(Gravity.CENTER);
        button.setBackground(EditorTheme.buttonSurface(activity,18,false));
        button.setFocusable(true);
        return button;
    }
    private TextView toolIcon(Tool tool){
        TextView icon=label(tool.symbol,21,0xffb1a8b8);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(EditorTheme.panel(activity,0xff24212a,0,28,0xff45404b));
        icon.setContentDescription(tool.name);
        icon.setFocusable(true);
        return icon;
    }
    private void selectTool(Tool tool){
        selected=tool;
        for(int i=0;i<toolButtons.size();i++){
            TextView button=toolButtons.get(i);
            boolean activeTool=Tool.values()[i]==tool;
            button.setTextColor(activeTool?0xfff8f5fa:0xffb2a9b8);
            GradientDrawable shape=new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(activeTool?0xff352d3e:0xff211f28);
            shape.setStroke(dp(activeTool?1.5f:1),
                activeTool?0xffefe6f9:0xff37323c);
            button.setBackground(shape);
            button.setScaleX(activeTool?1.04f:1f);button.setScaleY(activeTool?1.04f:1f);
            if(activeTool&&android.os.Build.VERSION.SDK_INT>=26)button.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
        titleLabel.setText(tool.name);
        ruler.configure(tool.min,tool==Tool.ZOOM?maxFrameZoom:tool.max,tool.step,toolValue(tool),tool.neutral);
        displayToolValue();
    }
    private float toolValue(Tool tool){
        switch(tool){
            case ZOOM:return zoom;
            case EV:return exposure;
            case SATURATION:return saturation;
            case WARMTH:return temperature;
            case HUE:return hue;
            case CONTRAST:return contrast;
            case SHARPNESS:return sharpness;
            case ALIGNMENT:return alignment;
        }
        return 0;
    }
    private void setToolValue(Tool tool,float value,boolean emitToRuler){
        float upper=tool==Tool.ZOOM?maxFrameZoom:tool.max;
        float clamped=Math.max(tool.min,Math.min(upper,value));
        switch(tool){
            case ZOOM:
                zoom=clamped;frameGuide.zoom(zoom);break;
            case EV:
                exposure=clamped;
                residualExposure=exposure-engine.setExposureEv(exposure);break;
            case SATURATION:saturation=clamped;break;
            case WARMTH:temperature=clamped;break;
            case HUE:hue=clamped;break;
            case CONTRAST:contrast=clamped;break;
            case SHARPNESS:sharpness=clamped;break;
            case ALIGNMENT:alignment=clamped;frameGuide.alignment(alignment);break;
        }
        if(selected==tool){
            if(emitToRuler)ruler.setValue(clamped,false);
            displayToolValue();
        }
        if(tool!=Tool.ZOOM)applyPreview();
    }
    private void displayToolValue(){
        float v=toolValue(selected);
        String text;
        switch(selected){
            case ZOOM:text=String.format(Locale.US,"%.2f×",v);break;
            case EV:text=String.format(Locale.US,"%+.2f EV",v);break;
            case SATURATION:text=String.format(Locale.US,"%.0f%%",v*100f);break;
            case WARMTH:text=String.format(Locale.US,"%+.0f",v*100f);break;
            case HUE:text=String.format(Locale.US,"%+.0f°",v);break;
            case SHARPNESS:text=String.format(Locale.US,"%.0f%%",v*400f);break;
            case ALIGNMENT:text=String.format(Locale.US,"%+.2f°",v);break;
            default:text=String.format(Locale.US,"%.0f%%",v*100f);
        }
        valueLabel.setText(text);
    }
    private void applyPreview(){
        if(preview==null)return;
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        paint.setColorFilter(new ColorMatrixColorFilter(CameraPhotoPipeline.colors(
            saturation,temperature,residualExposure,hue,contrast)));
        preview.setLayerType(View.LAYER_TYPE_HARDWARE,paint);
        preview.invalidate();
    }
    private void showCameraOptions(View anchor){
        PopupMenu menu=new PopupMenu(activity,anchor);
        for(int quality:new int[]{95,98,100}){
            menu.getMenu().add(1,quality,quality,"JPEG · qualidade "+quality+"%")
                .setCheckable(true).setChecked(quality==jpegQuality);
        }
        menu.getMenu().setGroupCheckable(1,true,true);
        menu.getMenu().add(2,1,110,showGrid?"Ocultar grade":"Exibir grade");
        menu.getMenu().add(3,1,111,"Restaurar ajustes da câmera");
        menu.setOnMenuItemClickListener(item->{
            if(item.getGroupId()==1){
                jpegQuality=item.getItemId();
                activity.getSharedPreferences("noise_camera",Context.MODE_PRIVATE).edit()
                    .putInt("jpeg_quality",jpegQuality).apply();
                status.setText("Qualidade de captura JPEG · "+jpegQuality+"%");
                return true;
            }
            if(item.getGroupId()==2){
                showGrid=!showGrid;
                frameGuide.grid(showGrid);
                activity.getSharedPreferences("noise_camera",Context.MODE_PRIVATE).edit()
                    .putBoolean("show_grid",showGrid).apply();
                return true;
            }
            for(Tool tool:Tool.values())setToolValue(tool,tool.neutral,false);
            frameGuide.centerFrame();
            selectTool(selected);
            status.setText("Ajustes redefinidos");
            return true;
        });
        menu.show();
    }
    private void cycleFlash(){
        if(!engine.hasFlash())return;
        int[] modes={ImageCapture.FLASH_MODE_OFF,ImageCapture.FLASH_MODE_AUTO,ImageCapture.FLASH_MODE_ON};
        flashIndex=(flashIndex+1)%modes.length;
        engine.setFlash(modes[flashIndex]);
        flashButton.setText(new String[]{"Off","Auto","On"}[flashIndex]);
        flashButton.setContentDescription("Flash "+flashButton.getText());
    }
    private void cycleTimer(){
        int[] seconds={0,3,5,10};
        int current=0;
        for(int i=0;i<seconds.length;i++)if(seconds[i]==timerSeconds)current=i;
        timerSeconds=seconds[(current+1)%seconds.length];
        timerButton.setText(timerSeconds+"s");
    }
    /** Select any aspect ratio in one tap rather than seven sequential toggles. */
    private void showRatios(View anchor){
        PopupMenu menu=new PopupMenu(activity,anchor);
        for(int i=0;i<RATIO_NAMES.length;i++){
            final int index=i;
            menu.getMenu().add(0,i,i,RATIO_NAMES[i])
                .setCheckable(true).setChecked(index==ratioIndex);
        }
        menu.getMenu().setGroupCheckable(0,true,true);
        menu.setOnMenuItemClickListener(item->{
            ratioIndex=item.getItemId();
            ratioButton.setText(RATIO_NAMES[ratioIndex]);
            frameGuide.exactAspect(ratioIndex==RATIOS.length-1?nativeAspect:RATIOS[ratioIndex]);
            return true;
        });
        menu.show();
    }
    private void cycleRatio(){
        ratioIndex=(ratioIndex+1)%RATIOS.length;
        ratioButton.setText(RATIO_NAMES[ratioIndex]);
        frameGuide.exactAspect(ratioIndex==RATIOS.length-1?nativeAspect:RATIOS[ratioIndex]);
    }
    private void scheduleShot(){
        if(!active||disposed||capturing)return;
        if(Build.VERSION.SDK_INT<29&&activity.checkSelfPermission(
            Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){
            activity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                MainActivity.CAMERA_STORAGE_PERMISSION);
            return;
        }
        capturing=true;shutterButton.setEnabled(false);
        final int seconds=timerSeconds;
        if(seconds<=0)shoot();else countdown(seconds);
    }
    private void countdown(int seconds){
        if(disposed||!active){capturing=false;return;}
        if(seconds==0){shoot();return;}
        status.setText("Capturando em "+seconds+"…");
        mainHandler.postDelayed(()->countdown(seconds-1),1000);
    }
    private CameraPhotoPipeline.Settings pendingSettings;
    private void shoot(){
        if(!active||disposed){capturing=false;return;}
        CameraPhotoPipeline.Settings settings=new CameraPhotoPipeline.Settings();
        settings.frame=frameGuide.frame();
        settings.viewWidth=frameGuide.getWidth();
        settings.viewHeight=frameGuide.getHeight();
        settings.mirror=engine.front();
        settings.orientation=deviceOrientationDegrees;
        settings.jpegQuality=jpegQuality;
        settings.sat=saturation;settings.warm=temperature;
        settings.exposure=residualExposure;
        settings.hue=hue;settings.contrast=contrast;
        settings.sharpness=sharpness;settings.alignment=alignment;
        pendingSettings=settings;
        status.setText("Fotografando…");
        engine.shoot();
    }
    private void saveCapturedPhoto(byte[] jpeg,int cameraRotation){
        final CameraPhotoPipeline.Settings snapshot=pendingSettings==null?
            new CameraPhotoPipeline.Settings():pendingSettings.copy();
        snapshot.jpegRotation=cameraRotation;
        pictures.execute(()->{
            try{
                Uri uri=CameraPhotoPipeline.save(activity.getApplicationContext(),jpeg,snapshot);
                Bitmap previewThumb=null;
                try(InputStream in=activity.getContentResolver().openInputStream(uri)){
                    BitmapFactory.Options options=new BitmapFactory.Options();
                    options.inSampleSize=16;
                    previewThumb=BitmapFactory.decodeStream(in,null,options);
                }catch(Exception ignored){}
                final Bitmap thumbnail=previewThumb;
                mainHandler.post(()->{
                    if(disposed){
                        if(thumbnail!=null)thumbnail.recycle();
                        return;
                    }
                    latestPhoto=uri;
                    capturing=false;shutterButton.setEnabled(true);
                    if(thumbnail!=null)lastThumbnail.setImageBitmap(thumbnail);
                    editButton.setAlpha(1f);editButton.setEnabled(true);
                    status.setText("Foto salva em Pictures/Noise!");
                });
            }catch(Exception|OutOfMemoryError e){
                android.util.Log.e("NoiseCamera","Save",e);
                mainHandler.post(()->{
                    if(!disposed){
                        capturing=false;shutterButton.setEnabled(true);
                        status.setText("Falha ao salvar: memória ou espaço insuficiente");
                    }
                });
            }
        });
    }
    void start(){
        if(disposed)return;
        active=true;
        if(deviceOrientationListener.canDetectOrientation())deviceOrientationListener.enable();
        if(activity.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            status.setText("Autorize a câmera");return;
        }
        engine.start();
    }
    void stop(){
        active=false;
        deviceOrientationListener.disable();
        mainHandler.removeCallbacksAndMessages(null);
        engine.stop();
        capturing=false;shutterButton.setEnabled(true);
    }
    void dispose(){
        if(disposed)return;
        stop();disposed=true;
        engine.dispose();pictures.shutdown();
    }
    static int nearestRightAngle(int orientation){
        if(orientation<0)return 0;
        return (((orientation+45)/90)*90)%360;
    }
    static Bitmap rotateForDevice(Bitmap source,int degrees){
        int rotation=(360-nearestRightAngle(degrees))%360;
        if(rotation==0)return source;
        Matrix matrix=new Matrix();matrix.setRotate(rotation);
        return Bitmap.createBitmap(source,0,0,source.getWidth(),source.getHeight(),matrix,true);
    }
    static Bitmap processPhoto(Bitmap source,float saturation,float temperature,float exposure){
        return CameraPhotoPipeline.processPhoto(source,saturation,temperature,exposure,0,1f,0);
    }
}
