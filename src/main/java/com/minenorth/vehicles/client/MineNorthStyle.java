package com.minenorth.vehicles.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeaconBeamBlock;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;

import java.util.Locale;

/**
 * Charte graphique MineNorth (identique aux mods Permis / EuroBank / Entreprises : fond bleu nuit, logo, boutons plats
 * arrondis, blocs à liseré), plus les outils propres au garage : sol du showroom, pictogrammes, prix.
 */
@OnlyIn(Dist.CLIENT)
public final class MineNorthStyle {
    public static final int FRAME = 0xFF0E0E10;
    /** Fond du panneau. */
    public static final int PANEL = 0xFF161048;
    /** Fond des blocs / listes. */
    public static final int LIST = 0xFF0E0A34;
    public static final int HOVER = 0xFF2E2480;
    public static final int CYAN = 0xFF20AAEB;
    public static final int DARK = 0xFF4A3CB4;
    public static final int PINK = 0xFFC83CF0;
    /** Vert des boutons de validation. */
    public static final int GREEN = 0xFF1E9E52;
    public static final int TEXT = 0xFFCFE3FF;
    public static final int MUTED = 0xFF8FA8E0;
    public static final int WARN = 0xFFFFE066;
    public static final int ALERT = 0xFFFF6A9A;
    /** Vert des textes de statut. */
    public static final int OK = 0xFF6CFF9A;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int DISABLED = 0xFF2A2468;
    /** Texte grisé (élément indisponible). */
    public static final int FADED = 0xFF5D67A8;
    public static final ResourceLocation LOGO = new ResourceLocation("minenorth_eurobank", "textures/gui/logo.png");

    private MineNorthStyle() {}

    // ------------------------------------------------------------------ charte commune

