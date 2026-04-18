package com.mentalfrostbyte.jello.gui.impl.jello.ingame.clickgui.musicplayer.elements;

import com.mentalfrostbyte.jello.managers.GuiManager;
import com.mentalfrostbyte.jello.util.client.render.FontSizeAdjust;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.newdawn.slick.opengl.Texture;
import org.newdawn.slick.util.BufferedImageUtil;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renders CJK-safe text for the MusicPlayer using Java2D → GL texture approach.
 * <p>
 * The previous Slick2D TrueTypeFont approach failed because its fixed-size texture
 * atlas (1024×512) cannot hold all ~20K CJK Unified Ideograph glyphs — only the
 * first ~1300 characters that fit would render; the rest appeared as tofu blocks.
 * <p>
 * This implementation renders each text string via {@code Graphics2D.drawString()}
 * into a {@link BufferedImage}, uploads it as a GL texture, and caches results in
 * an LRU map to avoid per-frame re-creation.
 */
public final class MusicPlayerTextHelper {

    /** Font used for tab button text (normal DPI). */
    private static final Font TAB_AWT_FONT = pickCjkFont(14);
    /** Font used for tab button text (2× Retina). */
    private static final Font TAB_AWT_FONT_2X = pickCjkFont(28);
    /** Font used for right-panel title text (normal DPI). */
    private static final Font TITLE_AWT_FONT = pickCjkFont(25);
    /** Font used for right-panel title text (2× Retina). */
    private static final Font TITLE_AWT_FONT_2X = pickCjkFont(50);

    /** Maximum number of cached text textures before LRU eviction kicks in. */
    private static final int MAX_CACHE_SIZE = 128;

