package io.shelldroid.core.db

import javax.inject.Qualifier

/**
 * Qualifier for the user-scoped DataStore provided by [DatabaseModule].
 *
 * Needed because [io.shelldroid.core.security] also provides a [DataStore], and Hilt
 * cannot disambiguate two providers for the same type without qualifiers. The security
 * module uses its own qualifier ([io.shelldroid.core.security.SecurityDataStore]).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UserPrefsDataStore
