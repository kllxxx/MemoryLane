package com.kllxx.memorylane.feature.onboarding.impl

/**
 * The pages of the onboarding, in the order they are shown. The page number in the state is the
 * [ordinal]; adding or moving a page here is the only change needed to keep the permission pages right.
 */
internal enum class OnboardingPage {
    Welcome,
    Swipe,

    /** Asks for the photo permission. */
    Photos,

    /** Asks for the notification permission. */
    Notifications,
    ;

    companion object {
        val Count: Int = entries.size

        /** The page that follows the photo permission step, shown after it was granted or skipped. */
        val AfterPhotos: Int = Photos.ordinal + 1
    }
}
