package com.prasoon.airmousetv.data.api

/** Where Send diagnostics addresses its email; the user's email app shows it and they can change it. */
const val SUPPORT_EMAIL = "appsbyprasoon@gmail.com"

/** The first line of the email body, left for the user to describe the problem in. */
private const val ASK_WHAT_HAPPENED = "What happened? (write here)"

/** The versions a bug report needs before its events: app, phone and TV. */
data class DiagnosticsHeader(
    val appVersion: String,
    val appBuild: Long,
    val phone: String,
    val android: String,
    /** The TV's vendor, model, address and remote service version; null before a TV has said. */
    val tv: String?,
)

/** The diagnostics as the user sees and copies them: the versions, a blank line, then [log]. */
fun diagnosticsReport(header: DiagnosticsHeader, log: String): String = buildString {
    appendLine("App ${header.appVersion} (${header.appBuild})")
    appendLine("Phone: ${header.phone}, Android ${header.android}")
    appendLine("TV: ${header.tv ?: "not connected yet"}")
    if (log.isNotEmpty()) {
        appendLine()
        append(log)
    }
}.trimEnd()

/** The email's subject, with the version so reports from old builds stand out. */
fun diagnosticsSubject(appVersion: String) = "Air Mouse TV Remote $appVersion diagnostics"

/** The email's body: room for the user's own words first, then the [report]. */
fun diagnosticsEmailBody(report: String) = "$ASK_WHAT_HAPPENED\n\n\n$report"
