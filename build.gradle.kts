/*
 * Steam 'n' Rails
 * Copyright (c) 2022-2024 The Railways Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import me.modmuss50.mpp.ModPublishExtension
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.task.RemapJarTask
import org.gradle.configurationcache.extensions.capitalized
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.Deflater
import dev.ithundxr.silk.ChangelogText
import me.modmuss50.mpp.ReleaseType

plugins {
    java
    `maven-publish`
    id("net.fabricmc.fabric-loom") version "1.14.+"
    id("me.modmuss50.mod-publish-plugin") version "0.7.4" // https://github.com/modmuss50/mod-publish-plugin
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("dev.ithundxr.silk") version "0.11.15" // https://github.com/IThundxr/silk
    id("net.kyori.blossom") version "2.1.0" // https://github.com/KyoriPowered/blossom
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.8" // https://github.com/JetBrains/gradle-idea-ext-plugin
}

loom {
    accessWidenerPath = file("src/main/resources/railways.accesswidener")

    runs {
        create("datagen") {
            client()

            name = "Minecraft Data"
            vmArg("-Dfabric-api.datagen")
            vmArg("-Dfabric-api.datagen.output-dir=${file("src/generated/resources")}")
            vmArg("-Dfabric-api.datagen.modid=railways")
            vmArg("-Dporting_lib.datagen.existing_resources=${file("src/main/resources")}")

            environmentVariable("DATAGEN", "TRUE")
        }
    }
}

println("Steam 'n' Rails v${"mod_version"()}")

val isRelease = System.getenv("RELEASE_BUILD")?.toBoolean() ?: false
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toInt()
// whether dev mixins should be stripped, even if it's not a release build
val removeDevMixinAnyway = System.getenv("REMOVE_DEV_MIXIN_ANYWAY")?.toBoolean() ?: false
// whether the build should include dev commands, even in a non-dev environment
val includeDevCommands = !isRelease && System.getenv("INCLUDE_DEV_COMMANDS")?.toBoolean() ?: false
val gitHash = "\"${calculateGitHash() + (if (hasUnstaged()) "-modified" else "")}\""

if (!isRelease && removeDevMixinAnyway) {
    println("Removing dev mixins, even though it's not a release build")
}

if (includeDevCommands) {
    println("Including dev commands in build")
}

extra["gitHash"] = gitHash
extra["includeDevCommands"] = includeDevCommands
//
//architectury {
//    minecraft = "minecraft_version"()
//}

dependencies {
    // We depend on fabric loader here to use the fabric @Environment annotations and get the mixin dependencies
    // Do NOT use other classes from fabric loader
    implementation("net.fabricmc:fabric-loader:${"fabric_loader_version"()}")
    // Compile against Create Fabric in common
    // beware of differences across platforms!
    // dependencies must also be pulled in to minimize problems, from remapping issues to compile errors.
    // All dependencies except Flywheel and Registrate are NOT safe to use!
    // Flywheel and Registrate must also be used carefully due to differences.
    //compileOnly("com.simibubi.create:create-fabric-${"minecraft_version"()}:${"create_fabric_version"()}")
    implementation("maven.modrinth:create-fly:${"minecraft_version"()}-${"create_fly_version"()}")

    // required for proper remapping and compiling
    compileOnly("net.fabricmc.fabric-api:fabric-api:${"fabric_api_version"()}")

    // JourneyMap compat
    compileOnly("info.journeymap:journeymap-api:${"journeymap_api_version"()}-fabric-SNAPSHOT")

    compileOnly("de.maxhenkel.voicechat:voicechat-api:${"voicechat_api_version"()}")
    compileOnly("maven.modrinth:simple-voice-chat:fabric-${"voicechat_version"()}")

    compileOnly(annotationProcessor("io.github.llamalad7:mixinextras-common:${"mixin_extras_version"()}")!!)

    // Fabric ASM (enum extension etc)
    implementation("com.github.Chocohead:Fabric-ASM:v2.3") {
        exclude (group = "net.fabricmc.fabric-api")
    }

    include("com.github.Chocohead:Fabric-ASM:v2.3")

    // Development QOL
    localRuntime("maven.modrinth:lazydfu:${"lazydfu_version"()}")
    localRuntime("com.terraformersmc:modmenu:${"modmenu_version"()}")

    // compileOnly("dev.emi:emi-fabric:${"emi_version"()}:api") { isTransitive = false }
    // localRuntime("dev.emi:emi-fabric:${"emi_version"()}")

    localRuntime("maven.modrinth:journeymap:${"journeymap_version"()}-fabric") // Test with JourneyMap in dev
    localRuntime("info.journeymap:journeymap-api:${"journeymap_api_version"()}-fabric-SNAPSHOT") // API is a JiJ on fabric, add manually

    compileOnly("info.journeymap:journeymap-api:${"journeymap_api_version"()}-fabric-SNAPSHOT") // for some reason this is needed explicitly

    compileOnly("de.maxhenkel.voicechat:voicechat-api:${"voicechat_api_version"()}")

    if ("enable_simple_voice_chat"().toBoolean()) {
        localRuntime("maven.modrinth:simple-voice-chat:fabric-${"voicechat_version"()}")
    }

    // mod compat for tracks
    if ("enable_hexcasting"().toBoolean()) {
        localRuntime("at.petra-k.paucal:paucal-fabric-${"minecraft_version"()}:${"paucal_version"()}")
        localRuntime("at.petra-k.hexcasting:hexcasting-fabric-${"minecraft_version"()}:${"hexcasting_version"()}")
        localRuntime("vazkii.patchouli:Patchouli:${"minecraft_version"()}-${"patchouli_version"()}-FABRIC")
    }

    if ("enable_byg"().toBoolean()) {
        localRuntime("maven.modrinth:biomesyougo:${"byg_version"()}-fabric")
        localRuntime("maven.modrinth:terrablender:${"terrablender_version_fabric"()}")
        localRuntime("maven.modrinth:geckolib:${"geckolib_version_fabric"()}")
        localRuntime("maven.modrinth:corgilib:${"corgilib_version_fabric"()}")
    }

    if ("enable_natures_spirit"().toBoolean()) {
        localRuntime("maven.modrinth:natures-spirit:${"natures_spirit_version"()}")
    }

    if ("enable_tweakeroo"().toBoolean()) {
        localRuntime("curse.maven:tweakeroo-297344:${"tweakeroo_version"()}")
        localRuntime("curse.maven:malilib-303119:${"malilib_version"()}")
    }

    if ("enable_sodium_rubidium"().toBoolean()) {
        localRuntime("maven.modrinth:sodium:${"sodium_version"()}")
        localRuntime("org.joml:joml:1.10.2")
        localRuntime("maven.modrinth:indium:${"indium_version"()}")
    }
    if ("enable_iris"().toBoolean()) {
        localRuntime("maven.modrinth:iris:${"iris_version"()}")
        localRuntime("org.anarres:jcpp:1.4.14")
        localRuntime("io.github.douira:glsl-transformer:2.0.0-pre13")
    }

    compileOnly(annotationProcessor("io.github.llamalad7:mixinextras-common:${"mixin_extras_version"()}")!!)!!
    implementation(include("io.github.llamalad7:mixinextras-fabric:${"mixin_extras_version"()}")!!)!!

    "minecraft"("com.mojang:minecraft:${"minecraft_version"()}")
}

sourceSets.main {
    resources { // include generated resources in resources
        srcDir("src/generated/resources")
        exclude(".cache/**")
        exclude("assets/create/**")
    }
    blossom.javaSources {
        property("version", "mod_version"())
        property("gitCommit", rootProject.extra["gitHash"].toString())
        property("includeDevCommands", rootProject.extra["includeDevCommands"].toString())
    }
}

apply(plugin = "java")
//    apply(plugin = "architectury-plugin")
apply(plugin = "maven-publish")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

base.archivesName.set("archives_base_name"())
group = "maven_group"()

// Formats the mod version to include the loader, Minecraft version, and build number (if present)
// example: 1.0.0+fabric-1.19.2-build.100 (or -local)
val build = buildNumber?.let { "-build.${it}" } ?: "-local"

var gitBranchLabel = "";
if ("mod_version"().endsWith("-alpha")) {
    // gitBranchLabel should be "-" + the current git branch (replacing any slashes with underscores)
    gitBranchLabel = "-" + calculateGitBranch().replace("/", "_")
}

version = "${"mod_version"()}${gitBranchLabel}+${project.name}-mc${"minecraft_version"() + if (isRelease) "" else build}"

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

java {
    withSourcesJar()
}


apply(plugin = "net.fabricmc.fabric-loom")
apply(plugin = "net.kyori.blossom")

setupRepositories()

val capitalizedName = project.name.replaceFirstChar { it.uppercase() }

configurations.configureEach {
    resolutionStrategy {
        force("net.fabricmc:fabric-loader:${"fabric_loader_version"()}")
    }
}



publishing {
    publications {
        create<MavenPublication>("maven${capitalizedName}") {
            artifactId = "${"archives_base_name"()}-${project.name}-${"minecraft_version"()}"
            from(components["java"])
        }
    }

    repositories {
        val mavenToken = System.getenv("MAVEN_TOKEN")
        val maven = if (isRelease) "releases" else "snapshots"
        if (mavenToken != null && mavenToken.isNotEmpty()) {
            maven {
                url = uri("https://maven.ithundxr.dev/${maven}")
                credentials {
                    username = "railways-github"
                    password = mavenToken
                }
            }
        }
    }
}

apply(plugin = "me.modmuss50.mod-publish-plugin")

//val common: Configuration by configurations.creating
val development = configurations.maybeCreate("development${capitalizedName}")

//configurations {
//    compileOnly.get().extendsFrom(common)
//    runtimeOnly.get().extendsFrom(common)
//    development.extendsFrom(common)
//}

tasks.processResources {
    // include packs
    from(file("src/main/resources")) {
        include("resourcepacks/")
    }

    // Trim -build.X+mcX.XX.X from version string
    //val createFabricVersion: String = Regex("(\\d+\\.\\d+\\.\\d+-\\w)").find("create_fabric_version"())?.value.toString()

    // set up properties for filling into metadata
    val properties = mapOf(
            "version" to version,
            "minecraft_version" to "minecraft_version"(),
            "fabric_api_version" to "fabric_api_version"(),
            "fabric_loader_version" to "fabric_loader_version"(),
            "voicechat_api_version" to "voicechat_api_version"(),
            "create_fly_version" to "create_fly_version"()
    )

    inputs.properties(properties)

    filesMatching(listOf("fabric.mod.json", "META-INF/mods.toml")) {
        expand(properties)
    }

    // must be part of primary mod to be findable
    exclude("resourcepacks/")

    // don't add development or to-do files into built jar
    exclude("**/*.bbmodel", "**/*.lnk", "**/*.xcf", "**/*.md", "**/*.txt", "**/*.blend", "**/*.blend1")

}

