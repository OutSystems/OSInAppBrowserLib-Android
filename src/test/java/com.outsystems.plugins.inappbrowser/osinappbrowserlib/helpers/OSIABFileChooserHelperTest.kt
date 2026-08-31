package com.outsystems.plugins.inappbrowser.osinappbrowserlib.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OSIABFileChooserHelperTest {

    private val fakeExtensionLookup: (String) -> String? = { extension ->
        when (extension) {
            "pdf" -> "application/pdf"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            else -> null
        }
    }

    // region resolveMimeTypes

    @Test
    fun `resolveMimeTypes keeps already-MIME tokens as-is`() {
        val result = OSIABFileChooserHelper.resolveMimeTypes(listOf("image/*", "application/pdf"))
        assertEquals(listOf("image/*", "application/pdf"), result)
    }

    @Test
    fun `resolveMimeTypes resolves pdf extension token to its MIME type`() {
        val result = OSIABFileChooserHelper.resolveMimeTypes(listOf(".pdf"), fakeExtensionLookup)
        assertEquals(listOf("application/pdf"), result)
    }

    @Test
    fun `resolveMimeTypes resolves docx extension token to its MIME type`() {
        val result = OSIABFileChooserHelper.resolveMimeTypes(listOf(".docx"), fakeExtensionLookup)
        assertEquals(
            listOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            result
        )
    }

    @Test
    fun `resolveMimeTypes resolves xlsx extension token to its MIME type`() {
        val result = OSIABFileChooserHelper.resolveMimeTypes(listOf(".xlsx"), fakeExtensionLookup)
        assertEquals(
            listOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            result
        )
    }

    @Test
    fun `resolveMimeTypes resolves mixed MIME and extension tokens`() {
        val result = OSIABFileChooserHelper.resolveMimeTypes(
            listOf("image/*", ".pdf", ".docx"),
            fakeExtensionLookup
        )
        assertEquals(
            listOf(
                "image/*",
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            ),
            result
        )
    }

    @Test
    fun `resolveMimeTypes deduplicates repeated tokens`() {
        val result = OSIABFileChooserHelper.resolveMimeTypes(
            listOf("image/*", "image/*", ".pdf", ".pdf"),
            fakeExtensionLookup
        )
        assertEquals(listOf("image/*", "application/pdf"), result)
    }

    @Test
    fun `resolveMimeTypes drops unresolvable extension tokens without crashing`() {
        val result = OSIABFileChooserHelper.resolveMimeTypes(
            listOf(".unknownext", "image/*"),
            fakeExtensionLookup
        )
        assertEquals(listOf("image/*"), result)
    }

    @Test
    fun `resolveMimeTypes ignores blank tokens`() {
        val result = OSIABFileChooserHelper.resolveMimeTypes(listOf("", "  ", "image/*"), fakeExtensionLookup)
        assertEquals(listOf("image/*"), result)
    }

    @Test
    fun `resolveMimeTypes returns empty list when accept list is empty`() {
        val result = OSIABFileChooserHelper.resolveMimeTypes(emptyList(), fakeExtensionLookup)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `resolveMimeTypes drops bare tokens with no slash and no leading dot`() {
        val result = OSIABFileChooserHelper.resolveMimeTypes(listOf("pdf", "image/*"), fakeExtensionLookup)
        assertEquals(listOf("image/*"), result)
    }

    @Test
    fun `resolveMimeTypes deduplicates when a MIME token and an extension token resolve to the same type`() {
        val lookup: (String) -> String? = { if (it == "pdf") "application/pdf" else null }
        val result = OSIABFileChooserHelper.resolveMimeTypes(listOf("application/pdf", ".pdf"), lookup)
        assertEquals(listOf("application/pdf"), result)
    }

    // endregion

    // region resolveChooserMimeConfig

    @Test
    fun `resolveChooserMimeConfig returns wildcard with no extras when list is empty`() {
        val config = OSIABFileChooserHelper.resolveChooserMimeConfig(emptyList())
        assertEquals(OSIABFileChooserHelper.WILDCARD_MIME_TYPE, config.type)
        assertNull(config.extraMimeTypes)
    }

    @Test
    fun `resolveChooserMimeConfig returns the single MIME type with no extras`() {
        val config = OSIABFileChooserHelper.resolveChooserMimeConfig(listOf("image/*"))
        assertEquals("image/*", config.type)
        assertNull(config.extraMimeTypes)
    }

    @Test
    fun `resolveChooserMimeConfig collapses same-category MIME types into a wildcard category type`() {
        val config = OSIABFileChooserHelper.resolveChooserMimeConfig(listOf("image/png", "image/jpeg"))
        assertEquals("image/*", config.type)
        assertNull(config.extraMimeTypes)
    }

    @Test
    fun `resolveChooserMimeConfig uses wildcard type with extra MIME types for disjoint categories`() {
        val config = OSIABFileChooserHelper.resolveChooserMimeConfig(
            listOf("image/*", "application/pdf", "application/msword")
        )
        assertEquals(OSIABFileChooserHelper.WILDCARD_MIME_TYPE, config.type)
        assertEquals(
            setOf("image/*", "application/pdf", "application/msword"),
            config.extraMimeTypes?.toSet()
        )
    }

    @Test
    fun `resolveChooserMimeConfig dedupes even when called with a non-distinct list`() {
        val config = OSIABFileChooserHelper.resolveChooserMimeConfig(listOf("image/*", "image/*"))
        assertEquals("image/*", config.type)
        assertNull(config.extraMimeTypes)
    }

    // endregion
}
