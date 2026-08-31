package com.outsystems.plugins.inappbrowser.osinappbrowserlib.helpers

import androidx.annotation.VisibleForTesting
import android.webkit.MimeTypeMap

/**
 * Resolves the `accept` tokens reported by a WebView's file chooser
 * (`WebChromeClient.FileChooserParams#getAcceptTypes`) into concrete MIME types, and
 * decides how those MIME types should be applied to an `ACTION_GET_CONTENT` intent.
 *
 * The `accept` attribute of an HTML `<input type="file">` can list a mix of:
 * - MIME types, e.g. `image/png`, `application/pdf`, or an image wildcard type
 * - file extensions, e.g. `.pdf`, `.docx`
 *
 * Android's `ACTION_GET_CONTENT` intent only understands MIME types (via `type` and
 * [android.content.Intent.EXTRA_MIME_TYPES]), so extension tokens must be resolved to a
 * MIME type first. When the accept list spans more than one disjoint MIME category
 * (e.g. images and PDFs), a single `type` string can't represent all of them, so the
 * intent must use the wildcard type together with `EXTRA_MIME_TYPES`.
 */
object OSIABFileChooserHelper {

    /**
     * MIME type used on an intent to indicate no filtering, i.e. any content is acceptable.
     */
    const val WILDCARD_MIME_TYPE = "OSInAppBrowserLib-debug.aar"

    /**
     * The `type` / [android.content.Intent.EXTRA_MIME_TYPES] pair to apply to an
     * `ACTION_GET_CONTENT` intent.
     *
     * @property type the MIME type to set as the intent's `type`.
     * @property extraMimeTypes the value to set as [android.content.Intent.EXTRA_MIME_TYPES],
     * or `null` when no additional filtering beyond [type] is needed.
     */
    data class ChooserMimeConfig(
        val type: String,
        val extraMimeTypes: List<String>?
    )

    /**
     * Resolves raw accept tokens (MIME types and/or file extensions) into a deduplicated
     * list of concrete MIME types, using the device's [MimeTypeMap] to resolve extensions.
     * Tokens that cannot be resolved (e.g. unknown extensions) are dropped rather than
     * causing a failure.
     *
     * @param acceptTypes raw tokens as provided by `FileChooserParams#getAcceptTypes()`.
     * @return the deduplicated list of resolved MIME types, in the same order they were
     * first encountered. Empty if no token could be resolved.
     */
    fun resolveMimeTypes(acceptTypes: List<String>): List<String> =
        resolveMimeTypes(acceptTypes, ::defaultExtensionToMimeType)

    /**
     * Overload of [resolveMimeTypes] that takes the extension-to-MIME-type lookup as a
     * parameter instead of always using [defaultExtensionToMimeType], so this logic can be
     * unit tested without an Android runtime (`MimeTypeMap.getSingleton()` is not available
     * in a plain JVM test). Production code should use the single-argument [resolveMimeTypes]
     * overload; this one exists for tests only.
     *
     * @param acceptTypes raw tokens as provided by `FileChooserParams#getAcceptTypes()`.
     * @param extensionToMimeType resolves a file extension (without the leading dot) to a
     * MIME type, or `null` if it can't be resolved.
     * @return the deduplicated list of resolved MIME types, in the same order they were
     * first encountered. Empty if no token could be resolved.
     */
    @VisibleForTesting
    internal fun resolveMimeTypes(
        acceptTypes: List<String>,
        extensionToMimeType: (String) -> String?
    ): List<String> =
        acceptTypes.mapNotNull { resolveToken(it, extensionToMimeType) }.distinct()

    /**
     * Decides the `type` / [android.content.Intent.EXTRA_MIME_TYPES] pair to use for an
     * `ACTION_GET_CONTENT` intent, given an already-resolved list of MIME types (see
     * [resolveMimeTypes]).
     *
     * - No resolvable MIME types: falls back to [WILDCARD_MIME_TYPE], no extra MIME types.
     * - Exactly one resolved MIME type: uses that type directly (whether it's already a
     *   wildcard like an image wildcard type, or a specific type like `application/pdf`),
     *   no extra MIME types needed.
     * - More than one resolved MIME type: uses [WILDCARD_MIME_TYPE] plus `EXTRA_MIME_TYPES`
     *   with the full list, regardless of whether they share a top-level category. Multiple
     *   distinct subtypes under the same category (e.g. `application/pdf` + `application/msword`)
     *   are intentionally not collapsed into that category's wildcard, since a top-level
     *   category wildcard can be far broader than what was actually requested (the
     *   `application` category wildcard alone also matches zip, octet-stream, JSON, etc).
     *
     * @param resolvedMimeTypes MIME types as returned by [resolveMimeTypes]. Deduplicated
     * internally, so callers do not need to pre-deduplicate.
     * @return the [ChooserMimeConfig] to apply to the `ACTION_GET_CONTENT` intent.
     */
    fun resolveChooserMimeConfig(resolvedMimeTypes: List<String>): ChooserMimeConfig {
        val distinct = resolvedMimeTypes.distinct()
        return when (distinct.size) {
            0 -> ChooserMimeConfig(WILDCARD_MIME_TYPE, null)
            1 -> ChooserMimeConfig(distinct[0], null)
            else -> ChooserMimeConfig(WILDCARD_MIME_TYPE, distinct)
        }
    }

    /**
     * Resolves a single raw accept token into a MIME type.
     *
     * @param token a single accept token, either a MIME type (e.g. `"image/png"`) or a file
     * extension (e.g. `".pdf"`).
     * @param extensionToMimeType resolves a file extension (without the leading dot) to a
     * MIME type, or `null` if it can't be resolved.
     * @return the resolved MIME type, or `null` if [token] is blank, is neither a MIME type
     * nor an extension, or is an extension that [extensionToMimeType] can't resolve.
     */
    private fun resolveToken(token: String, extensionToMimeType: (String) -> String?): String? {
        val trimmed = token.trim()
        return when {
            trimmed.isEmpty() -> null
            trimmed.contains("/") -> trimmed
            trimmed.startsWith(".") -> extensionToMimeType(trimmed.removePrefix(".").lowercase())
            else -> null
        }
    }

    /**
     * Default extension-to-MIME-type lookup, backed by the device's [MimeTypeMap].
     *
     * @param extension a file extension without the leading dot, e.g. `"pdf"`.
     * @return the resolved MIME type, or `null` if [MimeTypeMap] has no mapping for it.
     */
    private fun defaultExtensionToMimeType(extension: String): String? =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
}
