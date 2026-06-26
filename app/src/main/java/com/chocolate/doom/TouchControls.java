package com.chocolate.doom;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
import org.libsdl.app.SDLActivity;
import android.os.Handler;

/**
 * Virtual touch controls overlay for Chocolate Doom on Android.
 * Left half: floating analog joystick for movement (8-direction).
 * Right half: action buttons (fire, use, run, map, strafe, enter, menu, Y).
 *
 * Cheats: Triple-tap ☰ (Menu) within 1 second → God Mode (IDDQD + IDKFA).
 */
public class TouchControls extends View {
    private final Paint paint;
    private final Paint textPaint;
    private int w, h;

    // Analog joystick for movement (replaces old D-pad)
    private final AnalogJoystick joystick;

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

    // Cheat system — triple-tap Menu to activate god mode
    private final long[] menuTapTimes = new long[3];
    private int menuTapIndex = 0;
    private boolean godModeActive = false;
    private String overlayText = null;
    private long overlayUntil = 0;
    private final Handler handler = new Handler();

    // Android keycode mapping for cheat characters a-z
    private static final int[] CHAR_KEYCODES = {
        29,30,31,32,33,34,35,36,37,38,39,40,41,  // a-m
        42,43,44,45,46,47,48,49,50,51,52,53,54   // n-z
    };

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

        setOnTouchListener((v, event) -> handleTouch(event));

        // Create analog joystick (left half = movement zone)
        joystick = new AnalogJoystick(90f, 20f); // maxRadius=90px, deadZone=20px

        btnFire    = newButton("\uD83D\uDD2B", 113);  // CTRL_LEFT = fire
        btnUse     = newButton("\uD83D\uDEAA", 62);   // SPACE = use/open
        btnRun     = newButton("\uD83C\uDFC3", 59);   // SHIFT_LEFT = run
        btnMap     = newButton("\uD83D\uDDFA\uFE0F", 61);   // TAB = map
        btnStrafeR = newButton("\u27A1", 22);   // DPAD_RIGHT for strafe
        btnEnter   = newButton("\u21B5", 66);   // ENTER = select/confirm
        btnMenu    = newButton("\u2630", 111);  // ESCAPE = menu
        btnYes     = newButton("Y",    53);   // KEYCODE_Y = confirm/yes/quit

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

        // Action buttons: right side, arranged in rows
        float rightEdge = w - 30;
        float bottomY = h - 30;

        // Fire (large, right side upper)
        btnFire.cx = rightEdge - 110;
        btnFire.cy = bottomY - 400;
        btnFire.radius = 100f;

        // Use (below fire)
        btnUse.cx = rightEdge - 110;
        btnUse.cy = bottomY - 200;

        // Strafe right & Enter (left of fire area)
        btnStrafeR.cx = rightEdge - 300;
        btnStrafeR.cy = bottomY - 200;
        btnEnter.cx = rightEdge - 300;
        btnEnter.cy = bottomY - 70;

        // Run (right edge, lower)
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
        // Draw analog joystick (only when active)
        joystick.draw(canvas);

        // Draw hint text on left side when joystick is inactive
        if (!joystick.isActive()) {
            textPaint.setTextSize(36f);
            textPaint.setColor(Color.argb(80, 255, 255, 255));
            canvas.drawText("◄ touch to move ►", w / 4, h / 2, textPaint);
            textPaint.setTextSize(44f); // restore
            textPaint.setColor(Color.WHITE); // restore
        }

        // Action buttons
        for (ActionButton btn : allButtons) {
            drawActionButton(canvas, btn);
        }

        // Overlay text (god mode notification etc.)
        if (overlayText != null && System.currentTimeMillis() < overlayUntil) {
            float alpha = Math.min(1.0f, (overlayUntil - System.currentTimeMillis()) / 500f);
            textPaint.setTextSize(60f);
            textPaint.setColor(Color.argb((int)(255 * alpha), 255, 220, 50));
            textPaint.setFakeBoldText(true);
            paint.setColor(Color.argb((int)(128 * alpha), 0, 0, 0));
            canvas.drawRect(0, h/2 - 50, w, h/2 + 50, paint);
            canvas.drawText(overlayText, w/2, h/2 + 20, textPaint);
            textPaint.setFakeBoldText(false);
            textPaint.setTextSize(44f); // restore
            textPaint.setColor(Color.WHITE);
        }
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
                // Priority 1: Try analog joystick (left half of screen)
                if (joystick.onTouchDown(x, y, pointerId, w)) {
                    break;
                }

