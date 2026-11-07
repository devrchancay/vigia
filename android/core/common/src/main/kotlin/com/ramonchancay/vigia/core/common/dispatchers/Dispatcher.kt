package com.ramonchancay.vigia.core.common.dispatchers

import javax.inject.Qualifier

/** Qualifies an injected [kotlinx.coroutines.CoroutineDispatcher]. Never reference `Dispatchers.*` directly. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val dispatcher: VigiaDispatchers)

enum class VigiaDispatchers {
    Default,
    IO
}
