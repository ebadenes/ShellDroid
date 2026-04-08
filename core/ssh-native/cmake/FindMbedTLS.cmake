# Stub FindMbedTLS for libssh's find_package(MbedTLS REQUIRED).
# mbedTLS is built as a CMake subproject that already provides the
# targets mbedcrypto / mbedx509 / mbedtls and MbedTLS::* aliases.
# We just need to satisfy find_package_handle_standard_args and expose
# MBEDTLS_INCLUDE_DIR / MBEDTLS_LIBRARIES for ConfigureChecks.cmake.

if(NOT DEFINED SHELLDROID_MBEDTLS_DIR)
    message(FATAL_ERROR "SHELLDROID_MBEDTLS_DIR must be set before find_package(MbedTLS)")
endif()

set(MBEDTLS_INCLUDE_DIR  "${SHELLDROID_MBEDTLS_DIR}/include" CACHE PATH "" FORCE)
set(MBEDTLS_INCLUDE_DIRS "${SHELLDROID_MBEDTLS_DIR}/include" CACHE PATH "" FORCE)
set(MBEDTLS_SSL_LIBRARY    mbedtls    CACHE STRING "" FORCE)
set(MBEDTLS_CRYPTO_LIBRARY mbedcrypto CACHE STRING "" FORCE)
set(MBEDTLS_X509_LIBRARY   mbedx509   CACHE STRING "" FORCE)
set(MBEDTLS_LIBRARIES mbedtls mbedx509 mbedcrypto CACHE STRING "" FORCE)

# Read version from build_info.h (mbedTLS 3.x)
if(EXISTS "${MBEDTLS_INCLUDE_DIR}/mbedtls/build_info.h")
    file(STRINGS "${MBEDTLS_INCLUDE_DIR}/mbedtls/build_info.h" _mbedtls_version_str
         REGEX "^#[\t ]*define[\t ]+MBEDTLS_VERSION_STRING[\t ]+\"[0-9]+\\.[0-9]+\\.[0-9]+\"")
    string(REGEX REPLACE ".*\"([0-9]+\\.[0-9]+\\.[0-9]+)\".*" "\\1"
           MBEDTLS_VERSION "${_mbedtls_version_str}")
endif()

include(FindPackageHandleStandardArgs)
find_package_handle_standard_args(MbedTLS
    REQUIRED_VARS MBEDTLS_INCLUDE_DIR MBEDTLS_LIBRARIES
    VERSION_VAR   MBEDTLS_VERSION)

# libssh src/CMakeLists.txt checks `if(TARGET MbedTLS::mbedcrypto)`.
# mbedTLS's own CMakeLists already creates this alias, but just in case:
if(NOT TARGET MbedTLS::mbedcrypto AND TARGET mbedcrypto)
    add_library(MbedTLS::mbedcrypto ALIAS mbedcrypto)
endif()
