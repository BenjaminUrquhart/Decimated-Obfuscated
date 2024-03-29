package net.benjaminurquhart.decitweaks;

import sun.misc.Unsafe;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

import net.minecraftforge.common.MinecraftForge;

import java.io.File;
import java.lang.reflect.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.esotericsoftware.kryonet.Client;
import com.google.common.reflect.ClassPath;

//import net.decimation.mod.utilities.net.client_network.api.objects.ObjectRegistry;

@Mod(modid=Decimated.MODID, version=Decimated.VERSION, name="Decimated", dependencies="required-after:deci")
public class Decimated {
	
	public static final String MODID = "decimated-obf";
	public static final String VERSION = "0.0.4a";
	
	@Instance(MODID)
	public static Decimated instance;
	
	private List<ModContainer> otherMods;
	private List<?> litemods;
	
	private Map<String, Set<Class<?>>> packageCache;
	
	private String deciPackage;
	private boolean server;
	private Unsafe unsafe;
	
	private Object deciInstance;
	private File deciFile;
	
	private FakeNetworkWrapper wrapper;
	
	public Decimated() {
		this.packageCache = new HashMap<>();
	}
	
	public FakeNetworkWrapper getFakeDeciNetworkWrapper() {
		return wrapper;
	}
	public String getDeciPackage() {
		return deciPackage;
	}
	public Object getDeciInstance() {
		return deciInstance;
	}
	public File getDeciFile() {
		return deciFile;
	}
	public List<ModContainer> getOtherMods() {
		return Collections.unmodifiableList(otherMods);
	}
	public List<?> getLiteMods() {
		return litemods == null ? Collections.emptyList() : Collections.unmodifiableList(litemods);
	}
	private Set<Class<?>> findInnerClasses(Class<?> clazz) throws Exception {
		Set<Class<?>> out = new HashSet<>();
		for(Class<?> c : clazz.getDeclaredClasses()) {
			out.add(c);
			out.addAll(this.findInnerClasses(c));
		}
		return out;
	}
	public Set<Class<?>> getAllClassesInPackage(String pkg) throws Exception {
		if(packageCache.containsKey(pkg)) {
			return packageCache.get(pkg);
		}
		Class<?> tmp;
		Set<Class<?>> classes = new HashSet<>();
		ClassPath classPath = ClassPath.from(this.getClass().getClassLoader());
		Set<ClassPath.ClassInfo> infoSet = classPath.getTopLevelClassesRecursive(pkg);
		for(ClassPath.ClassInfo info : infoSet) {
			try {
				tmp = Class.forName(info.getName());
			}
			catch(Throwable e) {
				err("Failed to load class "+info.getName()+":");
				err(e.toString());
				continue;
			}
			if(classes.add(tmp)) {
				classes.addAll(this.findInnerClasses(tmp));
			}
		}
		packageCache.put(pkg, classes);
		return classes;
	}
	@EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		server = event.getSide() == Side.SERVER;
		if(server) {
			err("This is a server environment!");
			Loader.instance().runtimeDisableMod(MODID);
			return;
		}
		try {
			for(ModContainer container : Loader.instance().getModList()) {
				if(container.getModId().equals("deci")) {
					deciInstance = container.getMod();
					deciPackage = deciInstance.getClass().getPackage().getName();
					deciPackage = deciPackage.split("\\.")[0];
					log("Decimation package: " + deciPackage);
					deciFile = container.getSource();
					break;
				}
			}
			if(deciPackage == null) {
				throw new IllegalStateException("Failed to find deci package");
			}
		}
		catch(Throwable e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		boolean networking = false;
		log("Attempting to inject fake network wrapper...");
		try {
			Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
			unsafeField.setAccessible(true);
			unsafe = (Unsafe) unsafeField.get(null);
			
			wrapper = (FakeNetworkWrapper) unsafe.allocateInstance(FakeNetworkWrapper.class);
			
			SimpleNetworkWrapper real = null;
			Set<Class<?>> classes = this.getAllClassesInPackage(deciPackage);
			log("Found "+classes.size()+" potential classes. Scanning for field...");
			
			int fieldCount = 0;
			for(Class<?> clazz : classes) {
				for(Field field : clazz.getDeclaredFields()) {
					field.setAccessible(true);
					if(field.getType().equals(SimpleNetworkWrapper.class)) {
						if((field.getModifiers() & Modifier.STATIC) != Modifier.STATIC) {
							log("Skipping instance field "+field.getDeclaringClass().getName()+"."+field.getName());
						}
						else {
							log("Found static field "+field.getDeclaringClass().getName()+"."+field.getName());
							makeUnfinal(field);
							real = (SimpleNetworkWrapper) field.get(null);
							if(real == null) {
								log("Field was null, continuing...");
								continue;
							}
							else {
								wrapper.setChannel(real);
							}
							fieldCount++;
							field.set(null, wrapper);
							break;
						}
					}
				}
			}
			if(fieldCount == 0) {
				throw new IllegalStateException("Failed to find any static fields!");
			}
			else {
				log("Inserted wrapper into "+fieldCount+" static field"+(fieldCount<2?"":"s"));
				networking = true;
			}
		}
		catch(IllegalStateException e) {
			throw e;
		}
		catch(Throwable e) {
			err("An error occured!");
			e.printStackTrace();
		}
		if(networking) {
			log("Successfully planted networking interceptor");
			log("All warnings generated by the anticheat system can be safely ignored :)");
		}
		else {
			err("Failed to disable anticheat!");
			throw new IllegalStateException("Failed to disable anticheat!");
			//err("You will be unable to join servers!");
			//err("Please refer to the exception(s) printed above for details.");
		}
	}
	@EventHandler
	public void init(FMLInitializationEvent event) {
		if(server) return;
		otherMods = Loader.instance().getActiveModList()
				 .stream()
				 .filter(mod -> !mod.getModId().equals("deci") && !mod.getModId().equals("gvc"))
				 .filter(mod -> !mod.getModId().equals("FML") && !mod.getModId().equals("Forge"))
				 .filter(mod -> !mod.getModId().equals("mcp"))
				 .collect(Collectors.toList());
		log("Populated list of hidden Forge mods");
		
		log("Attempting to inject fake kryo client...");
		try {
			int count = 0;
			Client client = null;
			FakeClientNetworkConnection conn = new FakeClientNetworkConnection();
			Set<Class<?>> classes = this.getAllClassesInPackage(deciPackage);
			//ObjectRegistry.registerObjects(conn.getKryo());
			for(Class<?> clazz : classes) {
				for(Field field : clazz.getDeclaredFields()) {
					field.setAccessible(true);
					if(field.getType().equals(Client.class) && (field.getModifiers() & Modifier.STATIC) == Modifier.STATIC) {
						log("Found static kryo client field "+field.getDeclaringClass().getName()+"."+field.getName());
						client = (Client) field.get(null);
						client.close();
						makeUnfinal(field);
						field.set(null, conn);
						count++;
					}
				}
			}
			if(count > 0) {
				log("Inserted kryo client into "+count+" static field"+(count<2?"":"s"));
			}
			else {
				err("Failed to locate static kryo client!");
				err("Attempting to search instance fields...");
				
				Object tmp;
				Set<Object> checked = new HashSet<>();
				for(Field field : deciInstance.getClass().getDeclaredFields()) {
					field.setAccessible(true);
					if(field.getType().equals(Client.class) && (field.getModifiers() & Modifier.STATIC) == 0) {
						log("Found instance kryo client field "+field.getDeclaringClass().getName()+"."+field.getName());
						client = (Client) field.get(deciInstance);
						if(client != null) {
							client.close();
						}
						makeUnfinal(field);
						field.set(deciInstance, conn);
						count++;
					}
					else if((tmp = field.get(deciInstance)) != null && checked.add(tmp)){
						for(Field f : field.getType().getDeclaredFields()) {
							f.setAccessible(true);
							if(f.getType().equals(Client.class) && (f.getModifiers() & Modifier.STATIC) == 0) {
								log("Found instance kryo client field "+f.getDeclaringClass().getName()+"."+f.getName());
								client = (Client) f.get(tmp);
								if(client != null) {
									client.close();
								}
								makeUnfinal(f);
								f.set(tmp, conn);
								count++;
							}
						}
					}
				}
				if(count > 0) {
					log("Inserted kryo client into "+count+" instance field"+(count<2?"":"s"));
				}
				else {
					err("Failed to locate kryo client!");
				}
			}
		} 
		catch (Exception e) {
			err("An error occured!");
			e.printStackTrace();
		}
		
	}
	@EventHandler
	public void postInit(FMLPostInitializationEvent event) {
		if(server) return;
		log("Done with mod initialization.");
		log("The following Forge mod(s) will be hidden from the anticheat check:");
		for(ModContainer mod : otherMods) {
			log(String.format("%s %s (ID: %s, File: %s)", mod.getName(), mod.getDisplayVersion(), mod.getModId(), mod.getSource().getName()));
		}
		try {
			Class<?> liteloaderClass = Class.forName("com.mumfrey.liteloader.core.LiteLoader");
			Object liteloader = liteloaderClass.getMethod("getInstance").invoke(null);
			
			Field containerHolderField = liteloaderClass.getDeclaredField("mods");
			containerHolderField.setAccessible(true);
			Object holder = containerHolderField.get(liteloader);
			
			List<?> mods = (List<?>)holder.getClass().getMethod("getLoadedMods").invoke(holder);
			this.litemods = mods;
			
			Class<?> modInfoClass = Class.forName("com.mumfrey.liteloader.core.ModInfo");
			
			Method name = null, version = null, id = null, urlMethod = null;
			String url;
			log("The following litemod(s) will be hidden from the anticheat check:");
			for(Object mod : mods) {
				mod = modInfoClass.cast(mod);
				if(name == null) {
					name = modInfoClass.getMethod("getDisplayName");
					name.setAccessible(true);
				}
				if(version == null) {
					version = modInfoClass.getMethod("getVersion");
					name.setAccessible(true);
				}
				if(id == null) {
					id = modInfoClass.getMethod("getIdentifier");
					id.setAccessible(true);
				}
				if(urlMethod == null) {
					urlMethod = modInfoClass.getMethod("getURL");
					urlMethod.setAccessible(true);
				}
				url = String.valueOf(urlMethod.invoke(mod));
				if(url.endsWith("/")) {
					url = url.substring(0, url.length() - 1);
				}
				if(url.contains("/")) {
					url = url.substring(url.lastIndexOf("/") + 1);
				}
				if(url.trim().isEmpty()) {
					url = "<unknown file>";
				}
				log(String.format("%s %s (ID: %s, File: %s)", name.invoke(mod), version.invoke(mod), id.invoke(mod), url));
			}
		}
		catch(ClassNotFoundException e) {
			log("Liteloader is not installed. Skipping litemod check (" + e + ")");
		} 
		catch (Exception e) {
			err("An error occurred when getting litemods:");
			e.printStackTrace();
		}
		
		ClientServerJoinListener listener = new ClientServerJoinListener();
		CrashKeybindListener crasher = new CrashKeybindListener();
		
		MinecraftForge.EVENT_BUS.register(crasher);
		MinecraftForge.EVENT_BUS.register(listener);
		FMLCommonHandler.instance().bus().register(crasher);
		FMLCommonHandler.instance().bus().register(listener);
	}
	protected static void makeUnfinal(Field field) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		Field modifiers = Field.class.getDeclaredField("modifiers");
		modifiers.setAccessible(true);
		modifiers.setInt(field, modifiers.getInt(field) & ~Modifier.FINAL);
	}
	protected static void log(String s) {
		System.out.println("\u001b[36;1m[Decimated-LOG] " + s + "\u001b[0m");
	}
	protected static void err(String s) {
		System.out.println("\u001b[33;1m[Decimated-ERR] " + s + "\u001b[0m");
	}
	protected static void debug(String s) {
		System.out.println("\u001b[32;1m[Decimated-DBG] " + s + "\u001b[0m");
	}
}
