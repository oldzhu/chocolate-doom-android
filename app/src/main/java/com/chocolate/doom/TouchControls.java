package com.chocolate.doom;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import org.libsdl.app.SDLActivity;
import android.os.Handler;

/**
 * Virtual touch controls overlay for Chocolate Doom on Android.
 * D-pad on left, action buttons on right.
 */
public class TouchControls extends View {
    private final Paint paint;
    private final Paint textPaint;
    private final Paint bgPaint;
    private int w, h;

    // D-pad state
    private boolean dpadUp, dpadDown, dpadLeft, dpadRight;
    private float dpadCenterX, dpadCenterY;
    private static final float DPAD_RADIUS = 140f;

    // Action buttons
    private static class ActionButton {
        float cx, cy, radius;
        String label;
        int keyCode;
        boolean pressed;
        int pointerId = -1;
    }
    private ActionButton btnFire, btnUse, btnRun, btnMap, btnStrafeR, btnEnter, btnMenu, btnYes;
    private ActionButton[] allButtons;

    // D-pad pointer tracking
    private int dpadPointerId = -1;

    public TouchControls(Context context) {
        super(context);
        setAlpha(0.5f);

        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);

        textPaint = new Paint();
        textPaint.setAntiAlias(true);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(30f);
        textPaint.setTextAlign(Paint.Align.CENTER);

        bgPaint = new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(Color.argb(40, 255, 255, 255));

        setOnTouchListener((v, event) -> handleTouch(event));

        btnFire   = newButton("\uD83D\uDD2B", 113);  // CTRL_LEFT = fire
        btnUse    = newButton("\uD83D\uDEAA", 62);   // SPACE = use/open
        btnRun    = newButton("\uD83C\uDFC3", 59);   // SHIFT_LEFT = run
        btnMap    = newButton("\uD83D\uDDFA\uFE0F", 61);   // TAB = map
        btnStrafeR = newButton("\u27A1", 22);  // DPAD_RIGHT for strafe
        btnEnter  = newButton("\u21B5", 66);   // ENTER = select/confirm
        btnMenu   = newButton("\u2630", 111);  // ESCAPE = menu
        btnYes    = newButton("Y",   53);   // KEYCODE_Y = confirm/yes/quit

