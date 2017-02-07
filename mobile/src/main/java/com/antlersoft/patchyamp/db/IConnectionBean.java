/**
 * 
 */
package com.antlersoft.patchyamp.db;

import com.antlersoft.android.db.FieldAccessor;
import com.antlersoft.android.db.TableInterface;

/**
 * @author Michael A. MacDonald
 *
 */
@TableInterface(ImplementingClassName="AbstractConnectionBean",TableName="CONNECTION_BEAN")
interface IConnectionBean {
	@FieldAccessor
	long get_Id();
	@FieldAccessor
	String getNickname();
	@FieldAccessor
	String getUrl();
	@FieldAccessor
	String getLogin();
	@FieldAccessor
	String getPassword();
	@FieldAccessor
	boolean isVerified();
	@FieldAccessor
	boolean isKeepPassword();
}
