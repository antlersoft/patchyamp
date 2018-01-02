/*
 * Copyright (C) 2017 Michael A. MacDonald
 *
 * MODIFICATION OF CODE ORIGINALLY RELEASED BY Android Open Source Project
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

package com.example.android.uamp.model;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;

import com.antlersoft.patchyamp.R;
import com.example.android.uamp.playback.QueueManager;
import com.example.android.uamp.utils.LogHelper;
import com.example.android.uamp.utils.MediaIDHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.example.android.uamp.utils.MediaIDHelper.MEDIA_ID_ALBUMS;
import static com.example.android.uamp.utils.MediaIDHelper.MEDIA_ID_ARTISTS;
import static com.example.android.uamp.utils.MediaIDHelper.MEDIA_ID_ARTIST_SONGS;
import static com.example.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_GENRE;
import static com.example.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_SEARCH;
import static com.example.android.uamp.utils.MediaIDHelper.MEDIA_ID_PLAYLISTS;
import static com.example.android.uamp.utils.MediaIDHelper.MEDIA_ID_ROOT;
import static com.example.android.uamp.utils.MediaIDHelper.createMediaID;

/**
 * Simple data provider for music tracks. The actual metadata source is delegated to a
 * MusicProviderSource defined by a constructor argument of this class.
 */
public class MusicProvider {
    public static final String LOGIN_COMMAND = "LOGIN_COMMAND";
    public static final String NEEDS_LOGIN_COMMAND = "NEEDS_LOGIN_COMMAND";
    public static final String ERROR_REPORT_EVENT = "ERROR_REPORT_EVENT";
    public static final String ERROR_REPORT_EVENT_MESSAGE = "ERROR_REPORT_EVENT_MESSAGE";

    private static final String TAG = LogHelper.makeLogTag(MusicProvider.class);

    private final ConcurrentMap<String, MutableMediaMetadata> mMusicById;
    private List<MutableMediaMetadata> mLastRead;
    private String mLastCategory;
    private String mLastCategoryValue;
    private String mLastTitle;

    private MusicProviderSource mSource;

    private MusicProviderSource.ErrorCallback mErrorCallback;

    private static MusicProvider mInstance;

    public static MusicProvider getInstance(MusicProviderSource source, MusicProviderSource.ErrorCallback errorCallback) {
        if (mInstance == null) {
            mInstance = new MusicProvider(source, errorCallback);
        } else {
            mInstance.mErrorCallback = errorCallback;
        }
        return mInstance;
    }

    private MusicProvider(MusicProviderSource source, MusicProviderSource.ErrorCallback errorCallback) {
        mSource = source;
        mErrorCallback = errorCallback;

        mMusicById = new ConcurrentHashMap<>();
        clearCache();
    }

    private synchronized void clearCache() {
        mLastCategory = "NOT A CATEGORY";
        mLastCategoryValue="";
        mLastTitle="";
        mLastRead = new ArrayList<>();
    }

    public void requestLogin(Bundle extras) {
        clearCache();
        mSource.RequestLogin(extras, mErrorCallback);
    }

    public MusicProviderSource.State getState() {
        return mSource.getState();
    }

    /**
     * Very basic implementation of a search that filter music tracks with title containing
     * the given query.
     *
     */
    public void searchMusicBySongTitle(String query, MusicProviderSource.MediaFetchResult result) {
        mSource.GetSearchSongs(query, result);
    }

    /**
     * Very basic implementation of a search that filter music tracks with album containing
     * the given query.
     *
     */
    public void searchMusicByAlbum(String query, MusicProviderSource.MediaFetchResult result) {
    }

    /**
     * Very basic implementation of a search that filter music tracks with artist containing
     * the given query.
     *
     */
    public void searchMusicByArtist(String query, MusicProviderSource.MediaFetchResult result) {
    }

    public void getShuffledMusic(MusicProviderSource.MediaFetchResult result) {
        mSource.getDefaultSongs((title, it) -> {
            List<MediaMetadataCompat> songs = readSongsFromIterator(title, it, MEDIA_ID_MUSICS_BY_SEARCH, "random");
            Collections.shuffle(songs);
            result.setResult(title, songs.iterator());
        });
    }

    public synchronized void updateMusicArt(String musicId, Bitmap albumArt, Bitmap icon) {

        MutableMediaMetadata mutable = mMusicById.get(musicId);
        if (mutable == null) {
            return;
        }
        MediaMetadataCompat metadata = new MediaMetadataCompat.Builder(mutable.metadata)

                    // set high resolution bitmap in METADATA_KEY_ALBUM_ART. This is used, for
                    // example, on the lockscreen background when the media session is active.
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)

