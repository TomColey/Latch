package com.nathanb.lock.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBlockerPolicyTest {
    private val protected = setOf("android.safe", "com.tomcoley.latch")

    @Test
    fun `unlatched preserves inherited block list behaviour`() {
        assertTrue(shouldBlockPackage("social.app", setOf("social.app"), null, protected))
        assertFalse(shouldBlockPackage("useful.app", setOf("social.app"), null, protected))
    }

    @Test
    fun `latched blocks everything outside active Mode allow list`() {
        val allowed = setOf("maps.app", "music.app")

        assertFalse(shouldBlockPackage("maps.app", emptySet(), allowed, protected))
        assertTrue(shouldBlockPackage("social.app", emptySet(), allowed, protected))
    }

    @Test
    fun `protected packages remain available while latched`() {
        assertFalse(shouldBlockPackage("android.safe", emptySet(), emptySet(), protected))
        assertFalse(shouldBlockPackage("com.tomcoley.latch", emptySet(), emptySet(), protected))
    }

    @Test
    fun `latched allow list takes precedence over inherited block list`() {
        assertFalse(
            shouldBlockPackage(
                packageName = "maps.app",
                inheritedBlockedPackages = setOf("maps.app"),
                activeModeAllowedPackages = setOf("maps.app"),
                protectedPackages = protected,
            )
        )
    }

    @Test
    fun `standard package installer is an escape surface`() {
        assertTrue(isLatchEscapeSurface("com.android.packageinstaller", null))
        assertTrue(isLatchEscapeSurface("com.google.android.packageinstaller", "android.app.Dialog"))
    }

    @Test
    fun `permission controller uninstall activity is an escape surface`() {
        assertTrue(
            isLatchEscapeSurface(
                "com.google.android.permissioncontroller",
                "com.android.packageinstaller.UninstallerActivity",
            )
        )
    }

    @Test
    fun `launcher uninstall handoff is caught by class name`() {
        assertTrue(
            isLatchEscapeSurface(
                "app.olauncher",
                "app.olauncher.ui.UninstallDialog",
            )
        )
    }

    @Test
    fun `ordinary permission controller window is not treated as uninstall`() {
        assertFalse(
            isLatchEscapeSurface(
                "com.google.android.permissioncontroller",
                "com.android.permissioncontroller.permission.ui.GrantPermissionsActivity",
            )
        )
    }
}
