pluginManagement {
	repositories {
		google()
		mavenCentral()
		gradlePluginPortal()
	}
}

dependencyResolutionManagement {
	repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
	repositories {
		google()
		mavenCentral()
		// libadb-android solo está publicado en JitPack (permiso avanzado sin ordenador)
		maven { url = uri("https://jitpack.io") }
	}
}

rootProject.name = "RescateAccesible"
include(":app")
