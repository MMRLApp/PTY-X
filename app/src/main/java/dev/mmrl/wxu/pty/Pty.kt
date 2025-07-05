package dev.mmrl.wxu.pty

import android.webkit.JavascriptInterface
import com.dergoogler.mmrl.webui.interfaces.WXInterface
import com.dergoogler.mmrl.webui.interfaces.WXOptions
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class Pty : WXInterface {
    private var nativeHandle: Long = 0
    private var instance: Instance? = null
    private var currentCols = 80
    private var currentRows = 24

    constructor(wxOptions: WXOptions) : super(wxOptions) {
        name = "pty"

        val listener = object : EventListener {
            override fun onData(data: ByteArray) {
                activity?.runOnUiThread {
                    try {
                        val str = data.decodeToString()
                        webView.postWXEvent(EVENT_NAME_DATA, str)
                    } catch (e: Exception) {
                        webView.postWXEvent(EVENT_NAME_DATA, data.toHexString())
                    }
                }
            }

            override fun onExit(exitCode: Int) {
                activity?.runOnUiThread {
                    webView.postWXEvent(EVENT_NAME_EXIT, exitCode)
                }
            }
        }

        nativeSetEventListener(listener)
    }

    @JavascriptInterface
    fun start(sh: String, argsJson: String?, envJson: String?): Instance? {
        return start(sh, argsJson ?: "[]", envJson ?: "{}", currentCols, currentRows)
    }

    @JavascriptInterface
    fun start(sh: String, argsJson: String, envJson: String, cols: Int, rows: Int): Instance? {
        var args = emptyArray<String>()
        var env = emptyArray<String>()

        if (isJsonArray(argsJson)) {
            args = parseArgs(argsJson)
        } else {
            console.error("Invalid args JSON: $argsJson")
        }

        if (isJsonObject(envJson)) {
            env = parseEnv(envJson)
        } else {
            console.error("Invalid env JSON: $envJson")
        }

        nativeHandle = nativeStart(
            shell = sh,
            args = args,
            env = env,
            cols = cols,
            rows = rows
        )

        instance = Instance(
            nativeHandle = nativeHandle,
            currentCols = cols,
            currentRows = rows,
            wxOptions = wxOptions
        )

        return instance
    }

    @get:JavascriptInterface
    val version
        get(): Int {
            return BuildConfig.COMMIT_COUNT
        }

    interface EventListener {
        fun onData(data: ByteArray)

        fun onExit(exitCode: Int)
    }

    override fun onActivityDestroy() {
        super.onActivityDestroy()
        instance?.kill()
    }

    // Helper function for binary data fallback
    private fun ByteArray.toHexString(): String {
        return this.joinToString("") { "%02x".format(it) }
    }

    private fun isJsonArray(str: String): Boolean {
        return try {
            JSONArray(str)
            true
        } catch (_: JSONException) {
            false
        }
    }

    private fun isJsonObject(str: String): Boolean {
        return try {
            JSONObject(str)
            true
        } catch (_: JSONException) {
            false
        }
    }

    private fun parseArgs(json: String): Array<String> {
        val jsonArray = JSONArray(json)
        val list = mutableListOf<String>()
        for (i in 0 until jsonArray.length()) {
            list.add(jsonArray.getString(i))
        }
        return list.toTypedArray()
    }

    private fun parseEnv(json: String): Array<String> {
        val jsonObj = JSONObject(json)
        val list = mutableListOf<String>()
        val keys = jsonObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = jsonObj.getString(key)
            list.add("$key=$value")
        }
        return list.toTypedArray()
    }

    private external fun nativeStart(
        shell: String,
        args: Array<String>,
        env: Array<String>,
        cols: Int,
        rows: Int,
    ): Long

    private external fun nativeSetEventListener(listener: EventListener)

    companion object {
        const val EVENT_NAME_DATA = "pty-data"
        const val EVENT_NAME_EXIT = "pty-exit"
        const val DEFAULT_COLS = 80
        const val DEFAULT_ROWS = 24

        init {
            System.loadLibrary("pty")
        }
    }
}