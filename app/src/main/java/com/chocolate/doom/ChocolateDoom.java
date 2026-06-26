package com.chocolate.doom;

import org.libsdl.app.SDLActivity;
import android.util.Log;
import android.view.ViewGroup;

public class ChocolateDoom extends SDLActivity {
    private TouchControls touchControls;

    @Override
    protected String[] getLibraries() {
        return new String[] {
            "main"
        };
    }

    @Override
    protected String[] getArguments() {
        // Use internal app storage (accessible from native code)
        // WAD file must be at: /data/data/com.chocolate.doom/files/doom.wad
        return new String[] {
            "-iwad",
            getFilesDir().getAbsolutePath() + "/doom.wad"
        };
    }

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Add touch controls after SDL creates the layout
        new android.os.Handler().postDelayed(() -> {
            if (mLayout != null) {
                touchControls = new TouchControls(this);
                mLayout.addView(touchControls, new android.widget.RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ));
                touchControls.bringToFront();
                Log.v("ChocolateDoom", "Touch controls added to layout");
            } else {
                Log.w("ChocolateDoom", "mLayout is null, retrying...");
                // Retry once more
                new android.os.Handler().postDelayed(() -> {
                    if (mLayout != null) {
                        touchControls = new TouchControls(ChocolateDoom.this);
                        mLayout.addView(touchControls);
                        touchControls.bringToFront();
                        Log.v("ChocolateDoom", "Touch controls added (retry)");
                    }
                }, 500);
            }
        }, 500);
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
        Log.v("SDL", "setOrientationBis() SKIPPED to avoid surface destroy");
    }
}
