plugins {
    id("java")
}

group = "net.flamgop"
version = "1.0.0"

val lwjglNatives = Pair(
    System.getProperty("os.name")!!,
    System.getProperty("os.arch")!!
).let { (name, arch) ->
    when {
        arrayOf("Linux", "SunOS", "Unit").any { name.startsWith(it) } ->
            if (arrayOf("arm", "aarch64").any { arch.startsWith(it) }) "natives-linux${if (arch.contains("64") || arch.startsWith("armv8")) "-arm64" else "-arm32"}"
            else if (arch.startsWith("ppc")) "natives-linux-ppc64le"
            else if (arch.startsWith("riscv")) "natives-linux-riscv64"
            else "natives-linux"
        arrayOf("Windows").any { name.startsWith(it) } -> "natives-windows"
        else -> throw Error("Unrecognized or unsupported platform. Please set \"lwjglNatives\" manually")
    }
}

repositories {
    mavenCentral()
    maven("https://central.sonatype.com/repository/maven-snapshots")
}

dependencies {
    implementation(platform(libs.lwjgl.bom))

    implementation(libs.lwjgl)
    implementation(libs.lwjgl.assimp)
    implementation(libs.lwjgl.freetype)
    implementation(libs.lwjgl.glfw)
    implementation(libs.lwjgl.meshoptimizer)
    implementation(libs.lwjgl.msdfgen)
    implementation(libs.lwjgl.par)
    implementation(libs.lwjgl.stb)
    implementation(libs.lwjgl.vma)
    implementation(libs.lwjgl.vulkan)
    implementation("org.lwjgl:lwjgl:${libs.versions.lwjgl}:$lwjglNatives")
    implementation("org.lwjgl:lwjgl-assimp:${libs.versions.lwjgl}:$lwjglNatives")
    implementation("org.lwjgl:lwjgl-freetype:${libs.versions.lwjgl}:$lwjglNatives")
    implementation("org.lwjgl:lwjgl-glfw:${libs.versions.lwjgl}:$lwjglNatives")
    implementation("org.lwjgl:lwjgl-meshoptimizer:${libs.versions.lwjgl}:$lwjglNatives")
    implementation("org.lwjgl:lwjgl-msdfgen:${libs.versions.lwjgl}:$lwjglNatives")
    implementation("org.lwjgl:lwjgl-par:${libs.versions.lwjgl}:$lwjglNatives")
    implementation("org.lwjgl:lwjgl-stb:${libs.versions.lwjgl}:$lwjglNatives")
    implementation("org.lwjgl:lwjgl-vma:${libs.versions.lwjgl}:$lwjglNatives")

    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.graal.collections)

    implementation(libs.slf4j.api)
    implementation(libs.log4j.api)
    implementation(libs.log4j.core)
    implementation(libs.log4j.slf4j2.impl)
    compileOnly(libs.jetbrains.annotations)
}