import java.util.Properties

plugins {
	id("com.android.application")
	id("org.jetbrains.kotlin.android")
}

// Servidor de actualizaciones: ahí van version.json y RescateAccesible_v<versión>.apk.
// Se puede cambiar en Ajustes; para compilar con otro: gradle assembleRelease -PurlActualizaciones=https://...
val urlActualizaciones = (project.findProperty("urlActualizaciones") as String?) ?: "https://xebolax.synology.me/RescateAccesible"

// eSpeak NG (GPL-3.0-or-later) como segunda voz incluida. Para compilar sin él: -PincluirEspeak=false
// (entonces la app solo lleva código Apache 2.0 y puede distribuirse bajo esa licencia).
val incluirEspeak = (project.findProperty("incluirEspeak") as String?)?.toBoolean() ?: true

// Firma propia guardada en la carpeta "firma" (la crea compilar.bat).
val propiedadesFirma = Properties().apply {
	val archivo = rootProject.file("firma/firma.properties")
	if (archivo.exists()) archivo.inputStream().use { load(it) }
}

android {
	namespace = "com.xebolax.rescate"
	compileSdk = 35

	defaultConfig {
		applicationId = "com.xebolax.rescate"
		// Android 7.0: icono, mosaico de ajustes rápidos y voz propia. El atajo de las teclas de
		// volumen y el botón de accesibilidad existen desde Android 8.0 (se comprueba en tiempo de ejecución).
		minSdk = 24
		targetSdk = 35
		versionCode = 2
		versionName = "1.0.1"

		buildConfigField("String", "URL_ACTUALIZACIONES", "\"$urlActualizaciones\"")
		buildConfigField("boolean", "INCLUIR_ESPEAK", incluirEspeak.toString())
		// Repositorio de GitHub: respaldo del actualizador si el servidor propio falla
		buildConfigField("String", "REPO_GITHUB", "\"hxebolax/RescateAccesible\"")
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

		// Pico (la voz de rescate) es código nativo: todos los procesadores (pesa poco)
		ndk {
			abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
		}
		externalNativeBuild {
			cmake {
				arguments += listOf("-DANDROID_STL=c++_static", "-DINCLUIR_ESPEAK=${if (incluirEspeak) "ON" else "OFF"}")
			}
		}
	}

	sourceSets {
		getByName("main") {
			// Los datos de eSpeak van aparte: solo se empaquetan si se incluye eSpeak
			if (incluirEspeak) assets.srcDirs("src/main/assets-espeak")
		}
	}

	ndkVersion = "27.2.12479018"

	externalNativeBuild {
		cmake {
			path = file("src/main/cpp/CMakeLists.txt")
			version = "3.22.1"
		}
	}

	// Idiomas de la interfaz (Android 13+ los ofrece en Ajustes del sistema > Idioma de la app)
	androidResources {
		generateLocaleConfig = true
	}

	signingConfigs {
		if (propiedadesFirma.isNotEmpty()) {
			create("propia") {
				storeFile = rootProject.file(propiedadesFirma.getProperty("archivo"))
				storePassword = propiedadesFirma.getProperty("clave")
				keyAlias = propiedadesFirma.getProperty("alias")
				keyPassword = propiedadesFirma.getProperty("clave")
			}
		}
	}

	buildTypes {
		debug {
			// Versión de pruebas: se instala junto a la normal
			applicationIdSuffix = ".pruebas"
			versionNameSuffix = "-pruebas"
		}
		release {
			isMinifyEnabled = false
			signingConfig = signingConfigs.findByName("propia") ?: signingConfigs.getByName("debug")
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}

	buildFeatures {
		buildConfig = true
	}

	packaging {
		resources.excludes += listOf("META-INF/*.kotlin_module", "META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/DEPENDENCIES", "META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/BC1024KE.*", "META-INF/BC2048KE.*")
		// Las bibliotecas nativas sin comprimir y alineadas: se cargan directamente del APK
		jniLibs.useLegacyPackaging = false
	}

	testOptions {
		unitTests.isReturnDefaultValues = true
	}

	lint {
		abortOnError = false
		// WRITE_SECURE_SETTINGS es opcional: lo concede el usuario con adb, depuración inalámbrica o root
		disable += listOf("ProtectedPermissions", "MissingTranslation")
	}
}

dependencies {
	implementation("androidx.core:core-ktx:1.15.0")
	implementation("androidx.appcompat:appcompat:1.7.0")
	implementation("androidx.activity:activity-ktx:1.9.3")
	implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
	implementation("androidx.recyclerview:recyclerview:1.3.2")
	implementation("androidx.preference:preference-ktx:1.2.1")
	implementation("com.google.android.material:material:1.12.0")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

	// ADB inalámbrico para concederse el permiso avanzado sin ordenador (Apache-2.0 o GPL-3.0-or-later; arrastra spake2-android, LGPL-3.0, y bcprov)
	implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
	// TLS 1.3 propio: libadb lo necesita antes de Android 10 y evita API ocultas después (Apache 2.0)
	implementation("org.conscrypt:conscrypt-android:2.5.3")
	// Certificado X.509 de la app para el emparejamiento ADB (licencia MIT de Bouncy Castle)
	implementation("org.bouncycastle:bcpkix-jdk15to18:1.81")

	testImplementation("junit:junit:4.13.2")
	testImplementation("org.json:json:20240303")
	androidTestImplementation("androidx.test:runner:1.6.2")
	androidTestImplementation("androidx.test:core:1.6.1")
	androidTestImplementation("androidx.test.ext:junit:1.2.1")
	androidTestImplementation("com.google.android.apps.common.testing.accessibility.framework:accessibility-test-framework:4.1.1")
	androidTestImplementation("com.google.guava:guava:33.3.1-android")
}

kotlin {
	compilerOptions {
		jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
	}
}
