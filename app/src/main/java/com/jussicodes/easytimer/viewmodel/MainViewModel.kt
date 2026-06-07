package com.jussicodes.easytimer.viewmodel


import android.app.Application

import android.app.DownloadManager

import android.app.ForegroundServiceStartNotAllowedException

import android.content.Intent

import android.content.pm.ApplicationInfo

import android.content.pm.PackageManager

import android.net.Uri

import android.os.Build

import android.os.Environment

import android.graphics.Bitmap

import android.graphics.Canvas

import android.graphics.drawable.BitmapDrawable

import android.graphics.drawable.Drawable

import android.widget.Toast

import java.io.IOException

import androidx.lifecycle.AndroidViewModel

import androidx.lifecycle.viewModelScope

import com.jussicodes.easytimer.BuildConfig

import com.jussicodes.easytimer.data.PersistedTimer

import com.jussicodes.easytimer.data.PreferencesRepository

import com.jussicodes.easytimer.model.AppInfo

import com.jussicodes.easytimer.root.RootShellManager

import com.jussicodes.easytimer.model.Screen

import com.jussicodes.easytimer.model.TimerState

import com.jussicodes.easytimer.service.TimerForegroundService

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.flow.SharingStarted

import kotlinx.coroutines.flow.StateFlow

import kotlinx.coroutines.flow.asStateFlow

import kotlinx.coroutines.flow.combine

import kotlinx.coroutines.flow.stateIn

import kotlinx.coroutines.launch

import kotlinx.coroutines.withContext

import java.net.HttpURLConnection

import java.net.URL


class MainViewModel(application: Application) : AndroidViewModel(application) {

    data class UpdateUiState(
        val currentVersion: String = BuildConfig.VERSION_NAME,
        val latestVersion: String? = null,
        val statusText: String = "点击检查更新",
        val apkUrl: String? = null,
        val releasePageUrl: String? = null,
        val isChecking: Boolean = false,
        val isDownloading: Boolean = false,
        val canInstallUpdates: Boolean = false,
        val hasUpdate: Boolean = false
    )

    private data class ReleaseInfo(
        val version: String,
        val apkUrl: String,
        val releasePageUrl: String
    )

    private companion object {
        const val LATEST_RELEASE_URL = "https://github.com/easyTIDollar/EasyTimer/releases/latest"
        const val RELEASE_DOWNLOAD_URL = "https://github.com/easyTIDollar/EasyTimer/releases/download"
    }


    private val prefs = PreferencesRepository(application)

    private val pm = application.packageManager


    // -- UI state --


    private val _currentScreen = MutableStateFlow(Screen.HOME)

    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()


