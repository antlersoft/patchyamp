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

import android.os.Bundle;
import android.support.v4.media.MediaMetadataCompat;

import java.util.Iterator;

public interface MusicProviderSource {
    String CUSTOM_METADATA_TRACK_SOURCE = "__SOURCE__";

    enum State {
        NON_INITIALIZED, INITIALIZING, INITIALIZED
    }

    interface ErrorCallback {
        void onError(String message, Throwable throwable);
    }

    interface MediaFetchResult {
        void setResult(String title, Iterator<MediaMetadataCompat> items);
    }

    interface ItemResult {
        void setResult(MediaMetadataCompat item);
    }

    State getState();
    void RequestLogin(Bundle extras, ErrorCallback error);

    /**
     * Typically returns all songs, shuffled
     * @param result
     */
    void getDefaultSongs(MediaFetchResult result);
    void GetPlaylists(MediaFetchResult result);
    void GetPlaylistSongs(String playListId, MediaFetchResult toSetQueue);
    void GetGenres(MediaFetchResult result);
    void GetGenreSongs(String genreId, MediaFetchResult toSetQueue);
    void GetSearchSongs(String anyMatch, MediaFetchResult toSetQueue);
    void GetArtists(MediaFetchResult result);
    void GetArtistAlbums(String id, MediaFetchResult result);
    void GetArtistSongs(String id, MediaFetchResult result);
    void GetAlbums(MediaFetchResult result);
    void GetAlbumSongs(String id, MediaFetchResult result);
    void GetSong(String id, ItemResult result);

}