tasks.jar {
    archiveClassifier = "dev"

    manifest {
        attributes(mapOf("Git-Hash" to gitHash))
    }
}


components.getByName<AdhocComponentWithVariants>("java") {
    withVariantsFromConfiguration(project.configurations["shadowRuntimeElements"]) {
        skip()
    }
}

val releaseType =
    if (version.toString().contains("alpha")) {
        ReleaseType.ALPHA;
    } else if (version.toString().contains("beta")) {
        ReleaseType.BETA;
    } else {
        ReleaseType.STABLE;
    }
configure<ModPublishExtension> {
    //file.set(project.tasks.named("jar").get()) //FIXME: task -> file
    version.set(project.version.toString())
    changelog = ChangelogText.getChangelogText(rootProject).toString()
    type = releaseType
    displayName = "Steam 'n' Rails ${"mod_version"()} $capitalizedName ${"minecraft_version"()} C${"create_display_version"()}"
    modLoaders.add("fabric")
    modLoaders.add("quilt")

    val createVersionType = "create-fly"
    curseforge {
        projectId = "curseforge_id"()
        accessToken = providers.environmentVariable("CURSEFORGE_TOKEN")
        minecraftVersions.add("minecraft_version"())

        requires {
            slug = createVersionType
        }

        requires("fabric-api")
    }

    modrinth {
        projectId = "modrinth_id"()
        accessToken = providers.environmentVariable("MODRINTH_TOKEN")
        minecraftVersions.add("minecraft_version"())

        requires {
            slug = createVersionType
        }

        requires("fabric-api")
    }
}

