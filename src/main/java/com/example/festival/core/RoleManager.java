package com.example.festival.core;

import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Роли и постоянные бонусы к атрибутам.
 *
 * В 1.20.1 модификаторы адресуются UUID, а не ResourceLocation,
 * а операция называется ADDITION, а не ADD_VALUE.
 */
public class RoleManager {

	public enum PlayerRole {
		TANK("role.festival.tank"),
		DPS("role.festival.dps"),
		SUPPORT("role.festival.support");

		private final String key;

		PlayerRole(String key) {
			this.key = key;
		}

		public String getKey() {
			return key;
		}
	}

	private static final Map<UUID, PlayerRole> ROLES = new HashMap<>();

	private static final UUID TANK_HEALTH = UUID.fromString("6f0a1f4e-1111-4a11-9a11-000000000001");
	private static final UUID TANK_ARMOR = UUID.fromString("6f0a1f4e-1111-4a11-9a11-000000000002");
	private static final UUID DPS_DAMAGE = UUID.fromString("6f0a1f4e-1111-4a11-9a11-000000000003");

	public static void setRole(Player player, PlayerRole role) {
		removeBonuses(player);
		ROLES.put(player.getUUID(), role);
		applyBonuses(player, role);
		Msg.overlay(player,
				Component.translatable("message.festival.role_set", Component.translatable(role.getKey())));
	}

	public static PlayerRole getRole(Player player) {
		return ROLES.getOrDefault(player.getUUID(), PlayerRole.DPS);
	}

	private static void applyBonuses(Player player, PlayerRole role) {
		switch (role) {
			case TANK -> {
				add(player, Attributes.MAX_HEALTH, TANK_HEALTH, "festival_tank_health", 10.0D);
				add(player, Attributes.ARMOR, TANK_ARMOR, "festival_tank_armor", 8.0D);
				player.setHealth(player.getMaxHealth());
			}
			case DPS -> add(player, Attributes.ATTACK_DAMAGE, DPS_DAMAGE, "festival_dps_damage", 4.0D);
			case SUPPORT -> player.addEffect(
					new MobEffectInstance(MobEffects.REGENERATION, 20 * 600, 0, false, false));
		}
	}

	private static void add(Player player, Attribute attribute, UUID id, String name, double amount) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null || instance.getModifier(id) != null) {
			return;
		}
		instance.addPermanentModifier(new AttributeModifier(id, name, amount,
				AttributeModifier.Operation.ADDITION));
	}

	private static void removeBonuses(Player player) {
		remove(player, Attributes.MAX_HEALTH, TANK_HEALTH);
		remove(player, Attributes.ARMOR, TANK_ARMOR);
		remove(player, Attributes.ATTACK_DAMAGE, DPS_DAMAGE);
	}

	private static void remove(Player player, Attribute attribute, UUID id) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null && instance.getModifier(id) != null) {
			instance.removeModifier(id);
		}
	}

	public static void clear() {
		ROLES.clear();
	}
}
