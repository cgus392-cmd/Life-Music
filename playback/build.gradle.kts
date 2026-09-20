plugins {
    id("com.android.library")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    // Sin este plugin los @Serializable del ecualizador (SavedEQProfile,
    // ParametricEQBand, FilterType) no tenian serializador generado y guardar
    // un perfil tiraba la app con «Serializer for class ... is not found».
    // Se perdio al mover el EQ de :app a este modulo.
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.cglabs.lifemusic.playback"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    flavorDimensions += "variant"
    productFlavors {
        create("gms") { dimension = "variant" }
        create("foss") { dimension = "variant" }
    }
}
kotlin { jvmToolchain(21) }
dependencies {
    // Verificacion del DSP de separacion contra la referencia en Python.
    testImplementation(libs.junit)
    implementation(project(":core"))
    "gmsImplementation"(libs.cast.framework)
    api(libs.media3)
    api(libs.media3.session)
    api(libs.media3.hls)
    
    implementation(libs.hilt)
    ksp(libs.hilt.compiler)
}
