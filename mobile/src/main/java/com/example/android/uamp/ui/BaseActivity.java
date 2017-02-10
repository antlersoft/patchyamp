/*
 * Copyright (C) 2017 Michael A. MacDonald
 *
 * MODIFICATION OF CODE ORIGINALLY RELEASESD BY Android Open Source Project
 * UNDER THE SAME LICENSE
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
/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.example.android.uamp.ui;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.media.MediaRouter;

import com.antlersoft.patchyamp.AsyncRunner;
import com.antlersoft.patchyamp.IntroTextDialog;
import com.antlersoft.patchyamp.LoginDialog;
import com.antlersoft.patchyamp.db.ConnectionBean;
import com.antlersoft.patchyamp.db.PatchyDatabase;
import com.antlersoft.patchyamp.db.SavedState;
import com.example.android.uamp.MusicService;
import com.antlersoft.patchyamp.R;
import com.example.android.uamp.model.MusicProvider;
import com.example.android.uamp.utils.LogHelper;
import com.example.android.uamp.utils.NetworkHelper;
import com.example.android.uamp.utils.ResourceHelper;

/**
 * Base activity for activities that need to show a playback control fragment when media is playing.
 */
public abstract class BaseActivity extends ActionBarCastActivity implements MediaBrowserProvider {

    private static final String TAG = LogHelper.makeLogTag(BaseActivity.class);

    private MediaBrowserCompat mMediaBrowser;
    private PlaybackControlsFragment mControlsFragment;
    private static boolean mShowedIntro;
    Object mConnectionLock = new Object();
    boolean mConnectionCompleted;

    static boolean mDoLogin;
    PatchyDatabase mDatabase;
    Handler mHandler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDatabase = PatchyDatabase.getInstance(getApplicationContext());

        LogHelper.d(TAG, "Activity onCreate");

        mHandler = new Handler();

