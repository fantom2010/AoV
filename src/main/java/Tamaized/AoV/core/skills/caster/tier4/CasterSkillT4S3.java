package Tamaized.AoV.core.skills.caster.tier4;

import Tamaized.AoV.core.abilities.AbilityBase;
import Tamaized.AoV.core.skills.AoVSkill;
import Tamaized.AoV.core.skills.SkillIcons;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;

import java.util.ArrayList;
import java.util.List;

public class CasterSkillT4S3 extends AoVSkill {

	private static final List<AbilityBase> spells = new ArrayList<AbilityBase>();

	static {

	}

	public CasterSkillT4S3() {
		super(spells,

				TextFormatting.AQUA + "Spell Power IV",

				TextFormatting.RED + "Requires: 12 Points Spent in Tree",

				TextFormatting.RED + "Requires: Spell Power III",

				"",

				TextFormatting.GREEN + "+25 Spell Power"

		);
	}

	@Override
	public String getName() {
		return "CasterSkillT4S3";
	}

	@Override
	protected Buffs setupBuffs() {
		return new Buffs(0, 25, 0, 0, false);
	}

	@Override
	public ResourceLocation getIcon() {
		return SkillIcons.spellpower;
	}

	@Override
	public boolean isClassCore() {
		return false;
	}

	@Override
	public AoVSkill getParent() {
		return AoVSkill.caster_tier_3_3;
	}

	@Override
	public int getCost() {
		return 1;
	}

	@Override
	public int getLevel() {
		return 0;
	}

	@Override
	public int getSpentPoints() {
		return 12;
	}

}
