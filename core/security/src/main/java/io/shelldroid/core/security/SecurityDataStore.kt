package io.shelldroid.core.security

import javax.inject.Qualifier

/**
 * Qualifier for the security-scoped DataStore provided by [SecurityModule].
 *
 * Disambiguates from the user-preferences DataStore provided by `:core:db`
 * (`UserPrefsDataStore`). Both live in the same Hilt graph; without qualifiers
 * the compiler raises DuplicateBindings.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SecurityDataStore
