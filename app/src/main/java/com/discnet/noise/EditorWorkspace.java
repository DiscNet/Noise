package com.discnet.noise;

import android.content.Context;
import android.view.*;

/** Portrait bottom sheet, landscape side panel; no fixed vertical percentages. */
final class EditorWorkspace extends ViewGroup {
    View toolbar,stage,panel,categories;
    private int panelHeight;
    private boolean sheetTouched,fullscreen;
    EditorWorkspace(Context c){super(c);panelHeight=EditorTheme.dp(c,340);}
    void children(View top,View canvas,View tools,View navigation){toolbar=top;stage=canvas;panel=tools;categories=navigation;addView(top);addView(canvas);addView(tools);addView(navigation);}
    void fullscreen(boolean enabled){fullscreen=enabled;panel.setVisibility(enabled?GONE:VISIBLE);categories.setVisibility(enabled?GONE:VISIBLE);requestLayout();}
    boolean isFullscreen(){return fullscreen;}
    int sheetHeight(){return panel.getHeight();}
    void resizeSheet(int height){sheetTouched=true;panelHeight=Math.max(EditorTheme.dp(getContext(),48),Math.min(Math.max(EditorTheme.dp(getContext(),48),getHeight()-EditorTheme.dp(getContext(),224)),height));requestLayout();}
    void cycleSheet(){int min=EditorTheme.dp(getContext(),48),normal=EditorTheme.dp(getContext(),330);resizeSheet(panelHeight<=min+2?normal:panelHeight<normal+8?getHeight():min);}
    boolean landscape(){return getWidth()>getHeight()*1.15f;}
    private void exact(View v,int w,int h){v.measure(MeasureSpec.makeMeasureSpec(Math.max(0,w),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.max(0,h),MeasureSpec.EXACTLY));}
    @Override protected void onMeasure(int ws,int hs){int w=MeasureSpec.getSize(ws),h=MeasureSpec.getSize(hs),head=EditorTheme.dp(getContext(),52),nav=fullscreen?0:EditorTheme.dp(getContext(),34);setMeasuredDimension(w,h);exact(toolbar,w,head);
        int available=Math.max(0,h-head-nav);boolean wide=w>h*1.15f;
        if(fullscreen){exact(stage,w,available);exact(panel,0,0);}
        else if(wide){int pw=Math.min(EditorTheme.dp(getContext(),380),Math.round(w*.46f));exact(stage,w-pw,available);exact(panel,pw,available);}
        else{int ph=Math.min(sheetTouched?panelHeight:Math.round(available*.50f),Math.max(EditorTheme.dp(getContext(),48),available-EditorTheme.dp(getContext(),120)));exact(panel,w,ph);exact(stage,w,available-ph);}
        exact(categories,w,nav);
    }
    @Override protected void onLayout(boolean c,int l,int t,int r,int b){int w=r-l,head=toolbar.getMeasuredHeight(),nav=categories.getMeasuredHeight(),available=b-t-head-nav;
        toolbar.layout(0,0,w,head);
        if(fullscreen){stage.layout(0,head,w,head+available);panel.layout(0,0,0,0);}
        else if(landscape()){int pw=panel.getMeasuredWidth();stage.layout(0,head,w-pw,head+available);panel.layout(w-pw,head,w,head+available);}
        else{int ph=panel.getMeasuredHeight();stage.layout(0,head,w,head+available-ph);panel.layout(0,head+available-ph,w,head+available);}
        categories.layout(0,b-t-nav,w,b-t);
    }
}
