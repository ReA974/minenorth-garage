package com.minenorth.vehicles.client;

import com.minenorth.vehicles.menu.OpenSalePacket;
import com.minenorth.vehicles.menu.OpenSalePacket.Buyer;
import com.minenorth.vehicles.menu.OpenSalePacket.Car;
import com.minenorth.vehicles.menu.ScreenActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import static com.minenorth.vehicles.client.MineNorthStyle.*;

/** Vente entre joueurs, côté vendeur : véhicule, acheteur (nom RP, le plus proche présélectionné) et prix. */
@OnlyIn(Dist.CLIENT)
public class SaleScreen extends VehicleScreen {
    private static final int LW = 150, ROW = 18;

    private final OpenSalePacket data;
    private int sel, buyer, scroll;
    private EditBox price;
    private String priceText = "";

    private int lx, listY, listH, rx, rw, by;

    public SaleScreen(OpenSalePacket data) {
        super(data.id);
        this.data = data;
        sel = data.preselect >= 0 ? indexOfReal(data.preselect) : (data.cars.isEmpty() ? -1 : 0);
    }

    private int indexOfReal(int real) {
        for (int i = 0; i < data.cars.size(); i++) if (data.cars.get(i).index() == real) return i;
        return data.cars.isEmpty() ? -1 : 0;
    }

    @Override
    protected void init() {
        super.init();
        lx = left + 10;
        listY = top + 50;
        listH = (top + H - 20 - listY) / ROW * ROW;
        rx = lx + LW + 8;
        rw = left + W - 10 - rx;
        px = rx;
        py = top + 50;
        pw = rw;
        ph = Math.max(60, H - 50 - 92);
        by = py + ph + 8;
        price = new EditBox(font, rx + 64, by + 26, 90, 14, Component.literal("Prix"));
        price.setBordered(false);
        price.setMaxLength(9);
        price.setFilter(s -> s.matches("\\d*"));
        price.setValue(priceText);
        price.setResponder(s -> priceText = s);
        price.setTextColor(WHITE);
        addRenderableWidget(price);
        setInitialFocus(price);
    }

    private Car car() {
        return sel < 0 || sel >= data.cars.size() ? null : data.cars.get(sel);
    }

    private Buyer buyer() {
        return data.buyers.isEmpty() ? null : data.buyers.get(Math.floorMod(buyer, data.buyers.size()));
    }

    private int priceValue() {
        try {
            return priceText.isEmpty() ? 0 : Integer.parseInt(priceText);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    /** Raison qui bloque la vente, ou null. */
    private String blocker() {
        if (car() == null) return "Choisis un véhicule";
        if (buyer() == null) return "Personne à proximité pour acheter";
        int p = priceValue();
        if (p < 1) return "Indique un prix";
        if (data.maxPrice > 0 && p > data.maxPrice) return "Prix maximum : " + money(data.maxPrice);
        return null;
    }

    private int visibleRows() {
        return Math.max(1, listH / ROW);
    }

    // ------------------------------------------------------------------ interaction

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (inside(mx, my, lx, listY, LW, listH)) {
            int i = (int) ((my - listY) / ROW) + scroll;
            if (i < data.cars.size() && i != sel) {
                click();
                sel = i;
                resetView();
            }
            return true;
        }
        if (data.buyers.size() > 1) {
            if (inside(mx, my, rx + 52, by, 12, 14)) {
                click();
                buyer--;
                return true;
            }
            if (inside(mx, my, rx + rw - 14, by, 12, 14)) {
                click();
                buyer++;
                return true;
            }
        }
        if (inside(mx, my, rx + rw - 140, by + 48, 140, 20) && blocker() == null) {
            Car c = car();
            act(ScreenActionPacket.SALE_PROPOSE, c.index(), priceValue(), buyer().uuid() + "|" + c.item(), true);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (inside(mx, my, lx, listY, LW, listH)) {
            scroll = Math.max(0, Math.min(Math.max(0, data.cars.size() - visibleRows()), scroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    // ------------------------------------------------------------------ rendu

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderWindow(g, "Vendre un véhicule", "Vente à un autre joueur, plaque comprise");

        // véhicules
        rfill(g, lx, listY - 3, lx + LW, listY + listH + 3, 5, LIST);
        for (int r = 0; r < visibleRows() && r + scroll < data.cars.size(); r++) {
            int i = r + scroll;
            Car c = data.cars.get(i);
            int y = listY + r * ROW;
            if (i == sel) card(g, lx + 2, y + 1, LW - 8, ROW - 2, true, CYAN);
            else if (inside(mx, my, lx, y, LW, ROW)) rfill(g, lx + 2, y + 1, lx + LW - 6, y + ROW - 1, 4, HOVER);
            g.drawString(font, fit(font, c.label(), LW - 18), lx + 9, y + 5, i == sel ? WHITE : TEXT, false);
        }
        scrollBar(g, lx + LW - 5, listY, listH, scroll, visibleRows(), data.cars.size());

        // aperçu
        Car c = car();
        renderPreview(g, c == null ? null : c.item());
        if (c != null) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 450);
            rfill(g, px + 4, py + ph - 28, px + pw - 4, py + ph - 4, 4, (LIST & 0x00FFFFFF) | 0xD0000000);
            g.drawCenteredString(font, fit(font, c.label(), pw - 16), px + pw / 2, py + ph - 25, WHITE);
            g.drawCenteredString(font, c.plate().isEmpty() ? "Aucune plaque enregistrée" : "Plaque " + c.plate() + " incluse", px + pw / 2, py + ph - 14,
                    c.plate().isEmpty() ? MUTED : OK);
            g.pose().popPose();
        }

        // acheteur (nom RP), le plus proche d'abord
        g.drawString(font, "Acheteur", rx, by + 3, MUTED, false);
        rfill(g, rx + 50, by - 2, rx + rw, by + 16, 5, LIST);
        Buyer b = buyer();
        if (b == null) {
            g.drawString(font, "Personne à proximité", rx + 66, by + 3, FADED, false);
        } else {
            boolean many = data.buyers.size() > 1;
            if (many) g.drawString(font, "‹", rx + 55, by + 3, inside(mx, my, rx + 52, by, 12, 14) ? CYAN : MUTED, false);
            String dist = b.distance() + " m";
            g.drawString(font, fit(font, b.name(), rw - 100 - font.width(dist)), rx + 66, by + 3, WHITE, false);
            g.drawString(font, dist, rx + rw - 20 - font.width(dist), by + 3, MUTED, false);
            if (many) g.drawString(font, "›", rx + rw - 10, by + 3, inside(mx, my, rx + rw - 14, by, 12, 14) ? CYAN : MUTED, false);
        }

        // prix
        g.drawString(font, "Prix", rx, by + 28, MUTED, false);
        rfill(g, rx + 50, by + 22, rx + 160, by + 40, 5, LIST);
        g.drawString(font, "€", rx + 150, by + 28, MUTED, false);
        if (priceText.isEmpty() && !price.isFocused()) g.drawString(font, "0", rx + 64, by + 28, FADED, false);

        String err = blocker();
        if (err != null) g.drawString(font, fit(font, err, rw - 150), rx, by + 54, ALERT, false);
        else g.drawString(font, fit(font, "L'acheteur devra accepter", rw - 150), rx, by + 54, MUTED, false);
        button(g, "Proposer la vente", rx + rw - 140, by + 48, 140, 20, mx, my, GREEN, err == null);

        super.render(g, mx, my, pt);
    }
}
