package com.chocolate.hexen;

import org.libsdl.app.SDLActivity;
import android.util.Log;

public class ChocolateHexen extends SDLActivity {
    @Override
    protected String[] getLibraries() {
        return new String[] { "hexen" };
    }

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void setOrientationBis(int w, int h, boolean resizable, String hint) {
        Log.v("SDL", "setOrientationBis() SKIPPED");
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            SDLActivity.mNextNativeState = SDLActivity.NativeState.RESUMED;
            SDLActivity.handleNativeState();
        }
    }
}
