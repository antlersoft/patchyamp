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

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;

import com.antlersoft.patchyamp.db.PatchyDatabase;
import com.antoniotari.reactiveampache.Exceptions.AmpacheApiException;
import com.antoniotari.reactiveampache.api.AmpacheApi;
import com.antoniotari.reactiveampache.models.Playlist;
import com.antoniotari.reactiveampache.models.Song;
import com.antoniotari.reactiveampache.models.Tag;
import com.example.android.uamp.model.MusicProviderSource;
import com.example.android.uamp.utils.LogHelper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * An implementation of MusicProviderSource based on the reactive-ampache library.
 *
 * This is a placeholder until we get a more sophisticated implementation of MusicProvider that
 * uses ampache directly
 */

public class AmpacheSource implements MusicProviderSource {
    static final String TAG = LogHelper.makeLogTag(AmpacheSource.class);
    Object mLock = new Object();
    private EAmpacheState mState = EAmpacheState.INITIAL;
    private List<Song> mSongs;
    private PatchyDatabase mDbHelper;
    ArrayList<MediaBrowserCompat.MediaItem> mItems = new ArrayList<>();
    private String mLastPlaylistId = "";

    public AmpacheSource(Context context) {
        mDbHelper = new PatchyDatabase(context);
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        AmpacheApi.INSTANCE.initSession(context);
        synchronized (mLock) {
            mState = EAmpacheState.LOGGING_IN;
        }
        AmpacheApi.INSTANCE.initUser("http://192.168.1.64:8942/ampache", "admin", "4rborDay")
                .flatMap(aVoid -> AmpacheApi.INSTANCE.handshake())
                .subscribe(handshakeResponse -> {
                    LogHelper.i(TAG, "Expiration: " + handshakeResponse.getSession_expire());
                    synchronized (mLock) {
                        mState = EAmpacheState.RETRIEVING;
                        mLock.notifyAll();
                    }
                }, throwable -> {
                    synchronized (mLock) {
                        mState = EAmpacheState.FAILURE;
                        mLock.notifyAll();
                    }
                    onError(throwable);
                });
    }

