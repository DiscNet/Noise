package com.discnet.noise;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Coordinates screens; document, persistence, processing and export have independent owners. */
public class MainActivity extends Activity implements EditorPanel.Host {
    private EditorDocument document=new EditorDocument(),clipboard;
    private final EditHistory history=new EditHistory();
    private final ExecutorService files=Executors.newSingleThreadExecutor(),exportWorker=Executors.newSingleThreadExecutor();
    private final AtomicInteger loads=new AtomicInteger();
    private Future<?> importTask;
    private ProjectStore store;
    private ProjectVault vault;
    private String projectId,projectName="Sem título";
    private ImageSource source;
    private Bitmap original;
    private EditorSurface preview;
    private FrameLayout appBackground,editorStage;
    private EditorWorkspace workspace;
    private EditorPanel panel;
    private GalleryScreen galleryScreen;
    private GalleryScreen imagePickerScreen;
    private MediaLibrary.Photo pendingGalleryCopy;
    private CameraScreen cameraScreen;
    private int orientationBeforeCamera=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    private HomeScreen homeScreen;
    private boolean homeHostResumed;
    private SettingsScreen settingsScreen;
    private ProjectLibraryScreen projectLibrary;
    private boolean projectsFromEditor;
    private StudioTabBar studioTabs;
    private int activeStudioTab=-1;
    private float studioSwipeX,studioSwipeY;
    private boolean studioGestureArmed;
    private ResizeDialog exportScreen;
    private ExportProgressOverlay exportProgress;
    private ExportResultOverlay exportResult;
    private PresetDialog presetLibrary;
    private OrbitalSelector orbital;
    private CropEditor cropEditor;
    private LinearLayout topBar;
    private HorizontalScrollView categoryScroll;
    private final TextView[] tabs=new TextView[7];
    private ImageView undo,redo,save;
    private TextView status,empty,compareLabel;
    private boolean loading,exporting,destroyed,comparing,orbitalClosing;
    private int maxTexture=4096;
    private static final int GALLERY_PERMISSION=73,SAVE_PERMISSION=74,VAULT_REQUEST=82,CAMERA_PERMISSION=83;
    static final int CAMERA_STORAGE_PERMISSION=84, GALLERY_COPY_PERMISSION=85;
    private int dp(float n){return EditorTheme.dp(this,n);}
    private TextView text(String t,int size,int color){TextView v=new TextView(this);v.setTypeface(NoiseTypography.regular(this));v.setText(t);v.setTextSize(size);v.setTextColor(color);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private TextView button(String t,boolean primary){TextView v=text(t,14,primary?0xff19121f:EditorTheme.INK);v.setGravity(Gravity.CENTER);EditorTheme.button(v,primary);v.setMinHeight(dp(48));v.setFocusable(true);return v;}
    private LinearLayout vertical(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);store=new ProjectStore(this);vault=new ProjectVault(this);getWindow().setStatusBarColor(0xff0c0b10);getWindow().setNavigationBarColor(0xff0c0b10);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        appBackground=new SwipeNavigationHost(this,new SwipeNavigationHost.Handler(){
            @Override public boolean allow(float x,float y){return canSwipeForTools(x,y);}
            @Override public void start(boolean effects){showOrbital(effects,true);}
            @Override public void progress(float fraction){
                if(orbital!=null)orbital.setReveal(fraction);
            }
             @Override public void finish(boolean open){
                if(orbital==null)return;
                OrbitalSelector active=orbital;
                if(!open){orbital=null;orbitalClosing=true;}
                active.finishReveal(open);
            }
        });
        appBackground.setBackgroundColor(0xff0c0b10);setContentView(appBackground);
        appBackground.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        workspace=new EditorWorkspace(this);appBackground.addView(workspace,new FrameLayout.LayoutParams(-1,-1));
        topBar=new LinearLayout(this);topBar.setGravity(Gravity.CENTER_VERTICAL);topBar.setPadding(dp(4),0,dp(4),0);
        addIcon(topBar,IconArt.BACK,"Início",this::showHome);
        TextView brand=text("Noise!",18,EditorTheme.INK);brand.setTypeface(NoiseTypography.bold(this));topBar.addView(brand,new LinearLayout.LayoutParams(0,-1,1));
        undo=addIcon(topBar,IconArt.UNDO,"Desfazer",()->{document=history.undo(document);restored();});
        redo=addIcon(topBar,IconArt.REDO,"Refazer",()->{document=history.redo(document);restored();});
        save=addIcon(topBar,IconArt.SAVE,"Exportar imagem",this::showExport);
        addIcon(topBar,IconArt.SETTINGS,"Configurações",this::showSettings);
        ImageView menu=addIcon(topBar,IconArt.MORE,"Mais opções",()->{});menu.setOnClickListener(v->showMenu(menu));
        editorStage=new FrameLayout(this);preview=new EditorSurface(this,new EditorSurface.Listener(){
            public void ready(int maximum){maxTexture=Math.min(4096,maximum);}
            public void failed(String message){if(!destroyed){status.setText(message);Toast.makeText(MainActivity.this,message,Toast.LENGTH_LONG).show();}}
        });editorStage.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        preview.gestures(new EditorSurface.Gestures(){public void compare(boolean active){comparing=active;publish();}public void fullscreen(){toggleFullscreen();}});
        empty=text("Noise!\n\nAbrir uma imagem\n\nSeu estúdio, sem distrações",18,EditorTheme.INK);empty.setGravity(Gravity.CENTER);empty.setBackgroundColor(0xff0c0b10);empty.setFocusable(true);editorStage.addView(empty,new FrameLayout.LayoutParams(-1,-1));empty.setOnClickListener(v->showImagePicker());
        compareLabel=text("Original",12,EditorTheme.LIME);compareLabel.setBackgroundColor(0xff20242d);compareLabel.setPadding(dp(12),dp(6),dp(12),dp(6));FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);cp.topMargin=dp(8);editorStage.addView(compareLabel,cp);compareLabel.setVisibility(View.GONE);
        status=text("",11,EditorTheme.MUTED);status.setMaxLines(2);status.setPadding(dp(12),dp(5),dp(52),dp(5));status.setBackgroundColor(0xff0c0b10);FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);editorStage.addView(status,sp);
        ImageView full=IconArt.button(this,IconArt.FULL,"Alternar tela cheia");FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.BOTTOM|Gravity.RIGHT);editorStage.addView(full,fp);full.setOnClickListener(v->toggleFullscreen());
        panel=new EditorPanel(this,this);
        panel.effect=Math.max(0,Math.min(AdjustmentSpec.EFFECTS.length-1,
            getPreferences(0).getInt("lastEffect",0)));
        workspace.setSheetResizeEnabled(SettingsScreen.resizePanelEnabled(this));
        panel.panelResizeEnabled(workspace.isSheetResizeEnabled());
        installSheetGesture();
        categoryScroll=new HorizontalScrollView(this);categoryScroll.setHorizontalScrollBarEnabled(false);categoryScroll.setFillViewport(true);categoryScroll.setBackgroundColor(0xff17161d);
        LinearLayout strip=new LinearLayout(this);strip.setGravity(Gravity.CENTER_VERTICAL);
        strip.setPadding(dp(18),0,dp(18),0);categoryScroll.addView(strip);
        TextView gestureHint=text("←  Ferramentas           Efeitos  →",11,EditorTheme.MUTED);
        gestureHint.setLetterSpacing(.045f);gestureHint.setGravity(Gravity.CENTER_VERTICAL);
        gestureHint.setFocusable(false);gestureHint.setClickable(false);
        strip.addView(gestureHint,new LinearLayout.LayoutParams(-1,-1));
        // Keep the original category actions addressable to instrumentation and accessibility.
        // The user-facing category chooser is now the genuinely circular OrbitalSelector.
        for(int i=0;i<7;i++){final int n=i;TextView tab=text(AdjustmentSpec.CATEGORIES[i],13,EditorTheme.MUTED);
            tab.setVisibility(View.GONE);strip.addView(tab,new LinearLayout.LayoutParams(0,0));
            tabs[i]=tab;tab.setOnClickListener(v->selectCategory(n));}
        workspace.children(topBar,editorStage,panel,categoryScroll);selectCategory(0);updateActions();
        if(saved!=null){try{document=EditorDocument.fromJson(new JSONObject(saved.getString("document","{}")));history.restore(new JSONObject(saved.getString("history","{}")));}catch(Exception ignored){}
            String id=saved.getString("project");if(id!=null)loadProject(id,true);selectCategory(saved.getInt("category",0));}
        if(saved==null||saved.getString("project")==null)showHome();
        // Ask once on the first real app launch, not only after opening Gallery.
        // A denial is respected; the user may still use the system photo picker
        // and can explicitly grant later from the Gallery screen.
        if(saved==null)appBackground.post(this::requestFirstLaunchGalleryPermission);
    }
    private ImageView addIcon(LinearLayout parent,int icon,String label,Runnable action){ImageView v=IconArt.button(this,icon,label);parent.addView(v,new LinearLayout.LayoutParams(dp(48),dp(48)));v.setOnClickListener(w->action.run());return v;}
    private void installSheetGesture(){panel.handle.setOnClickListener(v->{
        if(workspace.isSheetResizeEnabled())workspace.cycleSheet();
    });panel.handle.setOnTouchListener(new View.OnTouchListener(){float start;int height;boolean moved;public boolean onTouch(View v,MotionEvent e){
        if(!workspace.isSheetResizeEnabled()||workspace.landscape())return false;
        switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:start=e.getRawY();height=workspace.sheetHeight();moved=false;return true;
            case MotionEvent.ACTION_MOVE:if(Math.abs(e.getRawY()-start)>dp(5))moved=true;if(moved)workspace.resizeSheet(height+Math.round(start-e.getRawY()));return true;
            case MotionEvent.ACTION_UP:if(!moved)v.performClick();return true;case MotionEvent.ACTION_CANCEL:return true;}return false;}});}
    private void selectCategory(int n){n=Math.max(0,Math.min(6,n));panel.select(n);
        for(int i=0;i<tabs.length;i++)tabs[i].setSelected(i==n);
        // A roda se abre exclusivamente por arrasto, sem botao de menu.
    }
    @Override public void openEffects(){showOrbital(true);}
    private boolean canSwipeForTools(float x,float y){
        // Physical edges are checked by SwipeNavigationHost. Horizontal
        // navigation may start anywhere in the editor EXCEPT actual editable
        // slider hit areas. Moving between tabs never disables this gate.
        if(original==null||loading||exporting||orbital!=null||preview==null
            ||workspace==null||cropEditor!=null||workspace.isFullscreen())return false;
        if(homeScreen!=null&&homeScreen.getVisibility()==View.VISIBLE)return false;
        if(galleryScreen!=null&&galleryScreen.getVisibility()==View.VISIBLE)return false;
        if(cameraScreen!=null)return false;
        if(projectLibrary!=null||presetLibrary!=null||settingsScreen!=null)return false;
        int[] rootPosition=new int[2];
        appBackground.getLocationOnScreen(rootPosition);
        int screenX=Math.round(rootPosition[0]+x);
        int screenY=Math.round(rootPosition[1]+y);
        if(panel!=null&&(panel.isOverAdjustmentBar(screenX,screenY)
            ||panel.isOverColorEditor(screenX,screenY)))return false;
        // Check all four screen extents; system bars have been excluded from
        // the root by WindowInsets padding.
        return x>=appBackground.getPaddingLeft()
            &&x<=appBackground.getWidth()-appBackground.getPaddingRight()
            &&y>=appBackground.getPaddingTop()
            &&y<=appBackground.getHeight()-appBackground.getPaddingBottom();
    }
    private void closeOrbital(){
        if(orbital!=null){OrbitalSelector old=orbital;orbital=null;orbitalClosing=true;old.dismiss();}
    }
    private boolean hasDismissingOverlay(){
        for(int i=0;i<appBackground.getChildCount();i++){
            View child=appBackground.getChildAt(i);
            if(child instanceof OrbitalSelector)return true;
        }
        return false;
    }
    private void showOrbital(boolean effects){showOrbital(effects,false);}
    private void showOrbital(boolean effects,boolean interactive){
        if(original==null){showImagePicker();return;}
        if(orbital!=null)return;
        // A closing wheel must never lock the editor after a category change.
        // If the user starts another edge gesture, interrupt the previous
        // fade and replace it; the old overlay cannot steal new touches.
        for(int i=appBackground.getChildCount()-1;i>=0;i--){
            View child=appBackground.getChildAt(i);
            if(child instanceof OrbitalSelector){
                appBackground.removeView(child);
            }
        }
        orbitalClosing=false;
        String[] labels=effects?AdjustmentSpec.EFFECTS:AdjustmentSpec.CATEGORIES;
        int[] symbols=effects?new int[]{15,12,10,16,32,38,26,17}:new int[]{0,1,7,10,9,24,32};
        OrbitalSelector wheel=new OrbitalSelector(this,
            effects?"Escolha um\nefeito":"Explore suas\nferramentas",
            effects?"Arraste para percorrer a roda.":"Deslize para explorar todas as categorias.",
            labels,symbols,effects?panel.effect:
                getPreferences(0).getInt("lastTool",panel.category),n->{
                if(effects){
                    panel.effect=n;
                    getPreferences(0).edit().putInt("lastEffect",n).apply();
                    selectCategory(3);
                }else{
                    getPreferences(0).edit().putInt("lastTool",n).apply();
                    selectCategory(n);
                }
                closeOrbital();
            });
        orbital=wheel;
        wheel.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            @Override public void onViewAttachedToWindow(View v){}
            @Override public void onViewDetachedFromWindow(View v){
                if(orbital==v)orbital=null;
                orbitalClosing=hasDismissingOverlay();
            }
        });
        wheel.setMode(effects);
        // A visible wheel never opens another wheel. Rightward drag closes.
        wheel.setNavigate(direction->closeOrbital());
        appBackground.addView(wheel,new FrameLayout.LayoutParams(-1,-1));
        wheel.bringToFront();
        if(interactive)wheel.beginReveal();
        else wheel.enterFrom(effects?1:-1);
    }
    private void revealStudioTabs(int page){
        if(studioTabs==null){
            studioTabs=new StudioTabBar(this,this::switchStudioTab);
            FrameLayout.LayoutParams position=new FrameLayout.LayoutParams(
                -1,dp(61),Gravity.BOTTOM);
            position.leftMargin=dp(11);
            position.rightMargin=dp(11);
            position.bottomMargin=dp(7);
            appBackground.addView(studioTabs,position);
        }
        activeStudioTab=page;
        studioTabs.select(page);
        studioTabs.setVisibility(View.VISIBLE);
        studioTabs.bringToFront();
    }
    private void hideStudioTabs(){
        activeStudioTab=-1;
        if(studioTabs!=null)studioTabs.setVisibility(View.GONE);
    }
    private void switchStudioTab(int page){
        if(exporting||cameraScreen!=null||settingsScreen!=null
            ||presetLibrary!=null||exportScreen!=null)return;
        if(page==activeStudioTab)return;
        if(galleryScreen!=null)galleryScreen.hidePhotoActions();
        if(projectLibrary!=null&&projectLibrary.dismissModal())return;
        if(page!=StudioTabBar.PROJECTS)closeProjectLibrary();
        if(page==StudioTabBar.HOME)showHome();
        else if(page==StudioTabBar.GALLERY)showGallery();
        else showProjects();
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        int type=event.getActionMasked();
        boolean eligible=activeStudioTab>=0&&studioTabs!=null
            &&studioTabs.getVisibility()==View.VISIBLE
            &&cameraScreen==null&&settingsScreen==null&&presetLibrary==null
            &&exportScreen==null&&!exporting&&exportResult==null&&imagePickerScreen==null
            &&(galleryScreen==null||!galleryScreen.dismissPhotoActionsPending())
            &&(projectLibrary==null||!projectLibrary.hasModal());
        if(type==MotionEvent.ACTION_DOWN){
            studioSwipeX=event.getX();studioSwipeY=event.getY();
            studioGestureArmed=eligible;
        }else if(type==MotionEvent.ACTION_POINTER_DOWN||type==MotionEvent.ACTION_CANCEL){
            studioGestureArmed=false;
        }else if(type==MotionEvent.ACTION_UP&&studioGestureArmed&&eligible){
            studioGestureArmed=false;
            float dx=event.getX()-studioSwipeX;
            float dy=event.getY()-studioSwipeY;
            if(Math.abs(dx)>Math.max(dp(68),getResources().getDisplayMetrics().widthPixels*.19f)
                &&Math.abs(dx)>Math.abs(dy)*1.55f){
                switchStudioTab(Math.max(0,Math.min(2,
                    activeStudioTab+(dx<0?1:-1))));
                return true;
            }
        }
        return super.dispatchTouchEvent(event);
    }
    private void showHome(){
        closeOrbital();
        if(homeScreen==null){
            homeScreen=new HomeScreen(this,this::showImagePicker,this::showCamera,this::showProjects,
                this::showSettings,()->{
                String id=getPreferences(0).getString("lastProject",null);
                if(id!=null)loadProject(id,false);else showImagePicker();
            });
            appBackground.addView(homeScreen,new FrameLayout.LayoutParams(-1,-1));
        }
        closeGallery();
        homeScreen.setVisibility(View.VISIBLE);homeScreen.bringToFront();
        revealStudioTabs(StudioTabBar.HOME);
        homeScreen.hostResumed(homeHostResumed);
        homeScreen.covered(settingsScreen!=null||projectLibrary!=null);
        files.execute(()->{
            ArrayList<ProjectStore.Project> projects=store.list();
            String id=getPreferences(0).getString("lastProject",null),name=null;
            for(ProjectStore.Project p:projects)if(p.id.equals(id)){name=p.name;break;}
            final String recentName=name;
            runOnUiThread(()->{if(!destroyed&&homeScreen!=null)homeScreen.setRecent(recentName);});
        });
    }
    private void closeHome(){if(homeScreen!=null)homeScreen.setVisibility(View.GONE);}
    private void showSettings(){
        if(settingsScreen!=null)return;
        closeOrbital();hideStudioTabs();
        SettingsScreen screen=new SettingsScreen(this,new SettingsScreen.Listener(){
            @Override public void close(){closeSettings();}
            @Override public void panelResizeChanged(boolean enabled){
                workspace.setSheetResizeEnabled(enabled);
                panel.panelResizeEnabled(enabled);
            }
        });
        settingsScreen=screen;
        if(homeScreen!=null)homeScreen.covered(true);
        appBackground.addView(screen,new FrameLayout.LayoutParams(-1,-1));
        screen.bringToFront();
    }
    private void closeSettings(){
        if(settingsScreen!=null){
            appBackground.removeView(settingsScreen);settingsScreen=null;
            if(homeScreen!=null)homeScreen.covered(projectLibrary!=null);
            if(homeScreen!=null&&homeScreen.getVisibility()==View.VISIBLE)
                revealStudioTabs(StudioTabBar.HOME);
        }
    }
    private void toggleFullscreen(){workspace.fullscreen(!workspace.isFullscreen());}
    public EditorDocument document(){return document;}
    public void begin(){history.begin(document);}
    public void change(int id,float value){
        document.values[id]=AdjustmentSpec.clamp(id,value);document.disabled[id]=false;
        if(id>=44&&id<50)document.asciiEnabled=true;
        panel.bind(id);publish();
    }
    public void end(){history.commit(document);updateActions();saveProject(false);}
    public void toggle(int id){begin();document.disabled[id]=!document.disabled[id];panel.bind(id);publish();end();}
    public void updated(){publish();}
    private void mutate(Runnable r){begin();r.run();history.commit(document);restored();}
    private void restored(){panel.rebuild();preview.resetViewport();publish();updateActions();saveProject(false);}
    private void publish(){if(preview==null)return;preview.setEditState(document.gpu(comparing));compareLabel.setVisibility(comparing?View.VISIBLE:View.GONE);}
    private void updateActions(){boolean ready=original!=null&&!loading;save.setEnabled(ready&&!exporting);undo.setEnabled(ready&&history.canUndo());redo.setEnabled(ready&&history.canRedo());for(View v:new View[]{save,undo,redo})v.setAlpha(v.isEnabled()?1:.32f);}
    private int sourceWidth(){return source!=null?source.width:original==null?1:original.getWidth();}
    private int sourceHeight(){return source!=null?source.height:original==null?1:original.getHeight();}
    private void sizeStatus(){if(original!=null){int[] size=document.outputSize(sourceWidth(),sourceHeight(),maxTexture);status.setText(size[0]+" × "+size[1]+"  ·  segure a foto para comparar");}}
    public void action(String a){switch(a){
        case "Recortar":showCrop();break;
        case "Redimensionar":showExport();break;
        case "Girar 90°":mutate(()->document.rotation=(document.rotation+1)%4);sizeStatus();break;
        case "Espelhar horizontal":mutate(()->document.flipX=!document.flipX);break;
        case "Espelhar vertical":mutate(()->document.flipY=!document.flipY);break;
        case "Redimensionar / exportar":showExport();break;
        case "Presets":showPresets();break;
        case "Copiar ajustes":clipboard=document.copy();toast("Ajustes copiados");break;
        case "Colar ajustes":if(clipboard!=null)mutate(()->document.copyLook(clipboard));else toast("Copie os ajustes de uma imagem primeiro");break;
        case "Salvar projeto":saveProject(true);break;
        case "Meus projetos":showProjects();break;
        case "Neon":mutate(()->document.copyLook(ProjectStore.builtins().get(3).document));break;
    }}
    private void showMenu(View anchor){PopupMenu p=new PopupMenu(this,anchor);String[] items={"Abrir imagem","Câmera","Presets","Salvar projeto","Meus projetos","Copiar ajustes","Colar ajustes","Comparar original","Tela cheia","Restaurar ajustes","Restaurar transformação"};for(String x:items)p.getMenu().add(x);p.setOnMenuItemClickListener(item->{String t=item.getTitle().toString();switch(t){case "Abrir imagem":showImagePicker();break;case "Câmera":showCamera();break;case "Comparar original":comparing=!comparing;publish();break;case "Tela cheia":toggleFullscreen();break;case "Restaurar ajustes":mutate(()->document.copyLook(new EditorDocument()));break;case "Restaurar transformação":mutate(()->{document.left=document.top=0;document.right=document.bottom=1;document.rotation=0;document.flipX=document.flipY=false;});sizeStatus();break;default:action(t);}return true;});p.show();}
    private void showCrop(){if(original==null||loading)return;if(cropEditor!=null)return;
        cropEditor=new CropEditor(this,original,document,sourceWidth(),sourceHeight(),new CropEditor.Listener(){public void cancel(){closeCrop();}public void apply(Rect ignored){RectF rect=cropEditor.normalizedRect();mutate(()->{document.left=rect.left;document.top=rect.top;document.right=rect.right;document.bottom=rect.bottom;});closeCrop();sizeStatus();}});appBackground.addView(cropEditor,new FrameLayout.LayoutParams(-1,-1));}
    private void closeCrop(){if(cropEditor!=null){appBackground.removeView(cropEditor);cropEditor=null;}}
    private void requestFirstLaunchGalleryPermission(){
        if(destroyed||isFinishing())return;
        android.content.SharedPreferences prefs=getPreferences(0);
        if(prefs.getBoolean("gallery_permission_first_launch_asked",false))return;
        prefs.edit().putBoolean("gallery_permission_first_launch_asked",true).apply();
        if(!hasGalleryPermission())grantGalleryPermission();
    }
    private boolean hasGalleryPermission(){if(Build.VERSION.SDK_INT>=34)return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)==PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)==PackageManager.PERMISSION_GRANTED;if(Build.VERSION.SDK_INT>=33)return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)==PackageManager.PERMISSION_GRANTED;return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;}
    private void grantGalleryPermission(){
        if(hasGalleryPermission())return;
        if(Build.VERSION.SDK_INT>=34)
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED},GALLERY_PERMISSION);
        else
            requestPermissions(new String[]{Build.VERSION.SDK_INT>=33
                ?Manifest.permission.READ_MEDIA_IMAGES:Manifest.permission.READ_EXTERNAL_STORAGE},
                GALLERY_PERMISSION);
    }
    private void showCamera(){
        if(cameraScreen!=null)return;
        hideStudioTabs();
        closeOrbital();closeSettings();closeGallery();closeHome();
        orientationBeforeCamera=getRequestedOrientation();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        cameraScreen=new CameraScreen(this,new CameraScreen.Listener(){
            @Override public void close(){closeCamera();showHome();}
            @Override public void edit(Uri uri){closeCamera();load(uri);}
            @Override public void gallery(){closeCamera();showGallery();}
        });
        appBackground.addView(cameraScreen,new FrameLayout.LayoutParams(-1,-1));
        cameraScreen.bringToFront();
        if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)
            cameraScreen.start();
        else requestPermissions(new String[]{Manifest.permission.CAMERA},CAMERA_PERMISSION);
    }
    private void closeCamera(){
        if(cameraScreen==null)return;
        CameraScreen screen=cameraScreen;cameraScreen=null;
        screen.dispose();appBackground.removeView(screen);
        if(!isFinishing())setRequestedOrientation(orientationBeforeCamera);
    }
    private void showGallery(){
        if(galleryScreen==null){
            galleryScreen=new GalleryScreen(this,new GalleryScreen.Listener(){
                @Override public void onPhoto(Uri uri){hideStudioTabs();load(uri);}
                @Override public void onShare(Uri uri){shareGallery(uri);}
                @Override public void onViewerState(boolean showing){
                    if(showing)hideStudioTabs();
                    else revealStudioTabs(StudioTabBar.GALLERY);
                }
                @Override public void onSystemGallery(){systemPicker();}
                @Override public void onGrantPermission(){grantGalleryPermission();}
                @Override public void onClose(){switchStudioTab(StudioTabBar.HOME);}
                @Override public void onProjects(){switchStudioTab(StudioTabBar.PROJECTS);}
            });
            appBackground.addView(galleryScreen,new FrameLayout.LayoutParams(-1,-1));
        }
        closeHome();
        galleryScreen.setVisibility(View.VISIBLE);galleryScreen.bringToFront();
        galleryScreen.refresh(hasGalleryPermission());
        revealStudioTabs(StudioTabBar.GALLERY);
    }
    private void closeGallery(){if(galleryScreen!=null)galleryScreen.setVisibility(View.GONE);}
    /** Picker route is independent: a single photo tap always imports directly. */
    private void showImagePicker(){
        if(imagePickerScreen!=null)return;
        hideStudioTabs();closeOrbital();
        imagePickerScreen=new GalleryScreen(this,new GalleryScreen.Listener(){
            @Override public void onPhoto(Uri uri){
                closeImagePicker();
                load(uri);
            }
            @Override public void onSystemGallery(){systemPicker();}
            @Override public void onGrantPermission(){grantGalleryPermission();}
            @Override public void onClose(){closeImagePicker();}
            @Override public void onProjects(){closeImagePicker();showProjects();}
            @Override public void onShare(Uri uri){shareGallery(uri);}
        },true);
        appBackground.addView(imagePickerScreen,new FrameLayout.LayoutParams(-1,-1));
        imagePickerScreen.bringToFront();
        imagePickerScreen.refresh(hasGalleryPermission());
    }
    private void closeImagePicker(){
        if(imagePickerScreen==null)return;
        GalleryScreen closing=imagePickerScreen;imagePickerScreen=null;
        appBackground.removeView(closing);closing.dispose();
        if(homeScreen!=null&&homeScreen.getVisibility()==View.VISIBLE)
            revealStudioTabs(StudioTabBar.HOME);
    }
    void requestGalleryDownloadPermission(MediaLibrary.Photo photo){
        if(Build.VERSION.SDK_INT>=29){
            if(galleryScreen!=null)galleryScreen.downloadPhoto(photo);
            return;
        }
        pendingGalleryCopy=photo;
        requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
            GALLERY_COPY_PERMISSION);
    }
    private void systemPicker(){Intent intent=Build.VERSION.SDK_INT>=33?new Intent(MediaStore.ACTION_PICK_IMAGES):new Intent(Intent.ACTION_PICK,MediaStore.Images.Media.EXTERNAL_CONTENT_URI);intent.setType("image/*");intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{startActivityForResult(intent,1);}catch(ActivityNotFoundException e){toast("Galeria do sistema indisponível");}}
    void load(Uri uri){saveProject(false);int token=loads.incrementAndGet();if(importTask!=null)importTask.cancel(true);loading=true;status.setText("Abrindo imagem…");updateActions();
        importTask=files.submit(()->{String id=ProjectStore.newId();try{File path=store.importOriginal(uri,id);ImageSource decoded=ImageSource.open(path,Math.min(maxTexture,1600));if(Thread.currentThread().isInterrupted()){decoded.preview.recycle();store.delete(id);return;}
            String name="Foto "+new SimpleDateFormat("dd/MM HH:mm",Locale.getDefault()).format(new Date());store.save(id,name,new EditorDocument(),new JSONObject());
            runOnUiThread(()->{if(destroyed||token!=loads.get()){decoded.preview.recycle();return;}projectId=id;projectName=name;document=new EditorDocument();history.clear();setSource(decoded);saveProject(false);askProjectVaultOnFirstImport();});
        }catch(Exception|OutOfMemoryError e){try{store.delete(id);}catch(Exception ignored){}loadFailed(token,"Não foi possível abrir. Confira o formato e o espaço disponível.");}});
    }
    private void loadFailed(int token,String msg){runOnUiThread(()->{if(!destroyed&&token==loads.get()){loading=false;updateActions();status.setText(msg);toast(msg);}});}
    private void setSource(ImageSource s){closeImagePicker();hideStudioTabs();source=s;original=s.preview;loading=false;preview.setImage(original,s.width,s.height);empty.setVisibility(View.GONE);closeGallery();closeHome();panel.rebuild();publish();sizeStatus();updateActions();getPreferences(0).edit().putString("lastProject",projectId).apply();}
    void setImage(Bitmap bitmap){closeImagePicker();hideStudioTabs();source=null;original=bitmap;preview.setImage(bitmap);loading=false;empty.setVisibility(View.GONE);closeGallery();closeHome();panel.rebuild();publish();sizeStatus();updateActions();}
    private void loadProject(String id,boolean retain){int token=loads.incrementAndGet();loading=true;updateActions();status.setText("Abrindo projeto…");files.execute(()->{try{ProjectStore.Project p=store.read(id);ImageSource decoded=ImageSource.open(p.source,Math.min(maxTexture,1600));runOnUiThread(()->{if(destroyed||token!=loads.get()){decoded.preview.recycle();return;}projectId=p.id;projectName=p.name;if(!retain){document=p.document;try{history.restore(p.history);}catch(JSONException ignored){history.clear();}}setSource(decoded);});}catch(Exception|OutOfMemoryError e){loadFailed(token,"Projeto indisponível ou arquivo inválido.");}});}
    private void saveProject(boolean explicit){if(projectId==null||original==null){if(explicit)toast("Abra uma foto para criar um projeto");return;}final String id=projectId,name=projectName;final EditorDocument snapshot=document.copy();final JSONObject h;try{h=history.toJson();}catch(JSONException e){return;}
        files.execute(()->{try{
            store.save(id,name,snapshot,h);
            boolean protectedProject=vault.attached();
            if(protectedProject)vault.backup(store,id);
            if(explicit)runOnUiThread(()->toast(protectedProject?
                "Projeto salvo também na pasta compartilhada":"Projeto salvo apenas no app. Proteja em Meus projetos."));}catch(Exception e){runOnUiThread(()->{if(!destroyed)toast("Não foi possível salvar o projeto. Verifique o espaço.");});}});
    }
    private void showProjects(){
        if(projectLibrary!=null){revealStudioTabs(StudioTabBar.PROJECTS);return;}
        projectsFromEditor=activeStudioTab<0
            &&(homeScreen==null||homeScreen.getVisibility()!=View.VISIBLE);
        closeGallery();
        final ProjectLibraryScreen screen=new ProjectLibraryScreen(this,new ProjectLibraryScreen.Listener(){
            @Override public void close(){
                if(projectsFromEditor){closeProjectLibrary();hideStudioTabs();}
                else switchStudioTab(StudioTabBar.HOME);
            }
            @Override public void create(){closeProjectLibrary();showGallery();}
            @Override public void chooseVault(){chooseProjectVault();}
            @Override public void open(ProjectStore.Project project){
                closeProjectLibrary();
                saveProject(false);
                loadProject(project.id,false);
            }
            @Override public void rename(ProjectStore.Project project,String name){
                files.execute(()->{
                    try{
                        ProjectStore.Project current=store.read(project.id);
                        store.save(project.id,name,current.document,
                            current.history==null?new JSONObject():current.history);
                        if(vault.attached())vault.backup(store,project.id);
                        runOnUiThread(()->{if(destroyed)return;
                            if(project.id.equals(projectId))projectName=name;
                            refreshProjectLibrary();
                        });
                    }catch(Exception e){runOnUiThread(()->{if(!destroyed)toast("Não foi possível renomear o projeto");});}
                });
            }
            @Override public void delete(ProjectStore.Project project){
                files.execute(()->{
                    try{
                        vault.delete(project.id);
                        store.delete(project.id);
                        runOnUiThread(()->{if(destroyed)return;
                            if(project.id.equals(projectId))projectId=null;
                            refreshProjectLibrary();
                        });
                    }catch(Exception e){runOnUiThread(()->{if(!destroyed)toast("Não foi possível excluir o projeto");});}
                });
            }
        });
        projectLibrary=screen;
        if(homeScreen!=null)homeScreen.covered(true);
        appBackground.addView(screen,new FrameLayout.LayoutParams(-1,-1));
        screen.bringToFront();
        revealStudioTabs(StudioTabBar.PROJECTS);
        refreshProjectLibrary();
    }
    private void askProjectVaultOnFirstImport(){
        if(vault.attached())return;
        android.content.SharedPreferences preferences=getPreferences(0);
        if(preferences.getBoolean("project_vault_setup_asked",false))return;
        preferences.edit().putBoolean("project_vault_setup_asked",true).apply();
        // First imported photo is safe in private storage before Android asks
        // for an independent shared location that survives uninstall.
        appBackground.postDelayed(()->{
            if(!destroyed&&!isFinishing()&&projectId!=null
                &&settingsScreen==null&&projectLibrary==null)chooseProjectVault();
        },650);
    }
    /** Android's SAF picker is necessary: after uninstall the old grant is lost.
     * Re-select the SAME parent folder to recover its Noise! Projects data.
     */
    private void chooseProjectVault(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
            |Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            |Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try{startActivityForResult(intent,VAULT_REQUEST);}
        catch(ActivityNotFoundException e){toast("Seletor de pasta indisponível");}
    }
    private void connectProjectVault(Uri selected){
        if(projectLibrary!=null)projectLibrary.showLoading();
        files.execute(()->{
            try{
                vault.attach(selected);
                int restored=vault.recover(store);
                int saved=vault.backupAll(store);
                runOnUiThread(()->{
                    if(destroyed)return;
                    if(projectLibrary!=null)projectLibrary.vaultStatus(true);
                    refreshProjectLibrary();
                    toast(restored>0?restored+" projetos recuperados; backup ativo":
                        "Pasta protegida. Projetos sincronizados: "+saved);
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    if(destroyed)return;
                    refreshProjectLibrary();
                    toast("Não foi possível acessar a pasta escolhida");
                });
            }
        });
    }
    private void refreshProjectLibrary(){
        if(projectLibrary==null)return;
        final ProjectLibraryScreen target=projectLibrary;
        target.showLoading();
        // Serialized after any pending saveProject(...) task.
        files.execute(()->{
            ArrayList<ProjectStore.Project> projects=store.list();
            runOnUiThread(()->{if(!destroyed&&projectLibrary==target)
                target.setProjects(projects);});
        });
    }
    private void closeProjectLibrary(){
        if(projectLibrary==null)return;
        ProjectLibraryScreen screen=projectLibrary;projectLibrary=null;
        appBackground.removeView(screen);screen.dispose();
        if(homeScreen!=null)homeScreen.covered(settingsScreen!=null);
    }
    private interface Named{void set(String name);}
    private void askName(String title,String initial,Named callback){EditText input=new EditText(this);input.setSingleLine(true);input.setText(initial);input.setSelectAllOnFocus(true);input.setPadding(dp(24),dp(12),dp(24),dp(12));AlertDialog d=new AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("Cancelar",null).setPositiveButton("Salvar",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(w->{String name=input.getText().toString().trim();if(name.isEmpty()||name.length()>60){input.setError("Use de 1 a 60 caracteres");return;}callback.set(name);d.dismiss();}));d.show();}
    private void showPresets(){
        if(original==null){toast("Abra uma imagem para visualizar presets");return;}
        if(presetLibrary!=null)return;
        files.execute(()->{
            ArrayList<ProjectStore.Preset> all=ProjectStore.builtins();
            all.addAll(store.presets());
            runOnUiThread(()->{
                if(destroyed||presetLibrary!=null)return;
                PresetDialog library=new PresetDialog(this,store,original,document,all,
                    new PresetDialog.Listener(){
                        @Override public void apply(EditorDocument look){
                            mutate(()->document.copyLook(look));
                        }
                        @Override public void save(String name){
                            final ProjectStore.Preset preset=new ProjectStore.Preset(
                                ProjectStore.newId(),name,document.copy());
                            files.execute(()->{
                                try{store.savePreset(preset);
                                    runOnUiThread(()->{if(!destroyed)toast("Preset salvo");});
                                }catch(Exception e){
                                    runOnUiThread(()->{if(!destroyed)toast("Falha ao salvar preset");});
                                }
                            });
                        }
                    });
                presetLibrary=library;
                library.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
                    public void onViewAttachedToWindow(View v){}
                    public void onViewDetachedFromWindow(View v){
                        if(presetLibrary==library)presetLibrary=null;
                    }
                });
                library.show();
            });
        });
    }
    private void showExport(){
        if(original==null||loading||exporting||exportScreen!=null)return;
        hideStudioTabs();
        exportScreen=new ResizeDialog(this,original,document,sourceWidth(),sourceHeight(),maxTexture,
            (w,h,jpeg,quality,exportNow)->{
                int[] nativeSize=document.nativeSize(sourceWidth(),sourceHeight());
                mutate(()->{
                    // Preserve the legacy JSON field, but explicit dimensions
                    // take precedence in the V1.28 output model.
                    document.outputWidth=w==nativeSize[0]&&h==nativeSize[1]?0:w;
                    document.outputHeight=w==nativeSize[0]&&h==nativeSize[1]?0:h;
                    document.outputLongEdge=0;
                    document.exportFormat=exportScreen!=null?
                        exportScreen.selectedFormat():(jpeg?"jpeg":"png");
                    document.jpeg=document.exportFormat.equals("jpeg");
                    document.jpegQuality=quality;
                });
                sizeStatus();
                exportScreen=null;
                if(exportNow)chooseOutput();
            });
        exportScreen.show();
    }
    private void chooseOutput(){if(Build.VERSION.SDK_INT<29&&checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},SAVE_PERMISSION);return;}export(null);}
    private void showExportProgress(){
        hideExportProgress();
        exportProgress=new ExportProgressOverlay(this);
        appBackground.addView(exportProgress,new FrameLayout.LayoutParams(-1,-1));
        exportProgress.bringToFront();
        exportProgress.requestFocus();
    }
    private void hideExportProgress(){
        if(exportProgress!=null){
            appBackground.removeView(exportProgress);
            exportProgress=null;
        }
    }
    private void export(Uri supplied){
        if(original==null||loading||exporting)return;
        final EditorDocument d=document.copy();
        final ImageSource current=source;
        final Bitmap previewSource=original;
        final int w=sourceWidth(),h=sourceHeight(),limit=maxTexture;
        exporting=true;
        status.setText("Salvando imagem…");
        updateActions();
        showExportProgress();
        exportWorker.execute(()->{
            Bitmap full=null;
            try{
                full=current!=null?ImageSource.decode(current.file,limit):previewSource;
                Uri uri=ExportManager.save(getApplicationContext(),full,d,w,h,limit,supplied);
                runOnUiThread(()->{
                    if(destroyed)return;
                    hideExportProgress();
                    exporting=false;
                    updateActions();
                    status.setText("Salvo em Pictures/Noise!");
                    if(uri!=null&&supplied==null)showExportResult(uri,d.jpeg);
                });
            }catch(Exception|OutOfMemoryError e){
                android.util.Log.e("NoiseExport","Save",e);
                runOnUiThread(()->{
                    if(destroyed)return;
                    hideExportProgress();
                    exporting=false;
                    updateActions();
                    status.setText("Falha ao exportar. Tente uma resolução menor ou libere espaço.");
                    toast("Não foi possível salvar a imagem");
                });
            }finally{
                if(full!=null&&full!=previewSource)full.recycle();
            }
        });
    }
    private void showExportResult(Uri uri,boolean jpeg){
        hideExportResult();
        exportResult=new ExportResultOverlay(this,this::hideExportResult,()->{
            hideExportResult();shareGallery(uri);
        });
        appBackground.addView(exportResult,new FrameLayout.LayoutParams(-1,-1));
        exportResult.bringToFront();
    }
    private void hideExportResult(){
        if(exportResult!=null){
            appBackground.removeView(exportResult);exportResult=null;
        }
    }
    private void shareGallery(Uri uri){
        String type=getContentResolver().getType(uri);
        share(uri,type!=null&&type.equalsIgnoreCase("image/jpeg"));
    }
    private void share(Uri uri,boolean jpeg){String detected=getContentResolver().getType(uri);
        Intent i=new Intent(Intent.ACTION_SEND).setType(detected!=null?detected:
            (jpeg?"image/jpeg":"image/png")).putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);i.setClipData(ClipData.newRawUri("Noise!",uri));startActivity(Intent.createChooser(i,"Compartilhar imagem"));}
    private void toast(String s){if(!destroyed)Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        if(request==1)load(data.getData());
        else if(request==VAULT_REQUEST)connectProjectVault(data.getData());
    }
    @Override public void onRequestPermissionsResult(int request,String[] names,int[] grants){super.onRequestPermissionsResult(request,names,grants);if(request==GALLERY_PERMISSION){
            if(galleryScreen!=null)galleryScreen.refresh(hasGalleryPermission());
            if(imagePickerScreen!=null)imagePickerScreen.refresh(hasGalleryPermission());
        }
        if(request==GALLERY_COPY_PERMISSION){
            MediaLibrary.Photo requested=pendingGalleryCopy;pendingGalleryCopy=null;
            if(requested!=null&&grants.length>0
                &&grants[0]==PackageManager.PERMISSION_GRANTED&&galleryScreen!=null)
                galleryScreen.downloadPhoto(requested);
            return;
        }if(request==CAMERA_PERMISSION){
            if(cameraScreen!=null){if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)cameraScreen.start();else toast("Acesso à câmera não autorizado");}
            return;
        }
        if(request==CAMERA_STORAGE_PERMISSION){if(grants.length==0||grants[0]!=PackageManager.PERMISSION_GRANTED)toast("Permissão de armazenamento necessária para salvar fotos");return;}
        if(request==SAVE_PERMISSION){if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)chooseOutput();else toast("Permissão necessária para salvar na galeria");}}
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);try{b.putString("document",document.toJson().toString());b.putString("history",history.toJson().toString());}catch(JSONException ignored){}b.putString("project",projectId);b.putInt("category",panel.category);saveProject(false);}
    @Override public void onBackPressed(){
        if(imagePickerScreen!=null){closeImagePicker();return;}
        // The save transaction is not cancellable; keep progress visible.
        if(exporting)return;
        if(exportResult!=null){hideExportResult();return;}
        if(exportScreen!=null){exportScreen.dismiss();exportScreen=null;return;}
        if(cameraScreen!=null){closeCamera();showHome();return;}
        if(settingsScreen!=null){closeSettings();return;}
        if(presetLibrary!=null){presetLibrary.onBack();return;}
        if(projectLibrary!=null){
            if(!projectLibrary.dismissModal()){
                if(projectsFromEditor){closeProjectLibrary();hideStudioTabs();}
                else switchStudioTab(StudioTabBar.HOME);
            }
            return;
        }
        if(cropEditor!=null){closeCrop();return;}
        if(orbital!=null&&orbital.getParent()!=null){closeOrbital();return;}
        if(homeScreen!=null&&homeScreen.getVisibility()==View.VISIBLE){super.onBackPressed();return;}
        if(galleryScreen!=null&&galleryScreen.getVisibility()==View.VISIBLE){
            if(galleryScreen.dismissPhotoActions())return;
            switchStudioTab(StudioTabBar.HOME);return;
        }
        if(workspace.isFullscreen()){workspace.fullscreen(false);return;}
        showHome();
    }
    @Override public void onConfigurationChanged(Configuration configuration){
        super.onConfigurationChanged(configuration);
        // Portrait camera stays in place. Sensor orientation is read separately
        // by CameraScreen and applied only to the captured JPEG.
    }
    @Override protected void onResume(){super.onResume();homeHostResumed=true;if(homeScreen!=null)homeScreen.hostResumed(true);if(preview!=null)preview.onResume();if(galleryScreen!=null&&galleryScreen.getVisibility()==View.VISIBLE)galleryScreen.refresh(hasGalleryPermission());if(cameraScreen!=null)cameraScreen.start();}
    @Override protected void onPause(){homeHostResumed=false;if(homeScreen!=null)homeScreen.hostResumed(false);comparing=false;publish();saveProject(false);preview.onPause();if(cameraScreen!=null)cameraScreen.stop();super.onPause();}
    @Override protected void onDestroy(){destroyed=true;
        if(exportScreen!=null){exportScreen.dismiss();exportScreen=null;}
        hideExportResult();hideExportProgress();closeSettings();closeCamera();
        if(presetLibrary!=null){presetLibrary.dismiss();presetLibrary=null;}
        closeProjectLibrary();loads.incrementAndGet();if(importTask!=null)importTask.cancel(true);closeCrop();preview.dispose();files.shutdown();exportWorker.shutdown();if(galleryScreen!=null)galleryScreen.dispose();closeImagePicker();super.onDestroy();}
}
