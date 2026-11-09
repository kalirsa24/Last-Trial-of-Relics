package com.example.festival.client;

import com.example.festival.core.RelicTier;
import com.example.festival.core.ShopManager;
import com.example.festival.network.BuyItemPayload;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Магазин: вкладки сверху + сетка карточек со страницами (колесо мыши).
 *
 * Ассортимент фильтруется по уровню Реликвии, на которой открыт магазин:
 * пистолеты — везде, ПП и винтовки — с 25 волн, остальное — только с вечной.
 *
 * Название на карточке переносится на две строки; если и в две не влезло —
 * при наведении всплывает полное название и цена.
 */
public class ShopScreen extends Screen {

	private static final int CARD_W = 116;
	private static final int CARD_H = 60;
	private static final int GAP = 6;
	private static final int PAD = 10;
	private static final int HEADER_H = 40;
	private static final int TAB_H = 20;
	private static final int TAB_GAP = 3;
	private static final int TABS_TOP = 6;
	private static final int FOOTER_H = 22;
	private static final int TAB_MIN_W = 44;

	private static final int C_DIM = 0x99000000;
	private static final int C_PANEL = 0xF01A1A1A;
	private static final int C_PANEL_BORDER = 0xFF3D3D3D;
	private static final int C_HEADER = 0xFF242424;
	private static final int C_CARD = 0xFF2B2B2B;
	private static final int C_CARD_HOVER = 0xFF3C3C3C;
	private static final int C_TEXT = 0xFFE6E6E6;
	private static final int C_DIM_TEXT = 0xFF9A9A9A;
	private static final int C_GOLD = 0xFFFFC24B;
	private static final int C_RED = 0xFFE05252;
	private static final int C_GREEN = 0xFF6FCF7A;

	private final ShopManager.Category[] tabs = ShopManager.Category.values();
	private final int[] pages = new int[tabs.length];

	private int coins;
	private int lives;
	private int maxLives;
	private int totemTier;
	private int trophies;
	private int wave;
	private int maxWaves;
	private int stage;
	private int stages;
	private int stopPrice;
	private boolean paused;
	private int priceMultiplier = 100;
	private RelicTier tier = RelicTier.GREATER;
	private int tab = 0;

	private List<ShopManager.Good> current = List.of();

	private static ShopScreen active = null;

	public ShopScreen(int coins, int lives, int maxLives, int totemTier, int tier, int trophies,
					  int wave, int maxWaves, int stage, int stages, int stopPrice, boolean paused,
					  int priceMultiplier) {
		super(Component.translatable("screen.festival.shop"));
		this.coins = coins;
		this.lives = lives;
		this.maxLives = maxLives;
		this.totemTier = totemTier;
		this.tier = RelicTier.byOrdinal(tier);
		this.trophies = trophies;
		this.wave = wave;
		this.maxWaves = maxWaves;
		this.stage = stage;
		this.stages = stages;
		this.stopPrice = stopPrice;
		this.paused = paused;
		this.priceMultiplier = priceMultiplier <= 0 ? 100 : priceMultiplier;
		rebuildTab();
		active = this;
	}

	public static void updateTrial(int coins, int lives, int maxLives, int totemTier, int trophies,
								   int wave, int maxWaves, int stage, int stages,
								   int stopPrice, boolean paused, int priceMultiplier) {
		if (active != null) {
			active.coins = coins;
			active.lives = lives;
			active.maxLives = maxLives;
			active.totemTier = totemTier;
			active.trophies = trophies;
			active.wave = wave;
			active.maxWaves = maxWaves;
			active.stage = stage;
			active.stages = stages;
			active.stopPrice = stopPrice;
			active.paused = paused;
			active.priceMultiplier = priceMultiplier <= 0 ? 100 : priceMultiplier;
		}
	}

	@Override
	public void onClose() {
		if (active == this) {
			active = null;
		}
		super.onClose();
	}

