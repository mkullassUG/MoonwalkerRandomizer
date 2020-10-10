package moonwalker.randomizer.core;

public final class Hashes
{
	private Hashes()
	{}
	
	public static long murmur64(long l)
	{
		l ^= l >>> 33;
		l *= 0xff51afd7ed558ccdL;
		l ^= l >>> 33;
		l *= 0xc4ceb9fe1a85ec53L;
		l ^= l >>> 33;
		return l;
	}
}
