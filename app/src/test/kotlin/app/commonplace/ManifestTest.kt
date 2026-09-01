package app.commonplace

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * "It asks for no permissions" is a promise made in the README, so it is held by a test
 * rather than by good intentions. Adding any permission — INTERNET most of all — fails here.
 */
@RunWith(RobolectricTestRunner::class)
class ManifestTest {

    @Test
    fun `the app requests no permissions at all`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)

        val declared = info.requestedPermissions?.toList().orEmpty()

        assertEquals(
            "Commonplace must not request permissions; found $declared",
            emptyList<String>(),
            declared,
        )
    }
}