        if (Build.VERSION.SDK_INT >= 21) {
            // Since our app icon has the same color as colorPrimary, our entry in the Recent Apps
            // list gets weird. We need to change either the icon or the color
            // of the TaskDescription.
            ActivityManager.TaskDescription taskDesc = new ActivityManager.TaskDescription(
                    getTitle().toString(),
                    BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher_white),
                    ResourceHelper.getThemeColor(this, R.attr.colorPrimary,
                            android.R.color.darker_gray));
            setTaskDescription(taskDesc);
        }

        // Connect a media browser just to get the media session token. There are other ways
        // this can be done, for example by sharing the session token directly.
        mMediaBrowser = new MediaBrowserCompat(this,
            new ComponentName(this, MusicService.class), mConnectionCallback, null);
    }

    @Override
    protected void onStart() {
        super.onStart();
        LogHelper.d(TAG, "Activity onStart");

        mControlsFragment = (PlaybackControlsFragment) getFragmentManager()
            .findFragmentById(R.id.fragment_playback_controls);
        if (mControlsFragment == null) {
            throw new IllegalStateException("Missing fragment with id 'controls'. Cannot continue.");
        }

        hidePlaybackControls();

        mMediaBrowser.connect();
        if (! mShowedIntro) {
            mShowedIntro = true;
            IntroTextDialog.showIntroTextIfNecessary(this, mDatabase);
        } else {
            arriveOnActivity();
        }
    }

    /**
     * This method is called from onStart or from the end of intro dialog if onStart
     * starts the intro dialog.  It waits in the background for the media controller
     * to connect, then checks if login is required.  If login is required and there
     * is an available connection bean, initiates login; if there is no connection
     * bean, starts the login dialog (as if from the menu item).  If no login is required,
     * calls onLoginInitiatedOrNotNeeded immediately.
     */
    public void arriveOnActivity() {
        AsyncRunner.RunAsync(() -> {
            synchronized (mConnectionLock) {
                while (! mConnectionCompleted) {
                    try {
                        mConnectionLock.wait();
                    } catch (InterruptedException ie) {

                    }
                }
            }
        }, () -> {
            if (mDoLogin) {
                mDoLogin = false;
                openLoginDialog(false);
                return;
            }
            MediaControllerCompat c = getSupportMediaController();
            if (c!=null) {
                c.sendCommand(MusicProvider.NEEDS_LOGIN_COMMAND, null, new ResultReceiver(mHandler) {
                    @Override
                    protected void onReceiveResult(int resultCode, Bundle resultData) {
                        if (resultCode == 0) {
                            onLoginInitiatedOrNotRequired();
                        } else {
                            // Login needed; see if we can use existing connection bean
                            SQLiteDatabase db = mDatabase.getReadableDatabase();
                            SavedState mostRecent = PatchyDatabase.getMostRecent(db);
                            ConnectionBean connection = null;
                            if (mostRecent != null) {
                                connection = new ConnectionBean();
                                if (connection.Gen_read(db, mostRecent.getCurrentConnectionId())) {
                                    requestLogin(connection);
                                    onLoginInitiatedOrNotRequired();
                                    return;
                                }
                            }
                            openLoginDialog(true);
                        }
                    }
                });
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        LogHelper.d(TAG, "Activity onStop");
        if (getSupportMediaController() != null) {
            getSupportMediaController().unregisterCallback(mMediaControllerCallback);
        }
        mMediaBrowser.disconnect();
    }

    public void requestLogin(ConnectionBean toConnect) {
        MediaControllerCompat c = getSupportMediaController();
        if (c!=null) {
            Bundle extras = new Bundle();
            extras.putParcelable(ConnectionBean.GEN_TABLE_NAME, toConnect.Gen_getValues());
            c.sendCommand(MusicProvider.LOGIN_COMMAND, extras, null);
        }
    }

    protected void openLoginDialog(boolean calledOnStartup) {
        new LoginDialog(this, mDatabase, calledOnStartup).show();
    }

    @Override
    public MediaBrowserCompat getMediaBrowser() {
        return mMediaBrowser;
    }

    private void onMediaControllerConnected() {
        synchronized (mConnectionLock) {
            mConnectionCompleted = true;
            mConnectionLock.notifyAll();
        }
    }

    /**
     * Called when the media controller has connected and been queried for login required;
     * then login initiated if required and this is called
     */
    public void onLoginInitiatedOrNotRequired() {

    }

    protected void showPlaybackControls() {
        LogHelper.d(TAG, "showPlaybackControls");
        if (NetworkHelper.isOnline(this)) {
            getFragmentManager().beginTransaction()
                .setCustomAnimations(
                    R.animator.slide_in_from_bottom, R.animator.slide_out_to_bottom,
                    R.animator.slide_in_from_bottom, R.animator.slide_out_to_bottom)
                .show(mControlsFragment)
                .commit();
        }
    }

    protected void hidePlaybackControls() {
        LogHelper.d(TAG, "hidePlaybackControls");
        getFragmentManager().beginTransaction()
            .hide(mControlsFragment)
            .commit();
    }

    /**
     * Check if the MediaSession is active and in a "playback-able" state
     * (not NONE and not STOPPED).
     *
     * @return true if the MediaSession's state requires playback controls to be visible.
     */
    protected boolean shouldShowControls() {
        MediaControllerCompat mediaController = getSupportMediaController();
        if (mediaController == null ||
            mediaController.getMetadata() == null ||
            mediaController.getPlaybackState() == null) {
            return false;
        }
        switch (mediaController.getPlaybackState().getState()) {
            case PlaybackStateCompat.STATE_ERROR:
            case PlaybackStateCompat.STATE_NONE:
            case PlaybackStateCompat.STATE_STOPPED:
                return false;
            default:
                return true;
        }
    }

    private void connectToSession(MediaSessionCompat.Token token) throws RemoteException {
        MediaControllerCompat mediaController = new MediaControllerCompat(this, token);
        setSupportMediaController(mediaController);
        mediaController.registerCallback(mMediaControllerCallback);

        if (shouldShowControls()) {
            showPlaybackControls();
        } else {
            LogHelper.d(TAG, "connectionCallback.onConnected: " +
                "hiding controls because metadata is null");
            hidePlaybackControls();
        }

        if (mControlsFragment != null) {
            mControlsFragment.onConnected();
        }

        onMediaControllerConnected();
    }

    // Callback that ensures that we are showing the controls
    private final MediaControllerCompat.Callback mMediaControllerCallback =
        new MediaControllerCompat.Callback() {
            @Override
            public void onPlaybackStateChanged(@NonNull PlaybackStateCompat state) {
                if (shouldShowControls()) {
                    showPlaybackControls();
                } else {
                    LogHelper.d(TAG, "mediaControllerCallback.onPlaybackStateChanged: " +
                            "hiding controls because state is ", state.getState());
                    hidePlaybackControls();
                }
            }

            @Override
            public void onMetadataChanged(MediaMetadataCompat metadata) {
                if (shouldShowControls()) {
                    showPlaybackControls();
                } else {
                    LogHelper.d(TAG, "mediaControllerCallback.onMetadataChanged: " +
                        "hiding controls because metadata is null");
                    hidePlaybackControls();
                }
            }
        };

    private final MediaBrowserCompat.ConnectionCallback mConnectionCallback =
        new MediaBrowserCompat.ConnectionCallback() {
            @Override
            public void onConnected() {
                LogHelper.d(TAG, "onConnected");
                try {
                    connectToSession(mMediaBrowser.getSessionToken());
                } catch (RemoteException e) {
                    LogHelper.e(TAG, e, "could not connect media controller");
                    hidePlaybackControls();
                }
            }
        };

}
