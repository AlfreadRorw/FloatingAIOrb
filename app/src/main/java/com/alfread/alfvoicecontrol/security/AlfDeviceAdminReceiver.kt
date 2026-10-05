package com.alfread.alfvoicecontrol.security

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Only requests the lock-screen policy (see device_admin_receiver.xml) so
 * that the "screen off" voice command can call DevicePolicyManager#lockNow().
 * It never reads the user's PIN/pattern/password and never disables it.
 */
class AlfDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "ALF Device Administrator enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "ALF Device Administrator disabled", Toast.LENGTH_SHORT).show()
    }
}
