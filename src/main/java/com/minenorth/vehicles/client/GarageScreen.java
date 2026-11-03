package com.minenorth.vehicles.client;

import com.minenorth.vehicles.menu.OpenGaragePacket;
import com.minenorth.vehicles.menu.OpenGaragePacket.Car;
import com.minenorth.vehicles.menu.ScreenActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static com.minenorth.vehicles.client.MineNorthStyle.*;

/** Garage : véhicules rangés (filtre Terre / Air / Mer), aperçu 3D, sortie et actions du garage. */
@OnlyIn(Dist.CLIENT)
public class GarageScreen extends VehicleScreen {
    private static final int LW = 150, ROW = 18;
    private static final String[] FILTERS = {"Tous", "TERRE", "AIR", "MER"};
    private static final String[] FILTER_LABELS = {"Tous", "Terre", "Air", "Mer"};
    /** Dernier véhicule regardé : retrouvé quand le serveur rouvre l'écran (changement de mode de sortie). */
    private static String lastItem = "";

    private final OpenGaragePacket data;
    private final List<Car> shown = new ArrayList<>();
    /** Filtres proposés (index dans FILTERS) : « Tous » + les seuls types présents, la zone ayant déjà masqué les autres. */
    private final List<Integer> chips = new ArrayList<>();
    private int filter, sel = -1, scroll;

    private int lx, listY, listH, rx, rw, by;

    public GarageScreen(OpenGaragePacket data) {
        super(data.id);
        this.data = data;
        chips.add(0);
        for (int i = 1; i < FILTERS.length; i++) {
            final String t = FILTERS[i];
            if (data.cars.stream().anyMatch(c -> c.type().equals(t))) chips.add(i);
        }
        applyFilter();
        for (int i = 0; i < shown.size(); i++) if (shown.get(i).item().equals(lastItem)) sel = i;
    }

    private void applyFilter() {
        shown.clear();
        for (Car c : data.cars) if (filter == 0 || c.type().equals(FILTERS[filter])) shown.add(c);
        sel = shown.isEmpty() ? -1 : 0;
        scroll = 0;
    }

    @Override
    protected void init() {
        super.init();
        lx = left + 10;
        listY = top + 72;
        listH = (top + H - 20 - listY) / ROW * ROW;
        rx = lx + LW + 8;
        rw = left + W - 10 - rx;
        px = rx;
        py = top + 50;
        pw = rw;
        ph = Math.max(70, H - 50 - 88);
        by = py + ph + 6;
    }

    private int chipW() {
        return (LW - 2 * (chips.size() - 1)) / chips.size();
    }

    private Car car() {
        return sel < 0 || sel >= shown.size() ? null : shown.get(sel);
    }

    private int visibleRows() {
        return Math.max(1, listH / ROW);
    }

    private static String typeGlyph(String type) {
        return switch (type) {
            case "AIR" -> "✈";
            case "MER" -> "≈";
            default -> "▶";
        };
    }

    private static int typeColor(String type) {
        return switch (type) {
            case "AIR" -> CYAN;
            case "MER" -> PINK;
            default -> WARN;
        };
    }

    private static String typeLabel(String type) {
        return switch (type) {
            case "AIR" -> "Aérien";
            case "MER" -> "Maritime";
            default -> "Terrestre";
        };
    }

    /** Boutons d'action du bas : libellé, action, couleur. */
    private List<Object[]> actions() {
        List<Object[]> out = new ArrayList<>();
        out.add(new Object[]{"Rentrer", ScreenActionPacket.STORE, GREEN});
        if (!data.service) {
            out.add(new Object[]{"Assurer", ScreenActionPacket.INSURE, DARK});
            out.add(new Object[]{"Vendre", ScreenActionPacket.SELL, DARK});
        }
        return out;
    }

