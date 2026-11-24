package com.example.pengingat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Jadwalkan ulang alarm setelah HP restart
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) = Store.rescheduleAll(c)
}
