package com.antlersoft.patchyamp.db;

import com.antlersoft.android.db.FieldAccessor;
import com.antlersoft.android.db.TableInterface;

@TableInterface(ImplementingClassName="SavedState",ImplementingIsAbstract=false,TableName="SAVED_STATE")
public interface ISavedState {
	@FieldAccessor
	long get_Id();
	@FieldAccessor
	long getCurrentConnectionId();
	@FieldAccessor
	boolean getConnectionValid();
	@FieldAccessor(Name="SHOW_SPLASH_VERSION")
	long getShowSplashVersion();
}
