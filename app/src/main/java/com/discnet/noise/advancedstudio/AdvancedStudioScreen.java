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
    private final Activity activity;
    private final Host host;
    private final StudioProjectStore store;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private StudioDocument document;
    private final LinearLayout page,menubar,tools,layersList,propertyPanel;
    private final FrameLayout editor;
    private final StudioCanvas canvas;
    private final TextView docTab,info,opacityLabel;
    private final SeekBar opacity;
    enum Tool { MOVE,HAND,BRUSH,ERASER,MASK_HIDE,MASK_REVEAL,SELECTION,CROP,BUCKET,GRADIENT,RECTANGLE,ELLIPSE }
    private Tool selectedTool=Tool.MOVE;
    private boolean closed,showLayers=true;
    private int foreground=0xffba8cf0,brushSize=24,brushOpacity=100;
    private RectF pendingCrop;
    private TextView toolMode;
    private int revision,savedRevision;
    private int ink=0xfff5f2f8,muted=0xffa9a4b0,accent=0xffbe9aeb;
    private final int surface=0xff101016,panel=0xff19171f;
    public AdvancedStudioScreen(Activity a,Host host,Bitmap imported){
        super(a);activity=a;this.host=host;store=new StudioProjectStore(a);
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
        commandMenu("Arquivo",new String[]{"Novo","Abrir projeto","Importar camada","Salvar .noise","Exportar PNG","Voltar"},
            this::fileCommand);
        commandMenu("Editar",new String[]{"Desfazer","Refazer"},
            value->{if(value.equals("Desfazer"))document.undo();else document.redo();changed(false);});
        commandMenu("Camadas",new String[]{"Nova camada","Duplicar","Excluir","Para cima","Para baixo","Texto editável","Retângulo","Elipse","Transformar","Mesclagem","Painel"},
            this::layerCommand);
        commandMenu("Selecionar",new String[]{"Selecionar tudo","Desmarcar","Recortar seleção"},
            this::selectionCommand);
        commandMenu("Imagem",new String[]{"Aplicar recorte","Cancelar recorte","Brilho / Contraste / Saturação"},
            this::imageCommand);
        commandMenu("Máscaras",new String[]{"Criar máscara branca","Criar máscara preta","Pintar preto","Pintar branco","Inverter máscara","Ativar/desativar","Densidade","Excluir máscara"},
            this::maskCommand);
        commandMenu("Filtros",new String[]{"Adicionar filtro","Gerenciar filtros"},
            this::filterCommand);
        commandMenu("Estilos",new String[]{"Sombra projetada","Remover sombra"},
            this::styleCommand);
        commandMenu("Visualizar",new String[]{"Ajustar à tela","Zoom +","Zoom −","Camadas"},
            this::viewCommand);

        LinearLayout contextBar=new LinearLayout(a);
        contextBar.setPadding(dp(12),0,dp(12),0);
        contextBar.setGravity(Gravity.CENTER_VERTICAL);
        contextBar.setBackgroundColor(0xff201c28);
        docTab=text("",13,ink);docTab.setSingleLine(true);
        contextBar.addView(docTab,new LinearLayout.LayoutParams(0,-1,1));
        toolMode=text("Mover",12,accent);contextBar.addView(toolMode);
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
        working.addView(toolScroll,new LinearLayout.LayoutParams(dp(60),-1));
        toolButton("Mover",Tool.MOVE);
        toolButton("Mão",Tool.HAND);
        toolButton("Pincel",Tool.BRUSH);
        toolButton("Borracha",Tool.ERASER);
        toolButton("Másc.−",Tool.MASK_HIDE);
        toolButton("Másc.+",Tool.MASK_REVEAL);
        toolButton("Seleção",Tool.SELECTION);
        toolButton("Recorte",Tool.CROP);
        toolButton("Balde",Tool.BUCKET);
        toolButton("Gradiente",Tool.GRADIENT);
        toolButton("Retâng.",Tool.RECTANGLE);
        toolButton("Elipse",Tool.ELLIPSE);
        editor=new FrameLayout(a);editor.setBackgroundColor(0xff0a090d);
        working.addView(editor,new LinearLayout.LayoutParams(0,-1,1));
        canvas=new StudioCanvas(a);
        editor.addView(canvas,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout dock=new LinearLayout(a);dock.setOrientation(LinearLayout.VERTICAL);
        dock.setBackgroundColor(panel);
        if(wide)body.addView(dock,new LinearLayout.LayoutParams(dp(220),-1));
        else body.addView(dock,new LinearLayout.LayoutParams(-1,dp(202)));
        propertyPanel=dock;
        LinearLayout dockHead=new LinearLayout(a);dockHead.setGravity(Gravity.CENTER_VERTICAL);
        dockHead.setPadding(dp(8),0,dp(8),0);
        dock.addView(dockHead,new LinearLayout.LayoutParams(-1,dp(43)));
        TextView layersTitle=text("CAMADAS",12,ink);
        dockHead.addView(layersTitle,new LinearLayout.LayoutParams(0,-1,1));
        dockHead.addView(action("＋",()->layerCommand("Nova camada")));
        dockHead.addView(action("⧉",()->layerCommand("Duplicar")));
        dockHead.addView(action("↑",()->layerCommand("Para cima")));
        dockHead.addView(action("↓",()->layerCommand("Para baixo")));
        dockHead.addView(action("×",()->layerCommand("Excluir")));
        ScrollView layerScroll=new ScrollView(a);layerScroll.setFillViewport(false);
        dock.addView(layerScroll,new LinearLayout.LayoutParams(-1,0,1));
        layersList=new LinearLayout(a);layersList.setOrientation(LinearLayout.VERTICAL);
        layerScroll.addView(layersList);
        LinearLayout alphaRow=new LinearLayout(a);alphaRow.setGravity(Gravity.CENTER_VERTICAL);
        alphaRow.setPadding(dp(9),0,dp(9),0);
        dock.addView(alphaRow,new LinearLayout.LayoutParams(-1,dp(36)));
        opacityLabel=text("Opacidade",11,muted);
        alphaRow.addView(opacityLabel,new LinearLayout.LayoutParams(dp(89),-1));
        opacity=new SeekBar(a);opacity.setMax(100);
        alphaRow.addView(opacity,new LinearLayout.LayoutParams(0,-1,1));
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            boolean started;
            @Override public void onStartTrackingTouch(SeekBar b){started=false;}
            @Override public void onStopTrackingTouch(SeekBar b){if(started)changed(true);}
            @Override public void onProgressChanged(SeekBar b,int progress,boolean fromUser){
                StudioDocument.Layer l=document.active();
                if(!fromUser||l==null||l.locked)return;
                if(!started){document.checkpoint();started=true;}
                l.opacity=progress/100f;
                canvas.invalidate();opacityLabel.setText("Opacidade "+progress+"%");
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
        properties.addView(action("Texto",this::textDialog));
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
    private void toolButton(String title,Tool mode){
        TextView button=tool(title);
        tools.addView(button,new LinearLayout.LayoutParams(-1,dp(49)));
        button.setOnClickListener(v->{selectedTool=mode;toolMode.setText(title);canvas.invalidate();});
        button.setOnLongClickListener(v->{toolMode.setText(title);return true;});
    }
    private void colorDialog(){
        EditText input=numberField("#RRGGBB",false);
        input.setText(String.format(java.util.Locale.ROOT,"#%06X",foreground&0xffffff));
        new AlertDialog.Builder(activity).setTitle("Cor principal · HEX")
            .setView(input).setNegativeButton("Cancelar",null)
            .setPositiveButton("Aplicar",(d,w)->{
                try{foreground=Color.parseColor(input.getText().toString().trim());}
                catch(Exception e){message("Cor hexadecimal inválida");}
            }).show();
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
                if(editing){if(current.locked){message("Camada bloqueada");return;}
                    document.checkpoint();current.text=value;current.color=foreground;
                }else document.addText(value,foreground,64f);
                changed(true);
            }).show();
    }
    private void transformDialog(){
        StudioDocument.Layer layer=document.active();
        if(layer==null||layer.locked){message("Selecione uma camada desbloqueada");return;}
        LinearLayout fields=new LinearLayout(activity);
        fields.setOrientation(LinearLayout.VERTICAL);fields.setPadding(dp(20),0,dp(20),0);
        EditText rotate=numberField("Rotação",false);
        rotate.setText(String.valueOf(layer.rotation));fields.addView(rotate);
        EditText sx=numberField("Escala horizontal",false);
        sx.setText(String.valueOf(layer.scaleX));fields.addView(sx);
        EditText sy=numberField("Escala vertical",false);
        sy.setText(String.valueOf(layer.scaleY));fields.addView(sy);
        new AlertDialog.Builder(activity).setTitle("Transformação livre — valores")
            .setView(fields).setNegativeButton("Cancelar",null)
            .setPositiveButton("Aplicar",(dialog,which)->{
                try{
                    float angle=Float.parseFloat(rotate.getText().toString());
                    float x=Float.parseFloat(sx.getText().toString());
                    float y=Float.parseFloat(sy.getText().toString());
                    if(!Float.isFinite(angle)||!Float.isFinite(x)||!Float.isFinite(y)
                        ||Math.abs(x)>.0f&&Math.abs(x)<.01f
                        ||Math.abs(y)>.0f&&Math.abs(y)<.01f
                        ||x==0||y==0||Math.abs(x)>16||Math.abs(y)>16)
                        throw new NumberFormatException();
                    document.checkpoint();layer.rotation=angle;
                    layer.scaleX=x;layer.scaleY=y;changed(true);
                }catch(Exception e){message("Transformação inválida");}
            }).show();
    }
    private void selectionCommand(String cmd){
        if("Selecionar tudo".equals(cmd))document.select(new RectF(0,0,document.width,document.height));
        else if("Desmarcar".equals(cmd))document.deselect();
        else if("Recortar seleção".equals(cmd)&&document.selection!=null){
            pendingCrop=new RectF(document.selection);selectedTool=Tool.CROP;
            toolMode.setText("Recorte");canvas.invalidate();return;
        }
        changed(true);
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
            case "Transformar":transformDialog();return;
            case "Mesclagem":blendDialog();return;
            case "Painel":case "Camadas":showLayers=!showLayers;
                propertyPanel.setVisibility(showLayers?VISIBLE:GONE);return;
        }
        changed(true);
    }
    private void blendDialog(){
        StudioDocument.Layer layer=document.active();
        if(layer==null||layer.locked)return;
        StudioDocument.Blend[] blends=StudioDocument.Blend.values();
        String[] labels={"Normal","Multiplicar","Tela","Sobrepor","Escurecer","Clarear"};
        new AlertDialog.Builder(activity).setTitle("Modo de mesclagem")
            .setSingleChoiceItems(labels,layer.blend.ordinal(),(dialog,which)->{
                document.checkpoint();layer.blend=blends[which];
                changed(true);dialog.dismiss();
            }).setNegativeButton("Cancelar",null).show();
    }
    private void maskCommand(String cmd){
        StudioDocument.Layer l=document.active();
        if(l==null||l.locked){message("Selecione uma camada desbloqueada");return;}
        try{
            switch(cmd){
                case "Criar máscara branca":StudioLayerEffects.createMask(document,true);break;
                case "Criar máscara preta":StudioLayerEffects.createMask(document,false);break;
                case "Pintar preto":selectedTool=Tool.MASK_HIDE;toolMode.setText("Ocultar na máscara");return;
                case "Pintar branco":selectedTool=Tool.MASK_REVEAL;toolMode.setText("Revelar na máscara");return;
                case "Inverter máscara":StudioLayerEffects.invertMask(document);break;
                case "Ativar/desativar":
                    if(l.mask==null)throw new IllegalStateException("Camada sem máscara");
                    document.checkpoint();l.maskEnabled=!l.maskEnabled;break;
                case "Excluir máscara":
                    if(l.mask==null)return;
                    document.checkpoint();l.mask=null;break;
                case "Densidade":
                    if(l.mask==null)throw new IllegalStateException("Camada sem máscara");
                    maskDensityDialog(l);return;
                default:return;
            }
            changed(true);
        }catch(Exception e){message(e.getMessage());}
    }
    private void maskDensityDialog(StudioDocument.Layer l){
        SeekBar density=new SeekBar(activity);density.setMax(100);density.setProgress(l.maskDensity);
        LinearLayout box=new LinearLayout(activity);box.setPadding(dp(22),dp(10),dp(22),dp(10));
        box.addView(density,new LinearLayout.LayoutParams(-1,dp(48)));
        new AlertDialog.Builder(activity).setTitle("Densidade da máscara")
            .setView(box).setNegativeButton("Cancelar",null)
            .setPositiveButton("Aplicar",(d,w)->{
                document.checkpoint();l.maskDensity=density.getProgress();changed(true);
            }).show();
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
        int min=current.kind==StudioLayerEffects.Kind.GAUSSIAN_BLUR?1:-100;
        int max=current.kind==StudioLayerEffects.Kind.GAUSSIAN_BLUR?8:100;
        SeekBar bar=new SeekBar(activity);bar.setMax(max-min);
        bar.setProgress(current.value-min);
        LinearLayout box=new LinearLayout(activity);
        box.setPadding(dp(23),dp(10),dp(23),dp(10));
        box.addView(bar,new LinearLayout.LayoutParams(-1,dp(50)));
        // Do not mutate the committed stack before Confirm; Cancel is clean.
        new AlertDialog.Builder(activity)
            .setTitle(current.kind.name()+" · "+current.value)
            .setView(box).setNegativeButton("Cancelar",null)
            .setPositiveButton("Aplicar",(dialog,which)->{
                StudioLayerEffects.Effect replacement=current.copy();
                replacement.value=bar.getProgress()+min;
                try{StudioLayerEffects.validate(replacement);}
                catch(Exception e){message(e.getMessage());return;}
                document.checkpoint();
                if(index<0)layer.effects.add(replacement);
                else layer.effects.set(index,replacement);
                changed(true);
            }).show();
    }
    private void styleCommand(String cmd){
        StudioDocument.Layer l=document.active();
        if(l==null||l.locked){message("Selecione uma camada desbloqueada");return;}
        if("Remover sombra".equals(cmd)){
            if(l.shadowEnabled){document.checkpoint();l.shadowEnabled=false;changed(true);}
            return;
        }
        LinearLayout box=new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),dp(5),dp(18),dp(5));
        EditText radius=numberField("Raio 1–16",false);
        radius.setText(String.valueOf(l.shadowRadius));box.addView(radius);
        EditText shift=numberField("Deslocamento X/Y",false);
        shift.setText(String.valueOf(l.shadowX));box.addView(shift);
        EditText transparency=numberField("Opacidade 0–100",false);
        transparency.setText(String.valueOf(Math.round(l.shadowOpacity*100)));
        box.addView(transparency);
        new AlertDialog.Builder(activity).setTitle("Sombra projetada")
            .setView(box).setNegativeButton("Cancelar",null)
            .setPositiveButton("Aplicar",(d,w)->{
                try{
                    float r=Float.parseFloat(radius.getText().toString());
                    float x=Float.parseFloat(shift.getText().toString());
                    int o=Integer.parseInt(transparency.getText().toString());
                    if(!Float.isFinite(r)||r<1||r>16||!Float.isFinite(x)||
                       Math.abs(x)>128||o<0||o>100)throw new IllegalArgumentException();
                    document.checkpoint();l.shadowRadius=r;l.shadowX=x;l.shadowY=x;
                    l.shadowOpacity=o/100f;l.shadowEnabled=true;changed(true);
                }catch(Exception e){message("Valores de sombra inválidos");}
            }).show();
    }
    private void viewCommand(String cmd){
        if(cmd.equals("Ajustar à tela")){document.zoom=1;document.panX=document.panY=0;canvas.invalidate();}
        else if(cmd.equals("Zoom +"))canvas.zoomBy(1.25f);
        else if(cmd.equals("Zoom −"))canvas.zoomBy(.8f);
        else if(cmd.equals("Camadas"))layerCommand("Painel");
        info.setText(document.width+"×"+document.height+" • "+Math.round(document.zoom*100)+"%");
    }
    private void updateUi(){
        if(closed)return;
        docTab.setText(document.name+"  •  "+(document.dirty?"● ":"")+".noise");
        info.setText(document.width+"×"+document.height+" • "+Math.round(document.zoom*100)+"%");
        layersList.removeAllViews();
        for(int i=document.layers.size()-1;i>=0;i--){
            StudioDocument.Layer layer=document.layers.get(i);
            LinearLayout row=new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(layer.id.equals(document.activeId)?0xff34293d:panel);
            TextView visibility=action(layer.visible?"◉":"○",()->{
                document.checkpoint();layer.visible=!layer.visible;changed(true);
            });
            row.addView(visibility,new LinearLayout.LayoutParams(dp(34),dp(40)));
            TextView title=text(layer.name,12,ink);title.setSingleLine(true);
            row.addView(title,new LinearLayout.LayoutParams(0,dp(40),1));
            title.setOnClickListener(v->{document.activeId=layer.id;updateUi();});
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
            if(layer.mask!=null||!layer.effects.isEmpty()||layer.shadowEnabled){
                TextView extras=text((layer.mask!=null?"M":"")+
                    (!layer.effects.isEmpty()?" fx":"")+
                    (layer.shadowEnabled?" ░":""),10,accent);
                row.addView(extras,new LinearLayout.LayoutParams(dp(36),dp(40)));
            }
            layersList.addView(row,new LinearLayout.LayoutParams(-1,dp(43)));
        }
        StudioDocument.Layer current=document.active();
        opacity.setEnabled(current!=null&&!current.locked);
        opacity.setProgress(current==null?100:Math.round(current.opacity*100));
        opacityLabel.setText(current==null?"Opacidade":("Opacidade "+opacity.getProgress()+"%"));
        canvas.invalidate();
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
        final StudioDocument snapshot=document.snapshot();
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
        final StudioDocument snapshot=document.snapshot();
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
        if(document.dirty)scheduleSaveNow();
        host.exit();
    }
    public void dispose(){
        main.removeCallbacks(autosave);
        // Saves already queued execute to completion; never recycle shared
        // layer bitmaps while a background save/export may be using them.
        closed=true;
        io.shutdown();
    }
    private final class StudioCanvas extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ScaleGestureDetector zoomDetector;
        private boolean gestureMoving,gestureUsed,resize;
        private float lastX,lastY,lastMidX,lastMidY,startDocX,startDocY;
        private RectF gestureRect;
        private StudioRasterTools.Stroke stroke;
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
            if(l==null)return null;
            float w=l.naturalWidth()*Math.abs(l.scaleX);
            float h=l.naturalHeight()*Math.abs(l.scaleY);
            return new RectF(l.x,l.y,l.x+w,l.y+h);
        }
        private void outline(Canvas canvas,RectF bounds,int color){
            if(bounds==null)return;
            paint.setColor(color);paint.setShader(null);paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(.8f,1.5f/scale()));
            canvas.drawRect(bounds,paint);
            paint.setStyle(Paint.Style.FILL);
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
            if(selectedTool==Tool.MOVE&&current!=null){
                RectF bounds=activeBounds(current);
                outline(c,bounds,0xffbd91e9);
                if(bounds!=null){
                    paint.setColor(0xfff1e9fd);
                    c.drawRect(bounds.right-5/s,bounds.bottom-5/s,
                        bounds.right+5/s,bounds.bottom+5/s,paint);
                }
            }
            outline(c,document.selection,0xffdbb4ff);
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
                gestureMoving=false;gestureUsed=false;resize=false;
                gestureRect=null;stroke=null;
                if(!inDocument(startDocX,startDocY))return true;
                switch(selectedTool){
                    case BRUSH:case ERASER:
                        try{
                            StudioDocument.Layer layer=document.active();
                            if(layer==null)throw new IllegalStateException("Selecione uma camada raster");
                            stroke=StudioRasterTools.begin(document,foreground,
                                brushSize,brushOpacity/100f,selectedTool==Tool.ERASER,
                                startDocX-layer.x,startDocY-layer.y,e.getPressure());
                            invalidate();
                        }catch(Exception error){message(error.getMessage());}
                        break;
                    case BUCKET:
                        try{
                            StudioDocument.Layer layer=document.active();
                            if(layer==null)throw new IllegalStateException("Selecione uma camada raster");
                            StudioRasterTools.bucket(document,
                                Math.round(startDocX-layer.x),Math.round(startDocY-layer.y),
                                foreground,24);
                            changed(true);
                        }catch(Exception error){message(error.getMessage());}
                        break;
                    case SELECTION:case CROP:case RECTANGLE:case ELLIPSE:
                        gestureRect=new RectF(startDocX,startDocY,startDocX,startDocY);
                        invalidate();break;
                    case MOVE:
                        StudioDocument.Layer current=document.active();
                        RectF bounds=activeBounds(current);
                        resize=current!=null&&bounds!=null
                            &&Math.abs(startDocX-bounds.right)<18/scale()
                            &&Math.abs(startDocY-bounds.bottom)<18/scale();
                        break;
                    default:break;
                }
                return true;
            }
            if(action==MotionEvent.ACTION_POINTER_DOWN&&e.getPointerCount()>1){
                if(stroke!=null){stroke.finish();stroke=null;changed(true);}
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
                    stroke.move(x-l.x,y-l.y);invalidate();
                }else if(gestureRect!=null){
                    gestureRect.set(startDocX,startDocY,x,y);gestureRect.sort();
                    invalidate();
                }else if(selectedTool==Tool.HAND){
                    document.panX+=dx;document.panY+=dy;invalidate();
                }else if(selectedTool==Tool.MOVE){
                    StudioDocument.Layer current=document.active();
                    if(current!=null&&!current.locked){
                        if(!gestureMoving&&Math.abs(dx)+Math.abs(dy)>1.5f){
                            document.checkpoint();gestureMoving=true;
                        }
                        if(gestureMoving){
                            if(resize){
                                float originalW=Math.max(1,current.naturalWidth());
                                float originalH=Math.max(1,current.naturalHeight());
                                current.scaleX=Math.max(.05f,Math.min(12f,
                                    (x-current.x)/originalW));
                                current.scaleY=Math.max(.05f,Math.min(12f,
                                    (y-current.y)/originalH));
                            }else{
                                current.x+=dx/scale();
                                current.y+=dy/scale();
                            }
                            invalidate();
                        }
                    }
                }
                lastX=e.getX();lastY=e.getY();return true;
            }
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
                if(stroke!=null){
                    stroke.finish();stroke=null;changed(true);
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
                    }
                }else if(selectedTool==Tool.GRADIENT&&!gestureUsed
                        &&action==MotionEvent.ACTION_UP){
                    try{
                        StudioDocument.Layer l=document.active();
                        if(l==null)throw new IllegalStateException("Selecione uma camada raster");
                        StudioRasterTools.gradient(document,
                            startDocX-l.x,startDocY-l.y,docX(e.getX())-l.x,
                            docY(e.getY())-l.y,foreground,Color.TRANSPARENT);
                        changed(true);
                    }catch(Exception ex){message(ex.getMessage());}
                }
                gestureRect=null;gestureMoving=false;gestureUsed=false;resize=false;
                invalidate();return true;
            }
            return true;
        }
    }
}
