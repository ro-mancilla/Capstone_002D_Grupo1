package com.f776.ecopan.core.navigation.deeplink

import android.net.Uri

/**
 * Parse the requested Uri and store it in a easily readable format
 *
 * @param uri the target deeplink uri to link to
 */
class DeepLinkRequest(
    val uri: Uri
) {
    /**
     * A list of path segments
     */
    val pathSegments: List<String> = buildList {
        uri.host?.let { if (it.isNotEmpty()) add(it) }
        addAll(uri.pathSegments)
    }

    /**
     * A map of query name to query value.
     *
     * Every accessor here is defensive because the uri arrives from an exported, BROWSABLE intent
     * filter: it can be opaque (`ecopan:schedule/1`), on which [Uri.getQueryParameterNames]
     * throws, and it can carry valueless or malformed parameters, for which
     * [Uri.getQueryParameter] returns null. Neither may take the app down on launch.
     */
    val queries: Map<String, String> = buildMap {
        if (!uri.isHierarchical) return@buildMap
        uri.queryParameterNames.forEach { argName ->
            runCatching { uri.getQueryParameter(argName) }
                .getOrNull()
                ?.let { this[argName] = it }
        }
    }

    // TODO add parsing for other Uri components, i.e. fragments, mimeType, action
}
