package stremio.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    val STREMIO_BIND_NIC_COMPATIBILITY = Compatibility(
        name = "Stremio",
        packageName = "com.stremio.one",
        apkFileType = ApkFileType.APK,
        appIconColor = 0xFF0045, // Icon color in Morphe Manager
        targets = listOf(
            // "version = null" means the patch works with the latest app target
            // and is expected to work with all future app targets
            AppTarget(
                version = null
            ),
            AppTarget(
                version = null,
            )
        )
    )
}
