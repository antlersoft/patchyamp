package com.antlersoft.patchyamp.db;

import com.antlersoft.android.db.FieldAccessor;
import com.antlersoft.android.db.TableInterface;

@TableInterface(ImplementingClassName="PlayingList",ImplementingIsAbstract=false,TableName="PLAYING_LIST")
public interface IPlayingList {
    @FieldAccessor
    long get_Id();
    @FieldAccessor
    String getMediaId();
    @FieldAccessor
    long getNowPlayingIndex();
    @FieldAccessor
    long getListContentId();
}
