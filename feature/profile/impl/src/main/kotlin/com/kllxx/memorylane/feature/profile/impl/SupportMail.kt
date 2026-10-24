package com.kllxx.memorylane.feature.profile.impl

import android.content.Intent
import android.net.Uri

// TODO MMLN-19: set support email
internal const val SUPPORT_EMAIL = "support@example.com"

internal fun supportMailIntent(email: String, subject: String, body: String): Intent =
    Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .putExtra(Intent.EXTRA_TEXT, body)
