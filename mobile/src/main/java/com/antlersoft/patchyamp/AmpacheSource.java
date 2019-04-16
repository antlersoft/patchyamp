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

import android.content.ContentValues;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.media.MediaMetadataCompat;

import com.antlersoft.patchyamp.db.ConnectionBean;
import com.antoniotari.reactiveampache.Exceptions.AmpacheApiException;
import com.antoniotari.reactiveampache.api.AmpacheApi;
import com.antoniotari.reactiveampache.models.Album;
import com.antoniotari.reactiveampache.models.Artist;
import com.antoniotari.reactiveampache.models.Playlist;
import com.antoniotari.reactiveampache.models.Song;
import com.antoniotari.reactiveampache.models.Tag;
import com.antoniotari.reactiveampache.models.TagEntity;
import com.example.android.uamp.model.MusicProviderSource;
import com.example.android.uamp.utils.LogHelper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;


/**
 * An implementation of MusicProviderSource based on the reactive-ampache library.
 *
 * This is a placeholder until we get a more sophisticated implementation of MusicProvider that
 * uses ampache directly
 */

public class AmpacheSource implements MusicProviderSource {
    static final String TAG = LogHelper.makeLogTag(AmpacheSource.class);
    Object mLock = new Object();
    private volatile EAmpacheState mState = EAmpacheState.INITIAL;
    private MusicProviderSource.ErrorCallback mErrorCallback;
    private static ArrayList<MediaMetadataCompat> mEmptyMetadata = new ArrayList<>();
    private ScheduledExecutorService _executorService = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> _pingFuture;
    public AmpacheSource(Context context) {
        AmpacheApi.INSTANCE.initSession(context);
    }

    private MediaMetadataCompat buildMetadataFromSong(Song song) {
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

    private Iterator<MediaMetadataCompat> songIterator(Iterable<Song> songs) {
        final Iterator<Song> s = songs.iterator();
        return new Iterator<MediaMetadataCompat>() {
            @Override
            public boolean hasNext() {
                return s.hasNext();
            }

            @Override
            public MediaMetadataCompat next() {
                return buildMetadataFromSong(s.next());
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

    private void waitForInitialization()
    {
        synchronized (mLock) {
            while (mState == EAmpacheState.LOGGING_IN || mState == EAmpacheState.RETRIEVING) {
                patientWait(mLock);
            }
        }
    }

    @Override
    public State getState() {
        switch (mState) {
            case FAILURE:
            case INITIAL:
            case NO_LOGIN_INFORMATION:
                return State.NON_INITIALIZED;
            case LOGGING_IN:
            case RETRIEVING:
                return State.INITIALIZING;
            case READY:
                return State.INITIALIZED;
        }
        return State.NON_INITIALIZED;
    }

    @Override
    public void getDefaultSongs(MediaFetchResult result) {
        final ArrayList<String> complain = new ArrayList<String>();
        AsyncRunner.RunAsync(() -> {
            waitForInitialization();
            if (mState != EAmpacheState.READY) {
                result.setResult("All songs shuffled", new ArrayList<MediaMetadataCompat>().iterator());
                complain.add("Not ready");
            }
            AmpacheApi.INSTANCE.getSongs().subscribe(songs -> {
                result.setResult("All songs shuffled", songIterator(songs));
            }, throwable -> {
                result.setResult("All songs shuffled", new ArrayList<MediaMetadataCompat>().iterator());
                synchronized (mLock) {
                    mState = EAmpacheState.FAILURE;
                    mLock.notifyAll();
                }
                onError(throwable);
            });
        }, () -> {
            if (! complain.isEmpty() && mErrorCallback!=null) {
                mErrorCallback.onError(complain.get(0), null);
            }
        });
    }

    @Override
    public void RequestLogin(Bundle extras, ErrorCallback error) {
        ContentValues connValues = extras.getParcelable(ConnectionBean.GEN_TABLE_NAME);
        mErrorCallback = error;
        if (connValues == null) {
            error.onError("No connection values in RequestLogin bundle", new IllegalArgumentException());
            return;
        }
        ConnectionBean bean = new ConnectionBean();
        bean.Gen_populate(connValues);
        boolean mayBeLoggingIn;
        // Set to logging in ASAP if not in login state
        synchronized (mLock) {
            mayBeLoggingIn = (getState() == State.INITIALIZING);
            if (! mayBeLoggingIn) {
                mState = EAmpacheState.LOGGING_IN;
            }
        }

        AsyncRunner.RunAsync(() -> {
                    synchronized (mLock) {
                        while (mayBeLoggingIn && (mState == EAmpacheState.LOGGING_IN || mState == EAmpacheState.RETRIEVING)) {
                            patientWait(mLock);
                        }
                        mErrorCallback = error;
                        mState = EAmpacheState.LOGGING_IN;
                        mLock.notifyAll();
                    }
                }, () -> {
            ScheduledFuture<?> f = _pingFuture;
            _pingFuture = null;
            if (f != null) {
                f.cancel(false);
            }
            AmpacheApi.INSTANCE.initUser(bean.getUrl(), bean.getLogin(), bean.getPassword())
                    .flatMap(aVoid -> AmpacheApi.INSTANCE.handshake())
                    .subscribe(handshakeResponse -> {
                        LogHelper.i(TAG, "Expiration: " + handshakeResponse.getSession_expire());
                        _pingFuture = _executorService.scheduleAtFixedRate(()->{
                            AmpacheApi.INSTANCE.ping().subscribe();
                        }, 300, 300, TimeUnit.SECONDS);
                        synchronized (mLock) {
                            mState = EAmpacheState.READY;
                            mLock.notifyAll();
                        }
                    }, throwable -> {
                        synchronized (mLock) {
                            mState = EAmpacheState.FAILURE;
                            mLock.notifyAll();
                        }
                        onError(throwable);
                    });
        });

    }

    @Override
    public void GetPlaylists(MediaFetchResult result) {
        final ArrayList<MediaMetadataCompat> items = new ArrayList<>();

        AsyncRunner.RunAsync(this::waitForInitialization, () -> {
            if (mState != EAmpacheState.READY) {
                result.setResult("Playlists", items.iterator());
                return;
            }

            AmpacheApi.INSTANCE.getPlaylists().subscribe(playlists -> {
                        for (Playlist pl : playlists) {
                            MediaMetadataCompat description = new MediaMetadataCompat.Builder()
                                    .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, pl.getId())
                                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, pl.getName())
                                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, pl.getType())
                                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, "android.resource://" +
                                            "com.antlersoft.patchyamp/drawable/ic_by_genre")
                                    .build();
                            items.add(description);
                        }
                        result.setResult("Playlists", items.iterator());
                    },
                    throwable -> {
                        onError(throwable);
                        result.setResult("Playlists", items.iterator());
                    });
        });
    }

