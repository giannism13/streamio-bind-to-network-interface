package stremio.patches.bindnic.fingerprints

import app.revanced.patcher.fingerprint
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

val applicationOnCreateFingerprint = fingerprint {
	accessFlags(AccessFlags.PUBLIC)
	returns("V")    //void
	parameters()
	opcodes(Opcode.INVOKE_SUPER)
	custom { _, classDef ->
		// Ensure Application subclass
		classDef.superclass == "Landroid/app/Application;"
	}
}