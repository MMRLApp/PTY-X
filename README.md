# PTY X for WebUI X

A Pseudoterminal built for WebUI X

## Install

Install the APK from GitHub Releases and paste the following into a choosen module. Or use the new Plugin editor in WebUI X: Portable

> [!IMPORTANT]
> This plugin requires at least WebUI X: Portable `v130`!

`/data/adb/.config/<ID>/config.webroot.json`

```jsonc
{
  // ... other configuration
  "dexFiles": [
    {
      "type": "apk",
      "path": "dev.mmrl.wxu.pty",
      "className": "dev.mmrl.wxu.pty.Pty",
      // Never disable the cached mode. It will crash when you close the WebUI and you re-open it.
      "cache": true
    }
  ]
}
```

After this have been done, you can use the PTY API

```ts
export { };

namespace Pty {
    export interface Shell {
        start(sh: String, argsJson: String | null, envJson: String | null): Instance;
        start(
            sh: String,
            argsJson: String,
            envJson: String,
            cols: number,
            rows: number
        ): Instance;
    }

    export interface Instance {
        write(data: String): void;
        kill(): void;
        resize(cols: number, rows: number): void;
    }
}

declare global {
    interface Window {
        pty: Pty.Shell;
    }
}
```

Basic usage

```js
import { WXEventHandler } from "https://esm.sh/webuix";
const wx = new WXEventHandler();

// Start the terminal when the window is loaded
window.addEventListener("load", () => {
  const args = JSON.stringify([]);
  const env = JSON.stringify({
    PWD: "$(pwd)",
    USER: "$(id -un)",
    PATH: "/data/adb/ksu/bin:/data/adb/ap/bin:/data/adb/magisk:/system/bin:/sbin:/vendor/bin:/system/sbin:/system/xbin:/system/product/bin:/system/system_ext/bin",
  });

  window.terminal = window.pty.start("/system/bin/su", args, env);

  // Alternative if you want it raw
  // window.pty.start("/system/bin/su", null, null);
});

// Handle terminal data
wx.on(window, "pty-data", (event) => {
  if (event.wx) {
    console.log(event.wx);
  }
});

// Handle terminal exit
wx.on(window, "pty-exit", (event) => {
  console.log(`Process exited with code ${event.wx}`);
});
```