	/** Строка «Волна 3/10 · этап 2/3» во второй строке шапки. */
	private Component waveLine() {
		if (paused) {
			return Component.translatable("screen.festival.wave_paused");
		}
		if (maxWaves <= 0) {
			return Component.translatable("screen.festival.wave_none");
		}
		if (wave <= 0) {
			return Component.translatable("screen.festival.wave_idle", maxWaves);
		}
		return Component.translatable("screen.festival.wave_line", wave, maxWaves, stage, stages);
	}

	// ------------------------------------------------------------------
	// Каталог текущей вкладки
	// ------------------------------------------------------------------

	private void rebuildTab() {
		current = ShopManager.inCategory(tabs[tab], tier);
	}

	private int perPage() {
		return cols() * rows();
	}

	private int maxPage() {
		return Math.max(0, (current.size() - 1) / perPage());
	}

	private int slotCount() {
		return Math.max(0, Math.min(perPage(), current.size() - pages[tab] * perPage()));
	}

	private ShopManager.Good goodAt(int slot) {
		int index = pages[tab] * perPage() + slot;
		return index >= 0 && index < current.size() ? current.get(index) : null;
	}

	private Component cardName(ShopManager.Good good) {
		return good.name();
	}

	private ItemStack cardStack(ShopManager.Good good) {
		return good.preview();
	}

	private int cardPrice(ShopManager.Good good) {
		if (ShopManager.STOP_WAVE_ID.equals(good.id())) {
			return stopPrice;
		}
		if (ShopManager.RESUME_WAVE_ID.equals(good.id())) {
			return 0;
		}
		int base = ShopManager.priceFor(good);
		if (base <= 0) {
			return 0;
		}
		return (int) Math.ceil(base * priceMultiplier / 100.0D);
	}

	/** Хватает ли денег: монетами или альтернативной ценой в наградах. */
	private boolean canAfford(ShopManager.Good good, int price) {
		if (coins >= price) {
			return true;
		}
		return good.trophyPrice() > 0 && trophies >= good.trophyPrice();
	}

	/** Карточка недоступна: остановка уже идёт или продолжать нечего. */
	private boolean isMaxed(ShopManager.Good good) {
		if (ShopManager.STOP_WAVE_ID.equals(good.id())) {
			return paused;
		}
		if (ShopManager.RESUME_WAVE_ID.equals(good.id())) {
			return !paused;
		}
		return false;
	}

	// ------------------------------------------------------------------
	// Геометрия
	// ------------------------------------------------------------------

	private int cols() {
		return this.width >= 520 ? 4 : 3;
	}

	private int rows() {
		return this.height >= 320 ? 3 : 2;
	}

	private int gridW() {
		return cols() * CARD_W + (cols() - 1) * GAP;
	}

	private int gridH() {
		return rows() * CARD_H + (rows() - 1) * GAP;
	}

	private int panelW() {
		return gridW() + PAD * 2;
	}

	private int panelH() {
		return HEADER_H + TABS_TOP + tabsH() + 8 + gridH() + 8 + FOOTER_H;
	}

	private int panelX() {
		return (this.width - panelW()) / 2;
	}

	private int panelY() {
		return (this.height - panelH()) / 2;
	}

	private int tabsY() {
		return panelY() + HEADER_H + TABS_TOP;
	}

	/** Один ряд вкладок, если подписи влезают, иначе два. */
	private int tabRows() {
		int single = (gridW() - (tabs.length - 1) * TAB_GAP) / tabs.length;
		return single >= TAB_MIN_W ? 1 : 2;
	}

	private int tabCols() {
		return (tabs.length + tabRows() - 1) / tabRows();
	}

	private int tabW() {
		return (gridW() - (tabCols() - 1) * TAB_GAP) / tabCols();
	}

	private int tabsH() {
		return tabRows() * TAB_H + (tabRows() - 1) * TAB_GAP;
	}

