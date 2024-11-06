/*
* Copyright (C) 2015 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.libs.http.server.websockets;

import be.nabu.libs.http.server.websockets.api.OpCode;
import junit.framework.TestCase;

public class BitTest extends TestCase {
	
	public void testOpCodeParsing() {
		// 127 = 1111 1111
		// so -15 (last set) this is 112 to get 0 if anded with 15
		assertEquals(OpCode.CONTINUATION, (OpCode.getOpCode((byte) (112 & 15))));
		assertEquals(OpCode.TEXT, (OpCode.getOpCode((byte) (113 & 15))));
	}
	
	public void testLongSignBit() {
		long a = Long.MIN_VALUE + 100;
		// need to set the most significant bit to 0
		long mask = Long.MAX_VALUE;
		a &= mask;
		assertEquals(100l, a);
	}

	public void testBitToggling() {
		assertEquals("10000000", Long.toBinaryString(0 | 128));
		assertEquals("11111111", Long.toBinaryString(0 | 128 | 127));
		assertEquals("11111110", Long.toBinaryString(0 | 128 | 126));
	}
	
}
