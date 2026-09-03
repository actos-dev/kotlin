package dev.actos

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

public data class Page<T>(
    public val items: List<T>,
    public val nextCursor: String?,
) {
    public val hasNext: Boolean get() = nextCursor != null
}

public fun <T> paginateFlow(fetchPage: suspend (cursor: String?) -> Page<T>): Flow<T> =
    flow {
        var currentCursor: String? = null
        while (true) {
            currentCoroutineContext().ensureActive()
            val page = fetchPage(currentCursor)
            for (item in page.items) {
                currentCoroutineContext().ensureActive()
                emit(item)
            }
            val next = page.nextCursor
            if (next == null || next == currentCursor) {
                break
            }
            currentCursor = next
        }
    }
