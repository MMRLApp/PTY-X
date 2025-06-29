# PTY X for WebUI X

A Pseudoterminal built for WebUI X

## Install

Install the APK from GitHub Releases and paste the following into a choosen module. Or use the new Plugin editor in WebUI X: Portable

`/data/adb/.config/<ID>/config.webroot.json`

```jsonc
{
  // ... other configuration
  "dexFiles": [
    {
      "type": "apk",
      "path": "dev.mmrl.wxu.pty",
      "className": "dev.mmrl.wxu.pty.Shell",
      // Never disable the cached mode. It will crash when you close the WebUI and you re-open it.
      "cache": true
    }
  ]
}
```

After this have been done, you can use the PTY API

```ts
export {};

interface Shell {
  start(sh: String, argsJson: String | null, envJson: String | null): void;
  start(
    sh: String,
    argsJson: String,
    envJson: String,
    cols: number,
    rows: number
  ): void;
  write(data: String): void;
  kill(): void;
  resize(cols: number, rows: number): void;
}

declare global {
  interface Window {
    pty: Shell;
  }
}
```

Basic usage

```js
import { WXEventHandler } from "https://esm.sh/webuix";
const wx = new WXEventHandler();

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

// Start the terminal when the window is loaded
window.addEventListener("load", () => {
  const args = JSON.stringify([]);
  const env = JSON.stringify({
    PWD: "$(pwd)",
    USER: "$(id -un)",
    HOSTNAME: "$(uname -n)",
    PATH: "/data/adb/ksu/bin:/data/adb/ap/bin:/data/adb/magisk:/system/bin:/sbin:/vendor/bin:/system/sbin:/system/xbin:/system/product/bin:/system/system_ext/bin",
  });

  window.pty.start("/system/bin/su", args, env);

  // Alternative if you want it raw
  // window.pty.start("/system/bin/su", null. null);
});
```

<details>
<summary>Full Terminal Example</summary>

```html
<!DOCTYPE html>
<html>
  <head>
    <meta charset="utf-8" />
    <title>PTY Shell</title>
    <link
      rel="stylesheet"
      href="https://cdn.jsdelivr.net/npm/xterm@5.3.0/css/xterm.css"
    />
    <link
      rel="stylesheet"
      href="https://cdn.jsdelivr.net/npm/xterm-addon-webgl@0.15.0/xterm-addon-webgl.css"
    />
    <link
      rel="stylesheet"
      type="text/css"
      href="https://mui.kernelsu.org/internal/insets.css"
    />
    <link
      rel="stylesheet"
      type="text/css"
      href="https://mui.kernelsu.org/internal/colors.css"
    />
    <style>
      html,
      body {
        margin: 0;
        padding: 0;
        height: 100%;
        width: 100%;
        overflow: hidden;
        background: var(--background);
      }

      #terminal {
        height: 100%;
        width: 100%;
        margin-top: var(--window-inset-top, 0px);
        margin-bottom: var(--window-inset-bottom, 0px);
        padding: 8px;
        box-sizing: border-box;
      }

      .xterm-viewport {
        scrollbar-width: thin;
        scrollbar-color: #555 #333;
      }

      .xterm-viewport::-webkit-scrollbar {
        width: 8px;
      }

      .xterm-viewport::-webkit-scrollbar-track {
        background: #333;
      }

      .xterm-viewport::-webkit-scrollbar-thumb {
        background: #555;
        border-radius: 4px;
      }
    </style>
  </head>

  <body>
    <div id="terminal"></div>

    <script src="https://cdn.jsdelivr.net/npm/xterm@5.3.0/lib/xterm.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/xterm-addon-fit@0.7.0/lib/xterm-addon-fit.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/xterm-addon-webgl@0.15.0/lib/xterm-addon-webgl.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/xterm-addon-unicode11@0.5.0/lib/xterm-addon-unicode11.js"></script>

    <script type="module">
      import { WXEventHandler } from "https://esm.sh/webuix";
      const wx = new WXEventHandler();

      const rootStyles = getComputedStyle(document.documentElement);

      // Initialize terminal with addons
      const term = new Terminal({
        fontSize: 14,
        fontFamily: "'Fira Code', 'Courier New', monospace",
        theme: {
          background: getColor("background"),
          foreground: getColor("onBackground"),
          cursor: getColor("primary"),
          selection: getColor("surfaceTint"),
        },
        allowTransparency: true,
        cols: 80,
        rows: 24,
        scrollback: 10000,
        cursorBlink: true,
        convertEol: true,
        allowProposedApi: true,
      });

      // Addons for better performance and features
      const fitAddon = new FitAddon.FitAddon();
      const webglAddon = new WebglAddon.WebglAddon();
      const unicode11Addon = new Unicode11Addon.Unicode11Addon();

      term.loadAddon(fitAddon);
      term.loadAddon(webglAddon);
      term.loadAddon(unicode11Addon);

      // Open terminal
      term.open(document.getElementById("terminal"));
      fitAddon.fit();

      // Handle window resize
      window.addEventListener("resize", () => {
        fitAddon.fit();
        const dims = term.proposeDimensions();
        if (dims) {
          window.pty.resize(dims.cols, dims.rows);
        }
      });

      // Terminal input handling
      term.onData((data) => {
        window.pty.write(data);
      });

      // Handle output from PTY
      wx.on(window, "pty-data", (event) => {
        if (event.wx) {
          term.write(event.wx);
        }
      });

      // Handle terminal exit
      wx.on(window, "pty-exit", (event) => {
        term.writeln(`\r\nProcess exited with code ${event.wx}`);
      });

      // Error handling for WebGL
      webglAddon.onContextLoss(() => {
        webglAddon.dispose();
      });

      window.addEventListener("load", () => {
        const args = JSON.stringify([]);
        const env = JSON.stringify({
          PWD: "$(pwd)",
          USER: "$(id -un)",
          HOSTNAME: "$(uname -n)",
          PATH: "/data/adb/ksu/bin:/data/adb/ap/bin:/data/adb/magisk:/system/bin:/sbin:/vendor/bin:/system/sbin:/system/xbin:/system/product/bin:/system/system_ext/bin",
        });

        window.pty.start("/system/bin/su", args, env);
      });

      function getColor(name) {
        return rootStyles.getPropertyValue(`--${name}`).trim();
      }
    </script>
  </body>
</html>
```

</details>
