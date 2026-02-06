import com.diffplug.gradle.spotless.BaseKotlinExtension

plugins {
	kotlin("multiplatform") version "2.3.20-Beta2"
	id("com.diffplug.spotless") version ("8.1.0")
}

group = "xyz.rafi67000.kotlin"

version = "1.0-SNAPSHOT"

repositories { mavenCentral() }

kotlin {
	linuxX64 {
		compilations["main"].cinterops {
			create("glfw3")
			create("spng")

			configureEach { extraOpts += listOf("-Xccall-mode", "direct") }
		}

		binaries { executable() }
	}

	sourceSets {
		linuxX64Main {
			dependencies { implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2") }
		}
	}
}

fun BaseKotlinExtension.apply() {
	ktfmt().kotlinlangStyle().configure { it.setRemoveUnusedImports(true) }
	leadingSpacesToTabs()
	endWithNewline()
}

spotless {
	kotlin {
		target("src/**/*.kt") // so spotless applies to multiplatform
		apply()
	}
	kotlinGradle { apply() }
}
