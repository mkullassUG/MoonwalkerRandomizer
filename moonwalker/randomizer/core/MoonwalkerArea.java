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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Random;

public class MoonwalkerArea
{
	private ArrayList<ShapeWrapper> areas;
	private RandomPointGenerator rpGen;
	
	public MoonwalkerArea()
	{
		this(new DefaultRandomPointGenerator());
	}
	public MoonwalkerArea(Rectangle r)
	{
		this();
		add(r);
	}
	public MoonwalkerArea(Point p)
	{
		this();
		add(p);
	}
	public MoonwalkerArea(RandomPointGenerator rpg)
	{
		areas = new ArrayList<>();
		rpGen = rpg;
	}
	public MoonwalkerArea(Rectangle r, RandomPointGenerator rpg)
	{
		this(rpg);
		add(r);
	}
	public MoonwalkerArea(Point p, RandomPointGenerator rpg)
	{
		this(rpg);
		add(p);
	}
	
	private MoonwalkerArea(ArrayList<ShapeWrapper> areas, RandomPointGenerator rpg)
	{
		this.areas = areas;
		rpGen = rpg;
	}
	
	public void add(Rectangle r)
	{
		areas.add(new ShapeWrapper(r));
	}
	public void add(Point p)
	{
		areas.add(new ShapeWrapper(p));
	}
	public Point getRandomPoint(Random r)
	{
		return rpGen.getRandomPoint(this, r);
	}
	public boolean contains(Point p)
	{
		for (ShapeWrapper sh:areas)
		{
			if (sh.isRectangle())
			{
				if (rectContains(sh.getRectangle(), p))
					return true;
			}
			else if (sh.getPoint().equals(p))
				return true;
		}
		return false;
	}
	public boolean rectContains(Rectangle rect, Point p)
	{
		return new Rectangle(rect.x, rect.y, rect.width + 1, rect.height + 1)
				.contains(p);
	}
	public Rectangle getBounds()
	{
		Rectangle ret = null;
		for (ShapeWrapper sh:areas)
		{
			if (sh.isRectangle())
			{
				Rectangle rect = sh.getRectangle();
				if (ret == null)
					ret = new Rectangle(rect);
				else
				{
					if (rect.x < ret.x)
					{
						ret.width += ret.x - rect.x;
						ret.x = rect.x;
					}
					if (rect.y < ret.y)
					{
						ret.height += ret.y - rect.y;
						ret.y = rect.y;
					}
					if ((rect.x + rect.width) > (ret.x + ret.width))
						ret.width = rect.x + rect.width - ret.x;
					if ((rect.y + rect.height) > (ret.y + ret.height))
						ret.height = rect.y + rect.height - ret.y;
				}
			}
			else
			{
				Point p = sh.getPoint();
				if (ret == null)
					ret = new Rectangle(p.x, p.y, 0, 0);
				else
				{
					if (p.x < ret.x)
					{
						ret.width += ret.x - p.x;
						ret.x = p.x;
					}
					if (p.y < ret.y)
					{
						ret.height += ret.y - p.y;
						ret.y = p.y;
					}
					if (p.x > (ret.x + ret.width))
						ret.width = p.x - ret.x;
					if (p.y > (ret.y + ret.height))
						ret.height = p.y - ret.y;
				}
			}
		}
		return ret;
	}
	public ShapeWrapper[] getContent()
	{
		return areas.toArray(l -> new ShapeWrapper[l]);
	}
	public RandomPointGenerator getRandomPointGenerator()
	{
		return rpGen;
	}
	public void setRandomPointGenerator(RandomPointGenerator rpg)
	{
		rpGen = rpg;
	}
	public MoonwalkerArea moveBy(Point point)
	{
		ArrayList<ShapeWrapper> newAreas = new ArrayList<>(areas.size());
		for (ShapeWrapper sh: areas)
		{
			if (sh.isRectangle())
			{
				Rectangle r = new Rectangle(sh.getRectangle());
				r.x += point.x;
				r.y += point.y;
				newAreas.add(new ShapeWrapper(r));
			}
			else
			{
				Point p = new Point(sh.getPoint());
				p.x += point.x;
				p.y += point.y;
				newAreas.add(new ShapeWrapper(p));
			}
		}
		return new MoonwalkerArea(newAreas, rpGen);
	}
//	public boolean intersects(Rectangle rect)
//	{
//		for (ShapeWrapper sh: areas)
//		{
//			if (sh.isRectangle())
//			{
//				if (sh.getRectangle().intersects(rect))
//					return true;
//			}
//			else if (rect.contains(sh.getPoint()))
//				return true;
//		}
//		return false;
//	}
	public boolean intersects(Rectangle rect)
	{
		Rectangle r1 = new Rectangle(rect);
		r1.width++;
		r1.height++;
		for (ShapeWrapper sh: areas)
		{
			if (sh.isRectangle())
			{
				Rectangle r2 = new Rectangle(sh.getRectangle());
				r2.width++;
				r2.height++;
				if (r1.intersects(r2))
					return true;
			}
			else if (r1.contains(sh.getPoint()))
				return true;
		}
		return false;
	}
	public boolean intersects(ShapeWrapper sh)
	{
		if (sh.isRectangle())
			return intersects(sh.getRectangle());
		else
			return contains(sh.getPoint());
	}
	public boolean intersects(MoonwalkerArea area)
	{
		for (ShapeWrapper targetSh: area.areas)
		{
			if (intersects(targetSh))
				return true;
		}
		return false;
	}
	
	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		MoonwalkerArea other = (MoonwalkerArea) obj;
		if (areas == null)
		{
			if (other.areas != null)
				return false;
		}
		else if (!areas.equals(other.areas))
			return false;
		return true;
	}
	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ((areas == null) ? 0 : areas.hashCode());
		return result;
	}
	@Override
	public String toString()
	{
		String ret = "";
		for (ShapeWrapper sh:areas)
		{
			ret += (sh.isRectangle()?sh.getRectangle():sh.getPoint()) + ", ";
		}
		int l = ret.length();
		if (l > 1)
			ret = ret.substring(0, l - 2);
		return "[" + ret + "]";
	}
	
	private static class DefaultRandomPointGenerator implements RandomPointGenerator
	{
		@Override
		public Point getRandomPoint(MoonwalkerArea area, Random r)
		{
			long sum = 0;
			for (ShapeWrapper sh: area.areas)
			{
				if (sh.isRectangle())
				{
					Rectangle rect = sh.getRectangle();
					sum += (rect.width + 1) * (rect.height + 1);
				}
				else
					sum++;
			}
			long desired = new BigDecimal(sum).multiply(new BigDecimal(r.nextDouble())).longValue();
			long counter = 0;
			for (ShapeWrapper sh: area.areas)
			{
				long incr;
				if (sh.isRectangle())
				{
					Rectangle rect = sh.getRectangle();
					int w = (rect.width + 1);
					int h = (rect.height + 1);
					incr = w * h;
					if ((desired >= counter) && (desired < (counter + incr)))
					{
						long diff = desired - counter;
						return new Point((int) (rect.x + diff % w), (int) (rect.y + diff / w));
					}
				}
				else
				{
					incr = 1;
					if (desired == counter)
					{
						return new Point(sh.getPoint());
					}
				}
				counter += incr;
			}
			//dead code (hopefully)
			return null;
		}
	}
}
