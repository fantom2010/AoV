package tamaized.aov.common.core.abilities.healer;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import tamaized.aov.AoV;
import tamaized.aov.common.capabilities.CapabilityList;
import tamaized.aov.common.capabilities.aov.IAoVCapability;
import tamaized.aov.common.core.abilities.Ability;
import tamaized.aov.common.core.abilities.AbilityBase;
import tamaized.aov.common.helper.ParticleHelper;
import tamaized.aov.proxy.CommonProxy;
import tamaized.aov.registry.AoVDamageSource;
import tamaized.aov.registry.SoundEvents;

import java.util.List;

public abstract class CureWounds extends AbilityBase {

	private final String name;
	private final int damage;
	private final int charges;
	private final double range;

	public CureWounds(String n, int c, double r, int dmg) {
		super(

				new TextComponentTranslation(n),

				new TextComponentTranslation(""),

				new TextComponentTranslation("aov.spells.global.charges", c),

				new TextComponentTranslation("aov.spells.global.range", r),

				new TextComponentTranslation("aov.spells.global.healing", dmg),

				new TextComponentTranslation(""),

				new TextComponentTranslation("aov.spells.curewounds.desc")

		);
		name = n;
		damage = dmg;
		charges = c;
		range = r;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public String getName() {
		return I18n.format(name);
	}

	@Override
	public double getMaxDistance() {
		return range;
	}

	@Override
	public int getMaxCharges() {
		return charges;
	}

	@Override
	public boolean usesInvoke() {
		return true;
	}

	protected abstract int getParticleColor();

	@Override
	public boolean cast(Ability ability, EntityPlayer caster, EntityLivingBase e) {
		IAoVCapability cap = caster.hasCapability(CapabilityList.AOV, null) ? caster.getCapability(CapabilityList.AOV, null) : null;
		if (cap == null)
			return false;
		int a = (int) (damage * (1f + (cap.getSpellPower() / 100f)));
		if (cap.getInvokeMass())
			castAsMass(caster, a, cap);
		else if (e == null) {
			caster.heal(a);
			SoundEvents.playMovingSoundOnServer(SoundEvents.heal, caster);
		} else {
			if (e.isEntityUndead()) {
				e.attackEntityFrom(AoVDamageSource.createEntityDamageSource(DamageSource.MAGIC, caster), a);
				SoundEvents.playMovingSoundOnServer(SoundEvents.heal, e);
			} else if (IAoVCapability.canBenefit(caster, cap, e)) {
				e.heal(a);
				SoundEvents.playMovingSoundOnServer(SoundEvents.heal, e);
			}
		}
		cap.addExp(caster, 24, this);
		return true;
	}

	private void castAsMass(EntityLivingBase caster, int dmg, IAoVCapability cap) {
		int range = (int) (getMaxDistance() * 2);
		ParticleHelper.spawnParticleMesh(ParticleHelper.MeshType.BURST, CommonProxy.ParticleType.Heart, caster.world, caster.getPositionVector(), range, getParticleColor());
		List<EntityLivingBase> list = caster.world.getEntitiesWithinAABB(EntityLivingBase.class, new AxisAlignedBB(caster.getPosition().add(-range, -range, -range), caster.getPosition().add(range, range, range)));
		for (EntityLivingBase entity : list) {
			if (entity.isEntityUndead()) {
				entity.attackEntityFrom(AoVDamageSource.createEntityDamageSource(DamageSource.MAGIC, caster), dmg);
				SoundEvents.playMovingSoundOnServer(SoundEvents.heal, entity);
			} else if (IAoVCapability.canBenefit(caster, cap, entity)) {
				entity.heal(dmg);
				SoundEvents.playMovingSoundOnServer(SoundEvents.heal, entity);
			}
			cap.addExp(caster, 24, this);
		}
	}

}
