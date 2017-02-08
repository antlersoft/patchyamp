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
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;

import com.antlersoft.patchyamp.db.ConnectionBean;
import com.antlersoft.patchyamp.db.PatchyDatabase;
import com.antlersoft.patchyamp.db.SavedState;
import com.example.android.uamp.ui.BaseActivity;

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
    boolean mCalledOnStartUp;

    public LoginDialog(Activity context, PatchyDatabase dbHelper, boolean calledOnStartUp) {
        super(context);
        setOwnerActivity(context);
        mDbHelper = dbHelper;
        mCalledOnStartUp = calledOnStartUp;
    }

    /* (non-Javadoc)
     * @see android.app.Dialog#onCreate(android.os.Bundle)
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_dialog);
        mTextNickname = (EditText)findViewById(R.id.textNickname);
        mTextUrl = (EditText)findViewById(R.id.textURL);
        mTextLogin = (EditText)findViewById(R.id.textUsername);
        mTextPassword = (EditText)findViewById(R.id.textPASSWORD);
        mKeepPassword = (CheckBox)findViewById(R.id.checkboxKeepPassword);
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
        ((Button)findViewById(R.id.buttonGO)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateSelectedFromView();
                saveAndWriteRecent();
                dismiss();
                ((BaseActivity)getOwnerActivity()).requestLogin(mSelected);
                if (mCalledOnStartUp) {
                    ((BaseActivity)getOwnerActivity()).onLoginInitiatedOrNotRequired();
                }
            }
        });
        arriveOnPage();
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
        mSpinnerConnection.setAdapter(new ArrayAdapter<ConnectionBean>(getOwnerActivity(), android.R.layout.simple_spinner_item,
                connections.toArray(new ConnectionBean[connections.size()])));
        mSpinnerConnection.setSelection(connectionIndex, false);
        mSelected = connections.get(connectionIndex);
        updateViewFromSelected();

    }

    private void updateViewFromSelected() {
        if (mSelected != null) {
            mTextLogin.setText(mSelected.getLogin());
            mTextNickname.setText(mSelected.getNickname());
            mTextPassword.setText(mSelected.getPassword());
            mTextUrl.setText(mSelected.getUrl());
            mKeepPassword.setChecked(mSelected.isKeepPassword());
        }
    }

    private void updateSelectedFromView() {
        if (mSelected != null) {
            mSelected.setLogin(mTextLogin.getText().toString());
            mSelected.setNickname(mTextNickname.getText().toString());
            mSelected.setPassword(mTextPassword.getText().toString());
            mSelected.setUrl(mTextUrl.getText().toString());
            mSelected.setKeepPassword(mKeepPassword.isChecked());
        }
    }
    private void saveAndWriteRecent()
    {
        if (mSelected == null)
            return;
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
