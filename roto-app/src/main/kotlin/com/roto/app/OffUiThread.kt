package com.roto.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Runs [work] off the UI thread; a failure goes to [report] as its message, and the result is null. */
internal suspend fun <T : Any> offUiThread(report: (String?) -> Unit, work: () -> T): T? =
    try {
        withContext(Dispatchers.IO) { work() }
    } catch (e: IOException) {
        report(e.message)
        null
    } catch (e: IllegalArgumentException) {
        report(e.message)
        null
    }