    public static int lighten(int c) {
        int r = Math.min(255, ((c >> 16) & 255) + 35), g = Math.min(255, ((c >> 8) & 255) + 35), b = Math.min(255, (c & 255) + 35);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Rectangle aux coins arrondis (rayon r en pixels GUI). */
    public static void rfill(GuiGraphics g, int x1, int y1, int x2, int y2, int r, int c) {
        r = Math.max(0, Math.min(r, Math.min(x2 - x1, y2 - y1) / 2));
        g.fill(x1, y1 + r, x2, y2 - r, c);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * (double) r - dy * dy));
            g.fill(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, c);
            g.fill(x1 + inset, y2 - i - 1, x2 - inset, y2 - i, c);
        }
    }

    /** « ACHETER » devient « Acheter » (plus lisible) ; le texte déjà en minuscules reste intact. */
    public static String soft(String s) {
        if (s == null || s.isEmpty() || !s.equals(s.toUpperCase(Locale.ROOT))) return s;
        String l = s.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(l.charAt(0)) + l.substring(1);
    }

    public static Component bold(String s) {
        return Component.literal(s).withStyle(ChatFormatting.BOLD);
    }

    /** Fond + cadre + logo + titre agrandi, comme les écrans Permis. */
    public static void panel(GuiGraphics g, int left, int top, int w, int h, String title, String subtitle) {
        Font font = Minecraft.getInstance().font;
        rfill(g, left - 3, top - 3, left + w + 3, top + h + 3, 9, FRAME);
        rfill(g, left, top, left + w, top + h, 7, PANEL);
        RenderSystem.enableBlend();
        g.blit(LOGO, left + 8, top + 5, 28, 28, 0, 0, 96, 96, 96, 96);
        scaled(g, bold(title), left + 42, top + 9, 1.6f, WHITE);
        if (subtitle != null && !subtitle.isBlank()) g.drawString(font, subtitle, left + 42, top + 26, MUTED, false);
        rfill(g, left + 10, top + 40, left + w - 10, top + 42, 1, HOVER);
    }

    public static void scaled(GuiGraphics g, Component c, int x, int y, float s, int color) {
        Font font = Minecraft.getInstance().font;
        g.pose().pushPose();
        g.pose().scale(s, s, 1f);
        g.drawString(font, c, Math.round(x / s), Math.round(y / s), color, false);
        g.pose().popPose();
    }

    /** Bloc sombre avec liseré de couleur à gauche. */
    public static void card(GuiGraphics g, int x, int y, int w, int h, boolean hover, int accent) {
        rfill(g, x, y, x + w, y + h, 4, accent);
        rfill(g, x + 3, y, x + w, y + h, 4, hover ? HOVER : LIST);
    }

    /** Bouton plat. */
    public static void button(GuiGraphics g, int x, int y, int w, int h, String label, int color, boolean hover, boolean active) {
        Font f = Minecraft.getInstance().font;
        int bg = !active ? DISABLED : (hover ? lighten(color) : color);
        rfill(g, x, y, x + w, y + h, h >= 16 ? 5 : 3, bg);
        label = soft(label);
        g.drawString(f, label, x + w / 2 - f.width(label) / 2, y + (h - 8) / 2, active ? WHITE : MUTED, false);
    }

    // ------------------------------------------------------------------ garage

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Bouton plat dont le survol est calculé ici ; renvoie true si survolé. */
    public static boolean button(GuiGraphics g, String label, int x, int y, int w, int h, int mx, int my, int color, boolean enabled) {
        boolean hov = enabled && inside(mx, my, x, y, w, h);
        button(g, x, y, w, h, label, color, hov, enabled);
        return hov;
    }

    /** Pastille de statut (« Assuré », « Disponible ici »...) : fond teinté, texte de la couleur. */
    public static void tag(GuiGraphics g, Font font, String text, int x, int y, int color) {
        int w = font.width(text) + 12;
        rfill(g, x, y, x + w, y + 13, 6, mix(color, LIST, 0.78f));
        g.drawString(font, text, x + 6, y + 3, color, false);
    }

    /** Segment épais (lignes en diagonale du sol). */
    public static void line(GuiGraphics g, float x1, float y1, float x2, float y2, float width, int color) {
        float dx = x2 - x1, dy = y2 - y1, len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.01f) return;
        float nx = -dy / len * width / 2, ny = dx / len * width / 2;
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        vc.vertex(m, x1 + nx, y1 + ny, 0).color(color).endVertex();
        vc.vertex(m, x1 - nx, y1 - ny, 0).color(color).endVertex();
        vc.vertex(m, x2 - nx, y2 - ny, 0).color(color).endVertex();
        vc.vertex(m, x2 + nx, y2 + ny, 0).color(color).endVertex();
    }

    /** Showroom : fond bleu nuit et sol quadrillé en perspective, aux couleurs de la charte. */
    public static void showroom(GuiGraphics g, int x, int y, int w, int h) {
        int horizon = y + h * 56 / 100;
        g.fillGradient(x, y, x + w, horizon, LIST, PANEL);
        g.fill(x, horizon, x + w, y + h, LIST);
        g.fill(x, horizon, x + w, horizon + 1, DARK);
        float vx = x + w / 2f, depth = y + h - horizon;
        int lineCol = (DARK & 0x00FFFFFF) | 0x70000000;
        for (int i = -12; i <= 12; i++) line(g, vx + i * w / 22f, horizon, vx + i * w / 3.2f, y + h + depth, 1f, lineCol);
        for (int i = 1; i < 9; i++) {
            float k = i / 9f;
            int yy = (int) (horizon + depth * k * k);
            g.fill(x, yy, x + w, yy + 1, lineCol);
        }
        g.flush();
    }

    public static String money(long v) {
        if (v == 0) return "Gratuit";
        return String.format(Locale.FRANCE, "%,d", v).replace(' ', ' ').replace(' ', ' ') + " €";
    }

    /** Texte coupé avec « .. » s'il dépasse maxW. */
    public static String fit(Font font, String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        while (!s.isEmpty() && font.width(s + "..") > maxW) s = s.substring(0, s.length() - 1);
        return s + "..";
    }

    public static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int gg = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bb = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | r << 16 | gg << 8 | bb;
    }

    /** Couleur d'aperçu d'un nom de teinte du catalogue. */
    public static int colorOf(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("noir") || n.contains("black")) return 0xFF1B1B22;
        if (n.contains("blanc") || n.contains("white")) return 0xFFF2F2F2;
        if (n.contains("rouge") || n.contains("red")) return 0xFFE0303C;
        if (n.contains("bleu") || n.contains("blue")) return 0xFF2F6BFF;
        if (n.contains("vert") || n.contains("green")) return 0xFF2FC25A;
        if (n.contains("jaune") || n.contains("yellow")) return 0xFFFFD630;
        if (n.contains("orange")) return 0xFFFF8A1F;
        if (n.contains("gris") || n.contains("grey") || n.contains("gray")) return 0xFF7D808C;
        if (n.contains("argent") || n.contains("silver")) return 0xFFC4C9D4;
        if (n.contains("rose") || n.contains("pink")) return 0xFFFF6FC8;
        if (n.contains("violet") || n.contains("purple")) return 0xFF9150FF;
        if (n.contains("marron") || n.contains("brown")) return 0xFF7A4A28;
        if (n.contains("beige")) return 0xFFE3CFA3;
        return 0xFFB8C0E8;
    }

    // ------------------------------------------------------------------ pictogrammes

    /** Pastille arrondie avec un symbole, à la place d'un item Minecraft. */
    public static void badge(GuiGraphics g, Font font, String glyph, int x, int y, int size, int color) {
        rfill(g, x, y, x + size, y + size, Math.max(2, size / 4), mix(color, LIST, 0.7f));
        float s = size / 14f;
        g.pose().pushPose();
        g.pose().translate(x + size / 2f - font.width(glyph) * s / 2f, y + size / 2f - 4 * s + 0.5f, 0);
        g.pose().scale(s, s, 1f);
        g.drawString(font, glyph, 0, 0, color, false);
        g.pose().popPose();
    }

    /**
     * Symbole et couleur remplaçant un item Minecraft de base dans les menus ; null pour un item de mod
     * (véhicule MTS, billet...), qui garde son vrai rendu.
     */
    public static Object[] iconFor(ItemStack st) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(st.getItem());
        if (key == null || !key.getNamespace().equals("minecraft")) return null;
        if (st.is(Items.ARROW)) return new Object[]{"«", MUTED};
        if (st.is(Items.BARRIER) || st.is(Items.RED_CONCRETE)) return new Object[]{"✖", ALERT};
        if (st.is(Items.LIME_CONCRETE) || st.is(Items.GREEN_CONCRETE)) return new Object[]{"✔", OK};
        if (st.is(Items.BOOK) || st.is(Items.WRITABLE_BOOK)) return new Object[]{"☰", CYAN};
        if (st.is(Items.CHEST) || st.is(Items.BARREL) || st.is(Items.ENDER_CHEST)) return new Object[]{"▣", WARN};
        if (st.is(Items.IRON_DOOR) || st.is(Items.OAK_DOOR)) return new Object[]{"⬇", CYAN};
        if (st.is(Items.SHIELD)) return new Object[]{"⛨", CYAN};
        if (st.is(Items.EMERALD) || st.is(Items.GOLD_INGOT) || st.is(Items.GOLD_NUGGET)) return new Object[]{"€", OK};
        if (st.is(Items.PAPER) || st.is(Items.MAP) || st.is(Items.FILLED_MAP)) return new Object[]{"▭", CYAN};
        if (st.is(Items.COMPASS) || st.is(Items.HOPPER)) return new Object[]{"✦", PINK};
        if (st.is(Items.FEATHER) || st.is(Items.ELYTRA)) return new Object[]{"✈", CYAN};
        if (st.is(Items.WATER_BUCKET) || st.is(Items.OAK_BOAT)) return new Object[]{"≈", CYAN};
        if (st.is(Items.MINECART)) return new Object[]{"▶", WARN};
        if (st.is(Items.CLOCK)) return new Object[]{"⌚", WARN};
        if (st.is(Items.NAME_TAG)) return new Object[]{"✎", CYAN};
        if (st.is(Items.ANVIL) || st.is(Items.IRON_INGOT)) return new Object[]{"⚒", MUTED};
        if (st.is(Items.LAVA_BUCKET) || st.is(Items.BUCKET)) return new Object[]{"⛽", WARN};
        if (st.getItem() instanceof BlockItem bi && bi.getBlock() instanceof BeaconBeamBlock beam) {   // vitres teintées
            return new Object[]{"●", 0xFF000000 | beam.getColor().getTextColor()};
        }
        String name = st.getHoverName().getString().replaceAll("(?i)§[0-9a-fk-or]", "").trim();
        return new Object[]{name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT), DARK};
    }
}
