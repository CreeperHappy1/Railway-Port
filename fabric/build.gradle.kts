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

architectury.fabric()

loom {
    val common = project(":common")
    accessWidenerPath = common.loom.accessWidenerPath

    runs {
        create("datagen") {
            client()

            name = "Minecraft Data"
            vmArg("-Dfabric-api.datagen")
            vmArg("-Dfabric-api.datagen.output-dir=${common.file("src/generated/resources")}")
            vmArg("-Dfabric-api.datagen.modid=railways")
            vmArg("-Dporting_lib.datagen.existing_resources=${common.file("src/main/resources")}")

            environmentVariable("DATAGEN", "TRUE")
        }
    }
}

dependencies {
    implementation("net.fabricmc:fabric-loader:${"fabric_loader_version"()}")
    implementation("net.fabricmc.fabric-api:fabric-api:${"fabric_api_version"()}")

    // Create - dependencies are added transitively
    //implementation("com.simibubi.create:create-fabric-${"minecraft_version"()}:${"create_fabric_version"()}")
    implementation("maven.modrinth:create-fly:${"minecraft_version"()}-${"create_fly_version"()}")

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
}

operator fun String.invoke(): String {
    return rootProject.ext[this] as? String
        ?: throw IllegalStateException("Property $this is not defined")
}