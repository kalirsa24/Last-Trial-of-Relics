package com.example.festival.core;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Каталог оружия TACZ для магазина.
 *
 * Раньше здесь была заглушка: без jar TACZ вкладки «Оружие / Патроны / Обвесы»
 * оставались пустыми. Теперь каталог собирается из реестра предметов мода
 * {@code tacz} в момент первого открытия магазина — если мод не установлен,
 * метод просто ничего не добавляет, и остальной магазин работает как обычно.
 *
 * Тип товара определяется по id предмета (пистолет / автомат / снайперка /
 * патроны / обвес), цена — базовая ставка класса плюс наценка из
 * {@link ShopManager#markup(String, int)}.
 */
public final class TaczShop {

	private static final String NAMESPACE = "tacz";

	private TaczShop() {
	}

	/** Установлен ли TACZ в этой сборке. */
	public static boolean available() {
		return FabricLoader.getInstance().isModLoaded(NAMESPACE);
	}

	// ------------------------------------------------------------------
	// Каталог
	// ------------------------------------------------------------------

	public static void addWeapons(List<ShopManager.Good> out) {
		if (!available()) {
			return;
		}
		for (Item item : BuiltInRegistries.ITEM) {
			ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
			if (!NAMESPACE.equals(id.getNamespace())) {
				continue;
			}
			String path = id.getPath();
			if (isAmmo(path) || isAttachment(path)) {
				continue;
			}
			int base = baseWeaponPrice(path);
			add(out, id, item, ShopManager.Category.WEAPONS, base, 1);
		}
	}

	public static void addAmmo(List<ShopManager.Good> out) {
		if (!available()) {
			return;
		}
		for (Item item : BuiltInRegistries.ITEM) {
			ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
			if (!NAMESPACE.equals(id.getNamespace()) || !isAmmo(id.getPath())) {
				continue;
			}
			add(out, id, item, ShopManager.Category.AMMO, 6, 32);
		}
	}

	public static void addAttachments(List<ShopManager.Good> out) {
		if (!available()) {
			return;
		}
		for (Item item : BuiltInRegistries.ITEM) {
			ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
			if (!NAMESPACE.equals(id.getNamespace()) || !isAttachment(id.getPath())) {
				continue;
			}
			add(out, id, item, ShopManager.Category.ATTACHMENTS, 25, 1);
		}
	}

	private static void add(List<ShopManager.Good> out, ResourceLocation id, Item item,
							ShopManager.Category category, int basePrice, int count) {
		int price = ShopManager.markup(id.getPath(), basePrice);
		ItemStack stack = new ItemStack(item, count);
		out.add(new ShopManager.Good(id.toString(), category, item.getDescription(),
				stack, List.of(stack.copy()), price, RelicTier.BASIC, 0, 0));
	}

	// ------------------------------------------------------------------
	// Классификация
	// ------------------------------------------------------------------

	private static boolean isAmmo(String path) {
		return path.contains("ammo") || path.contains("bullet") || path.contains("cartridge")
				|| path.contains("shell") || path.contains("magazine") && path.contains("ammo");
	}

	private static boolean isAttachment(String path) {
		return path.contains("scope") || path.contains("sight") || path.contains("muzzle")
				|| path.contains("grip") || path.contains("stock") || path.contains("laser")
				|| path.contains("attachment") || path.contains("silencer");
	}

	private static int baseWeaponPrice(String path) {
		if (path.contains("sniper") || path.contains("awp") || path.contains("mosin")) {
			return 120;
		}
		if (path.contains("shotgun")) {
			return 90;
		}
		if (path.contains("lmg") || path.contains("machinegun") || path.contains("minigun")) {
			return 130;
		}
		if (path.contains("pistol") || path.contains("handgun") || path.contains("glock")
				|| path.contains("deagle") || path.contains("revolver")) {
			return 40;
		}
		if (path.contains("rifle") || path.contains("smg") || path.contains("carbine")
				|| path.contains("ak") || path.contains("m4")) {
			return 70;
		}
		return 60;
	}
}
