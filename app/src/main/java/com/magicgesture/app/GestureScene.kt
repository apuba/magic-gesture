package com.magicgesture.app

/**
 * Phase-two scene presets. GENERAL preserves the user's existing global configuration; the two
 * focused scenes reduce the detector set and apply temporary, scene-local action mappings.
 */
enum class GestureScene { GENERAL, SHORT_VIDEO, MUSIC }

data class GestureSceneDefinition(
    val scene: GestureScene,
    val enabledCodes: Set<GestureCode>,
    val actionOverrides: Map<GestureCode, GestureAction>
) {
    init {
        require(SYSTEM_CONTROL_CODES.all { it in enabledCodes }) {
            "Every scene must retain system controls"
        }
        require(actionOverrides.keys.all { it in enabledCodes && it !in SYSTEM_CONTROL_CODES }) {
            "Scene mappings must belong to enabled, non-system gesture codes"
        }
    }

    /** Applies the scene after entitlement and global user switches have already been evaluated. */
    fun restrict(features: GestureFeatureConfig): GestureFeatureConfig = when (scene) {
        GestureScene.GENERAL -> features
        else -> features.restrictedTo(enabledCodes)
    }

    /**
     * Resolves an event with scene mappings layered over the user's persisted global mappings.
     * Building a temporary manager also lets a scene revive a globally unbound gesture without
     * writing that change back to preferences. System controls still bypass ordinary mapping.
     */
    fun resolve(
        event: GestureEvent,
        globalOverrides: Map<GestureCode, GestureAction> = emptyMap()
    ): MappedGesture? {
        val mapped = GestureMappingManager(globalOverrides + actionOverrides).resolve(event) ?: return null
        return mapped.takeIf { it.mapping.code in enabledCodes }
    }
}

object GestureSceneCatalog {
    private val allCodes = GestureCode.entries.toSet()

    private val shortVideoActions = mapOf(
        GestureCode.G12 to GestureAction.LIKE,
        GestureCode.G20 to GestureAction.THUMBS_UP_LIKE,
        GestureCode.G21 to GestureAction.FAVORITE_CURRENT,
        GestureCode.G22 to GestureAction.PLAY_PAUSE,
        GestureCode.G23 to GestureAction.TOGGLE_MUTE,
        GestureCode.G24 to GestureAction.BACK,
        GestureCode.G25 to GestureAction.SCROLL_UP,
        GestureCode.G31 to GestureAction.VOLUME_UP,
        GestureCode.G32 to GestureAction.VOLUME_DOWN,
        GestureCode.G36 to GestureAction.SCROLL_DOWN
    )

    private val musicActions = mapOf(
        GestureCode.G22 to GestureAction.PLAY_PAUSE,
        GestureCode.G23 to GestureAction.TOGGLE_MUTE,
        GestureCode.G24 to GestureAction.BACK,
        GestureCode.G29 to GestureAction.MEDIA_PREVIOUS,
        GestureCode.G30 to GestureAction.MEDIA_NEXT,
        GestureCode.G31 to GestureAction.VOLUME_UP,
        GestureCode.G32 to GestureAction.VOLUME_DOWN,
        GestureCode.G33 to GestureAction.PLAY_PAUSE
    )

    private val definitions = mapOf(
        GestureScene.GENERAL to GestureSceneDefinition(
            GestureScene.GENERAL,
            allCodes,
            emptyMap()
        ),
        GestureScene.SHORT_VIDEO to GestureSceneDefinition(
            GestureScene.SHORT_VIDEO,
            shortVideoActions.keys + SYSTEM_CONTROL_CODES,
            shortVideoActions
        ),
        GestureScene.MUSIC to GestureSceneDefinition(
            GestureScene.MUSIC,
            musicActions.keys + SYSTEM_CONTROL_CODES,
            musicActions
        )
    )

    /**
     * Builds the scene the user actually runs: the factory preset minus [removedCodes], plus
     * [addedCodes].
     *
     * A gesture added from outside the preset gets **no scene mapping**, so it keeps the action the
     * user already bound to it globally. Only preset members carry their scene-local mapping, and
     * the recognition lock survives every edit.
     */
    fun definition(
        scene: GestureScene,
        removedCodes: Set<GestureCode> = emptySet(),
        addedCodes: Set<GestureCode> = emptySet()
    ): GestureSceneDefinition {
        val base = requireNotNull(definitions[scene])
        // GENERAL is the user's own global configuration; editing it here would duplicate the
        // calibration page switches, so it never accepts customisation.
        if (!isEditable(scene)) return base
        if (removedCodes.isEmpty() && addedCodes.isEmpty()) return base
        val kept = (base.enabledCodes - removedCodes) + addedCodes + SYSTEM_CONTROL_CODES
        return GestureSceneDefinition(
            scene,
            kept,
            base.actionOverrides.filterKeys { it in kept }
        )
    }

