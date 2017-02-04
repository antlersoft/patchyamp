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

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * Database upgrader for PatchyAmp
 */

public class PatchyDatabase extends SQLiteOpenHelper {
    static final int DBV_0_1_X = 1;

    public final static String TAG = PatchyDatabase.class.toString();

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(AbstractConnectionBean.GEN_CREATE);
        db.execSQL(SavedState.GEN_CREATE);
    }

    private void defaultUpgrade(SQLiteDatabase db)
    {
        Log.i(TAG, "Doing default database upgrade (drop and create tables)");
        db.execSQL("DROP TABLE IF EXISTS " + AbstractConnectionBean.GEN_TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + SavedState.GEN_TABLE_NAME);
        onCreate(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < DBV_0_1_X) {
            defaultUpgrade(db);
        }
        /*
        else {
            // Intermediate upgrades
            if (oldVersion < xxxx) {
                ...
            } ...
            // Current upgrade
            ...
        }
         */
    }
    /**
     * Return the object representing the app global state in the database, or null
     * if the object hasn't been set up yet
     * @param db App's database -- only needs to be readable
     * @return Object representing the single persistent instance of MostRecentBean, which
     * is the app's global state
     */
    SavedState getMostRecent()
    {
        ArrayList<MostRecentBean> recents = new ArrayList<MostRecentBean>(1);
        MostRecentBean.getAll(db, MostRecentBean.GEN_TABLE_NAME, recents, MostRecentBean.GEN_NEW);
        if (recents.size() == 0)
            return null;
        return recents.get(0);
    }

}
