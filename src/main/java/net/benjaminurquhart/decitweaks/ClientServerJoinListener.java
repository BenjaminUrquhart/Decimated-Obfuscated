package net.benjaminurquhart.decitweaks;

import java.lang.reflect.Method;
import java.net.SocketAddress;

import com.boehmod.lib.utils.BoehModLogger;
import com.boehmod.lib.utils.BoehModLogger.EnumLogType;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ClientServerJoinListener {
	
	private static Method notificationMethod;
	private static boolean disabled;
	
	static {
		try {
			notificationMethod = Class.forName("net.decimation.mod.a.b").getDeclaredMethod("a", String.class);
		}
		catch(Exception e) {
			disabled = true;
		}
	}
	@SubscribeEvent
	@SideOnly(Side.CLIENT)
	public void onPlayerJoin(FMLNetworkEvent.ClientConnectedToServerEvent event) {
		BoehModLogger.printLine(EnumLogType.CLIENT, "Connecting to server...");
		SocketAddress address = event.manager.getSocketAddress();
		BoehModLogger.printLine(EnumLogType.CLIENT, "Address: " + address);
		Decimated decimated = Decimated.instance;
		this.sendNotification(String.format("%d Forge mods and %d Litemod(s) are hidden", decimated.getOtherMods().size(), decimated.getLiteMods().size()));
	}
	private void sendNotification(String msg) {
		if(disabled) {
			return;
		}
		try {
			notificationMethod.invoke(null, msg);
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
}
