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

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import com.antlersoft.android.dbimpl.NewInstance;

import java.lang.Comparable;

/**
 * @author Michael A. MacDonald
 *
 */
public class ConnectionBean extends AbstractConnectionBean implements Comparable<ConnectionBean> {
    public static final NewInstance<ConnectionBean> newInstance=new NewInstance<ConnectionBean>() {
        public ConnectionBean get() { return new ConnectionBean(); }
    };
    public ConnectionBean()
    {
        set_Id(0);
        setPassword("");
        setKeepPassword(true);
        setNickname("");
        setLogin("");
        setUrl("");
    }

    public boolean isNew()
    {
        return get_Id()== 0;
    }

    public void save(SQLiteDatabase database) {
        ContentValues values=Gen_getValues();
        values.remove(GEN_FIELD__ID);
        if ( ! isKeepPassword()) {
            values.put(GEN_FIELD_PASSWORD, "");
        }
        if ( isNew()) {
            set_Id(database.insert(GEN_TABLE_NAME, null, values));
        } else {
            database.update(GEN_TABLE_NAME, values, GEN_FIELD__ID + " = ?", new String[] { Long.toString(get_Id()) });
        }
    }

    @Override
    public String toString() {
        if ( isNew())
        {
            return "New";
        }
        return getNickname()+":"+getUrl()+":"+getLogin();
    }

    /* (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(ConnectionBean another) {
        int result = getNickname().compareTo(another.getNickname());
        if (result == 0) {
            result = getUrl().compareTo(another.getUrl());
            if ( result == 0) {
                result = getLogin().compareTo(another.getLogin());
            }
        }
        return result;
    }
}
