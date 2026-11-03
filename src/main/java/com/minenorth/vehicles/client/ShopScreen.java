package com.minenorth.vehicles.client;

import com.minenorth.vehicles.menu.OpenShopPacket;
import com.minenorth.vehicles.menu.OpenShopPacket.Color;
import com.minenorth.vehicles.menu.OpenShopPacket.Model;
import com.minenorth.vehicles.menu.ScreenActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.minenorth.vehicles.client.MineNorthStyle.*;

/**
 * Concession : recherche + pastilles de catégorie (un clic, toutes visibles) et liste des modèles à gauche ;
 * aperçu 3D, couleur, fiche, paiement et achat à droite.
 */
@OnlyIn(Dist.CLIENT)
public class ShopScreen extends VehicleScreen {
    private static final int ROW = 16, TAB_W = 82, PILL_H = 14, PILL_GAP = 3, SEARCH_H = 16;
    /** Lignes de modèles gardées au minimum sous les pastilles (le reste des catégories passe dans « + n »). */
    private static final int MIN_ROWS = 4;

    private final OpenShopPacket data;
    private final List<String> categories = new ArrayList<>();
    private final List<Integer> shown = new ArrayList<>();
    /** Nombre de modèles par catégorie (même ordre que categories). */
    private final List<Integer> counts = new ArrayList<>();
    private int category = -1;   // -1 = toutes
    private int sel, color, scroll;
    private boolean card;
    private EditBox search;
    private String query = "";
    /** Panneau des catégories qui ne tiennent pas dans les pastilles visibles. */
    private boolean moreOpen;

    /** Pastille posée : position et catégorie (-1 = Tous, -2 = « + n »). */
    private record Pill(int x, int y, int w, int cat, String label) {}

    private final List<Pill> pills = new ArrayList<>();
    private final List<Pill> morePills = new ArrayList<>();

    // mise en page
    private int LW = 150, lx, pillY, listY, listH, rx, rw, by;

    public ShopScreen(OpenShopPacket data) {
        super(data.id);
        this.data = data;
        for (Model m : data.models) {
            int i = categories.indexOf(m.category());
            if (i < 0) {
                categories.add(m.category());
                counts.add(1);
            } else counts.set(i, counts.get(i) + 1);
        }
        card = data.cardError.isEmpty();
        filter();
    }

    private static String plain(String s) {
        return s.replaceAll("(?i)[&§][0-9a-fk-or]", "");
    }

