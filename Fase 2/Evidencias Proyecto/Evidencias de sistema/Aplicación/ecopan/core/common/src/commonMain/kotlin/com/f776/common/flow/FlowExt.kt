package com.f776.common.flow

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

inline fun <T, V> Flow<List<T>>.mapRecursive(crossinline transform: (T) -> V): Flow<List<V>> = map { it.map(transform) }