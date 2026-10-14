package com.discnet.noise;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
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
    private String projectId,projectName="Sem título";
    private ImageSource source;
    private Bitmap original;
    private EditorSurface preview;
    private FrameLayout appBackground,editorStage;
    private EditorWorkspace workspace;
    private EditorPanel panel;
    private GalleryScreen galleryScreen;
    private HomeScreen homeScreen;
    private OrbitalSelector orbital;
    private CropEditor cropEditor;
    private LinearLayout topBar;
    private HorizontalScrollView categoryScroll;
    private final TextView[] tabs=new TextView[7];
    private ImageView undo,redo,save;
    private TextView status,empty,compareLabel;
    private boolean loading,exporting,destroyed,comparing;
    private int maxTexture=4096;
    private static final int GALLERY_PERMISSION=73,SAVE_PERMISSION=74;
    private int dp(float n){return EditorTheme.dp(this,n);}
    private TextView text(String t,int size,int color){TextView v=new TextView(this);v.setTypeface(NoiseTypography.regular(this));v.setText(t);v.setTextSize(size);v.setTextColor(color);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private TextView button(String t,boolean primary){TextView v=text(t,14,primary?0xff19121f:EditorTheme.INK);v.setGravity(Gravity.CENTER);EditorTheme.button(v,primary);v.setMinHeight(dp(48));v.setFocusable(true);return v;}
    private LinearLayout vertical(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);store=new ProjectStore(this);getWindow().setStatusBarColor(0xff0c0b10);getWindow().setNavigationBarColor(0xff0c0b10);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        appBackground=new SwipeNavigationHost(this,new SwipeNavigationHost.Handler(){
            @Override public boolean allow(float x,float y){return canSwipeForTools(x,y);}
            @Override public void start(){showOrbital(false,true);}
            @Override public void progress(float amount){
                if(orbital!=null)orbital.setReveal(amount);
            }
            @Override public void finish(boolean open){
                if(orbital==null)return;
                OrbitalSelector active=orbital;
                if(!open)orbital=null;
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
        ImageView menu=addIcon(topBar,IconArt.MORE,"Mais opções",()->{});menu.setOnClickListener(v->showMenu(menu));
        editorStage=new FrameLayout(this);preview=new EditorSurface(this,new EditorSurface.Listener(){
            public void ready(int maximum){maxTexture=Math.min(4096,maximum);}
            public void failed(String message){if(!destroyed){status.setText(message);Toast.makeText(MainActivity.this,message,Toast.LENGTH_LONG).show();}}
        });editorStage.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        preview.gestures(new EditorSurface.Gestures(){public void compare(boolean active){comparing=active;publish();}public void fullscreen(){toggleFullscreen();}});
        empty=text("Noise!\n\nAbrir uma imagem\n\nSeu estúdio, sem distrações",18,EditorTheme.INK);empty.setGravity(Gravity.CENTER);empty.setBackgroundColor(0xff0c0b10);empty.setFocusable(true);editorStage.addView(empty,new FrameLayout.LayoutParams(-1,-1));empty.setOnClickListener(v->showGallery());
        compareLabel=text("Original",12,EditorTheme.LIME);compareLabel.setBackgroundColor(0xff20242d);compareLabel.setPadding(dp(12),dp(6),dp(12),dp(6));FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);cp.topMargin=dp(8);editorStage.addView(compareLabel,cp);compareLabel.setVisibility(View.GONE);
        status=text("",11,EditorTheme.MUTED);status.setMaxLines(2);status.setPadding(dp(12),dp(5),dp(52),dp(5));status.setBackgroundColor(0xff0c0b10);FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);editorStage.addView(status,sp);
        ImageView full=IconArt.button(this,IconArt.FULL,"Alternar tela cheia");FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.BOTTOM|Gravity.RIGHT);editorStage.addView(full,fp);full.setOnClickListener(v->toggleFullscreen());
        panel=new EditorPanel(this,this);installSheetGesture();
        categoryScroll=new HorizontalScrollView(this);categoryScroll.setHorizontalScrollBarEnabled(false);categoryScroll.setFillViewport(true);categoryScroll.setBackgroundColor(0xff17161d);
        LinearLayout strip=new LinearLayout(this);strip.setGravity(Gravity.CENTER_VERTICAL);
        strip.setPadding(dp(18),0,dp(18),0);categoryScroll.addView(strip);
        TextView gestureHint=text("←  ARRASTE NA IMAGEM PARA AS FERRAMENTAS",11,EditorTheme.MUTED);
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
    }
    private ImageView addIcon(LinearLayout parent,int icon,String label,Runnable action){ImageView v=IconArt.button(this,icon,label);parent.addView(v,new LinearLayout.LayoutParams(dp(48),dp(48)));v.setOnClickListener(w->action.run());return v;}
    private void installSheetGesture(){panel.handle.setOnClickListener(v->workspace.cycleSheet());panel.handle.setOnTouchListener(new View.OnTouchListener(){float start;int height;boolean moved;public boolean onTouch(View v,MotionEvent e){
        if(workspace.landscape())return false;
        switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:start=e.getRawY();height=workspace.sheetHeight();moved=false;return true;
            case MotionEvent.ACTION_MOVE:if(Math.abs(e.getRawY()-start)>dp(5))moved=true;if(moved)workspace.resizeSheet(height+Math.round(start-e.getRawY()));return true;
            case MotionEvent.ACTION_UP:if(!moved)v.performClick();return true;case MotionEvent.ACTION_CANCEL:return true;}return false;}});}
    private void selectCategory(int n){n=Math.max(0,Math.min(6,n));panel.select(n);
        for(int i=0;i<tabs.length;i++)tabs[i].setSelected(i==n);
        // A roda se abre exclusivamente por arrasto, sem botao de menu.
    }
    @Override public void openEffects(){showOrbital(true);}
    private boolean canSwipeForTools(float x,float y){
        if(original==null||loading||orbital!=null||cropEditor!=null||preview==null
            ||preview.isZoomed()||workspace==null||workspace.isFullscreen())return false;
        if(homeScreen!=null&&homeScreen.getVisibility()==View.VISIBLE)return false;
        if(galleryScreen!=null&&galleryScreen.getVisibility()==View.VISIBLE)return false;
        // All of the image width is active, including its edges and the toolbar.
        // The gesture also works over the small grip above the tool sheet,
        // not just inside the previous narrow central preview rectangle.
        float y0=appBackground.getPaddingTop()+workspace.getTop();
        float y1=y0+editorStage.getBottom()+dp(74);
        boolean nearSide=x<dp(46)||x>appBackground.getWidth()-dp(46);
        float maxY=y0+workspace.getHeight()-dp(24);
        return x>=0&&x<=appBackground.getWidth()
            &&y>=y0&&((y<=y1)||(nearSide&&y<=maxY));
    }
    private void closeOrbital(){
        if(orbital!=null){OrbitalSelector old=orbital;orbital=null;old.exitTo(1);}
    }
    private void showOrbital(boolean effects){showOrbital(effects,false);}
    private void showOrbital(boolean effects,boolean interactive){
        if(original==null){showGallery();return;}
        OrbitalSelector previous=orbital;
        if(previous!=null)previous.exitTo(effects?-1:1);
        String[] labels=effects?AdjustmentSpec.EFFECTS:AdjustmentSpec.CATEGORIES;
        int[] symbols=effects?new int[]{15,12,10,16,32,38,26,17}:new int[]{0,1,7,10,9,24,32};
        OrbitalSelector wheel=new OrbitalSelector(this,
            effects?"Escolha um\nefeito":"Explore suas\nferramentas",
            effects?"Arraste para percorrer a roda.":"Deslize para explorar todas as categorias.",
            labels,symbols,effects?panel.effect:panel.category,n->{
                if(effects){panel.effect=n;selectCategory(3);}else selectCategory(n);
                orbital=null;
            });
        orbital=wheel;
        wheel.setNavigate(direction->{
            if(direction<0){if(!effects)showOrbital(true);else wheel.finishReveal(true);}
            else if(effects)showOrbital(false);
            else closeOrbital();
        });
        appBackground.addView(wheel,new FrameLayout.LayoutParams(-1,-1));
        wheel.bringToFront();
        if(interactive)wheel.setReveal(0f);
        else wheel.enterFrom(effects?-1:1);
    }
    private void showHome(){
        closeOrbital();
        if(homeScreen==null){
            homeScreen=new HomeScreen(this,this::showGallery,this::showProjects,()->{
                String id=getPreferences(0).getString("lastProject",null);
                if(id!=null)loadProject(id,false);else showGallery();
            });
            appBackground.addView(homeScreen,new FrameLayout.LayoutParams(-1,-1));
        }
        closeGallery();
        homeScreen.setVisibility(View.VISIBLE);homeScreen.bringToFront();
        files.execute(()->{
            ArrayList<ProjectStore.Project> projects=store.list();
            String id=getPreferences(0).getString("lastProject",null),name=null;
            for(ProjectStore.Project p:projects)if(p.id.equals(id)){name=p.name;break;}
            final String recentName=name;
            runOnUiThread(()->{if(!destroyed&&homeScreen!=null)homeScreen.setRecent(recentName);});
        });
    }
    private void closeHome(){if(homeScreen!=null)homeScreen.setVisibility(View.GONE);}
    private void toggleFullscreen(){workspace.fullscreen(!workspace.isFullscreen());}
    public EditorDocument document(){return document;}
    public void begin(){history.begin(document);}
    public void change(int id,float value){document.values[id]=AdjustmentSpec.clamp(id,value);document.disabled[id]=false;panel.bind(id);publish();}
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
    private void showMenu(View anchor){PopupMenu p=new PopupMenu(this,anchor);String[] items={"Abrir imagem","Presets","Salvar projeto","Meus projetos","Copiar ajustes","Colar ajustes","Comparar original","Tela cheia","Restaurar ajustes","Restaurar transformação"};for(String x:items)p.getMenu().add(x);p.setOnMenuItemClickListener(item->{String t=item.getTitle().toString();switch(t){case "Abrir imagem":showGallery();break;case "Comparar original":comparing=!comparing;publish();break;case "Tela cheia":toggleFullscreen();break;case "Restaurar ajustes":mutate(()->document.copyLook(new EditorDocument()));break;case "Restaurar transformação":mutate(()->{document.left=document.top=0;document.right=document.bottom=1;document.rotation=0;document.flipX=document.flipY=false;});sizeStatus();break;default:action(t);}return true;});p.show();}
    private void showCrop(){if(original==null||loading)return;if(cropEditor!=null)return;
        cropEditor=new CropEditor(this,original,document,new CropEditor.Listener(){public void cancel(){closeCrop();}public void apply(Rect ignored){RectF rect=cropEditor.normalizedRect();mutate(()->{document.left=rect.left;document.top=rect.top;document.right=rect.right;document.bottom=rect.bottom;});closeCrop();sizeStatus();}});appBackground.addView(cropEditor,new FrameLayout.LayoutParams(-1,-1));}
    private void closeCrop(){if(cropEditor!=null){appBackground.removeView(cropEditor);cropEditor=null;}}
    private boolean hasGalleryPermission(){if(Build.VERSION.SDK_INT>=34)return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)==PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)==PackageManager.PERMISSION_GRANTED;if(Build.VERSION.SDK_INT>=33)return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)==PackageManager.PERMISSION_GRANTED;return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;}
    private void grantGalleryPermission(){if(Build.VERSION.SDK_INT>=34)requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED},GALLERY_PERMISSION);else requestPermissions(new String[]{Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_IMAGES:Manifest.permission.READ_EXTERNAL_STORAGE},GALLERY_PERMISSION);}
    private void showGallery(){if(galleryScreen==null){galleryScreen=new GalleryScreen(this,new GalleryScreen.Listener(){public void onPhoto(Uri uri){load(uri);}public void onSystemGallery(){systemPicker();}public void onGrantPermission(){grantGalleryPermission();}public void onClose(){closeGallery();if(original==null)showHome();}public void onProjects(){closeGallery();showProjects();}});appBackground.addView(galleryScreen,new FrameLayout.LayoutParams(-1,-1));}closeHome();galleryScreen.setVisibility(View.VISIBLE);galleryScreen.bringToFront();galleryScreen.refresh(hasGalleryPermission());}
    private void closeGallery(){if(galleryScreen!=null)galleryScreen.setVisibility(View.GONE);}
    private void systemPicker(){Intent intent=Build.VERSION.SDK_INT>=33?new Intent(MediaStore.ACTION_PICK_IMAGES):new Intent(Intent.ACTION_PICK,MediaStore.Images.Media.EXTERNAL_CONTENT_URI);intent.setType("image/*");intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{startActivityForResult(intent,1);}catch(ActivityNotFoundException e){toast("Galeria do sistema indisponível");}}
    void load(Uri uri){saveProject(false);int token=loads.incrementAndGet();if(importTask!=null)importTask.cancel(true);loading=true;status.setText("Abrindo imagem…");updateActions();
        importTask=files.submit(()->{String id=ProjectStore.newId();try{File path=store.importOriginal(uri,id);ImageSource decoded=ImageSource.open(path,Math.min(maxTexture,1600));if(Thread.currentThread().isInterrupted()){decoded.preview.recycle();store.delete(id);return;}
            String name="Foto "+new SimpleDateFormat("dd/MM HH:mm",Locale.getDefault()).format(new Date());store.save(id,name,new EditorDocument(),new JSONObject());
            runOnUiThread(()->{if(destroyed||token!=loads.get()){decoded.preview.recycle();return;}projectId=id;projectName=name;document=new EditorDocument();history.clear();setSource(decoded);saveProject(false);});
        }catch(Exception|OutOfMemoryError e){try{store.delete(id);}catch(Exception ignored){}loadFailed(token,"Não foi possível abrir. Confira o formato e o espaço disponível.");}});
    }
    private void loadFailed(int token,String msg){runOnUiThread(()->{if(!destroyed&&token==loads.get()){loading=false;updateActions();status.setText(msg);toast(msg);}});}
    private void setSource(ImageSource s){source=s;original=s.preview;loading=false;preview.setImage(original,s.width,s.height);empty.setVisibility(View.GONE);closeGallery();closeHome();panel.rebuild();publish();sizeStatus();updateActions();getPreferences(0).edit().putString("lastProject",projectId).apply();}
    void setImage(Bitmap bitmap){source=null;original=bitmap;preview.setImage(bitmap);loading=false;empty.setVisibility(View.GONE);closeGallery();closeHome();panel.rebuild();publish();sizeStatus();updateActions();}
    private void loadProject(String id,boolean retain){int token=loads.incrementAndGet();loading=true;updateActions();status.setText("Abrindo projeto…");files.execute(()->{try{ProjectStore.Project p=store.read(id);ImageSource decoded=ImageSource.open(p.source,Math.min(maxTexture,1600));runOnUiThread(()->{if(destroyed||token!=loads.get()){decoded.preview.recycle();return;}projectId=p.id;projectName=p.name;if(!retain){document=p.document;try{history.restore(p.history);}catch(JSONException ignored){history.clear();}}setSource(decoded);});}catch(Exception|OutOfMemoryError e){loadFailed(token,"Projeto indisponível ou arquivo inválido.");}});}
    private void saveProject(boolean explicit){if(projectId==null||original==null){if(explicit)toast("Abra uma foto para criar um projeto");return;}final String id=projectId,name=projectName;final EditorDocument snapshot=document.copy();final JSONObject h;try{h=history.toJson();}catch(JSONException e){return;}
        files.execute(()->{try{store.save(id,name,snapshot,h);if(explicit)runOnUiThread(()->toast("Projeto salvo no aparelho"));}catch(Exception e){runOnUiThread(()->{if(!destroyed)toast("Não foi possível salvar o projeto. Verifique o espaço.");});}});
    }
    private void showProjects(){files.execute(()->{ArrayList<ProjectStore.Project> projects=store.list();runOnUiThread(()->{if(destroyed)return;if(projects.isEmpty()){toast("Abra uma imagem para criar seu primeiro projeto");return;}String[] names=new String[projects.size()];for(int i=0;i<names.length;i++)names[i]=projects.get(i).name;new AlertDialog.Builder(this).setTitle("Projetos no aparelho").setItems(names,(dlg,n)->{ProjectStore.Project p=projects.get(n);new AlertDialog.Builder(this).setTitle(p.name).setItems(new String[]{"Abrir","Renomear","Excluir"},(dialog,which)->{if(which==0){saveProject(false);loadProject(p.id,false);}else if(which==1)askName("Nome do projeto",p.name,name->{files.execute(()->{try{store.save(p.id,name,p.document,p.history);runOnUiThread(()->{if(p.id.equals(projectId))projectName=name;});}catch(Exception e){runOnUiThread(()->toast("Não foi possível renomear"));}});});else new AlertDialog.Builder(this).setMessage("Excluir o projeto e sua cópia privada do original?").setNegativeButton("Cancelar",null).setPositiveButton("Excluir",(v,w)->{if(p.id.equals(projectId))projectId=null;files.execute(()->{try{store.delete(p.id);}catch(Exception e){runOnUiThread(()->toast("Não foi possível excluir"));}});}).show();}).show();}).setNegativeButton("Fechar",null).show();});});}
    private interface Named{void set(String name);}
    private void askName(String title,String initial,Named callback){EditText input=new EditText(this);input.setSingleLine(true);input.setText(initial);input.setSelectAllOnFocus(true);input.setPadding(dp(24),dp(12),dp(24),dp(12));AlertDialog d=new AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("Cancelar",null).setPositiveButton("Salvar",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(w->{String name=input.getText().toString().trim();if(name.isEmpty()||name.length()>60){input.setError("Use de 1 a 60 caracteres");return;}callback.set(name);d.dismiss();}));d.show();}
    private void showPresets(){if(original==null){toast("Abra uma imagem para visualizar presets");return;}files.execute(()->{ArrayList<ProjectStore.Preset> all=ProjectStore.builtins();all.addAll(store.presets());runOnUiThread(()->{if(!destroyed)new PresetDialog(this,store,original,document,all,new PresetDialog.Listener(){public void apply(EditorDocument look){mutate(()->document.copyLook(look));}public void save(){askName("Novo preset","Meu estilo",name->{ProjectStore.Preset p=new ProjectStore.Preset(ProjectStore.newId(),name,document.copy());files.execute(()->{try{store.savePreset(p);runOnUiThread(()->toast("Preset salvo"));}catch(Exception e){runOnUiThread(()->toast("Falha ao salvar preset"));}});});}}).show();});});}
    private void showExport(){if(original==null||loading||exporting)return;LinearLayout form=vertical();form.setPadding(dp(24),dp(8),dp(24),dp(12));
        Spinner format=new Spinner(this);format.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"PNG · mantém transparência","JPEG · fundo branco"}));format.setSelection(document.jpeg?1:0);form.addView(format,new LinearLayout.LayoutParams(-1,dp(52)));
        TextView label=text("Lado maior (px) · proporção preservada",13,EditorTheme.MUTED);form.addView(label);EditText edge=new EditText(this);edge.setInputType(InputType.TYPE_CLASS_NUMBER);int[] current=document.outputSize(sourceWidth(),sourceHeight(),maxTexture);edge.setText(""+Math.max(current[0],current[1]));form.addView(edge,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView qLabel=text("Qualidade JPEG: "+document.jpegQuality+"%",13,EditorTheme.INK);form.addView(qLabel);SeekBar quality=new SeekBar(this);quality.setMin(1);quality.setMax(100);quality.setProgress(document.jpegQuality);form.addView(quality,new LinearLayout.LayoutParams(-1,dp(44)));quality.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int n,boolean u){qLabel.setText("Qualidade JPEG: "+n+"%");}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
        format.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int n,long id){qLabel.setVisibility(n==1?View.VISIBLE:View.GONE);quality.setVisibility(n==1?View.VISIBLE:View.GONE);}});
        TextView note=text("Pictures/Noise! · limite deste aparelho: "+maxTexture+" px. O original fica preservado no projeto.",12,EditorTheme.MUTED);form.addView(note);
        ScrollView scroller=new ScrollView(this);scroller.addView(form);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Exportar imagem").setView(scroller).setNegativeButton("Cancelar",null).setPositiveButton("Salvar imagem",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{try{int n=Integer.parseInt(edge.getText().toString());if(n<1||n>maxTexture)throw new NumberFormatException();document.outputLongEdge=n;document.jpeg=format.getSelectedItemPosition()==1;document.jpegQuality=quality.getProgress();saveProject(false);dialog.dismiss();chooseOutput();}catch(NumberFormatException e){edge.setError("Digite entre 1 e "+maxTexture);}}));dialog.show();
    }
    private void chooseOutput(){if(Build.VERSION.SDK_INT<29&&checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},SAVE_PERMISSION);return;}export(null);}
    private void export(Uri supplied){if(original==null||loading||exporting)return;final EditorDocument d=document.copy();final ImageSource current=source;final Bitmap previewSource=original;final int w=sourceWidth(),h=sourceHeight(),limit=maxTexture;exporting=true;status.setText("Exportando… você pode continuar editando");updateActions();
        exportWorker.execute(()->{Bitmap full=null;try{full=current!=null?ImageSource.decode(current.file,limit):previewSource;Uri uri=ExportManager.save(getApplicationContext(),full,d,w,h,limit,supplied);runOnUiThread(()->{if(destroyed)return;exporting=false;updateActions();status.setText("Salvo em Pictures/Noise!");if(uri!=null&&supplied==null)new AlertDialog.Builder(this).setTitle("Imagem salva").setMessage("Disponível na galeria, em Pictures/Noise!.").setNegativeButton("Concluir",null).setPositiveButton("Compartilhar",(a,b)->share(uri,d.jpeg)).show();});}
            catch(Exception|OutOfMemoryError e){android.util.Log.e("NoiseExport","Save",e);runOnUiThread(()->{if(!destroyed){exporting=false;updateActions();status.setText("Falha ao exportar. Tente uma resolução menor ou libere espaço.");toast("Não foi possível salvar a imagem");}});}finally{if(full!=null&&full!=previewSource)full.recycle();}});
    }
    private void share(Uri uri,boolean jpeg){Intent i=new Intent(Intent.ACTION_SEND).setType(jpeg?"image/jpeg":"image/png").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);i.setClipData(ClipData.newRawUri("Noise!",uri));startActivity(Intent.createChooser(i,"Compartilhar imagem"));}
    private void toast(String s){if(!destroyed)Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==1&&result==RESULT_OK&&data!=null&&data.getData()!=null)load(data.getData());}
    @Override public void onRequestPermissionsResult(int request,String[] names,int[] grants){super.onRequestPermissionsResult(request,names,grants);if(request==GALLERY_PERMISSION&&galleryScreen!=null)galleryScreen.refresh(hasGalleryPermission());if(request==SAVE_PERMISSION){if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)chooseOutput();else toast("Permissão necessária para salvar na galeria");}}
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);try{b.putString("document",document.toJson().toString());b.putString("history",history.toJson().toString());}catch(JSONException ignored){}b.putString("project",projectId);b.putInt("category",panel.category);saveProject(false);}
    @Override public void onBackPressed(){
        if(cropEditor!=null){closeCrop();return;}
        if(orbital!=null&&orbital.getParent()!=null){closeOrbital();return;}
        if(homeScreen!=null&&homeScreen.getVisibility()==View.VISIBLE){super.onBackPressed();return;}
        if(galleryScreen!=null&&galleryScreen.getVisibility()==View.VISIBLE){
            closeGallery();if(original==null)showHome();return;
        }
        if(workspace.isFullscreen()){workspace.fullscreen(false);return;}
        showHome();
    }
    @Override protected void onResume(){super.onResume();if(preview!=null)preview.onResume();if(galleryScreen!=null&&galleryScreen.getVisibility()==View.VISIBLE)galleryScreen.refresh(hasGalleryPermission());}
    @Override protected void onPause(){comparing=false;publish();saveProject(false);preview.onPause();super.onPause();}
    @Override protected void onDestroy(){destroyed=true;loads.incrementAndGet();if(importTask!=null)importTask.cancel(true);closeCrop();preview.dispose();files.shutdown();exportWorker.shutdown();if(galleryScreen!=null)galleryScreen.dispose();super.onDestroy();}
}
