package Tamaized.AoV.capabilities.aov;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import Tamaized.AoV.AoV;
import Tamaized.AoV.core.abilities.Ability;
import Tamaized.AoV.core.abilities.AbilityBase;
import Tamaized.AoV.core.abilities.Aura;
import Tamaized.AoV.core.skills.AoVSkill;
import Tamaized.AoV.network.ClientPacketHandler;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemShield;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;

public class AoVCapabilityHandler implements IAoVCapability {

	public static final float xpScale = 2.5F;

	public static int getExpForLevel(IAoVCapability cap, int level) {
		return level > cap.getMaxLevel() ? 0 : getExpForLevel(level);
	}

	public static int getExpForLevel(int level) {
		return level < 1 ? 0 : (int) Math.floor(25 * ((xpScale + (level - 2)) * (level - 1)));
	}

	public static int getLevelFromExp(float xp) {
		xp += 0.5F; // We need to offset because of float to int to float casting
		double a = ((-5 * xpScale) + Math.sqrt(25 * Math.pow(xpScale, 2) - 50 * xpScale + 4 * xp + 25) + 15) / 10;
		double b = ((-5 * xpScale) - Math.sqrt(25 * Math.pow(xpScale, 2) - 50 * xpScale + 4 * xp + 25) + 15) / 10;
		return Math.max((int) a, (int) b);
	}

	private int tick = 1;
	private boolean dirty = false;

	// TODO
	private int currentSlot = 0;

	// Calculate and update these when 'dirty'
	private List<AoVSkill> obtainedSkills = new ArrayList<AoVSkill>();
	private int skillPoints = 1;
	private int exp = 0;
	private int maxLevel = 15;
	private boolean invokeMass = false;
	private Ability[] slots = new Ability[] { null, null, null, null, null, null, null, null, null };

	// These can be separate
	private List<Ability> abilities = new ArrayList<Ability>();
	private float spellpower = 0;
	private int extraCharges = 0;
	private int dodge = 0;
	private int doublestrike = 0;
	private boolean selectiveFocus = false;
	private boolean hasInvoke = false;

	// Keep this on the server
	private List<Aura> auras = new ArrayList<Aura>();
	private Map<AbilityBase, DecayWrapper> decay = new HashMap<AbilityBase, DecayWrapper>();
	private int lastLevel = -1;

	private class DecayWrapper {
		private int amount = 1;
		private int tick = 0;
		private int decayTick = 20 * 30;

		public void addDecay() {
			amount++;
		}

		public void update() {
			tick++;
			if (tick % decayTick == 0) {
				amount--;
			}
		}

		public int getDecay() {
			return amount;
		}
	}

	@Override
	public void reset(boolean b) {
		if (b) {
			obtainedSkills.clear();
			skillPoints = 1;
			exp = 0;
			maxLevel = 15;
			lastLevel = -1;
		} else {
			AoVSkill core = getCoreSkill();
			obtainedSkills.clear();
			obtainedSkills.add(core);
			skillPoints = getLevel() - 1;
		}
		abilities.clear();
		auras.clear();
		decay.clear();
		invokeMass = false;
		slots = new Ability[] { null, null, null, null, null, null, null, null, null };
		dirty = true;
	}

	@Override
	public void update(EntityPlayer player) {
		if (tick > 0 && tick % 20 == 0) updateAbilities();
		updateAuras(player);
		updateDecay();
		if (dirty) {
			updateValues(player);
			if (player instanceof EntityPlayerMP) sendPacketUpdates((EntityPlayerMP) player);
			dirty = false;
		}
		updateHealth(player);
		if (tick > 0 && tick % (20 * 30) == 0 && hasSkill(AoVSkill.defender_core_4) && player != null) {
			ItemStack main = player.getHeldItemMainhand();
			ItemStack off = player.getHeldItemOffhand();
			if (!main.isEmpty() && main.getItem() instanceof ItemShield && main.getItem().isRepairable() && main.getItemDamage() > 0) {
				main.setItemDamage(0);
			}
			if (!off.isEmpty() && off.getItem() instanceof ItemShield && off.getItem().isRepairable() && off.getItemDamage() > 0) {
				off.setItemDamage(0);
			}
		}
		tick++;
	}

	private static final String defenderHealthName = "AoV Defender Health";
	private static final AttributeModifier defenderHealth = new AttributeModifier(defenderHealthName, 10.0D, 0);

