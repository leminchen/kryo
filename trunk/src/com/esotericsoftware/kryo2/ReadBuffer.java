
package com.esotericsoftware.kryo2;

import java.util.ArrayList;

import com.esotericsoftware.kryo.SerializationException;

public class ReadBuffer {
	private final ArrayList<Buffer> filledBuffers = new ArrayList();
	private final ArrayList<Buffer> emptyBuffers = new ArrayList();
	private final int bufferSize, coreBuffers, maxBuffers;
	private Buffer buffer;
	private int bufferIndex, position;
	private int lastBufferIndex;
	private int marks;
	private char[] chars = new char[64];
	private byte[] temp = new byte[8];

	public ReadBuffer () {
		this(4096, 4, 4);
	}

	public ReadBuffer (int size) {
		this(size, 1, 1);
	}

	public ReadBuffer (int bufferSize, int maxBuffers) {
		this(bufferSize, maxBuffers, maxBuffers);
	}

	public ReadBuffer (int bufferSize, int coreBuffers, int maxBuffers) {
		this.bufferSize = bufferSize;
		this.coreBuffers = coreBuffers;
		this.maxBuffers = maxBuffers;

		buffer = new Buffer();
		filledBuffers.add(buffer);
		buffer.bytes = new byte[bufferSize];
	}

	public ReadBuffer (byte[] bytes) {
		this(bytes, 0, bytes.length);
	}

	public ReadBuffer (byte[] bytes, int offset, int count) {
		bufferSize = bytes.length;
		coreBuffers = 1;
		maxBuffers = 1;

		buffer = new Buffer();
		filledBuffers.add(buffer);
		buffer.bytes = bytes;
		buffer.count = count;
		position = offset;
	}

	public int mark () {
		marks++;
		return bufferIndex * (bufferSize + 1) + position;
	}

	public void positionToMark (int mark) {
		if (marks == 0) throw new IllegalStateException("No marks have been set.");
		bufferIndex = mark / (bufferSize + 1);
		position = mark % (bufferSize + 1);
		buffer = filledBuffers.get(bufferIndex);
		marks--;
	}

	public void clear () {
		bufferIndex = 0;
		position = 0;
	}

	//

	private void bufferEmpty () {
		position = 0;
		bufferIndex++;
		// Move read buffers from filled to empty list.
		if (marks == 0) {
			while (bufferIndex > 0) {
				Buffer buffer = filledBuffers.remove(0);
				if (filledBuffers.size() + emptyBuffers.size() < coreBuffers) emptyBuffers.add(buffer);
				bufferIndex--;
			}
		}
		// Use already filled buffer.
		if (bufferIndex < filledBuffers.size()) {
			buffer = filledBuffers.get(bufferIndex);
			return;
		}
		if (!emptyBuffers.isEmpty()) {
			// Use an empty buffer.
			buffer = emptyBuffers.remove(0);
			filledBuffers.add(buffer);
		} else {
			// Add new buffer.
			if (maxBuffers != -1 && filledBuffers.size() + emptyBuffers.size() >= maxBuffers)
				throw new SerializationException("Maximum number of buffers reached: " + maxBuffers);
			buffer = new Buffer();
			buffer.bytes = new byte[bufferSize];
			filledBuffers.add(buffer);
		}
		// Fill buffer.
		buffer.count = input(buffer.bytes);
		if (buffer.count <= 0) throw new SerializationException("Buffer underflow.");
	}

	protected int input (byte[] buffer) {
		return -1;
	}

	// byte

	public byte readByte () {
		if (position == buffer.count) bufferEmpty();
		return buffer.bytes[position++];
	}

	public int readUnsignedByte () {
		if (position == buffer.count) bufferEmpty();
		return buffer.bytes[position++] & 0xFF;
	}

	public void readBytes (byte[] bytes) {
		readBytes(bytes, 0, bytes.length);
	}

	public void readBytes (byte[] bytes, int offset, int count) {
		while (true) {
			int copyCount = Math.min(buffer.count - position, count);
			System.arraycopy(buffer.bytes, position, bytes, offset, copyCount);
			position += copyCount;
			count -= copyCount;
			if (count == 0) break;
			offset += copyCount;
			bufferEmpty();
		}
	}

