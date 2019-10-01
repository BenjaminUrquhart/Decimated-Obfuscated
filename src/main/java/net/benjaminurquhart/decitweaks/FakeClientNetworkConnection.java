package net.benjaminurquhart.decitweaks;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.FrameworkMessage;

public class FakeClientNetworkConnection extends Client {

	public FakeClientNetworkConnection() {
		super(32768, 8192);
		this.addListener(new FakeMessageClientListener());
	}
	@Override
	public int sendTCP(Object object) {
		printStuff(object);
		return super.sendTCP(object);
	}
	@Override
	public int sendUDP(Object object) {
		printStuff(object);
		return super.sendUDP(object);
	}
	public static void printStuff(Object object) {
		if(object instanceof FrameworkMessage.KeepAlive) return;
		Decimated.debug("Sending object (Class: " + object.getClass().getName() + ")");
		try {
			Field[] fields = object.getClass().getFields();
			Set<String> seen = new HashSet<>();
			Decimated.debug("Fields:");
			for(Field field : fields) {
				field.setAccessible(true);
				seen.add(field.getName());
				Decimated.debug(field.getName() + ": " + field.get(object));
			}
			fields = object.getClass().getDeclaredFields();
			for(Field field : fields) {
				field.setAccessible(true);
				if(seen.add(field.getName())) {
					Decimated.debug(field.getName() + ": " + field.get(object));
				}
			}
		}
		catch(Exception e) {
			Decimated.err(e.toString());
		}
	}
}
