package io.shelldroid.core.db

import androidx.room.TypeConverter

class AuthTypeConverter {
    @TypeConverter fun toString(v: AuthType): String = v.name
    @TypeConverter fun fromString(v: String): AuthType = AuthType.valueOf(v)
}

class PortForwardTypeConverter {
    @TypeConverter fun toString(v: PortForwardType): String = v.name
    @TypeConverter fun fromString(v: String): PortForwardType = PortForwardType.valueOf(v)
}

class TombstoneTypeConverter {
    @TypeConverter fun toString(v: TombstoneType): String = v.name
    @TypeConverter fun fromString(v: String): TombstoneType = TombstoneType.valueOf(v)
}
