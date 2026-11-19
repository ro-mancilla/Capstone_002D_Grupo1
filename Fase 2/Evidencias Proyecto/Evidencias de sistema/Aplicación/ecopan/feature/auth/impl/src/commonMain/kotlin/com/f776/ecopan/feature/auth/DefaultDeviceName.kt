package com.f776.ecopan.feature.auth

/**
 * What the enrollment form pre-fills as this device's name.
 *
 * Per platform because the name is what a manager later reads in the admin list, and the two
 * clients are physically different things: "Caja 1" is a useful default on a till and
 * a wrong one on a baker's phone, which never rings up a sale (SPECS.md Client 1 vs Client 2).
 */
internal expect val DefaultDeviceName: String
