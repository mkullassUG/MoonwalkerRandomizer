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

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.function.Predicate;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import moonwalker.randomizer.core.MoonwalkerRandomizer.Binding;
import moonwalker.randomizer.core.MoonwalkerRandomizer.HitboxRef;
import moonwalker.randomizer.core.MoonwalkerRandomizer.MapRefResolver;

public class StageDataParser
{
	private HashMap<Short, Binding[]> bindingMap;
	private ArrayList<String> mStageNames;
	private HashMap<String, Integer> mStageIndices;
	private HashMap<String, HashMap<String, MoonwalkerArea>> mSpawnMaps;
	private HashMap<String, HashMap<Short, MapRefResolver>> mSpawnMapRefs;
	private HashMap<String, ArrayList<String>> mProcedures;
	private HashMap<String, HashMap<String, HashMap<String, HashMap<String, ArrayList<String>>>>>
		mProcedureArgs;
	
	private ArrayList<HitboxRef> mHitboxes;
	private HashMap<String, ArrayList<HitboxRef>> mCollisionChecks;
	
	public StageDataParser(Element root)
	{
		bindingMap = new HashMap<>();
		mStageNames = new ArrayList<>();
		mStageIndices = new HashMap<>();
		mSpawnMaps = new HashMap<>();
		mSpawnMapRefs = new HashMap<>();
		mProcedures = new HashMap<>();
		mProcedureArgs = new HashMap<>();
		
		mHitboxes = new ArrayList<>();
		mCollisionChecks = new HashMap<>();
		
	    Element globalAttrsElem = elemOrNull(root.getElementsByTagName("globalAttributes"), 0);
	    if (isImmediateChild(globalAttrsElem, root))
	    {
	    	Element bindingsElem = elemOrNull(globalAttrsElem.getElementsByTagName("bindings"), 0);
	    	if (isImmediateChild(bindingsElem, globalAttrsElem))
	    	{
	    		for (Node n: new IterableNodeList(bindingsElem.getElementsByTagName("boundObject")))
	    		{
	    			if (!(n instanceof Element))
	    				continue;
	    			Element objElem = (Element) n;
	    			short type = Short.parseShort(objElem.getAttribute("type"), 16);
	    			
	    			ArrayList<Binding> bList = new ArrayList<>();
	    			
	    			for (Node no: new IterableNodeList(objElem.getElementsByTagName("bindingObject")))
		    		{
		    			if (!(no instanceof Element))
		    				continue;
		    			
		    			Element bindSrcObjElem = (Element) no;
		    			short bindSrcType = Short.parseShort(bindSrcObjElem.getAttribute("type"), 16);
		    			String direction = elemOrNull(
		    					bindSrcObjElem.getElementsByTagName("searchDirection"), 0).getTextContent();
		    			int searchRange = Integer.parseInt(elemOrNull(
		    					bindSrcObjElem.getElementsByTagName("searchRange"), 0).getTextContent());
		    			int srcIndex = Integer.parseInt(elemOrNull(
		    					bindSrcObjElem.getElementsByTagName("sourceIndex"), 0).getTextContent());
		    			int destIndex = Integer.parseInt(elemOrNull(
		    					bindSrcObjElem.getElementsByTagName("destinationIndex"), 0).getTextContent());
		    			int len = Integer.parseInt(elemOrNull(
		    					bindSrcObjElem.getElementsByTagName("length"), 0).getTextContent());
		    			bList.add(new Binding(bindSrcType, direction, searchRange, srcIndex, destIndex, len));
		    		}
	    			
	    			bindingMap.put(type, bList.toArray(l -> new Binding[l]));
	    		}
	    	}
	    	
	    	Element hitboxesElem = elemOrNull(globalAttrsElem.getElementsByTagName("hitboxes"), 0);
	    	if (isImmediateChild(hitboxesElem, globalAttrsElem))
	    	{
	    		for (Node n: new IterableNodeList(hitboxesElem.getElementsByTagName("objectHitbox")))
	    		{
	    			if (!(n instanceof Element))
	    				continue;

	    			Element hitboxElem = (Element) n;
	    			parseHitboxRef(hitboxElem, mHitboxes);
	    		}
	    	}
	    	
	    	HashMap<String, HitboxRef> hitboxMap = new HashMap<>();
	    	for (HitboxRef hRef: mHitboxes)
	    		hitboxMap.put(hRef.getName(), hRef);
	    	
	    	Element collisionChecksElem = elemOrNull(globalAttrsElem.getElementsByTagName("collisionChecks"), 0);
	    	if (isImmediateChild(collisionChecksElem, globalAttrsElem))
	    	{
	    		for (Node n: new IterableNodeList(collisionChecksElem.getElementsByTagName("hitboxRef")))
	    		{
	    			if (!(n instanceof Element))
	    				continue;
	    			Element hitboxRefElem = (Element) n;
	    			if (!isImmediateChild(hitboxRefElem, collisionChecksElem))
	    				continue;
	    			
	    			String name = hitboxRefElem.getAttribute("name");
	    			ArrayList<HitboxRef> collisionCheckTargets = new ArrayList<>();
	    			
	    			for (Node no: new IterableNodeList(hitboxRefElem.getElementsByTagName("hitboxRef")))
		    		{
	    				if (!(no instanceof Element))
		    				continue;
	    				collisionCheckTargets.add(hitboxMap.get(((Element) no).getAttribute("name")));
		    		}
	    			
	    			mCollisionChecks.put(name, collisionCheckTargets);
	    		}
	    	}
	    }
	    
	    Element stagesElem = elemOrNull(root.getElementsByTagName("stages"), 0);
	    for (Node n: new IterableNodeList(stagesElem.getElementsByTagName("stage")))
	    {
	    	if (!(n instanceof Element))
				continue;
	    	Element e = (Element) n;
	    	String name = e.getAttribute("name");
	    	int index = Integer.parseInt(e.getAttribute("index"));
	    	
	    	mStageNames.add(name);
	    	mStageIndices.put(name, index);
	    	HashMap<String, MoonwalkerArea> areaMaps = new HashMap<>();
	    	Element areaMapsElem = elemOrNull(e.getElementsByTagName("spawnMaps"), 0);
	    	if (isImmediateChild(areaMapsElem, e))
	    	{
	    		for (Node no: new IterableNodeList(areaMapsElem.getElementsByTagName("spawnMap")))
	    	    {
	    			if (!(no instanceof Element))
	    				continue;
	    			Element el = (Element) no;
	    			String areaName = el.getAttribute("name");
	    			MoonwalkerArea area = new MoonwalkerArea();
	    			fillArea(area, el.getChildNodes());
	    			areaMaps.put(areaName, area);
	    	    }
	    	}
	    	mSpawnMaps.put(name, areaMaps);
	    	
	    	Element randomizationFlowElem = elemOrNull(e.getElementsByTagName("randomizationFlow"), 0);
	    	if (isImmediateChild(randomizationFlowElem, e))
	    	{
	    		Element randPosElem = elemOrNull(randomizationFlowElem.getElementsByTagName("randomizePositions"), 0);
	    		
	    		if (isImmediateChild(randPosElem, randomizationFlowElem))
	    		{
		    		HashMap<Short, MapRefResolver> map = new HashMap<>();
		    		for (Node no: new IterableNodeList(randPosElem.getElementsByTagName("object")))
		    	    {
		    			if (!(no instanceof Element))
		    				continue;
		    			Element objElem = (Element) no;
		    			short type = Short.parseShort(objElem.getAttribute("type"), 16);
		    			
		    			Element spawnMapRefElem = elemOrNull(objElem.getElementsByTagName("spawnMapRef"), 0);
		    			if (isImmediateChild(spawnMapRefElem, objElem))
		    			{
		    				String areaName = spawnMapRefElem.getAttribute("name");
		    				Element offsetElem = elemOrNull(spawnMapRefElem.getElementsByTagName("offset"), 0);
		    				int xOff = Integer.parseInt(offsetElem.getAttribute("x"));
		    				int yOff = Integer.parseInt(offsetElem.getAttribute("y"));
		    				
		    				MapRefResolver mrRes = new MapRefResolver(areaName, new Point(xOff, yOff));
		    				map.put(type, mrRes);
		    			}
		    			else
		    			{
		    				ArrayList<Predicate<byte[]>> preList = new ArrayList<>();
		    				ArrayList<String> mapNameList = new ArrayList<>();
		    				ArrayList<Point> offsetList = new ArrayList<>();
		    				
		    				for (Node node: new IterableNodeList(objElem.getElementsByTagName("case")))
		    				{
		    					if (!(node instanceof Element))
		    	    				continue;
		    					Element caseElem = (Element) node;
		    					if (!isImmediateChild(caseElem, objElem))
		    						continue;
		    					
		    					if (elemOrNull(caseElem.getElementsByTagName("doNotRandomize"), 0) == null)
		    					{
			    					spawnMapRefElem = elemOrNull(caseElem.getElementsByTagName("spawnMapRef"), 0);
				    				String areaName = spawnMapRefElem.getAttribute("name");
				    				Element offsetElem = elemOrNull(spawnMapRefElem.getElementsByTagName("offset"), 0);
				    				int xOff = Integer.parseInt(offsetElem.getAttribute("x"));
				    				int yOff = Integer.parseInt(offsetElem.getAttribute("y"));
				    				
				    				mapNameList.add(areaName);
				    				offsetList.add(new Point(xOff, yOff));
		    					}
		    					else
		    					{
		    						mapNameList.add(null);
				    				offsetList.add(null);
		    					}
			    				
			    				Element predicateElem = elemOrNull(caseElem.getElementsByTagName("predicate"), 0);
			    				Predicate<byte[]> pre = constructPredicate(predicateElem, Operator.AND);
			    				
			    				preList.add(pre);
		    				}
		    				
		    				Element defElem = elemOrNull(objElem.getElementsByTagName("defaultCase"), 0);
		    				String defAreaName = null;
		    				Point defOffset = null;
		    				if (isImmediateChild(defElem, objElem))
		    				{
			    				spawnMapRefElem = elemOrNull(defElem.getElementsByTagName("spawnMapRef"), 0);
			    				defAreaName = spawnMapRefElem.getAttribute("name");
			    				Element defOffsetElem = elemOrNull(spawnMapRefElem.getElementsByTagName("offset"), 0);
			    				int defXOff = Integer.parseInt(defOffsetElem.getAttribute("x"));
			    				int defYOff = Integer.parseInt(defOffsetElem.getAttribute("y"));
			    				defOffset = new Point(defXOff, defYOff);
		    				}
		    				
		    				MapRefResolver mrRes = new MapRefResolver(preList.toArray(l -> new Predicate[l]),
		    						mapNameList.toArray(l -> new String[l]),
		    						offsetList.toArray(l -> new Point[l]),
		    						defAreaName, defOffset);
		    				
		    				map.put(type, mrRes);
		    			}
		    	    }
		    		mSpawnMapRefs.put(name, map);
	    		}
	    		
	    		ArrayList<String> procList = new ArrayList<>();
	    		for (Node procNode: new IterableNodeList(randomizationFlowElem.getElementsByTagName("executeProcedure")))
	    	    {
	    			if (!(procNode instanceof Element))
	    				continue;
	    			Element procElem = (Element) procNode;
	    			String procName = procElem.getAttribute("name");
	    			procList.add(procName);
	    			
	    			for (Node no: new IterableNodeList(procElem.getChildNodes()))
		    	    {
	    				if (!(no instanceof Element))
		    				continue;
	    				
	    				Element procArgElem = (Element) no;
	    				String argName = procArgElem.getTagName().toLowerCase();
	    				
	    				mProcedureArgs.computeIfAbsent(name, key -> new HashMap<>());
	    				HashMap<String, HashMap<String, HashMap<String, ArrayList<String>>>>
	    					mProcArgs = mProcedureArgs.get(name);
	    				mProcArgs.computeIfAbsent(procName, key -> new HashMap<>());
	    				HashMap<String, HashMap<String, ArrayList<String>>> argMap
	    					= mProcArgs.get(procName);
	    				argMap.computeIfAbsent(argName, k -> new HashMap<>());
	    				
	    				HashMap<String, ArrayList<String>> arg = argMap.get(argName);
	    				
	    				NamedNodeMap attrMap = procArgElem.getAttributes();
	    				int attrMapLen = attrMap.getLength();
	    				for (int i = 0; i < attrMapLen; i++)
	    				{
	    					Node argAttrNode = attrMap.item(i);
	    					if (!(argAttrNode instanceof Attr))
	    						continue;
	    					
	    					Attr argAttr = (Attr) argAttrNode;
	    					String key = argAttr.getName();
	    					arg.computeIfAbsent(key, k -> new ArrayList<>());
	    					arg.get(key).add(argAttr.getValue());
	    				}
		    	    }
	    	    }
	    		mProcedures.put(name, procList);
	    	}
	    }
	}
	
