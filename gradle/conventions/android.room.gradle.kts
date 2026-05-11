plugins {
    id("com.google.devtools.ksp")
    id("androidx.room")
}

dependencies {
    implementation(libs.bundles.room)
    ksp(libs.room.compiler)
}
