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

package com.example.android.uamp.playback;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;

import com.example.android.uamp.AlbumArtCache;
import com.antlersoft.patchyamp.R;
import com.example.android.uamp.model.MusicProvider;
import com.example.android.uamp.utils.LogHelper;
import com.example.android.uamp.utils.MediaIDHelper;
import com.example.android.uamp.utils.QueueHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Simple data provider for queues. Keeps track of a current queue and a current index in the
 * queue. Also provides methods to set the current queue based on common queries, relying on a
 * given MusicProvider to provide the actual media metadata.
 */
public class QueueManager {
    private static final String TAG = LogHelper.makeLogTag(QueueManager.class);

    private MusicProvider mMusicProvider;
    private MetadataUpdateListener mListener;
    private Resources mResources;

    // "Now playing" queue:
    private List<MediaSessionCompat.QueueItem> mPlayingQueue;
    private int mCurrentIndex;

    public QueueManager(@NonNull MusicProvider musicProvider,
                        @NonNull Resources resources,
                        @NonNull MetadataUpdateListener listener) {
        this.mMusicProvider = musicProvider;
        this.mListener = listener;
        this.mResources = resources;

        mPlayingQueue = Collections.synchronizedList(new ArrayList<MediaSessionCompat.QueueItem>());
        mCurrentIndex = 0;
    }

    public boolean isSameBrowsingCategory(@NonNull String mediaId) {
        String[] newBrowseHierarchy = MediaIDHelper.getHierarchy(mediaId);
        MediaSessionCompat.QueueItem current = getCurrentMusic();
        if (current == null) {
            return false;
        }
        String[] currentBrowseHierarchy = MediaIDHelper.getHierarchy(
                current.getDescription().getMediaId());

        return Arrays.equals(newBrowseHierarchy, currentBrowseHierarchy);
    }

    public void setCurrentQueueIndex(int index) {
        if (index >= 0 && index < mPlayingQueue.size()) {
            mCurrentIndex = index;
            mListener.onCurrentQueueIndexUpdated(mCurrentIndex);
        }
    }

    public boolean setCurrentQueueItem(long queueId) {
        // set the current index on queue from the queue Id:
        int index = QueueHelper.getMusicIndexOnQueue(mPlayingQueue, queueId);
        setCurrentQueueIndex(index);
        return index >= 0;
    }

    public boolean setCurrentQueueItem(String mediaId) {
        // set the current index on queue from the music Id:
        int index = QueueHelper.getMusicIndexOnQueue(mPlayingQueue, mediaId);
        setCurrentQueueIndex(index);
        return index >= 0;
    }

    public boolean skipQueuePosition(int amount) {
        int index = mCurrentIndex + amount;
        if (index < 0) {
            // skip backwards before the first song will keep you on the first song
            index = 0;
        } else {
            // skip forwards when in last song will cycle back to start of the queue
            if (mPlayingQueue.size() == 0) {
                LogHelper.e(TAG, "Cannot skip to position in empty queue ", amount,
                        ". Current=", mCurrentIndex, " queue length=", mPlayingQueue.size());
                return false;
            }
            index %= mPlayingQueue.size();
        }
        if (!QueueHelper.isIndexPlayable(index, mPlayingQueue)) {
            LogHelper.e(TAG, "Cannot increment queue index by ", amount,
                    ". Current=", mCurrentIndex, " queue length=", mPlayingQueue.size());
            return false;
        }
        setCurrentQueueIndex(index);
        return true;
    }

    public void setQueueFromSearch(String query, Bundle extras, QueueHelper.QueryResult qr) {
        QueueHelper.getPlayingQueueFromSearch(query, extras, mMusicProvider, (queue) -> {
            setCurrentQueue(mResources.getString(R.string.search_queue_title), queue);
            updateMetadata();
            qr.SetResult(queue);
        });
    }

    public void setRandomQueue(Runnable toContinue) {
        QueueHelper.getRandomQueue(mMusicProvider, (queue) -> {
            setCurrentQueue(mResources.getString(R.string.random_queue_title),
                    queue);
            updateMetadata();
            toContinue.run();
        });
    }

