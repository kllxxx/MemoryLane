package com.kllxx.memorylane.core.domain.repository

import com.kllxx.memorylane.core.domain.permission.PermissionsState
import kotlinx.coroutines.flow.StateFlow

/** The permissions as the system has them now; one source for every screen and the streak. */
interface PermissionsRepository {
    val state: StateFlow<PermissionsState>

    /** Reads the permissions again; the user may have changed them in the system settings. */
    fun refresh()
}
