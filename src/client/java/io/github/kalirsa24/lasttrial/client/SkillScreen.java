package io.github.kalirsa24.lasttrial.client;

import io.github.kalirsa24.lasttrial.core.SkillManager;
import io.github.kalirsa24.lasttrial.network.BuySkillPayload;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Древо развития: пять ветвей, соединённых линиями.
 *
 * Каждый узел покупается за награды празднества; цена растёт с уровнем.
 * Уровни и количество наград приходят с сервера, покупка уходит обратно
 * строковым id ветки — сервер сам проверяет цену и выдаёт уровень.
 */
public class SkillScreen extends Screen {

	private static final int PANEL_W = 380;
	private static final int PANEL_H = 296;
	private static final int HEADER_H = 42;
	private static final int FOOTER_H = 20;
	private static final int NODE_W = 108;
	private static final int NODE_H = 46;

	private static final int C_DIM = 0x99000000;
	private static final int C_PANEL = 0xF01A1A1A;
	private static final int C_BORDER = 0xFF3D3D3D;
	private static final int C_HEADER = 0xFF242424;
	private static final int C_NODE = 0xFF2B2B2B;
	private static final int C_NODE_HOVER = 0xFF3C3C3C;
	private static final int C_TEXT = 0xFFE6E6E6;
	private static final int C_DIM_TEXT = 0xFF9A9A9A;
	private static final int C_GOLD = 0xFFFFC24B;
	private static final int C_GREEN = 0xFF6FCF7A;
	private static final int C_RED = 0xFFE05252;
	private static final int C_LINE = 0xFF4A4A4A;

	private final int[] levels;
	private final int trophies;
	private final List<Node> nodes = new ArrayList<>();

	private record Node(SkillManager.Skill skill, int x, int y, ItemStack icon) {
	}

	public SkillScreen(int[] levels, int trophies) {
		super(Component.translatable("screen.lasttrial.skills"));
		this.levels = levels;
		this.trophies = trophies;
		buildNodes();
	}

	private int panelX() {
		return (this.width - PANEL_W) / 2;
	}

	private int panelY() {
		return (this.height - PANEL_H) / 2;
	}

	/** Дерево: живучесть сверху, сила и оберег в середине, быстрота и удача снизу. */
	private void buildNodes() {
		nodes.clear();
		int cx = panelX() + PANEL_W / 2;
		int top = panelY() + HEADER_H + 16;

		add(SkillManager.Skill.VITALITY, cx, top, new ItemStack(Items.GOLDEN_APPLE));
		add(SkillManager.Skill.POWER, cx - 62, top + 78, new ItemStack(Items.IRON_SWORD));
		add(SkillManager.Skill.WARD, cx + 62, top + 78, new ItemStack(Items.SHIELD));
		add(SkillManager.Skill.SWIFTNESS, cx - 62, top + 156, new ItemStack(Items.FEATHER));
		add(SkillManager.Skill.FORTUNE, cx + 62, top + 156, new ItemStack(Items.DIAMOND));
	}

