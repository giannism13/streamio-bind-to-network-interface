package stremio.patches.bindnic.fingerprints

import app.morphe.patcher.Fingerprint

object OkHttpEngineCreateClientFingerprint : Fingerprint(
	returnType = "Lokhttp3/OkHttpClient;",
	parameters = listOf("Lio/ktor/client/plugins/HttpTimeoutConfig;"),
	custom = { method, _ ->
		method.definingClass == "Lio/ktor/client/engine/okhttp/OkHttpEngine;" &&
				method.name.contains("createOkHttpClient")
	}
)