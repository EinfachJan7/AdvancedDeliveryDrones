package de.cb.drones.resourcepack;

import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.file.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ResourcePackGenerator {

    private final JavaPlugin plugin;
    private final File modelsDir;
    private final File packZip;

    public ResourcePackGenerator(JavaPlugin plugin) {
        this.plugin = plugin;
        this.modelsDir = new File(plugin.getDataFolder(), "models");
        this.packZip = new File(plugin.getDataFolder(), "resourcepack.zip");
    }

    public void generate(Material material, int customModelData) {
        if (!modelsDir.exists()) {
            modelsDir.mkdirs();
            // Create a dummy readme
            try (FileWriter writer = new FileWriter(new File(modelsDir, "README.txt"))) {
                writer.write("Place your .json models and .png textures here.\n");
                writer.write("The plugin will automatically generate a resourcepack.zip on startup.\n");
            } catch (IOException ignored) {}
            return;
        }

        File[] files = modelsDir.listFiles();
        if (files == null || files.length == 0) {
            return;
        }

        boolean hasJson = false;
        String firstModelName = null;
        for (File file : files) {
            if (file.getName().endsWith(".json")) {
                hasJson = true;
                if (firstModelName == null) {
                    firstModelName = file.getName().replace(".json", "");
                }
            }
        }

        if (!hasJson) return;

        plugin.getLogger().info("Generating resourcepack.zip from models folder...");

        try (FileOutputStream fos = new FileOutputStream(packZip);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            // pack.mcmeta
            String mcmeta = """
            {
              "pack": {
                "pack_format": 15,
                "description": "AdvancedDeliveryDrones Models"
              }
            }
            """;
            addZipEntry(zos, "pack.mcmeta", mcmeta.getBytes());

            // Copy user files
            for (File file : files) {
                if (file.isDirectory()) continue;
                String name = file.getName();
                byte[] content = Files.readAllBytes(file.toPath());
                if (name.endsWith(".json")) {
                    addZipEntry(zos, "assets/minecraft/models/custom/" + name, content);
                } else if (name.endsWith(".png")) {
                    addZipEntry(zos, "assets/minecraft/textures/custom/" + name, content);
                }
            }

            // Generate override for the configured material
            if (firstModelName != null && material != null && customModelData > 0) {
                String matName = material.name().toLowerCase();
                String parentModel = material.isBlock() ? "minecraft:block/" + matName : "minecraft:item/generated";
                
                String overrideJson = String.format("""
                {
                  "parent": "%s",
                  "overrides": [
                    { "predicate": { "custom_model_data": %d }, "model": "custom/%s" }
                  ]
                }
                """, parentModel, customModelData, firstModelName);
                
                addZipEntry(zos, "assets/minecraft/models/item/" + matName + ".json", overrideJson.getBytes());
            }

            plugin.getLogger().info("Successfully generated resourcepack.zip");

        } catch (IOException e) {
            plugin.getLogger().severe("Failed to generate resource pack: " + e.getMessage());
        }
    }

    private void addZipEntry(ZipOutputStream zos, String path, byte[] content) throws IOException {
        ZipEntry entry = new ZipEntry(path);
        zos.putNextEntry(entry);
        zos.write(content);
        zos.closeEntry();
    }
}