                    // set small version of the album art in the DISPLAY_ICON. This is used on
                    // the MediaDescription and thus it should be small to be serialized if
                    // necessary
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, icon)

                    .build();
        mutable.metadata = metadata;
    }

    public void getMusic(String id, MusicProviderSource.ItemResult result) {
        MutableMediaMetadata mutable = mMusicById.get(id);
        if (mutable != null) {
            result.setResult(mutable.metadata);
        } else {
            mSource.GetSong(id, (metadata)->{
                if (metadata != null) {
                    mMusicById.put(id, new MutableMediaMetadata(id, metadata));
                }
                result.setResult(metadata);
            });
        }
    }

    public void getMusic(String id, final ResultWrapper<MediaBrowserCompat.MediaItem> result) {
        getMusic(id, new MusicProviderSource.ItemResult() {
            @Override
            public void setResult(MediaMetadataCompat item) {
                result.sendResult(createMediaItem(item, MediaIDHelper.createMediaID(null, MediaIDHelper.getHierarchy(id)), MediaIDHelper.extractMusicIDFromMediaID(id)));
            }
        });
    }

    private synchronized boolean returnExisting(String category, String value, MusicProviderSource.MediaFetchResult result) {
        if (mLastCategory.equals(category) && mLastCategoryValue.equals(value)) {
            final List<MutableMediaMetadata> lastRead = mLastRead;
            final Iterator<MutableMediaMetadata> lastIt = lastRead.iterator();
            Iterator<MediaMetadataCompat> it = new Iterator<MediaMetadataCompat>() {
                @Override
                public boolean hasNext() {
                    return lastIt.hasNext();
                }

                @Override
                public MediaMetadataCompat next() {
                    return lastIt.next().metadata;
                }

                @Override
                public void remove() {

                }
            };
            result.setResult(mLastTitle, it);
            return true;
        }
        return false;
    }

    private synchronized List<MediaMetadataCompat> readSongsFromIterator(String title, Iterator<MediaMetadataCompat> it, String category, String categoryValue) {
        ArrayList<MediaMetadataCompat> result = new ArrayList<>();
        if (! it.hasNext()) {
            return result;
        }
        mLastCategoryValue = categoryValue;
        mLastCategory = category;
        mLastTitle = title;
        mMusicById.clear();
        mLastRead.clear();
        while (it.hasNext()) {
            MediaMetadataCompat s = it.next();
            result.add(s);
            String musicId = s.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID);
            MutableMediaMetadata mutable = new MutableMediaMetadata(musicId, s);
            mLastRead.add(mutable);
            mMusicById.put(musicId, mutable);
        }
        return result;
    }

    public void getMusicByGenre(String genreId, MusicProviderSource.MediaFetchResult result) {
        if (returnExisting(MEDIA_ID_MUSICS_BY_GENRE, genreId, result)) {
            return;
        }
        mSource.GetGenreSongs(genreId, (title, items) -> {
            result.setResult(title, readSongsFromIterator(title, items, MEDIA_ID_MUSICS_BY_GENRE, genreId).iterator());
        });
    }

    public void getMusicByPlaylist(String playlistId, MusicProviderSource.MediaFetchResult result) {
        if (returnExisting(MEDIA_ID_PLAYLISTS, playlistId, result)) {
            return;
        }
        mSource.GetPlaylistSongs(playlistId, (title, items) -> {
            result.setResult(title, readSongsFromIterator(title, items, MEDIA_ID_PLAYLISTS, playlistId).iterator());
        });
    }

    public void getMusicByAlbum(String albumId, MusicProviderSource.MediaFetchResult result) {
        if (returnExisting(MEDIA_ID_ALBUMS, albumId, result)) {
            return;
        }
        mSource.GetAlbumSongs(albumId, (title, items) -> {
            result.setResult(title, readSongsFromIterator(title, items, MEDIA_ID_ALBUMS, albumId).iterator());
        });
    }

    public void getMusicByArtist(String artistId, MusicProviderSource.MediaFetchResult result) {
        if (returnExisting(MEDIA_ID_ARTIST_SONGS, artistId, result)) {
            return;
        }
        mSource.GetArtistSongs(artistId, (title, items) -> {
            result.setResult(title, readSongsFromIterator(title, items, MEDIA_ID_ARTIST_SONGS, artistId).iterator());
        });
    }

    public void getChildren(String mediaId, Resources resources, QueueManager queueManager, ResultWrapper<List<MediaBrowserCompat.MediaItem>> result) {
        List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();
        String[] hierarchy;

        if (!MediaIDHelper.isBrowseable(mediaId)) {
            result.sendResult(mediaItems);
            return;
        }

        if (MEDIA_ID_ROOT.equals(mediaId)) {
            mediaItems.addAll(createBrowsableMediaItemForRoot(resources));

        } else if (MEDIA_ID_MUSICS_BY_GENRE.equals(mediaId)) {
            mSource.GetGenres((title,iterator)->{
                while (iterator.hasNext()) {
                    mediaItems.add(createBrowsableMediaItemForGenre(iterator.next(), MEDIA_ID_MUSICS_BY_GENRE));
                }
                result.sendResult(mediaItems);
            });
            return;
        } else if (MEDIA_ID_PLAYLISTS.equals(mediaId)) {
            mSource.GetPlaylists((title,iterator)->{
                    while (iterator.hasNext()) {
                        mediaItems.add(createBrowsableMediaItemForGenre(iterator.next(), MEDIA_ID_PLAYLISTS));
                    }
                    result.sendResult(mediaItems);
                });
            return;
        } else if (mediaId.startsWith(MEDIA_ID_PLAYLISTS)) {
            String playListId = MediaIDHelper.getHierarchy(mediaId)[1];
            if (returnExisting(MEDIA_ID_PLAYLISTS, playListId, (title, it)->{
                while (it.hasNext()) {
                    mediaItems.add(createMediaItem(it.next(), MEDIA_ID_PLAYLISTS, playListId));
                }
                result.sendResult(mediaItems);
            })) {
                return;
            }
            mSource.GetPlaylistSongs(playListId, (title,iterator)->{
                List<MediaMetadataCompat> songs = readSongsFromIterator(title, iterator, MEDIA_ID_PLAYLISTS, playListId);
                for (MediaMetadataCompat s : songs) {
                   mediaItems.add(createMediaItem(s, MEDIA_ID_PLAYLISTS, playListId));
                }
                result.sendResult(mediaItems);
            });
            return;
        } else if (mediaId.startsWith(MEDIA_ID_MUSICS_BY_GENRE)) {
            String genre = MediaIDHelper.getHierarchy(mediaId)[1];
            if (returnExisting(MEDIA_ID_MUSICS_BY_GENRE, genre, (title, it)->{
                while (it.hasNext()) {
                    mediaItems.add(createMediaItem(it.next(), MEDIA_ID_MUSICS_BY_GENRE, genre));
                }
                result.sendResult(mediaItems);
            })) {
                return;
            }
            mSource.GetGenreSongs(genre, (title, iterator)->{
                List<MediaMetadataCompat> songs = readSongsFromIterator(title, iterator, MEDIA_ID_MUSICS_BY_GENRE, genre);
                for (MediaMetadataCompat s : songs) {
                    mediaItems.add(createMediaItem(s, MEDIA_ID_MUSICS_BY_GENRE, genre));
                }
                result.sendResult(mediaItems);
            });
            return;
        } else if (mediaId.equals(MEDIA_ID_ARTISTS)) {
            mSource.GetArtists((title,iterator)->{
                while (iterator.hasNext()) {
                    mediaItems.add(createBrowsableMediaItemForGenre(iterator.next(), MEDIA_ID_ARTISTS));
                }
                result.sendResult(mediaItems);
            });
            return;
        } else if (mediaId.startsWith(MEDIA_ID_ARTISTS)) {
            String artist = MediaIDHelper.getHierarchy(mediaId)[1];
            MediaMetadataCompat allSongs = new MediaMetadataCompat.Builder().putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, artist)
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, resources.getString(R.string.browse_artists_all_songs))
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, resources.getString(R.string.browse_albums_subtitle))
                    .build();
            mediaItems.add(createBrowsableMediaItemForGenre(allSongs, MEDIA_ID_ARTIST_SONGS));
            mSource.GetArtistAlbums(artist, (title, iterator) -> {
                while (iterator.hasNext()) {
                    mediaItems.add(createBrowsableMediaItemForGenre(iterator.next(), MEDIA_ID_ALBUMS));
                }
                result.sendResult(mediaItems);
            });
            return;
        } else if (mediaId.startsWith(MEDIA_ID_ARTIST_SONGS)) {
            String artistId = MediaIDHelper.getHierarchy(mediaId)[1];
            if (returnExisting(MEDIA_ID_ARTIST_SONGS, artistId, (title, it)->{
                while (it.hasNext()) {
                    mediaItems.add(createMediaItem(it.next(), MEDIA_ID_ARTIST_SONGS, artistId));
                }
                result.sendResult(mediaItems);
            })) {
                return;
            }
            mSource.GetArtistSongs(artistId, (title, iterator)->{
                List<MediaMetadataCompat> songs = readSongsFromIterator(title, iterator, MEDIA_ID_ARTIST_SONGS, artistId);
                for (MediaMetadataCompat s : songs) {
                    mediaItems.add(createMediaItem(s, MEDIA_ID_ARTIST_SONGS, artistId));
                }
                result.sendResult(mediaItems);
            });
            return;
        } else if (mediaId.equals(MEDIA_ID_ALBUMS)) {
            mSource.GetAlbums((title, iterator) -> {
                while (iterator.hasNext()) {
                    mediaItems.add(createBrowsableMediaItemForGenre(iterator.next(), MEDIA_ID_ALBUMS));
                }
                result.sendResult(mediaItems);
            });
            return;
        } else if (mediaId.startsWith(MEDIA_ID_ALBUMS)) {
            String album = MediaIDHelper.getHierarchy(mediaId)[1];
            if (returnExisting(MEDIA_ID_ALBUMS, album, (title, it)->{
                while (it.hasNext()) {
                    mediaItems.add(createMediaItem(it.next(), MEDIA_ID_ARTIST_SONGS, album));
                }
                result.sendResult(mediaItems);
            })) {
                return;
            }
            mSource.GetAlbumSongs(album, (title, iterator)->{
                List<MediaMetadataCompat> songs = readSongsFromIterator(title, iterator, MEDIA_ID_ALBUMS, album);
                for (MediaMetadataCompat s : songs) {
                    mediaItems.add(createMediaItem(s, MEDIA_ID_ALBUMS, album));
                }
                result.sendResult(mediaItems);
            });
            return;
        } else {
            LogHelper.w(TAG, "Skipping unmatched mediaId: ", mediaId);
        }
        result.sendResult(mediaItems);
    }

    private List<MediaBrowserCompat.MediaItem> createBrowsableMediaItemForRoot(Resources resources) {
        ArrayList<MediaBrowserCompat.MediaItem> items = new ArrayList<>();
        MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
                .setMediaId(MEDIA_ID_PLAYLISTS)
                .setTitle(resources.getString(R.string.drawer_playlists_title))
                .setSubtitle(resources.getString(R.string.playlists_subtitle))
                .setIconUri(Uri.parse("android.resource://" +
                        "com.antlersoft.patchyamp/drawable/ic_by_genre"))
                .build();
        items.add(new MediaBrowserCompat.MediaItem(description,
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
        description = new MediaDescriptionCompat.Builder()
                .setMediaId(MEDIA_ID_ARTISTS)
                .setTitle(resources.getString(R.string.browse_artists))
                .setSubtitle(resources.getString(R.string.browse_artists_subtitle))
                .setIconUri(Uri.parse("android.resource://" +
                        "com.antlersoft.patchyamp/drawable/ic_by_genre"))
                .build();
        items.add(new MediaBrowserCompat.MediaItem(description,
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
        description = new MediaDescriptionCompat.Builder()
                .setMediaId(MEDIA_ID_ALBUMS)
                .setTitle(resources.getString(R.string.browse_albums))
                .setSubtitle(resources.getString(R.string.browse_albums_subtitle))
                .setIconUri(Uri.parse("android.resource://" +
                        "com.antlersoft.patchyamp/drawable/ic_by_genre"))
                .build();
        items.add(new MediaBrowserCompat.MediaItem(description,
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
        description = new MediaDescriptionCompat.Builder()
                .setMediaId(MEDIA_ID_MUSICS_BY_GENRE)
                .setTitle(resources.getString(R.string.browse_genres))
                .setSubtitle(resources.getString(R.string.browse_genre_subtitle))
                .setIconUri(Uri.parse("android.resource://" +
                        "com.antlersoft.patchyamp/drawable/ic_by_genre"))
                .build();
        items.add(new MediaBrowserCompat.MediaItem(description,
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
        return items;
    }

    private MediaBrowserCompat.MediaItem createBrowsableMediaItemForGenre(MediaMetadataCompat genre, String category) {
        MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
                .setMediaId(createMediaID(null, category, genre.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)))
                .setTitle(genre.getString(MediaMetadataCompat.METADATA_KEY_TITLE))
                .setSubtitle(genre.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE))
                .build();
        return new MediaBrowserCompat.MediaItem(description,
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE);
    }

    private MediaBrowserCompat.MediaItem createMediaItem(MediaMetadataCompat metadata, String keyName, String idInKey) {
        // Since mediaMetadata fields are immutable, we need to create a copy, so we
        // can set a hierarchy-aware mediaID. We will need to know the media hierarchy
        // when we get a onPlayFromMusicID call, so we can create the proper queue based
        // on where the music was selected from (by artist, by genre, random, etc)
        if (keyName == null)
            keyName = MEDIA_ID_MUSICS_BY_GENRE;
        if (idInKey == null)
            idInKey = metadata.getString(MediaMetadataCompat.METADATA_KEY_GENRE);
        String hierarchyAwareMediaID = MediaIDHelper.createMediaID(
                metadata.getDescription().getMediaId(), keyName, idInKey);
        MediaMetadataCompat copy = new MediaMetadataCompat.Builder(metadata)
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, hierarchyAwareMediaID)
                .build();
        return new MediaBrowserCompat.MediaItem(copy.getDescription(),
                MediaBrowserCompat.MediaItem.FLAG_PLAYABLE);

    }

}
