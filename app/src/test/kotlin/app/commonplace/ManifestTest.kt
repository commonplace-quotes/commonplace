package app.commonplace

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * "It cannot reach the network and asks you for nothing" is a promise the README makes, so
 * it is held by a test rather than by good intentions.
 *
 * The merged manifest is not empty: AndroidX injects
 * `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, a signature-level permission
 * scoped to this app's own package that exists so it can register a non-exported runtime
 * receiver on Android 13+. It grants access to nothing, is never shown to the user, and
 * cannot be held by another app. What matters — and what these tests pin — is that no
 * *platform* or third-party permission is ever requested.
 */
@RunWith(RobolectricTestRunner::class)
class ManifestTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val requestedPermissions: List<String>
        get() = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            ?.toList()
            .orEmpty()

    @Test
    fun `no platform permission is requested`() {
        val platform = requestedPermissions.filter { it.startsWith("android.permission.") }

        assertEquals(
            "Commonplace must request no platform permissions; found $platform",
            emptyList<String>(),
            platform,
        )
    }

    @Test
    fun `every permission in the manifest belongs to this app itself`() {
        val foreign = requestedPermissions.filterNot { it.startsWith(context.packageName) }

        assertEquals(
            "only app-private permissions are allowed; found $foreign",
            emptyList<String>(),
            foreign,
        )
    }

    @Test
    fun `the app cannot reach the network, by construction`() {
        assertFalse(
            "Commonplace must never request INTERNET",
            requestedPermissions.contains(android.Manifest.permission.INTERNET),
        )
    }

    @Test
    fun `the app does not ask to see the list of installed apps`() {
        assertFalse(
            "Commonplace has no reason to enumerate installed packages",
            requestedPermissions.contains("android.permission.QUERY_ALL_PACKAGES"),
        )
    }

    @Test
    fun `the app asks for no storage access, because backups go through the file picker`() {
        val storage = requestedPermissions.filter { it.contains("EXTERNAL_STORAGE") || it.contains("MEDIA") }

        assertEquals(
            "the Storage Access Framework grants access per file; no storage permission is needed. Found $storage",
            emptyList<String>(),
            storage,
        )
    }
}