	private static void parseHitboxRef(Element hitboxElem, ArrayList<HitboxRef> list)
	{
		String name = hitboxElem.getAttribute("name");
		short type = (short) Integer.parseInt(hitboxElem.getAttribute("type"), 16);
		
		
		Element hitboxContentElem = elemOrNull(hitboxElem.getElementsByTagName("hitbox"), 0);
		Element predicateElem = elemOrNull(hitboxElem.getElementsByTagName("predicate"), 0);
		boolean isComplex = isImmediateChild(hitboxContentElem, hitboxElem);
		if (isComplex != isImmediateChild(predicateElem, hitboxElem))
			throw new IllegalArgumentException("Invalid hitbox definition: either predicate or hitbox shape is missing.");
		if (isComplex)
		{
			MoonwalkerArea area = new MoonwalkerArea();
			fillArea(area, hitboxContentElem.getChildNodes());
			Predicate<byte[]> pre = constructPredicate(predicateElem, Operator.AND);
			list.add(new HitboxRef(name, area, type, pre));
		}
		else
		{
			MoonwalkerArea area = new MoonwalkerArea();
			fillArea(area, hitboxElem.getChildNodes());
			list.add(new HitboxRef(name, area, type));
		}
	}
	private static void fillArea(MoonwalkerArea area, NodeList nl)
	{
		for (Node node: new IterableNodeList(nl))
	    {
			if (!(node instanceof Element))
				continue;
			Element elem = (Element) node;
			if (elem.getTagName().equalsIgnoreCase("Rectangle"))
				area.add(new Rectangle(
						Integer.parseInt(elem.getAttribute("x")),
						Integer.parseInt(elem.getAttribute("y")),
						Integer.parseInt(elem.getAttribute("w")),
						Integer.parseInt(elem.getAttribute("h"))));
			else
				area.add(new Point(
						Integer.parseInt(elem.getAttribute("x")),
						Integer.parseInt(elem.getAttribute("y"))));
	    }
	}