    private Iterator<MediaMetadataCompat> songIterator() {
        final Iterator<Song> s = mSongs.iterator();
        return new Iterator<MediaMetadataCompat>() {
            @Override
            public boolean hasNext() {
                return s.hasNext();
            }

            @Override
            public MediaMetadataCompat next() {
                Song song = s.next();
                StringBuilder sb = new StringBuilder();
                if (song.getTag() != null) {
                    for (Tag t : song.getTag()) {
                        if (sb.length() > 0)
                            sb.append(' ');
                        sb.append(t.getTag());
                    }
                }
                return new MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, song.getId())
                        .putString(MusicProviderSource.CUSTOM_METADATA_TRACK_SOURCE, song.getUrl())
                        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.getAlbum().getName())
                        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.getArtist().getName())
                        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.getTime()*1000L)
                        .putString(MediaMetadataCompat.METADATA_KEY_GENRE, sb.toString())
                        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, song.getArt())
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.getTitle())
                        .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, song.getTrack())
                        .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, song.getTrack()+1)
                        .build();

            }

            @Override
            public void remove() {
            }
        };
    }

    public static void patientWait(Object l)
    {
        try
        {
            l.wait();
        }
        catch (InterruptedException ie)
        {

        }
    }

    @Override
    public Iterator<MediaMetadataCompat> iterator() {
        synchronized (mLock) {
            if (mState == EAmpacheState.READY) {
                return songIterator();
            }
            while (mState == EAmpacheState.LOGGING_IN) {
                patientWait(mLock);
            }
            if (mState == EAmpacheState.READY)
                return songIterator();
            if (mState != EAmpacheState.RETRIEVING) {
                LogHelper.e(TAG, "Unexpected state trying to retrieve songs: "+mState.toString());
                return new ArrayList<MediaMetadataCompat>().iterator();
            }
            AmpacheApi.INSTANCE.getSongs().subscribe(songs -> {
                        if (songs == null)
                        {
                            LogHelper.e(TAG, "get songs return null song list");
                            synchronized (mLock) {
                                mState = EAmpacheState.FAILURE;
                                mLock.notifyAll();
                            }
                            return;
                        }
                        mSongs = songs;
                        synchronized (mLock) {
                            mState = EAmpacheState.READY;
                            mLock.notifyAll();
                        }
                    }, throwable -> {
                        onError(throwable);
                        synchronized (mLock) {
                            mState = EAmpacheState.FAILURE;
                            mLock.notifyAll();
                        }
                    }
            );
            while (mState == EAmpacheState.RETRIEVING) {
                patientWait(mLock);
            }
            if (mState == EAmpacheState.READY) {
                return songIterator();
            }
            LogHelper.e(TAG, "Failed to retrieve list of songs");
            return new ArrayList<MediaMetadataCompat>().iterator();
        }
    }

    @Override
    public void GetPlaylists(MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>> result) {
        final ArrayList<MediaBrowserCompat.MediaItem> items = new ArrayList<>();

        AmpacheApi.INSTANCE.getPlaylists().subscribe(playlists -> {
            for (Playlist pl : playlists) {
                MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
                        .setMediaId(PLAYLIST_PREFIX+pl.getId())
                        .setTitle(pl.getName())
                        .setSubtitle(pl.getType())
                        .setIconUri(Uri.parse("android.resource://" +
                                "com.antlersoft.patchyamp/drawable/ic_by_genre"))
                        .build();
                items.add(new MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
            }
            result.sendResult(items);
        },
                throwable -> {
                    onError(throwable);
                    result.sendResult(items);
                });
    }

    private void resultPlaylistSongs(final MediaMetadataCompatFromId toGet, final ItemFromMetadata toItem, final SetQueueDirectly toSetQueue, MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>> result) {
        List<MediaBrowserCompat.MediaItem> items = new ArrayList<>(mSongs.size());
        for (Song s : mSongs) {
            MediaMetadataCompat meta = toGet.getMetadata(s.getId());
            if (meta == null) {
                LogHelper.e(TAG, "No metadata found for song: "+s.toString());
            } else {
                items.add(toItem.getItem(meta));
            }
        }
        result.sendResult(items);
        if (toSetQueue != null) {
            toSetQueue.setCurrentQueueFromBrowse("Playlist", new Iterable<MediaMetadataCompat>() {
                public Iterator<MediaMetadataCompat> iterator() {
                    return new Iterator<MediaMetadataCompat>() {
                        private Iterator<Song> mSi = mSongs.iterator();

                        @Override
                        public boolean hasNext() {
                            return mSi.hasNext();
                        }

                        @Override
                        public MediaMetadataCompat next() {
                            return toGet.getMetadata(mSi.next().getId());
                        }

                        @Override
                        public void remove() {

                        }
                    };
                }
            });
        }
    }

    @Override
    public void GetPlaylistSongs(final String playListId, final MediaMetadataCompatFromId toGet, final ItemFromMetadata toItem, final SetQueueDirectly toSetQueue, MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>> result) {
        if (mLastPlaylistId.equals(playListId)) {
            resultPlaylistSongs(toGet, toItem, toSetQueue, result);
            return;
        }

        AmpacheApi.INSTANCE.getPlaylistSongs(playListId).subscribe(songs -> {
                    mSongs = songs;
                    mLastPlaylistId = playListId;
                    resultPlaylistSongs(toGet, toItem, toSetQueue, result);
                },
                throwable -> {
                    onError(throwable);
                    result.sendResult(new ArrayList<MediaBrowserCompat.MediaItem>(0));
                });
    }

    public void onError(Throwable throwable) {
        String message;
        if (throwable instanceof AmpacheApiException) {
            message = "Ampache error\ncode:" + ((AmpacheApiException) throwable).getAmpacheError().getCode() + "\nerror: " +
                    ((AmpacheApiException) throwable).getAmpacheError().getError();
        } else if (throwable.getLocalizedMessage()!=null) {
            message = throwable.getLocalizedMessage();
        } else {
            message = "Undefined error";
        }
        LogHelper.e(TAG, throwable, message);
    }}