	private int tabsX() {
		int rowW = tabCols() * tabW() + (tabCols() - 1) * TAB_GAP;
		return panelX() + PAD + Math.max(0, (gridW() - rowW) / 2);
	}

	private int tabX(int index) {
		return tabsX() + (index % tabCols()) * (tabW() + TAB_GAP);
	}

	private int tabY(int index) {
		return tabsY() + (index / tabCols()) * (TAB_H + TAB_GAP);
	}

	private int gridY() {
		return tabsY() + tabsH() + 8;
	}

	private int cardX(int slot) {
		return panelX() + PAD + (slot % cols()) * (CARD_W + GAP);
	}

	private int cardY(int slot) {
		return gridY() + (slot / cols()) * (CARD_H + GAP);
	}

	/** Вкладка под курсором или -1. */
	private int tabAt(double mouseX, double mouseY) {
		for (int i = 0; i < tabs.length; i++) {
			int x = tabX(i);
			int y = tabY(i);
			if (mouseX >= x && mouseX < x + tabW() && mouseY >= y && mouseY < y + TAB_H) {
				return i;
			}
		}
		return -1;
	}

	/** Товар под курсором (или null). */
	private ShopManager.Good goodUnder(double mouseX, double mouseY) {
		for (int slot = 0; slot < slotCount(); slot++) {
			int x = cardX(slot);
			int y = cardY(slot);
			if (mouseX >= x && mouseX < x + CARD_W && mouseY >= y && mouseY < y + CARD_H) {
				return goodAt(slot);
			}
		}
		return null;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ------------------------------------------------------------------
	// Отрисовка
	// ------------------------------------------------------------------

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		pages[tab] = Math.min(pages[tab], maxPage());

		int px = panelX();
		int py = panelY();
		int pw = panelW();
		int ph = panelH();

		g.fill(0, 0, this.width, this.height, C_DIM);

		g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, C_PANEL_BORDER);
		g.fill(px, py, px + pw, py + ph, C_PANEL);

		// шапка
		g.fill(px, py, px + pw, py + HEADER_H, C_HEADER);
		g.fill(px, py + HEADER_H - 1, px + pw, py + HEADER_H, C_PANEL_BORDER);

		Component title = Component.translatable("screen.festival.shop");
		g.drawString(this.font, title, px + PAD, py + 10, C_TEXT);

		Component lifeText = Component.translatable("screen.festival.lives", lives, maxLives);
		int lifeX = px + PAD + this.font.width(title) + 12;
		int lifeColor = lives <= 1 ? C_RED : (lives < maxLives ? C_GOLD : C_GREEN);
		g.drawString(this.font, lifeText, lifeX, py + 10, lifeColor);

		Component coinText = Component.translatable("screen.festival.coins", coins);
		int coinX = px + pw - PAD - this.font.width(coinText);
		g.drawString(this.font, coinText, coinX, py + 10, C_GOLD);

		Component trophyText = Component.translatable("screen.festival.trophies", trophies);
		g.drawString(this.font, trophyText, coinX - 10 - this.font.width(trophyText), py + 10, C_TEXT);

		// вторая строка шапки: волна и этап
		Component waveText = waveLine();
		g.drawString(this.font, waveText, px + PAD, py + 23, C_DIM_TEXT);

		// вкладки
		int hoverTab = tabAt(mouseX, mouseY);
		for (int i = 0; i < tabs.length; i++) {
			int x = tabX(i);
			int y = tabY(i);
			boolean selected = i == tab;
			boolean hover = i == hoverTab;

			g.fill(x, y, x + tabW(), y + TAB_H, selected ? C_CARD_HOVER : (hover ? C_CARD : C_HEADER));
			g.fill(x, y + TAB_H - 1, x + tabW(), y + TAB_H, selected ? C_GOLD : C_PANEL_BORDER);

			Component label = Component.translatable(tabs[i].nameKey());
			int color = selected ? C_GOLD : (hover ? C_TEXT : C_DIM_TEXT);
			g.drawString(this.font, label, x + (tabW() - this.font.width(label)) / 2, y + 6, color);
		}

