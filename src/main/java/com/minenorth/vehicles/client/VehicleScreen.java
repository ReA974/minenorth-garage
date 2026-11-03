package com.minenorth.vehicles.client;

import com.minenorth.vehicles.menu.CloseMenuPacket;
import com.minenorth.vehicles.menu.MenuNet;
import com.minenorth.vehicles.menu.ScreenActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import static com.minenorth.vehicles.client.MineNorthStyle.*;

/**
 * Base des écrans concession / garage (charte MineNorth) : panneau, aperçu 3D tournant (glisser pour tourner)
 * et envoi des actions au serveur.
 */
@OnlyIn(Dist.CLIENT)
abstract class VehicleScreen extends Screen {
    protected final int screenId;
    protected int left, top, W, H;
    /** Zone de l'aperçu 3D. */
    protected int px, py, pw, ph;
    private float yaw = 210, pitch = 18;
    private long lastDrag, lastFrame = System.currentTimeMillis();
    private boolean dragging;
    private long lockUntil;

    protected VehicleScreen(int screenId) {
        super(Component.literal("MineNorth"));
        this.screenId = screenId;
    }

    @Override
    protected void init() {
        W = Math.min(width - 16, 480);
        H = Math.min(height - 16, 300);
        left = (width - W) / 2;
        top = (height - H) / 2;
    }

    // ------------------------------------------------------------------ actions

    protected void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    /** Envoie une action (anti double-clic) ; close = fermer l'écran juste après. */
    protected void act(int action, int a, int b, String text, boolean close) {
        long now = System.currentTimeMillis();
        if (now < lockUntil) return;
        lockUntil = now + 600;
        click();
        MenuNet.CHANNEL.sendToServer(new ScreenActionPacket(screenId, action, a, b, text));
        if (close) onClose();
    }

    @Override
    public void removed() {
        MenuNet.CHANNEL.sendToServer(new CloseMenuPacket(screenId));
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ aperçu 3D

    protected void resetView() {
        yaw = 210;
        pitch = 18;
        lastDrag = 0;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (inside(mx, my, px, py, pw, ph)) dragging = true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            yaw += (float) dx * 1.6f;
            pitch = Math.max(-10, Math.min(60, pitch + (float) dy * 0.8f));
            lastDrag = System.currentTimeMillis();
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    /** Showroom + véhicule ; tourne seul tant qu'on ne l'a pas fait tourner depuis 3 s. */
    protected void renderPreview(GuiGraphics g, String itemId) {
        long now = System.currentTimeMillis();
        if (now - lastDrag > 3000) yaw += (now - lastFrame) * 0.02f;
        lastFrame = now;
        rfill(g, px, py, px + pw, py + ph, 5, LIST);
        g.enableScissor(px + 2, py + 2, px + pw - 2, py + ph - 2);
        showroom(g, px + 2, py + 2, pw - 4, ph - 4);
        if (itemId != null && !itemId.isEmpty()) {
            VehiclePreview.render(g, itemId, px + pw / 2, py + ph * 55 / 100, Math.min(pw * 0.62f, ph * 1.1f), yaw, pitch);
        }
        g.disableScissor();
    }

    /** Panneau de la charte MineNorth + aide en bas à droite. */
    protected void renderWindow(GuiGraphics g, String title, String subtitle) {
        renderBackground(g);
        panel(g, left, top, W, H, title, subtitle);
        String hint = "Échap : fermer";
        g.drawString(font, hint, left + W - 12 - font.width(hint), top + H - 12, MUTED, false);
    }

    /** Liste à défilement : barre fine à droite. */
    protected void scrollBar(GuiGraphics g, int x, int y, int h, int scroll, int visible, int total) {
        if (total <= visible) return;
        int barH = Math.max(12, h * visible / total);
        int barY = y + (h - barH) * scroll / Math.max(1, total - visible);
        rfill(g, x, y, x + 3, y + h, 1, LIST);
        rfill(g, x, barY, x + 3, barY + barH, 1, DARK);
    }
}
