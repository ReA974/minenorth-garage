package com.minenorth.vehicles.client;

import com.minenorth.vehicles.VehiclesMod;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Aperçu 3D d'un véhicule MTS dans un écran : le modèle (et ses pièces par défaut : roues, sièges...) est lu par
 * le parseur de MTS, puis dessiné ici avec un rendu entité vanilla. Tout passe par réflexion : sans MTS, ou si son
 * API change, on retombe sur l'icône de l'item agrandie.
 */
@OnlyIn(Dist.CLIENT)
public final class VehiclePreview {
    /** Morceau de modèle : triangles MTS (8 floats par sommet : normale, u, v, x, y, z) + placement + texture. */
    private record Piece(float[] data, Matrix4f transform, ResourceLocation texture) {}

    private record Mesh(List<Piece> pieces, Vector3f center, float extent) {}

    private static final Map<String, Mesh> CACHE = new HashMap<>();
    private static final Mesh FAILED = new Mesh(List.of(), new Vector3f(), 0);

    private VehiclePreview() {}

    /**
     * Dessine le véhicule centré sur (cx, cy).
     * @param size taille (pixels GUI) de la plus grande dimension du véhicule
     * @param yaw  rotation horizontale en degrés
     */
    public static void render(GuiGraphics g, String itemId, int cx, int cy, float size, float yaw, float pitch) {
        Mesh mesh = mesh(itemId);
        if (mesh == FAILED) {
            renderItem(g, itemId, cx, cy, size);
            return;
        }
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx, cy, 150);
        float s = size / mesh.extent();
        ps.mulPoseMatrix(new Matrix4f().scaling(s, s, -s));
        ps.mulPose(Axis.ZP.rotationDegrees(180));           // l'axe Y de l'écran descend
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        ps.mulPose(Axis.YP.rotationDegrees(yaw));
        ps.translate(-mesh.center().x, -mesh.center().y, -mesh.center().z);
        Lighting.setupForEntityInInventory();
        PoseStack.Pose root = ps.last();
        for (Piece p : mesh.pieces()) {
            VertexConsumer vc = g.bufferSource().getBuffer(RenderType.entityCutoutNoCull(p.texture()));
            Matrix4f m = new Matrix4f(root.pose()).mul(p.transform());
            Matrix3f n = new Matrix3f(root.normal()).mul(new Matrix3f(p.transform()));
            float[] d = p.data();
            // triangles MTS envoyés en quads : le 3e sommet est doublé
            for (int i = 0; i + 24 <= d.length; i += 24) {
                vertex(vc, m, n, d, i);
                vertex(vc, m, n, d, i + 8);
                vertex(vc, m, n, d, i + 16);
                vertex(vc, m, n, d, i + 16);
            }
        }
        g.flush();
        Lighting.setupFor3DItems();
        ps.popPose();
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float[] d, int i) {
        vc.vertex(m, d[i + 5], d[i + 6], d[i + 7]).color(255, 255, 255, 255).uv(d[i + 3], d[i + 4])
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(n, d[i], d[i + 1], d[i + 2]).endVertex();
    }

    /** Repli : l'icône de l'item, agrandie. */
    private static void renderItem(GuiGraphics g, String itemId, int cx, int cy, float size) {
        Item it = item(itemId);
        if (it == null) it = Items.MINECART;
        float s = Math.max(1f, size / 20f);
        g.pose().pushPose();
        g.pose().translate(cx - 8 * s, cy - 8 * s, 100);
        g.pose().scale(s, s, 1f);
        g.renderItem(new ItemStack(it), 0, 0);
        g.pose().popPose();
    }

    private static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id == null ? "" : id);
        Item it = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
        return it == null || it == Items.AIR ? null : it;
    }

    // ------------------------------------------------------------------ lecture du modèle MTS

    private static Mesh mesh(String itemId) {
        return CACHE.computeIfAbsent(itemId == null ? "" : itemId, id -> {
            try {
                Mesh m = load(id);
                return m == null ? FAILED : m;
            } catch (Throwable t) {   // MTS absent ou API différente : repli sur l'icône
                VehiclesMod.LOGGER.debug("[Vehicules] Aperçu 3D indisponible pour {} : {}", id, t.toString());
                return FAILED;
            }
        });
    }

    private static Mesh load(String itemId) throws Exception {
        Object wrapped = wrapped(itemId);
        if (wrapped == null) return null;
        Object def = field(wrapped, "definition");
        List<Piece> pieces = new ArrayList<>();
        addModel(pieces, wrapped, new Matrix4f());

        // pièces par défaut (roues, sièges, moteur visible...) placées comme sur le véhicule
        Object parts = def == null ? null : fieldOrNull(def, "parts");
        if (parts instanceof List<?> list) {
            for (Object pd : list) {
                Object dp = fieldOrNull(pd, "defaultPart");
                Object pos = fieldOrNull(pd, "pos");
                if (!(dp instanceof String dps) || dps.isEmpty() || pos == null) continue;
                Object partItem = wrapped("mts:" + dps.replace(':', '.'));
                if (partItem == null) continue;
                double x = (double) field(pos, "x"), y = (double) field(pos, "y"), z = (double) field(pos, "z");
                Matrix4f t = new Matrix4f().translation((float) x, (float) y, (float) z);
                Object rot = fieldOrNull(pd, "rot");
                if (rot != null) t.mul(rotation(rot));
                boolean inverse = Boolean.TRUE.equals(fieldOrNull(pd, "inverseMirroring"));
                if ((x < 0) != inverse) t.scale(-1, 1, 1);   // même règle de miroir que MTS
                try {
                    addModel(pieces, partItem, t);
                } catch (Exception ignored) {
                    // pièce illisible : le véhicule reste affiché sans elle
                }
            }
        }
        if (pieces.isEmpty()) return null;

        Vector3f min = new Vector3f(Float.MAX_VALUE), max = new Vector3f(-Float.MAX_VALUE), v = new Vector3f();
        for (Piece p : pieces) {
            float[] d = p.data();
            for (int i = 0; i + 8 <= d.length; i += 8) {
                p.transform().transformPosition(d[i + 5], d[i + 6], d[i + 7], v);
                min.min(v);
                max.max(v);
            }
        }
        Vector3f size = new Vector3f(max).sub(min);
        float extent = Math.max(size.x, Math.max(size.y, size.z));
        if (extent <= 0) return null;
        return new Mesh(pieces, new Vector3f(min).add(max).mul(0.5f), extent);
    }

    /** Objet MTS (AItemSubTyped) derrière l'item Forge, ou null. */
    private static Object wrapped(String itemId) throws Exception {
        Item it = item(itemId);
        if (it == null) return null;
        Method m;
        try {
            m = it.getClass().getMethod("getWrappedItem");
        } catch (NoSuchMethodException e) {
            return null;   // pas un item MTS
        }
        return m.invoke(it);
    }

    private static void addModel(List<Piece> out, Object wrapped, Matrix4f transform) throws Exception {
        Object def = field(wrapped, "definition");
        Object sub = fieldOrNull(wrapped, "subDefinition");
        if (def == null || sub == null) return;
        String model = (String) method(def, "getModelLocation", 1).invoke(def, sub);
        String tex = (String) method(def, "getTextureLocation", 2).invoke(def, sub, 0);
        if (model == null || tex == null) return;
        ResourceLocation texture = ResourceLocation.tryParse(tex.toLowerCase(Locale.ROOT).startsWith("/assets/")
                ? tex.substring(8).replaceFirst("/", ":") : tex);
        if (texture == null) return;
        Class<?> parser = Class.forName("minecrafttransportsimulator.rendering.AModelParser");
        List<?> objects = (List<?>) parser.getMethod("parseModel", String.class, boolean.class).invoke(null, model, false);
        for (Object o : objects) {
            String name = String.valueOf(fieldOrNull(o, "name"));
            // comme l'aperçu de l'établi MTS : ni vitres ni objets spéciaux (#...), ni lignes
            if (name.toLowerCase(Locale.ROOT).contains("window") || name.startsWith("#")) continue;
            if (Boolean.TRUE.equals(fieldOrNull(o, "isLines"))) continue;
            FloatBuffer fb = (FloatBuffer) field(o, "vertices");
            float[] d = new float[fb.limit()];
            for (int i = 0; i < d.length; i++) d[i] = fb.get(i);
            if (d.length >= 24) out.add(new Piece(d, new Matrix4f(transform), texture));
        }
    }

    private static Matrix4f rotation(Object r) throws Exception {
        float[] m = new float[9];
        String[] names = {"m00", "m01", "m02", "m10", "m11", "m12", "m20", "m21", "m22"};
        for (int i = 0; i < 9; i++) m[i] = (float) (double) field(r, names[i]);
        // RotationMatrix MTS : ligne par ligne ; JOML : colonne par colonne
        return new Matrix4f(m[0], m[3], m[6], 0, m[1], m[4], m[7], 0, m[2], m[5], m[8], 0, 0, 0, 0, 1);
    }

    private static Object field(Object o, String name) throws Exception {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(o);
            } catch (NoSuchFieldException ignored) {
                // champ hérité : on remonte
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object fieldOrNull(Object o, String name) {
        try {
            return field(o, name);
        } catch (Exception e) {
            return null;
        }
    }

    private static Method method(Object o, String name, int params) throws NoSuchMethodException {
        for (Method m : o.getClass().getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == params) return m;
        }
        throw new NoSuchMethodException(name);
    }
}
