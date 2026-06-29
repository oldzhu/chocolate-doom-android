package com.chocolate.doom;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
import org.libsdl.app.SDLActivity;
import android.os.Handler;

/**
 * Virtual touch controls overlay for Chocolate Doom on Android.
 *
 * Left half:  floating analog joystick for movement (8-direction).
 * Right half: action buttons (run, map, strafe, menu, Y, weapon cycle).
 * Game area:  tap → context-aware action (fire + use + select).
 *
 * Cheats: Triple-tap ☰ (Menu) within 1 second → God Mode (IDDQD + IDKFA).
 */
public class TouchControls extends View {
    private final Paint paint;
    private final Paint textPaint;
    private int w, h;

    // Analog joystick for movement
    private final AnalogJoystick joystick;

    // Action buttons
    private static class ActionButton {
        float cx, cy, radius;
        String label;
        int keyCode;
        boolean pressed;
        int pointerId = -1;
    }
    private ActionButton btnRun, btnMap, btnStrafeL, btnStrafeR, btnMenu, btnYes;
    private ActionButton btnWeapPrev, btnWeapNext;
    private ActionButton[] allButtons;

    // ── Game-area tap detection ──
    // When a pointer goes down on the game area (not claimed by joystick/buttons),
    // we record its start position/time. On up, if it was short + stationary → tap.
    private static final int MAX_POINTERS = 5;
    private final float[] tapStartX = new float[MAX_POINTERS];
    private final float[] tapStartY = new float[MAX_POINTERS];
    private final long[]  tapStartTime = new long[MAX_POINTERS];
    private final boolean[] tapClaimed = new boolean[MAX_POINTERS];
    private static final float TAP_MOVE_THRESHOLD = 25f;  // px — max movement for tap
    private static final long  TAP_TIME_THRESHOLD  = 250;  // ms — max duration for tap

    // Cheat system — triple-tap Menu to activate god mode
    private final long[] menuTapTimes = new long[3];
    private int menuTapIndex = 0;
    private boolean godModeActive = false;
    private String overlayText = null;
    private long overlayUntil = 0;
    private final Handler handler = new Handler();

    // Weapon cycle — track current slot (1=fist, 2=pistol, ..., 7=BFG)
    private int currentWeaponSlot = 2;

    // Ammo refill — periodic IDFA injection during god mode
    private boolean ammoRefillActive = false;

    // Android keycode mapping for cheat characters a-z
    private static final int[] CHAR_KEYCODES = {
        29,30,31,32,33,34,35,36,37,38,39,40,41,  // a-m
        42,43,44,45,46,47,48,49,50,51,52,53,54   // n-z
    };

    // Context-aware tap keycodes: Ctrl (fire) + Space (use/open) + Enter (select)
    private static final int[] CONTEXT_TAP_KEYS = {113, 62, 66};  // CTRL, SPACE, ENTER

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
        joystick = new AnalogJoystick(90f, 20f);

        // ── Buttons (fire/use/enter removed — replaced by context-aware tap) ──
        btnRun     = newButton("\uD83C\uDFC3", 59);   // SHIFT_LEFT = run
        btnMap     = newButton("\uD83D\uDDFA\uFE0F", 61);   // TAB = map
        btnStrafeL = newButton("\u25C0", 21);   // DPAD_LEFT = strafe left
        btnStrafeR = newButton("\u25B6", 22);   // DPAD_RIGHT = strafe right
        btnMenu    = newButton("\u2630", 111);  // ESCAPE = menu
        btnYes     = newButton("Y",    53);     // KEYCODE_Y = confirm/yes/quit
        btnWeapPrev = newButton("\u25C0\u25C0", 8);
        btnWeapNext = newButton("\u25B6\u25B6", 9);

        allButtons = new ActionButton[]{btnRun, btnMap, btnStrafeL, btnStrafeR, btnMenu, btnYes, btnWeapPrev, btnWeapNext};
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

        float rightEdge = w - 30;
        float bottomY = h - 30;

        // ── Right-column layout (top → bottom, minimum 10px gap between buttons) ──
        // Row 1: Menu + Y (r=50, gap=20px between edges)
        btnMenu.cx = rightEdge - 80;
        btnMenu.cy = 120;
        btnMenu.radius = 50f;

