package tamaized.aov.common.core.abilities.druid;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextComponentTranslation;
import tamaized.aov.common.capabilities.CapabilityList;
import tamaized.aov.common.capabilities.aov.IAoVCapability;
import tamaized.aov.common.capabilities.polymorph.IPolymorphCapability;
import tamaized.aov.common.core.abilities.Ability;
import tamaized.aov.common.core.abilities.AbilityBase;
import tamaized.aov.common.core.abilities.IAura;
import tamaized.aov.common.core.skills.SkillIcons;
import tamaized.aov.registry.AoVParticles;
import tamaized.aov.registry.ParticleRegistry;
import tamaized.tammodized.common.helper.CapabilityHelper;
import tamaized.tammodized.common.particles.ParticleHelper;

import javax.annotation.Nullable;
import java.util.List;

public class ElementalEmpowerment extends AbilityBase implements IAura {

	private static final String UNLOC = "aov.spells.elementalempowerment";
	private static final float DAMAGE = 1F;
	private static final float DISTANCE = 4F;

	public ElementalEmpowerment() {
		super(

				new TextComponentTranslation(UNLOC.concat(".name")),

				new TextComponentTranslation(""),

				new TextComponentTranslation(UNLOC.concat(".desc"))

		);
	}

	@Override
	public String getName() {
		return UNLOC.concat(".name");
	}

	@Override
	public int getMaxCharges() {
		return 2;
	}

	@Override
	public int getChargeCost() {
		return 1;
	}

	@Override
	public double getMaxDistance() {
		return DISTANCE;
	}

	@Override
	public int getCoolDown() {
		return 90;
	}

	@Override
	public boolean usesInvoke() {
		return false;
	}

	@Override
	public boolean isCastOnTarget(EntityPlayer caster, IAoVCapability cap, EntityLivingBase target) {
		return false;
	}

	@Override
	public boolean shouldDisable(@Nullable EntityPlayer caster, IAoVCapability cap) {
		IPolymorphCapability poly = CapabilityHelper.getCap(caster, CapabilityList.POLYMORPH, null);
		return poly == null || (poly.getMorph() != IPolymorphCapability.Morph.FireElemental && poly.getMorph() != IPolymorphCapability.Morph.WaterElemental);
	}

	@Override
	public boolean cast(Ability ability, EntityPlayer caster, EntityLivingBase target) {
		IAoVCapability cap = CapabilityHelper.getCap(caster, CapabilityList.AOV, null);
		if (cap == null)
			return false;
		IPolymorphCapability poly = CapabilityHelper.getCap(caster, CapabilityList.POLYMORPH, null);
		if (poly != null) {
			switch (poly.getMorph()) {
				case WaterElemental:
				case FireElemental:
					cap.addAura(createAura(ability));
					return true;
				default:
					return false;
			}
		}
		return false;
	}

	@Override
	public ResourceLocation getIcon() {
		return SkillIcons.vitality;
	}

	@Override
	public void castAsAura(EntityPlayer caster, IAoVCapability cap, int life) {
		IPolymorphCapability poly = CapabilityHelper.getCap(caster, CapabilityList.POLYMORPH, null);
		if (poly != null) {
			switch (poly.getMorph()) {
				case WaterElemental:
					ParticleRegistry.spawnFromServer(caster.world, AoVParticles.SNOW, caster.posX + caster.world.rand.nextDouble() * 1.5F - 0.75F, caster.posY + caster.eyeHeight - 0.25F + caster.world.rand.nextDouble() * 1.5F - 0.75F, caster.posZ + caster.world.rand.nextDouble() * 1.5F - 0.75F, 0, -0.03F, 0);
					break;
				case FireElemental:
					ParticleHelper.spawnVanillaParticleOnServer(caster.world, EnumParticleTypes.FLAME, caster.posX + caster.world.rand.nextDouble() * 1.5F - 0.75F, caster.posY + caster.eyeHeight - 0.25F + caster.world.rand.nextDouble() * 1.5F - 0.75F, caster.posZ + caster.world.rand.nextDouble() * 1.5F - 0.75F, 0, 0, 0);
					float damage = DAMAGE * (1F + (cap.getSpellPower() / 100F));
					if (life > 0 && life % (3 * 20) == 0) {
						List<EntityLivingBase> list = caster.world.getEntitiesWithinAABB(EntityLivingBase.class, new AxisAlignedBB(caster.getPosition().add(-DISTANCE, -DISTANCE, -DISTANCE), caster.getPosition().add(DISTANCE, DISTANCE, DISTANCE)));
						for (EntityLivingBase entity : list) {
							if (IAoVCapability.selectiveTarget(caster, cap, entity)) {
								entity.attackEntityFrom(DamageSource.IN_FIRE, damage);
								entity.setFire(5);
							}
						}
					}
					break;
				default:
					break;
			}
		}
	}

	@Override
	public int getLife() {
		return 45;
	}
}
