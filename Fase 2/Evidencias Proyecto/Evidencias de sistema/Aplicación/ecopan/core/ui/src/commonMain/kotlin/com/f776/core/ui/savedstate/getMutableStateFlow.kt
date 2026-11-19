package com.f776.core.ui.savedstate

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

inline fun <reified T> SavedStateHandle.getMutableStateFlow(
    defaultValue: T,
    coroutineScope: CoroutineScope,
    key: String? = null
) = PropertyDelegateProvider<Any, ReadOnlyProperty<Any, MutableStateFlow<T>>> { thisRef, property ->
    val viewModelClassName = thisRef.let { it::class.simpleName } ?: "UnknownViewModel"
    val actualKey = key ?: "${viewModelClassName}_${property.name}"
    val json = Json { ignoreUnknownKeys = true }

    val stateFlow = MutableStateFlow(
        get<String>(actualKey)?.let { json.decodeFromString<T>(it) } ?: defaultValue
    ).also { flow ->
        flow.onEach { set(actualKey, json.encodeToString(it)) }.launchIn(coroutineScope)
    }

    ReadOnlyProperty { _, _ -> stateFlow }
}