package net.benjaminurquhart.decitweaks;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
	
	private static final List<String> HASHES = Arrays.asList(
			"8BCD9D83DE001A0591F498105F369F070C47D2C9192774D5A5B69F646E44FAB4",
			"1DC0F1FE2614C5354292473E44649F90508AE069D249550EE4D9CE794EA8F0BF",
			"CC5A3C4D7B169CC0B0899F8304360B38E69D34311546E1B36631FB2C6A44F742",
			"CC07072E8BCA66F527A981D3C26A980FF127221B7E4B264D2056455D912513E0",
			"415CBA487EA5EDDCA981C6A00A5426236F8DF36C635F5808ABA227AD96D1B478",
			"1CE3A1AB348AAC5CFE70FBC60496146C594FAA21BB1BC5FC73DFB16432108940",
			"FA5482F06F9D7B8538D087E66C163C04AFD85F7669FF63FF330E1045503D6DA4",
			"11E974AC0A0BBE1ED63EB4D0F8ECCE5245E77DDE8AEFD78EE3C95F49B3C3C31D",
			"A71C6396AA3152773C1DB3444EB0667E22C440EE76552306A617CACD11277840",
			"59FCB6AA78F9604A31355EAFF8CB72CF2AEB0BCFDFD4EDD6D841637C14A9AB4A",
			"534313028FE05F39CFFEE217F385E222CF17ED258A95292D25982248F20D0193");
	
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
	    		Decimated.log("Intercepted cheating response");
	    		List<String> hashes = (List<String>) list.get(message);
	    		Decimated.log("Hashes:");
	    		for(String hash : hashes) {
	    			Decimated.log(hash);
	    		}
	    		Decimated.log("Total: "+hashes.size());
	    		if(hashes.isEmpty()) {
		    		hashes.addAll(HASHES);
	    		}
	    		else {
		    		String hash = hashes.get(hashes.size()-1);
		    		Decimated.log("Keeping hash "+hash);
		    		hashes.clear();
		    		hashes.add(hash);
	    		}
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
