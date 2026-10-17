package com.discnet.noise.advancedstudio;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;

/** One tactile color picker shared by raster tools, masks, text and layer styles. */
public final class StudioColorPicker extends LinearLayout {
    public interface Selected {void onColor(int argb);}
    private static final ArrayList<Integer> RECENT=new ArrayList<>();
    private final float[] hsv=new float[3];
    private final int before;
    private int alpha;
    private final Field square;
    private final SeekBar hue,opacity;
    private final LinearLayout fields;
    private final TextView current,previous;
    private boolean writing;
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    public static void show(Context c,String title,int initial,Selected onSelect){
        StudioColorPicker picker=new StudioColorPicker(c,initial);
        new AlertDialog.Builder(c).setTitle(title).setView(picker)
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Aplicar",(d,w)->{
                int color=picker.color();
                RECENT.remove(Integer.valueOf(color));RECENT.add(0,color);
                while(RECENT.size()>16)RECENT.remove(RECENT.size()-1);
                onSelect.onColor(color);
            }).show();
    }
    private TextView label(String value,int size){
        TextView t=new TextView(getContext());t.setText(value);t.setTextSize(size);
        t.setTextColor(0xffe7dfed);t.setGravity(Gravity.CENTER_VERTICAL);return t;
    }
    private StudioColorPicker(Context c,int initial){
        super(c);before=initial;Color.colorToHSV(initial,hsv);alpha=Color.alpha(initial);
        setOrientation(VERTICAL);setPadding(dp(14),dp(6),dp(14),dp(6));
        square=new Field(c);square.setContentDescription("Cor: arraste para mudar saturação e brilho");
        addView(square,new LayoutParams(-1,dp(235)));
        addView(label("MATIZ",11),new LayoutParams(-1,dp(24)));
        hue=new SeekBar(c);hue.setMax(360);hue.setProgress(Math.round(hsv[0]));
        hue.setProgressDrawable(new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{0xffff0000,0xffffff00,0xff00ff00,0xff00ffff,0xff0000ff,0xffff00ff,0xffff0000}));
        addView(hue,new LayoutParams(-1,dp(38)));
        addView(label("OPACIDADE",11),new LayoutParams(-1,dp(24)));
        opacity=new SeekBar(c);opacity.setMax(255);opacity.setProgress(alpha);
        addView(opacity,new LayoutParams(-1,dp(35)));
        LinearLayout sample=new LinearLayout(c);
        sample.setGravity(Gravity.CENTER_VERTICAL);
        addView(sample,new LayoutParams(-1,dp(48)));
        previous=label("ANTES",11);previous.setGravity(Gravity.CENTER);
        sample.addView(previous,new LayoutParams(0,dp(44),1));
        current=label("AGORA",11);current.setGravity(Gravity.CENTER);
        sample.addView(current,new LayoutParams(0,dp(44),1));
        previous.setBackgroundColor(before);
        previous.setOnClickListener(v->setColor(before));
        fields=new LinearLayout(c);fields.setOrientation(VERTICAL);
        addView(fields,new LayoutParams(-1,dp(106)));
        LinearLayout swatches=new LinearLayout(c);
        addView(swatches,new LayoutParams(-1,dp(37)));
        int[] palette={Color.WHITE,Color.BLACK,0xfffe6c87,0xffffc96d,
            0xff58d7b6,0xff579cff,0xffbb91ef};
        synchronized(RECENT){for(int color:RECENT)if(!contains(palette,color)){
            TextView chip=label("●",21);chip.setTextColor(color);
            chip.setGravity(Gravity.CENTER);
            swatches.addView(chip,new LayoutParams(0,dp(36),1));
            chip.setOnClickListener(v->setColor(color));
            if(swatches.getChildCount()==3)break;
        }}
        for(int color:palette){
            TextView chip=label("●",21);chip.setTextColor(color);
            chip.setGravity(Gravity.CENTER);
            swatches.addView(chip,new LayoutParams(0,dp(36),1));
            chip.setOnClickListener(v->setColor(color));
        }
        hue.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){}
            public void onProgressChanged(SeekBar b,int n,boolean fromUser){
                if(fromUser){hsv[0]=n;refresh(false);}
            }
        });
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){}
            public void onProgressChanged(SeekBar b,int n,boolean fromUser){
                if(fromUser){alpha=n;refresh(false);}
            }
        });
        refresh(true);
    }
    private static boolean contains(int[] array,int color){
        for(int item:array)if(item==color)return true;
        return false;
    }
    private int color(){return Color.HSVToColor(alpha,hsv);}
    private void setColor(int color){
        Color.colorToHSV(color,hsv);alpha=Color.alpha(color);
        hue.setProgress(Math.round(hsv[0]));opacity.setProgress(alpha);refresh(true);
    }
    private void refresh(boolean updateFields){
        square.invalidate();current.setBackgroundColor(color());
        current.setText(Color.alpha(color())<120?"AGORA":"");
        if(updateFields)renderFields();
    }
    private void renderFields(){
        if(writing)return;
        writing=true;
        fields.removeAllViews();
        editRow("RGB",new String[]{"R","G","B","A"},
            new int[]{Color.red(color()),Color.green(color()),Color.blue(color()),alpha},
            new int[]{255,255,255,255},true);
        editRow("HSV",new String[]{"H","S","V"},
            new int[]{Math.round(hsv[0]),Math.round(hsv[1]*100),Math.round(hsv[2]*100)},
            new int[]{360,100,100},false);
        writing=false;
    }
    private void editRow(String caption,String[] titles,int[] values,int[] max,boolean rgb){
        LinearLayout row=new LinearLayout(getContext());row.setGravity(Gravity.CENTER_VERTICAL);
        fields.addView(row,new LayoutParams(-1,dp(52)));
        TextView name=label(caption,11);row.addView(name,new LayoutParams(dp(35),-1));
        for(int i=0;i<titles.length;i++){
            final int index=i;
            LinearLayout cell=new LinearLayout(getContext());cell.setGravity(Gravity.CENTER);
            cell.setOrientation(VERTICAL);row.addView(cell,new LayoutParams(0,-1,1));
            TextView title=label(titles[i],9);title.setGravity(Gravity.CENTER);
            cell.addView(title,new LayoutParams(-1,dp(16)));
            EditText field=new EditText(getContext());field.setSingleLine(true);
            field.setInputType(InputType.TYPE_CLASS_NUMBER);
            field.setTextSize(12);field.setTextColor(0xffcda7f5);
            field.setGravity(Gravity.CENTER);field.setPadding(dp(2),0,dp(2),0);
            field.setSelectAllOnFocus(true);field.setText(String.valueOf(values[i]));
            cell.addView(field,new LayoutParams(-1,dp(32)));
            field.setOnEditorActionListener((v,a,e)->{
                applyNumber(field,index,max[index],rgb);return true;
            });
            field.setOnFocusChangeListener((v,focused)->{
                if(!focused&&!writing)applyNumber(field,index,max[index],rgb);
            });
        }
    }
    private void applyNumber(EditText field,int index,int max,boolean rgb){
        int next;try{next=Integer.parseInt(field.getText().toString());}
        catch(Exception e){return;}
        next=Math.min(max,Math.max(0,next));
        if(rgb){
            int[] channels={Color.red(color()),Color.green(color()),Color.blue(color()),alpha};
            channels[index]=next;
            setColor(Color.argb(channels[3],channels[0],channels[1],channels[2]));
        }else{
            if(index==0)hsv[0]=next;
            else if(index==1)hsv[1]=next/100f;
            else hsv[2]=next/100f;
            hue.setProgress(Math.round(hsv[0]));refresh(false);
        }
        field.clearFocus();
    }
    private final class Field extends View{
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Field(Context c){super(c);}
        @Override protected void onDraw(Canvas c){
            float w=getWidth(),h=getHeight();
            p.setShader(new LinearGradient(0,0,w,0,Color.WHITE,
                Color.HSVToColor(new float[]{hsv[0],1,1}),Shader.TileMode.CLAMP));
            c.drawRoundRect(0,0,w,h,dp(4),dp(4),p);
            p.setShader(new LinearGradient(0,0,0,h,Color.TRANSPARENT,
                Color.BLACK,Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,p);p.setShader(null);
            float x=hsv[1]*w,y=(1-hsv[2])*h;
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(3));p.setColor(Color.BLACK);
            c.drawCircle(x,y,dp(11),p);
            p.setStrokeWidth(dp(2));p.setColor(Color.WHITE);c.drawCircle(x,y,dp(9),p);
            p.setStyle(Paint.Style.FILL);
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            int a=e.getActionMasked();
            if(a==MotionEvent.ACTION_DOWN||a==MotionEvent.ACTION_MOVE){
                getParent().requestDisallowInterceptTouchEvent(true);
                hsv[1]=Math.max(0,Math.min(1,e.getX()/Math.max(1,getWidth())));
                hsv[2]=1-Math.max(0,Math.min(1,e.getY()/Math.max(1,getHeight())));
                refresh(false);return true;
            }
            if(a==MotionEvent.ACTION_UP){getParent().requestDisallowInterceptTouchEvent(false);
                renderFields();return true;}
            return true;
        }
    }
}
