package tamaized.aov.common.core.abilities.druid;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;
import tamaized.aov.AoV;
import tamaized.aov.common.capabilities.CapabilityList;
import tamaized.aov.common.capabilities.aov.IAoVCapability;
import tamaized.aov.common.capabilities.polymorph.IPolymorphCapability;
import tamaized.aov.common.core.abilities.Ability;
import tamaized.aov.common.core.abilities.AbilityBase;
import tamaized.aov.registry.AoVPotions;

import javax.annotation.Nullable;

public class Polymorph extends AbilityBase {

	private final ResourceLocation icon;
	private String name;
	private IPolymorphCapability.Morph type;

	public Polymorph(String name, IPolymorphCapability.Morph type) {
		super(

				new TextComponentTranslation((name = "aov.spells.polymorph.".concat(name)).concat(".name")),

				new TextComponentTranslation(""),

				new TextComponentTranslation(name.concat(".desc"))

		);
		this.name = name.concat(".name");
		this.type = type;
		icon = new ResourceLocation(AoV.MODID, "textures/spells/polymorph" + type.name().toLowerCase() + ".png");
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public String getName() {
		return I18n.format(name);
	}

	@Override
	public int getMaxCharges() {
		return -1;
	}

	@Override
	public int getChargeCost() {
		return 0;
	}

	@Override
	public double getMaxDistance() {
		return 0;
	}

	@Override
	public int getCoolDown() {
		return type == IPolymorphCapability.Morph.ArchAngel ? 600 : 60;
	}

	@Override
	public boolean usesInvoke() {
		return false;
	}

	@Override
	public boolean shouldDisable(@Nullable EntityPlayer caster, IAoVCapability cap) {
		return false;
	}

	@Override
	public boolean isCastOnTarget(EntityPlayer caster, IAoVCapability cap, EntityLivingBase target) {
		return false;
	}

	@Override
	public boolean canUseOnCooldown(IAoVCapability cap, EntityPlayer caster) {
		IPolymorphCapability poly = CapabilityList.getCap(caster, CapabilityList.POLYMORPH);
		return poly != null && poly.getMorph() == type && type != IPolymorphCapability.Morph.ArchAngel;
	}

	@Override
	public void onCooldownCast(Ability ability, EntityPlayer caster, EntityLivingBase target, int cooldown) {
		IPolymorphCapability cap = CapabilityList.getCap(caster, CapabilityList.POLYMORPH);
		if (cap != null && cap.getMorph() == type)
			cap.morph(null);

	}

	@Override
	public boolean cast(Ability ability, EntityPlayer caster, EntityLivingBase target) {
		boolean cooldown = true;
		IPolymorphCapability cap = CapabilityList.getCap(caster, CapabilityList.POLYMORPH);
		if (cap != null) {
			if (cap.getMorph() != type || type == IPolymorphCapability.Morph.ArchAngel) {
				cap.morph(type);
				if (type == IPolymorphCapability.Morph.ArchAngel)
					caster.addPotionEffect(new PotionEffect(AoVPotions.slowFall, 120 * 20));
			} else {
				cap.morph(null);
				cooldown = false;
			}
			IAoVCapability aov = CapabilityList.getCap(caster, CapabilityList.AOV);
			if (aov != null)
				aov.markDirty();
		}
		float pitch = type == IPolymorphCapability.Morph.ArchAngel ?

				caster.getRNG().nextFloat() * 0.20F + 0.95F :

				caster.getRNG().nextFloat() * 0.75F + 0.25F;
		caster.world.play(null, caster.posX, caster.posY, caster.posZ, type.sound, SoundCategory.PLAYERS, type == IPolymorphCapability.Morph.ArchAngel ? 1F : 0.5F, pitch);
		return cooldown;
	}

	@Override
	public ResourceLocation getIcon() {
		return icon;
	}
}
