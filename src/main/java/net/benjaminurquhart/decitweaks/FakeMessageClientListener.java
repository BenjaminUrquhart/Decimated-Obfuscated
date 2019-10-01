package net.benjaminurquhart.decitweaks;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.decimation.mod.utilities.net.client_network.api.messages.Response_FromServer_ServerList;
import net.decimation.mod.utilities.net.client_network.api.objects.ObjectServer;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.FrameworkMessage;
import com.esotericsoftware.kryonet.Listener;

public class FakeMessageClientListener extends Listener {

	@Override
	public void received(Connection conn, Object object) {
		if(object instanceof FrameworkMessage.KeepAlive) return;
		Decimated.debug("Received object (Class: " + object.getClass().getName() + ")");
		try {
			if(object instanceof Response_FromServer_ServerList) {
				printServerList(object);
				return;
			}
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
	private void printServerList(Object object) {
		Response_FromServer_ServerList serverList = (Response_FromServer_ServerList) object;
		Decimated.debug("Servers:");
		List<ObjectServer> servers = serverList.getServerObjects();
		for(ObjectServer server : servers) {
			Decimated.debug("--------------------------------------------------------------------");
			Decimated.debug("Name:        " + sanitize(server.getServerName()));
			Decimated.debug("MOTD:        " + sanitize(server.getServerMOTD()));
			Decimated.debug("Address:     " + server.getServerIP() + ":" + server.getServerPort());
			Decimated.debug("Mod version: " + server.getServerModVersion());
		}
		Decimated.debug("--------------------------------------------------------------------");
	}
	private String sanitize(String s) {
		return s.replaceAll("\u00A7.", "").replace("\n", " ");
	}
}
