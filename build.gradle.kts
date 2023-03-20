plugins {
    kotlin("jvm") version "1.8.0"
}

group = "com.grappenmaker"
version = "1.0"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(16)
}

tasks {
    jar {
        from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        manifest {
            attributes("Main-Class" to "com.grappenmaker.synacor.Synacor")
        }
    }

    compileKotlin {
        compilerOptions {
            freeCompilerArgs.add("-opt-in=kotlin.ExperimentalUnsignedTypes")
        }
    }
}