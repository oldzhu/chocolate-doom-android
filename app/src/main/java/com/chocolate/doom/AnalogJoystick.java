package com.chocolate.doom;

import android.graphics.*;
import org.libsdl.app.SDLActivity;

/**
 * Floating analog joystick for touch-to-move.
 * Touch anywhere on the left half of the screen, drag to move in 8 directions.
 *
 * Design:
 *   - Origin appears at touch-down point
 *   - Knob follows finger, clamped to maxRadius
 *   - Dead zone (20px) prevents drift
 *   - 8-direction output mapped to DPAD keys
 *   - Auto-run when drag exceeds 90% of max radius
 */
public class AnalogJoystick {
    // Geometry
    private float originX, originY;    // touch-down center
    private float knobX, knobY;        // current thumb position
    private final float maxRadius;     // max drag distance (px)
    private final float deadZone;      // ignore small movements (px)
    private static final float AUTO_RUN_RATIO = 0.90f; // trigger auto-run at 90% drag

    // State
    private boolean active;
    private int pointerId = -1;

    // Previous keystate for differential updates
    private boolean prevUp, prevDown, prevLeft, prevRight, prevRun;

    // Suppression — briefly disable joystick after context tap
    // to prevent arrow keys from interfering with menu selection.
    private long suppressUntil = 0;

    // Painting
    private final Paint outerPaint;
    private final Paint innerPaint;
    private final Paint knobPaint;

    public AnalogJoystick(float maxRadius, float deadZone) {
        this.maxRadius = maxRadius;
        this.deadZone = deadZone;

        outerPaint = new Paint();
        outerPaint.setAntiAlias(true);
        outerPaint.setStyle(Paint.Style.STROKE);
        outerPaint.setStrokeWidth(4f);
        outerPaint.setColor(Color.argb(120, 255, 255, 255));

        innerPaint = new Paint();
        innerPaint.setAntiAlias(true);
        innerPaint.setStyle(Paint.Style.FILL);
        innerPaint.setColor(Color.argb(40, 255, 255, 255));

        knobPaint = new Paint();
        knobPaint.setAntiAlias(true);
        knobPaint.setStyle(Paint.Style.FILL);
        knobPaint.setColor(Color.argb(160, 255, 255, 255));
    }

    public boolean isActive() { return active; }
    public int getPointerId() { return pointerId; }

    /**
     * Handle touch down on left half.
     * @param x screen X of touch
     * @param y screen Y of touch
     * @param id pointer ID
     * @return true if joystick activated
     */
    public boolean onTouchDown(float x, float y, int id, int screenWidth) {
        if (x > screenWidth / 2) return false; // only left half
        originX = x;
        originY = y;
        knobX = x;
        knobY = y;
        pointerId = id;
        active = true;
        prevUp = prevDown = prevLeft = prevRight = prevRun = false;
        return true;
    }

    /**
     * Handle touch move — update knob position and send key events.
     * Skipped during suppression window (after context tap).
     */
    public void onTouchMove(float x, float y) {
        if (!active) return;
        if (System.currentTimeMillis() < suppressUntil) return;  // suppressed

        float dx = x - originX;
        float dy = y - originY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        // Clamp to max radius
        if (dist > maxRadius) {
            float scale = maxRadius / dist;
            dx *= scale;
            dy *= scale;
            dist = maxRadius;
        }
        knobX = originX + dx;
        knobY = originY + dy;

        // Dead zone: no input if within dead zone
        if (dist < deadZone) {
            dx = 0;
            dy = 0;
        }

        // Compute 8-way direction
        boolean up    = dy < -deadZone;
        boolean down  = dy > deadZone;
        boolean left  = dx < -deadZone;
        boolean right = dx > deadZone;

        // Auto-run: hold shift when near max radius
        boolean run = (dist > maxRadius * AUTO_RUN_RATIO) && dist >= deadZone;

        // Send differential key events
        updateKey(prevUp,    up,    19); // DPAD_UP
        updateKey(prevDown,  down,  20); // DPAD_DOWN
        updateKey(prevLeft,  left,  21); // DPAD_LEFT
        updateKey(prevRight, right, 22); // DPAD_RIGHT
        updateKey(prevRun,   run,   59); // SHIFT_LEFT (auto-run)

        prevUp = up;
        prevDown = down;
        prevLeft = left;
        prevRight = right;
        prevRun = run;
    }

    /**
     * Handle touch up — release all keys and deactivate.
     */
    public void onTouchUp(int id) {
        if (id != pointerId) return;
        releaseAll();
        active = false;
        pointerId = -1;
    }

    /**
     * Briefly suppress joystick input (e.g. after context tap to prevent
     * arrow keys from overriding menu selection).
     */
    public void suppress(long durationMs) {
        suppressUntil = System.currentTimeMillis() + durationMs;
        releaseAll();  // immediately release any held keys
    }

    /**
     * Release all held keys immediately.
     */
    public void releaseAll() {
        if (prevUp)    SDLActivity.onNativeKeyUp(19);
        if (prevDown)  SDLActivity.onNativeKeyUp(20);
        if (prevLeft)  SDLActivity.onNativeKeyUp(21);
        if (prevRight) SDLActivity.onNativeKeyUp(22);
        if (prevRun)   SDLActivity.onNativeKeyUp(59);
        prevUp = prevDown = prevLeft = prevRight = prevRun = false;
    }

    private void updateKey(boolean was, boolean now, int keyCode) {
        if (now && !was) {
            SDLActivity.onNativeKeyDown(keyCode);
        } else if (!now && was) {
            SDLActivity.onNativeKeyUp(keyCode);
        }
    }

    /**
     * Draw the joystick visual (outer ring + inner knob).
     */
    public void draw(Canvas canvas) {
        if (!active) return;

        // Outer ring
        canvas.drawCircle(originX, originY, maxRadius, outerPaint);
        canvas.drawCircle(originX, originY, maxRadius, innerPaint);

        // Inner knob
        canvas.drawCircle(knobX, knobY, maxRadius * 0.35f, knobPaint);

        // Direction indicator (small dot showing current direction)
        if (prevUp || prevDown || prevLeft || prevRight) {
            float dirX = originX;
            float dirY = originY;
            if (prevUp)    dirY -= maxRadius * 0.7f;
            if (prevDown)  dirY += maxRadius * 0.7f;
            if (prevLeft)  dirX -= maxRadius * 0.7f;
            if (prevRight) dirX += maxRadius * 0.7f;
            knobPaint.setColor(Color.argb(200, 255, 200, 100)); // orange indicator
            canvas.drawCircle(dirX, dirY, maxRadius * 0.15f, knobPaint);
            knobPaint.setColor(Color.argb(160, 255, 255, 255)); // restore
        }
    }

    public boolean containsPointer(int id) {
        return pointerId == id;
    }
}
