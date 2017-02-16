package Tamaized.AoV.proxy;

import Tamaized.AoV.AoV;
import Tamaized.AoV.entity.projectile.caster.ProjectileNimbusRay;
import Tamaized.AoV.entity.projectile.caster.render.RenderNimbusRay;
import Tamaized.AoV.events.KeyHandler;
import Tamaized.AoV.gui.client.AoVOverlay;
import Tamaized.AoV.gui.client.AoVUIBar;
import Tamaized.AoV.network.ClientPacketHandler;
import Tamaized.TamModized.proxy.AbstractProxy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.IRenderFactory;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ClientProxy extends AbstractProxy {

	@SideOnly(Side.CLIENT)
	public static Minecraft mc = Minecraft.getMinecraft();

	@SideOnly(Side.CLIENT)
	public static AoVUIBar bar;

	public static KeyBinding key;
	public static boolean barToggle = false;

	@Override
	public void preRegisters() {

	}

	@Override
	public void preInit() {

		bar = new AoVUIBar();

		MinecraftForge.EVENT_BUS.register(new AoVOverlay());

		float shadowSize = 0.5F;
		
		RenderingRegistry.registerEntityRenderingHandler(ProjectileNimbusRay.class, new IRenderFactory<ProjectileNimbusRay>() {
			@Override
			public Render<? super ProjectileNimbusRay> createRenderFor(RenderManager manager) {
				return new RenderNimbusRay(manager);
			}
		});

	}

	@Override
	public void init() {

	}

	@Override
	public void postInit() {
		key = new KeyBinding("key.aovbar", org.lwjgl.input.Keyboard.KEY_LMENU, "key.categories.aov");
		ClientRegistry.registerKeyBinding(key);
		MinecraftForge.EVENT_BUS.register(new KeyHandler());
		AoV.channel.register(new ClientPacketHandler());
	}

}