    private static String norm(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    /** Catégorie choisie ET texte recherché (dans le nom du modèle ou sa catégorie, sans accents ni majuscules). */
    private void filter() {
        shown.clear();
        String q = norm(query.trim());
        for (int i = 0; i < data.models.size(); i++) {
            Model m = data.models.get(i);
            if (category >= 0 && !m.category().equals(categories.get(category))) continue;
            if (!q.isEmpty() && !norm(m.name()).contains(q) && !norm(m.category()).contains(q)) continue;
            shown.add(i);
        }
        if (!shown.contains(sel)) {
            sel = shown.isEmpty() ? -1 : shown.get(0);
            color = 0;
        }
        scroll = 0;
    }

    @Override
    protected void init() {
        super.init();
        LW = W < 420 ? 124 : 150;   // petite fenêtre (grande échelle d'interface) : colonne de gauche plus étroite
        lx = left + 10;
        search = new EditBox(font, lx + 18, top + 54, LW - 24, 10, Component.literal("Rechercher"));
        search.setBordered(false);
        search.setMaxLength(40);
        search.setTextColor(WHITE);
        search.setValue(query);
        search.setResponder(s -> {
            query = s;
            filter();
        });
        addRenderableWidget(search);
        layoutPills();
        rx = lx + LW + 8;
        rw = left + W - 10 - rx;
        px = rx;
        py = top + 50;
        pw = rw;
        ph = Math.max(50, H - 50 - 100);
        by = py + ph + 6;
    }

    private String pillLabel(int cat) {
        return cat < 0 ? "Tous " + data.models.size() : categories.get(cat) + " " + counts.get(cat);
    }

    /**
     * Pastilles « Tous » + une par catégorie, sur autant de lignes que la place le permet en gardant MIN_ROWS lignes de modèles ;
     * celles qui ne tiennent pas vont dans une pastille « + n » qui ouvre un panneau (un clic aussi).
     */
    private void layoutPills() {
        pills.clear();
        morePills.clear();
        pillY = top + 50 + SEARCH_H + 5;
        int bottom = top + H - 20;
        int maxRows = Math.max(1, Math.min(5, (bottom - pillY - MIN_ROWS * ROW - 6) / (PILL_H + PILL_GAP)));
        List<Integer> order = new ArrayList<>();
        order.add(-1);
        for (int i = 0; i < categories.size(); i++) order.add(i);
        int x = lx, y = pillY, row = 0, placed = 0;
        for (int k = 0; k < order.size(); k++) {
            int cat = order.get(k);
            String label = fit(font, pillLabel(cat), LW - 12);
            int w = font.width(label) + 12;
            if (x > lx && x + w > lx + LW) {   // ligne pleine : à la suivante, s'il en reste
                if (row + 1 >= maxRows) break;
                row++;
                x = lx;
                y += PILL_H + PILL_GAP;
            }
            // dernière ligne permise : garder la place de « + n » s'il reste des catégories après celle-ci
            int moreW = font.width("+ " + (order.size() - k - 1)) + 12;
            if (row == maxRows - 1 && k < order.size() - 1 && x + w + PILL_GAP + moreW > lx + LW) break;
            pills.add(new Pill(x, y, w, cat, label));
            x += w + PILL_GAP;
            placed++;
        }
        if (placed < order.size()) {
            String moreLabel = "+ " + (order.size() - placed);
            int w = font.width(moreLabel) + 12;
            if (x + w > lx + LW && pills.size() > 1) {   // pas la place sur la dernière ligne : la dernière pastille cède sa place
                Pill last = pills.remove(pills.size() - 1);
                placed--;
                x = last.x();
                y = last.y();
            }
            pills.add(new Pill(x, y, w, -2, "+ " + (order.size() - placed)));
            // panneau : les catégories restantes, posées sur toute la largeur de la liste
            int mx = lx + 6, my = y + PILL_H + 10;
            for (int k = placed; k < order.size(); k++) {
                int cat = order.get(k);
                String label = fit(font, pillLabel(cat), LW - 24);
                int pw2 = font.width(label) + 12;
                if (mx > lx + 6 && mx + pw2 > lx + LW - 6) {
                    mx = lx + 6;
                    my += PILL_H + PILL_GAP;
                }
                morePills.add(new Pill(mx, my, pw2, cat, label));
                mx += pw2 + PILL_GAP;
            }
        }
        listY = y + PILL_H + 7;
        listH = Math.max(ROW, (bottom - listY) / ROW * ROW);
    }

    private boolean hiddenActive() {
        for (Pill p : morePills) if (p.cat() == category) return true;
        return false;
    }

    private void choose(int cat) {
        click();
        category = cat;
        moreOpen = false;
        filter();
    }

    private Model model() {
        return sel < 0 ? null : data.models.get(sel);
    }

    private Color colorSel() {
        Model m = model();
        return m == null || m.colors().isEmpty() ? null : m.colors().get(Math.floorMod(color, m.colors().size()));
    }

    private int visibleRows() {
        return Math.max(1, listH / ROW);
    }

    /** Raison qui bloque l'achat, ou null. */
    private String blocker(Color c) {
        if (c == null) return "Aucun véhicule";
        if (data.waiting) return "Pose d'abord ton véhicule acheté";
        if (c.price() <= 0) return null;
        if (card) {
            if (!data.cardError.isEmpty()) return data.cardError;
            if (data.bank < c.price()) return "Solde bancaire insuffisant";
        } else if (data.cash < c.price()) return "Espèces insuffisantes";
        return null;
    }

    private int tabX(int i) {
        return left + W - 12 - (2 - i) * TAB_W - (1 - i) * 4;
    }

    // ------------------------------------------------------------------ interaction

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (inside(mx, my, tabX(1), top + 12, TAB_W, 18)) {            // onglet Mon garage
            act(ScreenActionPacket.SHOW_GARAGE, 0, 0, "", false);
            return true;
        }
        if (moreOpen) {   // panneau « + n » ouvert : il passe devant la liste
            for (Pill p : morePills) {
                if (inside(mx, my, p.x(), p.y(), p.w(), PILL_H)) {
                    choose(p.cat());
                    return true;
                }
            }
            moreOpen = false;
            if (inside(mx, my, lx, listY - 3, LW, listH + 6)) return true;
        }
        for (Pill p : pills) {
            if (!inside(mx, my, p.x(), p.y(), p.w(), PILL_H)) continue;
            if (p.cat() == -2) {
                click();
                moreOpen = !moreOpen;
            } else choose(p.cat());
            return true;
        }
        if (inside(mx, my, lx, listY, LW, listH)) {
            int i = (int) ((my - listY) / ROW) + scroll;
            if (i < shown.size() && shown.get(i) != sel) {
                click();
                sel = shown.get(i);
                color = 0;
                resetView();
            }
            return true;
        }
        if (inside(mx, my, px + 5, py + 5, 14, 14)) {
            click();
            resetView();
            return true;
        }
        Model m = model();
        if (m != null && m.colors().size() > 1) {
            if (inside(mx, my, rx + 18, by, 12, 14)) {
                click();
                color--;
                return true;
            }
            if (inside(mx, my, rx + 34 + font.width(colorLabel()), by, 12, 14)) {
                click();
                color++;
                return true;
            }
        }
        int payY = by + 32;
        if (inside(mx, my, rx, payY, 64, 18)) {
            click();
            card = true;
            return true;
        }
        if (inside(mx, my, rx + 68, payY, 64, 18)) {
            click();
            card = false;
            return true;
        }
        Color c = colorSel();
        if (inside(mx, my, rx + rw - 130, by + 56, 130, 20) && blocker(c) == null) {
            act(card && c.price() > 0 ? ScreenActionPacket.BUY_CARD : ScreenActionPacket.BUY_CASH, sel, Math.floorMod(color, m.colors().size()), "", true);
            return true;
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
        int pos = shown.indexOf(sel);
        if (key == GLFW.GLFW_KEY_ESCAPE && moreOpen) {
            moreOpen = false;
            return true;
        }
        if ((key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP) && !shown.isEmpty()) {
            pos = Math.max(0, Math.min(shown.size() - 1, pos + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1)));
            sel = shown.get(pos);
            color = 0;
            if (pos < scroll) scroll = pos;
            if (pos >= scroll + visibleRows()) scroll = pos - visibleRows() + 1;
            return true;
        }
        if ((key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) && !search.isFocused()) {
            color += key == GLFW.GLFW_KEY_RIGHT ? 1 : -1;
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private String colorLabel() {
        Color c = colorSel();
        return c == null ? "" : "« " + c.name() + " »";
    }

    // ------------------------------------------------------------------ rendu

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        String title = fit(font, plain(data.title), (int) ((tabX(0) - left - 50) / 1.6f));
        renderWindow(g, title, "Concession  ·  " + data.models.size() + " modèle(s)");

        // onglets
        button(g, tabX(0), top + 12, TAB_W, 18, "Acheter", CYAN, false, true);
        button(g, "Mon garage", tabX(1), top + 12, TAB_W, 18, mx, my, DARK, true);

        // recherche
        rfill(g, lx, top + 50, lx + LW, top + 50 + SEARCH_H, SEARCH_H / 2, search.isFocused() ? HOVER : LIST);
        g.drawString(font, "\u2315", lx + 7, top + 54, search.isFocused() ? CYAN : MUTED, false);
        if (query.isEmpty() && !search.isFocused()) g.drawString(font, fit(font, "Rechercher un modèle", LW - 26), lx + 18, top + 54, FADED, false);

        // pastilles de catégorie : toutes visibles, un clic
        for (Pill p : pills) {
            boolean active = p.cat() == -2 ? moreOpen || hiddenActive() : p.cat() == category;
            boolean hov = inside(mx, my, p.x(), p.y(), p.w(), PILL_H);
            int bg = active ? CYAN : hov ? HOVER : LIST;
            rfill(g, p.x(), p.y(), p.x() + p.w(), p.y() + PILL_H, PILL_H / 2, bg);
            String name = p.label();
            int sp = name.lastIndexOf(' ');
            if (p.cat() == -2 || sp < 0) g.drawString(font, name, p.x() + 6, p.y() + 3, active ? WHITE : TEXT, false);
            else {   // nom + nombre de modèles en plus discret
                g.drawString(font, name.substring(0, sp), p.x() + 6, p.y() + 3, active ? WHITE : TEXT, false);
                g.drawString(font, name.substring(sp + 1), p.x() + 6 + font.width(name.substring(0, sp + 1)), p.y() + 3, active ? LIST : MUTED, false);
            }
        }

        rfill(g, lx, listY - 3, lx + LW, listY + listH + 3, 5, LIST);
        for (int r = 0; r < visibleRows() && r + scroll < shown.size(); r++) {
            int idx = shown.get(r + scroll);
            Model m = data.models.get(idx);
            int y = listY + r * ROW;
            boolean hov = inside(mx, my, lx, y, LW, ROW);
            if (idx == sel) card(g, lx + 2, y, LW - 8, ROW - 1, true, CYAN);
            else if (hov) rfill(g, lx + 2, y, lx + LW - 6, y + ROW - 1, 4, HOVER);
            int min = Integer.MAX_VALUE;
            for (Color c : m.colors()) min = Math.min(min, c.price());
            String price = money(min == Integer.MAX_VALUE ? 0 : min);
            g.drawString(font, price, lx + LW - 10 - font.width(price), y + 4, min == 0 ? OK : CYAN, false);
            g.drawString(font, fit(font, m.name(), LW - 24 - font.width(price)), lx + 9, y + 4, idx == sel ? WHITE : TEXT, false);
        }
        if (shown.isEmpty()) g.drawCenteredString(font, query.isBlank() ? "Aucun véhicule" : "Aucun résultat", lx + LW / 2, listY + 10, MUTED);
        scrollBar(g, lx + LW - 5, listY, listH, scroll, visibleRows(), shown.size());

        // panneau « + n » : les autres catégories, par-dessus la liste
        if (moreOpen && !morePills.isEmpty()) {
            Pill last = morePills.get(morePills.size() - 1);
            int top2 = morePills.get(0).y() - 6, bottom2 = last.y() + PILL_H + 6;
            rfill(g, lx + 2, top2, lx + LW - 2, bottom2, 8, PANEL);
            rfill(g, lx + 3, top2 + 1, lx + LW - 3, bottom2 - 1, 7, HOVER);
            for (Pill p : morePills) {
                boolean active = p.cat() == category, hov = inside(mx, my, p.x(), p.y(), p.w(), PILL_H);
                rfill(g, p.x(), p.y(), p.x() + p.w(), p.y() + PILL_H, PILL_H / 2, active ? CYAN : hov ? DARK : LIST);
                g.drawString(font, p.label(), p.x() + 6, p.y() + 3, WHITE, false);
            }
        }

        // aperçu
        Model m = model();
        Color c = colorSel();
        renderPreview(g, c == null ? null : c.item());
        g.pose().pushPose();
        g.pose().translate(0, 0, 450);   // devant le modèle 3D
        boolean rh = inside(mx, my, px + 5, py + 5, 14, 14);
        rfill(g, px + 5, py + 5, px + 19, py + 19, 4, rh ? lighten(DARK) : DARK);
        g.drawCenteredString(font, "⟲", px + 12, py + 8, WHITE);
        String bal = "Espèces " + money(data.cash) + "  ·  Banque " + money(data.bank);
        g.drawString(font, bal, px + pw - 7 - font.width(bal), py + 8, TEXT, false);
        if (m != null) {
            rfill(g, px + 4, py + ph - 28, px + pw - 4, py + ph - 4, 4, (LIST & 0x00FFFFFF) | 0xD0000000);
            g.drawCenteredString(font, fit(font, m.name() + "  ·  " + m.category(), pw - 16), px + pw / 2, py + ph - 25, WHITE);
            g.drawCenteredString(font, fit(font, "Plaque attribuée à l'achat  ·  glisser pour tourner", pw - 16), px + pw / 2, py + ph - 14, MUTED);
        }
        g.pose().popPose();
        if (m == null || c == null) {
            super.render(g, mx, my, pt);
            return;
        }

        // couleur
        rfill(g, rx, by, rx + 14, by + 14, 4, WHITE);
        rfill(g, rx + 1, by + 1, rx + 13, by + 13, 3, colorOf(c.name()));
        boolean many = m.colors().size() > 1;
        if (many) g.drawString(font, "‹", rx + 21, by + 3, inside(mx, my, rx + 18, by, 12, 14) ? CYAN : MUTED, false);
        g.drawString(font, colorLabel(), rx + 30, by + 3, TEXT, false);
        int ax = rx + 34 + font.width(colorLabel());
        if (many) {
            g.drawString(font, "›", ax + 3, by + 3, inside(mx, my, ax, by, 12, 14) ? CYAN : MUTED, false);
            g.drawString(font, (Math.floorMod(color, m.colors().size()) + 1) + "/" + m.colors().size(), ax + 18, by + 3, FADED, false);
        }

        // fiche
        List<String> stats = new ArrayList<>();
        if (m.seats() > 0) stats.add(m.seats() + " place" + (m.seats() > 1 ? "s" : ""));
        if (m.trunk() > 0) stats.add("coffre " + m.trunk());
        if (m.speed() > 0) stats.add(m.speed() + " km/h");
        g.drawString(font, stats.isEmpty() ? "Véhicule neuf  ·  clés fournies" : String.join("  ·  ", stats), rx, by + 19, MUTED, false);

        // paiement
        int payY = by + 32;
        boolean free = c.price() <= 0;
        button(g, rx, payY, 64, 18, "Carte", card ? CYAN : DARK, !free && inside(mx, my, rx, payY, 64, 18), !free);
        button(g, rx + 68, payY, 64, 18, "Espèces", card ? DARK : CYAN, !free && inside(mx, my, rx + 68, payY, 64, 18), !free);
        String price = "Prix " + money(c.price());
        scaled(g, bold(price), rx + rw - 2 - Math.round(font.width(bold(price)) * 1.2f), payY + 5, 1.2f, free ? OK : CYAN);

        // achat
        String err = blocker(c);
        if (err != null) g.drawString(font, fit(font, err, rw - 140), rx, by + 62, ALERT, false);
        button(g, free ? "Récupérer" : "Acheter  " + money(c.price()), rx + rw - 130, by + 56, 130, 20, mx, my, GREEN, err == null);

        super.render(g, mx, my, pt);
    }
}
