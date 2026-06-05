package com.jussicodes.easytimer.root

import java.io.BufferedReader
import java.io.InputStreamReader

object RootShellManager {

    fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "echo root_ok"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readLine()
            reader.close()
            process.waitFor()
            result == "root_ok"
        } catch (e: Exception) {
            false
        }
    }

    fun forceStopApp(packageName: String): Boolean {
        return executeRootCommand("am force-stop $packageName")
    }

    private fun executeRootCommand(command: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
