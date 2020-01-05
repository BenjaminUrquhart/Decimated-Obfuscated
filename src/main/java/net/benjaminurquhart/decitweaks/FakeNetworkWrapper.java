package net.benjaminurquhart.decitweaks;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
//import net.decimation.mod.b.k.i;

public class FakeNetworkWrapper extends SimpleNetworkWrapper {

	private SimpleNetworkWrapper real;
	private String cheatingResponseClassName;
	
	private static final List<String> HASHES = new ArrayList<>();
	
	static {
		try {
			Decimated.log("Calculating SHA-256 of deci jarfile...");
			List<String> tmp = new ArrayList<>();
			byte[] buff = new byte[1024];
			File deciFile = Decimated.instance.getDeciFile();
			FileInputStream stream = new FileInputStream(deciFile);
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			int i = 0;
			do {
				i = stream.read(buff);
				if(i > 0) {
					digest.update(buff, 0, i);
				}
			}
			while(i > 0);
			stream.close();
			String hash = "";
			for(byte b : digest.digest()) {
				hash += Integer.toString((b & 0xFF) + 256, 16).substring(1);
			}
			hash = hash.toUpperCase();
			Decimated.log(deciFile.getName()+" -> "+hash);
			Decimated.log("Finding master hash array...");
			Set<Class<?>> classes = Decimated.instance.getAllClassesInPackage(Decimated.instance.getDeciPackage());
			boolean foundProcess = false;
			boolean foundArray = false;
			classSearch:
			for(Class<?> clazz : classes) {
				foundProcess = foundArray = false;
				for(Field field : clazz.getDeclaredFields()) {
					if(Modifier.isStatic(field.getModifiers()) && field.getType().equals(String[].class)) {
						field.setAccessible(true);
						Decimated.log("Found candidate field "+field.getName()+" in class "+clazz.getName());
						if(field.get(null) == null) {
							Decimated.log("Field contains null, skipping...");
							continue;
						}
						tmp = Arrays.asList((String[])field.get(null));
						Decimated.log("Contents:\n"+tmp);
						if(tmp.contains(hash)) {
							Decimated.log("Found master hash array in class "+clazz.getName());
							HASHES.addAll(tmp);
							break classSearch;
						}
						foundArray = true;
					}
					if(field.getType().equals(Process.class)) {
						foundProcess = true;
					}
					if(foundProcess && foundArray) {
						Decimated.log("Found master hash array in class "+clazz.getName()+" with safety override");
						HASHES.addAll(tmp);
						break classSearch;
					}
				}
			}
			if(HASHES.isEmpty()) {
				Decimated.err("Failed to find hash list. Inserting known hash...");
				HASHES.add(hash);
			}
		} 
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
	
	private Field list;
	
	public FakeNetworkWrapper() {
		super("ctx");
	}
	
	protected void setChannel(SimpleNetworkWrapper channel) {
		this.real = channel;
	}
	@Override
    public Packet getPacketFrom(IMessage message) {
        return real.getPacketFrom(message);
    }
	@Override
    public void sendToAll(IMessage message) {
		FakeClientNetworkConnection.printStuff(message);
    	real.sendToAll(message);
    }
	@Override
    public void sendTo(IMessage message, EntityPlayerMP player) {
		FakeClientNetworkConnection.printStuff(message);
    	real.sendTo(message, player);
    }
	
	@SuppressWarnings("unchecked")
	@Override
    public void sendToServer(IMessage message) {
		try {
			FakeClientNetworkConnection.printStuff(message);
	    	if(this.isCheatingResponse(message)) {
	    		Decimated.log("Intercepted cheating response (Class: "+cheatingResponseClassName+")");
	    		List<String> hashes = (List<String>) list.get(message);
	    		Decimated.log("Replacing hash list with whitelist...");
	    		hashes.clear();
	    		hashes.addAll(HASHES);
	    		Decimated.log("Done");
	    	}
		}
		catch(Exception e) {
			e.printStackTrace();
		}
    	real.sendToServer(message);
    }
	private boolean isCheatingResponse(IMessage msg) {
		if(cheatingResponseClassName != null) {
			return msg.getClass().getName().equals(cheatingResponseClassName);
		}
		try {
			boolean foundList = false, foundString = false;
			for(Field field : msg.getClass().getDeclaredFields()) {
				field.setAccessible(true);
				if(field.getType().equals(String.class) && ((String)field.get(msg)).contains("BoehMod Anticheat")) {
					foundString = true;
				}
				if(field.getType().equals(ArrayList.class)) {
					list = field;
					foundList = true;
				}
			}
			if(foundList && foundString) {
				Decimated.log("Found cheating response class: " + msg.getClass().getName());
				cheatingResponseClassName = msg.getClass().getName();
			}
			return foundList && foundString;
		}
		catch(Throwable e) {
			throw new RuntimeException(e);
		}
	}
	public long calculateRealSize() {
		long size = 0;
		File folder = new File("mods");
		File other = new File(folder, "1.7.10");
		
		for(File file : folder.listFiles()) {
			if(file.isFile() && !file.getName().toLowerCase().contains("optifile") && !file.getName().equals(".DS_Store")) {
				size += file.length();
			}
		}
		if(other.exists() || other.isDirectory()) {
			for(File file : other.listFiles()) {
				if(file.isFile() && !file.getName().toLowerCase().contains("optifile") && !file.getName().equals(".DS_Store")) {
					size += file.length();
				}
			}
		}
		return size;
	}
	public long calculateSize() {
		List<ModContainer> mods = Loader.instance().getActiveModList();
		long size = mods.stream()
						.filter(mod -> mod.getModId().equals("deci") || mod.getModId().equals("gvc"))
						.map(ModContainer::getSource)
						.peek(file -> Decimated.log("Found whitelisted mod in " + file.getName() + " (Size: " + file.length() + ")"))
						.mapToLong(File::length)
						.sum();
		return size;
	}
}
