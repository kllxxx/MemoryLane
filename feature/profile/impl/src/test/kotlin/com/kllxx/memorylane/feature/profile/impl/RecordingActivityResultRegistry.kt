package com.kllxx.memorylane.feature.profile.impl

import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat

/**
 * A registry for tests of the screens that launch the system: it records what was launched and answers at once with
 * what [answer] says, as the system would after the user chose. The same small helper is in every module whose screens
 * launch something (a copy per module, there is no shared test module).
 */
internal class RecordingActivityResultRegistry(
    private val answer: (contract: ActivityResultContract<*, *>, input: Any?) -> Any?,
) : ActivityResultRegistry() {

    val launches = mutableListOf<Pair<ActivityResultContract<*, *>, Any?>>()

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
        launches += contract to input
        @Suppress("UNCHECKED_CAST")
        dispatchResult(requestCode, answer(contract, input) as O)
    }

    val owner: ActivityResultRegistryOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry = this@RecordingActivityResultRegistry
    }
}
