package dev.mmrl.wxu.pty

import android.webkit.JavascriptInterface
import com.dergoogler.mmrl.webui.interfaces.WXInterface
import com.dergoogler.mmrl.webui.interfaces.WXOptions

class Instance(
    private val nativeHandle: Long,
    private var currentCols: Int,
    private var currentRows: Int,
    wxOptions: WXOptions,
) : WXInterface(wxOptions) {
    @JavascriptInterface
    fun resize(cols: Int, rows: Int) {
        currentCols = cols
        currentRows = rows
        nativeResize(nativeHandle, cols, rows)
    }

    @JavascriptInterface
    fun write(data: ByteArray) {
        try {
            nativeWrite(nativeHandle, data)
        } catch (e: Exception) {
            console.trace("Error writing to shell")
            console.error(e)
        }
    }

    @JavascriptInterface
    fun write(data: String) {
        this.write(data.toByteArray())
    }

    @JavascriptInterface
    fun kill() {
        try {
            nativeKill(nativeHandle)
        } catch (e: Exception) {
            console.trace("Error killing shell")
            console.error(e)
        }
    }

    private external fun nativeResize(handle: Long, cols: Int, rows: Int)

    private external fun nativeWrite(handle: Long, data: ByteArray?)

    private external fun nativeKill(handle: Long)
}