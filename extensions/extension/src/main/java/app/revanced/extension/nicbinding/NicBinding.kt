package app.revanced.extension.nicbinding

import android.app.Application
import android.content.Context
import android.net.*
import java.util.concurrent.atomic.AtomicBoolean

object NicBinding {
	@JvmField val isNetworkAllowed = AtomicBoolean(false)

	@Volatile private var cb: ConnectivityManager.NetworkCallback? = null
	@Volatile private var pinned: Network? = null

	@JvmStatic
	fun currentNetwork(): Network? = pinned

	/**
	 * Call from Application.onCreate() after super.onCreate().
	 * iface examples: "wlan0", "tun0"
	 */
	@JvmStatic
	fun onAppCreate(app: Application, iface: String) {
		start(app.applicationContext, iface)
	}

	@JvmStatic
	fun start(ctx: Context, iface: String) {
		val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

		// Restart-safe
		cb?.let { runCatching { cm.unregisterNetworkCallback(it) } }
		cb = null
		failClosed()

		val req = NetworkRequest.Builder()
			.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
			.build()

		cb = object : ConnectivityManager.NetworkCallback() {
			override fun onAvailable(n: Network) {
				if (cm.getLinkProperties(n)?.interfaceName == iface) bind(cm, n)
			}

			override fun onLinkPropertiesChanged(n: Network, lp: LinkProperties) {
				if (lp.interfaceName == iface) bind(cm, n)
				else if (pinned == n) failClosed()
			}

			override fun onLost(n: Network) {
				if (pinned == n) failClosed()
			}

			override fun onUnavailable() = failClosed()

			private fun bind(cm: ConnectivityManager, n: Network) {
				// Already bound + allowed
				if (pinned == n && isNetworkAllowed.get()) return

				@Suppress("DEPRECATION")
				val ok = ConnectivityManager.setProcessDefaultNetwork(n)

				if (ok) {
					pinned = n
					isNetworkAllowed.set(true)
					OkHttpHooks.onPinnedNetworkAvailable() // optional “refresh” hook
				} else {
					failClosed()
				}
			}
		}

		cm.registerNetworkCallback(req, cb!!)
		// requestNetwork(req, cb!!) is optional; keep minimal and non-pushy.
	}

	private fun failClosed() {
		pinned = null
		isNetworkAllowed.set(false)

		// IMPORTANT: don't restore default routing; just block and kill existing OkHttp work.
		OkHttpHooks.onFailClosed()
	}
}