package stremio.patches.bindnic.fingerprints

import app.revanced.patcher.fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

val okHttpBuilderBuildFingerprint  = fingerprint {
	accessFlags(AccessFlags.PUBLIC)
	returns("Lokhttp3/OkHttpClient;")
	parameters()
	custom { method, _ ->
		method.definingClass == "Lokhttp3/OkHttpClient\$Builder;" &&
		method.name == "build"
	}
}