	private static Element elemOrNull(NodeList src, int index)
	{
		if (index >= 0 && src.getLength() > index)
			return (Element) src.item(index);
		return null;
	}
	private static boolean isImmediateChild(Element child, Element parent)
	{
		return (child != null) && (child.getParentNode().equals(parent));
	}
	private static Predicate<byte[]> constructPredicate(Element root, Operator oper)
	{
		Predicate<byte[]> ret = null;
		
		for (Node pNode: new IterableNodeList(root.getChildNodes()))
		{
			if (!(pNode instanceof Element))
				continue;
			
			Element pElem = (Element) pNode;
			switch (pElem.getTagName().toLowerCase())
			{
				case "dataequals":
					int ind = Integer.parseInt(pElem.getAttribute("index"));
					int val = Integer.parseInt(pElem.getAttribute("value"));
					ret = concatPredicate(ret, arr -> arr[ind] == val, oper);
					break;
				case "dataequalshex":
					ind = Integer.parseInt(pElem.getAttribute("index"));
					val = Integer.parseInt(pElem.getAttribute("value"), 16);
					ret = concatPredicate(ret, arr -> arr[ind] == val, oper);
					break;
				case "const":
					String sVal = pElem.getAttribute("val");
					if (sVal.equalsIgnoreCase("true"))
						ret = concatPredicate(ret, arr -> true, oper);
					else if (sVal.equalsIgnoreCase("false"))
						ret = concatPredicate(ret, arr -> false, oper);
					else
						throw new IllegalArgumentException("Invalid constant.");
				case "and":
					ret = concatPredicate(ret, constructPredicate(pElem, Operator.AND), oper);
					break;
				case "or":
					ret = concatPredicate(ret, constructPredicate(pElem, Operator.OR), oper);
					break;
				case "xor":
					ret = concatPredicate(ret, constructPredicate(pElem, Operator.XOR), oper);
					break;
				case "not":
					ret = concatPredicate(ret, singleElemToPredicate(pElem).negate(), oper);
					break;
			}
		}
		
		if (ret == null)
			throw new IllegalArgumentException("Empty element.");
		
		return ret;
	}
	private static Predicate<byte[]> singleElemToPredicate(Element root)
	{
		boolean found = false;
		for (Node pNode: new IterableNodeList(root.getChildNodes()))
		{
			if (!(pNode instanceof Element))
				continue;
			if (found)
				throw new IllegalArgumentException("Too many operands.");
			found =  true;
		}
		return constructPredicate(root, Operator.AND);
	}
	private static Predicate<byte[]> concatPredicate(Predicate<byte[]> srcP, Predicate<byte[]> newP, Operator oper)
	{
		if (srcP == null)
			return newP;
		if (newP == null)
			return srcP;
		switch (oper)
		{
			default:
			case AND:
				return srcP.and(newP);
			case OR:
				return srcP.or(newP);
			case XOR:
				return srcP.or(newP).and(srcP.and(newP).negate());
			case NOT:
				throw new IllegalArgumentException("Too many operands for operation: not.");
		}
	}
	private enum Operator
	{
		AND,
		OR,
		XOR,
		NOT
	}
	private static class IterableNodeList implements Iterable<Node>
	{
		private Node[] arr;
		private int counter;
		
