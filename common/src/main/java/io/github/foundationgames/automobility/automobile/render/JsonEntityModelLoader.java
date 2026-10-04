package io.github.foundationgames.automobility.automobile.render;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import io.github.foundationgames.automobility.Automobility;
import net.minecraft.IdentifierException;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class JsonEntityModelLoader {
    public static final Identifier ID = Automobility.rl("json_entity_models");
    private static final Gson GSON = new Gson();
    private static final Map<ModelLayerLocation, LayerDefinition> LAYERS = new HashMap<>();

    private JsonEntityModelLoader() {
    }

    public static void reload(ResourceManager resourceManager) {
        LAYERS.clear();

        FileToIdConverter.json("models/entity").listMatchingResources(resourceManager).forEach((rl, res) -> {
            try {
                var fullPath = rl.getPath().replaceFirst("models/entity/", "");
                var splitPath = fullPath.split("/");
                var dirs = new String[splitPath.length - 1];
                System.arraycopy(splitPath, 0, dirs, 0, dirs.length);
                var layerName = splitPath[splitPath.length - 1].replace(".json", "");
                var modelName = String.join("/", dirs);
                var layer = new ModelLayerLocation(Identifier.fromNamespaceAndPath(rl.getNamespace(), modelName), layerName);

                try (var in = res.open(); var reader = new InputStreamReader(in)) {
                    var json = GSON.fromJson(reader, JsonObject.class);
                    if (isAutomobilityModelFormat(json)) {
                        LAYERS.put(layer, readLayer(json));
                    }
                }
            } catch (IdentifierException | IOException ex) {
                Automobility.LOG.error("Could not load entity model {}", rl, ex);
            } catch (RuntimeException ex) {
                Automobility.LOG.error("Could not load entity model {}", rl, ex);
            }
        });
    }

    public static Optional<ModelPart> bakeLayer(ModelLayerLocation layer) {
        var definition = LAYERS.get(layer);
        return definition == null ? Optional.empty() : Optional.of(definition.bakeRoot());
    }

    private static LayerDefinition readLayer(JsonObject json) {
        var texture = json.getAsJsonObject("texture");
        int width = texture.get("width").getAsInt();
        int height = texture.get("height").getAsInt();

        var mesh = new MeshDefinition();
        var root = mesh.getRoot();
        var bones = json.getAsJsonObject("bones");
        for (var entry : bones.entrySet()) {
            addPart(root, entry.getKey(), entry.getValue().getAsJsonObject());
        }

        return LayerDefinition.create(mesh, width, height);
    }

    private static boolean isAutomobilityModelFormat(JsonObject json) {
        if (json == null || !json.has("texture") || !json.get("texture").isJsonObject()) {
            return false;
        }

        var texture = json.getAsJsonObject("texture");
        return texture.has("width") && texture.has("height")
                && json.has("bones") && json.get("bones").isJsonObject();
    }

    private static void addPart(PartDefinition parent, String name, JsonObject json) {
        var part = parent.addOrReplaceChild(name, readCubes(json), readPose(json.getAsJsonObject("transform")));

        if (json.has("children") && json.get("children").isJsonObject()) {
            for (var entry : json.getAsJsonObject("children").entrySet()) {
                addPart(part, entry.getKey(), entry.getValue().getAsJsonObject());
            }
        }
    }

    private static CubeListBuilder readCubes(JsonObject json) {
        var builder = CubeListBuilder.create();

        if (!json.has("cuboids") || !json.get("cuboids").isJsonArray()) {
            return builder;
        }

        for (var element : json.getAsJsonArray("cuboids")) {
            var cuboid = element.getAsJsonObject();
            var offset = readVec3(cuboid.getAsJsonArray("offset"), new Vector3f());
            var dimensions = readVec3(cuboid.getAsJsonArray("dimensions"), new Vector3f());
            var dilation = readVec3(cuboid.getAsJsonArray("dilation"), new Vector3f());
            var uv = cuboid.getAsJsonArray("uv");
            var name = cuboid.has("name") ? cuboid.get("name").getAsString() : "";
            boolean mirror = cuboid.has("mirror") && cuboid.get("mirror").getAsBoolean();

            if (uv == null || uv.size() < 2) {
                continue;
            }

            builder.texOffs(Math.round(uv.get(0).getAsFloat()), Math.round(uv.get(1).getAsFloat())).mirror(mirror);
            builder.addBox(name, offset.x(), offset.y(), offset.z(), dimensions.x(), dimensions.y(), dimensions.z(),
                    new CubeDeformation(dilation.x(), dilation.y(), dilation.z()));
        }

        return builder;
    }

    private static PartPose readPose(JsonObject json) {
        if (json == null) {
            return PartPose.ZERO;
        }

        var origin = readVec3(json.getAsJsonArray("origin"), new Vector3f());
        var rotation = readVec3(json.getAsJsonArray("rotation"), new Vector3f());
        return PartPose.offsetAndRotation(origin.x(), origin.y(), origin.z(), rotation.x(), rotation.y(), rotation.z());
    }

    private static Vector3f readVec3(JsonArray array, Vector3f fallback) {
        if (array == null || array.size() < 3) {
            return fallback;
        }

        return new Vector3f(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat());
    }
}
