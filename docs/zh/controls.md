# 触控规格说明

> [English version](../en/controls.md)

## 布局

```
┌──────────────────────────────────────────────────┐
│  ☰ 菜单            Y（退出确认）                 │  ← y=120
│  (Escape, 55px)    (KEYCODE_Y, 55px)            │
│                                                  │
│                                                  │
│                游戏渲染区域                       │
│                                                  │
│                                                  │
│  🗺 地图          🔫 开火                        │  ← y=h-430
│  (Tab, 65px)      (Ctrl, 100px)                 │
│                                                  │
│                 🚪 使用                           │  ← y=h-230
│                 (Space, 65px)                    │
│                                                  │
│  ◄ ▲ ► ▼       ➡ 横移    🏃 跑步  ↵ 确认       │  ← y=h-100
│  (方向键 140px)  (DPAD_R,65)(Shift,65)(Enter,65)
└──────────────────────────────────────────────────┘
```

## 按钮规格

| # | 标签 | 按键 | Android 键码 | 尺寸 | 行为 | 用途 |
|---|-------|-----|-----------------|------|----------|---------|
| 1 | ▲ | DPAD_UP | 19 | 50px 圆 | 按住（状态） | 向前移动 |
| 2 | ▼ | DPAD_DOWN | 20 | 50px 圆 | 按住（状态） | 向后移动 |
| 3 | ◄ | DPAD_LEFT | 21 | 50px 圆 | 按住（状态） | 左转 |
| 4 | ► | DPAD_RIGHT | 22 | 50px 圆 | 按住（状态） | 右转 |
| 5 | 🔫 | CTRL_LEFT | 113 | 100px 圆 | 点按（80ms 自动释放） | 开火 |
| 6 | 🚪 | SPACE | 62 | 65px 圆 | 点按（80ms 自动释放） | 使用 / 开门 |
| 7 | 🏃 | SHIFT_LEFT | 59 | 65px 圆 | 按住（按下保持） | 跑步/冲刺 |
| 8 | 🗺️ | TAB | 61 | 65px 圆 | 点按（80ms 自动释放） | 切换自动地图 |
| 9 | ➡ | DPAD_RIGHT | 22 | 65px 圆 | 点按（80ms 自动释放） | 向右横移 |
| 10 | ↵ | ENTER | 66 | 65px 圆 | 点按（80ms 自动释放） | 选择 / 确认 |
| 11 | ☰ | ESCAPE | 111 | 55px 圆 | 点按（80ms 自动释放） | 打开菜单 |
| 12 | Y | KEYCODE_Y | 53 | 55px 圆 | 点按（80ms 自动释放） | 是 / 确认退出 |

## 尺寸与定位

### 参考坐标
- `w` = 屏幕宽度（像素）
- `h` = 屏幕高度（像素）
- `rightEdge = w - 30`（右边距）
- `bottomY = h - 30`（底部边距）

### 方向键
- 中心：`(DPAD_RADIUS + 30, h - DPAD_RADIUS - 30)`
- 外圈半径：`DPAD_RADIUS = 140px`
- 方向按钮：距中心 ±`DPAD_RADIUS * 0.55`（≈77px 偏移）
- 按钮半径：`50px`

### 操作按钮
| 按钮 | X 坐标 | Y 坐标（从 bottomY 偏移） | 半径 |
|--------|---|-------------------------|--------|
| 开火 | `rightEdge - 110` | `-400` | 100px |
| 使用 | `rightEdge - 110` | `-200` | 65px |
| 跑步 | `rightEdge - 110` | `-70` | 65px |
| 地图 | `rightEdge - 260` | `-400` | 65px |
| 横移 | `rightEdge - 300` | `-200` | 65px |
| 确认 | `rightEdge - 300` | `-70` | 65px |
| 菜单 | `rightEdge - 100` | (距顶部 y=120) | 55px |
| Y | `rightEdge - 200` | (距顶部 y=120) | 55px |

## 触摸命中检测

```java
private boolean isInCircle(float px, float py, float cx, float cy, float r) {
    float dx = px - cx;
    float dy = py - cy;
    return dx * dx + dy * dy <= r * r;
}
```

命中测试半径 = 按钮半径 + 30px 内边距。

## 手指状态机

```
                   ACTION_DOWN
  空闲 ────────────────────────────► 按下
   │                                    │
   │    ACTION_MOVE (手指移动)           │
   └────────────────────────────────────┘
   │                                    │
   │  ACTION_UP / ACTION_CANCEL         │
   ▼                                    ▼
  空闲 ◄──────────────────────────── 空闲
              （80ms 自动释放）
```

对于方向键：
- 每次 `ACTION_MOVE` 调用 `updateDpad(fingerX, fingerY)`
- 以 40px 阈值判断 dx/dy
- `updateDpadKey(oldState, newState, keyCode)` 仅在状态变化时发送事件

## 渲染

### 颜色
| 状态 | 填充 | 文字 |
|-------|------|------|
| 方向键 空闲 | `argb(100, 255,255,255)` | 白色 |
| 方向键 按下 | `argb(180, 255,255,255)` | 黑色 |
| 操作键 空闲 | `argb(100, 200,200,200)` | 白色 |
| 操作键 按下 | `argb(180, 255,200,100)`（橙色） | 白色 |

### 文字大小
- 方向键箭头：`56px`
- 操作键标签：`44px`

### 透明度
- 整体 View alpha：`0.5`（游戏上方半透明）

## 按键注入流水线

```
触摸 → ACTION_DOWN → Handler → onNativeKeyDown(Android键码) → JNI
  → Android_OnKeyDown(keycode) [C]
  → TranslateKeycode(keycode) → Android_Keycodes[] 查表
  → SDL_SendKeyboardKey(SDL_PRESSED, scancode)
  → SDL_GetKeyFromScancode(scancode) [键盘布局]
  → 将 SDL_KEYDOWN 事件推入队列（含 scancode + keysym）
  → DOOM: SDL_PollEvent() → if (ev.type == ev_keydown && ev.data1 == 'y') quit
```

## Y 键 — 为何需要特殊处理

DOOM 的退出处理函数（`m_menu.c:M_Responder`）：

```c
if (ev->type == ev_keydown) {
    ch = ev->data1;  // keysym.sym
    if (ch == 'y' || ch == 'Y' || ch == KEY_ENTER) {
        // 退出到 DOS
    }
}
```

- `commitText("y")` → 产生 `SDL_TEXTINPUT` → 事件类型 != `ev_keydown` → 被忽略 ❌
- `onNativeKeyDown(KEYCODE_Y=53)` → 产生 `SDL_KEYDOWN`，`keysym.sym = SDLK_y = 'y'` → 匹配 ✅
