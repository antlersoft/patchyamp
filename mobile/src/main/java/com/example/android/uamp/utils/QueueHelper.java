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

package com.example.android.uamp.utils;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.text.TextUtils;

import com.example.android.uamp.VoiceSearchParams;
import com.example.android.uamp.model.MusicProvider;
import com.example.android.uamp.model.MusicProviderSource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.example.android.uamp.utils.MediaIDHelper.MEDIA_ID_ALBUMS;
import static com.example.android.uamp.utils.MediaIDHelper.MEDIA_ID_ARTIST_SONGS;
import static com.example.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_GENRE;
import static com.example.android.uamp.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_SEARCH;
import static com.example.android.uamp.utils.MediaIDHelper.MEDIA_ID_PLAYLISTS;

/**
 * Utility class to help on queue related tasks.
 */
public class QueueHelper {
    public interface QueryResult {
        void SetResult(List<MediaSessionCompat.QueueItem> queueItems);
    }

    private static MusicProviderSource.MediaFetchResult FetchToQueryResult(QueryResult qr, String... categories) {
        return (title, items) -> {
            qr.SetResult(convertToQueue(items, categories));
        };
    }

    private static final String TAG = LogHelper.makeLogTag(QueueHelper.class);

    private static final int RANDOM_QUEUE_SIZE = 10;

    public static void getPlayingQueue(String mediaId,
            MusicProvider musicProvider, QueryResult result) {

        // extract the browsing hierarchy from the media ID:
        String[] hierarchy = MediaIDHelper.getHierarchy(mediaId);

        if (hierarchy.length != 2) {
            LogHelper.e(TAG, "Could not build a playing queue for this mediaId: ", mediaId);
            return;
        }

        String categoryType = hierarchy[0];
        String categoryValue = hierarchy[1];
        LogHelper.d(TAG, "Creating playing queue for ", categoryType, ",  ", categoryValue);

        if (categoryType.equals(MEDIA_ID_MUSICS_BY_GENRE)) {
            musicProvider.getMusicByGenre(categoryValue, FetchToQueryResult(result, hierarchy[0], hierarchy[1]));
            return;
        } else if (categoryType.equals(MEDIA_ID_MUSICS_BY_SEARCH)) {
            musicProvider.searchMusicBySongTitle(categoryValue, FetchToQueryResult(result, hierarchy[0], hierarchy[1]));
            return;
        } else if (categoryType.equals(MEDIA_ID_PLAYLISTS)) {
            musicProvider.getMusicByPlaylist(categoryValue, FetchToQueryResult(result, hierarchy[0], hierarchy[1]));
            return;
        } else if (categoryType.equals(MEDIA_ID_ALBUMS)) {
            musicProvider.getMusicByAlbum(categoryValue, FetchToQueryResult(result, hierarchy[0], hierarchy[1]));
            return;
        } else if (categoryType.equals(MEDIA_ID_ARTIST_SONGS)) {
            musicProvider.getMusicByArtist(categoryValue, FetchToQueryResult(result, hierarchy[0], hierarchy[1]));
            return;
        }

        LogHelper.e(TAG, "Unrecognized category type: ", categoryType, " for media ", mediaId);
    }

    public static void getPlayingQueueFromSearch(String query,
            Bundle queryParams, MusicProvider musicProvider, QueryResult result) {

        LogHelper.d(TAG, "Creating playing queue for musics from search: ", query,
            " params=", queryParams);

        VoiceSearchParams params = new VoiceSearchParams(query, queryParams);

        LogHelper.d(TAG, "VoiceSearchParams: ", params);

        if (params.isAny) {
            // If isAny is true, we will play anything. This is app-dependent, and can be,
            // for example, favorite playlists, "I'm feeling lucky", most recent, etc.
            getRandomQueue(musicProvider, result);
            return;
        }

        if (params.isAlbumFocus) {
            musicProvider.searchMusicByAlbum(params.album, FetchToQueryResult(result, MEDIA_ID_MUSICS_BY_SEARCH, query));
            return;
        } else if (params.isGenreFocus) {
            musicProvider.getMusicByGenre(params.genre, FetchToQueryResult(result, MEDIA_ID_MUSICS_BY_GENRE, params.genre));
            return;
        } else if (params.isArtistFocus) {
            musicProvider.searchMusicByArtist(params.artist, FetchToQueryResult(result, MEDIA_ID_MUSICS_BY_SEARCH, query));
            return;
        } else if (params.isSongFocus) {
            musicProvider.searchMusicBySongTitle(params.song, FetchToQueryResult(result, MEDIA_ID_MUSICS_BY_SEARCH, query));
            return;
        }

        // If there was no results using media focus parameter, we do an unstructured query.
        // This is useful when the user is searching for something that looks like an artist
        // to Google, for example, but is not. For example, a user searching for Madonna on
        // a PodCast application wouldn't get results if we only looked at the
        // Artist (podcast author). Then, we can instead do an unstructured search.
        if (params.isUnstructured) {
            // To keep it simple for this example, we do unstructured searches on the
            // song title only. A real world application could search on other fields as well.
            musicProvider.searchMusicBySongTitle(query, FetchToQueryResult(result, MEDIA_ID_MUSICS_BY_SEARCH, query));
        }
    }

