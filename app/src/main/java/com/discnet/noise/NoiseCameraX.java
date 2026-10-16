package com.discnet.noise;

import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.util.Range;
import android.util.Rational;
import android.view.Surface;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

import com.google.common.util.concurrent.ListenableFuture;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * CameraX owner isolated from the editor. No CameraControl.setZoomRatio is ever
 * called: the image stays still and only Noise's overlay framing changes.
 */
final class NoiseCameraX implements LifecycleOwner {
    interface Listener {
        void ready(boolean flashAvailable,boolean evAvailable,String resolutionLabel,float nativeAspect,float maxFrameZoom);
        void error(String message);
        void jpeg(byte[] data,int rotationDegrees);
    }
    private final Activity activity;
    private final PreviewView view;
    private final Listener listener;
    private final LifecycleRegistry registry=new LifecycleRegistry(this);
    private final Executor main;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private ProcessCameraProvider provider;
    private Camera camera;
    private ImageCapture capture;
    private boolean disposed=false, running=false;
    private int facing=CameraSelector.LENS_FACING_BACK;
    private int flash=ImageCapture.FLASH_MODE_OFF;
    private float requestedEv=0f,appliedEv=0f;
    private int token=0;

    NoiseCameraX(Activity activity,PreviewView view,Listener listener){
        this.activity=activity;this.view=view;this.listener=listener;
        main=ContextCompat.getMainExecutor(activity);
        registry.setCurrentState(Lifecycle.State.CREATED);
    }
    @NonNull @Override public Lifecycle getLifecycle(){return registry;}
    void start(){
        if(disposed)return;
        running=true;
        registry.setCurrentState(Lifecycle.State.RESUMED);
        bind();
    }
    void stop(){
        if(disposed)return;
        running=false;++token;
        registry.setCurrentState(Lifecycle.State.CREATED);
        camera=null;capture=null;
        if(provider!=null)provider.unbindAll();
    }
    void dispose(){
        if(disposed)return;
        stop();disposed=true;++token;
        registry.setCurrentState(Lifecycle.State.DESTROYED);
        if(provider!=null)provider.unbindAll();
        io.shutdown();
    }
    boolean ready(){return camera!=null&&capture!=null&&running&&!disposed;}
    boolean front(){return facing==CameraSelector.LENS_FACING_FRONT;}
    boolean hasFlash(){return camera!=null&&camera.getCameraInfo().hasFlashUnit();}
    int currentFlash(){return flash;}
    void setFlash(int mode){
        flash=mode;
        if(capture!=null&&hasFlash())capture.setFlashMode(mode);
    }
    void flip(){
        facing=facing==CameraSelector.LENS_FACING_BACK?
            CameraSelector.LENS_FACING_FRONT:CameraSelector.LENS_FACING_BACK;
        if(provider!=null&&running)bind();
    }
    private void bind(){
        if(disposed||!running)return;
        final int request=++token;
        ListenableFuture<ProcessCameraProvider> future=ProcessCameraProvider.getInstance(activity);
        future.addListener(()->{
            if(disposed||!running||request!=token)return;
            try{
                provider=future.get();
                provider.unbindAll();
                CameraSelector selection=new CameraSelector.Builder()
                    .requireLensFacing(facing).build();
                if(!provider.hasCamera(selection)){
                    // Do not silently open another lens when requested camera is absent.
                    camera=null;capture=null;
                    listener.error("A lente selecionada não está disponível neste aparelho");
                    return;
                }
                Preview previewUseCase=new Preview.Builder()
                    .setTargetRotation(Surface.ROTATION_0).build();
                previewUseCase.setSurfaceProvider(view.getSurfaceProvider());
                ImageCapture.Builder stillBuilder=new ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setJpegQuality(100)
                    .setTargetRotation(Surface.ROTATION_0)
                    .setResolutionSelector(new androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
                        .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY).build());
                capture=stillBuilder.build();
                androidx.camera.core.ViewPort port=view.getViewPort();
                if(port!=null){
                    androidx.camera.core.UseCaseGroup group=new androidx.camera.core.UseCaseGroup.Builder()
                        .addUseCase(previewUseCase).addUseCase(capture)
                        .setViewPort(port).build();
                    camera=provider.bindToLifecycle(this,selection,group);
                }else camera=provider.bindToLifecycle(this,selection,previewUseCase,capture);
                capture.setFlashMode(hasFlash()?flash:ImageCapture.FLASH_MODE_OFF);
                boolean nativeEv=hasNativeEv();
                if(nativeEv)setExposureEv(requestedEv);
                String resolution="MAX";float nativeAspect=.75f,maxFrameZoom=4f;
                androidx.camera.core.ResolutionInfo details=capture.getResolutionInfo();
                if(details!=null){
                    android.util.Size pixels=details.getResolution();
                    int w=pixels.getWidth(),h=pixels.getHeight();
                    resolution=w+"×"+h;
                    nativeAspect=Math.min(w,h)/(float)Math.max(w,h);
                    // Reserve at least ~800 pixels on the short side for useful crop detail.
                    maxFrameZoom=Math.max(1f,Math.min(4f,Math.min(w,h)/800f));
                }
                listener.ready(hasFlash(),nativeEv,resolution,nativeAspect,maxFrameZoom);
            }catch(Exception error){
                camera=null;capture=null;
                listener.error("Não foi possível iniciar a câmera: "+error.getClass().getSimpleName());
            }
        },main);
    }
    boolean hasNativeEv(){
        if(camera==null)return false;
        Range<Integer> r=camera.getCameraInfo().getExposureState().getExposureCompensationRange();
        return r!=null&&(r.getLower()!=0||r.getUpper()!=0);
    }
    /** Native exposure compensation (when present). Returns the applied EV. */
    float setExposureEv(float ev){
        requestedEv=ev;
        if(!hasNativeEv()){appliedEv=0;return 0;}
        CameraInfo info=camera.getCameraInfo();
        Range<Integer> range=info.getExposureState().getExposureCompensationRange();
        Rational step=info.getExposureState().getExposureCompensationStep();
        float quantum=step.floatValue();
        if(quantum<=0){appliedEv=0;return 0;}
        int index=Math.min(range.getUpper(),Math.max(range.getLower(),Math.round(ev/quantum)));
        appliedEv=index*quantum;
        camera.getCameraControl().setExposureCompensationIndex(index);
        return appliedEv;
    }
    float appliedExposure(){return appliedEv;}
    void focus(float x,float y){
        if(!ready())return;
        try{
            MeteringPoint point=view.getMeteringPointFactory().createPoint(x,y);
            FocusMeteringAction focus=new FocusMeteringAction.Builder(
                point,FocusMeteringAction.FLAG_AF|FocusMeteringAction.FLAG_AE)
                .setAutoCancelDuration(3,TimeUnit.SECONDS).build();
            camera.getCameraControl().startFocusAndMetering(focus);
        }catch(Exception ignored){listener.error("Foco neste ponto não disponível");}
    }
    void shoot(){
        if(!ready()){listener.error("A câmera ainda está inicializando");return;}
        final ImageCapture imageCapture=capture;
        imageCapture.takePicture(io,new ImageCapture.OnImageCapturedCallback(){
            @Override public void onCaptureSuccess(@NonNull ImageProxy image){
                try{
                    if(image.getFormat()!=ImageFormat.JPEG){
                        main.execute(()->listener.error("Formato de captura não suportado"));
                        return;
                    }
                    ByteBuffer data=image.getPlanes()[0].getBuffer();
                    byte[] bytes=new byte[data.remaining()];
                    data.get(bytes);
                    int rotation=image.getImageInfo().getRotationDegrees();
                    main.execute(()->{if(!disposed)listener.jpeg(bytes,rotation);});
                }catch(Exception error){
                    main.execute(()->{if(!disposed)listener.error("Falha ao capturar imagem");});
                }finally{image.close();}
            }
            @Override public void onError(@NonNull ImageCaptureException error){
                main.execute(()->{if(!disposed)listener.error("Falha de captura: "+error.getMessage());});
            }
        });
    }
}
