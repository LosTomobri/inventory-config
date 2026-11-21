package com.inventoryconfig.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Acceso a imageHeight, topPos y renderBg de pantallas de contenedor ajenas (Curios, hornos, tolva, etc.),
 * sin necesitar subclases ni dependencias. Si algo no se puede tocar, el mod sigue andando y esas
 * pantallas quedan sin achicar (solo se tapan los slots apagados).
 */
final class ScreenHeight {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Field IMAGE_HEIGHT = findField("imageHeight");
    private static final Field TOP_POS = findField("topPos");
    private static final Method RENDER_BG = findMethod();

    private ScreenHeight() {
    }

    private static Field findField(String name) {
        try {
            Field f = AbstractContainerScreen.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Inventory Config: no pude acceder a {}, las pantallas ajenas no se achican", name, e);
            return null;
        }
    }

    private static Method findMethod() {
        try {
            Method m = AbstractContainerScreen.class.getDeclaredMethod(
                    "renderBg", GuiGraphics.class, float.class, int.class, int.class);
            m.setAccessible(true);
            return m;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Inventory Config: no pude acceder a renderBg, las pantallas ajenas no se achican", e);
            return null;
        }
    }

    static boolean isSupported() {
        return IMAGE_HEIGHT != null && TOP_POS != null && RENDER_BG != null;
    }

    static int get(AbstractContainerScreen<?> screen) {
        return getInt(IMAGE_HEIGHT, screen);
    }

    static void set(AbstractContainerScreen<?> screen, int height) {
        setInt(IMAGE_HEIGHT, screen, height);
    }

    /** topPos: lo usan algunas pantallas para dibujar el fondo (otras lo calculan con imageHeight). */
    static void setTop(AbstractContainerScreen<?> screen, int top) {
        setInt(TOP_POS, screen, top);
    }

    private static int getInt(Field field, AbstractContainerScreen<?> screen) {
        if (field == null) return -1;
        try {
            return field.getInt(screen);
        } catch (IllegalAccessException e) {
            return -1;
        }
    }

    private static void setInt(Field field, AbstractContainerScreen<?> screen, int value) {
        if (field == null) return;
        try {
            field.setInt(screen, value);
        } catch (IllegalAccessException e) {
            LOGGER.warn("Inventory Config: no pude cambiar {}", field.getName(), e);
        }
    }

    /** Vuelve a dibujar el fondo de la pantalla (llama a su renderBg real). */
    static void renderBg(AbstractContainerScreen<?> screen, GuiGraphics g, int mouseX, int mouseY) {
        if (RENDER_BG == null) return;
        try {
            RENDER_BG.invoke(screen, g, 0.0F, mouseX, mouseY);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Inventory Config: fallo al redibujar el fondo", e);
        }
    }
}
