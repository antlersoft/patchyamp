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
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.MediaMetadataCompat;

import java.util.Iterator;
import java.util.List;

public interface MusicProviderSource {
    String CUSTOM_METADATA_TRACK_SOURCE = "__SOURCE__";
    String PLAYLIST_PREFIX = "__PLAYLIST_";
    enum State {
        NON_INITIALIZED, INITIALIZING, INITIALIZED
    }

    interface ErrorCallback {
        void onError(String message, Throwable throwable);
    }

    interface AllSongsCallback {
        void onAllSongsRead(Iterator<MediaMetadataCompat> allSongs);
    }

    interface MediaMetadataCompatFromId {
        MediaMetadataCompat getMetadata(String id);
    }
    interface ItemFromMetadata {
        MediaBrowserCompat.MediaItem getItem(MediaMetadataCompat meta);
    }
    interface SetQueueDirectly {
        void setCurrentQueueFromBrowse(String title, Iterable<MediaMetadataCompat> items);
    }

    State getState();
    void RequestLogin(Bundle extras, AllSongsCallback allSongs, ErrorCallback error);
    void GetPlaylists(MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>> result);
    void GetPlaylistSongs(String playListId, MediaMetadataCompatFromId toGet, ItemFromMetadata toItem, SetQueueDirectly toSetQueue, MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>> result);
}
