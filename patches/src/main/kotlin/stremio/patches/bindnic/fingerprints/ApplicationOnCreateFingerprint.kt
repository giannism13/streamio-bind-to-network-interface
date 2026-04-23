package stremio.patches.bindnic.fingerprints

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodesFilter
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode


object ApplicationOnCreateFingerprint : Fingerprint(
	accessFlags = listOf(AccessFlags.PUBLIC),
	returnType = "V",    //void
	filters = OpcodesFilter.opcodesToFilters(Opcode.INVOKE_SUPER),
	custom = {_, classDef ->
		// Ensure Application subclass
		classDef.superclass == "Landroid/app/Application;"
	}
)