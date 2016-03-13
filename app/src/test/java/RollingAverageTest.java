/*
 * RollingAverageTest.java is part of Wa-Tor (C) 2016 by Dirk Gassen.
 *
 * Wa-Tor is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Wa-Tor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import org.junit.Test;

import com.dirkgassen.wator.utils.RollingAverage;

import junit.framework.Assert;

/**
 * @author dirk.
 */
public class RollingAverageTest {

	@Test
	public void rollingAvgTest() {
		RollingAverage avg = new RollingAverage(3);
		Assert.assertEquals("Unexpected avg before adding anything", 0f, avg.getAverage());

		avg.add(1);
		Assert.assertEquals("Unexpected avg after adding first value", 1f, avg.getAverage());

		avg.add(2);
		Assert.assertEquals("Unexpected avg after adding second value", (1f + 2f) / 2f, avg.getAverage());

		avg.add(3);
		Assert.assertEquals("Unexpected avg after adding third value", (1f + 2f + 3f) / 3f, avg.getAverage());

		avg.add(4);
		Assert.assertEquals("Unexpected avg after adding fourth value", (2f + 3f + 4f) / 3f, avg.getAverage());

		avg.add(5);
		Assert.assertEquals("Unexpected avg after adding fifth value", (3f + 4f + 5f) / 3f, avg.getAverage());

		avg.add(6);
		Assert.assertEquals("Unexpected avg after adding sixth value", (4f + 5f + 6f) / 3f, avg.getAverage());

		avg.add(7);
		Assert.assertEquals("Unexpected avg after adding seventh value", (5f + 6f + 7f) / 3f, avg.getAverage());

		avg.add(0);
		Assert.assertEquals("Unexpected avg after adding eighth value", (6f + 7f + 0f) / 3f, avg.getAverage());

		avg.add(-1);
		Assert.assertEquals("Unexpected avg after adding ninth value", (7f + 0f + -1f) / 3f, avg.getAverage());

		avg.add(12);
		Assert.assertEquals("Unexpected avg after adding tenth value", (0f + -1f + 12f) / 3f, avg.getAverage());
	}

}