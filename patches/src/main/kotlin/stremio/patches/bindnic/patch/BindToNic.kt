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
		val method = ApplicationOnCreateFingerprint.method
		val insertIndex = method.implementation!!.instructions.size - 1

		val register = method.implementation!!.registerCount - 1

		method.addInstructions(
			insertIndex,
			"""
					const-string v$register, "${vpnInterface}"
                    invoke-static {p0, v$register}, Lstremio/morphe/extension/nicbinding/NicBinding;->onAppCreate(Landroid/app/Application;Ljava/lang/String;)V
				""".trimIndent()
		)

		// 2) Hook OkHttpClient.Builder.build() to configure + register clients
		val buildMethod = OkHttpEngineCreateClientFingerprint.method
		// Insert at the top of build():
		// call OkHttpHooks.configure(this)
		// (builder is p0)
		val insns = buildMethod.implementation!!.instructions

		val idx = insns.indexOfFirst {
			it.opcode.name.startsWith("INVOKE") &&
					it.toString().contains("newBuilder") &&
					it.toString().contains("OkHttpClient")
		}

		if (idx < 0) return@execute

		val moveIdx = idx + 1
		if (moveIdx >= insns.size) return@execute

		val moveInsn = buildMethod.getInstruction(moveIdx)

		if (!moveInsn.opcode.name.startsWith("MOVE_RESULT")) return@execute

		val reg = moveInsn.toString().substringAfter("move-result-object ").trim()

		buildMethod.addInstructions(
			moveIdx + 1,
			$$"""
            invoke-static {$reg}, Lstremio/morphe/extension/nicbinding/OkHttpHooks;->configure(Lokhttp3/OkHttpClient$Builder;)Lokhttp3/OkHttpClient$Builder;
            move-result-object $reg
            """.trimIndent()
		)

		val buildImpl = buildMethod.implementation ?: error("OkHttp build has no implementation")
		val buildInsns = buildImpl.instructions

		val retIndex = buildInsns.indexOfLast { it.opcode.name == "RETURN_OBJECT" }

		val retInsn = buildMethod.getInstruction(retIndex)
		val retReg = retInsn.toString().substringAfter("return-object ").trim()

		buildMethod.addInstructions(
			retIndex,
			"""
            invoke-static {$retReg}, Lstremio/morphe/extension/nicbinding/OkHttpHooks;->register(Lokhttp3/OkHttpClient;)V
            """.trimIndent()
		)
	}
}