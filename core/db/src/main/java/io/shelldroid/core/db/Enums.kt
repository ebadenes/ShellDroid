package io.shelldroid.core.db

enum class AuthType { PASSWORD, KEY_RSA, KEY_ED25519, KEY_ECDSA }

enum class PortForwardType { LOCAL, REMOTE, DYNAMIC }

enum class TombstoneType { HOST, IDENTITY, SNIPPET, PORT_FORWARD, CONNECTION_GROUP }
