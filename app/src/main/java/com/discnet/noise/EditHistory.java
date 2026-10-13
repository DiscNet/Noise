package com.discnet.noise;

import java.util.ArrayDeque;
import org.json.*;

/** One gesture is one transaction; redo is invalidated only by an actual change. */
final class EditHistory {
    private final ArrayDeque<EditorDocument> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    private EditorDocument start;
    void begin(EditorDocument current){if(start==null)start=current.copy();}
    void commit(EditorDocument current){if(start!=null&&!start.same(current)){undo.addLast(start);while(undo.size()>80)undo.removeFirst();redo.clear();}start=null;}
    boolean canUndo(){return !undo.isEmpty();}boolean canRedo(){return !redo.isEmpty();}
    int undoCount(){return undo.size();}
    EditorDocument undo(EditorDocument current){commit(current);if(undo.isEmpty())return current;redo.addLast(current.copy());return undo.removeLast();}
    EditorDocument redo(EditorDocument current){if(redo.isEmpty())return current;undo.addLast(current.copy());return redo.removeLast();}
    void clear(){undo.clear();redo.clear();start=null;}
    JSONObject toJson()throws JSONException{
        JSONObject o=new JSONObject();JSONArray u=new JSONArray(),r=new JSONArray();
        for(EditorDocument d:undo)u.put(d.toJson());for(EditorDocument d:redo)r.put(d.toJson());return o.put("undo",u).put("redo",r);
    }
    void restore(JSONObject o)throws JSONException{
        clear();if(o==null)return;JSONArray u=o.optJSONArray("undo"),r=o.optJSONArray("redo");
        if(u!=null)for(int i=Math.max(0,u.length()-80);i<u.length();i++)undo.add(EditorDocument.fromJson(u.getJSONObject(i)));
        if(r!=null)for(int i=Math.max(0,r.length()-80);i<r.length();i++)redo.add(EditorDocument.fromJson(r.getJSONObject(i)));
    }
}