    public void setQueueFromMusic(final String mediaId, final Runnable toContinue) {
        LogHelper.d(TAG, "setQueueFromMusic", mediaId);

        // The mediaId used here is not the unique musicId. This one comes from the
        // MediaBrowser, and is actually a "hierarchy-aware mediaID": a concatenation of
        // the hierarchy in MediaBrowser and the actual unique musicID. This is necessary
        // so we can build the correct playing queue, based on where the track was
        // selected from.
        boolean canReuseQueue = false;
        if (isSameBrowsingCategory(mediaId)) {
            canReuseQueue = setCurrentQueueItem(mediaId);
            updateMetadata();
        }
        if (!canReuseQueue) {
            String queueTitle = mResources.getString(R.string.browse_musics_by_genre_subtitle,
                    MediaIDHelper.extractBrowseCategoryValueFromMediaID(mediaId));
            QueueHelper.getPlayingQueue(mediaId, mMusicProvider, (queue) -> {
                setCurrentQueue(queueTitle, queue, mediaId);
                updateMetadata();
                toContinue.run();
            });
        } else {
            toContinue.run();
        }
    }

    public MediaSessionCompat.QueueItem getCurrentMusic() {
        if (!QueueHelper.isIndexPlayable(mCurrentIndex, mPlayingQueue)) {
            return null;
        }
        return mPlayingQueue.get(mCurrentIndex);
    }

    public int getCurrentQueueSize() {
        if (mPlayingQueue == null) {
            return 0;
        }
        return mPlayingQueue.size();
    }

    protected void setCurrentQueue(String title, List<MediaSessionCompat.QueueItem> newQueue) {
        setCurrentQueue(title, newQueue, null);
    }

    protected void setCurrentQueue(String title, List<MediaSessionCompat.QueueItem> newQueue,
                                   String initialMediaId) {
        mPlayingQueue = newQueue;
        int index = 0;
        if (initialMediaId != null) {
            index = QueueHelper.getMusicIndexOnQueue(mPlayingQueue, initialMediaId);
        }
        mCurrentIndex = Math.max(index, 0);
        mListener.onQueueUpdated(title, newQueue);
        if (initialMediaId != null && ! MediaIDHelper.isBrowseable(initialMediaId) && MediaIDHelper.isPlaylist(MediaIDHelper.getParentMediaID(initialMediaId))) {
            setCurrentQueueIndex(mCurrentIndex);
        }
    }

    public void updateMetadata() {
        MediaSessionCompat.QueueItem currentMusic = getCurrentMusic();
        if (currentMusic == null) {
            mListener.onMetadataRetrieveError();
            return;
        }
        final String musicId = MediaIDHelper.extractMusicIDFromMediaID(
                currentMusic.getDescription().getMediaId());
        mMusicProvider.getMusic(musicId, (metadata) -> {
            if (metadata == null) {
                throw new IllegalArgumentException("Invalid musicId " + musicId);
            }

            mListener.onMetadataChanged(metadata);

            // Set the proper album artwork on the media session, so it can be shown in the
            // locked screen and in other places.
            if (metadata.getDescription().getIconBitmap() == null &&
                    metadata.getDescription().getIconUri() != null) {
                String albumUri = metadata.getDescription().getIconUri().toString();
                AlbumArtCache.getInstance().fetch(albumUri, new AlbumArtCache.FetchListener() {
                    @Override
                    public void onFetched(String artUrl, Bitmap bitmap, Bitmap icon) {
                        mMusicProvider.updateMusicArt(musicId, bitmap, icon);

                        mMusicProvider.getMusic(musicId, (metadata)-> {
                            // If we are still playing the same music, notify the listeners:
                            MediaSessionCompat.QueueItem currentMusic = getCurrentMusic();
                            if (currentMusic == null) {
                                return;
                            }
                            String currentPlayingId = MediaIDHelper.extractMusicIDFromMediaID(
                                    currentMusic.getDescription().getMediaId());
                            if (musicId.equals(currentPlayingId)) {
                                mListener.onMetadataChanged(metadata);
                            }
                        });
                    }
                });
            }
        });
    }

    public interface MetadataUpdateListener {
        void onMetadataChanged(MediaMetadataCompat metadata);
        void onMetadataRetrieveError();
        void onCurrentQueueIndexUpdated(int queueIndex);
        void onQueueUpdated(String title, List<MediaSessionCompat.QueueItem> newQueue);
    }
}
