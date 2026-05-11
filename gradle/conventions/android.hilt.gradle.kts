plugins {
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

dependencies {
    implementation(libs.bundles.hilt)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.compiler.androidx)
}