    /** Assurer / Vendre portent sur le véhicule sélectionné ; toutes les actions demandent d'être au garage. */
    private boolean enabled(int action) {
        Car c = car();
        if (action == ScreenActionPacket.INSURE) return data.inZone && c != null && !c.insured();
        if (action == ScreenActionPacket.SELL) return data.inZone && c != null;
        return data.inZone;
    }

    private int actionsY() {
        return by + 48;
    }

    private int actionW() {
        return (rw - 4 * (actions().size() - 1)) / actions().size();
    }

    // ------------------------------------------------------------------ interaction

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int chipW = chipW();
        for (int i = 0; i < chips.size(); i++) {
            if (inside(mx, my, lx + i * (chipW + 2), top + 50, chipW, 18)) {
                click();
                filter = chips.get(i);
                applyFilter();
                return true;
            }
        }
        if (inside(mx, my, lx, listY, LW, listH)) {
            int i = (int) ((my - listY) / ROW) + scroll;
            if (i < shown.size() && i != sel) {
                click();
                sel = i;
                lastItem = shown.get(i).item();
                resetView();
            }
            return true;
        }
        if (inside(mx, my, px + 5, py + 5, 14, 14)) {
            click();
            resetView();
            return true;
        }
        Car c = car();
        if (inside(mx, my, rx + rw - 120, by + 20, 120, 20) && c != null && data.inZone && c.here()) {
            act(ScreenActionPacket.RETRIEVE, c.index(), 0, c.item(), true);
            return true;
        }
        if (inside(mx, my, rx, by + 22, 130, 14) && data.inZone) {
            act(ScreenActionPacket.TOGGLE_MODE, 0, 0, "", false);
            return true;
        }
        List<Object[]> acts = actions();
        int aw = actionW();
        for (int i = 0; i < acts.size(); i++) {
            int action = (int) acts.get(i)[1];
            if (inside(mx, my, rx + i * (aw + 4), actionsY(), aw, 18) && enabled(action)) {
                // l'assurance se fait sur place (le serveur rouvre l'écran à jour) ; les autres actions ferment l'écran
                Car c2 = car();
                int idx = c2 == null ? -1 : c2.index();
                act(action, idx, 0, c2 == null ? "" : c2.item(), action != ScreenActionPacket.INSURE);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (inside(mx, my, lx, listY, LW, listH)) {
            scroll = Math.max(0, Math.min(Math.max(0, shown.size() - visibleRows()), scroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP) && !shown.isEmpty()) {
            sel = Math.max(0, Math.min(shown.size() - 1, sel + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1)));
            lastItem = shown.get(sel).item();
            if (sel < scroll) scroll = sel;
            if (sel >= scroll + visibleRows()) scroll = sel - visibleRows() + 1;
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    // ------------------------------------------------------------------ rendu

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderWindow(g, data.title, data.inZone ? "Tes véhicules rangés" : "Consultation : va dans une zone garage pour sortir un véhicule");
        String count = data.cars.size() + " / " + data.max;
        scaled(g, bold(count), left + W - 14 - Math.round(font.width(bold(count)) * 1.3f), top + 14, 1.3f,
                data.cars.size() >= data.max ? ALERT : CYAN);

        // filtres
        int chipW = chipW();
        for (int i = 0; i < chips.size(); i++) {
            int x = lx + i * (chipW + 2);
            int f = chips.get(i);
            button(g, x, top + 50, chipW, 18, FILTER_LABELS[f], filter == f ? CYAN : DARK, inside(mx, my, x, top + 50, chipW, 18), true);
        }

        // liste
        rfill(g, lx, listY - 3, lx + LW, listY + listH + 3, 5, LIST);
        for (int r = 0; r < visibleRows() && r + scroll < shown.size(); r++) {
            int i = r + scroll;
            Car c = shown.get(i);
            int y = listY + r * ROW;
            boolean hov = inside(mx, my, lx, y, LW, ROW);
            if (i == sel) card(g, lx + 2, y + 1, LW - 8, ROW - 2, true, CYAN);
            else if (hov) rfill(g, lx + 2, y + 1, lx + LW - 6, y + ROW - 1, 4, HOVER);
            badge(g, font, typeGlyph(c.type()), lx + 8, y + 3, 12, typeColor(c.type()));
            int tc = !data.inZone || c.here() ? (i == sel ? WHITE : TEXT) : FADED;
            int right = c.insured() ? 14 : 4;
            g.drawString(font, fit(font, c.label(), LW - 36 - right), lx + 25, y + 5, tc, false);
            if (c.insured()) g.drawString(font, "⛨", lx + LW - 18, y + 5, OK, false);
        }
        if (shown.isEmpty()) {
            g.drawCenteredString(font, data.cars.isEmpty() ? (data.inZone ? "Rien pour ce garage" : "Garage vide") : "Aucun véhicule", lx + LW / 2, listY + 10, MUTED);
        }
        scrollBar(g, lx + LW - 5, listY, listH, scroll, visibleRows(), shown.size());

        // aperçu
        Car c = car();
        renderPreview(g, c == null ? null : c.item());
        g.pose().pushPose();
        g.pose().translate(0, 0, 450);   // devant le modèle 3D
        boolean rh = inside(mx, my, px + 5, py + 5, 14, 14);
        rfill(g, px + 5, py + 5, px + 19, py + 19, 4, rh ? lighten(DARK) : DARK);
        g.drawCenteredString(font, "⟲", px + 12, py + 8, WHITE);
        if (c != null) {
            rfill(g, px + 4, py + ph - 28, px + pw - 4, py + ph - 4, 4, (LIST & 0x00FFFFFF) | 0xD0000000);
            g.drawCenteredString(font, fit(font, c.label() + "  ·  " + typeLabel(c.type()), pw - 16), px + pw / 2, py + ph - 25, WHITE);
            g.drawCenteredString(font, fit(font, "Intact : pièces, carburant et coffres conservés", pw - 16), px + pw / 2, py + ph - 14, MUTED);
        } else {
            g.drawCenteredString(font, data.cars.isEmpty() ? "Aucun véhicule rangé" : "Choisis un véhicule", px + pw / 2, py + ph / 2 - 4, MUTED);
        }
        g.pose().popPose();

        // fiche + sortie
        if (c != null) {
            String ins = c.insured() ? "⛨ Assuré" : "Non assuré";
            tag(g, font, ins, rx, by, c.insured() ? OK : MUTED);
            boolean ok = data.inZone && c.here();
            tag(g, font, !data.inZone ? "Hors garage" : c.here() ? "Disponible ici" : "Pas dans ce garage",
                    rx + font.width(ins) + 18, by, ok ? OK : ALERT);
            button(g, "Sortir", rx + rw - 120, by + 20, 120, 20, mx, my, GREEN, ok);
        }
        if (data.inZone) {
            boolean mh = inside(mx, my, rx, by + 22, 130, 14);
            g.drawString(font, "⇄ Sortie : " + (data.handMode ? "en main" : "directe"), rx, by + 26, mh ? CYAN : MUTED, false);
        }

        // actions du garage
        List<Object[]> acts = actions();
        int aw = actionW();
        String tip = null;
        for (int i = 0; i < acts.size(); i++) {
            int x = rx + i * (aw + 4), action = (int) acts.get(i)[1];
            button(g, (String) acts.get(i)[0], x, actionsY(), aw, 18, mx, my, (int) acts.get(i)[2], enabled(action));
            if (action == ScreenActionPacket.INSURE && data.inZone && inside(mx, my, x, actionsY(), aw, 18)) {
                tip = c == null ? "Choisis un véhicule dans la liste" : c.insured() ? "Ce véhicule est déjà assuré"
                        : "Assurer " + c.label() + " : " + money(c.insurePrice());
            }
        }

        super.render(g, mx, my, pt);
        if (tip != null) g.renderTooltip(font, net.minecraft.network.chat.Component.literal(tip), mx, my);
    }
}
