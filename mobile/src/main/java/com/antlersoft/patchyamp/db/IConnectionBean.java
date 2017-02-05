/*
 * Copyright (C) 2017 Michael A. MacDonald
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
	int getLogin();
	@FieldAccessor
	String getPassword();
	@FieldAccessor
	boolean isVerified();
	@FieldAccessor
	boolean isKeepPassword();
}