        btnYes.cx = rightEdge - 200;
        btnYes.cy = 120;
        btnYes.radius = 50f;

        // Row 2: Map (r=60, 10px below Y)
        btnMap.cx = rightEdge - 140;
        btnMap.cy = 240;
        btnMap.radius = 60f;

        // Row 3: Weapon cycle (r=40, 10px below Map)
        btnWeapPrev.cx = rightEdge - 200;
        btnWeapPrev.cy = 350;
        btnWeapNext.cx = rightEdge - 60;
        btnWeapNext.cy = 350;
        btnWeapPrev.radius = 40f;
        btnWeapNext.radius = 40f;

        // Row 4: Strafe L/R + Run (bottom, 10px+ gaps)
        btnStrafeL.cx = rightEdge - 340;
        btnStrafeL.cy = bottomY - 80;
        btnStrafeL.radius = 45f;
        btnStrafeR.cx = rightEdge - 230;
        btnStrafeR.cy = bottomY - 80;
        btnStrafeR.radius = 45f;
        btnRun.cx = rightEdge - 110;
        btnRun.cy = bottomY - 80;
        btnRun.radius = 60f;
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
            canvas.drawText("tap to fire / use", w * 3 / 4, h * 3 / 4, textPaint);
            textPaint.setTextSize(44f);
            textPaint.setColor(Color.WHITE);
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
            textPaint.setTextSize(44f);
            textPaint.setColor(Color.WHITE);
        }
    }

    private void drawActionButton(Canvas canvas, ActionButton btn) {
        paint.setColor(btn.pressed ? Color.argb(180, 255, 200, 100) : Color.argb(100, 200, 200, 200));
        canvas.drawCircle(btn.cx, btn.cy, btn.radius, paint);
        textPaint.setTextSize(44f);
        canvas.drawText(btn.label, btn.cx, btn.cy + 15, textPaint);
    }

    // ─────────────────────────────────────────────────────────
    //  Touch handling
    // ─────────────────────────────────────────────────────────

    private boolean handleTouch(MotionEvent event) {
        int action = event.getActionMasked();
        int pointerIndex = event.getActionIndex();
        int pointerId = event.getPointerId(pointerIndex);
        float x = event.getX(pointerIndex);
        float y = event.getY(pointerIndex);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                // Record tap start position (for game-area tap detection)
                if (pointerId < MAX_POINTERS) {
                    tapStartX[pointerId] = x;
                    tapStartY[pointerId] = y;
                    tapStartTime[pointerId] = System.currentTimeMillis();
                    tapClaimed[pointerId] = false;
                }

                // Priority 1: Try analog joystick (left half of screen)
                if (joystick.onTouchDown(x, y, pointerId, w)) {
                    if (pointerId < MAX_POINTERS) tapClaimed[pointerId] = true;
                    break;
                }

                // Priority 2: Check action buttons
                for (ActionButton btn : allButtons) {
                    if (!btn.pressed && isInCircle(x, y, btn.cx, btn.cy, btn.radius + 30)) {
                        btn.pressed = true;
                        btn.pointerId = pointerId;
                        if (pointerId < MAX_POINTERS) tapClaimed[pointerId] = true;

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
                        } else if (btn == btnWeapPrev || btn == btnWeapNext) {
                            if (btn == btnWeapPrev) {
                                currentWeaponSlot = (currentWeaponSlot == 1) ? 7 : currentWeaponSlot - 1;
                            } else {
                                currentWeaponSlot = (currentWeaponSlot == 7) ? 1 : currentWeaponSlot + 1;
                            }
                            int slotKeyCode = 8 + currentWeaponSlot - 1;
                            SDLActivity.onNativeKeyDown(slotKeyCode);
                            final int finalKeyCode = slotKeyCode;
                            handler.postDelayed(() -> {
                                SDLActivity.onNativeKeyUp(finalKeyCode);
                                btn.pressed = false;
                                btn.pointerId = -1;
                            }, 80);
                        } else {
                            SDLActivity.onNativeKeyDown(btn.keyCode);
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

                // ── Game-area tap detection ──
                if (pointerId < MAX_POINTERS && !tapClaimed[pointerId]) {
                    long duration = System.currentTimeMillis() - tapStartTime[pointerId];
                    float dx = x - tapStartX[pointerId];
                    float dy = y - tapStartY[pointerId];
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);

                    if (duration < TAP_TIME_THRESHOLD && dist < TAP_MOVE_THRESHOLD) {
                        // Confirmed tap on game area → context-aware action
                        onGameTap();
                    }
                }
                // Clean up
                if (pointerId < MAX_POINTERS) {
                    tapClaimed[pointerId] = false;
                }
                break;
        }

        invalidate();
        return true;
    }

    /**
     * Context-aware tap on the game area.
     * Injects Ctrl (fire) + Space (use/open) + Enter (menu select)
     * simultaneously. The game engine only responds to the key
     * that's valid in the current context:
     *   - Facing enemy  → fires weapon
     *   - Facing door   → opens door
     *   - In menu       → selects menu item
     */
    private void onGameTap() {
        // Press all three keys
        for (int keyCode : CONTEXT_TAP_KEYS) {
            SDLActivity.onNativeKeyDown(keyCode);
        }
        // Release all three after short delay
        handler.postDelayed(() -> {
            for (int keyCode : CONTEXT_TAP_KEYS) {
                SDLActivity.onNativeKeyUp(keyCode);
            }
        }, 80);
    }

    private boolean isInCircle(float px, float py, float cx, float cy, float r) {
        float dx = px - cx;
        float dy = py - cy;
        return dx * dx + dy * dy <= r * r;
    }

    // ────────────────────────────────────────
    //  Cheat System — Triple-Tap God Mode
    // ────────────────────────────────────────

    private void onMenuTapped() {
        long now = System.currentTimeMillis();
        menuTapTimes[menuTapIndex % 3] = now;
        menuTapIndex++;

        if (menuTapIndex >= 3) {
            long span = menuTapTimes[(menuTapIndex - 1) % 3] - menuTapTimes[(menuTapIndex - 3) % 3];
            if (span < 1000 && span >= 0) {
                activateGodMode();
                menuTapIndex = 0;
            }
        }
    }

    private void activateGodMode() {
        if (godModeActive) return;
        godModeActive = true;

        // Inject: IDDQD (god mode ON + health=100) + IDKFA (all weapons/ammo/keys)
        injectCheatSequence("iddqdidkfa");
        currentWeaponSlot = 7;

        showOverlay("⚡ GOD MODE ⚡", 2000);

        if (!ammoRefillActive) {
            ammoRefillActive = true;
            scheduleAmmoRefill();     // ammo every 500ms (IDFA)
            scheduleHealthRefresh();  // health every 5s (double IDDQD toggle)
        }

        handler.postDelayed(() -> { godModeActive = false; }, 2500);
    }

    private void scheduleAmmoRefill() {
        handler.postDelayed(() -> {
            if (ammoRefillActive) {
                injectCheatSequence("idfa");
                scheduleAmmoRefill();
            }
        }, 500);
    }

    /**
     * Restore health to 100% every 5 seconds via double-IDDQD toggle.
     *
     * DOOM's IDDQD cheat uses XOR (bit-flip) — a single injection TOGGLES
     * god mode ON↔OFF. To work around this, we inject it TWICE in sequence:
     *   "iddqdiddqd" → 1st toggles OFF, 2nd toggles back ON (health=100)
     * Net effect: god mode stays ON, health restored to max.
     *
     * The double sequence takes ~650ms (10 chars × 65ms). Running every 5s
     * gives plenty of time between cycles.
     */
    private void scheduleHealthRefresh() {
        handler.postDelayed(() -> {
            if (ammoRefillActive) {
                injectCheatSequence("iddqdiddqd");  // toggle OFF→ON, health=100
                scheduleHealthRefresh();
            }
        }, 5000);  // every 5 seconds
    }

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

    private void showOverlay(String text, long durationMs) {
        overlayText = text;
        overlayUntil = System.currentTimeMillis() + durationMs;
        invalidate();
    }

    private static int charToKeyCode(char c) {
        if (c >= 'a' && c <= 'z') return CHAR_KEYCODES[c - 'a'];
        if (c >= 'A' && c <= 'Z') return CHAR_KEYCODES[c - 'A'];
        return -1;
    }
}
