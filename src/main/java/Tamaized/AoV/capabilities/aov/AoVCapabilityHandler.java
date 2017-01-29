package Tamaized.AoV.capabilities.aov;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import Tamaized.AoV.AoV;
import Tamaized.AoV.common.handlers.ClientPacketHandler;
import Tamaized.AoV.core.abilities.Ability;
import Tamaized.AoV.core.abilities.AbilityBase;
import Tamaized.AoV.core.abilities.AuraBase;
import Tamaized.AoV.core.abilities.IAura;
import Tamaized.AoV.core.skills.AoVSkill;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
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

	private List<AoVSkill> obtainedSkills = new ArrayList<AoVSkill>();
	private List<Ability> abilities = new ArrayList<Ability>();
	private Map<AuraBase, Integer> auras = new HashMap<AuraBase, Integer>();
	private int skillPoints = 1;
	private int exp = 0;
	private int maxLevel = 15;
	private boolean invokeMass = false;
	private Ability[] slots = new Ability[] { null, null, null, null, null, null, null, null, null };
	private int currentSlot = 0;

	// Calculate and update these when 'dirty'
	private float spellpower = 0;
	private int extraCharges = 0;
	private boolean selectiveFocus = false;
	private boolean hasInvoke = false;

	// Keep this on the server
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
			skillPoints = getLevel()-1;
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
		updateAbilities();
		updateAuras();
		updateDecay();
		if (dirty) {
			updateValues();
			if (player instanceof EntityPlayerMP) sendPacketUpdates((EntityPlayerMP) player);
			dirty = false;
		}
		tick++;
	}

	private void updateValues() {
		if (lastLevel < 0) lastLevel = getLevel();
		if (lastLevel < getLevel()) {
			skillPoints += (getLevel() - lastLevel);
			lastLevel = getLevel();
		}
		spellpower = 0;
		extraCharges = 0;
		selectiveFocus = false;
		hasInvoke = false;
		for (AoVSkill skill : obtainedSkills) {
			spellpower += skill.getBuffs().spellPower;
			extraCharges += skill.getBuffs().charges;
			if (skill.getBuffs().selectiveFocus) selectiveFocus = true;
			for (AbilityBase ability : skill.getAbilities()) {
				if (ability == AbilityBase.invokeMass) hasInvoke = true;
			}

		}
	}

	private void updateAbilities() {
		for (Ability ability : abilities)
			ability.update();
	}

	private void updateAuras() {
		Iterator<Entry<AuraBase, Integer>> iter = auras.entrySet().iterator();
		while (iter.hasNext()) {
			Entry<AuraBase, Integer> e = iter.next();
			AuraBase k = e.getKey();
			// k.update(this);
			if (k.getCurrentLife() >= e.getValue()) iter.remove();
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
		for (Ability ability : abilities)
			ability.reset(this);
		dirty = true;
	}

	@Override
	public List<Ability> getAbilities() {
		return abilities;
	}

	@Override
	public boolean canUseAbility(Ability ability) {
		return abilities.contains(ability) && ability.canUse(this);
	}

	@Override
	public void addAbility(Ability ability) {
		if (abilities.size() < 9) {
			abilities.add(ability);
			dirty = true;
		}
	}

	@Override
	public void removeAbility(Ability ability) {
		if (abilities.contains(ability)) {
			abilities.remove(ability);
			dirty = true;
		}
	}

	@Override
	public void castAbility(Ability ability, EntityPlayer caster, EntityLivingBase target) {
		if (canUseAbility(ability)) ability.cast(caster, target);
	}

	@Override
	public void addAura(IAura aura) {
		// TODO Auto-generated method stub
		dirty = true;
	}

	@Override
	public void addExp(int amount, AbilityBase spell) { // TODO: send packet to client for overlay floating text
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
			if (a != null && ability.getAbility() == a.getAbility()) return true;
		return false;
	}

	@Override
	public void addToNearestSlot(Ability ability) {
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
		abilities = cap.getAbilities();
		skillPoints = cap.getSkillPoints();
		exp = cap.getExp();
		maxLevel = cap.getMaxLevel();
		invokeMass = cap.getInvokeMass();
		for (int index = 0; index < 9; index++) {
			slots[index] = cap.getSlot(index);
		}
		currentSlot = cap.getCurrentSlot();
		spellpower = cap.getSpellPower();
		extraCharges = cap.getExtraCharges();
		selectiveFocus = cap.hasSelectiveFocus();
		hasInvoke = cap.hasInvokeMass();
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
			stream.writeInt(abilities.size());
			{
				for (Ability ability : abilities)
					ability.encode(stream);
			}
			// TODO: figure out auras
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
			stream.writeFloat(spellpower);
			stream.writeInt(extraCharges);
			stream.writeBoolean(selectiveFocus);
			stream.writeBoolean(hasInvoke);
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
		size = stream.readInt();
		{
			abilities.clear();
			for (int index = 0; index < size; index++) {
				abilities.add(Ability.construct(this, stream));
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
		spellpower = stream.readFloat();
		extraCharges = stream.readInt();
		selectiveFocus = stream.readBoolean();
		hasInvoke = stream.readBoolean();
	}

}