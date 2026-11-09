package com.f776.ecopan.core.navigation.deeplink

import android.net.Uri
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.text.get

@Serializable
data class TestNavKey(val id: Int) : NavKey

@RunWith(RobolectricTestRunner::class)
class DeepLinkMatcherTest {

    @Test
    fun testMatchWithPathHost() {
        // Pattern: ecopan://schedule/{id}
        // In this case, "schedule" is the host
        val patternUri = Uri.parse("ecopan://schedule/{id}")
        val pattern = DeepLinkPattern(TestNavKey.serializer(), patternUri)

        // Request: ecopan://schedule/123
        val requestUri = Uri.parse("ecopan://schedule/123")
        val request = DeepLinkRequest(requestUri)

        val matcher = DeepLinkMatcher(request, pattern)
        val match = matcher.match()

        assertNotNull("Should match", match)
        assertEquals(123, match!!.args["id"])
    }

    @Test
    fun testMatchMismatchHost() {
        // Pattern: ecopan:///schedule/{id}
        // In this case, host is empty, "schedule" is the first path segment
        val patternUri = Uri.parse("ecopan:///schedule/{id}")
        val pattern = DeepLinkPattern(TestNavKey.serializer(), patternUri)

        // Request: ecopan://schedule/123
        // In this case, "schedule" is the host, and pathSegments is ["123"]
        val requestUri = Uri.parse("ecopan://schedule/123")
        val request = DeepLinkRequest(requestUri)

        val matcher = DeepLinkMatcher(request, pattern)
        val match = matcher.match()

        assertNotNull("Should match even if one uses host and other uses path", match)
        assertEquals(123, match!!.args["id"])
    }

    @Test
    fun `an undeclared query parameter is a non-match, not a crash`() {
        // Reachable from any web page: the VIEW filter is BROWSABLE, so a third party can hand
        // MainActivity this uri. It used to NPE out of match() and take the app down on launch.
        val pattern = DeepLinkPattern(
            TestNavKey.serializer(),
            Uri.parse("ecopan://schedule/{id}")
        )
        val request = DeepLinkRequest(Uri.parse("ecopan://schedule/123?utm_source=evil"))

        assertNull(DeepLinkMatcher(request, pattern).match())
    }

    @Test
    fun `an opaque uri is a non-match, not a crash`() {
        // Uri.getQueryParameterNames throws UnsupportedOperationException on opaque uris.
        val pattern = DeepLinkPattern(
            TestNavKey.serializer(),
            Uri.parse("ecopan://schedule/{id}")
        )
        val request = DeepLinkRequest(Uri.parse("ecopan:schedule/123"))

        assertNull(DeepLinkMatcher(request, pattern).match())
    }

    @Test
    fun `a non-integer path argument is a non-match`() {
        val pattern = DeepLinkPattern(
            TestNavKey.serializer(),
            Uri.parse("ecopan://schedule/{id}")
        )
        val request = DeepLinkRequest(Uri.parse("ecopan://schedule/not-a-number"))

        assertNull(DeepLinkMatcher(request, pattern).match())
    }
}