fun transformJar(jar: File) {
    val contents = linkedMapOf<String, ByteArray>()
    JarFile(jar).use {
        it.entries().asIterator().forEach { entry ->
            if (!entry.isDirectory) {
                contents[entry.name] = it.getInputStream(entry).readAllBytes()
            }
        }
    }

    jar.delete()

    JarOutputStream(jar.outputStream()).use { out ->
        out.setLevel(Deflater.BEST_COMPRESSION)
        contents.forEach { var (name, data) = it
            if(name.startsWith("architectury_inject_${project.name}_common"))
                return@forEach

            if (name.endsWith(".json") || name.endsWith(".mcmeta")) {
                data = (JsonOutput.toJson(JsonSlurper().parse(data)).toByteArray())
            } else if (name.endsWith(".class")) {
                data = transformClass(data)
            }

            out.putNextEntry(JarEntry(name))
            out.write(data)
            out.closeEntry()
        }
        out.finish()
        out.close()
    }
}

fun transformClass(bytes: ByteArray): ByteArray {
    val node = ClassNode()
    ClassReader(bytes).accept(node, 0)

    // Remove Methods & Field Annotated with @DevEnvMixin
    node.methods.removeIf { methodNode: MethodNode -> removeIfDevMixin(node.name, methodNode.visibleAnnotations) }
    // Disabled as I don't feel ok with people being able to remove these
    //node.fields.removeIf { fieldNode: FieldNode -> removeIfDevMixin(fieldNode.visibleAnnotations) }

    return ClassWriter(0).also { node.accept(it) }.toByteArray()
}

