package stremio.patches.bindnic.fingerprints

import app.morphe.patcher.Fingerprint

object OkHttpBuilderBuildFingerprint : Fingerprint(
	returnType = "Lokhttp3/OkHttpClient;",
	custom = { method, _ ->
		method.name == "build" &&
				method.parameterTypes.isEmpty() &&
				method.implementation?.instructions?.any {
					it.toString().contains("OkHttpClient")
				} == true
	}
)