		// карточки
		ShopManager.Good hovered = goodUnder(mouseX, mouseY);
		for (int slot = 0; slot < slotCount(); slot++) {
			ShopManager.Good good = goodAt(slot);
			if (good == null) {
				continue;
			}
			int x = cardX(slot);
			int y = cardY(slot);
			boolean hover = good == hovered;
			boolean maxed = isMaxed(good);
			int price = cardPrice(good);
			boolean affordable = canAfford(good, price) && !maxed && trophies >= good.trophies();

			g.fill(x, y, x + CARD_W, y + CARD_H, hover ? C_CARD_HOVER : C_CARD);
			if (hover) {
				drawBorder(g, x, y, CARD_W, CARD_H, C_GOLD);
			}

			g.renderItem(cardStack(good), x + 8, y + 22);

			List<String> lines = wrap(plain(cardName(good).getString()), CARD_W - 38, 2);
			for (int line = 0; line < lines.size(); line++) {
				g.drawString(this.font, lines.get(line), x + 30, y + 7 + line * 10, C_TEXT);
			}

			if (maxed) {
				boolean setting = ShopManager.STOP_WAVE_ID.equals(good.id())
						|| ShopManager.RESUME_WAVE_ID.equals(good.id());
				g.drawString(this.font, Component.translatable(setting
						? "screen.festival.unavailable" : "screen.festival.maxed"),
						x + 30, y + 33, C_GREEN);
			} else {
				Component priceText = price <= 0
						? Component.translatable("screen.festival.free")
						: Component.translatable("screen.festival.price", price);
				g.drawString(this.font, priceText, x + 30, y + 33, affordable ? C_GOLD : C_RED);
				if (good.trophies() > 0) {
					Component trophy = Component.translatable("screen.festival.trophy_req", good.trophies());
					int color = trophies >= good.trophies() ? C_DIM_TEXT : C_RED;
					g.drawString(this.font, trophy, x + 30, y + 45, color);
				}
				if (good.trophyPrice() > 0) {
					Component alt = Component.translatable("screen.festival.or_trophies", good.trophyPrice());
					int color = trophies >= good.trophyPrice() ? C_GOLD : C_DIM_TEXT;
					g.drawString(this.font, alt, x + 30, y + 45, color);
				}
			}

			ItemStack stack = cardStack(good);
			if (stack.getCount() > 1) {
				g.drawString(this.font, "x" + stack.getCount(), x + 8, y + 44, C_DIM_TEXT);
			}

			if (hover && (lines.size() >= 2 && !fitsFull(lines, plain(cardName(good).getString()))
					|| good.trophies() > 0)) {
				showTooltip(g, good, price, mouseX, mouseY);
			}
		}

		if (current.isEmpty()) {
			Component empty = Component.translatable("screen.festival.empty");
			g.drawString(this.font, empty, px + (pw - this.font.width(empty)) / 2, gridY() + gridH() / 2 - 4, C_DIM_TEXT);
		}

		// подвал
		int fy = py + ph - FOOTER_H;
		g.fill(px, fy, px + pw, fy + 1, C_PANEL_BORDER);
		g.drawString(this.font, Component.translatable("screen.festival.hint"), px + PAD, fy + 8, C_DIM_TEXT);

