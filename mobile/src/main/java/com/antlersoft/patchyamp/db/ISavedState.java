package com.antlersoft.patchyamp.db;

import com.antlersoft.android.db.FieldAccessor;
import com.antlersoft.android.db.TableInterface;

@TableInterface(ImplementingClassName="AbstractSavedState",TableName="SAVED_STATE")
public interface ISavedState {
	@FieldAccessor
	long getCurrentConnectionId();
	@FieldAccessor
	boolean getConnectionValid();
}
