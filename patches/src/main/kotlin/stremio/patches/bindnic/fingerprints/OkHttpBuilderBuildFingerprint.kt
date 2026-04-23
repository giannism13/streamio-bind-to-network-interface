package stremio.patches.bindnic.fingerprints

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

object OkHttpBuilderBuildFingerprint : Fingerprint(
	accessFlags = listOf(AccessFlags.PUBLIC),
	returnType = "Lokhttp3/OkHttpClient;",
	custom = { method, _ ->
		method.definingClass == "Lokhttp3/OkHttpClient\$Builder;" &&
		method.name == "build"
	}
)