    private val _searchQuery = MutableStateFlow("")

    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()


    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())

    val allApps: StateFlow<List<AppInfo>> = _allApps.asStateFlow()


    private val _favoritePackages = MutableStateFlow<Set<String>>(emptySet())

    val favoritePackages: StateFlow<Set<String>> = _favoritePackages.asStateFlow()


    private val _recentPackages = MutableStateFlow<Set<String>>(emptySet())

    val recentPackages: StateFlow<Set<String>> = _recentPackages.asStateFlow()


    private val _timerState = MutableStateFlow<TimerState>(TimerState.Idle)

    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()


    private val _isRootAvailable = MutableStateFlow(false)

    val isRootAvailable: StateFlow<Boolean> = _isRootAvailable.asStateFlow()


    private val _selectedPreset = MutableStateFlow(0)

    val selectedPreset: StateFlow<Int> = _selectedPreset.asStateFlow()


    private val _customMinutes = MutableStateFlow("")

    val customMinutes: StateFlow<String> = _customMinutes.asStateFlow()


    private val _selectedApp = MutableStateFlow<AppInfo?>(null)

    val selectedApp: StateFlow<AppInfo?> = _selectedApp.asStateFlow()


    private val _isLoading = MutableStateFlow(true)

    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()


    private val _loadError = MutableStateFlow(false)

    val loadError: StateFlow<Boolean> = _loadError.asStateFlow()


    private val _lastDuration = MutableStateFlow(15)

    val lastDuration: StateFlow<Int> = _lastDuration.asStateFlow()


    // Self-destruct

    private val _selfDestruct = MutableStateFlow(false)

    val selfDestruct: StateFlow<Boolean> = _selfDestruct.asStateFlow()


    private val _updateUiState = MutableStateFlow(
        UpdateUiState(canInstallUpdates = canInstallUpdateApks())
    )

    val updateUiState: StateFlow<UpdateUiState> = _updateUiState.asStateFlow()


    // Self-destruct toggle


    // Derived: apps filtered by search, respecting favorites/recent order

    val filteredApps: StateFlow<List<AppInfo>> = combine(

        _allApps, _searchQuery, _favoritePackages, _recentPackages

    ) { apps, query, favs, recents ->

        val filtered = if (query.isBlank()) {

            apps

        } else {

            val q = query.lowercase()

            apps.filter {

                it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q)

            }

        }

        // Sort: favorites first, then recents, then alphabetical

        filtered.sortedWith(

            compareBy<AppInfo> { if (it.packageName in favs) 0 else if (it.packageName in recents) 1 else 2 }

                .thenBy { it.appName.lowercase() }

        )

    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())


    // Derived: favorite apps list

    val favoriteApps: StateFlow<List<AppInfo>> = combine(_allApps, _favoritePackages) { apps, favs ->

        apps.filter { it.packageName in favs }

    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())


    // Derived: recent apps list (not in favorites)

    val recentApps: StateFlow<List<AppInfo>> = combine(_allApps, _recentPackages, _favoritePackages) { apps, recents, favs ->

        apps.filter { it.packageName in recents && it.packageName !in favs }

    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())


    private var allAppsLoaded = false
    private var restoredTimerKey: String? = null


    init {

        checkRoot()

        observePreferences()

        observeServiceState()

        observePersistedTimer()

        loadAllApps()

        checkLatestVersionSilently()

    }


    private fun checkRoot() {

        viewModelScope.launch(Dispatchers.IO) {

            val available = RootShellManager.isRootAvailable()

            _isRootAvailable.value = available

        }

    }


    private fun loadFavoriteApps(packages: Set<String>) {

        val missing = packages.filter { pkg -> _allApps.value.none { it.packageName == pkg } }

        if (missing.isEmpty()) return

        viewModelScope.launch {

            val apps = withContext(Dispatchers.IO) {

                missing.mapNotNull { pkg ->

                    try {

                        val appInfo = pm.getApplicationInfo(pkg, 0)

                        safeBuildAppInfo(appInfo)

                    } catch (_: Exception) { null }

                }

            }

            _allApps.value = (_allApps.value + apps).sortedBy { it.appName.lowercase() }

        }

    }


    fun loadAllApps() {

        if (allAppsLoaded) return

        allAppsLoaded = true

        viewModelScope.launch {

            _isLoading.value = true

            try {

                val apps = withContext(Dispatchers.IO) {

                    pm.getInstalledApplications(PackageManager.GET_META_DATA)

                        .filter { safeHasLaunchIntent(it.packageName) }

                        .filter { it.packageName != "com.jussicodes.easytimer" }

                        .mapNotNull { appInfo -> safeBuildAppInfo(appInfo) }

                        .sortedBy { it.appName.lowercase() }

                }

                _allApps.value = apps

                _loadError.value = false

            } catch (_: Exception) {

                _loadError.value = true

            } finally {

                _isLoading.value = false

            }

        }

    }


    private fun safeHasLaunchIntent(packageName: String): Boolean {

        return try {

            pm.getLaunchIntentForPackage(packageName) != null

        } catch (_: Exception) {

            false

        }

    }


    private fun safeBuildAppInfo(appInfo: ApplicationInfo): AppInfo? {

        return try {

            val name = pm.getApplicationLabel(appInfo).toString()

            val icon = try {

                drawableToBitmap(appInfo.loadIcon(pm), 48)

            } catch (_: Exception) { null }

            AppInfo(packageName = appInfo.packageName, appName = name, icon = icon)

        } catch (_: Exception) {

            null

        }

    }


    private fun observePreferences() {

        viewModelScope.launch {

            prefs.favoritePackages.collect { packages ->

                _favoritePackages.value = packages

                loadFavoriteApps(packages)

            }

        }

        viewModelScope.launch {

            prefs.recentPackages.collect { _recentPackages.value = it }

        }

        viewModelScope.launch {

            prefs.lastDuration.collect { _lastDuration.value = it; _selectedPreset.value = it }
        }

        viewModelScope.launch {
            prefs.selfDestructEnabled.collect { _selfDestruct.value = it }
        }


    }


    fun toggleSelfDestruct(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setSelfDestructEnabled(enabled)
            _selfDestruct.value = enabled
        }
    }


    fun checkForUpdates() {
        if (_updateUiState.value.isChecking || _updateUiState.value.isDownloading) return

        viewModelScope.launch {
            _updateUiState.value = _updateUiState.value.copy(
                isChecking = true,
                statusText = "正在检查更新..."
            )

            val release = withContext(Dispatchers.IO) {
                runCatching { fetchLatestRelease() }.getOrNull()
            }

            if (release == null) {
                _updateUiState.value = _updateUiState.value.copy(
                    isChecking = false,
                    statusText = "检查失败，稍后重试"
                )
                return@launch
            }

            applyReleaseInfo(release, isChecking = false)
        }
    }


    private fun checkLatestVersionSilently() {
        viewModelScope.launch {
            val release = withContext(Dispatchers.IO) {
                runCatching { fetchLatestRelease() }.getOrNull()
            } ?: return@launch

            applyReleaseInfo(release, isChecking = false)
        }
    }


    private fun applyReleaseInfo(release: ReleaseInfo, isChecking: Boolean) {
        val versionCompare = compareVersions(release.version, BuildConfig.VERSION_NAME)
        val hasUpdate = versionCompare > 0
            _updateUiState.value = _updateUiState.value.copy(
                latestVersion = release.version,
                apkUrl = release.apkUrl,
                releasePageUrl = release.releasePageUrl,
                isChecking = isChecking,
                canInstallUpdates = canInstallUpdateApks(),
                hasUpdate = hasUpdate,
                statusText = when {
                hasUpdate -> "发现新版本，点击下载并安装"
                versionCompare < 0 -> "当前版本高于发布版本"
                else -> "已是最新版本"
            }
        )
    }


    fun downloadAndInstallUpdate() {
        val state = _updateUiState.value
        val apkUrl = state.apkUrl
        if (!state.hasUpdate || apkUrl.isNullOrBlank() || state.isDownloading) {
            checkForUpdates()
            return
        }

        viewModelScope.launch {
            _updateUiState.value = state.copy(
                isDownloading = true,
                statusText = "正在下载更新..."
            )

            val context = getApplication<Application>()
            val downloadId = withContext(Dispatchers.IO) {
                runCatching {
                    val request = DownloadManager.Request(Uri.parse(apkUrl))
                        .setTitle("EasyTimer ${state.latestVersion}")
                        .setDescription("正在下载 EasyTimer 更新")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalFilesDir(
                            context,
                            Environment.DIRECTORY_DOWNLOADS,
                            "EasyTimer-v${state.latestVersion}.apk"
                        )
                        .setMimeType("application/vnd.android.package-archive")
                    val manager = context.getSystemService(DownloadManager::class.java)
                    manager.enqueue(request)
                }.getOrNull()
            }

            if (downloadId == null) {
                _updateUiState.value = _updateUiState.value.copy(
                    isDownloading = false,
                    statusText = "下载失败，稍后重试"
                )
                return@launch
            }

            val apkUri = withContext(Dispatchers.IO) {
                waitForDownloadedApk(downloadId)
            }

            if (apkUri == null) {
                _updateUiState.value = _updateUiState.value.copy(
                    isDownloading = false,
                    statusText = "下载失败，稍后重试"
                )
                return@launch
            }

            _updateUiState.value = _updateUiState.value.copy(
                isDownloading = false,
                statusText = "下载完成，正在打开安装器"
            )
            openApkInstaller(apkUri)
        }
    }


    fun openReleaseNotes() {
        val url = _updateUiState.value.releasePageUrl ?: return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        getApplication<Application>().startActivity(intent)
    }


    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val context = getApplication<Application>()
        val intent = Intent(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }


    private fun observeServiceState() {

        viewModelScope.launch {

            TimerForegroundService.timerState.collect { state ->

                _timerState.value = state

                when {

                    state is TimerState.Running && !state.isPaused ->

                        _currentScreen.value = Screen.ACTIVE_TIMER

                    state is TimerState.Idle && _currentScreen.value == Screen.ACTIVE_TIMER ->

                        _currentScreen.value = Screen.HOME

                }

            }

        }

    }


    private fun observePersistedTimer() {
        viewModelScope.launch {
            prefs.activeTimer.collect { timer ->
                if (timer == null || TimerForegroundService.timerState.value !is TimerState.Idle) return@collect

                val remainingSeconds = resolveRemainingSeconds(timer)
                if (remainingSeconds <= 0) {
                    prefs.clearActiveTimer()
                    return@collect
                }

                _timerState.value = TimerState.Running(
                    packageName = timer.packageName,
                    appName = timer.appName,
                    totalSeconds = timer.totalSeconds,
                    remainingSeconds = remainingSeconds,
                    isPaused = timer.isPaused
                )
                _currentScreen.value = Screen.ACTIVE_TIMER

                val restoreKey = "${timer.packageName}:${timer.endAtMillis}:${timer.remainingSeconds}:${timer.isPaused}"
                if (!timer.isPaused && restoredTimerKey != restoreKey) {
                    restoredTimerKey = restoreKey
                    restoreRunningTimerService(timer, remainingSeconds)
                }
            }
        }
    }


    // -- Actions --


    fun onSearchQueryChanged(query: String) {

        _searchQuery.value = query

    }


    fun onAppSelected(app: AppInfo) {

        _selectedApp.value = app

        viewModelScope.launch {

            try { prefs.addRecent(app.packageName) } catch (_: IOException) {}

        }

        _currentScreen.value = Screen.TIMER_SETUP

    }


    fun onPresetSelected(minutes: Int) {

        _selectedPreset.value = minutes

        _customMinutes.value = ""

    }


    fun onCustomMinutesChanged(value: String) {

        _customMinutes.value = value.filter { it.isDigit() }.take(4)

    }


    fun startTimer(app: AppInfo) {

        val minutes = _customMinutes.value.toIntOrNull() ?: _selectedPreset.value

        if (minutes <= 0) return

        viewModelScope.launch {

            try { prefs.setLastDuration(minutes) } catch (_: IOException) {}

        }


        val context = getApplication<Application>()

        val intent = Intent(context, TimerForegroundService::class.java).apply {

            action = TimerForegroundService.ACTION_START

            putExtra(TimerForegroundService.EXTRA_PACKAGE_NAME, app.packageName)

            putExtra(TimerForegroundService.EXTRA_APP_NAME, app.appName)

            putExtra(TimerForegroundService.EXTRA_DURATION_MINUTES, minutes)

            putExtra(TimerForegroundService.EXTRA_SELF_DESTRUCT, selfDestruct.value)

        }

        try {

            context.startForegroundService(intent)

        } catch (_: ForegroundServiceStartNotAllowedException) {
            Toast.makeText(context, "Cannot start foreground service", Toast.LENGTH_SHORT).show()
        }
    }




    fun launchAndStartTimer(app: AppInfo) {

        val minutes = _customMinutes.value.toIntOrNull() ?: _selectedPreset.value

        if (minutes <= 0) return


        viewModelScope.launch {

            try { prefs.setLastDuration(minutes) } catch (_: IOException) {}

        }


        val context = getApplication<Application>()


        val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)

        if (launchIntent != null) {

            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            try {

                context.startActivity(launchIntent)

            } catch (_: Exception) {}

        }


        val intent = Intent(context, TimerForegroundService::class.java).apply {

            action = TimerForegroundService.ACTION_START

            putExtra(TimerForegroundService.EXTRA_PACKAGE_NAME, app.packageName)

            putExtra(TimerForegroundService.EXTRA_APP_NAME, app.appName)

            putExtra(TimerForegroundService.EXTRA_DURATION_MINUTES, minutes)

            putExtra(TimerForegroundService.EXTRA_SELF_DESTRUCT, selfDestruct.value)

        }

        try {

            context.startForegroundService(intent)

        } catch (_: ForegroundServiceStartNotAllowedException) {

            Toast.makeText(context, "????????????????", Toast.LENGTH_SHORT).show()

        }


    }


    fun pauseTimer() {

        sendAction(TimerForegroundService.ACTION_PAUSE)

    }


    fun resumeTimer() {

        sendAction(TimerForegroundService.ACTION_RESUME)

    }


    fun cancelTimer() {

        sendAction(TimerForegroundService.ACTION_CANCEL)

        _currentScreen.value = Screen.TIMER_SETUP

    }


    fun forceStopNow() {

        sendAction(TimerForegroundService.ACTION_FORCE_STOP_NOW)

    }


    fun toggleFavorite(packageName: String) {

        viewModelScope.launch {

            try {

                if (_favoritePackages.value.contains(packageName)) {

                    prefs.removeFavorite(packageName)

                } else {

                    prefs.addFavorite(packageName)

                }

            } catch (_: IOException) {}

        }

    }


    fun navigateToSetup() {

        _currentScreen.value = Screen.TIMER_SETUP

    }


    fun navigateToSettings() {

        refreshInstallPermissionStatus()

        _currentScreen.value = Screen.SETTINGS

    }


    fun navigateToHome() {

        _currentScreen.value = Screen.HOME

    }


    private fun sendAction(action: String) {

        val intent = Intent(getApplication(), TimerForegroundService::class.java).apply {

            this.action = action

        }

        getApplication<Application>().startService(intent)

    }


    private fun fetchLatestRelease(): ReleaseInfo {
        val connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            requestMethod = "GET"
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", "EasyTimer/${BuildConfig.VERSION_NAME}")
        }

        return try {
            if (connection.responseCode !in 300..399) {
                throw IOException("GitHub release request failed: ${connection.responseCode}")
            }

            val location = connection.getHeaderField("Location").orEmpty()
            val version = location.substringAfterLast("/").removePrefix("v")
            if (version.isBlank() || version == location) {
                throw IOException("Failed to read latest release tag")
            }
            ReleaseInfo(
                version = version,
                apkUrl = buildApkUrl(version),
                releasePageUrl = location
            )
        } finally {
            connection.disconnect()
        }
    }


    private fun buildApkUrl(version: String): String {
        return "$RELEASE_DOWNLOAD_URL/v$version/EasyTimer-v$version.apk"
    }


    private fun waitForDownloadedApk(downloadId: Long): Uri? {
        val context = getApplication<Application>()
        val manager = context.getSystemService(DownloadManager::class.java)
        val query = DownloadManager.Query().setFilterById(downloadId)

        repeat(180) {
            manager.query(query)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val status = cursor.getInt(statusIndex)
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> return manager.getUriForDownloadedFile(downloadId)
                        DownloadManager.STATUS_FAILED -> return null
                    }
                }
            }
            Thread.sleep(1_000)
        }

        return null
    }


    private fun openApkInstaller(apkUri: Uri) {
        val context = getApplication<Application>()
        if (!canInstallUpdateApks()) {
            refreshInstallPermissionStatus()
            openInstallPermissionSettings()
            Toast.makeText(context, "请允许 EasyTimer 安装未知来源应用后再安装更新", Toast.LENGTH_LONG).show()
            return
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }


    private fun refreshInstallPermissionStatus() {
        _updateUiState.value = _updateUiState.value.copy(
            canInstallUpdates = canInstallUpdateApks()
        )
    }


    private fun canInstallUpdateApks(): Boolean {
        val context = getApplication<Application>()
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
    }


    private fun resolveRemainingSeconds(timer: PersistedTimer): Int {
        if (timer.isPaused) return timer.remainingSeconds
        return ((timer.endAtMillis - System.currentTimeMillis()) / 1000L).toInt()
    }


    private fun restoreRunningTimerService(timer: PersistedTimer, remainingSeconds: Int) {
        val context = getApplication<Application>()
        val intent = Intent(context, TimerForegroundService::class.java).apply {
            action = TimerForegroundService.ACTION_START
            putExtra(TimerForegroundService.EXTRA_PACKAGE_NAME, timer.packageName)
            putExtra(TimerForegroundService.EXTRA_APP_NAME, timer.appName)
            putExtra(TimerForegroundService.EXTRA_TOTAL_SECONDS, timer.totalSeconds)
            putExtra(TimerForegroundService.EXTRA_DURATION_SECONDS, remainingSeconds)
            putExtra(TimerForegroundService.EXTRA_SELF_DESTRUCT, selfDestruct.value)
        }
        try {
            context.startForegroundService(intent)
        } catch (_: ForegroundServiceStartNotAllowedException) {
            _timerState.value = TimerState.Running(
                packageName = timer.packageName,
                appName = timer.appName,
                totalSeconds = timer.totalSeconds,
                remainingSeconds = remainingSeconds,
                isPaused = timer.isPaused
            )
        }
    }


    private fun compareVersions(left: String, right: String): Int {
        val leftParts = left.split(".").map { it.toIntOrNull() ?: 0 }
        val rightParts = right.split(".").map { it.toIntOrNull() ?: 0 }
        val maxSize = maxOf(leftParts.size, rightParts.size)

        for (i in 0 until maxSize) {
            val l = leftParts.getOrElse(i) { 0 }
            val r = rightParts.getOrElse(i) { 0 }
            if (l != r) return l.compareTo(r)
        }

        return 0
    }


    private fun drawableToBitmap(drawable: Drawable, targetDp: Int): Bitmap? {

        return try {

            val density = getApplication<Application>().resources.displayMetrics.density

            val targetPx = (targetDp * density).toInt()

            if (drawable is BitmapDrawable) {

                val src = drawable.bitmap

                if (src.width <= targetPx && src.height <= targetPx) return src

                val scale = targetPx.toFloat() / maxOf(src.width, src.height)

                return Bitmap.createScaledBitmap(

                    src, (src.width * scale).toInt(), (src.height * scale).toInt(), true

                )

            }

            val w = drawable.intrinsicWidth.coerceAtLeast(1)

            val h = drawable.intrinsicHeight.coerceAtLeast(1)

            val scale = targetPx.toFloat() / maxOf(w, h)

            val scaledW = (w * scale).toInt().coerceAtLeast(1)

            val scaledH = (h * scale).toInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(scaledW, scaledH, Bitmap.Config.ARGB_8888)

            val canvas = Canvas(bitmap)

            drawable.setBounds(0, 0, scaledW, scaledH)

            drawable.draw(canvas)

            bitmap

        } catch (_: Exception) {

            null

        }

    }

}


