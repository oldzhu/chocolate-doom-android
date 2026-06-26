# Third-Party Licenses

This project (Chocolate Doom Android) incorporates code from several upstream
projects, each under its own license. This file documents all dependencies.

## License Summary

| Component | License | Type | GPL-Compatible |
|-----------|---------|------|:---:|
| **Our Java code** (app/src/…/*.java) | MIT | Permissive | ✅ |
| **Chocolate Doom** | GPLv2 | Strong copyleft | ✅ (itself) |
| **SDL2** | zlib | Permissive | ✅ |
| **SDL2_mixer** | zlib | Permissive | ✅ |
| **Freedoom WADs** | BSD 3-Clause | Permissive | ✅ |

**Combined APK**: The full Chocolate Doom Android APK links GPLv2 code
(Chocolate Doom) with zlib (SDL2) and MIT (our Java). All three licenses
are GPLv2-compatible, so the combined work may be distributed under GPLv2.

---

## 1. Chocolate Doom — GPLv2

- **Project**: https://github.com/chocolate-doom/chocolate-doom
- **Version**: 3.1.1
- **License text**: See [COPYING.GPL](COPYING.GPL) or https://www.gnu.org/licenses/gpl-2.0.html

```
Chocolate Doom is Copyright (C) 1993-2020 by id Software,
Simon Howard, and contributors. Licensed under GPLv2.
```

This project does NOT include Chocolate Doom source code directly.
Users must obtain it from upstream and compile separately.

---

## 2. SDL2 — zlib License

- **Project**: https://github.com/libsdl-org/SDL
- **Version**: 2.30.0

```
Copyright (C) 1997-2024 Sam Lantinga <slouken@libsdl.org>

This software is provided 'as-is', without any express or implied
warranty. In no event will the authors be held liable for any damages
arising from the use of this software.

Permission is granted to anyone to use this software for any purpose,
including commercial applications, and to alter it and redistribute it
freely, subject to the following restrictions:

1. The origin of this software must not be misrepresented; you must not
   claim that you wrote the original software. If you use this software
   in a product, an acknowledgment in the product documentation would be
   appreciated but is not required.
2. Altered source versions must be plainly marked as such, and must not
   be misrepresented as being the original software.
3. This notice may not be removed or altered from any source distribution.
```

The SDL2 Java files under `sdl_patches/org/libsdl/app/` are derived from SDL2
source and retain this license. Two files (SDLActivity.java, SDLSurface.java)
contain modifications for OPPO ColorOS compatibility.

---

## 3. SDL2_mixer — zlib License

- **Project**: https://github.com/libsdl-org/SDL_mixer
- **Version**: 2.6.3

```
Copyright (C) 1997-2023 Sam Lantinga <slouken@libsdl.org>
Licensed under the same zlib license as SDL2 (see above).
```

---

## 4. Freedoom — BSD 3-Clause

- **Project**: https://freedoom.github.io/
- **WADs**: freedoom1.wad, freedoom2.wad, freedm.wad

```
Copyright (c) 2001-2024, Freedoom contributors
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice,
   this list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.
3. Neither the name of the Freedoom project nor the names of its contributors
   may be used to endorse or promote products derived from this software
   without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES… [full text at freedoom.github.io]
```

Freedoom WAD files are NOT included in this repository.
Users must download them separately from https://freedoom.github.io/download.html

---

## License Compliance

This project complies with all upstream licenses:

1. **GPLv2 (Chocolate Doom)**: Our Java code uses SDL2's public Java API
   (`onNativeKeyDown`, `onNativeKeyUp`, etc.) to communicate with the native
   engine through the zlib-licensed JNI bridge. The Java code does not link
   directly to GPLv2 Chocolate Doom code — SDL2 (zlib) is the intermediary.
   This is the standard architecture for SDL2-based Android apps. When
   distributed as a combined APK, the work is effectively GPLv2 but our
   individual source files remain available under MIT.

2. **zlib (SDL2/SDL2_mixer)**: Attribution preserved in file headers and
   this document.

3. **BSD (Freedoom)**: WADs are user-downloaded separately; attribution
   provided here.
