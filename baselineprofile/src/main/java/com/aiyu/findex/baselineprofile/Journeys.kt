package com.aiyu.findex.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

const val PACKAGE_NAME = "com.aiyu.findex"
private const val TIMEOUT_MS = 5_000L

/** Grants storage + notifications so launch skips onboarding and lands on Home, like a returning user. */
fun MacrobenchmarkScope.grantPermissions() {
    device.executeShellCommand("appops set $packageName MANAGE_EXTERNAL_STORAGE allow")
    device.executeShellCommand("pm grant $packageName android.permission.POST_NOTIFICATIONS")
}

fun MacrobenchmarkScope.waitForHome() {
    device.wait(Until.hasObject(By.desc("Search files")), TIMEOUT_MS)
}

/** Home scroll, open internal storage in Explorer, scroll the listing, back to Home. */
fun MacrobenchmarkScope.browseJourney() {
    waitForHome()
    device.findObject(By.scrollable(true))?.fling(Direction.DOWN)
    device.findObject(By.scrollable(true))?.fling(Direction.UP)
    device.findObject(By.desc("Browse storage"))?.click() ?: return
    device.waitForIdle()
    device.findObject(By.scrollable(true))?.let {
        it.fling(Direction.DOWN)
        it.fling(Direction.UP)
    }
    device.pressBack()
    waitForHome()
}