    @Override
    public void GetGenres(MediaFetchResult result) {
        final ArrayList<MediaMetadataCompat> items = new ArrayList<>();

        AsyncRunner.RunAsync(this::waitForInitialization, () -> {
            if (mState != EAmpacheState.READY) {
                result.setResult("Genres", items.iterator());
                return;
            }

            AmpacheApi.INSTANCE.getTags().subscribe(playlists -> {
                        for (TagEntity pl : playlists) {
                            MediaMetadataCompat description = new MediaMetadataCompat.Builder()
                                    .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, pl.getId())
                                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, pl.getName())
                                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, Integer.toString(pl.getSongs()))
                                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, "android.resource://" +
                                            "com.antlersoft.patchyamp/drawable/ic_by_genre")
                                    .build();
                            items.add(description);
                        }
                        result.setResult("Genres", items.iterator());
                    },
                    throwable -> {
                        onError(throwable);
                        result.setResult("Genres", items.iterator());
                    });
        });

    }

    @Override
    public void GetPlaylistSongs(String playListId, MediaFetchResult toSetQueue) {
        final ArrayList<MediaMetadataCompat> items = new ArrayList<>();

        AsyncRunner.RunAsync(this::waitForInitialization, () -> {
            if (mState != EAmpacheState.READY) {
                toSetQueue.setResult("Playlist", items.iterator());
                return;
            }
            AmpacheApi.INSTANCE.getPlaylistSongs(playListId).subscribe((songs) -> {
                toSetQueue.setResult("Playlist", songIterator(songs));
            }, (throwable) -> {
                mState = EAmpacheState.FAILURE;
                toSetQueue.setResult("Playlist", items.iterator());
                onError(throwable);
            });
        });
    }

    @Override
    public void GetGenreSongs(String genreId, MediaFetchResult toSetQueue) {
        final ArrayList<MediaMetadataCompat> items = new ArrayList<>();

        AsyncRunner.RunAsync(this::waitForInitialization, () -> {
            if (mState != EAmpacheState.READY) {
                toSetQueue.setResult("Genre", items.iterator());
                return;
            }
            AmpacheApi.INSTANCE.getTagSongs(genreId).subscribe((songs) -> {
                toSetQueue.setResult("Genre", songIterator(songs));
            }, (throwable) -> {
                mState = EAmpacheState.FAILURE;
                toSetQueue.setResult("Genre", items.iterator());
                onError(throwable);
            });
        });
    }

    @Override
    public void GetSearchSongs(String anyMatch, MediaFetchResult toSetQueue) {
        AsyncRunner.RunAsync(this::waitForInitialization, () -> {
            if (mState != EAmpacheState.READY) {
                toSetQueue.setResult("Genre", mEmptyMetadata.iterator());
                return;
            }
            AmpacheApi.INSTANCE.searchSongs(anyMatch).subscribe((songs) -> {
                toSetQueue.setResult(anyMatch, songIterator(songs));
            }, throwable -> {
                toSetQueue.setResult(anyMatch, mEmptyMetadata.iterator());
                onError(throwable);
            });
        });
    }

    @Override
    public void GetArtists(MediaFetchResult result) {
        AsyncRunner.RunAsync(this::waitForInitialization, () -> {
            if (mState != EAmpacheState.READY) {
                result.setResult("Artists", mEmptyMetadata.iterator());
                return;
            }
           AmpacheApi.INSTANCE.getArtists().subscribe((artists) -> {
               List<MediaMetadataCompat> metadata = new ArrayList<MediaMetadataCompat>();
               for (Artist a : artists) {
                   MediaMetadataCompat description = new MediaMetadataCompat.Builder()
                           .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, a.getId())
                           .putString(MediaMetadataCompat.METADATA_KEY_TITLE, a.getName())
                           .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, Integer.toString(a.getSongs()))
                           .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, "android.resource://" +
                                   "com.antlersoft.patchyamp/drawable/ic_by_genre")
                           .build();
                    metadata.add(description);
               }
               result.setResult("Artists", metadata.iterator());
           }, throwable -> {
               result.setResult("Artists", mEmptyMetadata.iterator());
               onError(throwable);
           });
        });
    }

    @Override
    public void GetAlbums(MediaFetchResult result) {
        AsyncRunner.RunAsync(this::waitForInitialization, () -> {
            if (mState != EAmpacheState.READY) {
                result.setResult("Albums", mEmptyMetadata.iterator());
                return;
            }
            AmpacheApi.INSTANCE.getAlbums().subscribe((albums) -> {
                List<MediaMetadataCompat> metadata = new ArrayList<MediaMetadataCompat>();
                for (Album a : albums) {
                    MediaMetadataCompat description = new MediaMetadataCompat.Builder()
                            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, a.getId())
                            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, a.getName())
                            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, a.getArtist().getName())
                            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, a.getArt())
                            .build();
                    metadata.add(description);
                }
                result.setResult("Albums", metadata.iterator());
            }, throwable -> {
                result.setResult("Albums", mEmptyMetadata.iterator());
                onError(throwable);
            });
        });
    }

    @Override
    public void GetArtistAlbums(String id, MediaFetchResult result) {
        AsyncRunner.RunAsync(this::waitForInitialization, () -> {
            if (mState != EAmpacheState.READY) {
                result.setResult("Albums", mEmptyMetadata.iterator());
                return;
            }
            AmpacheApi.INSTANCE.getAlbumsFromArtist(id).subscribe((albums) -> {
                List<MediaMetadataCompat> metadata = new ArrayList<MediaMetadataCompat>();
                for (Album a : albums) {
                    MediaMetadataCompat description = new MediaMetadataCompat.Builder()
                            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, a.getId())
                            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, a.getName())
                            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, a.getArtist().getName())
                            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, a.getArt())
                            .build();
                    metadata.add(description);
                }
                result.setResult("Albums", metadata.iterator());
            }, throwable -> {
                result.setResult("Albums", mEmptyMetadata.iterator());
                onError(throwable);
            });
        });
    }

    @Override
    public void GetArtistSongs(String id, MediaFetchResult result) {
        final ArrayList<MediaMetadataCompat> items = new ArrayList<>();

        AsyncRunner.RunAsync(this::waitForInitialization, () -> {
            if (mState != EAmpacheState.READY) {
                result.setResult("Artist", items.iterator());
                return;
            }
            AmpacheApi.INSTANCE.getSongsFromArtist(id).subscribe((songs) -> {
                result.setResult("Artist", songIterator(songs));
            }, (throwable) -> {
                mState = EAmpacheState.FAILURE;
                result.setResult("Artist", items.iterator());
                onError(throwable);
            });
        });
    }

    @Override
    public void GetAlbumSongs(String id, MediaFetchResult result) {
        final ArrayList<MediaMetadataCompat> items = new ArrayList<>();

        AsyncRunner.RunAsync(this::waitForInitialization, () -> {
            if (mState != EAmpacheState.READY) {
                result.setResult("Album", items.iterator());
                return;
            }
            AmpacheApi.INSTANCE.getSongsFromAlbum(id).subscribe((songs) -> {
                result.setResult("Album", songIterator(songs));
            }, (throwable) -> {
                mState = EAmpacheState.FAILURE;
                result.setResult("Album", items.iterator());
                onError(throwable);
            });
        });

    }

    @Override
    public void GetSong(String id, ItemResult result) {
        AmpacheApi.INSTANCE.getSong(id).subscribe((Song song)->{
            result.setResult(buildMetadataFromSong(song));
        }, (throwable)->{
            mState = EAmpacheState.FAILURE;
            onError(throwable);
            result.setResult(null);
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
        if (mErrorCallback != null) {
            mErrorCallback.onError(message, throwable);
        }
    }
}
