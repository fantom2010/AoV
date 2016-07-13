package Tamaized.AoV.entity.projectile.caster;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.World;
import Tamaized.AoV.AoV;
import Tamaized.AoV.entity.projectile.ProjectileBase;

public class ProjectileNimbusRay extends ProjectileBase {
	
	private int damage = 0;

	public ProjectileNimbusRay(World worldIn, EntityLivingBase shooter, int dmg) {
		super(worldIn, shooter);
		damage = dmg;
	}

	@Override
	protected void damageEntity(Entity hit, Entity source) {
		DamageSource dmg = ProjectileBase.causeDamage(this, source, AoV.damageSources.damageSource_caster_NimbusRay);
		hit.attackEntityFrom(dmg, damage);
	}
	
	@Override
	protected void updateMotion(float f4, float f6){
		super.updateMotion(f4, 0.00f);
	}

}