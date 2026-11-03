package com.minenorth.vehicles.client;

import com.minenorth.vehicles.menu.OpenSalePacket;
import com.minenorth.vehicles.menu.ScreenActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import static com.minenorth.vehicles.client.MineNorthStyle.*;

/** Vente entre joueurs, côté acheteur : l'offre reçue (vendeur en nom RP, véhicule, plaque, prix) à accepter ou refuser. */
@OnlyIn(Dist.CLIENT)
public class OfferScreen extends VehicleScreen {
    private final OpenSalePacket.Offer offer;

    public OfferScreen(OpenSalePacket data) {
        super(data.id);
        this.offer = data.offer;
    }

    @Override
    protected void init() {
        super.init();
        W = Math.min(width - 16, 320);
        H = Math.min(height - 16, 250);
        left = (width - W) / 2;
        top = (height - H) / 2;
        px = left + 10;
        py = top + 50;
        pw = W - 20;
        ph = Math.max(60, H - 50 - 92);
    }

    private boolean affordable() {
        return offer.balance() >= offer.price();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int y = top + H - 38, bw = (W - 28) / 2;
        if (inside(mx, my, left + 10, y, bw, 20) && affordable()) {
            act(ScreenActionPacket.SALE_ACCEPT, 0, 0, "", true);
            return true;
        }
        if (inside(mx, my, left + 18 + bw, y, bw, 20)) {
            act(ScreenActionPacket.SALE_REFUSE, 0, 0, "", true);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderWindow(g, "Offre de vente", fit(font, offer.seller() + " te propose un véhicule", W - 60));
        renderPreview(g, offer.item());

        int y = py + ph + 8;
        g.drawString(font, fit(font, offer.label(), W - 30), left + 12, y, WHITE, false);
        g.drawString(font, offer.plate().isEmpty() ? "Sans plaque enregistrée" : "Plaque " + offer.plate() + " mise à ton nom", left + 12, y + 12,
                offer.plate().isEmpty() ? MUTED : OK, false);
        String price = "Prix " + money(offer.price());
        scaled(g, bold(price), left + W - 12 - Math.round(font.width(bold(price)) * 1.2f), y, 1.2f, CYAN);
        String bal = "Ton solde : " + money(offer.balance());
        g.drawString(font, bal, left + W - 12 - font.width(bal), y + 14, affordable() ? MUTED : ALERT, false);

        int by = top + H - 38, bw = (W - 28) / 2;
        button(g, "Accepter", left + 10, by, bw, 20, mx, my, GREEN, affordable());
        button(g, "Refuser", left + 18 + bw, by, bw, 20, mx, my, DARK, true);
        if (!affordable()) g.drawString(font, "Solde bancaire insuffisant", left + 12, by - 12, ALERT, false);

        super.render(g, mx, my, pt);
    }
}
