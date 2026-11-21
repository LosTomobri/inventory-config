package com.inventoryconfig.client;

import com.inventoryconfig.ConfigActionPayload;
import com.inventoryconfig.ConfigData;
import com.inventoryconfig.ExtraBar;
import com.inventoryconfig.ExtraLayout;
import com.inventoryconfig.LayoutBundle;
import com.inventoryconfig.ModState;
import com.inventoryconfig.Profile;
import com.inventoryconfig.RadialLayout;
import com.inventoryconfig.RulesLayout;
import com.inventoryconfig.RuleItem;
import com.inventoryconfig.SlotRule;
import com.inventoryconfig.UiOptions;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Pantalla de configuracion (/inventoryconfig). Muestra el inventario completo, centrado; click (o arrastrar)
 * sobre un slot lo deshabilita (rojo) o lo vuelve a habilitar. "Save" lo aplica a TODOS los jugadores del
 * servidor. Tambien tiene los modos (survival / creative), el boton de slots extra
 * y los perfiles. Los textos salen de lang/en_us.json.
 * <p>
 * Los cambios quedan en esta pantalla hasta apretar "Save" (los perfiles se guardan aparte).
 */
public class ConfigScreen extends Screen {

    static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/inventory.png");

    private static final String LANG = "inventoryconfig.";

    static final int PANEL_W = 176;
    static final int PANEL_H = 166;
    private static final int SIDE_W = 130;
    private static final int GAP = 8;
    private static final int BTN_H = 18;
    private static final int STEP = 20;
    private static final int LIST_ROWS = 3;
    /** Cuanto dura armado el boton Reset ("Confirm?") antes de volver a su texto normal. */
    private static final long RESET_ARMED_MS = 4000L;
    static final int PANEL_COLOR = 0xFFC6C6C6;

    /** Casillas del inventario: {slot de Inventory, x, y} con las mismas posiciones que el inventario de vanilla. */
    static final int[][] CELLS = buildCells();

    long mask;
    private boolean survival;
    private boolean creative;
    /** Hotbars extra y "Hide in Inventory": se editan aca y se mandan al apretar Save (UI en la fase 2). */
    List<ExtraBar> extraBars;
    boolean hideAssigned;
    /** Reglas de slot y radial: tambien se editan aca y se mandan con Save / perfiles (UI en las fases 2 y 5). */
    RulesLayout rules;
    RadialLayout radial;
    /** Ocultar crafteo / recetario del inventario: tambien se editan aca y se mandan con Save / perfiles. */
    UiOptions ui;
    private String nameValue = "";
    private int scroll = 0;
    private Component status = null;
    /** Valor que se esta "pintando" mientras se arrastra el mouse (null = no se arrastra). */
    private Boolean paint = null;

    private int left;
    private int top;
    /** Y del rotulo "Profiles" (depende de cuantos botones hay arriba). */
    private int profilesLabelY;
    /** Filas visibles de la lista de perfiles (depende del alto de la pantalla). */
    private int listRows = LIST_ROWS;
    private EditBox nameBox;
    private Button resetButton;
    private boolean resetArmed = false;
    private long resetDeadline = 0L;

    public ConfigScreen() {
        super(Component.translatable(LANG + "gui.title"));
        this.mask = ModState.disabledMask;
        this.survival = ModState.survival;
        this.creative = ModState.creative;
        this.extraBars = new ArrayList<>(ModState.extra.bars());
        this.hideAssigned = ModState.extra.hideAssigned();
        this.rules = ModState.rules;
        this.radial = ModState.radial;
        this.ui = ModState.ui;
    }

    /** Deja las barras validas: un slot asignado que se deshabilito vuelve a "U". Lo llaman las pantallas de slots extra. */
    void cleanExtra() {
        extraBars = new ArrayList<>(ExtraLayout.sanitize(extraBars, hideAssigned, mask).bars());
        rules = RulesLayout.sanitize(rules, mask);
        radial = RadialLayout.sanitize(radial, mask);
    }

    private ExtraLayout layout() {
        return new ExtraLayout(List.copyOf(extraBars), hideAssigned);
    }

    /** Todo el layout del borrador, listo para mandar al servidor (que lo vuelve a validar). */
    private LayoutBundle bundle() {
        return new LayoutBundle(mask, layout(), rules, radial, ui);
    }

    /**
     * X del inventario, compartida con los selectores de slot para que siempre quede en el mismo lugar: centrado, y si
     * en pantallas chicas no entran las dos columnas se corre (la de la izquierda tiene prioridad).
     */
    static int panelLeft(int width) {
        int l = (width - PANEL_W) / 2;
        l = Math.min(l, width - 4 - SIDE_W - GAP - PANEL_W);
        return Math.max(l, 4 + SIDE_W + GAP);
    }

