cmake_minimum_required(VERSION 3.22.1)
project(shelldroid_ssh C)

set(VENDOR ${CMAKE_CURRENT_SOURCE_DIR}/../../vendor)
set(SHELLDROID_MBEDTLS_DIR ${VENDOR}/mbedtls)

# Allow our stub FindMbedTLS.cmake to be found before libssh's own.
list(PREPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake")

# Android NDK: relax warnings-as-errors that third-party libs trip on.
add_compile_options(-Wno-error -Wno-unused-function -Wno-unused-variable
                    -Wno-unused-parameter -Wno-deprecated-declarations
                    -Wno-pointer-sign -Wno-incompatible-pointer-types)

# -------------------------------------------------------------------
# mbedTLS (static)
# -------------------------------------------------------------------
set(ENABLE_PROGRAMS OFF CACHE BOOL "" FORCE)
set(ENABLE_TESTING  OFF CACHE BOOL "" FORCE)
set(USE_SHARED_MBEDTLS_LIBRARY OFF CACHE BOOL "" FORCE)
set(USE_STATIC_MBEDTLS_LIBRARY ON  CACHE BOOL "" FORCE)
set(MBEDTLS_FATAL_WARNINGS     OFF CACHE BOOL "" FORCE)
set(GEN_FILES                  OFF CACHE BOOL "" FORCE)
add_subdirectory(${VENDOR}/mbedtls ${CMAKE_BINARY_DIR}/mbedtls EXCLUDE_FROM_ALL)

# -------------------------------------------------------------------
# libssh (static, mbedTLS backend)
# -------------------------------------------------------------------
set(WITH_SERVER            OFF CACHE BOOL "" FORCE)
set(WITH_GSSAPI            OFF CACHE BOOL "" FORCE)
set(WITH_EXAMPLES          OFF CACHE BOOL "" FORCE)
set(WITH_ZLIB              OFF CACHE BOOL "" FORCE)
set(WITH_MBEDTLS           ON  CACHE BOOL "" FORCE)
set(WITH_PCAP              OFF CACHE BOOL "" FORCE)
set(WITH_NACL              OFF CACHE BOOL "" FORCE)
set(WITH_GCRYPT            OFF CACHE BOOL "" FORCE)
set(WITH_SYMBOL_VERSIONING OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS      OFF CACHE BOOL "" FORCE)
set(BUILD_STATIC_LIB       ON  CACHE BOOL "" FORCE)
set(UNIT_TESTING           OFF CACHE BOOL "" FORCE)
set(CLIENT_TESTING         OFF CACHE BOOL "" FORCE)
set(SERVER_TESTING         OFF CACHE BOOL "" FORCE)

add_subdirectory(${VENDOR}/libssh ${CMAKE_BINARY_DIR}/libssh EXCLUDE_FROM_ALL)

# With BUILD_SHARED_LIBS=OFF, libssh's `add_library(ssh ...)` becomes STATIC.
# Ensure PIC so we can link it into our SHARED .so.
set_target_properties(ssh PROPERTIES POSITION_INDEPENDENT_CODE ON)
foreach(t mbedtls mbedx509 mbedcrypto)
    if(TARGET ${t})
        set_target_properties(${t} PROPERTIES POSITION_INDEPENDENT_CODE ON)
    endif()
endforeach()

# -------------------------------------------------------------------
# Our JNI shim .so
# -------------------------------------------------------------------
add_library(shelldroid_ssh SHARED src/main/cpp/shelldroid_ssh.c)

find_library(log-lib log)

target_include_directories(shelldroid_ssh PRIVATE
    ${VENDOR}/libssh/include
    ${VENDOR}/mbedtls/include
    ${CMAKE_BINARY_DIR}/libssh/include
)

target_link_libraries(shelldroid_ssh PRIVATE
    ssh
    mbedtls mbedx509 mbedcrypto
    ${log-lib}
)

# Pull in all libssh symbols (so nm shows them in the final .so for sanity).
target_link_options(shelldroid_ssh PRIVATE
    "-Wl,--whole-archive" "$<TARGET_FILE:ssh>" "-Wl,--no-whole-archive")
