package dev.mmrl.wxu.pty

import android.webkit.JavascriptInterface
import com.dergoogler.mmrl.webui.interfaces.WXInterface
import com.dergoogler.mmrl.webui.interfaces.WXOptions
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class Shell : WXInterface {
    private var shell: Pty
    private var currentCols = 80
    private var currentRows = 24

    constructor(wxOptions: WXOptions) : super(wxOptions) {
        val view = wxOptions.webView
        name = "pty"

        val act = activity

        shell = Pty.create(object : Pty.EventListener {
            override fun onData(data: ByteArray) {
                act?.runOnUiThread {
                    try {
                        val str = data.decodeToString()
                        view.postWXEvent(EVENT_NAME_DATA, str)
                    } catch (e: Exception) {
                        view.postWXEvent(EVENT_NAME_DATA, data.toHexString())
                    }
                }
            }

            override fun onExit(exitCode: Int) {
                act?.runOnUiThread {
                    view.postWXEvent(EVENT_NAME_EXIT, exitCode)
                }
            }
        })
    }

    @JavascriptInterface
    fun start(sh: String, argsJson: String?, envJson: String?) {
        start(sh, argsJson ?: "[]", envJson ?: "{}", currentCols, currentRows)
    }

    @JavascriptInterface
    fun start(sh: String, argsJson: String, envJson: String, currentCols: Int, currentRows: Int) {
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

        shell.start(sh, args, env, currentCols, currentRows)
    }

    @JavascriptInterface
    fun write(command: String) {
        try {
            shell.write(command.toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            console.trace("Error writing to shell")
            console.error(e)
        }
    }

    @JavascriptInterface
    fun kill() {
        shell.kill()
    }

    @JavascriptInterface
    fun resize(cols: Int, rows: Int) {
        currentCols = cols
        currentRows = rows
        shell.resize(cols, rows)
    }

    override fun onActivityDestroy() {
        super.onActivityDestroy()
        shell.kill()
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

    private companion object {
        const val EVENT_NAME_DATA = "pty-data"
        const val EVENT_NAME_EXIT = "pty-exit"
    }
}