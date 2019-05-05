package com.example.android.uamp.model;

import android.support.v4.media.MediaBrowserServiceCompat;
/**
 * Created by mike on 1/1/18.
 *
 * This class exists to wrap MediaBrowserServiceCompat.Result, solely because that class does not have
 * a public constructor
 *
 * Rest is copied from wrapped class:
 *
 * Completion handler for asynchronous callback methods in {@link MediaBrowserServiceCompat}.
 * <p>
 * Each of the methods that takes one of these to send the result must call
 * {@link #sendResult} to respond to the caller with the given results. If those
 * functions return without calling {@link #sendResult}, they must instead call
 * {@link #detach} before returning, and then may call {@link #sendResult} when
 * they are done. If more than one of those methods is called, an exception will
 * be thrown.
 *
 * @see MediaBrowserServiceCompat#onLoadChildren
 * @see MediaBrowserServiceCompat#onLoadItem
 */
public class ResultWrapper<T> {
    private MediaBrowserServiceCompat.Result<T> mInnerResult;
    private boolean mDetachCalled;
    private boolean mSendResultCalled;

    public ResultWrapper(MediaBrowserServiceCompat.Result<T> inner) {
        mInnerResult = inner;
    }

    /**
     * Send the result back to the caller.
     */
    public void sendResult(T result) {
        if (mInnerResult != null)
            mInnerResult.sendResult(result);
        else {
            if (mSendResultCalled) {
                throw new IllegalStateException("sendResult() called twice for: in ResultWrapper");
            }
            mSendResultCalled = true;
            onSendResult(result);
        }
    }

    /**
     * Detach this message from the current thread and allow the {@link #sendResult}
     * call to happen later.
     */
    public void detach() {
        if (mInnerResult != null) {
            mInnerResult.detach();
        }
        else {
            if (mDetachCalled) {
                throw new IllegalStateException("detach() called when detach() had already"
                        + " been called for in ResultWrapper");
            }
            if (mSendResultCalled) {
                throw new IllegalStateException("detach() called when sendResult() had already"
                        + " been called for in ResultWrapper");
            }
            mDetachCalled = true;
        }
    }

    /**
     * If not using wrapped Result, must override this
     * @param Result
     */
    public void onSendResult(T Result) {

    }
}
