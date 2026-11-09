package com.example.festival.core;

import net.minecraft.world.entity.Entity;

import java.util.UUID;

/**
 * Метки сущностей испытания.
 *
 * Каждый моб несёт {@link #MOB} и метку владельца {@code festival_owner:<uuid>} —
 * по ней забег находится за O(1) даже когда активных испытаний несколько.
 */
public final class TrialTags {

	public static final String MOB = "festival_mob";
	public static final String BOSS = "festival_boss";
	public static final String MINIBOSS = "festival_miniboss";
	public static final String SUMMONER = "festival_summoner";
	public static final String SUMMONED = "festival_summoned";

	private static final String OWNER_PREFIX = "festival_owner:";

	private TrialTags() {
	}

	public static String owner(UUID owner) {
		return OWNER_PREFIX + owner;
	}

	/** Владелец забега по метке сущности, либо {@code null}. */
	public static UUID ownerOf(Entity entity) {
		for (String tag : entity.getTags()) {
			if (tag.startsWith(OWNER_PREFIX)) {
				return parse(tag);
			}
		}
		return null;
	}

	/** Разбор метки владельца без обращения к сущности (кэшируется в целях ИИ). */
	public static UUID parseOwnerTag(String tag) {
		return tag.startsWith(OWNER_PREFIX) ? parse(tag) : null;
	}

	/** Метка владельца из набора меток, либо {@code null}. */
	public static UUID ownerOf(Iterable<String> tags) {
		for (String tag : tags) {
			if (tag.startsWith(OWNER_PREFIX)) {
				return parse(tag);
			}
		}
		return null;
	}

	private static UUID parse(String tag) {
		try {
			return UUID.fromString(tag.substring(OWNER_PREFIX.length()));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
