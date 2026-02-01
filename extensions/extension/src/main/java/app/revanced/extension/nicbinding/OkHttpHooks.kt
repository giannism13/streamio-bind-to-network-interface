package app.revanced.extension.nicbinding

import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap

object OkHttpHooks {
	// Weak-ish registry (we can't use java.lang.ref.WeakHashMap safely across all API levels with concurrency)
	// We'll keep a set of identity hashes mapped to clients; it can grow a bit, but most apps build few clients.
	private val clients = ConcurrentHashMap<Int, OkHttpClient>()

	@JvmStatic
	fun register(client: OkHttpClient) {
		clients[System.identityHashCode(client)] = client
	}

	@JvmStatic
	fun onFailClosed() {
		// Cancel and evict so in-flight/pool doesn't keep going.
		// Best-effort; any exceptions are swallowed.
		for (c in clients.values) {
			runCatching { c.dispatcher.cancelAll() }
			runCatching { c.connectionPool.evictAll() }
		}
	}

	@JvmStatic
	fun onPinnedNetworkAvailable() {
		// Optional: you could cancel/evict too to force fresh connections on the pinned network.
		// Keeping it no-op is fine; enable if you want "reconnect cleanly" behavior.
		// for (c in clients.values) { ... }
	}

	/**
	 * Called from patched OkHttpClient.Builder.build() to enforce leak prevention.
	 *
	 * - Adds a kill switch interceptor (once)
	 * - Sets DNS to pinned DNS
	 * - Sets socketFactory to pinned network (best-effort)
	 */
	@JvmStatic
	fun configure(builder: OkHttpClient.Builder): OkHttpClient.Builder {
		// Add interceptor once
		if (!hasKillSwitch(builder)) {
			builder.addInterceptor(KillSwitchInterceptor)
		}

		// DNS always pinned
		builder.dns(PinnedDns)

		// Best-effort bind sockets to pinned network
		NicBinding.currentNetwork()?.let { n ->
			runCatching { builder.socketFactory(n.socketFactory) }
		}

		return builder
	}

	// NOTE: OkHttpClient.Builder doesn't expose interceptors list directly in a stable public API.
	// We’ll keep this as "always add" in patch unless we check via reflection.
	// For safety, we implement reflective check; if it fails, we just add (duplicate interceptor is annoying but functional).
	private fun hasKillSwitch(builder: OkHttpClient.Builder): Boolean {
		return runCatching {
			val f = builder.javaClass.getDeclaredField("interceptors")
			f.isAccessible = true
			val list = f.get(builder) as? List<*>
			list?.any { it === KillSwitchInterceptor } == true
		}.getOrDefault(false)
	}

	private object KillSwitchInterceptor : Interceptor {
		override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
			if (!NicBinding.isNetworkAllowed.get()) {
				throw IOException("Blocked: pinned network unavailable")
			}
			return chain.proceed(chain.request())
		}
	}

	private object PinnedDns : Dns {
		override fun lookup(hostname: String): List<InetAddress> {
			val n = NicBinding.currentNetwork()
				?: throw UnknownHostException("No pinned network")
			return n.getAllByName(hostname).toList()
		}
	}
}