fun removeIfDevMixin(nodeName: String, visibleAnnotations: List<AnnotationNode>?): Boolean {
    // Don't remove methods if it's not a GHA build/Release build
    if (!removeDevMixinAnyway && buildNumber == null && !nodeName.lowercase(Locale.ROOT).matches(Regex(".*\\/mixin\\/.*Mixin")))
        return false

    if (visibleAnnotations != null) {
        for (annotationNode in visibleAnnotations) {
            if (annotationNode.desc == "Lcom/railwayteam/railways/annotation/mixin/DevEnvMixin;")
                return true
        }
    }

    return false
}

fun <T> getValueFromAnnotation(annotation: AnnotationNode?, key: String): T? {
    var getNextValue = false

    if (annotation?.values == null) {
        return null
    }

    // Keys and value are stored in successive pairs, search for the key and if found return the following entry
    for (value in annotation.values) {
        if (getNextValue) {
            @Suppress("UNCHECKED_CAST")
            return value as T
        }
        if (value == key) {
            getNextValue = true
        }
    }

    return null
}

tasks.register("railwaysPublish") {
    when (val platform = System.getenv("PLATFORM")) {
        "both" -> {
            dependsOn(tasks.build, ":fabric:publish", ":forge:publish", ":common:publish", ":fabric:publishMods", ":forge:publishMods")
        }
        "fabric", "forge" -> {
            dependsOn("${platform}:build", "${platform}:publish", "${platform}:publishMods")
        }
    }
}

