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

public class ShapeWrapper
{
	Rectangle rect;
	Point point;
	
	public ShapeWrapper(Rectangle r)
	{
		rect = r;
	}
	public ShapeWrapper(Point p)
	{
		point = p;
	}
	public synchronized Rectangle getRectangle()
	{
		return rect;
	}
	public synchronized Point getPoint()
	{
		return point;
	}
	public boolean isRectangle()
	{
		return rect != null;
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
		ShapeWrapper other = (ShapeWrapper) obj;
		if (point == null)
		{
			if (other.point != null)
				return false;
		}
		else if (!point.equals(other.point))
			return false;
		if (rect == null)
		{
			if (other.rect != null)
				return false;
		}
		else if (!rect.equals(other.rect))
			return false;
		return true;
	}
	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ((point == null) ? 0 : point.hashCode());
		result = prime * result + ((rect == null) ? 0 : rect.hashCode());
		return result;
	}
	@Override
	public String toString()
	{
		return (rect == null)?point.toString():rect.toString();
	}
}