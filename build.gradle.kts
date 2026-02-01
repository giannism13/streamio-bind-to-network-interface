import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

subprojects {
	tasks.withType<KotlinCompile>().configureEach {
		compilerOptions {
			freeCompilerArgs.add("-Xcontext-parameters")
		}
	}
}