	// int

	public int readInt () {
		// BOZO - Avoid method calls?
		return (readByte() & 0xFF) << 24 | (readByte() & 0xFF) << 16 | (readByte() & 0xFF) << 8 | readByte() & 0xFF;
	}

	public int readInt (boolean optimizePositive) {
		// BOZO - Unroll?
		for (int offset = 0, result = 0; offset < 32; offset += 7) {
			int b = readByte();
			result |= (b & 0x7F) << offset;
			if ((b & 0x80) == 0) {
				if (!optimizePositive) result = (result >>> 1) ^ -(result & 1);
				return result;
			}
		}
		throw new SerializationException("Malformed integer.");
	}

	public boolean canReadInt () {
		int start = mark();
		try {
			for (int offset = 0; offset < 32; offset += 7)
				if ((readByte() & 0x80) == 0) return true;
		} catch (SerializationException ignored) {
		} finally {
			positionToMark(start);
		}
		return false;
	}

	// string

	public String readString () {
		int charCount = readInt(true);
		if (chars.length < charCount) chars = new char[charCount];
		int c = 0, charIndex = 0;
		while (charIndex < charCount) {
			if (position == buffer.count) bufferEmpty();
			c = buffer.bytes[position++] & 0xFF;
			if (c > 127) break;
			chars[charIndex++] = (char)c;
		}
		if (charIndex < charCount) {
			while (true) {
				switch (c >> 4) {
				case 0:
				case 1:
				case 2:
				case 3:
				case 4:
				case 5:
				case 6:
				case 7:
					chars[charIndex++] = (char)c;
					break;
				case 12:
				case 13:
					chars[charIndex++] = (char)((c & 0x1F) << 6 | readByte() & 0x3F);
					break;
				case 14:
					chars[charIndex++] = (char)((c & 0x0F) << 12 | (readByte() & 0x3F) << 6 | readByte() & 0x3F);
					break;
				}
				if (charIndex >= charCount) break;
				c = readUnsignedByte();
			}
		}
		return new String(chars, 0, charCount);
	}

	// float

	public float readFloat () {
		return Float.intBitsToFloat(readInt());
	}

	public float readFloat (float precision, boolean optimizePositive) {
		return readInt(optimizePositive) / (float)precision;
	}

	// short

	public short readShort () {
		return (short)((readUnsignedByte() << 8) + readUnsignedByte());
	}

	public int readUnsignedShort () {
		return (readByte() << 8) + readByte();
	}

	public short readShort (boolean optimizePositive) {
		byte value = readByte();
		if (optimizePositive) {
			if (value == -1) return readShort(); // short positive
			return (short)(value & 0xFF);
		}
		if (value == -128) return readShort(); // short
		return value;
	}

	// long

	public long readLong () {
		readBytes(temp, 0, 8);
		return (((long)temp[0] << 56) //
			+ ((long)(temp[1] & 255) << 48) //
			+ ((long)(temp[2] & 255) << 40) //
			+ ((long)(temp[3] & 255) << 32) //
			+ ((long)(temp[4] & 255) << 24) //
			+ ((temp[5] & 255) << 16) //
			+ ((temp[6] & 255) << 8) //
		+ ((temp[7] & 255) << 0));
	}

	public long readLong (boolean optimizePositive) {
		long result = 0;
		for (int offset = 0; offset < 64; offset += 7) {
			byte b = readByte();
			result |= (long)(b & 0x7F) << offset;
			if ((b & 0x80) == 0) {
				if (!optimizePositive) result = (result >>> 1) ^ -(result & 1);
				return result;
			}
		}
		throw new SerializationException("Malformed long.");
	}

	// boolean

	public boolean readBoolean () {
		if (position == buffer.count) bufferEmpty();
		return buffer.bytes[position++] == 1;
	}

	// char

	public char readChar () {
		return (char)((readUnsignedByte() << 8) + readUnsignedByte());
	}

	// double

	public double readDouble () {
		return Double.longBitsToDouble(readLong());
	}

	public double readDouble (double precision, boolean optimizePositive) {
		return readLong(optimizePositive) / (double)precision;
	}

	//

	static class Buffer {
		byte[] bytes;
		int count;
	}
}