		public IterableNodeList(NodeList nl)
		{
			arr = new Node[nl.getLength()];
			for (int i = 0; i < arr.length; i++)
				arr[i] = nl.item(i);
			counter = 0;
		}
		
		@Override
		public Iterator<Node> iterator()
		{
			return new Iterator<Node>()
			{
				@Override
				public boolean hasNext()
				{
					return counter < arr.length;
				}
				@Override
				public Node next()
				{
					return arr[counter++];
				}
			};
		}
	}

	public HashMap<Short, Binding[]> getBindingMap()
	{
		return bindingMap;
	}
	public ArrayList<String> getStageNameList()
	{
		return mStageNames;
	}
	public HashMap<String, Integer> getStageIndexList()
	{
		return mStageIndices;
	}
	public HashMap<String, HashMap<String, MoonwalkerArea>> getSpawnMapList()
	{
		return mSpawnMaps;
	}
	public HashMap<String, HashMap<Short, MapRefResolver>> getSpawnMapRefList()
	{
		return mSpawnMapRefs;
	}
	public HashMap<String, ArrayList<String>> getExecutedProcedureList()
	{
		return mProcedures;
	}
	public HashMap<String, HashMap<String, HashMap<String, HashMap<String, ArrayList<String>>>>>
		getProcedureArguments()
	{
		return mProcedureArgs;
	}
	public ArrayList<HitboxRef> getHitboxes()
	{
		return mHitboxes;
	}
	public HashMap<String, ArrayList<HitboxRef>> getCollisionChecks()
	{
		return mCollisionChecks;
	}
}
