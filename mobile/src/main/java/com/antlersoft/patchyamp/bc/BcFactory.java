package com.antlersoft.patchyamp.bc;

public class BcFactory {
    private IBcNotificationChannelCreator mBcNotificationChannelCreator;

    private static BcFactory _theInstance = new BcFactory();

    /**
     * This is here so checking the static doesn't get optimized away;
     * note we can't use SDK_INT because that is too new
     * @return sdk version
     */
    private int getSdkVersion()
    {
        return android.os.Build.VERSION.SDK_INT;
    }

    public IBcNotificationChannelCreator getNotificationChannelCreator() {
        if (mBcNotificationChannelCreator == null) {
            synchronized (this) {
                if (mBcNotificationChannelCreator == null) {
                    if (getSdkVersion() >= 26) {
                        try {
                            mBcNotificationChannelCreator = (IBcNotificationChannelCreator) getClass().getClassLoader().loadClass("com.antlersoft.patchyamp.bc.NotificationChannelCreatorV26").newInstance();
                        } catch (Exception ie) {
                            mBcNotificationChannelCreator = new DefaultChannelCreator();
                            throw new RuntimeException("Error instantiating", ie);
                        }
                    } else {
                        mBcNotificationChannelCreator = new DefaultChannelCreator();
                    }
                }
            }
        }
        return mBcNotificationChannelCreator;
    }

    /**
     * Returns the only instance of this class, which manages the SDK specific interface
     * implementations
     * @return Factory instance
     */
    public static BcFactory getInstance()
    {
        return _theInstance;
    }
}
