package com.bergerkiller.bukkit.mw;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Random;
import java.util.logging.Level;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipException;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.reflection.classes.RegionFileCacheRef;
import com.bergerkiller.bukkit.common.reflection.classes.RegionFileRef;
import com.bergerkiller.bukkit.common.utils.NBTUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;

public class WorldManager {

	/**
	 * Closes all file streams associated with the world specified.
	 * 
	 * @param world to close
	 */
	public static void closeWorldStreams(World world) {
		synchronized (RegionFileCacheRef.TEMPLATE.getType()) {
			try {
				String worldPart = "." + File.separator + world.getName();
				Iterator<Entry<File, Object>> iter = RegionFileCacheRef.FILES.entrySet().iterator();
				Entry<File, Object> entry;
				while (iter.hasNext()) {
					entry = iter.next();
					if (!entry.getKey().toString().startsWith(worldPart) || entry.getValue() == null) {
						continue;
					}
					try {
						RegionFileRef.close.invoke(entry.getValue());
						iter.remove();
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
			} catch (Exception ex) {
				MyWorlds.plugin.log(Level.WARNING, "Exception while removing world reference for '" + world.getName() + "'!");
				ex.printStackTrace();
			}
		}
	}

	/*
	 * Get or set the respawn point of a world
	 */
	public static void setSpawnLocation(String forWorld, Location destination) {
		setSpawn(forWorld, new Position(destination));
	}
	public static void setSpawn(String forWorld, Position destination) {
		WorldConfig.get(forWorld).spawnPoint = destination;
	}
	public static Position getRespawn(String ofWorld) {
		return WorldConfig.get(ofWorld).spawnPoint;
	}
	public static Location getRespawnLocation(String ofWorld) {
		Position pos = getRespawn(ofWorld);
		if (pos != null) {
			Location loc = pos.toLocation();
			if (loc.getWorld() != null) {
				return loc;
			}
		}
		return null;
	}
	public static Location getRespawnLocation(World ofWorld) {
		return getRespawnLocation(ofWorld.getName());
	}
	
	/*
	 * Gets a possible teleport position on a certain world
	 */
	public static Location getSpawnLocation(World onWorld) {
		Position[] pos = getSpawnPoints(onWorld);
		if (pos.length > 0) return pos[0].toLocation();
		return onWorld.getSpawnLocation();
	}
	public static Position[] getSpawnPoints() {
		Collection<WorldConfig> all = WorldConfig.all();
		Position[] pos = new Position[all.size()];
		int i = 0;
		for (WorldConfig wc : all) {
			pos[i] = wc.spawnPoint;
			i++;
		}
		return pos;
	}
	public static Position[] getSpawnPoints(World onWorld) {
		return getSpawnPoints(onWorld.getName());
	}
	public static Position[] getSpawnPoints(String onWorld) {
		ArrayList<Position> pos = new ArrayList<Position>();
		for (Position p : getSpawnPoints()) {
			if (p.getWorldName().equalsIgnoreCase(onWorld)) {
				pos.add(p);
			}
		}
		return pos.toArray(new Position[0]);
	}

	/*
	 * Chunk generators
	 */
	public static String[] getGeneratorPlugins() {
		ArrayList<String> gens = new ArrayList<String>();
		for (Plugin plugin : Bukkit.getServer().getPluginManager().getPlugins()) {
			try {
				String mainclass = plugin.getDescription().getMain();
				Class<?> cmain = Class.forName(mainclass);
				if (cmain == null) continue;
				if (cmain.getMethod("getDefaultWorldGenerator", String.class, String.class).getDeclaringClass() != JavaPlugin.class) {
					gens.add(plugin.getDescription().getName());
				}
			} catch (Throwable t) {}
		}
		return gens.toArray(new String[0]);
	}

	public static String fixGeneratorName(String name) {
		if (name == null) return null;
		String id = "";
		int index = name.indexOf(":");
		if (index != -1) {
			id = name.substring(index + 1);
			name = name.substring(0, index);
		}
		if (!name.equals("")) {
			name = name.toLowerCase();
			//get plugin
			final String pname = ParseUtil.parseArray(getGeneratorPlugins(), name, null);
			if (pname == null) {
				return null;
			} else {
				return pname + ":" + id;
			}
		} else {
			return name + ":" + id;
		}
	}
	public static ChunkGenerator getGenerator(String worldname, String name) {
		if (name == null) return null;
		String id = "";
		int index = name.indexOf(":");
		if (index != -1) {
			id = name.substring(index + 1);
			name = name.substring(0, index);
		}
		Plugin plug = Bukkit.getServer().getPluginManager().getPlugin(name);
		if (plug != null) {
			return plug.getDefaultWorldGenerator(worldname, id);
		} else {
			return null;
		}
	}
	public static void setGenerator(String worldname, String name) {
		WorldConfig.get(worldname).chunkGeneratorName = name;
	}

	/*
	 * General world fields
	 */
	public static long getSeed(String seed) {
		if (seed == null) return 0;
		long seedval = 0;
		try {
			seedval = Long.parseLong(seed);
		} catch (Exception ex) {
			seedval = seed.hashCode();
		}
		return seedval;
	}
	public static long getRandomSeed(String seed) {
		long seedval = getSeed(seed);
		if (seedval == 0) {
			seedval = new Random().nextLong();
		}
		return seedval;
	}

	public static String getWorldName(CommandSender sender, String[] args, boolean useAlternative) {
		String alternative = (args == null || args.length == 0) ? null : args[args.length - 1];
		return getWorldName(sender, alternative, useAlternative);
	}
	public static String getWorldName(CommandSender sender, String alternative, boolean useAlternative) {
		String worldname = null;
		if (useAlternative) {
			worldname = WorldManager.matchWorld(alternative);
		} else if (sender instanceof Player) {
			worldname = ((Player) sender).getWorld().getName();
		} else {
			for (World w : Bukkit.getServer().getWorlds()) {
				worldname = w.getName();
				break;
			}
		}
		return worldname;
	}

	public static boolean generateData(String worldname, String seed) {
		return generateData(worldname, getRandomSeed(seed));
	}
	public static boolean generateData(String worldname, long seed) {
		CommonTagCompound data = new CommonTagCompound("Data");
		data.putValue("thundering", (byte) 0);
		data.putValue("thundering", (byte) 0);
		data.putValue("LastPlayed", System.currentTimeMillis());
		data.putValue("RandomSeed", seed);
		data.putValue("version", (int) 19132);
		data.putValue("Time", 0L);
		data.putValue("raining", (byte) 0);
		data.putValue("SpawnX", 0);
		data.putValue("thunderTime", (int) 200000000);
		data.putValue("SpawnY", 64);
		data.putValue("SpawnZ", 0);
		data.putValue("LevelName", worldname);
		data.putValue("SizeOnDisk", getWorldSize(worldname));
		data.putValue("rainTime", (int) 50000);
	    return setData(worldname, data);
	}
	public static CommonTagCompound getData(String worldname) {
		File f = getDataFile(worldname);
		if (!f.exists()) {
			return null;
		}
		try {
			CommonTagCompound root = CommonTagCompound.readFrom(f);
			if (root != null) {
				return root.get("Data", CommonTagCompound.class);
			} else {
				return null;
			}
		} catch (Exception ex) {
			return null;
		}
	}

	public static boolean setData(String worldname, CommonTagCompound data) {
    	try {
			CommonTagCompound root = new CommonTagCompound();
			root.put(data.getName(), data);
			data.writeTo(getDataFile(worldname));
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	public static File getDataFile(String worldname) {
		return new File(WorldUtil.getWorldFolder(worldname), "level.dat");
	}
	public static File getUIDFile(String worldname) {
		return new File(WorldUtil.getWorldFolder(worldname), "uid.dat");
	}

	public static long getWorldSize(String worldname) {
		return getFolderSize(WorldUtil.getWorldFolder(worldname));
	}
	public static WorldInfo getInfo(String worldname) {
		WorldInfo info = null;
		try {
			CommonTagCompound t = getData(worldname);
			if (t != null) {
				info = new WorldInfo();
				info.seed = t.getValue("RandomSeed", 0L);
				info.time = t.getValue("Time", 0L);
				info.raining = t.getValue("raining", (byte) 0) != 0;
		        info.thundering = t.getValue("thundering", (byte) 0) != 0;
			}
		} catch (Exception ex) {}
		World w = getWorld(worldname);
		if (w != null) {
			if (info == null) {
				info = new WorldInfo();
			}
			info.seed = w.getSeed();
			info.time = w.getFullTime();
			info.raining = w.hasStorm();
	        info.thundering = w.isThundering();
		}
		if (info != null && MyWorlds.calculateWorldSize) {
			info.size = getWorldSize(worldname);
		}
		return info;
	}
	public static String matchWorld(String matchname) {
		if (matchname == null || matchname.isEmpty()) {
			return null;
		}
		Collection<String> worldnames = WorldUtil.getLoadableWorlds();
		for (String worldname : worldnames) {
			if (worldname.equalsIgnoreCase(matchname)) return worldname;
		}
		matchname = matchname.toLowerCase();
		for (String worldname : worldnames) {
			if (worldname.toLowerCase().contains(matchname)) return worldname;
		}
		return null;
	}
	public static World getWorld(String worldname) {
		if (worldname == null) return null;
		try {
			return Bukkit.getServer().getWorld(worldname);
		} catch (Exception ex) {
			return null;
		}
	}

	public static boolean isBroken(String worldname) {
		return getData(worldname) == null && !isLoaded(worldname);
	}
	public static boolean isLoaded(String worldname) {
		return getWorld(worldname) != null;
	}
	public static boolean worldExists(String worldname) {
		return worldname != null && WorldUtil.isLoadableWorld(worldname);
	}

	public static World getOrCreateWorld(String worldname) {
		World w = getWorld(worldname);
		if (w != null) return w;
		return createWorld(worldname, 0);
	}
	
	public static boolean unload(World world) {
		if (world == null) return false;
		return Bukkit.getServer().unloadWorld(world, world.isAutoSave());
	}

	/**
	 * Regenerates the spawn point for a world if it is not properly set<br>
	 * Also updates the spawn position in the world configuration
	 * 
	 * @param world to regenerate the spawn point for
	 */
	public static void fixSpawnLocation(World world) {
		Environment env = world.getEnvironment();
		Location loc = world.getSpawnLocation();
		if (env == Environment.NETHER || env == Environment.THE_END) {
			// Use a portal agent to generate the world spawn point
			loc = WorldUtil.findSpawnLocation(loc);
			if (loc == null) {
				return; // Failure?
			}
		} else {
			loc.setY(loc.getWorld().getHighestBlockYAt(loc));
		}

		// Minor offset
		loc.setX(0.5 + (double) loc.getBlockX());
		loc.setY(0.5 + (double) loc.getBlockY());
		loc.setZ(0.5 + (double) loc.getBlockZ());

		// Set new fixed spawn position
		world.setSpawnLocation(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

		// Set spawn position of world config
		WorldConfig.get(world).spawnPoint = new Position(loc);
	}

	public static World createWorld(String worldname, long seed) {
		final boolean load = WorldManager.worldExists(worldname);
		WorldConfig wc = WorldConfig.get(worldname);
		StringBuilder msg = new StringBuilder();
		if (load) {
			msg.append("Loading");
		} else {
			msg.append("Generating");
		}
		msg.append(" world '").append(worldname).append("'");
		if (seed == 0) {
			if (wc.chunkGeneratorName != null) {
				msg.append(" using chunk generator: '").append(wc.chunkGeneratorName).append("'");
			}
		} else {
			msg.append(" using seed ").append(seed);
			if (wc.chunkGeneratorName != null) {
				msg.append(" and chunk generator: '").append(wc.chunkGeneratorName).append("'");
			}
		}
		MyWorlds.plugin.log(Level.INFO, msg.toString());
		
		final int retrycount = 3;
		World w = null;
		int i = 0;
		ChunkGenerator cgen = null;
		try {
			if (wc.chunkGeneratorName != null) {
				cgen = getGenerator(worldname, wc.chunkGeneratorName);
			}
		} catch (Exception ex) {}
		if (cgen == null) {
			if (wc.chunkGeneratorName != null) {
				MyWorlds.plugin.log(Level.SEVERE, "World '" + worldname + "' could not be loaded because the chunk generator '" + wc.chunkGeneratorName + "' was not found!");
				return null;
			}
		}
		for (i = 0; i < retrycount + 1; i++) {
			try {
				WorldCreator c = new WorldCreator(worldname);
				wc.worldmode.apply(c);
				if (seed != 0) {
					c.seed(seed);
				}
				c.generator(cgen);
				w = c.createWorld();
			} catch (Exception ex) {
				MyWorlds.plugin.log(Level.WARNING, "World load issue: " + ex.getMessage());
				for (StackTraceElement el : ex.getStackTrace()) {
					if (el.getClassName().equals("com.bergerkiller.bukkit.mw.WorldManager")) break;
					System.out.println("    at " + el.toString());
				}
			}
			if (w != null) break;
		}
		if (w != null) {
			// Logic for newly generated worlds
			if (!load) {
				// Generate a possible spawn point for this world
				fixSpawnLocation(w);
			}
			wc.update(w);
		}
		if (w == null) {
			MyWorlds.plugin.log(Level.WARNING, "Operation failed after " + i + " retries!");
		} else if (i == 1) {
			MyWorlds.plugin.log(Level.INFO, "Operation succeeded after 1 retry!");
		} else if (i > 0) {
			MyWorlds.plugin.log(Level.INFO, "Operation succeeded after " + i + " retries!");
		}
		return w;
	}

	private static boolean delete(File folder) {
		if (folder.isDirectory()) {
			for (File f : folder.listFiles()) {
				if (!delete(f)) return false;
			}
		}
		return folder.delete();
	}
    public static boolean copy(File sourceLocation, File targetLocation) {
    	try {
            if (sourceLocation.isDirectory()) {
                if (!targetLocation.exists()) {
                    targetLocation.mkdir();
                }
                for (String child : sourceLocation.list()) {
                    if (!copy(new File(sourceLocation, child), new File(targetLocation, child))) {
                    	return false;
                    }
                }
            } else {
                InputStream in = new FileInputStream(sourceLocation);
                OutputStream out = new FileOutputStream(targetLocation);
                // Copy the bits from instream to outstream
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                in.close();
                out.close();
            }
            return true;
    	} catch (IOException ex) {
    		ex.printStackTrace();
    		return false;
    	}
    }
	private static long getFolderSize(File folder) {
		if (!folder.exists()) return 0;
		if (folder.isDirectory()) {
			long size = 0;
			for (File subfile : folder.listFiles()) {
				size += getFolderSize(subfile);
			}
			return size;
		} else {
			return folder.length();
		}
	}

	private static boolean renameWorld(String worldname, String newname) {
		if (isLoaded(worldname)) {
			return false;
		}
		CommonTagCompound data = getData(worldname);
		if (data == null) {
			return false;
		}
		data.putValue("LevelName", newname);
		return setData(worldname, data);
	}

	public static boolean deleteWorld(String worldname) {
		return delete(WorldUtil.getWorldFolder(worldname));
	}
	public static boolean copyWorld(String worldname, String newname) {
		File destFolder = WorldUtil.getWorldFolder(newname);
		if (!copy(WorldUtil.getWorldFolder(worldname), destFolder)) return false;;
		renameWorld(newname, newname);
		File uid = new File(destFolder, "uid.dat");
		if (uid.exists()) uid.delete();
		return true;
	}

	public static Location getEvacuation(Player player) {
		World world = player.getWorld();
		String[] portalnames;
		if (Permission.COMMAND_SPAWN.has(player) || Permission.COMMAND_TPP.has(player)) {
			for (Position pos : getSpawnPoints()) {
				Location loc = pos.toLocation();
				if (loc.getWorld() == null || loc.getWorld() == world) {
					continue;
				}
				if (Permission.canEnter(player, loc.getWorld())) {
					return loc;
				}
			}
			portalnames = Portal.getPortals();
		} else {
			portalnames = Portal.getPortals(world);
		}
		for (String name : portalnames) {
			if (Permission.canEnterPortal(player, name)) {
				Location loc = Portal.getPortalLocation(name, player.getWorld().getName(), true);
				if (loc == null || loc.getWorld() == null || loc.getWorld() == world) {
					continue;
				}
				if (Permission.canEnter(player, loc.getWorld())) {
					return loc;
				}
			}
		}
		return null;
	}

	public static void evacuate(World world, String message) {
		for (Player player : world.getPlayers()) {
			Location loc = getEvacuation(player);
			if (loc != null) {
				player.teleport(loc);
				player.sendMessage(ChatColor.RED + message);
			} else {
				player.kickPlayer(message);
			}
		}
	}
	public static void teleportToClonedWorld(World from, World cloned) {
		for (Player p : from.getPlayers().toArray(new Player[0])) {
			Location loc = p.getLocation();
			loc.setWorld(cloned);
			p.teleport(loc);
		}
	}
	public static void teleportToWorldSpawn(World from, World to) {
		for (Player p : from.getPlayers().toArray(new Player[0])) {
			p.teleport(to.getSpawnLocation());
		}
	}
	
	/**
	 * Repairs the chunk region file
	 * Returns -1 if the file had to be removed
	 * Returns -2 if we had no access
	 * Returns -3 if file removal failed (from -1)
	 * Returns the amount of changed chunks otherwise
	 * @param chunkfile
	 * @param backupfolder
	 * @return
	 */
	public static int repairRegion(File chunkfile, File backupfolder) {
		MyWorlds.plugin.log(Level.INFO, "Performing repairs on region file: " + chunkfile.getName());
		RandomAccessFile raf = null;
		try {
			String name = chunkfile.getName().substring(2);
			String xname = name.substring(0, name.indexOf('.'));
			name = name.substring(xname.length() + 1);
			String zname = name.substring(0, name.indexOf('.'));
			
			int xOffset = 32 * Integer.parseInt(xname);
			int zOffset = 32 * Integer.parseInt(zname);
			
			raf = new RandomAccessFile(chunkfile, "rw");
			File backupfile = new File(backupfolder + File.separator + chunkfile.getName());
			int[] locations = new int[1024];
			for (int i = 0; i < 1024; i++) {
				locations[i] = raf.readInt();
			}
			//Validate the data
			int editcount = 0;
			int chunkX = 0;
			int chunkZ = 0;
			
			//x = 5
			//z = 14
			//i = 5 + 14 * 32 = 453
			//x = i % 32 = 5
			//z = [(453 - 5)]  448 / 32 = 
			
			byte[] data = new byte[8096];
			for (int i = 0; i < locations.length; i++) {
				chunkX = i % 32;
				chunkZ = (i - chunkX) >> 5;
				chunkX += xOffset;
				chunkZ += zOffset;
				int location = locations[i];
				if (location == 0) continue;
				try {
					int offset = location >> 8;
                    int size = location & 255;
                    long seekindex = (long) (offset * 4096);
					raf.seek(seekindex);
					int length = raf.readInt();
					//Read and test the data
					if (length > 4096 * size) {
						editcount++;
						locations[i] = 0;
						MyWorlds.plugin.log(Level.WARNING, "Invalid length: " + length + " > 4096 * " + size);
						//Invalid length
					} else if (size > 0 && length > 0) {
						byte version = raf.readByte();
						if (data.length < length + 10) {
							data = new byte[length - 1];
						}
						raf.read(data);
						ByteArrayInputStream bais = new ByteArrayInputStream(data);
						//Try to load it all...
						DataInputStream stream;
						if (version == 1) {
							stream = new DataInputStream(new BufferedInputStream(new GZIPInputStream(bais)));
						} else if (version == 2) {
							stream = new DataInputStream(new BufferedInputStream(new InflaterInputStream(bais)));
						} else {
							stream = null;
							//Unknown version
							MyWorlds.plugin.log(Level.WARNING, "Unknown region version: " + version + " (we probably need an update here!)");
						}
						if (stream != null) {
							
							//Validate the stream and close
							try {
								CommonTagCompound comp = CommonTagCompound.readFrom(stream);
								if (comp == null) {
									editcount++;
									locations[i] = 0;
									MyWorlds.plugin.log(Level.WARNING, "Invalid tag compound at chunk " + chunkX + "/" + chunkZ);
								} else {
									//correct location?
									if (comp.containsKey("Level")) {
										CommonTagCompound level = comp.createCompound("Level");
										int xPos = level.getValue("xPos", Integer.MIN_VALUE);
										int zPos = level.getValue("zPos", Integer.MIN_VALUE);
										//valid coordinates?
										if (xPos != chunkX || zPos != chunkZ) {
											MyWorlds.plugin.log(Level.WARNING, "Chunk [" + xPos + "/" + zPos + "] was stored at [" + chunkX + "/" + chunkZ + "], moving...");
											level.putValue("xPos", chunkX);
											level.putValue("zPos", chunkZ);
											//rewrite to stream
											ByteArrayOutputStream baos = new ByteArrayOutputStream(8096);
									        DataOutputStream dataoutputstream = new DataOutputStream(new DeflaterOutputStream(baos));
									        NBTUtil.writeCompound(level, dataoutputstream);
									        dataoutputstream.close();
									        //write to region file
									        raf.seek(seekindex);
									        byte[] newdata = baos.toByteArray();
									        raf.writeInt(newdata.length + 1);
									        raf.writeByte(2);
									        raf.write(newdata, 0, newdata.length);
									        editcount++;
										}
									} else {
										editcount++;
										locations[i] = 0;
										MyWorlds.plugin.log(Level.WARNING, "Invalid tag compound at chunk " + chunkX + "/" + chunkZ);
									}
								}
							} catch (ZipException ex) {
								//Invalid.
								editcount++;
								locations[i] = 0;
								MyWorlds.plugin.log(Level.WARNING, "Chunk at position " + chunkX + "/" + chunkZ + " is not in a valid ZIP format (it's corrupted, and thus lost)");
							} catch (Exception ex) {
								//Invalid.
								editcount++;
								locations[i] = 0;
								MyWorlds.plugin.log(Level.WARNING, "Failed to properly read chunk at position " + chunkX + "/" + chunkZ + ":");
								ex.printStackTrace();
							}
							stream.close();
						}
					}
				} catch (Exception ex) {
					editcount++;
					locations[i] = 0;
					ex.printStackTrace();
				}
			}
			if (editcount > 0) {
				if (backupfolder.mkdirs() && copy(chunkfile, backupfile)) {
					//Write out the new locations
					raf.seek(0);
					for (int location : locations) {
						raf.writeInt(location);
					}
				} else {
					MyWorlds.plugin.log(Level.WARNING, "Failed to make a copy of the file, no changes are made.");
					return -2;
				}
			}
			//Done.
			raf.close();
			return editcount;
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try {
			if (raf != null) raf.close();
		} catch (Exception ex) {}
		
		try {
			chunkfile.delete();
			return -1;
		} catch (Exception ex) {
			return -3;
		}
	}
}