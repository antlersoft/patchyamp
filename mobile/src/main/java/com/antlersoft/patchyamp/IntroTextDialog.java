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

import android.app.Dialog;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.MenuItem.OnMenuItemClickListener;
import android.widget.Button;
import android.widget.TextView;

import com.antlersoft.patchyamp.db.PatchyDatabase;
import com.antlersoft.patchyamp.db.SavedState;
import com.example.android.uamp.ui.BaseActivity;

/**
 * @author Michael A. MacDonald
 *
 */
public class IntroTextDialog extends Dialog {

	private PackageInfo packageInfo;
	private PatchyDatabase database;
    BaseActivity mContext;
	
	static IntroTextDialog dialog;
	
	public static void showIntroTextIfNecessary(BaseActivity context, PatchyDatabase database)
	{
 		PackageInfo pi;
		try
		{
			pi = context.getPackageManager().getPackageInfo("com.antlersoft.patchyamp", 0);
		}
		catch (PackageManager.NameNotFoundException nnfe)
		{
			return;
		}
		SavedState mr = PatchyDatabase.getMostRecent(database.getReadableDatabase());
		if (mr == null || mr.getShowSplashVersion() != pi.versionCode)
		{
			if (dialog == null)
			{
				dialog = new IntroTextDialog(context, pi, database);
				dialog.show();
			}
		}
	}
	
	/**
	 * @param context -- Containing dialog
	 */
	private IntroTextDialog(BaseActivity context, PackageInfo pi, PatchyDatabase database) {
		super(context);
        mContext = context;
		setOwnerActivity(context);
		packageInfo = pi;
		this.database = database;
	}

	/* (non-Javadoc)
	 * @see android.app.Dialog#onCreate(android.os.Bundle)
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.intro_dialog);
		StringBuilder sb = new StringBuilder(getContext().getResources().getString(R.string.intro_title));
		sb.append(" ");
		sb.append(packageInfo.versionName);
		setTitle(sb);
		sb.delete(0, sb.length());
		sb.append(getContext().getResources().getString(R.string.intro_text));
		sb.append(packageInfo.versionName);
		sb.append(getContext().getResources().getString(R.string.intro_version_text));
		TextView introTextView = (TextView)findViewById(R.id.textIntroText);
		introTextView.setText(Html.fromHtml(sb.toString()));
		introTextView.setMovementMethod(LinkMovementMethod.getInstance());
		((Button)findViewById(R.id.buttonCloseIntro)).setOnClickListener(new View.OnClickListener() {

			/* (non-Javadoc)
			 * @see android.view.View.OnClickListener#onClick(android.view.View)
			 */
			@Override
			public void onClick(View v) {
				dismiss();
                mContext.arriveOnActivity();
			}
			
		});
		((Button)findViewById(R.id.buttonCloseIntroDontShow)).setOnClickListener(new View.OnClickListener() {

			/* (non-Javadoc)
			 * @see android.view.View.OnClickListener#onClick(android.view.View)
			 */
			@Override
			public void onClick(View v) {
				dontShowAgain();mContext.arriveOnActivity();
			}
			
		});

	}

	/* (non-Javadoc)
	 * @see android.app.Dialog#onCreateOptionsMenu(android.view.Menu)
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		getOwnerActivity().getMenuInflater().inflate(R.menu.intro_dialog_menu,menu);
		menu.findItem(R.id.itemOpenDoc).setOnMenuItemClickListener(new OnMenuItemClickListener() {

			@Override
			public boolean onMenuItemClick(MenuItem item) {
				Utils.showDocumentation(getOwnerActivity());
				dismiss();
				return true;
			}
		});
		menu.findItem(R.id.itemClose).setOnMenuItemClickListener(new OnMenuItemClickListener() {

			@Override
			public boolean onMenuItemClick(MenuItem item) {
				dismiss();
				return true;
			}
		});
		menu.findItem(R.id.itemDontShowAgain).setOnMenuItemClickListener(new OnMenuItemClickListener() {

			@Override
			public boolean onMenuItemClick(MenuItem item) {
				dontShowAgain();
				return true;
			}
		});
		return true;
	}

	private void dontShowAgain()
	{
		SQLiteDatabase db = database.getWritableDatabase();
		SavedState mostRecent = PatchyDatabase.getMostRecent(db);
		if (mostRecent != null)
		{
			mostRecent.setShowSplashVersion(packageInfo.versionCode);
			mostRecent.Gen_update(db);
		}
		dismiss();
	}
}
