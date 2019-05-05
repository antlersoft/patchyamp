package com.antlersoft.patchyamp.db;

import com.antlersoft.android.db.FieldAccessor;
import com.antlersoft.android.db.TableInterface;

@TableInterface(ImplementingClassName="ListContent",ImplementingIsAbstract=false,TableName="LIST_CONTENT")
public interface IListContent {
    @FieldAccessor
    long get_Id();
    @FieldAccessor
    String getJson();
}
