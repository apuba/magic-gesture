package com.magicgesture.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureSceneTest {
    @Test fun everySceneRetainsTheRecognitionLock() {
        GestureScene.entries.forEach { scene ->
            assertTrue(GestureCode.G28 in GestureSceneCatalog.definition(scene).enabledCodes)
        }
    }

    @Test fun shortVideoKeepsFocusedControlsAndPausesCursorAndDuplicatePalmScroll() {
        val features = GestureSceneCatalog.definition(GestureScene.SHORT_VIDEO)
            .restrict(GestureFeatureConfig())

        assertFalse(features.cursor)
        assertFalse(features.click)
        assertFalse(features.palmVerticalScroll)
        assertTrue(features.like)
        assertTrue(features.thumbsUp)
        assertTrue(features.ok)
        assertTrue(features.lShape)
        assertTrue(features.lShapeDown)
        assertTrue(features.twoFingerVolume)
        assertTrue(features.loveLock)
    }

    @Test fun musicKeepsMediaControlsAndPausesShortVideoGestures() {
        val features = GestureSceneCatalog.definition(GestureScene.MUSIC)
            .restrict(GestureFeatureConfig())

        assertTrue(features.playPause)
        assertTrue(features.pinkyMute)
        assertTrue(features.twoFingerTrack)
        assertTrue(features.twoFingerVolume)
        assertTrue(features.leftL)
        assertTrue(features.loveLock)
        assertFalse(features.lShape)
        assertFalse(features.lShapeDown)
        assertFalse(features.like)
    }

    @Test fun sceneMappingTemporarilyOverridesGlobalMappingWithoutMutatingIt() {
        val globalOverrides = mapOf(GestureCode.G25 to GestureAction.SCREENSHOT)
        val global = requireNotNull(GestureMappingManager(globalOverrides).resolve(GestureEvent.LShape))
        val sceneMapped = requireNotNull(GestureSceneCatalog.definition(GestureScene.SHORT_VIDEO)
            .resolve(GestureEvent.LShape, globalOverrides))

        assertEquals(GestureAction.SCREENSHOT, global.mapping.action)
        assertEquals(GestureAction.SCROLL_UP, sceneMapped.mapping.action)
    }

    @Test fun scenePresetCanTemporarilyReviveAGloballyUnboundAction() {
        val globalOverrides = mapOf(GestureCode.G25 to GestureAction.NONE)
        assertNull(GestureMappingManager(globalOverrides).resolve(GestureEvent.LShape))

        val sceneMapped = requireNotNull(GestureSceneCatalog.definition(GestureScene.SHORT_VIDEO)
            .resolve(GestureEvent.LShape, globalOverrides))
        assertEquals(GestureAction.SCROLL_UP, sceneMapped.mapping.action)
    }

    @Test fun sceneFiltersGesturesThatAreNotPartOfItsPreset() {
        val selfie = requireNotNull(GestureMappingManager().resolve(GestureEvent.Selfie))
        assertNull(GestureSceneCatalog.definition(GestureScene.SHORT_VIDEO).resolve(GestureEvent.Selfie))
        assertEquals(selfie, GestureSceneCatalog.definition(GestureScene.GENERAL).resolve(GestureEvent.Selfie))
    }

    @Test fun removingAGestureDropsItFromTheSceneAndItsMapping() {
        val trimmed = GestureSceneCatalog.definition(GestureScene.SHORT_VIDEO, setOf(GestureCode.G12))

        assertFalse(GestureCode.G12 in trimmed.enabledCodes)
        assertNull(trimmed.actionOverrides[GestureCode.G12])
        assertFalse(trimmed.restrict(GestureFeatureConfig()).like)
        // Untouched gestures keep both their place in the scene and their scene mapping.
        assertTrue(GestureCode.G25 in trimmed.enabledCodes)
        assertEquals(GestureAction.SCROLL_UP, requireNotNull(trimmed.resolve(GestureEvent.LShape)).mapping.action)
    }

    @Test fun recognitionLockSurvivesEverySceneRemoval() {
        val trimmed = GestureSceneCatalog.definition(GestureScene.SHORT_VIDEO, SYSTEM_CONTROL_CODES)

        assertTrue(trimmed.enabledCodes.containsAll(SYSTEM_CONTROL_CODES))
        assertTrue(GestureSceneCatalog.definition(GestureScene.MUSIC, SYSTEM_CONTROL_CODES)
            .enabledCodes.containsAll(SYSTEM_CONTROL_CODES))
    }

    @Test fun generalSceneNeverAcceptsRemovals() {
        assertFalse(GestureSceneCatalog.isEditable(GestureScene.GENERAL))
        assertTrue(GestureSceneCatalog.isEditable(GestureScene.SHORT_VIDEO))
        assertTrue(GestureSceneCatalog.isEditable(GestureScene.MUSIC))

        val definition = GestureSceneCatalog.definition(GestureScene.GENERAL, setOf(GestureCode.G22))
        assertEquals(GestureCode.entries.toSet(), definition.enabledCodes)
        assertTrue(GestureCode.G22 in definition.enabledCodes)
    }

    @Test fun addingAGestureOutsideThePresetKeepsItsGlobalMapping() {
        val preset = GestureSceneCatalog.definition(GestureScene.MUSIC)
        assertFalse(GestureCode.G25 in preset.enabledCodes)

        val edited = GestureSceneCatalog.definition(GestureScene.MUSIC, addedCodes = setOf(GestureCode.G25))

        assertTrue(GestureCode.G25 in edited.enabledCodes)
        // No scene mapping is invented for an added gesture: the user's own global mapping applies.
        assertNull(edited.actionOverrides[GestureCode.G25])
        // The preset mappings are untouched.
        assertEquals(GestureAction.MEDIA_NEXT, edited.actionOverrides[GestureCode.G30])
    }

    @Test fun removalsAndAdditionsCombineAndKeepTheRecognitionLock() {
        val preset = GestureSceneCatalog.definition(GestureScene.SHORT_VIDEO)
        val edited = GestureSceneCatalog.definition(
            GestureScene.SHORT_VIDEO,
            setOf(GestureCode.G12),
            setOf(GestureCode.G29, GestureCode.G30)
        )

        assertEquals((preset.enabledCodes - GestureCode.G12) + GestureCode.G29 + GestureCode.G30, edited.enabledCodes)
        assertFalse(GestureCode.G12 in edited.enabledCodes)
        assertNull(edited.actionOverrides[GestureCode.G12])
        assertTrue(edited.enabledCodes.containsAll(SYSTEM_CONTROL_CODES))
    }

    @Test fun generalSceneIgnoresBothRemovalsAndAdditions() {
        val base = GestureSceneCatalog.definition(GestureScene.GENERAL)
        val edited = GestureSceneCatalog.definition(
            GestureScene.GENERAL,
            setOf(GestureCode.G22),
            setOf(GestureCode.G25)
        )

        // GENERAL is the user's own global configuration, so the factory object is returned as is.
        assertTrue(base === edited)
        assertEquals(GestureCode.entries.toSet(), edited.enabledCodes)
    }

    @Test fun clearingRemovalsRestoresTheWholePreset() {
        val preset = GestureSceneCatalog.definition(GestureScene.MUSIC)
        val trimmed = GestureSceneCatalog.definition(GestureScene.MUSIC, setOf(GestureCode.G29, GestureCode.G30))

        assertEquals(preset.enabledCodes - setOf(GestureCode.G29, GestureCode.G30), trimmed.enabledCodes)
        assertEquals(preset.enabledCodes, GestureSceneCatalog.definition(GestureScene.MUSIC).enabledCodes)
    }

    @Test fun globalDisabledSwitchStaysDisabledInsideScene() {
        val features = GestureSceneCatalog.definition(GestureScene.SHORT_VIDEO)
            .restrict(GestureFeatureConfig(lShapeDown = false, like = false))

        assertFalse(features.lShapeDown)
        assertFalse(features.like)
        assertTrue(features.lShape)
    }

    @Test fun lockedSceneChangesWaitForReleaseAndOnlyLatestTargetWins() {
        val controller = GestureSceneController()

        assertNull(controller.request(GestureScene.SHORT_VIDEO, recognitionLocked = true))
        assertNull(controller.request(GestureScene.MUSIC, recognitionLocked = true))
        assertEquals(GestureScene.GENERAL, controller.current)
        assertEquals(GestureScene.MUSIC, controller.pendingWhileLocked)

        val transition = requireNotNull(controller.onRecognitionUnlockedAndReleased())
        assertEquals(GestureScene.GENERAL, transition.previous)
        assertEquals(GestureScene.MUSIC, transition.current)
        assertEquals(GestureScene.MUSIC, controller.current)
        assertNull(controller.pendingWhileLocked)
    }

    @Test fun requestingCurrentSceneDoesNotCreateAResetBoundary() {
        val controller = GestureSceneController(GestureScene.MUSIC)
        assertNull(controller.request(GestureScene.MUSIC, recognitionLocked = false))
    }

    @Test fun unknownAppFallsBackToGeneralAndRequestsAssociation() {
        val controller = GestureSceneAssignmentController()
        val selection = controller.onForegroundApp("com.example.video")

        assertEquals(GestureScene.GENERAL, selection.scene)
        assertTrue(selection.needsAssociation)
        assertFalse(selection.isTemporary)
    }

    @Test fun savedAssociationIsResolvedWithoutPrompting() {
        val controller = GestureSceneAssignmentController(
            mapOf("com.example.music" to GestureScene.MUSIC)
        )
        val selection = controller.onForegroundApp("com.example.music")

        assertEquals(GestureScene.MUSIC, selection.scene)
        assertFalse(selection.needsAssociation)
    }

    @Test fun temporaryChoiceExpiresOnlyAfterLeavingTheCurrentApp() {
        val controller = GestureSceneAssignmentController(
            mapOf("com.example.video" to GestureScene.SHORT_VIDEO)
        )
        controller.onForegroundApp("com.example.video")
        assertEquals(GestureScene.GENERAL, controller.useTemporarily(GestureScene.GENERAL).scene)
        assertTrue(controller.selection().isTemporary)

        assertEquals(GestureScene.GENERAL, controller.onForegroundApp("com.example.other").scene)
        assertFalse(controller.selection().isTemporary)
        assertEquals(GestureScene.SHORT_VIDEO, controller.onForegroundApp("com.example.video").scene)
    }

    @Test fun settingDefaultClearsTemporaryChoiceForThatApp() {
        val controller = GestureSceneAssignmentController()
        controller.onForegroundApp("com.example.player")
        controller.useTemporarily(GestureScene.SHORT_VIDEO)

        val selection = controller.setDefault("com.example.player", GestureScene.MUSIC)
        assertEquals(GestureScene.MUSIC, selection.scene)
        assertFalse(selection.isTemporary)
        assertEquals(GestureScene.MUSIC, controller.associations()["com.example.player"])
    }

    @Test fun deletingCurrentAssociationFallsBackAndRequestsAChoice() {
        val controller = GestureSceneAssignmentController(
            mapOf("com.example.video" to GestureScene.SHORT_VIDEO)
        )
        controller.onForegroundApp("com.example.video")

        val selection = controller.removeDefault("com.example.video")
        assertEquals(GestureScene.GENERAL, selection.scene)
        assertTrue(selection.needsAssociation)
    }

    @Test fun disablingAutoSwitchReturnsGeneralAndSuppressesPrompts() {
        val controller = GestureSceneAssignmentController(
            mapOf("com.example.music" to GestureScene.MUSIC)
        )
        controller.onForegroundApp("com.example.music")

        val selection = controller.setAutoSwitchEnabled(false)
        assertEquals(GestureScene.GENERAL, selection.scene)
        assertFalse(selection.needsAssociation)
        assertFalse(selection.isTemporary)
    }

    @Test fun manualTemporarySceneStillWorksWhenAutoSwitchIsDisabled() {
        val controller = GestureSceneAssignmentController(autoSwitchEnabled = false)
        controller.onForegroundApp("com.example.music")

        val selection = controller.useTemporarily(GestureScene.MUSIC)
        assertEquals(GestureScene.MUSIC, selection.scene)
        assertTrue(selection.isTemporary)
        assertFalse(selection.needsAssociation)
    }

    @Test fun invalidPackagesAreRejectedOrTreatedAsNoForegroundApp() {
        val controller = GestureSceneAssignmentController()
        assertEquals(GestureScene.GENERAL, controller.onForegroundApp("not a package").scene)
        assertNull(controller.currentPackage)
        assertFalse(GestureSceneAssignmentController.isValidPackageName("bad package"))
        assertTrue(GestureSceneAssignmentController.isValidPackageName("com.example.valid"))
    }

    @Test fun replacingSavedAssociationsReevaluatesTheCurrentApp() {
        val controller = GestureSceneAssignmentController()
        controller.onForegroundApp("com.example.video")

        val selection = controller.replaceAssociations(
            mapOf("com.example.video" to GestureScene.SHORT_VIDEO)
        )
        assertEquals(GestureScene.SHORT_VIDEO, selection.scene)
        assertFalse(selection.needsAssociation)
    }

    @Test fun ownAppInForegroundFallsBackToGeneralWithoutAskingForAssociation() {
        val controller = GestureSceneAssignmentController(
            mapOf("com.example.video" to GestureScene.SHORT_VIDEO)
        )
        controller.onForegroundApp("com.example.video")
        assertEquals(GestureScene.SHORT_VIDEO, controller.selection().scene)

        val selection = controller.onSelfForeground(true)
        assertEquals(GestureScene.GENERAL, selection.scene)
        assertFalse(selection.needsAssociation)
        assertFalse(selection.isTemporary)
        // The package is kept so leaving our own app can restore that app's scene.
        assertEquals("com.example.video", selection.packageName)
    }

    @Test fun ownAppWithoutAssociationNeverRequestsAPrompt() {
        val controller = GestureSceneAssignmentController()
        controller.onForegroundApp("com.example.unknown")

        assertTrue(controller.selection().needsAssociation)
        assertFalse(controller.onSelfForeground(true).needsAssociation)
    }

    @Test fun ownAppInForegroundDropsTheSavedSceneButKeepsAManualChoice() {
        val controller = GestureSceneAssignmentController(
            mapOf("com.example.player" to GestureScene.SHORT_VIDEO)
        )
        controller.onForegroundApp("com.example.player")
        // The saved scene alone stops acting while our own UI is on top.
        assertEquals(GestureScene.GENERAL, controller.onSelfForeground(true).scene)

        // A scene the user picked by hand is an explicit wish, so it survives our own UI.
        controller.useManually(GestureScene.MUSIC)
        assertEquals(GestureScene.MUSIC, controller.selection().scene)
        assertEquals(GestureScene.MUSIC, controller.onSelfForeground(false).scene)
        assertTrue(controller.selection().isTemporary)
    }

    @Test fun manualSceneSwitchAlsoWorksWithoutAForegroundPackage() {
        val controller = GestureSceneAssignmentController()
        assertEquals(GestureScene.GENERAL, controller.selection().scene)
        assertEquals(GestureScene.MUSIC, controller.useManually(GestureScene.MUSIC).scene)
        assertEquals(GestureScene.MUSIC, controller.selection().scene)
        // The manual choice still ends with the next app switch.
        assertEquals(GestureScene.GENERAL, controller.onForegroundApp("com.example.video").scene)
    }

    @Test fun packageTrackingStaysIndependentFromOwnAppFlag() {
        val controller = GestureSceneAssignmentController(
            mapOf("com.example.music" to GestureScene.MUSIC)
        )
        controller.onForegroundApp("com.example.video")
        controller.onSelfForeground(true)

        assertEquals("com.example.music", controller.onForegroundApp("com.example.music").packageName)
        assertEquals(GestureScene.GENERAL, controller.selection().scene)
        assertEquals(GestureScene.MUSIC, controller.onSelfForeground(false).scene)
    }

    @Test fun anAppIsOnlyOfferedTheSceneChoiceOnce() {
        val controller = GestureSceneAssignmentController()

        val first = controller.onForegroundApp("com.example.unknown")
        assertTrue(first.needsAssociation)
        assertTrue(first.fromForegroundSwitch)

        controller.markNotified("com.example.unknown")

        // Same app, silent from now on, even through another real foreground switch. This is what
        // stopped the guidance from reappearing every time the user went back to an app they had
        // deliberately left unassociated.
        val again = controller.onForegroundApp("com.example.unknown")
        assertFalse(again.needsAssociation)
        assertEquals(GestureScene.GENERAL, again.scene)

        // A different app is still offered the choice.
        assertTrue(controller.onForegroundApp("com.example.other").needsAssociation)
    }

    @Test fun markingNotifiedIgnoresPackagesThatCannotBeBound() {
        val controller = GestureSceneAssignmentController()
        controller.onForegroundApp("com.example.unknown")

        controller.markNotified(null)
        controller.markNotified("not-a-package")

        assertTrue(controller.selection().needsAssociation)
        assertEquals(emptySet<String>(), controller.notifiedPackages())
        // A valid package is remembered, and only that one.
        controller.markNotified("com.example.unknown")
        assertEquals(setOf("com.example.unknown"), controller.notifiedPackages())
    }

    @Test fun onlyARealForegroundAppSwitchMayOpenTheAssociationSheet() {
        val controller = GestureSceneAssignmentController()

        val fromSwitch = controller.onForegroundApp("com.example.unknown")
        assertTrue(fromSwitch.needsAssociation)
        assertTrue(fromSwitch.fromForegroundSwitch)

        // Our own-app flag interleaves with the previous app's window events and flips back and
        // forth. Re-opening the sheet on every flip made it pop up and vanish in an endless loop.
        assertFalse(controller.onSelfForeground(true).fromForegroundSwitch)
        val backToApp = controller.onSelfForeground(false)
        assertTrue(backToApp.needsAssociation)
        assertFalse(backToApp.fromForegroundSwitch)

        // A preference reload or a manual switch must not open it either.
        assertFalse(controller.setAutoSwitchEnabled(true).fromForegroundSwitch)
        assertFalse(controller.useTemporarily(GestureScene.MUSIC).fromForegroundSwitch)
        assertFalse(controller.replaceAssociations(emptyMap()).fromForegroundSwitch)
    }

    @Test fun foregroundPackageMustRemainStableBeforeItIsAccepted() {
        val stabilizer = ForegroundPackageStabilizer(500L)
        assertNull(stabilizer.offer("com.example.first", 0L))
        assertNull(stabilizer.offer("com.example.second", 200L))
        assertNull(stabilizer.offer("com.example.second", 699L))
        assertEquals("com.example.second", stabilizer.offer("com.example.second", 700L)?.packageName)
        assertNull(stabilizer.offer("com.example.second", 1_000L))
    }

    @Test fun stableLossOfForegroundIsAlsoEmittedForGeneralFallback() {
        val stabilizer = ForegroundPackageStabilizer(500L)
        stabilizer.offer("com.example.video", 0L)
        stabilizer.offer("com.example.video", 500L)

        assertNull(stabilizer.offer(null, 600L))
        val lost = requireNotNull(stabilizer.offer(null, 1_100L))
        assertNull(lost.packageName)
    }
}
