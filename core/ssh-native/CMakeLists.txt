cmake_minimum_required(VERSION 3.22.1)
project(shelldroid_ssh)
# TODO: agregar libssh + mbedtls cuando los submódulos estén
add_library(shelldroid_ssh SHARED src/main/cpp/shelldroid_ssh.c)

find_library(log-lib log)
target_link_libraries(shelldroid_ssh ${log-lib})