		Component pageText = Component.translatable("screen.festival.page", pages[tab] + 1, maxPage() + 1);
		g.drawString(this.font, pageText, px + pw - PAD - this.font.width(pageText), fy + 8, C_GOLD);
	}

	private void showTooltip(GuiGraphics g, ShopManager.Good good, int price, int mouseX, int mouseY) {
		List<Component> lines = new ArrayList<>();
		lines.add(cardName(good).copy().withStyle(net.minecraft.ChatFormatting.WHITE));
		if (isMaxed(good)) {
			lines.add(Component.translatable("screen.festival.maxed"));
		} else {
			lines.add(price <= 0
					? Component.translatable("screen.festival.free")
					: Component.translatable("screen.festival.price", price));
			if (good.trophyPrice() > 0) {
				lines.add(Component.translatable("screen.festival.or_trophies", good.trophyPrice()));
			}
			if (good.trophies() > 0) {
				lines.add(Component.translatable("screen.festival.trophy_have", good.trophies(), trophies));
			}
		}
		g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
	}

	/**
	 * Перенос названия по словам: не больше {@code maxLines} строк, каждая
	 * не шире {@code maxWidth}. Что не влезло — обрезается многоточием.
	 */
	private List<String> wrap(String text, int maxWidth, int maxLines) {
		List<String> lines = new ArrayList<>(maxLines);
		StringBuilder line = new StringBuilder();

		for (String word : text.split(" ")) {
			if (word.isEmpty()) {
				continue;
			}
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (this.font.width(candidate) <= maxWidth) {
				line.setLength(0);
				line.append(candidate);
				continue;
			}
			if (line.isEmpty()) {
				// одно слово шире строки — режем по символам
				lines.add(this.font.plainSubstrByWidth(word, maxWidth - 6) + "…");
			} else {
				lines.add(line.toString());
				line.setLength(0);
				line.append(word);
			}
			if (lines.size() == maxLines - 1) {
				break;
			}
		}
		if (lines.size() < maxLines && !line.isEmpty()) {
			String rest = line.toString();
			if (this.font.width(rest) > maxWidth) {
				rest = this.font.plainSubstrByWidth(rest, maxWidth - 6) + "…";
			}
			lines.add(rest);
		}
		return lines.isEmpty() ? List.of("") : lines;
	}

	/** Влезло ли название полностью (для решения, нужен ли тултип). */
	private boolean fitsFull(List<String> lines, String original) {
		String joined = String.join(" ", lines);
		return joined.equals(original);
	}

	/**
	 * Убирает §-коды: имена из TACZ приходят цветными, а строка на карточке
	 * рисуется без разбора форматирования — иначе на экране будет «§eПатроны».
	 */
	private static String plain(String text) {
		if (text.indexOf('§') < 0) {
			return text;
		}
		StringBuilder builder = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '§') {
				i++;
				continue;
			}
			builder.append(c);
		}
		return builder.toString();
	}

	private void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
		g.fill(x, y, x + w, y + 1, color);
		g.fill(x, y + h - 1, x + w, y + h, color);
		g.fill(x, y, x + 1, y + h, color);
		g.fill(x + w - 1, y, x + w, y + h, color);
	}

	// ------------------------------------------------------------------
	// Ввод
	// ------------------------------------------------------------------

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double vertical) {
		int currentPage = pages[tab];
		int target = currentPage;
		if (vertical > 0) {
			target = Math.max(0, currentPage - 1);
		} else if (vertical < 0) {
			target = Math.min(maxPage(), currentPage + 1);
		}
		if (target != currentPage) {
			pages[tab] = target;
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, vertical);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0) {
			return super.mouseClicked(mouseX, mouseY, button);
		}

		int clickedTab = tabAt(mouseX, mouseY);
		if (clickedTab >= 0) {
			if (clickedTab != tab) {
				tab = clickedTab;
				rebuildTab();
				pages[tab] = Math.min(pages[tab], maxPage());
			}
			return true;
		}

		ShopManager.Good good = goodUnder(mouseX, mouseY);
		if (good != null && !isMaxed(good) && canAfford(good, cardPrice(good))
				&& trophies >= good.trophies()) {
			FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
			new BuyItemPayload(good.id()).write(buf);
			ClientPlayNetworking.send(BuyItemPayload.ID, buf);
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}
}