    /** LRU cache: cacheKey → CachedText (texture + dimensions). */
    private static final Map<String, CachedText> textCache =
            new LinkedHashMap<String, CachedText>(MAX_CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedText> eldest) {
                    if (size() > MAX_CACHE_SIZE) {
                        eldest.getValue().dispose();
                        return true;
                    }
                    return false;
                }
            };

    private MusicPlayerTextHelper() {
    }

    // ── public API ──────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the string contains any character above ASCII 127.
     * This is used as the fast gate to decide whether the CJK rendering path is needed.
     */
    public static boolean containsNonAscii(String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > 127) {
                return true;
            }
        }
        return false;
    }

    /**
     * Draw a tab button label, using CJK-safe Java2D rendering.
     */
    public static void drawTabText(float x, float y, String text, int color,
                                   FontSizeAdjust widthAdjust, FontSizeAdjust heightAdjust) {
        drawText(TAB_AWT_FONT, TAB_AWT_FONT_2X, 14, x, y, text, color, widthAdjust, heightAdjust);
    }

    /**
     * Returns tab-font text width in GUI coordinates, including HiDPI scaling.
     */
    public static float getTabTextWidth(String text) {
        if (text == null || text.isEmpty()) {
            return 0.0F;
        }

        boolean useHiRes = (double) GuiManager.scaleFactor == 2.0;
        float scale = useHiRes ? 1.0F / GuiManager.scaleFactor : 1.0F;
        String cacheKey = buildCacheKey(text, 14, useHiRes);
        CachedText cached = textCache.get(cacheKey);
        if (cached != null) {
            return cached.imgWidth * scale;
        }

        Font renderFont = useHiRes ? TAB_AWT_FONT_2X : TAB_AWT_FONT;
        return measureTextWidth(renderFont, text) * scale;
    }

    /**
     * Draw the right-panel song title, using CJK-safe Java2D rendering.
     */
    public static void drawTitleText(float x, float y, String text, int color) {
        drawText(TITLE_AWT_FONT, TITLE_AWT_FONT_2X, 25, x, y, text, color,
                FontSizeAdjust.NEGATE_AND_DIVIDE_BY_2, FontSizeAdjust.field14489);
    }

    // ── internals ───────────────────────────────────────────────────────

    private static void drawText(Font font, Font hiResFont, int fontSize, float x, float y,
                                 String text, int color,
                                 FontSizeAdjust widthAdjust, FontSizeAdjust heightAdjust) {
        if (text == null || text.isEmpty()) {
            return;
        }

        boolean useHiRes = (double) GuiManager.scaleFactor == 2.0;
        Font renderFont = useHiRes ? hiResFont : font;
        float scale = useHiRes ? 1.0F / GuiManager.scaleFactor : 1.0F;

        // ── obtain or create cached texture ──
        String cacheKey = buildCacheKey(text, fontSize, useHiRes);
        CachedText cached = textCache.get(cacheKey);
        if (cached == null) {
            cached = renderToTexture(renderFont, text);
            if (cached == null) {
                return; // fallback: silently skip on failure
            }
            textCache.put(cacheKey, cached);
        }

        float displayWidth = cached.imgWidth * scale;
        float displayHeight = cached.imgHeight * scale;

        // ── alignment offsets ──
        float offsetX = switch (widthAdjust) {
            case NEGATE_AND_DIVIDE_BY_2 -> -displayWidth / 2.0F;
            case WIDTH_NEGATE -> -displayWidth;
            default -> 0;
        };
        float offsetY = switch (heightAdjust) {
            case NEGATE_AND_DIVIDE_BY_2 -> -displayHeight / 2.0F;
            case HEIGHT_NEGATE -> -displayHeight;
            default -> 0;
        };

        float drawX = (float) Math.round(x + offsetX);
        float drawY = (float) Math.round(y + offsetY);

        // ── extract colour components ──
        float a = (float) (color >> 24 & 0xFF) / 255.0F;
        float r = (float) (color >> 16 & 0xFF) / 255.0F;
        float g = (float) (color >> 8 & 0xFF) / 255.0F;
        float b = (float) (color & 0xFF) / 255.0F;

        // ── draw textured quad (save & restore full GL state to avoid polluting Slick2D) ──
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            cached.texture.bind();

            // The texture was rendered with white text; tint it via glColor.
            GL11.glColor4f(r, g, b, a);

            float u = cached.texture.getWidth();   // widthRatio  = imgW / texW
            float v = cached.texture.getHeight();  // heightRatio = imgH / texH

            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0, 0);
            GL11.glVertex2f(drawX, drawY);
            GL11.glTexCoord2f(0, v);
            GL11.glVertex2f(drawX, drawY + displayHeight);
            GL11.glTexCoord2f(u, v);
            GL11.glVertex2f(drawX + displayWidth, drawY + displayHeight);
            GL11.glTexCoord2f(u, 0);
            GL11.glVertex2f(drawX + displayWidth, drawY);
            GL11.glEnd();
        } finally {
            GL11.glPopAttrib();
            // glPopAttrib restores the real GL color, but GlStateManager still
            // caches our old (r,g,b,a) values.  Invalidate the cache so the next
            // RenderSystem.color4f / GlStateManager.color4f call actually issues
            // a GL11.glColor4f instead of being silently skipped.
            RenderSystem.clearCurrentColor();
        }
    }

    private static String buildCacheKey(String text, int fontSize, boolean useHiRes) {
        return text + "|" + fontSize + "|" + (useHiRes ? "2x" : "1x");
    }

    private static int measureTextWidth(Font font, String text) {
        BufferedImage measure = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gMeasure = measure.createGraphics();
        gMeasure.setFont(font);
        FontMetrics fm = gMeasure.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        gMeasure.dispose();
        return Math.max(textWidth + 2, 1);
    }

    /**
     * Renders the given text into a {@link BufferedImage} using Java2D and uploads
     * it as a GL texture via Slick's {@link BufferedImageUtil}.
     */
    private static CachedText renderToTexture(Font font, String text) {
        // ── measure ──
        BufferedImage measure = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gMeasure = measure.createGraphics();
        gMeasure.setFont(font);
        FontMetrics fm = gMeasure.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int textHeight = fm.getHeight();
        gMeasure.dispose();

        if (textWidth <= 0) textWidth = 1;
        if (textHeight <= 0) textHeight = 1;

        // add 1px padding right to avoid clipping of rightmost antialiased pixel
        int imgW = textWidth + 2;
        int imgH = textHeight;

        // ── render white text on transparent background ──
        BufferedImage image = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setFont(font);
        g2d.setColor(Color.WHITE);
        g2d.drawString(text, 0, fm.getAscent());
        g2d.dispose();

        // ── upload as GL texture ──
        try {
            Texture texture = BufferedImageUtil.getTexture(
                    "musictext_" + text.hashCode() + "_" + font.getSize(), image);
            return new CachedText(texture, imgW, imgH);
        } catch (IOException e) {
            System.err.println("[MusicPlayerTextHelper] Failed to create texture for text: " + text);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Pick a CJK-capable AWT font.  Tries "Microsoft YaHei" first, then falls back
     * to "SimHei", "NSimSun", or the JRE's logical "Dialog" font.
     */
    private static Font pickCjkFont(int size) {
        String[] candidates = {"Microsoft YaHei", "SimHei", "NSimSun", "Dialog"};
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] available = ge.getAvailableFontFamilyNames();

        for (String name : candidates) {
            for (String avail : available) {
                if (avail.equalsIgnoreCase(name)) {
                    return new Font(name, Font.PLAIN, size);
                }
            }
        }
        // ultimate fallback
        return new Font(Font.SANS_SERIF, Font.PLAIN, size);
    }

    // ── inner cache entry ───────────────────────────────────────────────

    private static final class CachedText {
        final Texture texture;
        final int imgWidth;
        final int imgHeight;

        CachedText(Texture texture, int imgWidth, int imgHeight) {
            this.texture = texture;
            this.imgWidth = imgWidth;
            this.imgHeight = imgHeight;
        }

        void dispose() {
            try {
                GL11.glDeleteTextures(texture.getTextureID());
            } catch (Exception ignored) {
                // best-effort cleanup
            }
        }
    }
}
