
package com.antlersoft.patchyamp.bc;

import android.content.Context;

public interface IBcNotificationChannelCreator {
    public void createChannel(Context context, String id, CharSequence name, String description, int importance,
                              boolean showOnLock, boolean showBadge);
}