    /**
     * Only the focused presets can be customised. GENERAL simply keeps whatever the user already
     * enabled globally, and the recognition lock must survive in every scene.
     */
    fun isEditable(scene: GestureScene): Boolean = scene != GestureScene.GENERAL
}

data class GestureSceneTransition(
    val previous: GestureScene,
    val current: GestureScene
)

/**
 * Pure scene state machine. Runtime integration must safely end exclusive actions and reset the
 * recognition pipeline for every emitted transition. While G28 is locked, only the newest target
 * is retained and it is applied after Love has been unlocked and fully released.
 */
class GestureSceneController(initial: GestureScene = GestureScene.GENERAL) {
    var current: GestureScene = initial
        private set

    var pendingWhileLocked: GestureScene? = null
        private set

    fun request(target: GestureScene, recognitionLocked: Boolean): GestureSceneTransition? {
        if (recognitionLocked) {
            pendingWhileLocked = target
            return null
        }
        pendingWhileLocked = null
        return apply(target)
    }

    fun onRecognitionUnlockedAndReleased(): GestureSceneTransition? {
        val target = pendingWhileLocked ?: return null
        pendingWhileLocked = null
        return apply(target)
    }

    private fun apply(target: GestureScene): GestureSceneTransition? {
        if (target == current) return null
        val transition = GestureSceneTransition(current, target)
        current = target
        return transition
    }
}

data class GestureSceneSelection(
    val packageName: String?,
    val scene: GestureScene,
    val needsAssociation: Boolean,
    val isTemporary: Boolean,
    /**
     * True only when this selection comes from a real foreground app switch. The association sheet
     * must be driven by this flag alone: our own-app flag can flip every few hundred milliseconds
     * while window events from the previous app and from MagicGesture keep interleaving, and
     * showing the sheet on every flip made it pop up and vanish in an endless loop.
     */
    val fromForegroundSwitch: Boolean = false
)

/**
 * App-to-scene assignment state, kept independent from Android so package transitions and the
 * lifetime of a temporary override can be covered by ordinary JVM tests.
 */
