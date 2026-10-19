package com.discnet.noise.advancedstudio;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Phone-first gradient editor: geometry, draggable numeric stops, RGBA, midpoint.
 * No hex-only workflow. Edits are staged and committed once through callback.
 */
public final class StudioGradientEditor {
    public interface OnApply {void apply(StudioGradient gradient);}
    private final Context ctx;
    private final StudioGradient value;
    private final LinearLayout root,stops;
    private final Preview preview;
    private StudioGradientEditor(Context ctx,StudioGradient original){
        this.ctx=ctx;value=original.copy();
        root=new LinearLayout(ctx);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14),dp(8),dp(14),dp(12));
        preview=new Preview(ctx);root.addView(preview,new LinearLayout.LayoutParams(-1,dp(48)));
        rowChoice("TIPO",new String[]{"Linear","Radial","Angular","Refletido","Diamante"},
            StudioGradient.TYPES,value.type,(v)->{value.type=v;preview.invalidate();});
        rowSlider("Ângulo",Math.round(value.angle),-180,180,n->{value.angle=n;preview.invalidate();});
        rowSlider("Escala",Math.round(value.scale),1,400,n->{value.scale=n;preview.invalidate();});
        rowSlider("Posição X",Math.round(value.offsetX),-100,100,n->{value.offsetX=n;preview.invalidate();});
        rowSlider("Posição Y",Math.round(value.offsetY),-100,100,n->{value.offsetY=n;preview.invalidate();});
        LinearLayout flags=new LinearLayout(ctx);
        root.addView(flags,new LinearLayout.LayoutParams(-1,dp(38)));
        toggle(flags,"Inverter",value.reverse,v->{value.reverse=v;preview.invalidate();});
        toggle(flags,"Dither",value.dither,v->{value.dither=v;preview.invalidate();});
        TextView label=text("CORES E TRANSPARÊNCIA",11);
        label.setTextColor(0xffc99af3);
        root.addView(label,new LinearLayout.LayoutParams(-1,dp(30)));
        stops=new LinearLayout(ctx);stops.setOrientation(LinearLayout.VERTICAL);
        root.addView(stops);
        Button add=new Button(ctx);add.setText("＋ Adicionar ponto");
        root.addView(add,new LinearLayout.LayoutParams(-1,dp(46)));
        add.setOnClickListener(v->{
            try{
                int index=Math.max(0,value.colors.length-2);
                float at=(value.positions[index]+value.positions[index+1])*.5f;
                int c=value.color(at,0,0);
                value.insert(at,c);renderStops();preview.invalidate();
            }catch(Exception e){Toast.makeText(ctx,e.getMessage(),Toast.LENGTH_SHORT).show();}
        });
        renderStops();
    }
    public static void show(Context c,String title,StudioGradient initial,OnApply action){
        StudioGradientEditor editor=new StudioGradientEditor(c,initial);
        ScrollView scroll=new ScrollView(c);scroll.setFillViewport(false);
        scroll.addView(editor.root);
        AlertDialog dialog=new AlertDialog.Builder(c).setTitle(title).setView(scroll)
            .setNegativeButton("Cancelar",null).setPositiveButton("Aplicar",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(-1).setOnClickListener(v->{
            try{editor.value.validate();action.apply(editor.value.copy());dialog.dismiss();}
            catch(Exception e){Toast.makeText(c,e.getMessage(),Toast.LENGTH_LONG).show();}
        }));
        dialog.show();
    }
    private int dp(int n){return Math.round(n*ctx.getResources().getDisplayMetrics().density);}
    private TextView text(String text,int size){
        TextView v=new TextView(ctx);v.setText(text);v.setTextSize(size);
        v.setTextColor(0xffede7f3);v.setGravity(Gravity.CENTER_VERTICAL);return v;
    }
    private interface ChangeString {void onChange(String next);}
    private interface ChangeInt {void onChange(int next);}
    private interface ChangeBool {void onChange(boolean value);}
    private void rowChoice(String name,String[] display,String[] ids,
                           String selected,ChangeString callback){
        LinearLayout row=new LinearLayout(ctx);row.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(row,new LinearLayout.LayoutParams(-1,dp(44)));
        row.addView(text(name,12),new LinearLayout.LayoutParams(dp(78),-1));
        Spinner spinner=new Spinner(ctx);
        spinner.setAdapter(new ArrayAdapter<String>(ctx,android.R.layout.simple_spinner_dropdown_item,display));
        row.addView(spinner,new LinearLayout.LayoutParams(0,-1,1));
        spinner.setSelection(Math.max(0,Arrays.asList(ids).indexOf(selected)));
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> parent){}
            public void onItemSelected(android.widget.AdapterView<?> parent,View view,int index,long id){
                callback.onChange(ids[index]);
            }
        });
    }
    private void rowSlider(String caption,int current,int minimum,int maximum,ChangeInt callback){
        LinearLayout row=new LinearLayout(ctx);row.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(row,new LinearLayout.LayoutParams(-1,dp(43)));
        TextView name=text(caption,11);
        row.addView(name,new LinearLayout.LayoutParams(dp(78),-1));
        SeekBar bar=new SeekBar(ctx);bar.setMax(maximum-minimum);bar.setProgress(current-minimum);
        row.addView(bar,new LinearLayout.LayoutParams(0,-1,1));
        TextView amount=text(String.valueOf(current),11);
        amount.setGravity(Gravity.CENTER);
        row.addView(amount,new LinearLayout.LayoutParams(dp(43),-1));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){}
            public void onProgressChanged(SeekBar b,int n,boolean user){
                if(user){callback.onChange(n+minimum);amount.setText(String.valueOf(n+minimum));}
            }
        });
    }
    private void toggle(LinearLayout row,String caption,boolean current,ChangeBool callback){
        CheckBox cb=new CheckBox(ctx);cb.setText(caption);cb.setTextColor(0xffe9dff5);
        cb.setTextSize(11);cb.setChecked(current);
        row.addView(cb,new LinearLayout.LayoutParams(0,-1,1));
        cb.setOnCheckedChangeListener((button,next)->callback.onChange(next));
    }
    private void renderStops(){
        stops.removeAllViews();
        for(int i=0;i<value.colors.length;i++){
            final int index=i;
            LinearLayout line=new LinearLayout(ctx);line.setGravity(Gravity.CENTER_VERTICAL);
            stops.addView(line,new LinearLayout.LayoutParams(-1,dp(54)));
            TextView swatch=text("■",25);swatch.setTextColor(value.colors[i]);
            line.addView(swatch,new LinearLayout.LayoutParams(dp(33),dp(45)));
            swatch.setOnClickListener(v->StudioColorPicker.show(ctx,
                "Cor e alfa do ponto "+(index+1),value.colors[index],col->{
                    value.colors[index]=col;swatch.setTextColor(col);preview.invalidate();
                }));
            SeekBar pos=new SeekBar(ctx);pos.setMax(1000);
            pos.setProgress(Math.round(value.positions[i]*1000));
            line.addView(pos,new LinearLayout.LayoutParams(0,-1,1));
            TextView number=text(Math.round(value.positions[i]*100)+"%",11);
            line.addView(number,new LinearLayout.LayoutParams(dp(41),dp(45)));
            pos.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                public void onStartTrackingTouch(SeekBar b){}
                public void onStopTrackingTouch(SeekBar b){}
                public void onProgressChanged(SeekBar b,int n,boolean user){
                    if(!user)return;
                    float low=index==0?0:value.positions[index-1]+.001f;
                    float high=index==value.positions.length-1?1:value.positions[index+1]-.001f;
                    value.positions[index]=Math.max(low,Math.min(high,n/1000f));
                    number.setText(Math.round(value.positions[index]*100)+"%");
                    preview.invalidate();
                }
            });
            TextView delete=text("×",22);delete.setGravity(Gravity.CENTER);
            line.addView(delete,new LinearLayout.LayoutParams(dp(28),-1));
            delete.setOnClickListener(v->{
                if(value.colors.length<=2)return;
                value.remove(index);renderStops();preview.invalidate();
            });
            if(i<value.midpoints.length){
                LinearLayout midRow=new LinearLayout(ctx);midRow.setGravity(Gravity.CENTER_VERTICAL);
                stops.addView(midRow,new LinearLayout.LayoutParams(-1,dp(34)));
                midRow.addView(text("Ponto médio",10),new LinearLayout.LayoutParams(dp(84),-1));
                SeekBar mid=new SeekBar(ctx);mid.setMax(90);
                mid.setProgress(Math.round(value.midpoints[i]*100)-5);
                midRow.addView(mid,new LinearLayout.LayoutParams(0,-1,1));
                TextView midValue=text(Math.round(value.midpoints[i]*100)+"%",10);
                midRow.addView(midValue,new LinearLayout.LayoutParams(dp(41),-1));
                mid.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                    public void onStartTrackingTouch(SeekBar b){}
                    public void onStopTrackingTouch(SeekBar b){}
                    public void onProgressChanged(SeekBar b,int n,boolean user){
                        if(user){value.midpoints[index]=(n+5)/100f;
                            midValue.setText((n+5)+"%");preview.invalidate();}
                    }
                });
            }
        }
    }
    private final class Preview extends View{
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Preview(Context c){super(c);}
        @Override protected void onDraw(Canvas c){
            for(int x=0;x<getWidth();x+=3)for(int y=0;y<getHeight();y+=3){
                float t=value.coordinate(x,y,getWidth(),getHeight());
                p.setColor(value.color(t,x,y));c.drawRect(x,y,x+3,y+3,p);
            }
        }
    }
}