        allButtons = new ActionButton[]{btnFire, btnUse, btnRun, btnMap, btnStrafeR, btnEnter, btnMenu, btnYes};
    }

    private ActionButton newButton(String label, int keyCode) {
        ActionButton b = new ActionButton();
        b.label = label;
        b.keyCode = keyCode;
        b.radius = 65f;
        return b;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        this.w = w;
        this.h = h;

        // D-pad: bottom-left, larger
        dpadCenterX = DPAD_RADIUS + 30;
        dpadCenterY = h - DPAD_RADIUS - 30;

        // Action buttons: bottom-right, arranged in rows
        float rightEdge = w - 30;
        float bottomY = h - 30;

        // Fire (large, center-right)
        btnFire.cx = rightEdge - 110;
        btnFire.cy = bottomY - 400;
        btnFire.radius = 100f;

        // Use (below fire)
        btnUse.cx = rightEdge - 110;
        btnUse.cy = bottomY - 200;

        // Strafe right & Enter (left of fire)
        btnStrafeR.cx = rightEdge - 300;
        btnStrafeR.cy = bottomY - 200;
        btnEnter.cx = rightEdge - 300;
        btnEnter.cy = bottomY - 70;

        // Run (right edge, mid)
        btnRun.cx = rightEdge - 110;
        btnRun.cy = bottomY - 70;

        // Map (above fire)
        btnMap.cx = rightEdge - 260;
        btnMap.cy = bottomY - 400;
        btnMap.radius = 65f;

        // Menu (top-right)
        btnMenu.cx = rightEdge - 100;
        btnMenu.cy = 120;
        btnMenu.radius = 55f;

        // Yes/Confirm (next to menu)
        btnYes.cx = rightEdge - 200;
        btnYes.cy = 120;
        btnYes.radius = 55f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // D-pad background
        paint.setColor(Color.argb(60, 200, 200, 200));
        canvas.drawCircle(dpadCenterX, dpadCenterY, DPAD_RADIUS + 10, paint);

        // D-pad
        drawDpadButton(canvas, dpadCenterX, dpadCenterY - DPAD_RADIUS * 0.55f, dpadUp, "\u25B2");
        drawDpadButton(canvas, dpadCenterX, dpadCenterY + DPAD_RADIUS * 0.55f, dpadDown, "\u25BC");
        drawDpadButton(canvas, dpadCenterX - DPAD_RADIUS * 0.55f, dpadCenterY, dpadLeft, "\u25C4");
        drawDpadButton(canvas, dpadCenterX + DPAD_RADIUS * 0.55f, dpadCenterY, dpadRight, "\u25BA");

        // Action buttons
        for (ActionButton btn : allButtons) {
            drawActionButton(canvas, btn);
        }
    }

    private void drawDpadButton(Canvas canvas, float x, float y, boolean pressed, String arrow) {
        paint.setColor(pressed ? Color.argb(180, 255, 255, 255) : Color.argb(100, 255, 255, 255));
        canvas.drawCircle(x, y, 50, paint);
        paint.setColor(pressed ? Color.BLACK : Color.WHITE);
        textPaint.setColor(pressed ? Color.BLACK : Color.WHITE);
        textPaint.setTextSize(56f);
        canvas.drawText(arrow, x, y + 18, textPaint);
    }

    private void drawActionButton(Canvas canvas, ActionButton btn) {
        paint.setColor(btn.pressed ? Color.argb(180, 255, 200, 100) : Color.argb(100, 200, 200, 200));
        canvas.drawCircle(btn.cx, btn.cy, btn.radius, paint);
        textPaint.setTextSize(44f);
        canvas.drawText(btn.label, btn.cx, btn.cy + 15, textPaint);
    }

    private boolean handleTouch(MotionEvent event) {
        int action = event.getActionMasked();
        int pointerIndex = event.getActionIndex();
        int pointerId = event.getPointerId(pointerIndex);
        float x = event.getX(pointerIndex);
        float y = event.getY(pointerIndex);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                // Check D-pad
                if (isInCircle(x, y, dpadCenterX, dpadCenterY, DPAD_RADIUS + 50)) {
                    dpadPointerId = pointerId;
                    updateDpad(x, y);
                }
                // Check action buttons
                for (ActionButton btn : allButtons) {
                    if (!btn.pressed && isInCircle(x, y, btn.cx, btn.cy, btn.radius + 30)) {
                        btn.pressed = true;
                        btn.pointerId = pointerId;
                        // Y button: use onNativeKeyDown for proper KEYDOWN event (DOOM checks ev_keydown)
                        if (btn == btnYes) {
                            SDLActivity.onNativeKeyDown(btn.keyCode);
                            final ActionButton fb = btn;
                            new Handler().postDelayed(() -> {
                                if (fb.pressed) {
                                    fb.pressed = false;
                                    fb.pointerId = -1;
                                    SDLActivity.onNativeKeyUp(fb.keyCode);
                                }
                            }, 80);
                        } else {
                            SDLActivity.onNativeKeyDown(btn.keyCode);
                            // For tap-type buttons (all except Run), auto-release after delay
                            if (btn != btnRun) {
                                final ActionButton fb = btn;
                                new Handler().postDelayed(() -> {
                                    if (fb.pressed) {
                                        fb.pressed = false;
                                        fb.pointerId = -1;
                                        SDLActivity.onNativeKeyUp(fb.keyCode);
                                    }
                                }, 80);
                            }
                        }
                    }
                }
                break;

            case MotionEvent.ACTION_MOVE:
                // Update D-pad
                for (int i = 0; i < event.getPointerCount(); i++) {
                    int pid = event.getPointerId(i);
                    float px = event.getX(i);
                    float py = event.getY(i);
                    if (pid == dpadPointerId) {
                        updateDpad(px, py);
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                // Release D-pad
                if (pointerId == dpadPointerId) {
                    releaseDpad();
                    dpadPointerId = -1;
                }
                // Release any held button for this pointer
                for (ActionButton btn : allButtons) {
                    if (btn.pressed && btn.pointerId == pointerId) {
                        btn.pressed = false;
                        btn.pointerId = -1;
                        SDLActivity.onNativeKeyUp(btn.keyCode);
                    }
                }
                break;
        }

        invalidate();
        return true;
    }

    private void updateDpad(float x, float y) {
        float dx = x - dpadCenterX;
        float dy = y - dpadCenterY;
        float dist = (float)Math.sqrt(dx * dx + dy * dy);
        float threshold = 40f;

        boolean newUp = dy < -threshold;
        boolean newDown = dy > threshold;
        boolean newLeft = dx < -threshold;
        boolean newRight = dx > threshold;

        updateDpadKey(dpadUp, newUp, 19);    // DPAD_UP
        updateDpadKey(dpadDown, newDown, 20); // DPAD_DOWN
        updateDpadKey(dpadLeft, newLeft, 21); // DPAD_LEFT
        updateDpadKey(dpadRight, newRight, 22); // DPAD_RIGHT

        dpadUp = newUp;
        dpadDown = newDown;
        dpadLeft = newLeft;
        dpadRight = newRight;
    }

    private void updateDpadKey(boolean oldState, boolean newState, int keyCode) {
        if (newState && !oldState) {
            SDLActivity.onNativeKeyDown(keyCode);
        } else if (!newState && oldState) {
            SDLActivity.onNativeKeyUp(keyCode);
        }
    }

    private void releaseDpad() {
        updateDpadKey(dpadUp, false, 19);
        updateDpadKey(dpadDown, false, 20);
        updateDpadKey(dpadLeft, false, 21);
        updateDpadKey(dpadRight, false, 22);
        dpadUp = dpadDown = dpadLeft = dpadRight = false;
    }

    private boolean isInCircle(float px, float py, float cx, float cy, float r) {
        float dx = px - cx;
        float dy = py - cy;
        return dx * dx + dy * dy <= r * r;
    }
}