fun Project.setupRepositories() {
    repositories {
        mavenCentral()
        maven("https://modmaven.dev/") // flywheel fabric
        maven("https://maven.shedaniel.me/") // Cloth Config, REI
        maven("https://maven.blamejared.com/") // JEI, Hex Casting
        exclusiveMaven("https://maven.parchmentmc.org", "org.parchmentmc.data") // Parchment mappings
        exclusiveMaven("https://maven.quiltmc.org/repository/release", "org.quiltmc") // Quilt Mappings
        maven("https://jm.gserv.me/repository/maven-public/") // JourneyMap API
        exclusiveMaven("https://api.modrinth.com/maven", "maven.modrinth") // LazyDFU, JourneyMap, Create fly
        exclusiveMaven("https://cursemaven.com", "curse.maven")
        maven("https://maven.theillusivec4.top/") // Curios
        maven("https://maven.tterrag.com/") { // Flywheel, Registrate, Create
            content {
                includeGroup("com.simibubi.create")
                includeGroup("com.tterrag.registrate")
                includeGroup("com.jozufozu.flywheel")
            }
        }
        maven("https://maven.maxhenkel.de/repository/public") // Simple Voice Chat
        maven("https://maven.jamieswhiteshirt.com/libs-release") // Reach Entity Attributes
        exclusiveMaven("https://thedarkcolour.github.io/KotlinForForge/", "thedarkcolour") // KFF (Hex Casting dependency)
        maven("https://maven.terraformersmc.com/releases/") // Mod Menu, EMI
        maven("https://mvn.devos.one/snapshots/") // Porting Lib, Forge Tags, Milk Lib, Registrate Fabric
        maven("https://mvn.devos.one/releases/") // Porting Lib
        maven("https://maven.cafeteria.dev/releases") // Fake Player API
        maven("https://raw.githubusercontent.com/Fuzss/modresources/main/maven/") // forge config api port
        exclusiveMaven("https://maven.ladysnake.org/releases", "dev.onyxstudios.cardinal-components-api") // Cardinal Components (Hex Casting dependency)
        maven("https://jitpack.io/") { // Mixin Extras, Fabric ASM
            content {
                includeGroupByRegex("com.github.*")
            }
        }
    }
}

fun calculateGitHash(): String {
    try {
        val output = providers.exec {
            commandLine("git", "rev-parse", "HEAD")
        }
        return output.standardOutput.asText.get().trim()
    } catch(_: Throwable) {
        return "unknown"
    }
}

fun calculateGitBranch(): String {
    try {
        val output = providers.exec {
            commandLine("git", "rev-parse", "--abbrev-ref", "HEAD")
        }
        return output.standardOutput.asText.get().trim()
    } catch(_: Throwable) {
        return "unknown"
    }
}

fun hasUnstaged(): Boolean {
    try {
        val output = providers.exec {
            commandLine("git", "status", "--porcelain")
        }
        val result = output.standardOutput.asText.get().replace(Regex("M gradlew(\\.bat)?"), "").trimEnd()
        if (result.isNotEmpty())
            println("Found stageable results:\n${result}\n")
        return result.isNotEmpty()
    }  catch(_: Throwable) {
        return false
    }
}

//fun Project.architectury(action: Action<ArchitectPluginExtension>) {
//    action.execute(this.extensions.getByType<ArchitectPluginExtension>())
//}

fun RepositoryHandler.exclusiveMaven(url: String, vararg groups: String) {
    exclusiveContent {
        forRepository { maven(url) }
        filter {
            groups.forEach {
                includeGroup(it)
            }
        }
    }
}

operator fun String.invoke(): String {
    return rootProject.ext[this] as? String
        ?: throw IllegalStateException("Property $this is not defined")
}

