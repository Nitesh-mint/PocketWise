package com.pocketwise

import android.app.Application
import android.os.Build
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.pocketwise.core.data.repository.RecurringRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class App : Application() {
    @Inject lateinit var recurringRepository: RecurringRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate() // Hilt injects fields here
        // Add any recurring expenses (rent, subscriptions) that came due while
        // the app wasn't running — no background scheduler needed.
        appScope.launch { recurringRepository.postDue() }
        // Without google-services.json there's no Firebase config: the app still
        // runs normally and voice logging just reports that it isn't set up.
        FirebaseApp.initializeApp(this) ?: return
        // App Check proves requests come from this app, so the Gemini key can
        // stay server-side. Play Integrity only vouches for installs that came
        // from Google Play — a sideloaded release build (Android Studio, adb)
        // always failed it, which surfaced as "Couldn't reach the AI service".
        // So pick by install source, not by build type: anything not from Play
        // uses the debug provider, which only works with tokens registered in
        // the Firebase console — other people's sideloaded copies stay locked out.
        Firebase.appCheck.installAppCheckProviderFactory(
            if (isInstalledFromPlay()) PlayIntegrityAppCheckProviderFactory.getInstance() else DebugAppCheckProviderFactory.getInstance()
        )
    }

    private fun isInstalledFromPlay(): Boolean {
        val installer = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                packageManager.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstallerPackageName(packageName)
            }
        }.getOrNull()
        return installer == "com.android.vending"
    }
}
