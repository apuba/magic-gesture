package com.magicgesture.app

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Tracks whether one of MagicGesture's own screens is actually in front of the user.
 *
 * Accessibility window events cannot answer this: the floating dot, the scene guidance banner and
 * the gesture feedback are all overlay windows owned by our own package, so every one of them
 * appearing looked like "the user is looking at MagicGesture" and dropped the current app's scene
 * back to GENERAL. Opening an app could therefore land on its scene and lose it a moment later.
 *
 * Only a resumed Activity means the user is really on our UI.
 */
class GestureApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                resumedCount += 1
            }

            override fun onActivityPaused(activity: Activity) {
                // Switching between our own screens briefly passes through zero, which is fine:
                // the service only acts on a value that stayed put for the whole stability window.
                resumedCount = (resumedCount - 1).coerceAtLeast(0)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    companion object {
        @Volatile private var resumedCount = 0

        /** True while the user is looking at one of our own screens. */
        @JvmStatic fun ownScreenVisible(): Boolean = resumedCount > 0
    }
}