                // Priority 2: Check action buttons
                for (ActionButton btn : allButtons) {
                    if (!btn.pressed && isInCircle(x, y, btn.cx, btn.cy, btn.radius + 30)) {
                        btn.pressed = true;
                        btn.pointerId = pointerId;

                        // Y button: special handling for proper KEYDOWN event
                        if (btn == btnYes) {
                            SDLActivity.onNativeKeyDown(btn.keyCode);
                            final ActionButton fb = btn;
                            handler.postDelayed(() -> {
                                if (fb.pressed) {
                                    fb.pressed = false;
                                    fb.pointerId = -1;
                                    SDLActivity.onNativeKeyUp(fb.keyCode);
                                }
                            }, 80);
                        } else if (btn == btnMenu) {
                            // Menu button: normal press + triple-tap detection
                            SDLActivity.onNativeKeyDown(btn.keyCode);
                            onMenuTapped();
                            final ActionButton fb = btn;
                            handler.postDelayed(() -> {
                                if (fb.pressed) {
                                    fb.pressed = false;
                                    fb.pointerId = -1;
                                    SDLActivity.onNativeKeyUp(fb.keyCode);
                                }
                            }, 80);
                        } else {
                            SDLActivity.onNativeKeyDown(btn.keyCode);
                            // Tap-type buttons (all except Run) auto-release after delay
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
                // Update joystick for the active pointer
                for (int i = 0; i < event.getPointerCount(); i++) {
                    int pid = event.getPointerId(i);
                    float px = event.getX(i);
                    float py = event.getY(i);

                    if (joystick.containsPointer(pid)) {
                        joystick.onTouchMove(px, py);
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                // Release joystick if this pointer owns it
                joystick.onTouchUp(pointerId);

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

    private boolean isInCircle(float px, float py, float cx, float cy, float r) {
        float dx = px - cx;
        float dy = py - cy;
        return dx * dx + dy * dy <= r * r;
    }

    // ────────────────────────────────────────
    //  Cheat System — Triple-Tap God Mode
    // ────────────────────────────────────────

    /**
     * Called each time the Menu button is tapped.
     * Tracks last 3 taps; if all within 1000ms → activate God Mode.
     */
    private void onMenuTapped() {
        long now = System.currentTimeMillis();
        menuTapTimes[menuTapIndex % 3] = now;
        menuTapIndex++;

        if (menuTapIndex >= 3) {
            long span = menuTapTimes[(menuTapIndex - 1) % 3] - menuTapTimes[(menuTapIndex - 3) % 3];
            if (span < 1000 && span >= 0) {
                activateGodMode();
                menuTapIndex = 0; // reset
            }
        }
    }

    /**
     * Activate God Mode: inject IDDQD (invulnerability) + IDKFA (all weapons/ammo/keys).
     * Shows overlay text for 2 seconds.
     */
    private void activateGodMode() {
        if (godModeActive) return;
        godModeActive = true;

        // Inject cheat sequence: iddqdidkfa
        injectCheatSequence("iddqdidkfa");

        // Visual feedback
        showOverlay("⚡ GOD MODE ⚡", 2000);

        // Reset god mode flag after sequence completes (allow re-activation)
        handler.postDelayed(() -> { godModeActive = false; }, 2500);
    }

    /**
     * Inject a cheat code character sequence via keyboard events.
     * Each character is sent with 60ms delay, held for 50ms.
     *
     * @param sequence lowercase letters only (e.g. "iddqd", "idkfa")
     */
    private void injectCheatSequence(String sequence) {
        for (int i = 0; i < sequence.length(); i++) {
            final char c = sequence.charAt(i);
            final int keyCode = charToKeyCode(c);
            if (keyCode < 0) continue;

            final long delay = i * 65L;
            handler.postDelayed(() -> {
                SDLActivity.onNativeKeyDown(keyCode);
                handler.postDelayed(() -> {
                    SDLActivity.onNativeKeyUp(keyCode);
                }, 50);
            }, delay);
        }
    }

    /**
     * Show overlay text centered on screen, fading out.
     * @param text message to display
     * @param durationMs how long to show (milliseconds)
     */
    private void showOverlay(String text, long durationMs) {
        overlayText = text;
        overlayUntil = System.currentTimeMillis() + durationMs;
        invalidate();
    }

    /**
     * Map lowercase letter 'a'-'z' to Android keycode.
     * @return Android KEYCODE_* value, or -1 if not a letter
     */
    private static int charToKeyCode(char c) {
        if (c >= 'a' && c <= 'z') return CHAR_KEYCODES[c - 'a'];
        if (c >= 'A' && c <= 'Z') return CHAR_KEYCODES[c - 'A'];
        return -1;
    }
}
