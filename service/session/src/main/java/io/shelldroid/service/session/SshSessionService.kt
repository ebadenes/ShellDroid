package io.shelldroid.service.session

import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.LifecycleService

class SshSessionService : LifecycleService() {
    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}
