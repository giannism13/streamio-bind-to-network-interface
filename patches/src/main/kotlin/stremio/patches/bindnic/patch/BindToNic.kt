package stremio.patches.bindnic.patch

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption
import stremio.patches.shared.Constants.STREMIO_BIND_NIC_COMPATIBILITY
import stremio.patches.bindnic.fingerprints.ApplicationOnCreateFingerprint
import stremio.patches.bindnic.fingerprints.OkHttpEngineCreateClientFingerprint

@Suppress("unused")
val bindToNic = bytecodePatch(
	name = "Bind to a network interface",
	description = "Routes all network traffic through a specific network interface",
	default = true
) {
	compatibleWith(STREMIO_BIND_NIC_COMPATIBILITY)

	extendWith("extensions/extension.mpe")

	val vpnInterface by stringOption(
		"NIC",
		"tun0",
		description = "The network interface to bind to",
		required = true
	)

	execute {

		// ----------------------------
		// 1) Hook Application.onCreate safely
		// ----------------------------
		val method = ApplicationOnCreateFingerprint.method
		val impl = method.implementation ?: return@execute
		val insns = impl.instructions

		// Insert BEFORE final return (more stable than size - 1)
		val insertIndex = insns.indexOfLast {
			it.opcode.name.startsWith("RETURN")
		}.takeIf { it >= 0 } ?: return@execute

		method.addInstructions(
			insertIndex,
			"""
			const-string v0, "$vpnInterface"
			invoke-static {p0, v0}, Lstremio/morphe/extension/nicbinding/NicBinding;->onAppCreate(Landroid/app/Application;Ljava/lang/String;)V
		""".trimIndent()
		)

		// ----------------------------
		// 2) Hook OkHttpClient.Builder creation safely
		// ----------------------------
		val buildMethod = OkHttpEngineCreateClientFingerprint.method
		val buildImpl = buildMethod.implementation ?: return@execute
		val buildInsns = buildImpl.instructions

		// Find newBuilder() call (loose but stable match)
		val idx = buildInsns.indexOfFirst {
			it.opcode.name.startsWith("INVOKE") &&
					it.toString().contains("newBuilder")
		}.takeIf { it >= 0 } ?: return@execute

		val moveIdx = idx + 1
		if (moveIdx >= buildInsns.size) return@execute

		val moveInsn = buildMethod.getInstruction(moveIdx)
		if (!moveInsn.opcode.name.startsWith("MOVE_RESULT")) return@execute

		val builderReg = moveInsn.toString()
			.substringAfter("move-result-object ")
			.trim()

		// Inject configure() after builder creation
		buildMethod.addInstructions(
			moveIdx + 1,
			$$"""
			invoke-static {$builderReg}, Lstremio/morphe/extension/nicbinding/OkHttpHooks;->configure(Lokhttp3/OkHttpClient$Builder;)Lokhttp3/OkHttpClient$Builder;
			move-result-object $builderReg
			""".trimIndent()
		)

		// ----------------------------
		// 3) Hook final OkHttpClient return safely
		// ----------------------------
		val retIndex = buildInsns.indexOfLast {
			it.opcode.name == "RETURN_OBJECT"
		}.takeIf { it >= 0 } ?: return@execute

		val retInsn = buildMethod.getInstruction(retIndex)
		val retReg = retInsn.toString()
			.substringAfter("return-object ")
			.trim()

		buildMethod.addInstructions(
			retIndex,
			"""
			invoke-static {$retReg}, Lstremio/morphe/extension/nicbinding/OkHttpHooks;->register(Lokhttp3/OkHttpClient;)V
			""".trimIndent()
		)
	}
}