class GestureSceneAssignmentController(
    associations: Map<String, GestureScene> = emptyMap(),
    autoSwitchEnabled: Boolean = true,
    /**
     * Packages that have already been offered the scene choice. Every app is asked at most once:
     * without this, switching apps kept re-opening the sheet for apps the user deliberately left
     * unassociated, which made the guidance pure noise.
     */
    notifiedPackages: Set<String> = emptySet()
) {
    private val saved = associations
        .filterKeys(::isValidPackageName)
        .toMutableMap()

    private val notified = notifiedPackages.filter(::isValidPackageName).toMutableSet()

    var autoSwitchEnabled: Boolean = autoSwitchEnabled
        private set

    var currentPackage: String? = null
        private set

    private var temporaryScene: GestureScene? = null

    /** True while MagicGesture's own UI is on top; see [onSelfForeground]. */
    private var selfForeground = false

    fun setAutoSwitchEnabled(enabled: Boolean): GestureSceneSelection {
        autoSwitchEnabled = enabled
        if (!enabled) temporaryScene = null
        return selection()
    }

    fun onForegroundApp(packageName: String?): GestureSceneSelection {
        val normalized = packageName?.takeIf(::isValidPackageName)
        if (normalized != currentPackage) temporaryScene = null
        currentPackage = normalized
        return selection(fromForegroundSwitch = true)
    }

    fun useTemporarily(scene: GestureScene): GestureSceneSelection {
        if (currentPackage == null) return selection()
        temporaryScene = scene
        return selection()
    }

    /**
     * A scene the user picked by hand from the floating dot. Unlike [useTemporarily] it also applies
     * while no foreground package is known yet, which is exactly the case when the switcher is
     * opened before any third party app has settled. The choice still ends on the next app switch.
     */
    fun useManually(scene: GestureScene): GestureSceneSelection {
        temporaryScene = scene
        return selection()
    }

    /**
     * Marks whether MagicGesture's own UI is the top window. Gesture control keeps running there,
     * but no scene preset may act on our own screens, so the scene falls back to GENERAL and no
     * association prompt is requested. The current package is deliberately kept so leaving our own
     * app restores that app's scene through the ordinary foreground path.
     */
    fun onSelfForeground(inSelf: Boolean): GestureSceneSelection {
        if (selfForeground == inSelf) return selection()
        selfForeground = inSelf
        return selection()
    }

    fun setDefault(packageName: String, scene: GestureScene): GestureSceneSelection {
        require(isValidPackageName(packageName)) { "Invalid app package name" }
        saved[packageName] = scene
        if (packageName == currentPackage) temporaryScene = null
        return selection()
    }

    /**
     * Remembers that this package has been offered the scene choice, so it is never asked again.
     * Called on every way out of the prompt: dismissed, timed out, or answered.
     */
    fun markNotified(packageName: String?) {
        val normalized = packageName?.takeIf(::isValidPackageName) ?: return
        notified.add(normalized)
    }

    fun notifiedPackages(): Set<String> = notified.toSet()

    fun removeDefault(packageName: String): GestureSceneSelection {
        saved.remove(packageName)
        if (packageName == currentPackage) temporaryScene = null
        return selection()
    }

    fun associations(): Map<String, GestureScene> = saved.toMap()

    fun replaceAssociations(associations: Map<String, GestureScene>): GestureSceneSelection {
        saved.clear()
        saved.putAll(associations.filterKeys(::isValidPackageName))
        temporaryScene = null
        return selection()
    }

    fun selection(fromForegroundSwitch: Boolean = false): GestureSceneSelection {
        val pkg = currentPackage
        if (pkg == null) {
            // No foreground app yet. Only a scene the user picked by hand can be active here; the
            // association prompt cannot, because it needs a package to bind the choice to.
            temporaryScene?.let { return GestureSceneSelection(pkg, it, false, true) }
            return GestureSceneSelection(pkg, GestureScene.GENERAL, false, false)
        }
        // A scene the user picked by hand is an explicit wish, so it survives even while our own
        // UI is on top.
        temporaryScene?.let {
            return GestureSceneSelection(pkg, it, false, true, fromForegroundSwitch)
        }
        // Our own screens are never a scene target: GENERAL keeps the user's global configuration
        // and never asks to associate MagicGesture with a scene.
        if (selfForeground) {
            return GestureSceneSelection(pkg, GestureScene.GENERAL, false, false, fromForegroundSwitch)
        }
        if (!autoSwitchEnabled) {
            return GestureSceneSelection(pkg, GestureScene.GENERAL, false, false, fromForegroundSwitch)
        }
        val associated = saved[pkg]
        return if (associated != null) {
            GestureSceneSelection(pkg, associated, false, false, fromForegroundSwitch)
        } else {
            // A real app switch is the only thing that may open the prompt, and each app is only
            // offered the choice once. Re-entering this state from our own-app flag, a preference
            // change or a manual switch stays silent as well.
            val stillToAsk = pkg !in notified
            GestureSceneSelection(pkg, GestureScene.GENERAL, stillToAsk, false, fromForegroundSwitch)
        }
    }

    companion object {
        internal fun isValidPackageName(value: String): Boolean =
            value.isNotBlank() && value.length <= 255 &&
                value.none(Char::isWhitespace) && value.contains('.')
    }
}

data class StableForeground(val packageName: String?)

/** Emits a foreground package only after the same value has remained stable for [stableMs]. */
class ForegroundPackageStabilizer(private val stableMs: Long = 500L) {
    init { require(stableMs >= 0L) }

    private var candidate: String? = null
    private var candidateSince = 0L
    private var candidateInitialized = false
    private var accepted: String? = null
    private var acceptedInitialized = false

    fun offer(packageName: String?, now: Long): StableForeground? {
        if (!candidateInitialized || packageName != candidate) {
            candidate = packageName
            candidateSince = now
            candidateInitialized = true
            return null
        }
        if (now - candidateSince < stableMs) return null
        if (acceptedInitialized && accepted == candidate) return null
        accepted = candidate
        acceptedInitialized = true
        return StableForeground(accepted)
    }

    fun reset() {
        candidate = null
        candidateSince = 0L
        candidateInitialized = false
        accepted = null
        acceptedInitialized = false
    }
}
