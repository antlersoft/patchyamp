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

import java.lang.Void;

import android.os.AsyncTask;

/**
 * Created by mike on 2/6/17.
 */

public class AsyncRunner extends AsyncTask<Void, Void, Void> {
    private Runnable mInBackground;
    private Runnable mContinueOnUiThread;

    public static void RunAsync(Runnable inBackground, Runnable continueOnUiThread) {
        new AsyncRunner(inBackground, continueOnUiThread).execute();
    }

    private AsyncRunner(Runnable inBackground, Runnable continueOnUiThread) {
        mInBackground = inBackground;
        mContinueOnUiThread = continueOnUiThread;
    }

    @Override
    protected Void doInBackground(Void... params) {
        mInBackground.run();
        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        if (mContinueOnUiThread != null)
            mContinueOnUiThread.run();
    }
}
