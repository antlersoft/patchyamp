package com.antlersoft.patchyamp.bc;

import android.content.Context;

class DefaultChannelCreator implements IBcNotificationChannelCreator {
    /**
     * Default implementation does nothing
     * @param context
     * @param id
     * @param name
     * @param description
     * @param importance
     * @param showOnLock
     * @param showBadge
     */
    @Override
    public void createChannel(Context context, String id, CharSequence name, String description, int importance, boolean showOnLock, boolean showBadge) {

    }
}