    public static int getMusicIndexOnQueue(Iterable<MediaSessionCompat.QueueItem> queue,
             String mediaId) {
        int index = 0;
        for (MediaSessionCompat.QueueItem item : queue) {
            if (mediaId.equals(item.getDescription().getMediaId())) {
                return index;
            }
            index++;
        }
        return -1;
    }

    public static int getMusicIndexOnQueue(Iterable<MediaSessionCompat.QueueItem> queue,
             long queueId) {
        int index = 0;
        for (MediaSessionCompat.QueueItem item : queue) {
            if (queueId == item.getQueueId()) {
                return index;
            }
            index++;
        }
        return -1;
    }

    public static List<MediaSessionCompat.QueueItem> convertToQueue(
            Iterator<MediaMetadataCompat> tracks, String... categories) {
        List<MediaSessionCompat.QueueItem> queue = new ArrayList<>();
        int count = 0;
        while (tracks.hasNext()) {
            MediaMetadataCompat track = tracks.next();
            // We create a hierarchy-aware mediaID, so we know what the queue is about by looking
            // at the QueueItem media IDs.
            String hierarchyAwareMediaID = MediaIDHelper.createMediaID(
                    track.getDescription().getMediaId(), categories);

            MediaMetadataCompat trackCopy = new MediaMetadataCompat.Builder(track)
                    .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, hierarchyAwareMediaID)
                    .build();

            // We don't expect queues to change after created, so we use the item index as the
            // queueId. Any other number unique in the queue would work.
            MediaSessionCompat.QueueItem item = new MediaSessionCompat.QueueItem(
                    trackCopy.getDescription(), count++);
            queue.add(item);
        }
        return queue;

    }

    /**
     * Create a random queue with at most {@link #RANDOM_QUEUE_SIZE} elements.
     *
     * @param musicProvider the provider used for fetching music.
     * @return list containing {@link MediaSessionCompat.QueueItem}'s
     */
    public static void getRandomQueue(MusicProvider musicProvider, QueryResult qr) {
        musicProvider.getShuffledMusic(FetchToQueryResult(qr, MEDIA_ID_MUSICS_BY_SEARCH, "random"));
    }

    public static boolean isIndexPlayable(int index, List<MediaSessionCompat.QueueItem> queue) {
        return (queue != null && index >= 0 && index < queue.size());
    }

    /**
     * Determine if two queues contain identical media id's in order.
     *
     * @param list1 containing {@link MediaSessionCompat.QueueItem}'s
     * @param list2 containing {@link MediaSessionCompat.QueueItem}'s
     * @return boolean indicating whether the queue's match
     */
    public static boolean equals(List<MediaSessionCompat.QueueItem> list1,
                                 List<MediaSessionCompat.QueueItem> list2) {
        if (list1 == list2) {
            return true;
        }
        if (list1 == null || list2 == null) {
            return false;
        }
        if (list1.size() != list2.size()) {
            return false;
        }
        for (int i=0; i<list1.size(); i++) {
            if (list1.get(i).getQueueId() != list2.get(i).getQueueId()) {
                return false;
            }
            if (!TextUtils.equals(list1.get(i).getDescription().getMediaId(),
                    list2.get(i).getDescription().getMediaId())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Determine if queue item matches the currently playing queue item
     *
     * @param context for retrieving the {@link MediaControllerCompat}
     * @param queueItem to compare to currently playing {@link MediaSessionCompat.QueueItem}
     * @return boolean indicating whether queue item matches currently playing queue item
     */
    public static boolean isQueueItemPlaying(Context context,
                                             MediaSessionCompat.QueueItem queueItem) {
        // Queue item is considered to be playing or paused based on both the controller's
        // current media id and the controller's active queue item id
        MediaControllerCompat controller = ((FragmentActivity) context).getSupportMediaController();
        if (controller != null && controller.getPlaybackState() != null) {
            long currentPlayingQueueId = controller.getPlaybackState().getActiveQueueItemId();
            String currentPlayingMediaId = controller.getMetadata().getDescription()
                    .getMediaId();
            String itemMusicId = MediaIDHelper.extractMusicIDFromMediaID(
                    queueItem.getDescription().getMediaId());
            if (queueItem.getQueueId() == currentPlayingQueueId
                    && currentPlayingMediaId != null
                    && TextUtils.equals(currentPlayingMediaId, itemMusicId)) {
                return true;
            }
        }
        return false;
    }
}
