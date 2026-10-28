package com.motorista.radar

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Atualizador nativo via GitHub Releases.
 * Zero bibliotecas externas: usa apenas HttpURLConnection e DownloadManager do Android.
 */
object AppUpdater {

    private const val GITHUB_REPO = "gustavobaptistadev-ops/radar-corrida"
    private const val API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

    fun checkForUpdate(activity: Activity, onNoUpdate: (() -> Unit)? = null) {
        val currentVer = try {
            val pInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
            pInfo.versionName ?: "0.0"
        } catch (e: Exception) {
            "0.0"
        }

        thread {
            try {
                val url = URL(API_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "RadarCorrida-App")
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = reader.readText()
                    reader.close()

                    // Extração leve de JSON sem bibliotecas (regex simples)
                    val tagRegex = """"tag_name":\s*"([^"]+)"""".toRegex()
                    val tagName = tagRegex.find(response)?.groupValues?.get(1) ?: ""

                    val downloadRegex = """"browser_download_url":\s*"([^"]+app-release\.apk)"""".toRegex()
                    val downloadUrl = downloadRegex.find(response)?.groupValues?.get(1)
                        ?: "https://github.com/$GITHUB_REPO/releases/download/$tagName/app-release.apk"

                    val latestClean = tagName.removePrefix("v").trim()
                    val currentClean = currentVer.removePrefix("v").trim()

                    activity.runOnUiThread {
                        if (isNewer(latestClean, currentClean)) {
                            startDownloadAndInstall(activity, downloadUrl, tagName)
                        } else {
                            onNoUpdate?.invoke() ?: Toast.makeText(
                                activity,
                                "✅ Você já está na versão mais recente ($currentVer)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    activity.runOnUiThread {
                        Toast.makeText(activity, "Não foi possível verificar atualizações agora", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    Toast.makeText(activity, "Erro ao checar atualização: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        if (latest.isEmpty()) return false
        val latParts = latest.split('.').mapNotNull { it.toIntOrNull() }
        val curParts = current.split('.').mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(latParts.size, curParts.size)
        for (i in 0 until maxLen) {
            val l = latParts.getOrElse(i) { 0 }
            val c = curParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    private fun startDownloadAndInstall(activity: Activity, downloadUrl: String, tagName: String) {
        Toast.makeText(activity, "Baixando atualização $tagName...", Toast.LENGTH_SHORT).show()

        val fileName = "radar-corrida-$tagName.apk"
        val destinationFile = File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (destinationFile.exists()) {
            destinationFile.delete()
        }

        val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
            setTitle("Radar Corrida $tagName")
            setDescription("Baixando atualização do aplicativo...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationUri(Uri.fromFile(destinationFile))
            setMimeType("application/vnd.android.package-archive")
        }

        val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    try {
                        ctxt.unregisterReceiver(this)
                    } catch (_: Exception) {}

                    triggerInstall(activity, destinationFile)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(
                onComplete,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            activity.registerReceiver(
                onComplete,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }
    }

    private fun triggerInstall(activity: Activity, apkFile: File) {
        if (!apkFile.exists()) {
            Toast.makeText(activity, "Arquivo de atualização não encontrado", Toast.LENGTH_SHORT).show()
            return
        }

        // Permissão no Android 8+ para instalar pacotes desconhecidos
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivity(settingsIntent)
                Toast.makeText(activity, "Permita 'Instalar apps desconhecidos' para o Radar Corrida e tente novamente", Toast.LENGTH_LONG).show()
                return
            }
        }

        val apkUri = GenericFileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        activity.startActivity(installIntent)
    }
}
