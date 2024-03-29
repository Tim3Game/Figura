package net.blancworks.figura.models.parsers;

import com.google.gson.*;
import net.blancworks.figura.LocalPlayerData;
import net.blancworks.figura.models.CustomModel;
import net.blancworks.figura.models.CustomModelPart;
import net.blancworks.figura.models.CustomModelPartCuboid;
import net.blancworks.figura.models.CustomModelPartMesh;
import net.minecraft.client.util.math.Vector3f;
import net.minecraft.client.util.math.Vector4f;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BlockbenchModelDeserializer implements JsonDeserializer<CustomModel> {


    @Override
    public CustomModel deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {

        CustomModel retModel = new CustomModel();

        JsonObject root = json.getAsJsonObject();
        JsonObject meta = root.get("meta").getAsJsonObject();
        String name = root.get("name").getAsString();
        JsonObject resolution = root.get("resolution").getAsJsonObject();
        JsonArray elements = root.get("elements").getAsJsonArray();
        JsonArray outliner = root.get("outliner").getAsJsonArray();
        JsonArray textures = root.get("textures").getAsJsonArray();

        retModel.texWidth = resolution.get("width").getAsFloat();
        retModel.texHeight = resolution.get("height").getAsFloat();

        HashMap<UUID, JsonObject> elementsByUuid = sortElements(elements);
        HashMap<UUID, CustomModelPart> parsedParts = new HashMap<UUID, CustomModelPart>();


        //Parse out custom model parts from json objects.
        for (Map.Entry<UUID, JsonObject> entry : elementsByUuid.entrySet()) {
            UUID id = entry.getKey();
            JsonObject obj = entry.getValue();

            parsedParts.put(id, parseElement(obj, retModel));
        }

        for (JsonElement element : outliner) {
            if (element.isJsonObject()) {
                //If the element is a json object, it's a group, so parse the group.
                buildGroup(element.getAsJsonObject(), retModel, parsedParts, null);
            } else {
                //If the element is a string, it's an element, so just add it to the children.
                String s = element.getAsString();

                if (s != null)
                    retModel.all_parts.add(parsedParts.get(UUID.fromString(s)));
            }
        }

        return retModel;
    }

    //Builds out a group from a JsonObject that specifies the group in the outline.
    public void buildGroup(JsonObject group, CustomModel target, HashMap<UUID, CustomModelPart> allParts, CustomModelPart parent) {
        CustomModelPart groupPart = new CustomModelPart();

        if (group.has("name")) {
            groupPart.name = group.get("name").getAsString();
            
            if(groupPart.name.startsWith("MESH_")){
                Path meshFilePath = LocalPlayerData.getContentDirectory().resolve(groupPart.name.substring(5) + ".obj");

                if (Files.exists(meshFilePath)) {
                    groupPart = CustomModelPartMesh.loadFromObj(meshFilePath);
                    groupPart.name = group.get("name").getAsString();
                }
            }
            
            //Find parent type.
            for (Map.Entry<String, CustomModelPart.ParentType> entry : nameParentTypeTags.entrySet()) {
                if (groupPart.name.contains(entry.getKey())) {
                    groupPart.parentType = entry.getValue();
                    break;
                }
            }
        }
        if (group.has("visibility")) groupPart.visible = group.get("visibility").getAsBoolean();
        if (group.has("origin")) groupPart.pivot = v3fFromJArray(group.get("origin").getAsJsonArray());
        if (group.has("rotation")) groupPart.rot = v3fFromJArray(group.get("rotation").getAsJsonArray());

        JsonArray children = group.get("children").getAsJsonArray();

        for (JsonElement child : children) {
            if (child.isJsonObject()) {
                //If the element is a json object, it's a group, so parse the group.
                buildGroup(child.getAsJsonObject(), target, allParts, groupPart);
            } else {
                //If the element is a string, it's an element, so just add it to the children.
                String s = child.getAsString();

                if (s != null)
                    groupPart.children.add(allParts.get(UUID.fromString(s)));
            }
        }

        //Add part.
        if (parent == null)
            target.all_parts.add(groupPart);
        else
            parent.children.add(groupPart);
    }

    public static final HashMap<String, CustomModelPart.ParentType> nameParentTypeTags = new HashMap<String, CustomModelPart.ParentType>() {{
        put("HEAD", CustomModelPart.ParentType.Head);
        put("TORSO", CustomModelPart.ParentType.Torso);
        put("LEFT_ARM", CustomModelPart.ParentType.LeftArm);
        put("RIGHT_ARM", CustomModelPart.ParentType.RightArm);
        put("LEFT_LEG", CustomModelPart.ParentType.LeftLeg);
        put("RIGHT_LEG", CustomModelPart.ParentType.RightLeg);
        put("NO_PARENT", CustomModelPart.ParentType.None);
    }};

    public CustomModelPart parseElement(JsonObject elementObject, CustomModel target) {
        CustomModelPartCuboid elementPart = new CustomModelPartCuboid();

        if (elementObject.has("name")) {
            elementPart.name = elementObject.get("name").getAsString();
        }
        if (elementObject.has("visibility")) elementPart.visible = elementObject.get("visibility").getAsBoolean();

        Vector3f from = v3fFromJArray(elementObject.get("from").getAsJsonArray());
        Vector3f to = v3fFromJArray(elementObject.get("to").getAsJsonArray());
        if (elementObject.has("origin"))
            elementPart.pivot = v3fFromJArray(elementObject.get("origin").getAsJsonArray());
        if (elementObject.has("rotation"))
            elementPart.rot = v3fFromJArray(elementObject.get("rotation").getAsJsonArray());


        Vector3f size = to.copy();
        size.subtract(from);

        if (elementObject.has("uv_offset")) {
            elementPart.uOffset = elementObject.get("uv_offset").getAsJsonArray().get(0).getAsInt();
            elementPart.vOffset = elementObject.get("uv_offset").getAsJsonArray().get(1).getAsInt();
        }

        JsonObject facesObject = elementObject.get("faces").getAsJsonObject();

        CompoundTag cuboidPropertiesTag = new CompoundTag();

        cuboidPropertiesTag.put("f", new ListTag(){{
            add(FloatTag.of(from.getX()));
            add(FloatTag.of(from.getY()));
            add(FloatTag.of(from.getZ()));
        }});

        cuboidPropertiesTag.put("t", new ListTag(){{
            add(FloatTag.of(to.getX()));
            add(FloatTag.of(to.getY()));
            add(FloatTag.of(to.getZ()));
        }});

        cuboidPropertiesTag.put("tw", FloatTag.of(target.texWidth));
        cuboidPropertiesTag.put("th", FloatTag.of(target.texHeight));
        
        cuboidPropertiesTag.put("n", JsonArrayToListTag(facesObject.get("north").getAsJsonObject().get("uv").getAsJsonArray()));
        cuboidPropertiesTag.put("s", JsonArrayToListTag(facesObject.get("south").getAsJsonObject().get("uv").getAsJsonArray()));
        cuboidPropertiesTag.put("e", JsonArrayToListTag(facesObject.get("east").getAsJsonObject().get("uv").getAsJsonArray()));
        cuboidPropertiesTag.put("w", JsonArrayToListTag(facesObject.get("west").getAsJsonObject().get("uv").getAsJsonArray()));
        cuboidPropertiesTag.put("u", JsonArrayToListTag(facesObject.get("up").getAsJsonObject().get("uv").getAsJsonArray()));
        cuboidPropertiesTag.put("d", JsonArrayToListTag(facesObject.get("down").getAsJsonObject().get("uv").getAsJsonArray()));
        
        elementPart.cuboidProperties = cuboidPropertiesTag;
        elementPart.rebuild();

        return elementPart;
    }

    public void generateFace(CustomModelPartCuboid part, Vector3f a, Vector3f b, Vector3f c, Vector3f d, Vector4f uv, float texWidth, float texHeight) {
        part.generateFace(a, b, c, d, uv, texWidth, texHeight);
    }

    public Vector3f v3fFromJArray(JsonArray array) {
        return new Vector3f(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat());
    }

    public Vector4f v4fFromJArray(JsonArray array) {
        return new Vector4f(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat(), array.get(3).getAsFloat());
    }

    public ListTag JsonArrayToListTag(JsonArray array){
        return new ListTag(){{
            for (JsonElement element : array) {
                add(FloatTag.of(element.getAsFloat()));
            }
        }};
    }
    
    //Sorts out all the things in a json array out by UUID.
    public HashMap<UUID, JsonObject> sortElements(JsonArray elementContainer) {
        HashMap<UUID, JsonObject> objects = new HashMap<>();
        for (JsonElement jsonElement : elementContainer) {
            if (jsonElement.isJsonObject() == false)
                continue;
            JsonObject obj = jsonElement.getAsJsonObject();

            if (obj.has("uuid") == false)
                continue;
            objects.put(UUID.fromString(obj.get("uuid").getAsString()), obj);


            if (obj.has("children")) {
                JsonElement children = obj.get("children");
                if (children.isJsonArray() == true) {
                    JsonArray childrenArray = children.getAsJsonArray();

                    objects.putAll(sortElements(childrenArray));
                }
            }
        }
        return objects;
    }
}
