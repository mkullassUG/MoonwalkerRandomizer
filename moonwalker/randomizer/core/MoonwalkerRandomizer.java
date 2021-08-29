/*
    Copyright (C) 2020 Micha³ Kullass

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package moonwalker.randomizer.core;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import moonwalker.core.structures.MDirectObject;
import moonwalker.core.structures.MoonwalkerObject;
import moonwalker.core.structures.MDirectObject.Container;
import moonwalker.core.utils.MoonwalkerIO;
import moonwalker.core.utils.MoonwalkerMetadata;
import moonwalker.core.utils.OutOfSpaceException;
import moonwalker.core.utils.IntRange;
import moonwalker.core.utils.IntRangeSet;

public class MoonwalkerRandomizer
{
	private HashMap<Short, Binding[]> bindingMap;
	private ArrayList<String> mStageNames;
	private HashMap<String, Integer> mStageIndices;
	private HashMap<String, HashMap<String, MoonwalkerArea>> mSpawnMaps;
	private HashMap<String, HashMap<Short, MapRefResolver>> mSpawnMapRefs;
	private HashMap<String, ArrayList<String>> mExecProcs;
	private HashMap<String, HashMap<String, HashMap<String, HashMap<String, ArrayList<String>>>>> mProcArgs;
	private ArrayList<HitboxRef> mHitboxes;
	private HashMap<String, ArrayList<HitboxRef>> mCollisionChecks;
	
	private int retryLimit;
	private int mergeThreshold;
	
	public MoonwalkerRandomizer() throws ParserConfigurationException, SAXException, IOException
	{
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		Document doc = builder.parse(getClass().getResourceAsStream("/moonwalker/randomizer/data/Randomizer.xml"));
		
		Element root = doc.getDocumentElement();
		
		StageDataParser sdp = new StageDataParser(root);
		
		bindingMap = sdp.getBindingMap();
		mStageNames = sdp.getStageNameList();
		mStageIndices = sdp.getStageIndexList();
		mSpawnMaps = sdp.getSpawnMapList();
		mSpawnMapRefs = sdp.getSpawnMapRefList();
		mExecProcs = sdp.getExecutedProcedureList();
		mProcArgs = sdp.getProcedureArguments();
		mHitboxes = sdp.getHitboxes();
		mCollisionChecks = sdp.getCollisionChecks();
		
		RandomPointGenerator rpGen = (area, r) ->
		{			
			Rectangle rect = area.getBounds();
			
			while ((rect.width > 1) || (rect.height > 1))
			{
				int wHalf = rect.width / 2;
				int hHalf = rect.height / 2;
				Rectangle nw = new Rectangle(rect.x, rect.y, wHalf, hHalf);
				Rectangle ne = new Rectangle(rect.x + wHalf, rect.y,
						rect.width - wHalf, hHalf);
				Rectangle sw = new Rectangle(rect.x, rect.y + hHalf,
						wHalf, rect.height - hHalf);
				Rectangle se = new Rectangle(rect.x + wHalf, rect.y + hHalf,
						rect.width - wHalf, rect.height - hHalf);
				
				List<Rectangle> rects = Stream.of(nw, ne, sw, se)
						.filter(rec -> area.intersects(rec))
						.collect(Collectors.toList());
				
				rect = rects.get(r.nextInt(rects.size()));
			}
			
			List<Point> ret = getPoints(rect)
					.stream()
					.filter(p -> area.contains(p))
					.collect(Collectors.toList());
			
			return ret.get(r.nextInt(ret.size()));
		};
		
		for (HashMap<String, MoonwalkerArea> map: mSpawnMaps.values())
			for (MoonwalkerArea area: map.values())
				area.setRandomPointGenerator(rpGen);
		
		retryLimit = 100;
		mergeThreshold = 16;
	}
	public void randomize(byte[] rom, Map<String, Boolean> settings,
			MoonwalkerMetadata meta, long seed, File customMusicDir) throws OutOfSpaceException
	{
		if (rom.length != meta.getRomLength())
			throw new IllegalArgumentException("File does not match ROM specifications.");
		
		Random seedGen = new Random(seed);
		
		//debug switch
		boolean log = false;
		
		if (settings.getOrDefault("randomizePositions", true))
		{
			MDirectObject[][] objectArr = MoonwalkerIO.loadMainObjectArray(rom, meta);
			
			mergeMisalignments(objectArr);
			
			for (String stageName: mStageNames)
			{
				Random mainRand = new Random(Hashes.murmur64(seedGen.nextLong()));
				
				Random posRand = new Random(Hashes.murmur64(mainRand.nextLong()));
				Random procRand = new Random(Hashes.murmur64(mainRand.nextLong()));
				
				int stageIndex = mStageIndices.get(stageName);
				
				if (log)
					System.out.println("Randomizing stage " + stageName + " (index " + stageIndex + ")");
				
				HashMap<Short, MapRefResolver> mapperMap = mSpawnMapRefs.get(stageName);
				
				Point camPos = MoonwalkerIO.getInitialCameraPosition(rom, stageIndex, meta);
				Dimension camSize = meta.getCameraSize();
				Rectangle initialCamera = new Rectangle(camPos.x, camPos.y,
						camSize.width, camSize.height);
				
				if (mapperMap != null)
				{
					ArrayList<MDirectObject> finishedObjsList = new ArrayList<>();
					ArrayList<Map.Entry<MDirectObject, MapRef>> queuedObjsList = new ArrayList<>();
					
					for (MDirectObject obj: objectArr[stageIndex])
					{
						short type = obj.getType();
						byte[] data = obj.getData();
						
						MapRefResolver mapper = mapperMap.get(type);
						if (mapper == null)
						{
							finishedObjsList.add(obj);
							continue;
						}
						MapRef map = mapper.getMapRef(data);
						if ((map == null) || (!settings.getOrDefault(
							"randomizePositions."
							+ stageName
							+ ".type:0x"
								+ Integer.toHexString(0xFFFF & obj.getType())
									.toUpperCase(), true)))
						{
							finishedObjsList.add(obj);
							continue;
						}
						
						//TODO turn into a proper tuple
						queuedObjsList.add(new AbstractMap.SimpleEntry<MDirectObject, MapRef>(obj, map));
					}
					
					for (Map.Entry<MDirectObject, MapRef> queuedEntry: queuedObjsList)
					{
						MDirectObject obj = queuedEntry.getKey();
						MapRef map = queuedEntry.getValue();
						
						randomizePosition(obj, finishedObjsList,
								stageName, map, initialCamera, posRand, log);
						finishedObjsList.add(obj);
					}
				}
				
				ArrayList<String> procList = mExecProcs.get(stageName);
				if (procList != null)
				{
					for (String procName: procList)
					{
						if(!settings.getOrDefault(
								"executeProcedures."
								+ stageName
								+ ".proc:"
								+ procName, true))
							continue;
						
						switch (procName)
						{
							case "randomizeCaveData":
								randomizeCaveData(objectArr[stageIndex], stageName, stageIndex,
										initialCamera,
										new Random(Hashes.murmur64(procRand.nextLong())));
								break;
							case "fixStage1Doors":
								fixStage1Doors(objectArr[stageIndex], stageName, stageIndex,
										new Random(Hashes.murmur64(procRand.nextLong())));
								break;
//							case "fixSpiders":
//								fixSpiders(objectArr[stageIndex], stageName, stageIndex,
//										new Random(Hashes.murmur64(procRand.nextLong())));
//								break;
							case "randomizeTeleporters":
								randomizeTeleporters(objectArr[stageIndex], stageName, stageIndex,
										new Random(Hashes.murmur64(procRand.nextLong())));
								break;
							default:
								System.err.println("Unrecognised procedure in stage " + stageName
										+ ": " + procName + ". Skipping.");
								break;
						}
					}
				}
				
				for (MDirectObject obj: objectArr[stageIndex])
					applyGlobalAttributes(obj, objectArr[stageIndex]);
			}
			
			MoonwalkerIO.saveMainObjectArray(rom, objectArr, meta);
		}
		else
			mStageNames.forEach(e -> seedGen.nextLong());
		
		long currGlobalSeed;
		
		currGlobalSeed = seedGen.nextLong();
		boolean randStageOrder = settings.getOrDefault("levelOrder.randomizeStageOrder", true);
		boolean randRoundOrder = settings.getOrDefault("levelOrder.randomizeRoundOrder", true);
		L0: if (randStageOrder || randRoundOrder)
		{
			Random r = new Random(Hashes.murmur64(currGlobalSeed));
			
			//TODO implement swapping of initial level
			boolean keep_1_1_first = settings.getOrDefault("levelOrder.keep_1-1_first", true);
			boolean keep_5_3_last = settings.getOrDefault("levelOrder.keep_5-3_last", true);
			
			final int LEVEL_SWAP_ENTRY_POINT = 0x511C;
			final int INITIAL_LEVEL_SWAP_ENTRY_POINT = 0x6432;
			
			ArrayList<Integer> rounds = generateLevelOrder(
					randStageOrder, randRoundOrder,
					keep_1_1_first, keep_5_3_last, r);
			
			int firstRound = rounds.get(0);
			
			int[] nextLevelArray = new int[rounds.size()];
			int l = rounds.size();
			for (int i = 1, currLevel = firstRound; i < l; i++)
			{
				int level = rounds.get(i);
				nextLevelArray[currLevel] = level;
				currLevel = level;
			}
			
			int arrLen = 0x86;
			long[] lArr =
			{
				0x0l, 0x0l, 0x0l, 0x0l, 
				0x54de784252de7842l, 
				0x5ede784256de7842l, 
				0x1000400c42de3830l, 
				0xfb3148e3754e0265l, 
				0x380c754e42dec000l, 
				0xa0000679adf3c00l, 
				0x754e00de1400fc11l, 
				0xfc31feffe748l, 
				0x30510000b94e42del, 
				0xf83140dd40def831l, 
				0x40def83142dd42del, 
				0x42dc42def83140dcl, 
				0x754eff7fdf4cl
			};
			
			byte[] arr = Arrays.copyOf(BitSet.valueOf(lArr).toByteArray(), arrLen);
			
			for (int i = 0; i < nextLevelArray.length; i++)
			{
				arr[i * 2] = (byte) ((0xFF00 & nextLevelArray[i]) >> 8);
				arr[(i * 2) + 1] = (byte) (0xFF & nextLevelArray[i]);
			}
			
			arr[0x5E] = (byte) ((0xFF00 & firstRound) >> 8);
			arr[0x5F] = (byte) (0xFF & firstRound);
			
			IntRangeSet rs = meta.getFreeROMSpace(MoonwalkerRandomizer.class, "levelSwapAssembly");
			IntRange range = rs.findContinuousRange(arr.length);
			
			if (range == null)
				break L0;
			
			int rangeStart = range.getStart();
			
			ByteBuffer buf = ByteBuffer.wrap(rom);
			buf.position(rangeStart);
			buf.put(arr);
			
			meta.assignROMSpace(MoonwalkerRandomizer.class,
					"levelSwapAssembly", new IntRangeSet(range));
			
			arrLen = 0x14;
			lArr = new long[]
			{
					0xf94e00000000b94el, 0x30510000l
			};
			arr = Arrays.copyOf(BitSet.valueOf(lArr).toByteArray(), arrLen);
			int codeStart = rangeStart + 0x20;
			arr[2] = (byte) ((0xFF000000 & codeStart) >> 24);
			arr[3] = (byte) ((0xFF0000 & codeStart) >> 16);
			arr[4] = (byte) ((0xFF00 & codeStart) >> 8);
			arr[5] = (byte) (0xFF & codeStart);
			
			buf.position(LEVEL_SWAP_ENTRY_POINT);
			buf.put(arr);
			
			if (firstRound != 0)
			{
				arrLen = 0x6;
				lArr = new long[]
				{
						0xb94el
				};
				arr = Arrays.copyOf(BitSet.valueOf(lArr).toByteArray(), arrLen);
				
				int funcStart = rangeStart + 0x46;
				
				arr[2] = (byte) ((0xFF000000 & funcStart) >> 24);
				arr[3] = (byte) ((0xFF0000 & funcStart) >> 16);
				arr[4] = (byte) ((0xFF00 & funcStart) >> 8);
				arr[5] = (byte) (0xFF & funcStart);
				
				buf.position(INITIAL_LEVEL_SWAP_ENTRY_POINT);
				buf.put(arr);
			}
		}
		
		currGlobalSeed = seedGen.nextLong();
		//TODO implement boss randomization
//		if (settings.getOrDefault("randomizeBosses", Boolean.FALSE))
//		{
//			if (log)
//				System.out.println("Randomizing bosses");
//		
//			Random r = new Random(Hashes.hashLong(currSeed));
//		
//			int bossTableOffset = 0x5F4AE;
//			int bossTableLen = 15;
//			int bossTableSegmentLen = 3;
//			
//			ByteBuffer buf = ByteBuffer.wrap(rom);
//			for (int i = 0; i < bossTableLen; i += bossTableSegmentLen)
//			{
//				int[] addrArr = new int[bossTableSegmentLen];
//				for (int i0 = 0; i0 < addrArr.length; i0++)
//					addrArr[i0] = buf.getInt(bossTableOffset + (4 * i) + (4 * i0));
//				
//				shuffleArray(addrArr, r);
//				
//				for (int i0 = 0; i0 < addrArr.length; i0++)
//					buf.putInt(bossTableOffset + (4 * i) + (4 * i0), addrArr[i0]);
//			}
//		}
		
		currGlobalSeed = seedGen.nextLong();
		if (settings.getOrDefault("randomizeMusic", Boolean.FALSE))
		{
			if (log)
				System.out.println("Randomizing music");
			
			Random r = new Random(Hashes.murmur64(currGlobalSeed));
			
			int musicTableOffset = 0x600A4;
			int musicTableLen = 5;
			
			boolean shuffleStandard = settings.getOrDefault("randomizeMusic.shuffleStandard", Boolean.TRUE);
			boolean insertCustom = settings.getOrDefault("randomizeMusic.insertCustom", Boolean.TRUE);
			
			boolean simpleShuffle = shuffleStandard && !insertCustom;
			
			if (insertCustom)
			{
				if ((customMusicDir != null)
						&& customMusicDir.exists()
						&& customMusicDir.isDirectory())
				{
					File[] fMusic = customMusicDir.listFiles((FileFilter) f -> 
						!f.isDirectory()
							&& (f.length() < 0x400000)
							&& f.getName().toLowerCase().endsWith(".smps"));
					
					ArrayList<byte[]> musicList = new ArrayList<>();
					for (File f: fMusic)
					{
						try
						{
							musicList.add(Files.readAllBytes(f.toPath()));
						}
						catch (Exception e)
						{}
					}
					
					int musLen = musicList.size();
					if (musLen > 0)
					{
						LinkedList<Integer> availableMusicIndices = r.ints(0, musicTableLen + musLen)
								.distinct()
								.limit(musicTableLen + musLen)
								.collect(LinkedList::new, LinkedList::add, LinkedList::addAll);
						ArrayList<Integer> finalMusicIndices = new ArrayList<Integer>();
						HashMap<Integer, IntRange> musicMap = new HashMap<>();
						
						IntRangeSet rs = meta.getFreeROMSpace(MoonwalkerRandomizer.class, "customMusic");
						for (int i = 0; i < musicTableLen; i++)
						{
							int n = availableMusicIndices.pollFirst();
							if (n >= musicTableLen)
							{
								IntRange range;
								do
								{
									range = rs.findContinuousRange(musicList.get(n - musicTableLen).length);
									if (range == null)
										n = availableMusicIndices.pollFirst();
									else
										rs = rs.difference(range);
								}
								while ((n >= musicTableLen) && (range == null));
								if (range != null)
									musicMap.put(n, range);
							}
							
							if (n < musicTableLen)
								availableMusicIndices.add(n);
							finalMusicIndices.add(n);
						}
						
						ByteBuffer buf = ByteBuffer.wrap(rom);
						int[] addrArr = new int[musicTableLen];
						for (int i = 0; i < musicTableLen; i++)
							addrArr[i] = buf.getInt(musicTableOffset + (i * 4));
						
						int nameTableOffset = 0x6936;
						int nameLength = 0x13;
						
						byte[][] defaultNames = new byte[musicTableLen][nameLength];
						for (int i = 0; i < musicTableLen; i++)
						{
							buf.mark();
							try
							{
								buf.position(nameTableOffset + (i * nameLength));
								buf.get(defaultNames[i]);
							}
							catch (Exception e)
							{
								for (int i0 = 0; i0 < (nameLength - 1); i0++)
									defaultNames[i][i0] = (byte) ' ';
							}
							finally
							{
								buf.reset();
							}
						}
						
						int[] destAddrArr = new int[addrArr.length];
						byte[][] destNames = new byte[musicTableLen][];
						IntRangeSet usedSpace = new IntRangeSet();
						
						for (int i = 0; i < musicTableLen; i++)
						{
							int n = finalMusicIndices.get(i);
							if (n < musicTableLen)
							{
								if (shuffleStandard)
								{
									destAddrArr[i] = addrArr[n];
									destNames[i] = defaultNames[n];
								}
								else
								{
									destAddrArr[i] = addrArr[i];
									destNames[i] = defaultNames[i];
								}
							}
							else
							{
								IntRange range = musicMap.get(n);
								usedSpace = usedSpace.union(range);
								int addr = range.getStart();

								byte[] name = parseMusicName(
										fMusic[n - musicTableLen].getName(),
										nameLength);
								
								buf.mark();
								try
								{
									buf.position(addr);
									buf.put(musicList.get(n - musicTableLen));
									destAddrArr[i] = range.getStart();
									destNames[i] = name;
								}
								catch (Exception e)
								{
									destAddrArr[i] = addrArr[i];
									destNames[i] = new byte[nameLength];
								}
								finally
								{
									buf.reset();
								}
							}
						}
						
						for (int i = 0; i < musicTableLen; i++)
							buf.putInt(musicTableOffset + (i * 4), destAddrArr[i]);
						
						for (int i = 0; i < musicTableLen; i++)
						{
							buf.mark();
							try
							{
								buf.position(nameTableOffset + (i * nameLength));
								buf.put(destNames[i]);
							}
							catch (Exception e)
							{}
							finally
							{
								buf.reset();
							}
						}
						
						meta.assignROMSpace(MoonwalkerRandomizer.class, "customMusic", usedSpace);
					}
					else
						simpleShuffle = shuffleStandard;
				}
				else
					simpleShuffle = shuffleStandard;
			}
			
			if (simpleShuffle)
			{
				//Shuffle music
				
				ByteBuffer buf = ByteBuffer.wrap(rom);
				int[] addrArr = new int[musicTableLen];
				for (int i = 0; i < musicTableLen; i++)
					addrArr[i] = buf.getInt(musicTableOffset + (i * 4));
				
				int[] randIndices = r.ints(0, musicTableLen)
						.distinct()
						.limit(musicTableLen)
						.toArray();
				
				for (int i = 0; i < musicTableLen; i++)
					buf.putInt(musicTableOffset + (i * 4), addrArr[randIndices[i]]);
				
				//Change names in Options menu
				
				int nameTableOffset = 0x6936;
				int nameLength = 0x13;
				
				byte[][] defaultNames = new byte[musicTableLen][nameLength];
				for (int i = 0; i < musicTableLen; i++)
				{
					buf.mark();
					try
					{
						buf.position(nameTableOffset + (i * nameLength));
						buf.get(defaultNames[i]);
					}
					catch (Exception e)
					{
						for (int i0 = 0; i0 < (nameLength - 1); i0++)
							defaultNames[i][i0] = (byte) ' ';
					}
					finally
					{
						buf.reset();
					}
				}
				
				byte[][] destNames = new byte[musicTableLen][];
				
				for (int i = 0; i < musicTableLen; i++)
					destNames[i] = defaultNames[randIndices[i]];
				
				for (int i = 0; i < musicTableLen; i++)
				{
					buf.mark();
					try
					{
						buf.position(nameTableOffset + (i * nameLength));
						buf.put(destNames[i]);
					}
					catch (Exception e)
					{}
					finally
					{
						buf.reset();
					}
				}
			}
		}
		
		if (settings.getOrDefault("replaceTitleText", Boolean.TRUE))
		{
			//Replace "Press start button" with "Randomized"
			final int arrLen = 0x1CE;
//			final long[] arr = {0x0L, 0x8605850584058305L, 0x8a05890588058705L, 0x8b05L, 0x0L, 0x8e058d058c050000L,
//					0x9205910590058f05L, 0x94059305L, 0x380128000000000L, 0x3509240714017300L, 0x813d463c663b5618L,
//					0x1b75172514150203L, 0xfb58f968f8483836L, 0x80483fa18060482L, 0x1a45197516151535L, 0x7df9c8ff3a563926L,
//					0x743225c53a754663L, 0xb8f674a758cf7adbL, 0x35f072a0b6e1a6abL, 0xdc643a599f681730L, 0xea2d047b1a14393dL,
//					0xe5da46eda68576f1L, 0x5f1e798b29f0eae1L, 0xec212af03664b2bbL, 0xed9ab032e2317539L, 0x4367f34263fc03b5L,
//					0xe06fbb6941914397L, 0x3e6eba545c333a75L, 0x42295420840a04beL, 0x6440d8596817afdeL, 0xb224c9f42e10e319L,
//					0x49be77811861326bL, 0x3f8a408c32993525L, 0x232872c9fe9b2419L, 0xf0aa49ff2649a687L, 0x81879b0c932423ffL,
//					0xad03f74d4992f351L, 0x24794387ed5b92e4L, 0xd181c04708ba3e9eL, 0x24L};
			final long[] arr = {0x8305000000000000L, 0x8705860585058405L, 0x8b058a0589058805L, 
					0x8c05L, 0x0L, 0x90058f058e058d05L, 0x9405930592059105L,
					0x96059505L, 0x480148000000000L, 0x2504141955064602L,
					0x8104731735156618L, 0x5258161506047147L, 0x58205360b750226L,
					0xd0483532870171eL, 0x392609570a142847L, 0xbfff00751d350867L,
					0x9d5d75c5ae340b46L, 0x6e552e099cdbf226L, 0xcb9b37d26b95bae1L,
					0x61a5afe5849226b5L, 0xbdc8bc799b35ab98L, 0xadb2dc624627954eL,
					0xbb2cf282376f2d96L, 0xc25bb2b889b41881L, 0x35fa6d6adcb765f9L,
					0x41d9f246e60dd366L, 0x2d96ad12b0a9a874L, 0x21dd9617bc55316cL,
					0x6125938ef3aba438L, 0xcb392124b7cbc7afL, 0x19dc8a6533d40c15L,
					0xaa61d2b7c5728b4dL, 0xc89dbd79855bd64cL, 0xc6edcd9b5792ec57L,
					0xe6cd37490289df96L, 0x3c49028adf9626f5L, 0x912458d2a5c517deL,
					0x27492085bb585f4cL, 0x4082f46df1e6d59bL, 0x44b05657d9c2481L,
					0x4812ac5cbae00b92L, 0xa2749b0bae48517L, 0x649254a3c56e8b19L};
			
			ByteBuffer buf = ByteBuffer.wrap(rom);
			buf.position(0x34846);
			buf.put(Arrays.copyOf(BitSet.valueOf(arr).toByteArray(), arrLen));
		}
		
		MoonwalkerIO.fixChecksum(rom);
	}
	
	private static ArrayList<Integer> generateLevelOrder(boolean randStageOrder, boolean randRoundOrder, boolean keep_1_1_first, boolean keep_5_3_last, Random r)
	{
		final int LEVEL_COUNT = 15;
		final int ROUNDS_IN_STAGE = 3;
		
		ArrayList<Integer> rounds = null;
		if (randStageOrder && !randRoundOrder)
		{
			int STAGE_COUNT = LEVEL_COUNT / ROUNDS_IN_STAGE;
			ArrayList<Integer> stages = new ArrayList<>(
					IntStream.range(0, STAGE_COUNT)
					.mapToObj(i -> i)
					.collect(Collectors.toList()));
			Collections.shuffle(stages, r);
			
			if (keep_1_1_first)
			{
				stages.remove(Integer.valueOf(0));
				stages.add(0, Integer.valueOf(0));
			}
			if (keep_5_3_last)
			{
				int lastStage = STAGE_COUNT - 1;
				stages.remove(Integer.valueOf(lastStage));
				stages.add(lastStage);
			}
			
			rounds = new ArrayList<>(stages.stream()
					.flatMap(stage -> IntStream.range(
								stage * ROUNDS_IN_STAGE,
								(stage + 1) * ROUNDS_IN_STAGE)
							.mapToObj(i -> i))
					.filter(round -> round < LEVEL_COUNT)
					.collect(Collectors.toList()));
		}
		else
		{
			rounds = new ArrayList<>(IntStream.range(0, LEVEL_COUNT)
					.mapToObj(i -> i)
					.collect(Collectors.toList()));
			Collections.shuffle(rounds, r);
			
			if (!randStageOrder)
			{
				rounds = new ArrayList<>(rounds.stream()
						.sorted((v1, v2) -> Integer.compare(
								v1 / ROUNDS_IN_STAGE,
								v2 / ROUNDS_IN_STAGE))
						.collect(Collectors.toList()));
			}
			
			if (keep_1_1_first)
			{
				rounds.remove(Integer.valueOf(0));
				rounds.add(0, Integer.valueOf(0));
			}
			if (keep_5_3_last)
			{
				int lastStage = (LEVEL_COUNT / ROUNDS_IN_STAGE) - 1;
				rounds.remove(Integer.valueOf(lastStage));
				rounds.add(lastStage);
			}
		}
		
		rounds.add(15);
		
		return rounds;
	}
	private byte[] parseMusicName(String name, int nameLength)
	{
		if (name.contains("."))
			name = name.substring(0, name.lastIndexOf("."));
		
		//TODO move to constant
		HashMap<Character, Integer> map = new HashMap<>(
				Map.of(
						' ', 0x20,
						'\'', 0x3A,
						'=', 0x3B,
						'.', 0x3C,
						'!', 0x3D,
						'-', 0x3E,
						'?', 0x3F,
						'x', 0x40,
						',', 0x5B));
		map.putAll(IntStream.rangeClosed('0', '9')
				.mapToObj(c -> c)
				.collect(Collectors.toMap(
						c -> (char) (int) c,
						c -> c)));
		map.putAll(IntStream.rangeClosed('A', 'Z')
				.mapToObj(c -> c)
				.collect(Collectors.toMap(
						c -> (char) (int) c,
						c -> c)));
		
		int[] nameArr = name.codePoints()
				.map(c -> Character.toUpperCase(c))
				.filter(c -> map.containsKey((char) c))
				.map(c -> map.get((char) c))
				.toArray();
		
		byte[] ret = new byte[nameLength];
		int l = ret.length - 1;
		for (int i = 0; i < l; i++)
		{
			if (i < nameArr.length)
				ret[i] = (byte) nameArr[i];
			else
				ret[i] = (byte) ' ';
		}
		ret[l] = 0;
		return ret;
	}
	private void randomizeCaveData(MDirectObject[] objArr, String stageName, int stageIndex, Rectangle initialCamera, Random r)
	{
		if ((stageIndex < 9) || (stageIndex > 0xB))
			throw new IllegalArgumentException("RandomizeCaveData used on incorrect stage: index 0x"
					+ Integer.toHexString(stageIndex));
		
		int caveWithKCount = new int[]{7, 9, 10}[stageIndex - 9];
		
		List<Short> objTableWithoutMarkerList = List.<Short>of((short) 0x14, (short) 0x20);
		short[] objTableIndArr_withK = new short[]{0x0, 0x4, 0x8, 0x10, 0x18, 0x1C, 0x20, 0x24, 0x28,
				List.<Short>of((short) 0xC, (short) 0x14).get(
						r.nextInt(objTableWithoutMarkerList.size()))};
		short[] objTableIndArr_withoutK = {0x2C, 0x30, 0x34, 0x38};
		short markerType = 0x49;
		
		HashMap<Short, Short> caveMap = new HashMap<>();
		caveMap.put((short) 0x0, (short) 0x10);
		caveMap.put((short) 0x4, (short) 0x10);
		caveMap.put((short) 0x18, (short) 0x10);
		caveMap.put((short) 0x1C, (short) 0x10);
		caveMap.put((short) 0x20, (short) 0x10);
		caveMap.put((short) 0x30, (short) 0x10);
		
		caveMap.put((short) 0xC, (short) 0x11);
		caveMap.put((short) 0x10, (short) 0x11);
		caveMap.put((short) 0x14, (short) 0x11);
		caveMap.put((short) 0x2C, (short) 0x11);
		caveMap.put((short) 0x38, (short) 0x11);
		
		caveMap.put((short) 0x8, (short) 0x12);
		caveMap.put((short) 0x24, (short) 0x12);
		caveMap.put((short) 0x28, (short) 0x12);
		caveMap.put((short) 0x34, (short) 0x12);
		
		//TODO Remove hardcoded values by actually scanning cave object table
		HashMap<Short, Short> kidIndexMap = new HashMap<>();
		kidIndexMap.put((short) 0x0, (short) 0x1);
		kidIndexMap.put((short) 0x4, (short) 0x2);
		kidIndexMap.put((short) 0x8, (short) 0x4);
		kidIndexMap.put((short) 0xC, (short) 0x8);
		kidIndexMap.put((short) 0x10, (short) 0x10);
		kidIndexMap.put((short) 0x14, (short) 0x8);
		kidIndexMap.put((short) 0x18, (short) 0x20);
		kidIndexMap.put((short) 0x1C, (short) 0x40);
		kidIndexMap.put((short) 0x20, (short) 0x80);
		kidIndexMap.put((short) 0x24, (short) 0x100);
		kidIndexMap.put((short) 0x28, (short) 0x200);
		
		int camX1 = initialCamera.x;
		int camY1 = initialCamera.y;
		int camX2 = initialCamera.x + initialCamera.width;
		int camY2 = initialCamera.y + initialCamera.height;
		
		ArrayList<Short> typeList = new ArrayList<>(
				mProcArgs.get(stageName)
				.get("randomizeCaveData")
				.get("object")
				.get("type")
				.stream()
				.map(s -> Short.parseShort(s, 16))
				.collect(Collectors.toList()));
		
		ArrayList<MDirectObject> caveObjList = new ArrayList<>();
		LinkedList<MDirectObject> kidMarkerList = new LinkedList<>();
		
		for (MDirectObject obj: objArr)
		{
			short type = obj.getType();
			if (typeList.contains(type))
				caveObjList.add(obj);
			else if (type == markerType)
				kidMarkerList.add(obj);
		}
		
		Collections.shuffle(caveObjList, r);
		shuffleArray(objTableIndArr_withK, r);
		
		int i = 0;
		for (MDirectObject obj: caveObjList)
		{
			short objTableIndex;
			if (i < caveWithKCount)
			{
				objTableIndex = objTableIndArr_withK[i];
				i++;
			}
			else
				objTableIndex = objTableIndArr_withoutK[r.nextInt(objTableIndArr_withoutK.length)];
			short caveIndex = caveMap.get(objTableIndex);
			byte[] data = obj.getData();
			
			data[0] = (byte) ((0xFF00 & caveIndex) >> 8);
			data[1] = (byte) (0xFF & caveIndex);
			data[2] = (byte) ((0xFF00 & objTableIndex) >> 8);
			data[3] = (byte) (0xFF & objTableIndex);
			
			obj.setData(data);
		}
		
		ArrayList<MDirectObject> delayedMarkingList = new ArrayList<>();
		
		for (MDirectObject obj: caveObjList)
		{
			if (kidMarkerList.isEmpty())
				break;
			
			byte[] data = obj.getData();
			short objTableIndex = (short) (((0xFF & data[2]) << 8) | (0xFF & data[3]));
			
			if (!contains(objTableIndArr_withK, objTableIndex))
				continue;
			
			if (objTableWithoutMarkerList.contains(objTableIndex))
			{
				delayedMarkingList.add(obj);
				continue;
			}
			
			MDirectObject marker = kidMarkerList.pop();
			Point p = obj.getAbsolutePosition();
			p.x += 28;
			p.y += 56;
			marker.setAbsolutePosition(p);
			byte[] markerData = marker.getData();
			short kidIndex = kidIndexMap.getOrDefault(objTableIndex, (short) 0);
			markerData[4] = (byte) ((0xFF00 & kidIndex) >> 8);
			markerData[5] = (byte) (0xFF & kidIndex);
			marker.setData(markerData);
		}
		for (MDirectObject obj: delayedMarkingList)
		{
			if (kidMarkerList.isEmpty())
				break;
			
			MDirectObject marker = kidMarkerList.pop();
			Point p = obj.getAbsolutePosition();
			p.x += 28;
			p.y += 56;
			marker.setAbsolutePosition(p);
			byte[] data = obj.getData();
			byte[] markerData = marker.getData();
			short objTableIndex = (short) (((0xFF & data[2]) << 8) | (0xFF & data[3]));
			short kidIndex = kidIndexMap.getOrDefault(objTableIndex, (short) 0);
			markerData[4] = (byte) ((0xFF00 & kidIndex) >> 8);
			markerData[5] = (byte) (0xFF & kidIndex);
			marker.setData(markerData);
		}
		
		for (MDirectObject marker: objArr)
		{
			if (marker.getType() != markerType)
				continue;
			
			Point p = marker.getAbsolutePosition();
			if ((p.x >= camX1) && (p.y >= camY1) && (p.x <= camX2) && (p.y <= camY2))
				marker.setContainer(MDirectObject.Container.ALL_TABLES);
			else
				marker.setContainer(MDirectObject.Container.REGION_TABLE);
		}
	}
	private void fixStage1Doors(MDirectObject[] objArr, String stageName, int stageIndex, Random r)
	{
		if ((stageIndex < 0) || (stageIndex > 2))
			throw new IllegalArgumentException("FixStage1Doors used on incorrect stage: index 0x"
					+ Integer.toHexString(stageIndex));
		
		HashMap<String, ArrayList<String>> argMap = mProcArgs.get(stageName)
				.get("fixStage1Doors")
				.get("inlinespawnmapref");
		
		MoonwalkerArea map = mSpawnMaps.get(stageName).get(argMap
				.get("name").get(0));
		int xOff = Integer.parseInt(argMap.get("offsetX").get(0));
		int yOff = Integer.parseInt(argMap.get("offsetY").get(0));
		
		for (MDirectObject o: objArr)
		{
			if (!(o.getType() == 0x50))
				continue;
			
			byte[] data = o.getData();
			
			List<Byte> vals = List.of((byte) 0x4, (byte) 0xC,
					(byte) 0x14, (byte) 0x1C, (byte) 0x24,
					(byte) 0x2C, (byte) 0x34, (byte) 0x3C);
			if (!vals.contains(data[2]))
				continue;
			
			boolean isLeftDoor = false;
			Point p = o.getAbsolutePosition();
			for (ShapeWrapper sh: map.getContent())
			{
				if (sh.isRectangle())
				{
					Rectangle rect = new Rectangle(sh.getRectangle());
					rect.x += xOff;
					rect.y += yOff;
					if (rect.contains(p))
					{
						isLeftDoor = true;
						break;
					}
				}
				else
				{
					Point po = new Point(sh.getPoint());
					po.x += xOff;
					po.y += yOff;
					if (po.equals(p))
					{
						isLeftDoor = true;
						break;
					}
				}
			}
			
			if (isLeftDoor)
			{
				data[0] |= 0x10;
				data[2] |= 0x10;
				data[4] |= 0x10;
			}
			else
			{
				data[0] &= 0xEF;
				data[2] &= 0xEF;
				data[4] &= 0xEF;
			}
			o.setData(data);
		}
	}
//	private void fixSpiders(MDirectObject[] objArr, String stageName, int stageIndex, Random r)
//	{
//		if ((stageIndex < 9) || (stageIndex > 11))
//			throw new IllegalArgumentException("FixSpiders used on incorrect stage: index 0x"
//					+ Integer.toHexString(stageIndex));
//		
//		HashMap<String, ArrayList<String>> argMap = mProcArgs.get(stageName)
//				.get("fixSpiders")
//				.get("inlinespawnmapref");
//		
//		MoonwalkerArea map = mSpawnMaps.get(stageName).get(argMap
//				.get("name").get(0));
//		int xOff = Integer.parseInt(argMap.get("offsetX").get(0));
//		int yOff = Integer.parseInt(argMap.get("offsetY").get(0));
//		
//		for (MDirectObject o: objArr)
//		{
//			if (!(o.getType() == 0x4C))
//				continue;
//			
//			byte[] data = o.getData();
//			
//			//TODO implement
//			
////			o.setData(data);
//		}
//	}
	private void randomizeTeleporters(MDirectObject[] objArr, String stageName, int stageIndex, Random r)
	{
		if (stageIndex != 0xC)
			throw new IllegalArgumentException("RandomizeTeleporters used on incorrect stage: index 0x"
					+ Integer.toHexString(stageIndex));
		
		HashMap<MDirectObject, MDirectObject> teleportMap = new HashMap<>();
		
		try
		{
			ArrayList<Short> typeList = new ArrayList<>(
					mProcArgs.get(stageName)
					.get("randomizeTeleporters")
					.get("object")
					.get("type")
					.stream()
					.map(s -> Short.parseShort(s, 16))
					.collect(Collectors.toList()));
			
			ArrayList<MDirectObject> objList = new ArrayList<>(Arrays.stream(objArr)
				.filter(obj -> typeList.contains(obj.getType()))
				.collect(Collectors.toList()));
			
			Map<Integer, List<MDirectObject>> groupedObjs =
					objList.stream()
					.collect(Collectors.groupingBy(MDirectObject::getAbsoluteY));
			
			List<Integer> floorList = groupedObjs.keySet()
					.stream()
					.collect(Collectors.collectingAndThen(Collectors.toList(), 
						list ->
						{
							Collections.shuffle(list, r);
							return list;
						}));
			
			int l = floorList.size();
			for (int i = 0; i < l; i++)
			{
				MDirectObject key = getRandom(groupedObjs.get(floorList.get(i)), r);
				teleportMap.put(key, pickSafeTarget(
						key, groupedObjs.get(floorList.get((i + 1) % l)), r));
			}
			objList.forEach(obj ->
			{
				if (!teleportMap.containsKey(obj))
					teleportMap.put(obj, pickSafeTarget(obj, objList, r));
			});
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return;
		}
		
		int ratio = 16;
		teleportMap.forEach((srcObj, targetObj) ->
		{
			int targetAddr = targetObj.getAllocationAddress();
			int xVal;
			int yVal;
			if (srcObj.getAbsolutePosition().equals(targetObj.getAbsolutePosition()))
			{
				xVal = 1;
				yVal = 1;
			}
			else
			{
				int targetX = targetObj.getAbsoluteX();
				int targetY = targetObj.getAbsoluteY();
				int xOff = targetX - Math.min(Math.max(srcObj.getAbsoluteX(), 128), 448);
				int yOff = targetY - Math.min(Math.max(srcObj.getAbsoluteY(), 96), 772);
				xVal = xOff / ratio;
				yVal = yOff / ratio;
				xVal += (xVal < 0)?-1:1;
				yVal += (yVal < 0)?-1:1;
			}
				
			byte[] data = srcObj.getData();
			data[0] = (byte) ((0xFF00 & targetAddr) >> 8);
			data[1] = (byte) (0xFF & targetAddr);
			data[2] = (byte) ((0xFF00 & yVal) >> 8);
			data[3] = (byte) (0xFF & yVal);
			data[4] = (byte) ((0xFF00 & xVal) >> 8);
			data[5] = (byte) (0xFF & xVal);
			srcObj.setData(data);
		});
	}
	private <T> T getRandom(Collection<T> col, Random r)
	{
		ArrayList<T> list = new ArrayList<>(col);
		Collections.shuffle(list, r);
		return list.get(0);
	}
	private List<Point> getPoints(Rectangle rect)
	{
		ArrayList<Point> ret = new ArrayList<>();
		int xLim = rect.x + rect.width;
		int yLim = rect.y + rect.height;
		for (int x = rect.x; x <= xLim; x++)
			for (int y = rect.y; y <= yLim; y++)
				ret.add(new Point(x, y));
		
		return ret;
	}
	private MDirectObject pickSafeTarget(MDirectObject obj, List<MDirectObject> objList, Random r)
	{
		int srcX = obj.getAbsoluteX();
		int srcY = obj.getAbsoluteY();
		long lim = Math.min(objList.size() * 10l, 100);
		for (int i = 0; i < lim; i++)
		{
			MDirectObject ret = getRandom(objList, r);
			int targX = ret.getAbsoluteX();
			int targY = ret.getAbsoluteY();
			if ((targX == 288)
					&& (targY == 496)
					&& (Math.abs(srcY - targY) < 300))
			{
				System.out.println("Teleport mapping rejected: ("
						+ srcX + ", "
						+ srcY + ") -> ("
						+ targX + ", "
						+ targY + ")");
				continue;
			}
			if ((targX == 288)
					&& (targY == 496))
			{
				System.out.println("Dangerous teleport mapping accepted: ("
						+ srcX + ", "
						+ srcY + ") -> ("
						+ targX + ", "
						+ targY + ")");
			}
			return ret;
		}
		throw new IllegalArgumentException("No safe teleport target found for " + obj);
	}
	private static boolean contains(short[] arr, short val)
	{
		for (short s: arr)
			if (s == val)
				return true;
		return false;
	}
	private static void shuffleArray(short[] arr, Random r)
	{
		ArrayList<Short> list = new ArrayList<>(arr.length);
		for (short s: arr)
			list.add(s);
		Collections.shuffle(list, r);
		for (int i = 0; i < arr.length; i++)
			arr[i] = list.get(i);
	}
	private static void shuffleArray(int[] arr, Random r)
	{
		ArrayList<Integer> list = new ArrayList<>(arr.length);
		for (int s: arr)
			list.add(s);
		Collections.shuffle(list, r);
		for (int i = 0; i < arr.length; i++)
			arr[i] = list.get(i);
	}
	private void mergeMisalignments(MDirectObject[][] objArr)
	{
		for (int i = 0; i < objArr.length; i++)
		{
			ArrayList<MDirectObject> list = new ArrayList<>();
			L0: for (MDirectObject srcObj: objArr[i])
			{
				Container srcCont = srcObj.getContainer();
				if (srcCont == Container.ALL_TABLES)
				{
					list.add(srcObj);
					continue;
				}
				
				Container compCont = (srcCont == Container.INITIAL_TABLE)?
						Container.REGION_TABLE:Container.INITIAL_TABLE;
				
				for (MDirectObject compObj: list)
				{
					if (compObj.getContainer() != compCont)
						continue;
					if (compObj.getType() != srcObj.getType())
						continue;
					if (compObj.getAllocationAddress() != srcObj.getAllocationAddress())
						continue;
					if (compObj.getAbsolutePosition().distance(srcObj.getAbsolutePosition()) < mergeThreshold)
					{
						compObj.setContainer(Container.ALL_TABLES);
						continue L0;
					}
				}
				
				list.add(srcObj);
			}
			
			objArr[i] = list.toArray(l -> new MDirectObject[l]);
		}
	}
	private void randomizePosition(MDirectObject obj, ArrayList<MDirectObject> randomizedObjList,
			String stageName, MapRef map, Rectangle initialCamera, Random rand, boolean log)
	{
		final int REGION_WIDTH = 320; //TODO move to Metadata
		final int BORDER_BUFFER = 3;
		
		Random r = new Random(Hashes.murmur64(rand.nextLong()));
		
		Point p = null;
		int stageIndex = mStageIndices.get(stageName);
		Point offset = map.getOffset();
		MoonwalkerArea area = mSpawnMaps.get(stageName).get(map.getAreaName());
		int i = 0;
		for (; i < retryLimit; i++)
		{
			p = area.getRandomPoint(r);
			p.x += offset.x;
			p.y += offset.y;
			
			if (p.x > BORDER_BUFFER)
			{
				int off = p.x % REGION_WIDTH;
				if (off <= BORDER_BUFFER)
					p.x += BORDER_BUFFER - off;
				else if (off >= (REGION_WIDTH - BORDER_BUFFER))
					p.x -= off - REGION_WIDTH + BORDER_BUFFER;
			}
			
			obj.setAbsolutePosition(p.x, p.y);
		
			if (!intersects(obj, randomizedObjList))
				break;
			
			if ((i + 1) >= retryLimit)
				System.out.println("Retry limit reached, skipping 0x"
						+ Integer.toHexString(0xFFFF & obj.getType()));
		}
		
		if (stageIndex < 0x10)
		{
			int camX1 = initialCamera.x;
			int camY1 = initialCamera.y;
			int camX2 = initialCamera.x + initialCamera.width;
			int camY2 = initialCamera.y + initialCamera.height;
			if ((p.x >= camX1) && (p.y >= camY1) && (p.x <= camX2) && (p.y <= camY2))
				obj.setContainer(MDirectObject.Container.ALL_TABLES);
			else
				obj.setContainer(MDirectObject.Container.REGION_TABLE);
		}
		else
			obj.setContainer(MDirectObject.Container.INITIAL_TABLE);
	}
	private boolean intersects(MDirectObject obj, ArrayList<MDirectObject> objList)
	{
		HitboxRef srcHRef = null;
		for (HitboxRef hRef: mHitboxes)
		{
			if (hRef.matches(obj))
			{
				srcHRef = hRef;
				break;
			}
		}
		if (srcHRef == null)
			return false;
		ArrayList<HitboxRef> targetHRefs = mCollisionChecks.get(srcHRef.getName());
		MoonwalkerArea srcArea = srcHRef.getHitboxArea().moveBy(obj.getAbsolutePosition());
		
		for (MDirectObject o: objList)
		{
			if (o != obj)
			{
				HitboxRef targetHRef = null;
				for (HitboxRef hRef: targetHRefs)
				{
					if (hRef.matches(o))
					{
						targetHRef = hRef;
						break;
					}
				}
				if (targetHRef == null)
					continue;
				MoonwalkerArea targetArea = targetHRef.getHitboxArea().moveBy(o.getAbsolutePosition());
				
				if (srcArea.intersects(targetArea))
					return true;
			}
		}
		return false;
	}
	private void applyGlobalAttributes(MDirectObject obj, MDirectObject[] objArr)
	{	
		short type = obj.getType();
		if (bindingMap.containsKey(type))
		{
			Binding[] bArr = bindingMap.get(type);
			Point p = obj.getAbsolutePosition();
			double minDist = Double.MAX_VALUE;
			MDirectObject binderObj = null;
			Binding b = null;
			
			for (Binding bin: bArr)
			{
				short binderType = bin.getBinderType();
				BiPredicate<Point, Point> filter = bin.getFilter();
				
				for (MDirectObject candidate: objArr)
				{
					if (candidate.getType() != binderType)
						continue;
					Point caP = candidate.getAbsolutePosition();
					if (!filter.test(caP, p))
						continue;
					
					double dist = caP.distance(p);
					if (dist < minDist)
					{
						binderObj = candidate;
						minDist = dist;
						b = bin;
					}
				}
			}
			if (binderObj == null)
				throw new IllegalStateException("Attribute processing failure: Cannot bind type 0x"
						+ Integer.toHexString(0xFFFF & type) + ", no suitable objects found.");
			
			byte[] srcArr = binderObj.getData();
			byte[] destArr = obj.getData();
			int srcInd = b.getSourceIndex();
			int destInd = b.getDestinationIndex();
			int len = b.getLength();
			for (int ind = 0; ind < len; ind++)
				destArr[destInd + ind] = srcArr[srcInd + ind];
			
			obj.setData(destArr);
		}
	}
	
	private String format(Rectangle r)
	{
		return "<rectangle x=\""
				+ r.x + "\" y=\""
				+ r.y + "\" w=\""
				+ r.width + "\" h=\""
				+ r.height + "\"/>";
	}
	private String format(ShapeWrapper wr)
	{
		if (wr.isRectangle())
			return format(wr.getRectangle());
		
		Point p = wr.getPoint();
		return "<point x=\"" + p.x + "\" y=\"" + p.y + "\"/>";
	}
	
	static class Binding
	{
		private short objType;
		private BiPredicate<Point, Point> directionPre;
		private int srcIndex;
		private int destIndex;
		private int length;
		
		public Binding(short objType, String searchDirection, int searchRange, int srcIndex, int destIndex, int length)
		{
			this.objType = objType;
			switch ((searchDirection == null)?"null":searchDirection)
			{
				case "N":
					directionPre = (binderP, bindeeP) -> 
						(binderP.y <= bindeeP.y) && (binderP.distance(bindeeP) < searchRange);
					break;
				case "W":
					directionPre = (binderP, bindeeP) -> 
						(binderP.x <= bindeeP.x) && (binderP.distance(bindeeP) < searchRange);
					break;
				case "S":
					directionPre = (binderP, bindeeP) -> 
						(binderP.y >= bindeeP.y) && (binderP.distance(bindeeP) < searchRange);
					break;
				case "E":
					directionPre = (binderP, bindeeP) -> 
						(binderP.x >= bindeeP.x) && (binderP.distance(bindeeP) < searchRange);
					break;
				case "NW":
					directionPre = (binderP, bindeeP) -> 
						(binderP.y <= bindeeP.y)
						&& (binderP.x <= bindeeP.x)
						&& (binderP.distance(bindeeP) < searchRange);
					break;
				case "NE":
					directionPre = (binderP, bindeeP) -> 
						(binderP.y <= bindeeP.y)
						&& (binderP.x >= bindeeP.x)
						&& (binderP.distance(bindeeP) < searchRange);
					break;
				case "SW":
					directionPre = (binderP, bindeeP) -> 
						(binderP.y >= bindeeP.y)
						&& (binderP.x <= bindeeP.x)
						&& (binderP.distance(bindeeP) < searchRange);
					break;
				case "SE":
					directionPre = (binderP, bindeeP) -> 
						(binderP.y >= bindeeP.y)
						&& (binderP.x >= bindeeP.x)
						&& (binderP.distance(bindeeP) < searchRange);
					break;
				default:
					directionPre = (binderP, bindeeP) -> binderP.distance(bindeeP) < searchRange;
			}
			this.srcIndex = srcIndex;
			this.destIndex = destIndex;
			this.length = length;
		}

		public short getBinderType()
		{
			return objType;
		}
		public BiPredicate<Point, Point> getFilter()
		{
			return directionPre;
		}
		public int getSourceIndex()
		{
			return srcIndex;
		}
		public int getDestinationIndex()
		{
			return destIndex;
		}
		public int getLength()
		{
			return length;
		}
	}
	static class MapRefResolver
	{
		private String defaultAreaName;
		private Point defaultOffset;
		
		private Predicate<byte[]>[] predicateArr;
		private String[] areaNameArr;
		private Point[] offsetArr;
		
		public MapRefResolver(String areaName, Point offset)
		{
			predicateArr = new Predicate[0];
			areaNameArr = new String[0];
			offsetArr = new Point[0];
			initDefault(areaName, offset);
		}
		public MapRefResolver(Predicate<byte[]>[] preArr, String[] areaNameArr,
				Point[] offsetArr, String defaultAreaName, Point defaultOffset)
		{
			if ((preArr.length != areaNameArr.length) || (areaNameArr.length != offsetArr.length))
				throw new IllegalArgumentException("Array lengths do not match.");
			predicateArr = preArr.clone();
			this.areaNameArr = areaNameArr.clone();
			this.offsetArr = new Point[offsetArr.length];
			for (int i = 0; i < offsetArr.length; i++)
				this.offsetArr[i] = offsetArr[i];
			
			initDefault(defaultAreaName, defaultOffset);
		}
		
		private void initDefault(String defAreaName, Point defOffset)
		{
			if ((defAreaName != null) != (defOffset != null))
				throw new IllegalArgumentException("Invalid default case: Only one of the arguments is null.");
			defaultAreaName = defAreaName;
			if (defOffset != null)
				defaultOffset = new Point(defOffset);
		}
		
		public MapRef getMapRef(byte[] data)
		{
			for (int i = 0; i < predicateArr.length; i++)
			{
				if (predicateArr[i].test(data))
				{
					if ((areaNameArr[i] != null) && (offsetArr[i] != null))
						return new MapRef(areaNameArr[i], offsetArr[i]);
					return null;
				}
			}
			if (defaultAreaName != null)
				return new MapRef(defaultAreaName, defaultOffset);
			return null;
		}
		
		@Override
		public String toString()
		{
			String ret =  "[MapRefResolver: ";
			for (int i = 0; i < predicateArr.length; i++)
			{
				ret += predicateArr[i] + ", ";
				ret += areaNameArr[i] + ", ";
				ret += offsetArr[i] + "; ";
			}
			return ret + "default: " + defaultAreaName + ", " + defaultOffset + "]";
		}
	}
	private static class MapRef
	{
		private String areaName;
		private Point offset;
		
		public MapRef(String areaName, Point offset)
		{
			this.areaName = areaName;
			this.offset = new Point(offset);
		}
		public String getAreaName()
		{
			return areaName;
		}
		public Point getOffset()
		{
			return offset;
		}
	}
	
	static class HitboxRef
	{
		private String name;
		private MoonwalkerArea hitbox;
		private short srcObjType;
		private Predicate<byte[]> pred;
		
		public HitboxRef(String name, MoonwalkerArea hitboxArea, short srcObjType)
		{
			this(name, hitboxArea, srcObjType, null);
		}
		public HitboxRef(String name, MoonwalkerArea hitboxArea, short srcObjType, Predicate<byte[]> predicate)
		{
			this.name = name;
			hitbox = hitboxArea;
			this.srcObjType = srcObjType;
			if (predicate == null)
				pred = arg -> true;
			else
				pred = predicate;
		}
		
		public String getName()
		{
			return name;
		}
		public MoonwalkerArea getHitboxArea()
		{
			return hitbox;
		}
		public boolean matches(MoonwalkerObject obj)
		{
			return (srcObjType == obj.getType()) && pred.test(obj.getData());
		}
	}
}
