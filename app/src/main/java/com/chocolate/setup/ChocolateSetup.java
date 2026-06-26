package com.chocolate.setup;

import org.libsdl.app.SDLActivity;
import android.util.Log;

public class ChocolateSetup extends SDLActivity {
    @Override
    protected String[] getLibraries() {
        return new String[] { "setup" };
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            SDLActivity.mNextNativeState = SDLActivity.NativeState.RESUMED;
            SDLActivity.handleNativeState();
        }
    }
    
    @Override
    public void setOrientationBis(int w, int h, boolean resizable, String hint) {
        Log.v("SDL", "setOrientationBis() SKIPPED for setup");
    }
}
