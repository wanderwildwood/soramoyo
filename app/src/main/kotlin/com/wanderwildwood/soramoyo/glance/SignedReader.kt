package com.wanderwildwood.soramoyo.glance

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.security.MessageDigest

/**
 * Whether the app asking ([callingPackage]) is [reader] signed with the release certificate whose
 * SHA-256 is [certSha256] and nothing else. A name alone is not enough - an app can take a reader's
 * name on a phone where the reader is not installed. A debuggable build of this app answers any
 * build of the reader, for testing.
 */
internal fun isSignedReader(context: Context, callingPackage: String?, reader: String, certSha256: String): Boolean {
    if (callingPackage != reader) return false
    if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) return true
    return runCatching {
        val signers = context.packageManager
            .getPackageInfo(reader, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
        !signers.isNullOrEmpty() && signers.all { cert ->
            MessageDigest.getInstance("SHA-256").digest(cert.toByteArray())
                .joinToString("") { "%02x".format(it) } == certSha256
        }
    }.getOrDefault(false)
}
