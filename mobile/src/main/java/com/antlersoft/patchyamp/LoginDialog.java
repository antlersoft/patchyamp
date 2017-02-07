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
package com.antlersoft.patchyamp;

import android.app.Activity;
import android.app.Dialog;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;

import com.antlersoft.patchyamp.db.ConnectionBean;
import com.antlersoft.patchyamp.db.PatchyDatabase;
import com.antlersoft.patchyamp.db.SavedState;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Created by mike on 2/6/17.
 */

public class LoginDialog extends Dialog {
    private PatchyDatabase mDbHelper;
    private Spinner mSpinnerConnection;
    private ConnectionBean mSelected;
    private EditText mTextNickname;
    private EditText mTextUrl;
    private EditText mTextLogin;
    private EditText mTextPassword;
    private CheckBox mKeepPassword;

    public LoginDialog(Activity context, PatchyDatabase dbHelper) {
        super(context);
        setOwnerActivity(context);
        mDbHelper = dbHelper;
    }

    /* (non-Javadoc)
     * @see android.app.Dialog#onCreate(android.os.Bundle)
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_dialog);
        mSpinnerConnection = (Spinner) findViewById(R.id.spinnerConnection);
        mSpinnerConnection.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mSelected = (ConnectionBean)parent.getSelectedItem();
                updateViewFromSelected();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                mSelected = null;
            }
        });
    }

    void arriveOnPage() {
        ArrayList<ConnectionBean> connections = new ArrayList<>();
        ConnectionBean.getAll(mDbHelper.getReadableDatabase(), ConnectionBean.GEN_TABLE_NAME, connections, ConnectionBean.newInstance);
        Collections.sort(connections);
        connections.add(0, new ConnectionBean());
        int connectionIndex = 0;
        if (connections.size() > 1) {
            SavedState mostRecent = PatchyDatabase.getMostRecent(mDbHelper.getReadableDatabase());
            if (mostRecent != null) {
                for (int i = 1; i < connections.size(); ++i) {
                    if (connections.get(i).get_Id() == mostRecent.getCurrentConnectionId()) {
                        connectionIndex = i;
                        break;
                    }
                }
            }
        }
        mSpinnerConnection.setAdapter(new ArrayAdapter<ConnectionBean>(this, android.R.layout.simple_spinner_item,
                connections.toArray(new ConnectionBean[connections.size()])));
        mSpinnerConnection.setSelection(connectionIndex, false);
        mSelected = connections.get(connectionIndex);
        updateViewFromSelected();

    }

    private void updateViewFromSelected() {
        mTextLogin.setText(mSelected.getLogin());
        mTextNickname.setText(mSelected.getNickname());
        mTextPassword.setText(mSelected.getPassword());
        mTextUrl.setText(mSelected.getUrl());
        mKeepPassword.setChecked(mSelected.isKeepPassword());
    }
    private void saveAndWriteRecent()
    {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        db.beginTransaction();
        try
        {
            mSelected.save(db);
            SavedState mostRecent = PatchyDatabase.getMostRecent(db);
            if (mostRecent == null)
            {
                mostRecent = new SavedState();
                mostRecent.setCurrentConnectionId(mSelected.get_Id());
                mostRecent.Gen_insert(db);
            }
            else
            {
                mostRecent.setCurrentConnectionId(mSelected.get_Id());
                mostRecent.Gen_update(db);
            }
            db.setTransactionSuccessful();
        }
        finally
        {
            db.endTransaction();
        }
    }
}
