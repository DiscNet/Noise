package com.discnet.noise.advancedstudio;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;

/**
 * Isolated Android workspace. Only functional commands are exposed:
 * document operations, raster layer management, move/pan, zoom, PNG export.
 */
public final class AdvancedStudioScreen extends FrameLayout {
    public interface Host {void exit(); void importImage();}
    public static final int FONT_PICKER_REQUEST=23091;
    private final Activity activity;
    private final Host host;
    private final StudioProjectStore store;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private StudioDocument document,parentSmartDocument;
    private String smartOwnerId,editingMaskId;
    private boolean paintingMask;
    private final LinearLayout page,menubar,tools,layersList,propertyPanel,layerPane,textPanel;
    private final ScrollView layerScroll,textScroll;
    private final HashMap<Tool,FrameLayout> toolGroups=new HashMap<>();
    private final HashMap<Tool,StudioToolIcon> toolGlyphs=new HashMap<>();
    private final FrameLayout editor;
    private final StudioCanvas canvas;
    private final TextView docTab,info,opacityLabel;
    private final SeekBar opacity;
    enum Tool { MOVE,TRANSFORM,HAND,BRUSH,ERASER,MASK_HIDE,MASK_REVEAL,SELECTION,CROP,BUCKET,GRADIENT,RECTANGLE,ELLIPSE,TEXT }
    private Tool selectedTool=Tool.MOVE;
    private boolean closed,showLayers=true,showTextPanel=false,showTransformPanel=false,
        keepAspect=true,editingEffects=false,editingShadow=false;
    private int selectedFilterIndex=-1,selectedStyleIndex=-1;
    private boolean editingStyle;
    private LinearLayout layerEffectsBar,effectActions,effectControls,transformPanel;
    private ScrollView effectsScroll,transformScroll;
    private TextView blendButton;
    private EditText opacityInput;
    private int foreground=0xffba8cf0,brushSize=24,brushOpacity=100;
    private RectF pendingCrop;
    private TextView toolMode;
    private int revision,savedRevision;
    private boolean refreshingPanels;
    private final Runnable deferredPanelRefresh=this::refreshPanelsIfIdle;
    private void refreshPanelsIfIdle(){
        if(closed)return;
        View focused=findFocus();
        // An active precision input must not be detached while the IME is editing it.
        if(focused instanceof EditText&&focused!=opacityInput)return;
        updatePanels();
    }
    private int ink=0xfff5f2f8,muted=0xffa9a4b0,accent=0xffbe9aeb;
    private final int surface=0xff101016,panel=0xff19171f;
    public AdvancedStudioScreen(Activity a,Host host,Bitmap imported){
        super(a);activity=a;this.host=host;store=new StudioProjectStore(a);
        StudioFontLibrary.init(a);
        setBackgroundColor(surface);setClickable(true);setFocusableInTouchMode(true);
        document=StudioDocument.blank("Sem título",
            imported==null?1080:Math.min(StudioDocument.MAX_SIDE,imported.getWidth()),
            imported==null?1080:Math.min(StudioDocument.MAX_SIDE,imported.getHeight()),true);
        if(imported!=null&&StudioDocument.validSize(imported.getWidth(),imported.getHeight())){
            document.add("Fotografia importada",imported);
        }
        page=new LinearLayout(a);page.setOrientation(LinearLayout.VERTICAL);
        addView(page,new LayoutParams(-1,-1));
        menubar=new LinearLayout(a);menubar.setGravity(Gravity.CENTER_VERTICAL);
        HorizontalScrollView menuScroll=new HorizontalScrollView(a);
        menuScroll.setFillViewport(false);menuScroll.setHorizontalScrollBarEnabled(false);
        menuScroll.setBackgroundColor(0xff16141c);
        menuScroll.addView(menubar);
        page.addView(menuScroll,new LinearLayout.LayoutParams(-1,dp(52)));
        commandMenu("Arquivo",new String[]{"Novo","Abrir projeto","Importar camada","Salvar .noise","Exportar PNG","Fechar conteúdo inteligente","Voltar"},
            this::fileCommand);
        commandMenu("Editar",new String[]{"Desfazer","Refazer","Transformação livre","Girar 90° horário","Girar 90° anti-horário","Girar 180°","Espelhar horizontal","Espelhar vertical"},
            this::editCommand);
        commandMenu("Camadas",new String[]{"Nova camada","Duplicar","Excluir","Para cima","Para baixo","Texto editável","Retângulo","Elipse","Transformar","Mesclagem","Converter em objeto inteligente","Editar conteúdo inteligente","Rasterizar objeto inteligente","Painel"},
            this::layerCommand);
        commandMenu("Selecionar",new String[]{"Selecionar tudo","Selecionar pixels da camada","Inverter seleção","Desmarcar","Limpar","Preencher com cor","Recortar seleção"},
            this::selectionCommand);
        commandMenu("Imagem",new String[]{"Aplicar recorte","Cancelar recorte","Brilho / Contraste / Saturação"},
            this::imageCommand);
        commandMenu("Máscaras",new String[]{"Criar máscara branca","Criar máscara preta","Editar máscara","Editar pixels","Pintar preto","Pintar branco","Aplicar máscara","Vincular/desvincular","Visualizar máscara","Inverter máscara","Ativar/desativar","Densidade","Excluir máscara"},
            this::maskCommand);
        commandMenu("Filtros",new String[]{"Adicionar filtro","Gerenciar filtros"},
            this::filterCommand);
        commandMenu("Estilos",new String[]{"Adicionar estilo","Bisel e relevo","Sombra projetada","Sombra interna","Brilho externo","Brilho interno","Contorno","Sobreposição de cor","Sobreposição de degradê","Remover sombra"},
            this::styleCommand);
        commandMenu("Texto",new String[]{"Novo texto","Painel Caractere / Parágrafo"},this::textCommand);
        commandMenu("Visualizar",new String[]{"Ajustar à tela","Zoom +","Zoom −","Camadas","Texto"},
            this::viewCommand);

        LinearLayout contextBar=new LinearLayout(a);
        contextBar.setPadding(dp(12),0,dp(12),0);
        contextBar.setGravity(Gravity.CENTER_VERTICAL);
        contextBar.setBackgroundColor(0xff201c28);
        docTab=text("",13,ink);docTab.setSingleLine(true);
        contextBar.addView(docTab,new LinearLayout.LayoutParams(0,-1,1));
        toolMode=text("Mover",12,accent);contextBar.addView(toolMode);
        contextBar.addView(action("⚙",()->{
            if(selectedTool==Tool.TRANSFORM)transformDialog();
            else if(selectedTool==Tool.TEXT){showTextPanel=true;showTransformPanel=false;updatePanels();}
            else if(selectedTool==Tool.BRUSH||selectedTool==Tool.ERASER)colorDialog();
        }));
        page.addView(contextBar,new LinearLayout.LayoutParams(-1,dp(41)));

        LinearLayout body=new LinearLayout(a);
        boolean wide=a.getResources().getDisplayMetrics().widthPixels>=dp(620);
        body.setOrientation(wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
        page.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout working=new LinearLayout(a);working.setOrientation(LinearLayout.HORIZONTAL);
        if(wide)body.addView(working,new LinearLayout.LayoutParams(0,-1,1));
        else body.addView(working,new LinearLayout.LayoutParams(-1,0,1));
        tools=new LinearLayout(a);tools.setOrientation(LinearLayout.VERTICAL);
        tools.setPadding(dp(2),dp(5),dp(2),dp(5));
        tools.setBackgroundColor(0xff16141c);
        ScrollView toolScroll=new ScrollView(a);
        toolScroll.setFillViewport(false);
        toolScroll.setVerticalScrollBarEnabled(false);
        toolScroll.addView(tools);
        working.addView(toolScroll,new LinearLayout.LayoutParams(dp(51),-1));
        toolGroup("move",Tool.MOVE,Tool.HAND);
        toolGroup("selection",Tool.SELECTION,Tool.CROP);
        toolGroup("brush",Tool.BRUSH,Tool.ERASER,Tool.MASK_HIDE,Tool.MASK_REVEAL);
        toolGroup("fill",Tool.BUCKET,Tool.GRADIENT);
        toolGroup("shape",Tool.RECTANGLE,Tool.ELLIPSE);
        toolGroup("type",Tool.TEXT);
        toolGroup("transform",Tool.TRANSFORM);
        editor=new FrameLayout(a);editor.setBackgroundColor(0xff0a090d);
        working.addView(editor,new LinearLayout.LayoutParams(0,-1,1));
        canvas=new StudioCanvas(a);
        editor.addView(canvas,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout dock=new LinearLayout(a);dock.setOrientation(LinearLayout.VERTICAL);
        dock.setBackgroundColor(panel);
        if(wide)body.addView(dock,new LinearLayout.LayoutParams(dp(220),-1));
        else body.addView(dock,new LinearLayout.LayoutParams(-1,dp(295)));
        propertyPanel=dock;
        LinearLayout dockHead=new LinearLayout(a);dockHead.setGravity(Gravity.CENTER_VERTICAL);
        dockHead.setPadding(dp(8),0,dp(8),0);
        dock.addView(dockHead,new LinearLayout.LayoutParams(-1,dp(42)));
        TextView layersTitle=action("CAMADAS",()->{showTextPanel=false;showTransformPanel=false;updatePanels();});
        layersTitle.setTextSize(11);layersTitle.setTextColor(accent);
        dockHead.addView(layersTitle,new LinearLayout.LayoutParams(0,-1,1));
        TextView textTab=action("TEXTO",()->{showTextPanel=true;showTransformPanel=false;updatePanels();});
        textTab.setTextSize(11);
        dockHead.addView(textTab,new LinearLayout.LayoutParams(dp(52),-1));
        dockHead.addView(action("TRANSF.",this::transformDialog),new LinearLayout.LayoutParams(dp(64),-1));
        dockHead.addView(action("＋",()->layerCommand("Nova camada")));
        dockHead.addView(action("⧉",()->layerCommand("Duplicar")));
        dockHead.addView(action("↑",()->layerCommand("Para cima")));
        dockHead.addView(action("↓",()->layerCommand("Para baixo")));
        dockHead.addView(action("×",()->layerCommand("Excluir")));
        layerPane=new LinearLayout(a);layerPane.setOrientation(LinearLayout.VERTICAL);
        dock.addView(layerPane,new LinearLayout.LayoutParams(-1,0,1));
        blendButton=action("Normal  ⌄",this::blendDialog);
        LinearLayout blendRow=new LinearLayout(a);
        blendRow.setGravity(Gravity.CENTER_VERTICAL);
        blendRow.setPadding(dp(10),0,dp(10),0);
        blendRow.setBackgroundColor(0xff211c29);
        layerPane.addView(blendRow,new LinearLayout.LayoutParams(-1,dp(35)));
        blendRow.addView(text("MESCLAGEM",10,muted),
            new LinearLayout.LayoutParams(dp(78),dp(33)));
        blendButton.setTextColor(accent);blendButton.setTextSize(12);
        blendRow.addView(blendButton,new LinearLayout.LayoutParams(0,dp(34),1));
        LinearLayout alphaRow=new LinearLayout(a);alphaRow.setGravity(Gravity.CENTER_VERTICAL);
        alphaRow.setPadding(dp(10),0,dp(10),0);
        layerPane.addView(alphaRow,new LinearLayout.LayoutParams(-1,dp(35)));
        opacityLabel=text("OPACIDADE",10,muted);
        alphaRow.addView(opacityLabel,new LinearLayout.LayoutParams(dp(79),-1));
        opacity=new SeekBar(a);opacity.setMax(100);
        opacity.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
        opacity.setThumbTintList(android.content.res.ColorStateList.valueOf(accent));
        alphaRow.addView(opacity,new LinearLayout.LayoutParams(0,-1,1));
        opacityInput=new EditText(a);opacityInput.setSingleLine(true);
        opacityInput.setTextSize(12);opacityInput.setTextColor(accent);
        opacityInput.setGravity(Gravity.CENTER);
        opacityInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        opacityInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        opacityInput.setSelectAllOnFocus(true);
        alphaRow.addView(opacityInput,new LinearLayout.LayoutParams(dp(48),dp(32)));
        opacityInput.setOnEditorActionListener((v,action,event)->{
            try{
                StudioDocument.Layer selected=document.active();
                int n=Integer.parseInt(opacityInput.getText().toString().trim());
                if(selected!=null&&!selected.locked&&n>=0&&n<=100){
                    document.checkpoint();selected.opacity=n/100f;
                    changed(true);
                }
            }catch(Exception error){message("Opacidade deve estar entre 0 e 100");}
            opacityInput.clearFocus();return true;
        });
        layerScroll=new ScrollView(a);layerScroll.setFillViewport(false);
        layerPane.addView(layerScroll,new LinearLayout.LayoutParams(-1,0,1));
        layersList=new LinearLayout(a);layersList.setOrientation(LinearLayout.VERTICAL);
        layerScroll.addView(layersList);
        effectsScroll=new ScrollView(a);
        effectsScroll.setVisibility(GONE);
        layerPane.addView(effectsScroll,new LinearLayout.LayoutParams(-1,0,1));
        effectControls=new LinearLayout(a);effectControls.setOrientation(LinearLayout.VERTICAL);
        effectControls.setPadding(dp(7),dp(5),dp(7),dp(5));
        effectsScroll.addView(effectControls);
        layerEffectsBar=new LinearLayout(a);
        layerEffectsBar.setOrientation(LinearLayout.VERTICAL);
        layerEffectsBar.setBackgroundColor(0xff221c2a);
        layerPane.addView(layerEffectsBar,new LinearLayout.LayoutParams(-1,dp(46)));
        HorizontalScrollView actionsScroll=new HorizontalScrollView(a);
        actionsScroll.setHorizontalScrollBarEnabled(false);
        layerEffectsBar.addView(actionsScroll,new LinearLayout.LayoutParams(-1,dp(45)));
        effectActions=new LinearLayout(a);effectActions.setGravity(Gravity.CENTER_VERTICAL);
        effectActions.setPadding(dp(7),0,dp(7),0);actionsScroll.addView(effectActions);
        textScroll=new ScrollView(a);textScroll.setFillViewport(false);
        dock.addView(textScroll,new LinearLayout.LayoutParams(-1,0,1));
        textPanel=new LinearLayout(a);textPanel.setOrientation(LinearLayout.VERTICAL);
        textPanel.setPadding(dp(10),dp(6),dp(10),dp(12));
        textScroll.addView(textPanel);textScroll.setVisibility(GONE);
        transformScroll=new ScrollView(a);transformScroll.setFillViewport(false);
        dock.addView(transformScroll,new LinearLayout.LayoutParams(-1,0,1));
        transformPanel=new LinearLayout(a);transformPanel.setOrientation(LinearLayout.VERTICAL);
        transformPanel.setPadding(dp(9),dp(5),dp(9),dp(12));
        transformScroll.addView(transformPanel);transformScroll.setVisibility(GONE);
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            boolean started;
            @Override public void onStartTrackingTouch(SeekBar b){started=false;}
            @Override public void onStopTrackingTouch(SeekBar b){if(started)changed(true);}
            @Override public void onProgressChanged(SeekBar b,int progress,boolean fromUser){
                StudioDocument.Layer l=document.active();
                if(!fromUser||l==null||l.locked)return;
                if(!started){document.checkpoint();started=true;}
                l.opacity=progress/100f;
                canvas.invalidate();
                if(!opacityInput.hasFocus())opacityInput.setText(String.valueOf(progress));
            }
        });

        LinearLayout footer=new LinearLayout(a);footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(dp(9),0,dp(9),0);footer.setBackgroundColor(0xff17151c);
        info=text("",11,muted);
        footer.addView(info,new LinearLayout.LayoutParams(0,-1,1));
        footer.addView(action("−",()->canvas.zoomBy(.8f)));
        footer.addView(action("+",()->canvas.zoomBy(1.25f)));
        page.addView(footer,new LinearLayout.LayoutParams(-1,dp(37)));
        LinearLayout properties=new LinearLayout(a);properties.setGravity(Gravity.CENTER_VERTICAL);
        properties.setPadding(dp(7),0,dp(7),0);
        properties.setBackgroundColor(0xff201b27);
        page.addView(properties,new LinearLayout.LayoutParams(-1,dp(45)));
        properties.addView(action("Cor",this::colorDialog));
        TextView widthLabel=text("Pincel",11,muted);
        properties.addView(widthLabel);
        SeekBar size=new SeekBar(a);size.setMax(119);size.setProgress(brushSize-1);
        properties.addView(size,new LinearLayout.LayoutParams(0,dp(42),1));
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){}
            public void onProgressChanged(SeekBar b,int v,boolean from){
                if(from){brushSize=v+1;widthLabel.setText("Pincel "+brushSize);}
            }
        });
        properties.addView(action("Texto",()->{showTextPanel=true;showTransformPanel=false;updatePanels();}));
        updateToolbar();
        updateUi();
        if(imported!=null)scheduleSave();
    }
    private int dp(float x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private TextView text(String text,int size,int color){
        TextView t=new TextView(activity);
        t.setText(text);t.setTextSize(size);t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(dp(5),0,dp(5),0);
        t.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));
        return t;
    }
    private String toolName(Tool t){
        switch(t){
            case MOVE:return "Mover";case TRANSFORM:return "Transformar";
            case HAND:return "Mão";case BRUSH:return "Pincel";
            case ERASER:return "Borracha";case MASK_HIDE:return "Máscara: ocultar";
            case MASK_REVEAL:return "Máscara: revelar";case SELECTION:return "Seleção retangular";
            case CROP:return "Recorte";case BUCKET:return "Balde";
            case GRADIENT:return "Gradiente";case RECTANGLE:return "Retângulo";
            case ELLIPSE:return "Elipse";case TEXT:return "Texto";
            default:return "Ferramenta";
        }
    }
    private void activateTool(Tool t){
        selectedTool=t;toolMode.setText(toolName(t));
        if(t==Tool.TEXT){showTextPanel=true;showTransformPanel=false;updatePanels();}
        if(t==Tool.TRANSFORM){showTextPanel=false;showTransformPanel=true;updatePanels();}
        updateToolbar();canvas.invalidate();
    }
    private void toolGroup(String icon,Tool first,Tool... variants){
        FrameLayout button=new FrameLayout(activity);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(45),dp(44));
        lp.setMargins(dp(3),dp(1),dp(3),dp(1));
        tools.addView(button,lp);
        StudioToolIcon glyph=new StudioToolIcon(activity,icon);
        FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(dp(27),dp(27),Gravity.CENTER);
        button.addView(glyph,ip);
        if(variants.length>0){
            TextView marker=text("⌄",10,muted);marker.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams mp=new FrameLayout.LayoutParams(dp(12),dp(11),
                Gravity.BOTTOM|Gravity.RIGHT);
            button.addView(marker,mp);
        }
        button.setClickable(true);button.setFocusable(true);
        button.setContentDescription(toolName(first)+"; toque prolongado para alternativas");
        if(android.os.Build.VERSION.SDK_INT>=26)
            button.setTooltipText(toolName(first));
        button.setOnClickListener(v->activateTool(first));
        button.setOnLongClickListener(v->{
            if(variants.length==0){activateTool(first);return true;}
            PopupMenu menu=new PopupMenu(activity,button);
            Tool[] all=new Tool[variants.length+1];all[0]=first;
            System.arraycopy(variants,0,all,1,variants.length);
            for(int i=0;i<all.length;i++)menu.getMenu().add(0,i,i,toolName(all[i]));
            menu.setOnMenuItemClickListener(item->{activateTool(all[item.getItemId()]);return true;});
            menu.show();return true;
        });
        toolGroups.put(first,button);toolGlyphs.put(first,glyph);
        for(Tool t:variants){toolGroups.put(t,button);toolGlyphs.put(t,glyph);}
    }
    private void updateToolbar(){
        HashSet<FrameLayout> visited=new HashSet<>();
        for(Map.Entry<Tool,FrameLayout> entry:toolGroups.entrySet()){
            FrameLayout button=entry.getValue();
            if(!visited.add(button))continue;
            boolean active=button==toolGroups.get(selectedTool);
            GradientDrawable bg=new GradientDrawable();
            bg.setColor(active?0xff362843:0xff16141c);
            bg.setCornerRadius(dp(8));
            if(active)bg.setStroke(dp(1),0xff9863c7);
            button.setBackground(bg);
            for(Map.Entry<Tool,StudioToolIcon> glyph:toolGlyphs.entrySet())
                if(toolGroups.get(glyph.getKey())==button)
                    glyph.getValue().setSelectedTool(active);
        }
    }
    private void colorDialog(){
        StudioColorPicker.show(activity,"Cor principal",foreground,
            color->{foreground=color;canvas.invalidate();});
    }
    private void pickLayerColor(StudioDocument.Layer layer){
        if(layer==null||layer.locked)return;
        StudioColorPicker.show(activity,"Cor da camada",layer.color,color->{
            document.checkpoint();layer.color=color;changed(true);
        });
    }
    private void chooseFont(){
        if(document.active()==null||document.active().type!=StudioDocument.Type.TEXT){
            message("Selecione uma camada de texto");return;
        }
        Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.setType("*/*");pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{activity.startActivityForResult(pick,FONT_PICKER_REQUEST);}
        catch(Exception e){message("Não foi possível abrir o seletor de arquivos");}
    }
    public void importFontUri(Uri uri){
        if(uri==null||closed)return;
        final String layerId=document.activeId;
        io.execute(()->{
            try{
                String font=StudioFontLibrary.importFont(activity,uri);
                main.post(()->{
                    if(closed)return;
                    StudioDocument.Layer target=null;
                    for(StudioDocument.Layer l:document.layers)
                        if(l.id.equals(layerId)){target=l;break;}
                    if(target!=null&&target.type==StudioDocument.Type.TEXT&&!target.locked){
                        document.checkpoint();target.fontFamily=font;
                        changed(true);
                        message("Fonte personalizada instalada");
                    }else message("Fonte instalada: selecione uma camada de texto");
                });
            }catch(Exception error){main.post(()->message(
                "Falha ao importar fonte: "+error.getMessage()));}
        });
    }
    private void textDialog(){
        StudioDocument.Layer current=document.active();
        boolean editing=current!=null&&current.type==StudioDocument.Type.TEXT;
        EditText input=numberField("Texto editável",false);
        input.setSingleLine(false);input.setMinLines(2);
        input.setText(editing?current.text:"Noise!");
        new AlertDialog.Builder(activity).setTitle(editing?"Editar texto":"Nova camada de texto")
            .setView(input).setNegativeButton("Cancelar",null)
            .setPositiveButton("Aplicar",(dialog,which)->{
                String value=input.getText().toString();
                if(value.trim().isEmpty()){message("Insira um texto");return;}
                if(value.length()>8192){message("Texto muito longo");return;}
                if(editing){
                    if(current.locked){message("Camada bloqueada");return;}
                    StudioDocument.Layer preview=current.copy();preview.text=value;
                    if(!canApplyText(current,preview))return;
                    document.checkpoint();current.text=value;
                    StudioLayerEffects.resizeMask(current);
                }else{
                    document.addText(value,foreground,64f);
                    showTextPanel=true;
                }
                changed(true);
            }).show();
    }
    private EditText numericField(LinearLayout box,String hint,float value){
        EditText input=numberField(hint,false);
        input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED|
            InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.format(Locale.ROOT,"%.2f",value));
        box.addView(input,new LinearLayout.LayoutParams(-1,dp(45)));
        return input;
    }
    private float fieldValue(EditText input){return Float.parseFloat(input.getText().toString().trim());}
    private void editCommand(String cmd){
        StudioDocument.Layer layer=document.active();
        if("Desfazer".equals(cmd)){if(document.undo())changed(true);return;}
        if("Refazer".equals(cmd)){if(document.redo())changed(true);return;}
        if("Transformação livre".equals(cmd)){activateTool(Tool.TRANSFORM);transformDialog();return;}
        if(layer==null||layer.locked){message("Selecione uma camada desbloqueada");return;}
        document.checkpoint();
        switch(cmd){
            case "Girar 90° horário":layer.rotation+=90;break;
            case "Girar 90° anti-horário":layer.rotation-=90;break;
            case "Girar 180°":layer.rotation+=180;break;
            case "Espelhar horizontal":layer.scaleX=-layer.scaleX;break;
            case "Espelhar vertical":layer.scaleY=-layer.scaleY;break;
            default:return;
        }
        changed(true);
    }
    private void textCommand(String cmd){
        if("Novo texto".equals(cmd)){textDialog();return;}
        showTextPanel=true;updatePanels();
    }
    private boolean canApplyText(StudioDocument.Layer original,StudioDocument.Layer draft){
        if(draft.text.length()>8192||draft.text.trim().isEmpty()){
            message("Texto inválido");return false;
        }
        if(original.mask!=null&&(long)draft.naturalWidth()*draft.naturalHeight()>
            StudioLayerEffects.MAX_EFFECT_PIXELS){
            message("O texto excede o tamanho permitido pela máscara");return false;
        }
        return true;
    }
    private void applyTextDraft(StudioDocument.Layer current,StudioDocument.Layer draft){
        if(current==null||current.locked||current.type!=StudioDocument.Type.TEXT)return;
        if(!canApplyText(current,draft))return;
        document.checkpoint();
        current.fontFamily=draft.fontFamily;current.fontBold=draft.fontBold;
        current.fontItalic=draft.fontItalic;current.fontSize=draft.fontSize;
        current.color=draft.color;current.letterSpacing=draft.letterSpacing;
        current.lineSpacing=draft.lineSpacing;current.textAlign=draft.textAlign;
        current.x=draft.x;current.y=draft.y;
        current.scaleX=draft.scaleX;current.scaleY=draft.scaleY;
        StudioLayerEffects.resizeMask(current);changed(true);
    }
    private void textTitle(String title){
        TextView heading=text(title,10,accent);
        heading.setPadding(dp(7),dp(9),0,dp(3));
        textPanel.addView(heading,new LinearLayout.LayoutParams(-1,dp(31)));
    }
    private void textOption(String title,String value,Runnable action){
        LinearLayout row=new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(9),0,dp(9),0);
        TextView label=text(title,12,ink),detail=text(value,12,accent);
        row.addView(label,new LinearLayout.LayoutParams(0,dp(41),1));
        detail.setSingleLine(true);detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(detail,new LinearLayout.LayoutParams(dp(138),dp(41)));
        row.setOnClickListener(v->action.run());
        row.setBackgroundColor(0xff241f2b);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(42));
        lp.bottomMargin=dp(3);textPanel.addView(row,lp);
    }
    private interface NumericSetter {void apply(StudioDocument.Layer l,float v);}
    private void inspectorField(LinearLayout target,StudioDocument.Layer layer,
                                String caption,String unit,float v,float low,float high,
                                float step,NumericSetter setter,boolean textDimensions){
        final boolean[] started={false};
        StudioInspectorField field=new StudioInspectorField(activity,caption,unit,
            v,low,high,step,new StudioInspectorField.Callback(){
                public void begin(){
                    if(layer!=document.active()||layer.locked)return;
                    if(!started[0]){document.checkpoint();started[0]=true;}
                }
                public void preview(float value){
                    if(!started[0]||layer!=document.active()||layer.locked)return;
                    if(textDimensions){
                        StudioDocument.Layer candidate=layer.copy();
                        setter.apply(candidate,value);
                        if((candidate.mask!=null||candidate.shadowEnabled||
                            !candidate.effects.isEmpty())&&
                            (long)candidate.naturalWidth()*candidate.naturalHeight()>
                            StudioLayerEffects.MAX_EFFECT_PIXELS)return;
                    }
                    setter.apply(layer,value);
                    if(textDimensions&&layer.mask!=null)
                        StudioLayerEffects.resizeMask(layer);
                    StudioLayerEffects.invalidate(layer);
                    canvas.invalidate();
                }
                public void finish(){
                    if(!started[0])return;
                    started[0]=false;
                    if(textDimensions){
                        try{StudioLayerEffects.resizeMask(layer);}
                        catch(Exception error){message(error.getMessage());}
                    }
                    changed(true);
                }
            });
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(66));
        lp.bottomMargin=dp(5);target.addView(field,lp);
    }
    private void transformControls(){
        int lastScroll=transformScroll.getScrollY();
        transformPanel.removeAllViews();
        StudioDocument.Layer layer=document.active();
        if(layer==null||layer.locked){
            transformPanel.addView(text("Selecione uma camada desbloqueada para transformar.",12,muted));
            return;
        }
        textTitleTo(transformPanel,"TRANSFORMAÇÃO LIVRE");
        inspectorField(transformPanel,layer,"X","px",layer.x,-8192,8192,1,
            (l,v)->l.x=v,false);
        inspectorField(transformPanel,layer,"Y","px",layer.y,-8192,8192,1,
            (l,v)->l.y=v,false);
        float w=Math.abs(layer.scaleX)*layer.naturalWidth();
        float h=Math.abs(layer.scaleY)*layer.naturalHeight();
        inspectorField(transformPanel,layer,"Largura","px",w,1,
            Math.min(131072,layer.naturalWidth()*16f),1,(l,v)->{
                float old=Math.abs(l.scaleX);
                float next=v/Math.max(1,l.naturalWidth());
                if(keepAspect&&old>0)
                    l.scaleY=Math.copySign(Math.min(16,Math.max(.01f,
                        Math.abs(l.scaleY)*next/old)),l.scaleY);
                l.scaleX=Math.copySign(next,l.scaleX);
            },false);
        inspectorField(transformPanel,layer,"Altura","px",h,1,
            Math.min(131072,layer.naturalHeight()*16f),1,(l,v)->{
                float old=Math.abs(l.scaleY);
                float next=v/Math.max(1,l.naturalHeight());
                if(keepAspect&&old>0)
                    l.scaleX=Math.copySign(Math.min(16,Math.max(.01f,
                        Math.abs(l.scaleX)*next/old)),l.scaleX);
                l.scaleY=Math.copySign(next,l.scaleY);
            },false);
        CheckBox link=new CheckBox(activity);
        link.setText("Vincular proporções W/H");
        link.setTextColor(ink);link.setTextSize(12);link.setChecked(keepAspect);
        link.setOnCheckedChangeListener((b,checked)->keepAspect=checked);
        transformPanel.addView(link,new LinearLayout.LayoutParams(-1,dp(39)));
        inspectorField(transformPanel,layer,"Ângulo","°",layer.rotation,-360,360,.5f,
            (l,v)->l.rotation=v,false);
        inspectorField(transformPanel,layer,"Inclinação X","°",layer.skewX,-65,65,.5f,
            (l,v)->l.skewX=v,false);
        inspectorField(transformPanel,layer,"Inclinação Y","°",layer.skewY,-65,65,.5f,
            (l,v)->l.skewY=v,false);
        LinearLayout presets=new LinearLayout(activity);
        presets.setGravity(Gravity.CENTER_VERTICAL);
        transformPanel.addView(presets,new LinearLayout.LayoutParams(-1,dp(41)));
        presets.addView(action("90° ↻",()->editCommand("Girar 90° horário")));
        presets.addView(action("90° ↺",()->editCommand("Girar 90° anti-horário")));
        presets.addView(action("⇋",()->editCommand("Espelhar horizontal")));
        presets.addView(action("⇵",()->editCommand("Espelhar vertical")));
        transformScroll.post(()->transformScroll.scrollTo(0,lastScroll));
    }
    private void textTitleTo(LinearLayout destination,String caption){
        TextView title=text(caption,10,accent);
        title.setPadding(dp(8),dp(8),0,dp(5));
        destination.addView(title,new LinearLayout.LayoutParams(-1,dp(30)));
    }
    private String blendName(StudioDocument.Blend blend){
        switch(blend){
            case MULTIPLY:return "Multiplicar";case SCREEN:return "Tela";
            case OVERLAY:return "Sobrepor";case DARKEN:return "Escurecer";
            case LIGHTEN:return "Clarear";default:return "Normal";
        }
    }
    private void effectsActions(){
        effectActions.removeAllViews();
        StudioDocument.Layer layer=document.active();
        if(layer==null)return;
        if(editingEffects){
            effectActions.addView(action("← Camadas",()->{
                editingEffects=false;editingStyle=false;selectedFilterIndex=-1;
                layerScroll.setVisibility(VISIBLE);effectsScroll.setVisibility(GONE);
                updatePanels();
            }));
        }else{
            effectActions.addView(action("fx",()->addStyleMenu(layer)));
        }
        effectActions.addView(action(layer.clippingMask?"✓ ↓ Corte":"↓ Máscara de corte",
            ()->toggleClippingMask(layer)));
        for(int i=0;i<layer.effects.size();i++){
            final int position=i;
            StudioLayerEffects.Effect effect=layer.effects.get(i);
            TextView chip=action((effect.enabled?"● ":"○ ")+effect.kind.name(),
                ()->{editingEffects=true;editingShadow=false;
                    selectedFilterIndex=position;updatePanels();});
            effectActions.addView(chip);
        }
        if(layer.mask!=null){
            effectActions.addView(action("◩ Máscara",
                ()->maskCommand("Densidade")));
        }
    }
    private void effectsControls(){
        int last=effectsScroll.getScrollY();effectControls.removeAllViews();
        StudioDocument.Layer layer=document.active();
        if(layer==null)return;
        if(editingStyle&&selectedStyleIndex>=0&&selectedStyleIndex<layer.styles.size()){
            editStyleControls(layer,layer.styles.get(selectedStyleIndex));
        }else if(editingShadow){
            textTitleTo(effectControls,"SOMBRA PROJETADA");
            if(!layer.shadowEnabled)effectControls.addView(action("Ativar sombra",
                ()->{if(layer.locked)return;
                    document.checkpoint();layer.shadowEnabled=true;changed(true);}));
            inspectorField(effectControls,layer,"Desfoque","px",layer.shadowRadius,
                1,16,.25f,(l,v)->{l.shadowRadius=v;l.shadowEnabled=true;},false);
            inspectorField(effectControls,layer,"Distância X","px",layer.shadowX,
                -128,128,1,(l,v)->{l.shadowX=v;l.shadowEnabled=true;},false);
            inspectorField(effectControls,layer,"Distância Y","px",layer.shadowY,
                -128,128,1,(l,v)->{l.shadowY=v;l.shadowEnabled=true;},false);
            inspectorField(effectControls,layer,"Opacidade","%",layer.shadowOpacity*100,
                0,100,1,(l,v)->{l.shadowOpacity=v/100f;l.shadowEnabled=true;},false);
            effectControls.addView(action("Remover sombra",()->styleCommand("Remover sombra")));
        }else if(selectedFilterIndex==-2&&layer.mask!=null){
            textTitleTo(effectControls,"MÁSCARA · DENSIDADE");
            inspectorField(effectControls,layer,"Densidade","%",layer.maskDensity,
                0,100,1,(l,v)->l.maskDensity=Math.round(v),false);
            effectControls.addView(action(layer.maskEnabled?"Desativar máscara":"Ativar máscara",
                ()->{document.checkpoint();layer.maskEnabled=!layer.maskEnabled;changed(true);}));
        }else if(selectedFilterIndex>=0&&selectedFilterIndex<layer.effects.size()){
            StudioLayerEffects.Effect e=layer.effects.get(selectedFilterIndex);
            textTitleTo(effectControls,"FILTRO · "+e.kind.name());
            int min=e.kind==StudioLayerEffects.Kind.GAUSSIAN_BLUR?1:-100;
            int max=e.kind==StudioLayerEffects.Kind.GAUSSIAN_BLUR?8:100;
            inspectorField(effectControls,layer,"Intensidade","",e.value,min,max,1,
                (l,v)->e.value=Math.round(v),false);
            effectControls.addView(action(e.enabled?"Desativar":"Ativar",
                ()->{document.checkpoint();e.enabled=!e.enabled;changed(true);}));
            effectControls.addView(action("Excluir filtro",
                ()->{document.checkpoint();layer.effects.remove(e);
                    editingEffects=false;editingStyle=false;selectedFilterIndex=-1;changed(true);}));
        }else{
            textTitleTo(effectControls,"PROPRIEDADES DA CAMADA");
            effectControls.addView(action("Editar estilos · fx",()->addStyleMenu(layer)));
        }
        effectsScroll.post(()->effectsScroll.scrollTo(0,last));
    }
    private void updatePanels(){
        if(closed||refreshingPanels)return;
        refreshingPanels=true;
        try{
        layerPane.setVisibility(showTextPanel||showTransformPanel?GONE:VISIBLE);
        textScroll.setVisibility(showTextPanel?VISIBLE:GONE);
        transformScroll.setVisibility(showTransformPanel?VISIBLE:GONE);
        if(showTransformPanel){transformControls();return;}
        if(!showTextPanel){
            StudioDocument.Layer current=document.active();
            if(editingEffects&&(current==null||current.locked)){
                editingEffects=false;
            }
            layerScroll.setVisibility(editingEffects?GONE:VISIBLE);
            effectsScroll.setVisibility(editingEffects?VISIBLE:GONE);
            effectsActions();if(editingEffects)effectsControls();
            return;
        }
        int lastTextScroll=textScroll.getScrollY();
        textPanel.removeAllViews();
        StudioDocument.Layer layer=document.active();
        if(layer==null||layer.type!=StudioDocument.Type.TEXT){
            textTitle("CARACTERE / PARÁGRAFO");
            textOption("Nova camada de texto","+",this::textDialog);
            TextView help=text("Selecione uma camada de texto para alterar fonte, tamanho, cor, alinhamento e posição.",12,muted);
            textPanel.addView(help);
            return;
        }
        TextView preview=text(layer.text,18,layer.color);
        preview.setTypeface(StudioTypography.font(layer));
        preview.setSingleLine(true);preview.setMaxLines(1);
        preview.setEllipsize(android.text.TextUtils.TruncateAt.END);
        preview.setPadding(dp(10),dp(7),dp(10),dp(7));
        preview.setBackgroundColor(0xff38313f);
        textPanel.addView(preview,new LinearLayout.LayoutParams(-1,dp(42)));
        textTitle("CARACTERE");
        textOption("Conteúdo","Editar",this::textDialog);
        textOption("Fonte",StudioFontLibrary.label(layer.fontFamily),()->textSetting("font"));
        textOption("Importar .ttf / .otf","Escolher arquivo",this::chooseFont);
        inspectorField(textPanel,layer,"Tamanho","px",layer.fontSize,8,600,1,
            (l,v)->l.fontSize=v,true);
        textOption("Cor","Toque para selecionar",()->pickLayerColor(layer));
        LinearLayout swatches=new LinearLayout(activity);
        int[] samples={0xffffffff,0xff111111,0xffb18aff,0xffff668c,
            0xffffb64f,0xff64dcd8,0xff598bff};
        for(int sample:samples){
            TextView swatch=text("■",23,sample);
            swatch.setGravity(Gravity.CENTER);swatches.addView(swatch,new LinearLayout.LayoutParams(0,dp(34),1));
            swatch.setOnClickListener(v->{
                if(layer.locked)return;
                document.checkpoint();layer.color=sample;changed(true);
            });
        }
        textPanel.addView(swatches,new LinearLayout.LayoutParams(-1,dp(34)));
        inspectorField(textPanel,layer,"Vermelho","R",Color.red(layer.color),0,255,1,
            (l,v)->l.color=Color.argb(Color.alpha(l.color),Math.round(v),
                Color.green(l.color),Color.blue(l.color)),false);
        inspectorField(textPanel,layer,"Verde","G",Color.green(layer.color),0,255,1,
            (l,v)->l.color=Color.argb(Color.alpha(l.color),Color.red(l.color),
                Math.round(v),Color.blue(l.color)),false);
        inspectorField(textPanel,layer,"Azul","B",Color.blue(layer.color),0,255,1,
            (l,v)->l.color=Color.argb(Color.alpha(l.color),Color.red(l.color),
                Color.green(l.color),Math.round(v)),false);
        textOption("Negrito",layer.fontBold?"Ativado":"Desativado",()->textSetting("bold"));
        textOption("Itálico",layer.fontItalic?"Ativado":"Desativado",()->textSetting("italic"));
        inspectorField(textPanel,layer,"Espaç. letras","em",layer.letterSpacing,-.2f,1.5f,.01f,
            (l,v)->l.letterSpacing=v,true);
        textTitle("PARÁGRAFO");
        textOption("Alinhamento",layer.textAlign,()->textSetting("align"));
        inspectorField(textPanel,layer,"Entrelinhas","×",layer.lineSpacing,.5f,3f,.02f,
            (l,v)->l.lineSpacing=v,true);
        textTitle("GEOMETRIA");
        inspectorField(textPanel,layer,"Posição X","px",layer.x,-8192,8192,1,
            (l,v)->l.x=v,false);
        inspectorField(textPanel,layer,"Posição Y","px",layer.y,-8192,8192,1,
            (l,v)->l.y=v,false);
        inspectorField(textPanel,layer,"Largura","px",
            Math.abs(layer.scaleX*layer.naturalWidth()),1,
            Math.min(131072,layer.naturalWidth()*16f),1,
            (l,v)->l.scaleX=Math.copySign(v/Math.max(1,l.naturalWidth()),l.scaleX),false);
        inspectorField(textPanel,layer,"Altura","px",
            Math.abs(layer.scaleY*layer.naturalHeight()),1,
            Math.min(131072,layer.naturalHeight()*16f),1,
            (l,v)->l.scaleY=Math.copySign(v/Math.max(1,l.naturalHeight()),l.scaleY),false);
        textOption("Transformação completa","Abrir",this::transformDialog);
        textScroll.post(()->textScroll.scrollTo(0,lastTextScroll));
        }finally{refreshingPanels=false;}
    }
    private void textSetting(String what){
        StudioDocument.Layer current=document.active();
        if(current==null||current.type!=StudioDocument.Type.TEXT||current.locked){
            message("Selecione uma camada de texto desbloqueada");return;
        }
        if("bold".equals(what)||"italic".equals(what)){
            StudioDocument.Layer next=current.copy();
            if("bold".equals(what))next.fontBold=!next.fontBold;else next.fontItalic=!next.fontItalic;
            applyTextDraft(current,next);return;
        }
        if("align".equals(what)){
            String[] choices={"Esquerda","Centro","Direita"};
            String[] ids={"LEFT","CENTER","RIGHT"};
            new AlertDialog.Builder(activity).setTitle("Alinhamento do parágrafo")
                .setSingleChoiceItems(choices,Arrays.asList(ids).indexOf(current.textAlign),
                    (dialog,index)->{
                        StudioDocument.Layer next=current.copy();next.textAlign=ids[index];
                        applyTextDraft(current,next);dialog.dismiss();
                    }).setNegativeButton("Cancelar",null).show();
            return;
        }
        if("font".equals(what)){
            String[] fonts={"sans-serif","sans-serif-light","sans-serif-condensed",
                "sans-serif-black","serif","serif-monospace","monospace","cursive"};
            new AlertDialog.Builder(activity).setTitle("Família tipográfica")
                .setSingleChoiceItems(fonts,Arrays.asList(fonts).indexOf(current.fontFamily),
                    (dialog,index)->{
                        StudioDocument.Layer next=current.copy();next.fontFamily=fonts[index];
                        applyTextDraft(current,next);dialog.dismiss();
                    }).setNeutralButton("Importar .TTF / .OTF",(dialog,which)->chooseFont())
                    .setNegativeButton("Cancelar",null).show();
            return;
        }
        if("legacy-font".equals(what)){
            new AlertDialog.Builder(activity).setNeutralButton("Outra fonte do sistema",(dialog,which)->{
                        EditText family=numberField("Nome da família de fonte",false);
                        family.setText(current.fontFamily);
                        new AlertDialog.Builder(activity).setTitle("Família de fonte")
                            .setView(family).setNegativeButton("Cancelar",null)
                            .setPositiveButton("Aplicar",(d,w)->{
                                StudioDocument.Layer next=current.copy();
                                next.fontFamily=family.getText().toString().trim();
                                if(next.fontFamily.isEmpty()||next.fontFamily.length()>64){
                                    message("Nome de fonte inválido");return;
                                }
                                applyTextDraft(current,next);
                            }).show();
                    }).setNegativeButton("Cancelar",null).show();
            return;
        }
        float value;
        switch(what){
            case "size":value=current.fontSize;break;
            case "tracking":value=current.letterSpacing;break;
            case "leading":value=current.lineSpacing;break;
            case "x":value=current.x;break;
            case "y":value=current.y;break;
            case "width":value=Math.abs(current.scaleX*current.naturalWidth());break;
            case "height":value=Math.abs(current.scaleY*current.naturalHeight());break;
            default:value=0;
        }
        if("color".equals(what)){pickLayerColor(current);return;}
        EditText input=numberField("Valor",false);
        if("color".equals(what))
            input.setText(String.format(Locale.ROOT,"#%06X",current.color&0xffffff));
        else{
            input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED|
                InputType.TYPE_NUMBER_FLAG_DECIMAL);
            input.setText(String.format(Locale.ROOT,"%.2f",value));
        }
        new AlertDialog.Builder(activity).setTitle("Texto · "+what).setView(input)
            .setNegativeButton("Cancelar",null).setPositiveButton("Aplicar",(dialog,which)->{
                try{
                    StudioDocument.Layer draft=current.copy();
                    float v="color".equals(what)?0:fieldValue(input);
                    if(!Float.isFinite(v))throw new NumberFormatException();
                    switch(what){
                        case "size":
                            if(v<8||v>600)throw new NumberFormatException();
                            draft.fontSize=v;break;
                        case "tracking":
                            if(v<-.2f||v>1.5f)throw new NumberFormatException();
                            draft.letterSpacing=v;break;
                        case "leading":
                            if(v<.5f||v>3)throw new NumberFormatException();
                            draft.lineSpacing=v;break;
                        case "color":draft.color=Color.parseColor(input.getText().toString().trim());break;
                        case "x":draft.x=v;break;
                        case "y":draft.y=v;break;
                        case "width":case "height":
                            if(v<1||v>StudioDocument.MAX_SIDE*16)throw new NumberFormatException();
                            if("width".equals(what))draft.scaleX=
                                Math.copySign(v/Math.max(1,current.naturalWidth()),current.scaleX);
                            else draft.scaleY=
                                Math.copySign(v/Math.max(1,current.naturalHeight()),current.scaleY);
                            break;
                    }
                    applyTextDraft(current,draft);
                }catch(Exception error){message("Valor de texto inválido");}
            }).show();
    }
    private void transformDialog(){
        showTextPanel=false;showTransformPanel=true;selectedTool=Tool.TRANSFORM;
        toolMode.setText("Transformar");updateToolbar();updatePanels();canvas.invalidate();
    }
    private void selectionCommand(String cmd){
        try{
            switch(cmd){
                case "Selecionar tudo":
                    document.select(new RectF(0,0,document.width,document.height));break;
                case "Selecionar pixels da camada":
                    document.selectPixels(document.active());break;
                case "Inverter seleção":document.invertSelection();break;
                case "Desmarcar":document.deselect();break;
                case "Limpar":
                    StudioRasterTools.clearOrFill(document,Color.TRANSPARENT,true,paintingMask);
                    break;
                case "Preencher com cor":
                    StudioColorPicker.show(activity,"Preencher seleção",foreground,color->{
                        try{
                            StudioRasterTools.clearOrFill(document,color,false,paintingMask);
                            changed(true);
                        }catch(Exception ex){message(ex.getMessage());}
                    });
                    return;
                case "Recortar seleção":
                    if(document.selection==null){
                        message("O recorte requer uma seleção retangular");return;
                    }
                    pendingCrop=new RectF(document.selection);
                    selectedTool=Tool.CROP;toolMode.setText("Recorte");
                    canvas.invalidate();return;
                default:return;
            }
            changed(true);
        }catch(Exception e){message(e.getMessage());}
    }
    private void imageCommand(String cmd){
        if("Cancelar recorte".equals(cmd)){pendingCrop=null;canvas.invalidate();return;}
        if("Aplicar recorte".equals(cmd)){
            if(pendingCrop==null){message("Defina a área de recorte");return;}
            try{document.crop(pendingCrop);pendingCrop=null;changed(true);}
            catch(Exception e){message(e.getMessage());}return;
        }
        if("Brilho / Contraste / Saturação".equals(cmd))adjustDialog();
    }
    private void adjustDialog(){
        LinearLayout box=new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20),0,dp(20),0);
        EditText brightness=numberField("Brilho (-100 a 100)",false);
        EditText contrast=numberField("Contraste (-100 a 100)",false);
        EditText saturation=numberField("Saturação (-100 a 100)",false);
        brightness.setText("0");contrast.setText("0");saturation.setText("0");
        box.addView(brightness);box.addView(contrast);box.addView(saturation);
        new AlertDialog.Builder(activity).setTitle("Ajustes na camada raster")
            .setView(box).setNegativeButton("Cancelar",null)
            .setPositiveButton("Aplicar",(d,w)->{
                try{
                    StudioRasterTools.adjust(document,
                        Integer.parseInt(brightness.getText().toString()),
                        Integer.parseInt(contrast.getText().toString()),
                        Integer.parseInt(saturation.getText().toString()));
                    changed(true);
                }catch(Exception e){message("Ajuste: "+e.getMessage());}
            }).show();
    }
    private TextView tool(String s){
        TextView v=text(s,11,ink);v.setGravity(Gravity.CENTER);
        v.setContentDescription("Ferramenta "+s);return v;
    }
    private TextView action(String label,Runnable r){
        TextView b=text(label,17,ink);b.setGravity(Gravity.CENTER);
        b.setMinWidth(dp(35));b.setClickable(true);
        b.setOnClickListener(v->r.run());
        return b;
    }
    private interface Action {void select(String name);}
    private void commandMenu(String title,String[] commands,Action action){
        TextView item=text(title,13,ink);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(11),0,dp(11),0);
        menubar.addView(item,new LinearLayout.LayoutParams(-2,dp(49)));
        item.setOnClickListener(v->{
            PopupMenu menu=new PopupMenu(activity,item);
            for(String command:commands)menu.getMenu().add(command);
            menu.setOnMenuItemClickListener(p->{action.select(p.getTitle().toString());return true;});
            menu.show();
        });
    }
    private void fileCommand(String cmd){
        switch(cmd){
            case "Novo":newDialog();break;
            case "Abrir projeto":openProjects();break;
            case "Importar camada":host.importImage();break;
            case "Salvar .noise":scheduleSaveNow();break;
            case "Exportar PNG":export();break;
            case "Fechar conteúdo inteligente":if(parentSmartDocument!=null)closeSmartEditor();else message("Nenhum objeto inteligente aberto");break;
            case "Voltar":onBack();break;
        }
    }
    private void layerCommand(String cmd){
        StudioDocument.Layer active=document.active();
        switch(cmd){
            case "Nova camada":document.add("Camada "+(document.layers.size()+1),null);break;
            case "Duplicar":if(active!=null)document.duplicate();break;
            case "Excluir":document.remove();break;
            case "Para cima":document.moveOrder(1);break;
            case "Para baixo":document.moveOrder(-1);break;
            case "Texto editável":textDialog();return;
            case "Retângulo":document.addShape(StudioDocument.Type.RECTANGLE,foreground,200,160);break;
            case "Elipse":document.addShape(StudioDocument.Type.ELLIPSE,foreground,200,160);break;
            case "Transformar":activateTool(Tool.TRANSFORM);transformDialog();return;
            case "Mesclagem":showTextPanel=false;showTransformPanel=false;
                updatePanels();blendDialog();return;
            case "Editar conteúdo inteligente":openSmartEditor(active);return;
            case "Converter em objeto inteligente":
                if(active==null||active.locked||active.type!=StudioDocument.Type.RASTER){
                    message("Selecione uma camada raster desbloqueada");return;
                }
                document.checkpoint();
                StudioDocument inside=StudioDocument.blank(active.name,
                    active.bitmap.getWidth(),active.bitmap.getHeight(),true);
                inside.add("Conteúdo",active.bitmap.copy(Bitmap.Config.ARGB_8888,true));
                inside.markSaved();active.smartContent=inside;
                active.type=StudioDocument.Type.SMART;break;
            case "Rasterizar objeto inteligente":
                if(active==null||active.locked||active.type!=StudioDocument.Type.SMART){
                    message("Selecione um objeto inteligente incorporado");return;
                }
                document.checkpoint();active.type=StudioDocument.Type.RASTER;
                active.smartContent=null;break;
            case "Painel":case "Camadas":showLayers=!showLayers;
                propertyPanel.setVisibility(showLayers?VISIBLE:GONE);return;
        }
        changed(true);
    }
    private void openSmartEditor(StudioDocument.Layer owner){
        if(owner==null||owner.type!=StudioDocument.Type.SMART||owner.locked){
            message("Selecione um objeto inteligente desbloqueado");return;
        }
        if(parentSmartDocument!=null){message("Feche primeiro o conteúdo atual");return;}
        if(owner.smartContent==null){
            StudioDocument content=StudioDocument.blank(owner.name,
                owner.bitmap.getWidth(),owner.bitmap.getHeight(),true);
            content.add("Conteúdo",owner.bitmap.copy(Bitmap.Config.ARGB_8888,true));
            content.markSaved();owner.smartContent=content;
        }
        parentSmartDocument=document;parentSmartDocument.checkpoint();smartOwnerId=owner.id;
        document=owner.smartContent.snapshot();
        editingMaskId=null;paintingMask=false;
        selectedTool=Tool.BRUSH;showTextPanel=false;showTransformPanel=false;
        updateUi();message("Editando conteúdo inteligente • Arquivo > Fechar conteúdo");
    }
    private void syncSmartEditor(){
        if(parentSmartDocument==null)return;
        StudioDocument.Layer owner=null;
        for(StudioDocument.Layer l:parentSmartDocument.layers)
            if(l.id.equals(smartOwnerId)){owner=l;break;}
        if(owner==null)return;
        owner.smartContent=document.snapshot();
        owner.bitmap=document.composite();
    }
    private void closeSmartEditor(){
        if(parentSmartDocument==null)return;
        syncSmartEditor();
        document=parentSmartDocument;parentSmartDocument=null;smartOwnerId=null;
        editingMaskId=null;paintingMask=false;
        changed(true);message("Conteúdo inteligente atualizado");
    }
    private void selectMask(StudioDocument.Layer layer,boolean mask){
        if(layer==null)return;
        document.activeId=layer.id;
        paintingMask=mask&&layer.mask!=null;
        editingMaskId=paintingMask?layer.id:null;
        if(paintingMask)selectedTool=Tool.BRUSH;
        updateUi();
        message(paintingMask?"Pintando na máscara (branco revela; preto oculta)":
            "Editando pixels da camada");
    }
    private void blendDialog(){
        StudioDocument.Layer layer=document.active();
        if(layer==null||layer.locked)return;
        PopupMenu popup=new PopupMenu(activity,blendButton);
        for(StudioDocument.Blend mode:StudioDocument.Blend.values())
            popup.getMenu().add(0,mode.ordinal(),mode.ordinal(),
                (mode==layer.blend?"✓ ":"")+blendName(mode));
        popup.setOnMenuItemClickListener(item->{
            StudioDocument.Blend mode=StudioDocument.Blend.values()[item.getItemId()];
            if(layer.blend!=mode){
                document.checkpoint();layer.blend=mode;changed(true);
            }
            return true;
        });
        popup.show();
    }
    private void maskCommand(String cmd){
        StudioDocument.Layer l=document.active();
        if(l==null||l.locked){message("Selecione uma camada desbloqueada");return;}
        try{
            switch(cmd){
                case "Criar máscara branca":
                    StudioLayerEffects.createMask(document,true);
                    selectMask(l,true);break;
                case "Criar máscara preta":
                    StudioLayerEffects.createMask(document,false);
                    selectMask(l,true);break;
                case "Editar máscara":
                    if(l.mask==null)StudioLayerEffects.createMask(document,true);
                    selectMask(l,true);return;
                case "Editar pixels":selectMask(l,false);return;
                case "Pintar preto":
                    if(l.mask==null)StudioLayerEffects.createMask(document,true);
                    selectMask(l,true);activateTool(Tool.MASK_HIDE);return;
                case "Pintar branco":
                    if(l.mask==null)StudioLayerEffects.createMask(document,true);
                    selectMask(l,true);activateTool(Tool.MASK_REVEAL);return;
                case "Aplicar máscara":
                    StudioLayerEffects.applyMask(document);
                    paintingMask=false;editingMaskId=null;break;
                case "Vincular/desvincular":
                    if(l.mask==null)throw new IllegalStateException("Camada sem máscara");
                    document.checkpoint();l.maskLinked=!l.maskLinked;break;
                case "Visualizar máscara":
                    if(l.mask==null)throw new IllegalStateException("Camada sem máscara");
                    new AlertDialog.Builder(activity).setTitle("Prévia da máscara")
                        .setView(new MaskThumb(activity,l)).setPositiveButton("Fechar",null).show();
                    return;
                case "Inverter máscara":StudioLayerEffects.invertMask(document);break;
                case "Ativar/desativar":
                    if(l.mask==null)throw new IllegalStateException("Camada sem máscara");
                    document.checkpoint();l.maskEnabled=!l.maskEnabled;break;
                case "Excluir máscara":
                    if(l.mask==null)return;
                    document.checkpoint();l.mask=null;
                    paintingMask=false;editingMaskId=null;break;
                case "Densidade":
                    if(l.mask==null)throw new IllegalStateException("Camada sem máscara");
                    maskDensityDialog(l);return;
                default:return;
            }
            changed(true);
        }catch(Exception e){message(e.getMessage());}
    }
    private void maskDensityDialog(StudioDocument.Layer l){
        showTextPanel=false;showTransformPanel=false;
        editingEffects=true;editingShadow=false;selectedFilterIndex=-2;
        updatePanels();
    }
    private void toggleClippingMask(StudioDocument.Layer layer){
        if(layer==null||layer.locked){message("Selecione uma camada desbloqueada");return;}
        if(!layer.clippingMask&&document.layers.indexOf(layer)==0){
            message("A máscara de corte precisa de uma camada abaixo");return;
        }
        document.checkpoint();
        layer.clippingMask=!layer.clippingMask;
        changed(true);
        message(layer.clippingMask?"Máscara de corte ativada":"Máscara de corte removida");
    }
    private void filterCommand(String cmd){
        StudioDocument.Layer layer=document.active();
        if(layer==null||layer.locked){message("Selecione uma camada desbloqueada");return;}
        if("Adicionar filtro".equals(cmd)){addFilterDialog(layer);return;}
        manageFilterDialog(layer);
    }
    private void addFilterDialog(StudioDocument.Layer layer){
        if(layer.effects.size()>=8){message("Limite de 8 filtros por camada");return;}
        if((long)layer.naturalWidth()*layer.naturalHeight()>
            StudioLayerEffects.MAX_EFFECT_PIXELS){
            message("Filtros limitados a 2 MP por camada nesta versão");return;
        }
        String[] names={"Desfoque gaussiano","Brilho","Saturação"};
        new AlertDialog.Builder(activity).setTitle("Novo filtro inteligente")
            .setItems(names,(dialog,index)->{
                StudioLayerEffects.Kind kind=index==0?
                    StudioLayerEffects.Kind.GAUSSIAN_BLUR:index==1?
                    StudioLayerEffects.Kind.BRIGHTNESS:StudioLayerEffects.Kind.SATURATION;
                parameterDialog(layer,new StudioLayerEffects.Effect(kind,index==0?3:0),-1);
            }).show();
    }
    private void manageFilterDialog(StudioDocument.Layer layer){
        if(layer.effects.isEmpty()){message("Esta camada não tem filtros");return;}
        String[] names=new String[layer.effects.size()];
        for(int i=0;i<names.length;i++){
            StudioLayerEffects.Effect e=layer.effects.get(i);
            names[i]=(e.enabled?"● ":"○ ")+e.kind.name()+" · "+e.value;
        }
        new AlertDialog.Builder(activity).setTitle("Pilha de filtros (ordem de cima para baixo)")
            .setItems(names,(d,index)->filterActions(layer,index)).show();
    }
    private void filterActions(StudioDocument.Layer layer,int index){
        new AlertDialog.Builder(activity).setTitle("Filtro "+(index+1))
            .setItems(new String[]{"Editar intensidade","Ativar / desativar","Subir","Descer","Excluir"},
                (d,choice)->{
                    if(index>=layer.effects.size())return;
                    StudioLayerEffects.Effect effect=layer.effects.get(index);
                    if(choice==0){parameterDialog(layer,effect,index);return;}
                    if((choice==2&&index==0)||
                        (choice==3&&index==layer.effects.size()-1))return;
                    document.checkpoint();
                    if(choice==1)effect.enabled=!effect.enabled;
                    else if(choice==2)Collections.swap(layer.effects,index,index-1);
                    else if(choice==3)Collections.swap(layer.effects,index,index+1);
                    else if(choice==4)layer.effects.remove(index);
                    changed(true);
                }).show();
    }
    private void parameterDialog(StudioDocument.Layer layer,
                                 StudioLayerEffects.Effect current,int index){
        if(layer.locked)return;
        if(index<0){
            document.checkpoint();
            layer.effects.add(current.copy());index=layer.effects.size()-1;
            changed(true);
        }
        editingEffects=true;editingShadow=false;editingStyle=false;selectedFilterIndex=index;
        showTextPanel=false;showTransformPanel=false;
        updatePanels();
    }
    private void addStyleMenu(StudioDocument.Layer layer){
        if(layer==null||layer.locked){message("Selecione uma camada desbloqueada");return;}
        StudioLayerStyles.Kind[] kinds=StudioLayerStyles.Kind.values();
        String[] choices=new String[kinds.length];
        for(int i=0;i<kinds.length;i++)choices[i]=kinds[i].title;
        new AlertDialog.Builder(activity).setTitle("Adicionar estilo de camada")
            .setItems(choices,(d,index)->addStyle(layer,kinds[index]))
            .setNegativeButton("Cancelar",null).show();
    }
    private void addStyle(StudioDocument.Layer layer,StudioLayerStyles.Kind kind){
        if(layer.styles.size()>=StudioLayerStyles.MAX_STYLES){
            message("Limite de 10 estilos por camada");return;
        }
        if((long)layer.naturalWidth()*layer.naturalHeight()>
            StudioLayerStyles.MAX_PIXELS){
            message("Estilos limitados a 2 MP por camada");return;
        }
        document.checkpoint();
        layer.styles.add(new StudioLayerStyles.Style(kind));
        layer.showStyles=true;
        changed(true);
        openStyle(layer,layer.styles.size()-1);
    }
    private void openStyle(StudioDocument.Layer layer,int index){
        document.activeId=layer.id;
        editingEffects=true;editingShadow=false;editingStyle=true;
        selectedStyleIndex=index;selectedFilterIndex=-1;
        showTextPanel=false;showTransformPanel=false;
        updateUi();updatePanels();
    }
    private void styleChoice(LinearLayout into,StudioDocument.Layer layer,
                             String label,String[] names,String[] ids,
                             String current,java.util.function.Consumer<String> onPick){
        LinearLayout row=new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(9),0,dp(7),0);
        row.setBackgroundColor(0xff27222d);
        TextView heading=text(label,12,ink);
        row.addView(heading,new LinearLayout.LayoutParams(0,dp(39),1));
        int pos=Arrays.asList(ids).indexOf(current);
        TextView selected=action(pos<0?current:names[pos]+"  ⌄",()->{});
        selected.setTextSize(11);selected.setTextColor(accent);
        row.addView(selected,new LinearLayout.LayoutParams(dp(125),dp(39)));
        selected.setOnClickListener(v->{
            PopupMenu menu=new PopupMenu(activity,selected);
            for(int k=0;k<ids.length;k++)
                menu.getMenu().add(0,k,k,names[k]);
            menu.setOnMenuItemClickListener(item->{
                if(layer.locked)return true;
                document.checkpoint();onPick.accept(ids[item.getItemId()]);
                changed(true);updatePanels();return true;
            });menu.show();
        });
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(39));
        lp.bottomMargin=dp(4);into.addView(row,lp);
    }
    private void styleValue(StudioDocument.Layer layer,String title,String unit,
                            float value,float min,float max,float step,
                            java.util.function.DoubleConsumer setter){
        inspectorField(effectControls,layer,title,unit,value,min,max,step,
            (l,v)->setter.accept(v),false);
    }
    private void styleColor(StudioDocument.Layer layer,String title,int color,
                            java.util.function.IntConsumer setter){
        LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8),0,dp(8),0);
        row.setBackgroundColor(0xff27222d);
        TextView name=text(title,12,ink);
        row.addView(name,new LinearLayout.LayoutParams(0,dp(42),1));
        TextView swatch=action("■  Selecionar",()->{});
        swatch.setTextColor(color);
        row.addView(swatch,new LinearLayout.LayoutParams(dp(125),dp(42)));
        swatch.setOnClickListener(v->StudioColorPicker.show(activity,title,color,chosen->{
            if(layer.locked)return;
            document.checkpoint();setter.accept(chosen);changed(true);
        }));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(43));
        lp.bottomMargin=dp(4);effectControls.addView(row,lp);
    }
    private void styleToggle(StudioDocument.Layer layer,String label,
                             boolean current,java.util.function.Consumer<Boolean> setter){
        CheckBox cb=new CheckBox(activity);
        cb.setText(label);cb.setTextColor(ink);cb.setChecked(current);cb.setTextSize(12);
        cb.setOnCheckedChangeListener((button,checked)->{
            if(layer.locked)return;
            document.checkpoint();setter.accept(checked);changed(true);
        });
        effectControls.addView(cb,new LinearLayout.LayoutParams(-1,dp(43)));
    }
    private void editStyleControls(StudioDocument.Layer layer,StudioLayerStyles.Style style){
        textTitleTo(effectControls,"ESTILO DE CAMADA · "+style.kind.title.toUpperCase(Locale.ROOT));
        styleToggle(layer,"Ativar efeito",style.enabled,v->style.enabled=v);
        textTitleTo(effectControls,"ESTRUTURA");
        String[] blendNames={"Normal","Multiplicar","Tela","Sobrepor","Escurecer","Clarear","Adicionar"};
        String[] blends={"NORMAL","MULTIPLY","SCREEN","OVERLAY","DARKEN","LIGHTEN","ADD"};
        styleChoice(effectControls,layer,"Modo de mesclagem",
            blendNames,blends,style.blend,v->style.blend=v);
        styleValue(layer,"Opacidade","%",style.opacity*100,0,100,1,v->style.opacity=(float)(v/100));
        switch(style.kind){
            case COLOR_OVERLAY:
                styleColor(layer,"Cor",style.color,v->style.color=v);break;
            case GRADIENT_OVERLAY:
                styleColor(layer,"Cor inicial",style.color,v->style.color=v);
                styleColor(layer,"Cor final",style.color2,v->style.color2=v);
                styleValue(layer,"Ângulo","°",style.angle,-180,180,1,v->style.angle=(float)v);
                styleValue(layer,"Escala","%",style.gradientScale,10,400,5,
                    v->style.gradientScale=(float)v);
                styleToggle(layer,"Inverter degradê",style.reverse,v->style.reverse=v);
                break;
            case STROKE:
                styleColor(layer,"Cor do contorno",style.color,v->style.color=v);
                styleValue(layer,"Espessura","px",style.size,1,64,1,v->style.size=(float)v);
                styleChoice(effectControls,layer,"Posição",
                    new String[]{"Fora","Dentro","Centro"},
                    new String[]{"OUTSIDE","INSIDE","CENTER"},style.position,
                    v->style.position=v);
                break;
            case INNER_GLOW:case OUTER_GLOW:
                styleColor(layer,"Cor do brilho",style.color,v->style.color=v);
                styleValue(layer,"Tamanho","px",style.size,1,128,1,v->style.size=(float)v);
                styleValue(layer,style.kind==StudioLayerStyles.Kind.OUTER_GLOW?"Expansão":"Contração",
                    "%",style.spread,0,100,1,v->style.spread=(float)v);
                if(style.kind==StudioLayerStyles.Kind.INNER_GLOW)
                    styleChoice(effectControls,layer,"Origem",
                        new String[]{"Bordas","Centro"},
                        new String[]{"EDGE","CENTER"},style.sourceCenter?"CENTER":"EDGE",
                        v->style.sourceCenter=v.equals("CENTER"));
                break;
            case DROP_SHADOW:case INNER_SHADOW:
                styleColor(layer,"Cor da sombra",style.color,v->style.color=v);
                styleValue(layer,"Ângulo","°",style.angle,-180,180,1,v->style.angle=(float)v);
                styleValue(layer,"Distância","px",style.distance,0,128,1,v->style.distance=(float)v);
                styleValue(layer,"Tamanho","px",style.size,1,128,1,v->style.size=(float)v);
                styleValue(layer,"Expansão","%",style.spread,0,100,1,v->style.spread=(float)v);
                break;
            case BEVEL_EMBOSS:
                styleChoice(effectControls,layer,"Estilo",
                    new String[]{"Bisel interno","Bisel externo","Relevo","Relevo almofada"},
                    new String[]{"INNER_BEVEL","OUTER_BEVEL","EMBOSS","PILLOW"},
                    style.bevelStyle,v->style.bevelStyle=v);
                styleChoice(effectControls,layer,"Técnica",
                    new String[]{"Suave","Cinzel duro","Cinzel suave"},
                    new String[]{"SMOOTH","CHISEL_HARD","CHISEL_SOFT"},
                    style.technique,v->style.technique=v);
                styleChoice(effectControls,layer,"Direção",new String[]{"Acima","Abaixo"},
                    new String[]{"UP","DOWN"},style.direction,v->style.direction=v);
                styleValue(layer,"Profundidade","%",style.depth,0,1000,10,v->style.depth=(float)v);
                styleValue(layer,"Tamanho","px",style.size,1,128,1,v->style.size=(float)v);
                styleValue(layer,"Suavização","px",style.soften,0,64,1,v->style.soften=(float)v);
                styleValue(layer,"Ângulo da luz","°",style.angle,-180,180,1,v->style.angle=(float)v);
                styleValue(layer,"Altitude da luz","°",style.altitude,0,90,1,v->style.altitude=(float)v);
                styleColor(layer,"Luz",style.highlight,v->style.highlight=v);
                styleValue(layer,"Opac. da luz","%",style.highlightOpacity*100,
                    0,100,1,v->style.highlightOpacity=(float)(v/100));
                styleColor(layer,"Sombra",style.shade,v->style.shade=v);
                styleValue(layer,"Opac. sombra","%",style.shadowOpacity*100,
                    0,100,1,v->style.shadowOpacity=(float)(v/100));
                break;
        }
        if(style.kind==StudioLayerStyles.Kind.BEVEL_EMBOSS||
            style.kind==StudioLayerStyles.Kind.INNER_GLOW||
            style.kind==StudioLayerStyles.Kind.OUTER_GLOW){
            textTitleTo(effectControls,"QUALIDADE · CURVAS");
            styleChoice(effectControls,layer,"Contorno",
                new String[]{"Linear","Arredondado","Agudo","Cone","Anel","Meia curva","Personalizado"},
                new String[]{"LINEAR","ROUND","SHARP","CONE","RING","HALF_ROUND","CUSTOM"},
                style.contour,v->style.contour=v);
            final boolean[] started={false};
            StudioContourEditor curve=new StudioContourEditor(activity,style,
                new StudioContourEditor.Callback(){
                    public void begin(){if(!started[0]){document.checkpoint();started[0]=true;}}
                    public void preview(){canvas.invalidate();}
                    public void finish(){if(started[0]){started[0]=false;changed(true);}}
                });
            effectControls.addView(curve,new LinearLayout.LayoutParams(-1,dp(128)));
            styleToggle(layer,"Suavizar curva",style.antiAlias,v->style.antiAlias=v);
        }
        effectControls.addView(action("Excluir este estilo",()->{
            if(layer.locked)return;
            document.checkpoint();layer.styles.remove(style);editingEffects=false;
            editingStyle=false;selectedStyleIndex=-1;changed(true);
        }));
    }
    private void styleCommand(String cmd){
        StudioDocument.Layer layer=document.active();
        if(layer==null||layer.locked){message("Selecione uma camada desbloqueada");return;}
        if("Adicionar estilo".equals(cmd)){addStyleMenu(layer);return;}
        for(StudioLayerStyles.Kind kind:StudioLayerStyles.Kind.values()){
            if(kind.title.equals(cmd)){addStyle(layer,kind);return;}
        }
        if("Remover sombra".equals(cmd)){
            if(layer.shadowEnabled){
                document.checkpoint();layer.shadowEnabled=false;changed(true);
            }
            return;
        }
        if((long)layer.naturalWidth()*layer.naturalHeight()>
            StudioLayerEffects.MAX_EFFECT_PIXELS){
            message("Sombra limitada a 2 MP nesta versão");return;
        }
        editingEffects=true;editingShadow=true;editingStyle=false;selectedFilterIndex=-1;
        showTextPanel=false;showTransformPanel=false;updatePanels();
    }
    private void viewCommand(String cmd){
        if(cmd.equals("Ajustar à tela")){document.zoom=1;document.panX=document.panY=0;canvas.invalidate();}
        else if(cmd.equals("Zoom +"))canvas.zoomBy(1.25f);
        else if(cmd.equals("Zoom −"))canvas.zoomBy(.8f);
        else if(cmd.equals("Camadas")){showTextPanel=false;showTransformPanel=false;updatePanels();}
        else if(cmd.equals("Texto")){showTextPanel=true;showTransformPanel=false;updatePanels();}
        info.setText(document.width+"×"+document.height+" • "+Math.round(document.zoom*100)+"%");
    }
    /** Layer thumbnails show actual source pixels, never an invented mock preview. */
    private final class LayerThumb extends View {
        private final StudioDocument.Layer layer;
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        LayerThumb(Context context,StudioDocument.Layer l){super(context);layer=l;
            setContentDescription(l.type==StudioDocument.Type.SMART?
                "Objeto inteligente incorporado":l.type==StudioDocument.Type.RASTER?
                "Camada raster":l.type==StudioDocument.Type.TEXT?"Camada de texto":"Forma vetorial");
        }
        @Override protected void onDraw(Canvas c){
            float w=getWidth(),h=getHeight(),pad=dp(4);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xff32303a);c.drawRoundRect(1,1,w-1,h-1,dp(5),dp(5),p);
            p.setColor(0xff54505b);
            int side=dp(7);
            for(int y=2;y<h-2;y+=side)for(int x=2;x<w-2;x+=side)
                if(((x/side)+(y/side))%2==0)
                    c.drawRect(x,y,Math.min(x+side,w-2),Math.min(y+side,h-2),p);
            float availW=w-pad*2,availH=h-pad*2;
            int nw=Math.max(1,layer.naturalWidth()),nh=Math.max(1,layer.naturalHeight());
            float scale=Math.min(availW/nw,availH/nh);
            float dw=nw*scale,dh=nh*scale;
            float left=(w-dw)/2,top=(h-dh)/2;
            if((layer.type==StudioDocument.Type.RASTER||
                layer.type==StudioDocument.Type.SMART)&&layer.bitmap!=null){
                p.setColor(Color.WHITE);
                c.drawBitmap(layer.bitmap,null,new RectF(left,top,left+dw,top+dh),p);
            }else if(layer.type==StudioDocument.Type.TEXT){
                c.save();c.translate(left,top);c.scale(scale,scale);
                p.setColor(layer.color);StudioTypography.draw(c,layer,p);c.restore();
            }else{
                p.setColor(layer.color);
                if(layer.type==StudioDocument.Type.ELLIPSE)
                    c.drawOval(left,top,left+dw,top+dh,p);
                else c.drawRect(left,top,left+dw,top+dh,p);
            }
            // Corner status: S = smart, R = raster, T = text, V = vector shape.
            String mark=layer.type==StudioDocument.Type.SMART?"S":
                layer.type==StudioDocument.Type.RASTER?"R":
                layer.type==StudioDocument.Type.TEXT?"T":"V";
            p.setColor(layer.type==StudioDocument.Type.SMART?0xffa679e5:0xff22202b);
            c.drawRoundRect(w-dp(17),h-dp(17),w-dp(2),h-dp(2),dp(3),dp(3),p);
            p.setColor(Color.WHITE);p.setTextSize(dp(10));
            p.setTypeface(Typeface.DEFAULT_BOLD);
            c.drawText(mark,w-dp(13),h-dp(5),p);
        }
    }
    private final class MaskThumb extends View{
        private final StudioDocument.Layer owner;
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        MaskThumb(Context ctx,StudioDocument.Layer l){super(ctx);owner=l;
            setContentDescription("Miniatura da máscara; tocar para editar");}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            if(owner.mask==null)return;
            p.setColor(0xffffffff);p.setStyle(Paint.Style.FILL);
            c.drawRoundRect(2,2,getWidth()-2,getHeight()-2,dp(4),dp(4),p);
            p.setColor(Color.WHITE);
            c.drawBitmap(owner.mask,null,new Rect(4,4,getWidth()-4,getHeight()-4),p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2));
            p.setColor(paintingMask&&owner.id.equals(editingMaskId)?accent:0xff554961);
            c.drawRect(1,1,getWidth()-1,getHeight()-1,p);
            p.setStyle(Paint.Style.FILL);
        }
    }
    private void updateUi(){
        if(closed)return;
        docTab.setText((parentSmartDocument!=null?"◆ ":"")+document.name+"  •  "+(document.dirty?"● ":"")+".noise");
        info.setText(document.width+"×"+document.height+" • "+Math.round(document.zoom*100)+"%");
        layersList.removeAllViews();
        for(int i=document.layers.size()-1;i>=0;i--){
            StudioDocument.Layer layer=document.layers.get(i);
            LinearLayout row=new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(layer.id.equals(document.activeId)?0xff34293d:panel);
            if(layer.clippingMask)row.setPadding(dp(11),0,0,0);
            TextView visibility=action(layer.visible?"◉":"○",()->{
                document.checkpoint();layer.visible=!layer.visible;changed(true);
            });
            row.addView(visibility,new LinearLayout.LayoutParams(dp(34),dp(40)));
            LayerThumb thumbnail=new LayerThumb(activity,layer);
            row.addView(thumbnail,new LinearLayout.LayoutParams(dp(49),dp(43)));
            thumbnail.setOnClickListener(v->selectMask(layer,false));
            if(layer.type==StudioDocument.Type.SMART)
                thumbnail.setOnLongClickListener(v->{openSmartEditor(layer);return true;});
            if(layer.mask!=null){
                MaskThumb maskPreview=new MaskThumb(activity,layer);
                row.addView(maskPreview,new LinearLayout.LayoutParams(dp(41),dp(40)));
                maskPreview.setOnClickListener(v->selectMask(layer,true));
            }
            TextView title=text((layer.clippingMask?"↓ ":"")+
                (layer.type==StudioDocument.Type.SMART?"◆ ":"")+layer.name,12,ink);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(title,new LinearLayout.LayoutParams(0,dp(40),1));
            title.setOnClickListener(v->selectMask(layer,false));
            title.setOnLongClickListener(v->{
                document.activeId=layer.id;
                updateUi();
                if(layer.type==StudioDocument.Type.TEXT)textDialog();
                else rename(layer);
                return true;});
            TextView lock=action(layer.locked?"L":"—",()->{
                document.checkpoint();layer.locked=!layer.locked;changed(true);
            });
            row.addView(lock,new LinearLayout.LayoutParams(dp(31),dp(40)));
            if(layer.mask!=null||!layer.effects.isEmpty()||layer.shadowEnabled||!layer.styles.isEmpty()){
                TextView extras=text((layer.mask!=null?"M":"")+
                    (!layer.effects.isEmpty()||!layer.styles.isEmpty()?" fx":"")+
                    (layer.shadowEnabled?" ░":""),10,accent);
                row.addView(extras,new LinearLayout.LayoutParams(dp(36),dp(40)));
            }
            layersList.addView(row,new LinearLayout.LayoutParams(-1,dp(43)));
            if(!layer.styles.isEmpty()||layer.shadowEnabled){
                LinearLayout stylesHeader=new LinearLayout(activity);
                stylesHeader.setGravity(Gravity.CENTER_VERTICAL);
                stylesHeader.setPadding(dp(20),0,dp(8),0);
                stylesHeader.setBackgroundColor(0xff201b26);
                stylesHeader.addView(action(layer.showStyles?"⌄":"›",()->{
                    layer.showStyles=!layer.showStyles;updateUi();
                }),new LinearLayout.LayoutParams(dp(29),dp(31)));
                TextView fxTitle=text("fx  Efeitos",11,accent);
                stylesHeader.addView(fxTitle,new LinearLayout.LayoutParams(0,dp(31),1));
                fxTitle.setOnClickListener(v->{layer.showStyles=!layer.showStyles;updateUi();});
                layersList.addView(stylesHeader,new LinearLayout.LayoutParams(-1,dp(31)));
                if(layer.showStyles){
                    for(int k=0;k<layer.styles.size();k++){
                        final int index=k;
                        StudioLayerStyles.Style style=layer.styles.get(k);
                        LinearLayout styleRow=new LinearLayout(activity);
                        styleRow.setGravity(Gravity.CENTER_VERTICAL);
                        styleRow.setPadding(dp(46),0,dp(9),0);
                        styleRow.setBackgroundColor(0xff19151f);
                        TextView eye=action(style.enabled?"◉":"○",()->{
                            if(layer.locked)return;
                            document.checkpoint();style.enabled=!style.enabled;changed(true);
                        });
                        styleRow.addView(eye,new LinearLayout.LayoutParams(dp(34),dp(33)));
                        TextView styleLabel=text(style.kind.title,11,style.enabled?ink:muted);
                        styleRow.addView(styleLabel,new LinearLayout.LayoutParams(0,dp(33),1));
                        styleLabel.setGravity(Gravity.CENTER_VERTICAL);
                        styleLabel.setOnClickListener(v->openStyle(layer,index));
                        layersList.addView(styleRow,new LinearLayout.LayoutParams(-1,dp(33)));
                    }
                    if(layer.shadowEnabled){
                        TextView legacy=action("      ◉ Sombra legada",()->styleCommand("Sombra projetada"));
                        layersList.addView(legacy,new LinearLayout.LayoutParams(-1,dp(33)));
                    }
                }
            }
        }
        StudioDocument.Layer current=document.active();
        opacity.setEnabled(current!=null&&!current.locked);
        opacity.setProgress(current==null?100:Math.round(current.opacity*100));
        if(!opacityInput.hasFocus())opacityInput.setText(String.valueOf(opacity.getProgress()));
        blendButton.setText(current==null?"Normal  ⌄":blendName(current.blend)+"  ⌄");
        // Defer inspector reconstruction to the next UI idle slice.
        // Rebuilding EditTexts synchronously during a slider/IME callback can
        // detach its focused view and recursively trigger edit commits.
        main.removeCallbacks(deferredPanelRefresh);
        main.postDelayed(deferredPanelRefresh,96);
        updateToolbar();canvas.invalidate();
    }
    private void changed(boolean persistent){
        revision++;
        updateUi();
        if(persistent)scheduleSave();
    }
    private void rename(StudioDocument.Layer layer){
        EditText input=new EditText(activity);input.setSingleLine(true);
        input.setText(layer.name);
        new AlertDialog.Builder(activity).setTitle("Renomear camada")
            .setView(input).setNegativeButton("Cancelar",null)
            .setPositiveButton("Renomear",(dialog,which)->{
                String name=input.getText().toString().trim();
                if(!name.isEmpty()){document.checkpoint();layer.name=name;changed(true);}
            }).show();
    }
    private void newDialog(){
        LinearLayout inputs=new LinearLayout(activity);
        inputs.setPadding(dp(22),dp(5),dp(22),dp(5));
        inputs.setOrientation(LinearLayout.VERTICAL);
        EditText name=numberField("Nome",false);
        EditText w=numberField("Largura",true),h=numberField("Altura",true);
        name.setText("Novo documento");w.setText("1080");h.setText("1080");
        inputs.addView(name);inputs.addView(w);inputs.addView(h);
        CheckBox transparent=new CheckBox(activity);
        transparent.setText("Fundo transparente");transparent.setChecked(true);
        inputs.addView(transparent);
        new AlertDialog.Builder(activity).setTitle("Novo documento")
            .setView(inputs).setNegativeButton("Cancelar",null)
            .setPositiveButton("Criar",(dialog,which)->{
                try {
                    int width=Integer.parseInt(w.getText().toString());
                    int height=Integer.parseInt(h.getText().toString());
                    if(!StudioDocument.validSize(width,height))
                        throw new IllegalArgumentException("Dimensões inválidas");
                    if(document.dirty)scheduleSaveNow();
                    document=StudioDocument.blank(name.getText().toString(),
                        width,height,transparent.isChecked());
                    revision++;updateUi();scheduleSave();
                }catch(Exception e){message("Use dimensões válidas (até 16 megapixels).");}
            }).show();
    }
    private EditText numberField(String hint,boolean numeric){
        EditText field=new EditText(activity);
        field.setHint(hint);
        field.setSingleLine(true);
        if(numeric)field.setInputType(InputType.TYPE_CLASS_NUMBER);
        return field;
    }
    private void openProjects(){
        ArrayList<String> ids=store.list();
        if(ids.isEmpty()){message("Nenhum projeto Advanced Studio salvo");return;}
        String[] options=ids.toArray(new String[0]);
        new AlertDialog.Builder(activity).setTitle("Projetos Advanced Studio")
            .setItems(options,(dialog,index)->{
                if(document.dirty)scheduleSaveNow();
                String id=options[index];
                io.execute(()->{
                    try{
                        StudioDocument loaded=store.load(id);
                        main.post(()->{if(closed)return;
                            document=loaded;revision++;updateUi();
                            message("Projeto .noise restaurado");
                        });
                    }catch(Exception e){main.post(()->message("Falha ao abrir: "+e.getMessage()));}
                });
            }).show();
    }
    public void addRasterUri(Uri uri) {
        if(closed||uri==null)return;
        // Decoding is off the UI thread, and bounds are checked before allocation.
        io.execute(()->{
            try(InputStream in=activity.getContentResolver().openInputStream(uri)){
                if(in==null)throw new java.io.IOException("Imagem indisponível");
                android.graphics.BitmapFactory.Options opt=new android.graphics.BitmapFactory.Options();
                opt.inJustDecodeBounds=true;
                android.graphics.BitmapFactory.decodeStream(in,null,opt);
                if(opt.outWidth<1||opt.outHeight<1||
                   (long)opt.outWidth*opt.outHeight>StudioDocument.MAX_PIXELS)
                    throw new java.io.IOException("Imagem excede limite de memória nesta fase");
                Bitmap bmp;
                try(InputStream source=activity.getContentResolver().openInputStream(uri)){
                    bmp=android.graphics.BitmapFactory.decodeStream(source);
                }
                if(bmp==null)throw new java.io.IOException("Formato não suportado");
                main.post(()->{
                    if(closed){bmp.recycle();return;}
                    document.add("Imagem "+(document.layers.size()+1),bmp);
                    changed(true);
                });
            }catch(Exception e){main.post(()->message("Falha ao importar: "+e.getMessage()));}
        });
    }
    private final Runnable autosave=()->scheduleSaveNow();
    private void scheduleSave(){
        main.removeCallbacks(autosave);
        main.postDelayed(autosave,1700);
    }
    private void scheduleSaveNow(){
        if(closed)return;
        main.removeCallbacks(autosave);
        if(parentSmartDocument!=null)syncSmartEditor();
        final StudioDocument snapshot=(parentSmartDocument!=null?
            parentSmartDocument:document).snapshot();
        final StudioDocument original=document;
        final int stamp=revision;
        io.execute(()->{
            try{
                store.save(snapshot);
                main.post(()->{
                    if(!closed&&document==original&&revision==stamp){
                        savedRevision=stamp;document.markSaved();updateUi();
                    }
                });
            }catch(Exception e){main.post(()->message("Não foi possível salvar .noise: "+e.getMessage()));}
        });
    }
    private void export(){
        if(parentSmartDocument!=null)syncSmartEditor();
        final StudioDocument snapshot=(parentSmartDocument!=null?
            parentSmartDocument:document).snapshot();
        io.execute(()->{
            try{
                store.exportPng(snapshot);
                main.post(()->message("PNG salvo em Pictures/Noise!"));
            }catch(Exception e){main.post(()->message("Exportação falhou: "+e.getMessage()));}
        });
    }
    private void message(String value){
        if(!closed)Toast.makeText(activity,value,Toast.LENGTH_SHORT).show();
    }
    public void onBack(){
        if(parentSmartDocument!=null){closeSmartEditor();return;}
        if(document.dirty)scheduleSaveNow();
        host.exit();
    }
    public void dispose(){
        main.removeCallbacks(autosave);
        main.removeCallbacks(deferredPanelRefresh);
        // Saves already queued execute to completion; never recycle shared
        // layer bitmaps while a background save/export may be using them.
        closed=true;
        io.shutdown();
    }
    private final class StudioCanvas extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ScaleGestureDetector zoomDetector;
        private boolean gestureMoving,gestureUsed;
        private int transformHandle;
        private float originalX,originalY,originalScaleX,originalScaleY,originalRotation;
        private float lastX,lastY,lastMidX,lastMidY,startDocX,startDocY;
        private RectF gestureRect;
        private StudioRasterTools.Stroke stroke;
        private StudioLayerEffects.MaskStroke maskStroke;
        private Bitmap pixelAnts,selectionRef;
        private Bitmap buildPixelAnts(){
            Bitmap mask=document.selectionPixels;
            if(mask==null)return null;
            if(mask==selectionRef&&pixelAnts!=null&&!pixelAnts.isRecycled())return pixelAnts;
            selectionRef=mask;
            if(pixelAnts!=null)pixelAnts.recycle();
            int w=mask.getWidth(),h=mask.getHeight();
            int[] p=new int[w*h];mask.getPixels(p,0,w,0,0,w,h);
            int[] edges=new int[p.length];
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){
                int i=y*w+x;
                if(Color.alpha(p[i])<128)continue;
                if(x==0||y==0||x==w-1||y==h-1||
                    Color.alpha(p[i-1])<128||Color.alpha(p[i+1])<128||
                    Color.alpha(p[i-w])<128||Color.alpha(p[i+w])<128)
                    edges[i]=((x+y)/4)%2==0?0xffffffff:0xff211926;
            }
            pixelAnts=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            pixelAnts.setPixels(edges,0,w,0,0,w,h);return pixelAnts;
        }
        StudioCanvas(Context c){
            super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);
            zoomDetector=new ScaleGestureDetector(c,
                new ScaleGestureDetector.SimpleOnScaleGestureListener(){
                    @Override public boolean onScale(ScaleGestureDetector d){
                        zoomBy(d.getScaleFactor());return true;
                    }
                });
        }
        void zoomBy(float factor){
            document.zoom=Math.max(.12f,Math.min(12f,document.zoom*factor));
            info.setText(document.width+"×"+document.height+" • "+Math.round(document.zoom*100)+"%");
            invalidate();
        }
        float fit(){return Math.max(.01f,Math.min((getWidth()-dp(24))/(float)document.width,
            (getHeight()-dp(24))/(float)document.height));}
        float scale(){return fit()*document.zoom;}
        float left(){return (getWidth()-document.width*scale())*.5f+document.panX;}
        float top(){return (getHeight()-document.height*scale())*.5f+document.panY;}
        float docX(float x){return (x-left())/scale();}
        float docY(float y){return (y-top())/scale();}
        boolean inDocument(float x,float y){
            return x>=0&&x<document.width&&y>=0&&y<document.height;
        }
        private RectF activeBounds(StudioDocument.Layer l){
            return l==null?null:StudioTransformMath.bounds(l);
        }
        private void outline(Canvas canvas,RectF bounds,int color){
            if(bounds==null)return;
            paint.setColor(color);paint.setShader(null);paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(.8f,1.5f/scale()));
            canvas.drawRect(bounds,paint);
            paint.setStyle(Paint.Style.FILL);
        }
        private float[] layerPoint(StudioDocument.Layer l,float px,float py){
            return StudioTransformMath.point(l,px,py);
        }
        private float[][] transformHandles(StudioDocument.Layer l){
            float w=l.naturalWidth(),h=l.naturalHeight();
            return new float[][]{
                layerPoint(l,0,0),layerPoint(l,w/2,0),layerPoint(l,w,0),
                layerPoint(l,0,h/2),layerPoint(l,w,h/2),
                layerPoint(l,0,h),layerPoint(l,w/2,h),layerPoint(l,w,h),
                layerPoint(l,w/2,-Math.max(12,26/scale()/Math.max(.01f,Math.abs(l.scaleY))))
            };
        }
        private int findTransformHandle(StudioDocument.Layer l,float x,float y){
            if(l==null||l.locked)return 0;
            float[][] handles=transformHandles(l);
            float radius=dp(13)/scale();
            int best=0;float distance=radius*radius;
            for(int i=0;i<handles.length;i++){
                float dx=x-handles[i][0],dy=y-handles[i][1];
                float d=dx*dx+dy*dy;
                if(d<distance){distance=d;best=i+1;}
            }
            if(best!=0)return best;
            RectF bounds=activeBounds(l);
            return bounds!=null&&bounds.contains(x,y)?-1:0;
        }
        private void transformOverlay(Canvas c,StudioDocument.Layer l){
            float[][] points=transformHandles(l);
            paint.setShader(null);paint.setColor(0xffba8cff);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.6f/scale());
            Path path=new Path();
            path.moveTo(points[0][0],points[0][1]);
            path.lineTo(points[2][0],points[2][1]);
            path.lineTo(points[7][0],points[7][1]);
            path.lineTo(points[5][0],points[5][1]);path.close();
            c.drawPath(path,paint);
            c.drawLine(points[1][0],points[1][1],points[8][0],points[8][1],paint);
            paint.setStyle(Paint.Style.FILL);
            for(int i=0;i<points.length;i++){
                paint.setColor(0xfff7f1ff);
                c.drawCircle(points[i][0],points[i][1],5f/scale(),paint);
                paint.setColor(0xff8d58c1);
                c.drawCircle(points[i][0],points[i][1],2.7f/scale(),paint);
            }
        }
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            if(document==null)return;
            float s=scale();
            c.drawColor(0xff0c0b0f);
            c.save();c.translate(left(),top());c.scale(s,s);
            paint.setShader(null);paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.FILL);
            c.drawRect(0,0,document.width,document.height,paint);
            // Checkerboard is viewport-only decoration, never exported.
            int cell=48;
            paint.setColor(0xffddd9e0);
            for(int y=0;y<document.height;y+=cell)
                for(int x=0;x<document.width;x+=cell)
                    if(((x/cell)+(y/cell))%2==0)
                        c.drawRect(x,y,Math.min(x+cell,document.width),
                            Math.min(y+cell,document.height),paint);
            document.render(c);
            StudioDocument.Layer current=document.active();
            if(selectedTool==Tool.MOVE&&current!=null)
                outline(c,activeBounds(current),0xffbd91e9);
            if(selectedTool==Tool.TRANSFORM&&current!=null)
                transformOverlay(c,current);
            outline(c,document.selection,0xffdbb4ff);
            if(document.selectionPixels!=null){
                Bitmap ants=buildPixelAnts();
                if(ants!=null){
                    paint.setColor(Color.WHITE);paint.setAlpha(255);
                    paint.setFilterBitmap(false);c.drawBitmap(ants,0,0,paint);
                    paint.setFilterBitmap(true);
                }
            }
            outline(c,pendingCrop,0xffedf0ee);
            if(gestureRect!=null)outline(c,gestureRect,0xfff2cdf6);
            c.restore();
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            zoomDetector.onTouchEvent(e);
            int action=e.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){
                lastX=e.getX();lastY=e.getY();
                startDocX=docX(lastX);startDocY=docY(lastY);
                gestureMoving=false;gestureUsed=false;transformHandle=0;
                gestureRect=null;stroke=null;maskStroke=null;
                if(!inDocument(startDocX,startDocY))return true;
                switch(selectedTool){
                    case MASK_HIDE:case MASK_REVEAL:
                        try{
                            StudioDocument.Layer l=document.active();
                            if(l==null)throw new IllegalStateException("Selecione uma máscara");
                            float[] local=StudioTransformMath.inversePoint(l,startDocX,startDocY);
                            maskStroke=StudioLayerEffects.beginMaskStroke(document,
                                local[0],local[1],
                                selectedTool==Tool.MASK_REVEAL,brushSize);
                            invalidate();
                        }catch(Exception error){message(error.getMessage());}
                        break;
                    case BRUSH:case ERASER:
                        try{
                            StudioDocument.Layer layer=document.active();
                            if(layer==null)throw new IllegalStateException("Selecione uma camada raster");
                            if(layer.type==StudioDocument.Type.SMART&&!paintingMask){
                                final StudioDocument.Layer owner=layer;
                                new AlertDialog.Builder(activity).setTitle("Objeto inteligente")
                                    .setItems(new String[]{"Editar conteúdo","Rasterizar","Cancelar"},
                                        (d,index)->{
                                            if(index==0)openSmartEditor(owner);
                                            if(index==1)layerCommand("Rasterizar objeto inteligente");
                                        }).show();
                                break;
                            }
                            if(paintingMask&&layer.mask!=null&&layer.id.equals(editingMaskId)){
                                int gray=selectedTool==Tool.ERASER?0:
                                    Math.round(Color.red(foreground)*.299f+
                                    Color.green(foreground)*.587f+
                                    Color.blue(foreground)*.114f);
                                float[] local=StudioTransformMath.inversePoint(layer,startDocX,startDocY);
                                maskStroke=StudioLayerEffects.beginMaskStroke(document,
                                    local[0],local[1],gray,brushSize);
                                invalidate();break;
                            }
                            stroke=StudioRasterTools.begin(document,foreground,
                                brushSize,brushOpacity/100f,selectedTool==Tool.ERASER,
                                StudioTransformMath.inversePoint(layer,startDocX,startDocY)[0],
                                StudioTransformMath.inversePoint(layer,startDocX,startDocY)[1],
                                e.getPressure());
                            invalidate();
                        }catch(Exception error){message(error.getMessage());}
                        break;
                    case BUCKET:
                        try{
                            StudioDocument.Layer layer=document.active();
                            if(layer==null)throw new IllegalStateException("Selecione uma camada raster");
                            StudioRasterTools.bucket(document,
                                Math.round(StudioTransformMath.inversePoint(layer,startDocX,startDocY)[0]),
                                Math.round(StudioTransformMath.inversePoint(layer,startDocX,startDocY)[1]),
                                foreground,24);
                            changed(true);
                        }catch(Exception error){message(error.getMessage());}
                        break;
                    case SELECTION:case CROP:case RECTANGLE:case ELLIPSE:
                        gestureRect=new RectF(startDocX,startDocY,startDocX,startDocY);
                        invalidate();break;
                    case TRANSFORM:
                        StudioDocument.Layer transforming=document.active();
                        transformHandle=findTransformHandle(transforming,startDocX,startDocY);
                        if(transforming!=null){
                            originalX=transforming.x;originalY=transforming.y;
                            originalScaleX=transforming.scaleX;
                            originalScaleY=transforming.scaleY;
                            originalRotation=transforming.rotation;
                        }
                        break;
                    case MOVE:break;
                    default:break;
                }
                return true;
            }
            if(action==MotionEvent.ACTION_POINTER_DOWN&&e.getPointerCount()>1){
                if(stroke!=null){stroke.finish();stroke=null;changed(true);}
                if(maskStroke!=null){maskStroke.finish();maskStroke=null;changed(true);}
                gestureRect=null;
                lastMidX=(e.getX(0)+e.getX(1))*.5f;
                lastMidY=(e.getY(0)+e.getY(1))*.5f;
                gestureUsed=true;invalidate();return true;
            }
            if(action==MotionEvent.ACTION_MOVE){
                if(e.getPointerCount()>1){
                    float mx=(e.getX(0)+e.getX(1))*.5f;
                    float my=(e.getY(0)+e.getY(1))*.5f;
                    document.panX+=mx-lastMidX;document.panY+=my-lastMidY;
                    lastMidX=mx;lastMidY=my;invalidate();gestureUsed=true;return true;
                }
                if(gestureUsed)return true;
                float x=docX(e.getX()),y=docY(e.getY());
                float dx=e.getX()-lastX,dy=e.getY()-lastY;
                if(stroke!=null){
                    StudioDocument.Layer l=stroke.layer;
                    float[] local=StudioTransformMath.inversePoint(l,x,y);
                    stroke.move(local[0],local[1]);invalidate();
                }else if(maskStroke!=null){
                    StudioDocument.Layer l=maskStroke.layer;
                    float[] local=StudioTransformMath.inversePoint(l,x,y);
                    maskStroke.move(local[0],local[1]);invalidate();
                }else if(gestureRect!=null){
                    gestureRect.set(startDocX,startDocY,x,y);gestureRect.sort();
                    invalidate();
                }else if(selectedTool==Tool.HAND){
                    document.panX+=dx;document.panY+=dy;invalidate();
                }else if(selectedTool==Tool.TRANSFORM){
                    StudioDocument.Layer selected=document.active();
                    if(selected!=null&&!selected.locked&&transformHandle!=0){
                        if(!gestureMoving){document.checkpoint();gestureMoving=true;}
                        float diffX=x-startDocX,diffY=y-startDocY;
                        if(transformHandle==-1){
                            selected.x=originalX+diffX;selected.y=originalY+diffY;
                        }else if(transformHandle==9){
                            float[] center={StudioTransformMath.centerX(selected),
                                StudioTransformMath.centerY(selected)};
                            float a=(float)Math.atan2(startDocY-center[1],startDocX-center[0]);
                            float b=(float)Math.atan2(y-center[1],x-center[0]);
                            selected.rotation=originalRotation+
                                (float)Math.toDegrees(b-a);
                        }else{
                            float rad=(float)Math.toRadians(originalRotation);
                            float u=(float)(Math.cos(rad)*diffX+Math.sin(rad)*diffY);
                            float v=(float)(-Math.sin(rad)*diffX+Math.cos(rad)*diffY);
                            int index=transformHandle;
                            float hSide=(index==1||index==4||index==6)?-1:
                                (index==3||index==5||index==8)?1:0;
                            float vSide=index<=3?-1:index>=6?1:0;
                            float xs=Math.abs(originalScaleX),ys=Math.abs(originalScaleY);
                            if(hSide!=0)
                                xs=Math.max(.01f,Math.min(16,xs+hSide*u/
                                    Math.max(1,selected.naturalWidth())));
                            if(vSide!=0)
                                ys=Math.max(.01f,Math.min(16,ys+vSide*v/
                                    Math.max(1,selected.naturalHeight())));
                            if(keepAspect){
                                float ratio=hSide!=0?xs/Math.abs(originalScaleX):
                                    ys/Math.abs(originalScaleY);
                                xs=Math.max(.01f,Math.min(16,Math.abs(originalScaleX)*ratio));
                                ys=Math.max(.01f,Math.min(16,Math.abs(originalScaleY)*ratio));
                            }
                            selected.scaleX=Math.copySign(xs,originalScaleX);
                            selected.scaleY=Math.copySign(ys,originalScaleY);
                        }
                        invalidate();
                    }
                }else if(selectedTool==Tool.MOVE){
                    StudioDocument.Layer current=document.active();
                    if(current!=null&&!current.locked){
                        if(!gestureMoving&&Math.abs(dx)+Math.abs(dy)>1.5f){
                            document.checkpoint();gestureMoving=true;
                        }
                        if(gestureMoving){
                            current.x+=dx/scale();
                            current.y+=dy/scale();
                            invalidate();
                        }
                    }
                }
                lastX=e.getX();lastY=e.getY();return true;
            }
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
                if(stroke!=null){
                    stroke.finish();stroke=null;changed(true);
                }else if(maskStroke!=null){
                    maskStroke.finish();maskStroke=null;changed(true);
                }else if(gestureMoving)changed(true);
                if(gestureRect!=null&&!gestureUsed&&action==MotionEvent.ACTION_UP){
                    RectF rect=new RectF(gestureRect);
                    rect.intersect(0,0,document.width,document.height);
                    if(rect.width()>2&&rect.height()>2){
                        switch(selectedTool){
                            case SELECTION:document.select(rect);changed(true);break;
                            case CROP:pendingCrop=rect;break;
                            case RECTANGLE:case ELLIPSE:
                                try{
                                    StudioDocument.Layer layer=document.addShape(
                                        selectedTool==Tool.RECTANGLE?
                                            StudioDocument.Type.RECTANGLE:StudioDocument.Type.ELLIPSE,
                                        foreground,Math.round(rect.width()),Math.round(rect.height()));
                                    layer.x=rect.left;layer.y=rect.top;changed(true);
                                }catch(Exception ex){message(ex.getMessage());}
                                break;
                            default:break;
                        }
                    }else if(selectedTool==Tool.SELECTION&&document.hasSelection()){
                        document.deselect();changed(true);
                    }
                }else if(selectedTool==Tool.TEXT&&!gestureUsed
                        &&action==MotionEvent.ACTION_UP
                        &&inDocument(startDocX,startDocY)){
                    try{
                        StudioDocument.Layer l=document.addText("Texto",foreground,64f);
                        l.x=startDocX;l.y=startDocY;
                        showTextPanel=true;changed(true);textDialog();
                    }catch(Exception error){message(error.getMessage());}
                }else if(selectedTool==Tool.GRADIENT&&!gestureUsed
                        &&action==MotionEvent.ACTION_UP){
                    try{
                        StudioDocument.Layer l=document.active();
                        if(l==null)throw new IllegalStateException("Selecione uma camada raster");
                        StudioRasterTools.gradient(document,
                            StudioTransformMath.inversePoint(l,startDocX,startDocY)[0],
                            StudioTransformMath.inversePoint(l,startDocX,startDocY)[1],
                            StudioTransformMath.inversePoint(l,docX(e.getX()),docY(e.getY()))[0],
                            StudioTransformMath.inversePoint(l,docX(e.getX()),docY(e.getY()))[1],
                            foreground,Color.TRANSPARENT);
                        changed(true);
                    }catch(Exception ex){message(ex.getMessage());}
                }
                gestureRect=null;gestureMoving=false;gestureUsed=false;transformHandle=0;
                invalidate();return true;
            }
            return true;
        }
    }
}