    private static int[][] buildCells() {
        List<int[]> cells = new ArrayList<>();
        for (int i = 0; i < 4; i++) cells.add(new int[]{39 - i, 8, 8 + 18 * i});   // helmet ... boots
        cells.add(new int[]{40, 77, 62});                                          // offhand
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) cells.add(new int[]{9 + r * 9 + c, 8 + 18 * c, 84 + 18 * r});
        }
        for (int c = 0; c < 9; c++) cells.add(new int[]{c, 8 + 18 * c, 142});      // hotbar
        return cells.toArray(new int[0][]);
    }

    /** Lo llama ModState.configHook cuando llegan los perfiles. */
    public static void onProfiles(boolean open) {
        Minecraft mc = Minecraft.getInstance();
        if (open) {
            mc.setScreen(new ConfigScreen());
        } else if (mc.screen instanceof ConfigScreen screen) {
            screen.rebuildWidgets();
        }
    }

    // ---------------------------------------------------------------- widgets

    @Override
    protected void init() {
        // El inventario va en el medio: a la izquierda modos y perfiles, a la derecha el resto.
        left = panelLeft(width);
        // Inventario + fila de botones de abajo, centrados en vertical.
        top = Math.max(20, (height - PANEL_H - 26) / 2);

        int lx = left - GAP - SIDE_W;
        int rx = left + PANEL_W + GAP;

        // ---- columna izquierda: Survival, Creative, Enable All y perfiles
        int y = top;
        addRenderableWidget(Button.builder(modeText("survival", survival), b -> {
            survival = !survival;
            b.setMessage(modeText("survival", survival));
        }).bounds(lx, y, SIDE_W, BTN_H).build());
        y += STEP;
        addRenderableWidget(Button.builder(modeText("creative", creative), b -> {
            creative = !creative;
            b.setMessage(modeText("creative", creative));
        }).bounds(lx, y, SIDE_W, BTN_H).build());
        y += STEP;
        addRenderableWidget(Button.builder(Component.translatable(LANG + "gui.enable_all"), b -> mask = 0L)
                .bounds(lx, y, SIDE_W, BTN_H).build());
        y += STEP;

        // Perfiles
        profilesLabelY = y + 2;
        y += 12; // lugar para el rotulo "Profiles"
        nameBox = new EditBox(font, lx, y, SIDE_W, BTN_H, Component.translatable(LANG + "gui.profile_name"));
        nameBox.setMaxLength(ConfigData.MAX_NAME);
        nameBox.setHint(Component.translatable(LANG + "gui.profile_name"));
        nameBox.setValue(nameValue);
        nameBox.setResponder(s -> nameValue = s);
        addRenderableWidget(nameBox);
        y += STEP;
        addRenderableWidget(Button.builder(Component.translatable(LANG + "gui.save_profile"), b -> saveProfile())
                .bounds(lx, y, SIDE_W, BTN_H).build());
        y += STEP + 2;

        // En pantallas chicas la lista de perfiles se achica (minimo 1 fila).
        listRows = Math.max(1, Math.min(LIST_ROWS, (height - 6 - y) / STEP));
        List<String> names = new ArrayList<>(ModState.profiles.keySet());
        int maxScroll = Math.max(0, names.size() - listRows);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        for (int row = 0; row < listRows; row++) {
            int idx = scroll + row;
            if (idx >= names.size()) break;
            String name = names.get(idx);
            addRenderableWidget(Button.builder(Component.literal(name), b -> loadProfile(name))
                    .bounds(lx, y, SIDE_W - 22, BTN_H).build());
            addRenderableWidget(Button.builder(Component.literal("X"), b -> deleteProfile(name))
                    .bounds(lx + SIDE_W - 20, y, 20, BTN_H).build());
            y += STEP;
        }

        // ---- columna derecha: Extra Slots, Slot Rules, Radial Menu, Hide Crafting y Hide Recipes
        y = top;
        // Extra slots in game: hotbars extra (se guardan con Save).
        addRenderableWidget(Button.builder(Component.translatable(LANG + "gui.extra_slots"),
                        b -> minecraft.setScreen(new ExtraSlotsScreen(this)))
                .bounds(rx, y, SIDE_W, BTN_H).build());
        y += STEP;
        // Slot Rules: item fijo / exclusivo / no soltar para los 41 slots del inventario.
        addRenderableWidget(Button.builder(Component.translatable(LANG + "gui.slot_rules"),
                        b -> minecraft.setScreen(new SlotRulesScreen(this)))
                .bounds(rx, y, SIDE_W, BTN_H).build());
        y += STEP;
        // Radial Menu: 8 casillas, un slot del inventario principal por casilla (global, se guarda con Save).
        addRenderableWidget(Button.builder(Component.translatable(LANG + "gui.radial"),
                        b -> minecraft.setScreen(new RadialConfigScreen(this)))
                .bounds(rx, y, SIDE_W, BTN_H).build());
        y += STEP;

        // Hide Crafting / Hide Recipes: sacan del inventario del juego la grilla de crafteo y el recetario.
        addRenderableWidget(Button.builder(uiText("hide_crafting", ui.hideCrafting()), b -> {
            ui = new UiOptions(!ui.hideCrafting(), ui.hideRecipes(), ui.hideCuriosButton(), ui.showCuriosSlots());
            b.setMessage(uiText("hide_crafting", ui.hideCrafting()));
        }).bounds(rx, y, SIDE_W, BTN_H).build());
        y += STEP;
        addRenderableWidget(Button.builder(uiText("hide_recipes", ui.hideRecipes()), b -> {
            ui = new UiOptions(ui.hideCrafting(), !ui.hideRecipes(), ui.hideCuriosButton(), ui.showCuriosSlots());
            b.setMessage(uiText("hide_recipes", ui.hideRecipes()));
        }).bounds(rx, y, SIDE_W, BTN_H).build());
        y += STEP;
        // Curios: abre su pantalla de opciones. Solo existe si el mod Curios esta instalado.
        if (CuriosCompat.loaded()) {
            addRenderableWidget(Button.builder(Component.translatable(LANG + "gui.curios"),
                            b -> minecraft.setScreen(new CuriosConfigScreen(this)))
                    .bounds(rx, y, SIDE_W, BTN_H).build());
            y += STEP;
        }

        // Save / Cancel, debajo del inventario
        int by = top + PANEL_H + 6;
        addRenderableWidget(Button.builder(Component.translatable(LANG + "gui.save"), b -> apply())
                .bounds(left, by, 86, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(LANG + "gui.cancel"), b -> onClose())
                .bounds(left + 90, by, 86, 20).build());
        // Reset: abajo de Save / Cancel y centrado entre los dos. El primer click lo arma ("Confirm?" en rojo),
        // el segundo deja todo como viene de fabrica.
        resetButton = Button.builder(resetText(), b -> onResetClicked())
                .bounds(left + 88 - 43, by + 22, 86, 20).build();
        addRenderableWidget(resetButton);
    }

    private Component resetText() {
        return resetArmed
                ? Component.translatable(LANG + "gui.reset.confirm").withStyle(ChatFormatting.RED)
                : Component.translatable(LANG + "gui.reset");
    }

    private static Component uiText(String key, boolean on) {
        return Component.translatable(LANG + "gui." + key,
                Component.translatable(LANG + (on ? "gui.on" : "gui.off")));
    }

    private static Component modeText(String mode, boolean on) {
        return Component.translatable(LANG + "gui." + mode,
                Component.translatable(LANG + (on ? "gui.on" : "gui.off")));
    }

    // ---------------------------------------------------------------- acciones

    private void apply() {
        PacketDistributor.sendToServer(new ConfigActionPayload(
                ConfigActionPayload.APPLY, "", survival, creative, bundle()));
        onClose();
    }

    private void onResetClicked() {
        if (!resetArmed) {
            resetArmed = true;
            resetDeadline = Util.getMillis() + RESET_ARMED_MS;
            resetButton.setMessage(resetText());
            return;
        }
        resetAll();
    }

    private void disarmReset() {
        resetArmed = false;
        if (resetButton != null) resetButton.setMessage(resetText());
    }

    /**
     * Deja el borrador como viene de fabrica (los mismos valores que tiene {@link ConfigData} al crearse): sin slots
     * deshabilitados, ni barras extra, ni reglas, ni radial, ni crafteo / recetario ocultos, con Survival activo y Creative apagado. Los perfiles
     * guardados no se tocan. Como el resto de los cambios, se aplica al servidor recien con Save.
     */
    private void resetAll() {
        ConfigData d = new ConfigData();
        mask = d.disabledMask;
        survival = d.survival;
        creative = d.creative;
        extraBars = new ArrayList<>(d.extraBars);
        hideAssigned = d.hideAssignedSlots;
        rules = d.rules;
        radial = d.radial;
        ui = d.ui;
        resetArmed = false;
        status = Component.translatable(LANG + "gui.status.reset");
        rebuildWidgets();
    }

    @Override
    public void tick() {
        if (resetArmed && Util.getMillis() > resetDeadline) disarmReset();
    }

    private void saveProfile() {
        String name = nameValue.trim();
        if (name.isEmpty()) {
            status = Component.translatable(LANG + "gui.status.name_required");
            return;
        }
        PacketDistributor.sendToServer(new ConfigActionPayload(
                ConfigActionPayload.SAVE_PROFILE, name, survival, creative, bundle()));
        status = Component.translatable(LANG + "gui.status.saved", name);
    }

    private void loadProfile(String name) {
        Profile saved = ModState.profiles.get(name);
        if (saved == null) return;
        mask = saved.mask() & ConfigData.ALL;
        extraBars = new ArrayList<>(saved.extra().bars());
        hideAssigned = saved.extra().hideAssigned();
        rules = saved.rules();
        radial = saved.radial();
        ui = saved.ui();
        cleanExtra();
        nameValue = name;
        nameBox.setValue(name);
        status = Component.translatable(LANG + "gui.status.loaded", name);
    }

    private void deleteProfile(String name) {
        PacketDistributor.sendToServer(new ConfigActionPayload(
                ConfigActionPayload.DELETE_PROFILE, name, false, false, LayoutBundle.EMPTY));
        status = Component.translatable(LANG + "gui.status.deleted", name);
    }

    @Override
    public boolean isPauseScreen() {
        // En un mundo de un jugador, pausar frenaria al servidor justo cuando le mandamos los cambios.
        return false;
    }

    // ---------------------------------------------------------------- slots

    boolean isOff(int slot) {
        return (mask >> slot & 1L) != 0L;
    }

    private void set(int slot, boolean off) {
        if (off) mask |= 1L << slot;
        else mask &= ~(1L << slot);
    }

    private int cellAt(double mx, double my) {
        for (int i = 0; i < CELLS.length; i++) {
            int x = left + CELLS[i][1] - 1;
            int y = top + CELLS[i][2] - 1;
            if (mx >= x && mx < x + 18 && my >= y && my < y + 18) return i;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Un click en cualquier otro lado cancela el "Confirm?" del Reset.
        if (resetArmed && (resetButton == null || !resetButton.isMouseOver(mx, my))) disarmReset();
        if (button == 0) {
            int i = cellAt(mx, my);
            if (i >= 0) {
                paint = !isOff(CELLS[i][0]);
                set(CELLS[i][0], paint);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (paint != null && button == 0) {
            int i = cellAt(mx, my);
            if (i >= 0) set(CELLS[i][0], paint);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        paint = null;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (mx < left - GAP / 2.0 && scrollY != 0 && ModState.profiles.size() > listRows) {
            scroll += scrollY < 0 ? 1 : -1;
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    static Component slotName(int slot) {
        return switch (slot) {
            case 39 -> Component.translatable(LANG + "slot.helmet");
            case 38 -> Component.translatable(LANG + "slot.chestplate");
            case 37 -> Component.translatable(LANG + "slot.leggings");
            case 36 -> Component.translatable(LANG + "slot.boots");
            case 40 -> Component.translatable(LANG + "slot.offhand");
            default -> slot < 9
                    ? Component.translatable(LANG + "slot.hotbar", slot + 1)
                    : Component.translatable(LANG + "slot.inventory", slot - 8);
        };
    }

    // ---------------------------------------------------------------- dibujo

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        super.render(g, mx, my, partialTick); // fondo y widgets

        g.drawCenteredString(font, title, left + PANEL_W / 2, top - 14, 0xFFFFFFFF);

        // Inventario completo, con la textura de vanilla.
        g.blit(TEXTURE, left, top, 0, 0, PANEL_W, PANEL_H);
        // Tapa el crafteo (no se configura) y deja la ayuda y la leyenda de las marcas.
        drawLegend(g);

        int hover = cellAt(mx, my);
        for (int i = 0; i < CELLS.length; i++) {
            int x = left + CELLS[i][1] - 1;
            int y = top + CELLS[i][2] - 1;
            if (isOff(CELLS[i][0])) {
                g.fill(x, y, x + 18, y + 18, 0xC0D02020);
                g.drawCenteredString(font, "X", x + 9, y + 5, 0xFFFFFFFF);
            } else {
                drawSlotConfig(g, CELLS[i][0], x, y); // reglas, hotbar extra y radial
            }
            if (i == hover) {
                g.fill(x + 1, y + 1, x + 17, y + 17, 0x80FFFFFF);
            }
        }

        g.drawString(font, Component.translatable(LANG + "gui.profiles"), left - GAP - SIDE_W, profilesLabelY, 0xFFFFFFFF);
        if (status != null) {
            g.drawWordWrap(font, status, left, top + PANEL_H + 52, PANEL_W, 0xFFFFFF55);
        }

        if (hover >= 0) {
            int slot = CELLS[hover][0];
            Component name = slotName(slot);
            List<Component> tip = new ArrayList<>();
            tip.add(isOff(slot) ? Component.translatable(LANG + "slot.disabled", name) : name);
            if (!isOff(slot)) tip.addAll(slotConfigLines(slot));
            g.renderTooltip(font, tip, Optional.empty(), mx, my);
        }
    }

    // ---------------------------------------------------------------- config reflejada en el inventario

    private static final int C_BAR = 0xFF55FFFF;     // numero de hotbar extra
    private static final int C_RADIAL = 0xFFFFAA00;  // numero de casilla del radial
    private static final int C_EXCL = 0xFFFFFF55;    // exclusivo
    private static final int C_DROP = 0xFFFF5555;    // no soltar

    /** Numero (1..8) de la hotbar extra a la que pertenece {@code slot}, o 0. */
    private int barOf(int slot) {
        for (int b = 0; b < extraBars.size(); b++) {
            if (extraBars.get(b).slots().contains(slot)) return b + 1;
        }
        return 0;
    }

    /** Numero (1..8) de la casilla del radial que usa {@code slot}, o 0. */
    private int radialCellOf(int slot) {
        int i = radial.cells().indexOf(slot);
        return i < 0 ? 0 : i + 1;
    }

    /**
     * Lo que tiene configurado un slot, dibujado sobre su casilla: item fijo (el item atenuado), numero de hotbar
     * extra (arriba a la izquierda), numero de casilla del radial (arriba a la derecha), E = exclusivo (abajo a la
     * izquierda) y D = no soltar (abajo a la derecha). Va con un z alto para quedar por encima del fondo.
     */
    private void drawSlotConfig(GuiGraphics g, int slot, int x, int y) {
        SlotRule r = rules.get(slot);
        RuleItem shown = RuleVisuals.preview(r, slot);
        if (shown != null) {
            g.renderFakeItem(SlotPickerBase.stackOf(shown), x + 1, y + 1);
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.fill(x + 1, y + 1, x + 17, y + 17, 0x90808080);
            g.pose().popPose();
        }
        int bar = barOf(slot);
        int cell = radialCellOf(slot);
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        if (bar > 0) g.drawString(font, String.valueOf(bar), x + 2, y + 1, C_BAR, true);
        if (cell > 0) g.drawString(font, String.valueOf(cell), x + 11, y + 1, C_RADIAL, true);
        if (r.exclusive()) g.drawString(font, "E", x + 2, y + 9, C_EXCL, true);
        if (r.noDrop()) g.drawString(font, "D", x + 11, y + 9, C_DROP, true);
        g.pose().popPose();
    }

    /** Recuadro oscuro sobre el crafteo (que no se configura): la ayuda y la leyenda de las marcas. */
    private void drawLegend(GuiGraphics g) {
        int x = left + 97;
        int y = top + 6;
        g.fill(left + 95, top + 6, left + 172, top + 56, 0xFF202020);
        int ly = y + 4;
        ly = legendLine(g, x, ly, "#", C_BAR, "legend.bar");
        ly = legendLine(g, x, ly, "#", C_RADIAL, "legend.radial");
        ly = legendLine(g, x, ly, "E", C_EXCL, "legend.exclusive");
        legendLine(g, x, ly, "D", C_DROP, "legend.nodrop");
    }

    private int legendLine(GuiGraphics g, int x, int y, String glyph, int color, String key) {
        g.drawString(font, glyph, x, y, color, false);
        g.drawString(font, Component.translatable(LANG + key), x + 9, y, 0xFFAAAAAA, false);
        return y + 10;
    }

    /** Lineas extra del tooltip de un slot: a que hotbar extra y casilla del radial pertenece. */
    private List<Component> slotConfigLines(int slot) {
        List<Component> lines = new ArrayList<>();
        int bar = barOf(slot);
        if (bar > 0) {
            ExtraBar b = extraBars.get(bar - 1);
            lines.add(Component.translatable(LANG + "tip.bar", bar,
                    Component.translatable(LANG + "extra.position." + b.position().name().toLowerCase(Locale.ROOT)))
                    .withStyle(ChatFormatting.AQUA));
        }
        int cell = radialCellOf(slot);
        if (cell > 0) {
            lines.add(Component.translatable(LANG + "tip.radial", cell).withStyle(ChatFormatting.GOLD));
        }
        return lines;
    }
}