	private void updateHealth(EntityPlayer player) {
		if (player == null) return;
		IAttributeInstance hp = player.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);
		Iterator<AttributeModifier> iter = hp.getModifiers().iterator();
		while (iter.hasNext()) {
			AttributeModifier mod = iter.next();
			if (mod.getName().equals(defenderHealthName)) {
				hp.removeModifier(mod);
			}
		}
		if (hasSkill(AoVSkill.defender_tier_4_2) && !hp.hasModifier(defenderHealth)) {
			hp.applyModifier(defenderHealth);
		}
	}

	private void updateValues(EntityPlayer player) {
		if (lastLevel < 0) lastLevel = getLevel();
		if (lastLevel < getLevel()) {
			skillPoints += (getLevel() - lastLevel);
			lastLevel = getLevel();
		}
		spellpower = 0;
		extraCharges = 0;
		dodge = 0;
		doublestrike = 0;
		selectiveFocus = false;
		hasInvoke = false;
		List<AbilityBase> list = new ArrayList<AbilityBase>();
		abilities.clear();
		for (AoVSkill skill : obtainedSkills) {
			spellpower += skill.getBuffs().spellPower;
			extraCharges += skill.getBuffs().charges;
			dodge += skill.getBuffs().dodge;
			doublestrike += skill.getBuffs().doublestrike;
			if (skill.getBuffs().selectiveFocus) selectiveFocus = true;
			for (AbilityBase ability : skill.getAbilities()) {
				if (ability == AbilityBase.invokeMass) hasInvoke = true;
				list.add(ability);
			}
		}
		for (AbilityBase ability : list)
			addAbility(new Ability(ability, this));
		if (player != null) {
			if (player.getActivePotionEffect(AoV.potions.aid) != null) dodge += 5;
		}
	}

	private void updateAbilities() {
		for (Ability ability : slots)
			if (ability != null) ability.update();
	}

	private void updateAuras(EntityPlayer player) {
		Iterator<Aura> iter = auras.iterator();
		while (iter.hasNext()) {
			Aura aura = iter.next();
			aura.update(player);
			if (aura.isDead()) iter.remove();
		}
	}

	private void updateDecay() {
		Iterator<DecayWrapper> iter = decay.values().iterator();
		while (iter.hasNext()) {
			DecayWrapper wrapper = iter.next();
			wrapper.update();
			if (wrapper.getDecay() <= 0) iter.remove();
		}
	}

	@Override
	public void resetCharges() {
		for (Ability ability : slots)
			if (ability != null) ability.reset(this);
		dirty = true;
	}

	@Override
	public List<Ability> getAbilities() {
		return abilities;
	}

	@Override
	public boolean canUseAbility(Ability ability) {
		boolean flag = false;
		for (Ability a : abilities)
			if (a.compare(ability) && ability.canUse(this)) flag = true;
		return flag;
	}

	@Override
	public void addAbility(Ability ability) {
		abilities.add(ability);
		dirty = true;
	}

	@Override
	public void removeAbility(Ability ability) {
		Iterator<Ability> iter = abilities.iterator();
		while (iter.hasNext()) {
			Ability a = iter.next();
			if (a.compare(ability)) {
				iter.remove();
				dirty = true;
			}
		}
	}

	@Override
	public void castAbility(Ability ability, EntityPlayer caster, EntityLivingBase target) {
		if (canUseAbility(ability)) ability.cast(caster, target);
	}

	@Override
	public void addAura(Aura aura) {
		auras.add(aura);
		dirty = true;
	}

	@Override
	public void addExp(int amount, AbilityBase spell) {
		if (getLevel() >= getMaxLevel()) return;
		if (spell == null) {

		} else if (decay.containsKey(spell)) {
			amount /= decay.get(spell).getDecay();
			decay.get(spell).addDecay();
		} else {
			decay.put(spell, new DecayWrapper());
		}
		exp += amount;
		dirty = true;
	}

	@Override
	public void addObtainedSkill(AoVSkill skill) {
		if (!obtainedSkills.contains(skill)) {
			obtainedSkills.add(skill);
			dirty = true;
		}
	}

	@Override
	public boolean hasSkill(AoVSkill skill) {
		return obtainedSkills.contains(skill);
	}

	@Override
	public boolean hasCoreSkill() {
		for (AoVSkill skill : obtainedSkills)
			if (skill.isClassCore()) return true;
		return false;
	}

	@Override
	public AoVSkill getCoreSkill() {
		for (AoVSkill skill : obtainedSkills)
			if (skill.isClassCore()) return skill;
		return null;
	}

	@Override
	public List<AoVSkill> getObtainedSkills() {
		return obtainedSkills;
	}

	@Override
	public void removeSkill(AoVSkill skill) {
		obtainedSkills.remove(skill);
		dirty = true;
	}

	@Override
	public int getLevel() {
		return getLevelFromExp(exp);
	}

	@Override
	public int getMaxLevel() {
		return maxLevel;
	}

	@Override
	public void setMaxLevel(int amount) {
		maxLevel = amount;
		dirty = true;
	}

	@Override
	public int getExp() {
		return exp;
	}

	@Override
	public void setExp(int amount) {
		exp = amount;
		dirty = true;
	}

	@Override
	public int getExpNeededToLevel() {
		return getLevel() >= maxLevel ? 0 : getExpForLevel(this, getLevel() + 1) - exp;
	}

	@Override
	public int getSkillPoints() {
		return skillPoints;
	}

	@Override
	public void setSkillPoints(int amount) {
		skillPoints = amount;
		dirty = true;
	}

	@Override
	public int getSpentSkillPoints() {
		return getLevel() - skillPoints;
	}

	@Override
	public float getSpellPower() {
		return spellpower;
	}

	@Override
	public int getExtraCharges() {
		return extraCharges;
	}

	@Override
	public int getDodge() {
		return dodge;
	}

	@Override
	public int getDoubleStrike() {
		return doublestrike;
	}

	@Override
	public boolean hasSelectiveFocus() {
		return selectiveFocus;
	}

	@Override
	public void toggleInvokeMass(boolean b) {
		invokeMass = hasInvoke ? b : false;
		dirty = true;
	}

	@Override
	public void toggleInvokeMass() {
		invokeMass = hasInvoke ? !invokeMass : false;
		dirty = true;
	}

	@Override
	public boolean getInvokeMass() {
		return invokeMass;
	}

	@Override
	public boolean hasInvokeMass() {
		return hasInvoke;
	}

	@Override
	public void setSlot(Ability ability, int slot) {
		boolean flag = false;
		for (Ability check : getAbilities()) {
			if (check.compare(ability)) flag = true;
		}
		if (flag && slot >= 0 && slot < slots.length) slots[slot] = ability;
		dirty = true;
	}

	@Override
	public Ability getSlot(int slot) {
		return slot >= 0 && slot < slots.length ? slots[slot] : null;
	}

	@Override
	public int getSlotFromAbility(Ability ability) {
		int index = 0;
		for (Ability a : slots) {
			if (a != null && ability.compare(a)) return index;
			index++;
		}
		return -1;
	}

	@Override
	public int getCurrentSlot() {
		return currentSlot;
	}

	@Override
	public void setCurrentSlot(int index) {
		currentSlot = index;
		dirty = true;
	}

	@Override
	public boolean slotsContain(Ability ability) {
		for (Ability a : slots)
			if (a != null && ability.compare(a)) return true;
		return false;
	}

	@Override
	public void addToNearestSlot(Ability ability) {
		if (slotsContain(ability)) return;
		for (int i = 0; i < slots.length; i++) {
			if (slots[i] == null) {
				setSlot(ability, i);
				break;
			}
		}
		dirty = true;
	}

	@Override
	public void removeSlot(int slot) {
		if (slot >= 0 && slot < slots.length) slots[slot] = null;
		dirty = true;
	}

	@Override
	public void clearAllSlots() {
		slots = new Ability[] { null, null, null, null, null, null, null, null, null };
		dirty = true;
	}

	@Override
	public void copyFrom(IAoVCapability cap) {
		obtainedSkills = cap.getObtainedSkills();
		skillPoints = cap.getSkillPoints();
		exp = cap.getExp();
		maxLevel = cap.getMaxLevel();
		invokeMass = cap.getInvokeMass();
		for (int index = 0; index < 9; index++) {
			slots[index] = cap.getSlot(index);
		}
		currentSlot = cap.getCurrentSlot();
		dirty = true;
	}

	private void sendPacketUpdates(EntityPlayerMP player) {

		ByteBufOutputStream bos = new ByteBufOutputStream(Unpooled.buffer());
		DataOutputStream stream = new DataOutputStream(bos);
		try {
			stream.writeInt(ClientPacketHandler.getPacketTypeID(ClientPacketHandler.PacketType.AOVDATA));
			stream.writeInt(obtainedSkills.size());
			{
				for (AoVSkill skill : obtainedSkills)
					stream.writeInt(skill.getID());
			}
			stream.writeInt(skillPoints);
			stream.writeInt(exp);
			stream.writeInt(maxLevel);
			stream.writeBoolean(invokeMass);
			for (int index = 0; index < 9; index++) {
				Ability ability = slots[index];
				if (ability == null) {
					stream.writeBoolean(false);
				} else {
					stream.writeBoolean(true);
					ability.encode(stream);
				}
			}
			stream.writeInt(currentSlot);
			FMLProxyPacket packet = new FMLProxyPacket(new PacketBuffer(bos.buffer()), AoV.networkChannelName);
			AoV.channel.sendTo(packet, player);
			bos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void decodePacket(ByteBufInputStream stream) throws IOException {
		int size = 0;
		size = stream.readInt();
		{
			obtainedSkills.clear();
			for (int index = 0; index < size; index++) {
				obtainedSkills.add(AoVSkill.getSkillFromID(stream.readInt()));
			}
		}
		skillPoints = stream.readInt();
		exp = stream.readInt();
		maxLevel = stream.readInt();
		invokeMass = stream.readBoolean();
		for (int index = 0; index < 9; index++) {
			slots[index] = stream.readBoolean() ? Ability.construct(this, stream) : null;
		}
		currentSlot = stream.readInt();
		dirty = true;
	}

}
