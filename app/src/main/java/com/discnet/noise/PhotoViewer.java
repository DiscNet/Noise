package com.discnet.noise;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Immersive image viewer; no editing until the explicit Edit action. */
final class PhotoViewer extends FrameLayout {
    interface Actions {
        void back();
        void edit(Uri uri);
        void share(Uri uri);
        void download(MediaLibrary.Photo photo);
        void nearingEnd();
    }
    private final Activity activity;
    private final MediaLibrary media;
    private final Actions actions;
    private final ArrayList<MediaLibrary.Photo> items=new ArrayList<>();
    private final ZoomPhotoView image;
    private final LinearLayout header,footer;
    private final TextView title,counter;
    private TextView feedback;
    private final ExecutorService decode=Executors.newFixedThreadPool(2);
    private final android.util.LruCache<String,Bitmap> full=new android.util.LruCache<String,Bitmap>(42*1024*1024){
        @Override protected int sizeOf(String key,Bitmap value){return value.getByteCount();}
    };
    private final Set<String> inflight=Collections.synchronizedSet(new HashSet<>());
    private final Handler handler=new Handler(Looper.getMainLooper());
    private int index,serial;
    private boolean closed,controlsVisible=true;
    PhotoViewer(Activity a,MediaLibrary store,List<MediaLibrary.Photo> source,
                int at,Actions action){
        super(a);activity=a;media=store;actions=action;
        items.addAll(source);index=Math.max(0,Math.min(at,items.size()-1));
        setBackgroundColor(0xff080808);
        setClickable(true);setFocusable(true);
        image=new ZoomPhotoView(a,new ZoomPhotoView.Gestures(){
            @Override public void controls(){toggleControls();}
            @Override public void page(int delta){move(delta);}
        });
        addView(image,new LayoutParams(-1,-1));

        header=new LinearLayout(a);header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12),0,dp(16),0);
        header.setBackgroundColor(0x88080808);
        LayoutParams hp=new LayoutParams(-1,dp(64),Gravity.TOP);
        addView(header,hp);
        ImageView back=IconArt.button(a,IconArt.BACK,"Voltar à galeria");
        back.setBackground(circle());
        back.setImageDrawable(new IconArt(IconArt.BACK,0xffffffff));
        header.addView(back,new LinearLayout.LayoutParams(dp(46),dp(46)));
        back.setOnClickListener(v->actions.back());
        LinearLayout text=new LinearLayout(a);text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(dp(14),0,0,0);
        header.addView(text,new LinearLayout.LayoutParams(0,-2,1));
        title=label("Fotografia",14,0xffeeeeee);
        title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        text.addView(title);
        counter=label("",12,0xffa9a9a9);text.addView(counter);

        footer=new LinearLayout(a);footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(18),dp(12),dp(18),dp(24));
        footer.setBackgroundColor(0x88080808);
        LayoutParams fp=new LayoutParams(-1,dp(99),Gravity.BOTTOM);
        addView(footer,fp);
        makeAction(IconArt.SAVE,"Baixar",()->actions.download(current()));
        makeAction(IconArt.RESIZE,"Editar",()->actions.edit(current().uri));
        makeAction(IconArt.SHARE,"Compartilhar",()->actions.share(current().uri));

        feedback=label("",12,0xffffffff);
        feedback.setGravity(Gravity.CENTER);
        feedback.setPadding(dp(12),dp(7),dp(12),dp(7));
        feedback.setBackground(pill());
        feedback.setVisibility(GONE);
        LayoutParams fb=new LayoutParams(-2,dp(37),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        fb.bottomMargin=dp(111);
        addView(feedback,fb);
        render();
    }
    private MediaLibrary.Photo current(){return items.get(index);}
    private void makeAction(int icon,String description,Runnable action){
        FrameLayout outer=new FrameLayout(activity);
        LinearLayout.LayoutParams margin=new LinearLayout.LayoutParams(dp(60),dp(56));
        margin.leftMargin=dp(13);margin.rightMargin=dp(13);
        footer.addView(outer,margin);
        ImageView button=new ImageView(activity);
        button.setImageDrawable(new IconArt(icon,0xffffffff));
        button.setPadding(dp(16),dp(16),dp(16),dp(16));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x45ffffff),circle(),null));
        button.setContentDescription(description);
        button.setClickable(true);button.setFocusable(true);
        outer.addView(button,new LayoutParams(dp(56),dp(56),Gravity.CENTER));
        button.setOnClickListener(v->action.run());
    }
    private GradientDrawable pill(){
        GradientDrawable d=new GradientDrawable();
        d.setColor(0xff27232f);d.setCornerRadius(dp(7));return d;
    }
    private GradientDrawable circle(){
        GradientDrawable d=new GradientDrawable();
        d.setColor(0xc9232326);d.setCornerRadius(dp(50));
        d.setStroke(Math.max(1,dp(1)),0xff777777);
        return d;
    }
    private TextView label(String s,int size,int color){
        TextView v=new TextView(activity);v.setText(s);v.setTextSize(size);
        v.setTextColor(color);v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }
    int count(){return items.size();}
    int position(){return index;}
    void append(List<MediaLibrary.Photo> added){
        if(closed)return;
        HashSet<Long> seen=new HashSet<>();
        for(MediaLibrary.Photo p:items)seen.add(p.id);
        for(MediaLibrary.Photo p:added)if(seen.add(p.id))items.add(p);
        counter.setText((index+1)+" / "+items.size());
    }
    void showMessage(String message){
        if(closed)return;
        feedback.setText(message);feedback.setVisibility(VISIBLE);
        handler.removeCallbacks(clearMessage);
        handler.postDelayed(clearMessage,2300);
    }
    private final Runnable clearMessage=()->feedback.setVisibility(GONE);
    void toggleControls(){
        controlsVisible=!controlsVisible;
        header.animate().alpha(controlsVisible?1f:0f).setDuration(190).start();
        footer.animate().alpha(controlsVisible?1f:0f).setDuration(190).start();
        header.setClickable(controlsVisible);footer.setClickable(controlsVisible);
    }
    void move(int delta){
        int next=index+delta;
        if(next<0||next>=items.size()){
            if(next>=items.size())actions.nearingEnd();
            image.animate().translationX(delta>0?-dp(14):dp(14)).setDuration(90)
                .withEndAction(()->image.animate().translationX(0).setDuration(135).start()).start();
            return;
        }
        // Keep the outgoing bitmap on a temporary image layer while the
        // incoming image actually travels across the width of the viewport.
        // This makes the transition spatial, not a tiny fade on one ImageView.
        image.animate().cancel();
        final int distance=Math.max(getWidth(),dp(300));
        ImageView outgoing=new ImageView(activity);
        outgoing.setBackgroundColor(0xff080808);
        outgoing.setImageDrawable(image.getDrawable());
        outgoing.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addView(outgoing,1,new LayoutParams(-1,-1));
        index=next;
        render();
        image.setTranslationX((delta>0?1:-1)*distance);
        image.setAlpha(1f);
        android.view.animation.Interpolator easing=
            new android.view.animation.DecelerateInterpolator(1.35f);
        outgoing.animate().translationX((delta>0?-1:1)*distance)
            .setInterpolator(easing).setDuration(280)
            .withEndAction(()->removeView(outgoing)).start();
        image.animate().translationX(0).setInterpolator(easing)
            .setDuration(280).start();
    }
    private void render(){
        if(closed||items.isEmpty())return;
        MediaLibrary.Photo p=current();
        title.setText(p.name);
        counter.setText((index+1)+" / "+items.size()+"  ·  "+
            MediaLibrary.dateTitle(p.added));
        final int token=++serial;
        // Move() owns the slide transform; render only swaps photo content.
        // Preloaded neighbours appear immediately without an empty frame.
        image.setImageBitmap(null);
        Bitmap ready=full.get(p.uri.toString());
        if(ready!=null)image.setImageBitmap(ready);
        else{
            media.thumbnail(p,540,bitmap->{
                if(!closed&&token==serial&&image.getDrawable()==null)
                    image.setImageBitmap(bitmap);
            });
            load(p,token,true);
        }
        if(index>=items.size()-8)actions.nearingEnd();
        if(index>0)load(items.get(index-1),token,false);
        if(index+1<items.size())load(items.get(index+1),token,false);
    }
    private void load(MediaLibrary.Photo photo,int token,boolean visible){
        String key=photo.uri.toString();
        if(full.get(key)!=null||!inflight.add(key)||closed)return;
        decode.execute(()->{
            Bitmap bmp=null;
            try{bmp=media.decode(photo.uri,2048);}
            catch(Exception|OutOfMemoryError error){
                if(visible)handler.post(()->showMessage("Esta imagem não está disponível"));
            }finally{inflight.remove(key);}
            if(closed){if(bmp!=null)bmp.recycle();return;}
            if(bmp==null)return;
            full.put(key,bmp);
            Bitmap decoded=bmp;
            handler.post(()->{
                if(!closed&&token==serial&&visible)image.setImageBitmap(decoded);
            });
        });
    }
    void close(){
        closed=true;serial++;
        handler.removeCallbacksAndMessages(null);
        decode.shutdownNow();
        image.setImageDrawable(null);
        // Evict only after clearing the drawable; worker checks closed.
        full.evictAll();
    }
    private int dp(float v){return EditorTheme.dp(activity,v);}
}