	private void add(SkillManager.Skill skill, int centerX, int topY, ItemStack icon) {
		nodes.add(new Node(skill, centerX - NODE_W / 2, topY, icon));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int level(SkillManager.Skill skill) {
		return skill.ordinal() < levels.length ? levels[skill.ordinal()] : 0;
	}

	private int cost(SkillManager.Skill skill) {
		return SkillManager.cost(skill, level(skill));
	}

	private boolean maxed(SkillManager.Skill skill) {
		return level(skill) >= skill.maxLevel();
	}

	private Node nodeAt(double mouseX, double mouseY) {
		for (Node node : nodes) {
			if (mouseX >= node.x() && mouseX < node.x() + NODE_W
					&& mouseY >= node.y() && mouseY < node.y() + NODE_H) {
				return node;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------
	// Отрисовка
	// ------------------------------------------------------------------

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		int px = panelX();
		int py = panelY();

		g.fill(0, 0, this.width, this.height, C_DIM);
		g.fill(px - 1, py - 1, px + PANEL_W + 1, py + PANEL_H + 1, C_BORDER);
		g.fill(px, py, px + PANEL_W, py + PANEL_H, C_PANEL);

		g.fill(px, py, px + PANEL_W, py + HEADER_H, C_HEADER);
		g.fill(px, py + HEADER_H - 1, px + PANEL_W, py + HEADER_H, C_BORDER);

		Component title = Component.translatable("screen.lasttrial.skills");
		g.drawString(this.font, title, px + 12, py + 9, C_TEXT);
		g.drawString(this.font, Component.translatable("screen.lasttrial.skills_hint"),
				px + 12, py + 24, C_DIM_TEXT);

		Component trophy = Component.translatable("screen.lasttrial.trophies", trophies);
		g.drawString(this.font, trophy, px + PANEL_W - 12 - this.font.width(trophy), py + 9, C_GOLD);

		drawLines(g);
		drawNodes(g, mouseX, mouseY);

		int fy = py + PANEL_H - FOOTER_H;
		g.fill(px, fy, px + PANEL_W, fy + 1, C_BORDER);
		g.drawString(this.font, Component.translatable("screen.lasttrial.skills_footer"),
				px + 12, fy + 7, C_DIM_TEXT);
	}

	private void drawLines(GuiGraphics g) {
		for (int i = 1; i < nodes.size(); i++) {
			Node parent = nodes.get(i <= 2 ? 0 : (i == 3 ? 1 : 2));
			Node child = nodes.get(i);
			int x1 = parent.x() + NODE_W / 2;
			int y1 = parent.y() + NODE_H;
			int x2 = child.x() + NODE_W / 2;
			int y2 = child.y();
			int midY = (y1 + y2) / 2;
			g.fill(Math.min(x1, x2), midY, Math.max(x1, x2), midY + 1, C_LINE);
			g.fill(x1, y1, x1 + 1, midY, C_LINE);
			g.fill(x2, midY, x2 + 1, y2, C_LINE);
		}
	}

	private void drawNodes(GuiGraphics g, int mouseX, int mouseY) {
		Node hovered = nodeAt(mouseX, mouseY);
		for (Node node : nodes) {
			SkillManager.Skill skill = node.skill();
			boolean hover = node == hovered;
			boolean full = maxed(skill);
			int cost = cost(skill);

			g.fill(node.x(), node.y(), node.x() + NODE_W, node.y() + NODE_H,
					hover ? C_NODE_HOVER : C_NODE);
			if (hover) {
				border(g, node.x(), node.y(), NODE_W, NODE_H, C_GOLD);
			}

			g.renderItem(node.icon(), node.x() + 6, node.y() + 6);

			Component name = Component.translatable(skill.nameKey());
			g.drawString(this.font, name, node.x() + 26, node.y() + 6, C_TEXT);

			Component levelText = Component.translatable("screen.lasttrial.skill_level",
					level(skill), skill.maxLevel());
			g.drawString(this.font, levelText, node.x() + 26, node.y() + 18, C_DIM_TEXT);

			if (full) {
				g.drawString(this.font, Component.translatable("screen.lasttrial.maxed"),
						node.x() + 26, node.y() + 31, C_GREEN);
			} else {
				boolean affordable = trophies >= cost;
				g.drawString(this.font, Component.translatable("screen.lasttrial.skill_cost", cost),
						node.x() + 26, node.y() + 31, affordable ? C_GOLD : C_RED);
			}

			Component effect = Component.translatable("screen.lasttrial.skill_effect",
					effectText(skill, level(skill)));
			g.drawString(this.font, effect, node.x() + 6, node.y() + NODE_H - 11, C_DIM_TEXT);
		}
	}

	/** Текст текущего бонуса ветки. */
	private String effectText(SkillManager.Skill skill, int level) {
		return switch (skill) {
			case VITALITY -> "+" + (level * 2) + " HP";
			case POWER -> "+" + (level * 3) + "%";
			case WARD -> "-" + (level * 3) + "%";
			case SWIFTNESS -> "+" + (level * 3) + "%";
			case FORTUNE -> "+" + String.format(java.util.Locale.ROOT, "%.1f", level * 0.3F) + "%";
		};
	}

	private void border(GuiGraphics g, int x, int y, int w, int h, int color) {
		g.fill(x, y, x + w, y + 1, color);
		g.fill(x, y + h - 1, x + w, y + h, color);
		g.fill(x, y, x + 1, y + h, color);
		g.fill(x + w - 1, y, x + w, y + h, color);
	}

	// ------------------------------------------------------------------
	// Ввод
	// ------------------------------------------------------------------

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0) {
			return super.mouseClicked(mouseX, mouseY, button);
		}
		Node node = nodeAt(mouseX, mouseY);
		if (node != null) {
			SkillManager.Skill skill = node.skill();
			if (!maxed(skill) && trophies >= cost(skill)) {
				FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
				new BuySkillPayload(skill.id()).write(buf);
				ClientPlayNetworking.send(BuySkillPayload.ID, buf);
			}
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}
}
