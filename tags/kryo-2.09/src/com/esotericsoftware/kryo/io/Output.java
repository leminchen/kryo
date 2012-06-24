
package com.esotericsoftware.kryo.io;

import java.io.IOException;
import java.io.OutputStream;

import com.esotericsoftware.kryo.KryoException;

/** An OutputStream that buffers data in a byte array and optionally flushes to another OutputStream. Utility methods are provided
 * for efficiently writing primitive types and strings.
 * @author Nathan Sweet <misc@n4te.com> */
public class Output extends OutputStream {
	private int maxCapacity, capacity, position, total;
	private byte[] buffer;
	private OutputStream outputStream;

	/** Creates an uninitialized Output. {@link #setBuffer(byte[], int)} must be called before the Output is used. */
	public Output () {
	}

	/** Creates a new Output for writing to a byte array.
	 * @param bufferSize The initial and maximum size of the buffer. An exception is thrown if this size is exceeded. */
	public Output (int bufferSize) {
		this(bufferSize, bufferSize);
	}

	/** Creates a new Output for writing to a byte array.
	 * @param bufferSize The initial size of the buffer.
	 * @param maxBufferSize The buffer is doubled as needed until it exceeds maxBufferSize and an exception is thrown. */
	public Output (int bufferSize, int maxBufferSize) {
		if (maxBufferSize < -1) throw new IllegalArgumentException("maxBufferSize cannot be < -1: " + maxBufferSize);
		this.capacity = bufferSize;
		this.maxCapacity = maxBufferSize == -1 ? Integer.MAX_VALUE : maxBufferSize;
		buffer = new byte[bufferSize];
	}

	/** Creates a new Output for writing to a byte array.
	 * @see #setBuffer(byte[]) */
	public Output (byte[] buffer) {
		this(buffer, buffer.length);
	}

	/** Creates a new Output for writing to a byte array.
	 * @see #setBuffer(byte[], int) */
	public Output (byte[] buffer, int maxBufferSize) {
		if (buffer == null) throw new IllegalArgumentException("buffer cannot be null.");
		setBuffer(buffer, maxBufferSize);
	}

	/** Creates a new Output for writing to an OutputStream. A buffer size of 4096 is used. */
	public Output (OutputStream outputStream) {
		this(4096, 4096);
		if (outputStream == null) throw new IllegalArgumentException("outputStream cannot be null.");
		this.outputStream = outputStream;
	}

	/** Creates a new Output for writing to an OutputStream. */
	public Output (OutputStream outputStream, int bufferSize) {
		this(bufferSize, bufferSize);
		if (outputStream == null) throw new IllegalArgumentException("outputStream cannot be null.");
		this.outputStream = outputStream;
	}

	public OutputStream getOutputStream () {
		return outputStream;
	}

	/** Sets a new OutputStream. The position and total are reset, discarding any buffered bytes. */
	public void setOutputStream (OutputStream outputStream) {
		if (outputStream == null) throw new IllegalArgumentException("outputStream cannot be null.");
		this.outputStream = outputStream;
		position = 0;
		total = 0;
	}

	/** Sets the buffer that will be written to. {@link #setBuffer(byte[], int)} is called with the specified buffer's length as the
	 * maxBufferSize. */
	public void setBuffer (byte[] buffer) {
		setBuffer(buffer, buffer.length);
	}

	/** Sets the buffer that will be written to. The position and total are reset, discarding any buffered bytes. The
	 * {@link #setOutputStream(OutputStream) OutputStream} is set to null.
	 * @param maxBufferSize The buffer is doubled as needed until it exceeds maxBufferSize and an exception is thrown. */
	public void setBuffer (byte[] buffer, int maxBufferSize) {
		if (buffer == null) throw new IllegalArgumentException("buffer cannot be null.");
		if (maxBufferSize < -1) throw new IllegalArgumentException("maxBufferSize cannot be < -1: " + maxBufferSize);
		this.buffer = buffer;
		this.maxCapacity = maxBufferSize == -1 ? Integer.MAX_VALUE : maxBufferSize;
		capacity = buffer.length;
		position = 0;
		total = 0;
		outputStream = null;
	}

	/** Returns the buffer. The bytes between zero and {@link #position()} are the data that has been written. */
	public byte[] getBuffer () {
		return buffer;
	}

	/** Returns a new byte array containing the bytes currently in the buffer between zero and {@link #position()}. */
	public byte[] toBytes () {
		byte[] newBuffer = new byte[position];
		System.arraycopy(buffer, 0, newBuffer, 0, position);
		return newBuffer;
	}

	/** Returns the current position in the buffer. This is the number of bytes that have not been flushed. */
	public int position () {
		return position;
	}

	/** Sets the current position in the buffer. */
	public void setPosition (int position) {
		this.position = position;
	}

	/** Returns the total number of bytes written. This may include bytes that have not been flushed. */
	public int total () {
		return total + position;
	}

	/** Sets the position and total to zero. */
	public void clear () {
		position = 0;
		total = 0;
	}

	/** @return true if the buffer has been resized. */
	private boolean require (int required) throws KryoException {
		if (capacity - position >= required) return false;
		if (required > maxCapacity)
			throw new KryoException("Buffer overflow. Max capacity: " + maxCapacity + ", required: " + required);
		flush();
		while (capacity - position < required) {
			if (capacity == maxCapacity)
				throw new KryoException("Buffer overflow. Available: " + (capacity - position) + ", required: " + required);
			// Grow buffer.
			capacity = Math.min(capacity * 2, maxCapacity);
			if (capacity < 0) capacity = maxCapacity;
			byte[] newBuffer = new byte[capacity];
			System.arraycopy(buffer, 0, newBuffer, 0, position);
			buffer = newBuffer;
		}
		return true;
	}

	// OutputStream

	/** Writes the buffered bytes to the underlying OutputStream, if any. */
	public void flush () throws KryoException {
		if (outputStream == null) return;
		try {
			outputStream.write(buffer, 0, position);
		} catch (IOException ex) {
			throw new KryoException(ex);
		}
		total += position;
		position = 0;
	}

	/** Flushes any buffered bytes and closes the underlying OutputStream, if any. */
	public void close () throws KryoException {
		flush();
		if (outputStream != null) {
			try {
				outputStream.close();
			} catch (IOException ignored) {
			}
		}
	}

	/** Writes a byte. */
	public void write (int value) throws KryoException {
		if (position == capacity) require(1);
		buffer[position++] = (byte)value;
	}

	/** Writes the bytes. Note the byte[] length is not written. */
	public void write (byte[] bytes) throws KryoException {
		if (bytes == null) throw new IllegalArgumentException("bytes cannot be null.");
		writeBytes(bytes, 0, bytes.length);
	}

	/** Writes the bytes. Note the byte[] length is not written. */
	public void write (byte[] bytes, int offset, int length) throws KryoException {
		writeBytes(bytes, offset, length);
	}

	// byte

	public void writeByte (byte value) throws KryoException {
		if (position == capacity) require(1);
		buffer[position++] = value;
	}

	public void writeByte (int value) throws KryoException {
		if (position == capacity) require(1);
		buffer[position++] = (byte)value;
	}

	/** Writes the bytes. Note the byte[] length is not written. */
	public void writeBytes (byte[] bytes) throws KryoException {
		if (bytes == null) throw new IllegalArgumentException("bytes cannot be null.");
		writeBytes(bytes, 0, bytes.length);
	}

	/** Writes the bytes. Note the byte[] length is not written. */
	public void writeBytes (byte[] bytes, int offset, int count) throws KryoException {
		if (bytes == null) throw new IllegalArgumentException("bytes cannot be null.");
		int copyCount = Math.min(capacity - position, count);
		while (true) {
			System.arraycopy(bytes, offset, buffer, position, copyCount);
			position += copyCount;
			count -= copyCount;
			if (count == 0) return;
			offset += copyCount;
			copyCount = Math.min(capacity, count);
			require(copyCount);
		}
	}

	// int

	/** Writes a 4 byte int. */
	public void writeInt (int value) throws KryoException {
		require(4);
		byte[] buffer = this.buffer;
		buffer[position++] = (byte)(value >> 24);
		buffer[position++] = (byte)(value >> 16);
		buffer[position++] = (byte)(value >> 8);
		buffer[position++] = (byte)value;
	}

	/** Writes a 1-5 byte int.
	 * @param optimizePositive If true, small positive numbers will be more efficient (1 byte) and small negative numbers will be
	 *           inefficient (5 bytes). */
	public int writeInt (int value, boolean optimizePositive) throws KryoException {
		if (!optimizePositive) value = (value << 1) ^ (value >> 31);
		if ((value & ~0x7F) == 0) {
			require(1);
			buffer[position++] = (byte)value;
			return 1;
		} else if ((value >>> 5 & ~0xFF) == 0) {
			require(2);
			buffer[position++] = (byte)(value & 0x1F | 0x80); // take 1st 5 bits, set bit 8
			buffer[position++] = (byte)(value >>> 5);
			return 2;
		} else if ((value >>> 13 & ~0xFF) == 0) {
			require(3);
			byte[] buffer = this.buffer;
			int position = this.position;
			buffer[position++] = (byte)(value & 0x1F | (1 << 5) | 0x80); // 1st 5 bits, set bits 6,7 to adtl bytes - 1, set bit 8
			buffer[position++] = (byte)(value >>> 5);
			buffer[position++] = (byte)(value >>> 13);
			this.position = position;
			return 3;
		} else if ((value >>> 21 & ~0xFF) == 0) {
			require(4);
			byte[] buffer = this.buffer;
			int position = this.position;
			buffer[position++] = (byte)(value & 0x1F | (2 << 5) | 0x80);
			buffer[position++] = (byte)(value >>> 5);
			buffer[position++] = (byte)(value >>> 13);
			buffer[position++] = (byte)(value >>> 21);
			this.position = position;
			return 4;
		}
		require(5);
		byte[] buffer = this.buffer;
		int position = this.position;
		buffer[position++] = (byte)(value & 0x1F | (3 << 5) | 0x80);
		buffer[position++] = (byte)(value >>> 5);
		buffer[position++] = (byte)(value >>> 13);
		buffer[position++] = (byte)(value >>> 21);
		buffer[position++] = (byte)(value >>> 29);
		this.position = position;
		return 5;
	}

	// string

	/** Writes the length and string using UTF8, or null.
	 * @param value May be null. */
	public void writeString (String value) throws KryoException {
		if (value == null) {
			writeByte(1 << 7);
			return;
		}
		int charCount = value.length();
		if (charCount == 0) {
			writeByte(1 | 1 << 7);
			return;
		}
		// Detect ASCII.
		boolean ascii = false;
		if (charCount > 1 && charCount < 64) {
			ascii = true;
			for (int i = 0; i < charCount; i++) {
				int c = value.charAt(i);
				if (c > 127) {
					ascii = false;
					break;
				}
			}
		}
		if (ascii) {
			if (capacity - position < charCount)
				writeAscii_slow(value, charCount);
			else {
				value.getBytes(0, charCount, buffer, position);
				position += charCount;
			}
			buffer[position - 1] |= 0x80;
		} else {
			writeStringLength(charCount + 1);
			int charIndex = 0;
			if (capacity - position >= charCount) {
				// Try to write 8 bit chars.
				byte[] buffer = this.buffer;
				int position = this.position;
				for (; charIndex < charCount; charIndex++) {
					int c = value.charAt(charIndex);
					if (c > 127) break;
					buffer[position++] = (byte)c;
				}
				this.position = position;
			}
			if (charIndex < charCount) writeString_slow(value, charCount, charIndex);
		}
	}

	private void writeStringLength (int value) {
		if (value < 1 << 6) {
			require(1);
			buffer[position++] = (byte)(value | 1 << 7); // set bit 8
		} else if (value < 1 << 13) {
			require(2);
			buffer[position++] = (byte)(value | 1 << 6 | 1 << 7); // set bit 7 and 8
			buffer[position++] = (byte)(value >>> 6);
		} else if (value < 1 << 21) {
			require(3);
			buffer[position++] = (byte)(value | 1 << 6 | 1 << 7); // set bit 7 and 8
			buffer[position++] = (byte)((value >>> 6) | 1 << 7); // set bit 8
			buffer[position++] = (byte)(value >>> 13);
		} else
			throw new KryoException("String too long, must be less than " + (1 << 21) + " code points: " + value);
	}

	private void writeString_slow (String value, int charCount, int charIndex) {
		for (; charIndex < charCount; charIndex++) {
			if (position == capacity) require(Math.min(capacity, charCount - charIndex));
			int c = value.charAt(charIndex);
			if (c <= 0x007F) {
				buffer[position++] = (byte)c;
			} else if (c > 0x07FF) {
				buffer[position++] = (byte)(0xE0 | c >> 12 & 0x0F);
				require(2);
				buffer[position++] = (byte)(0x80 | c >> 6 & 0x3F);
				buffer[position++] = (byte)(0x80 | c & 0x3F);
			} else {
				buffer[position++] = (byte)(0xC0 | c >> 6 & 0x1F);
				require(1);
				buffer[position++] = (byte)(0x80 | c & 0x3F);
			}
		}
	}

	private void writeAscii_slow (String value, int charCount) throws KryoException {
		byte[] buffer = this.buffer;
		int charIndex = 0;
		int charsToWrite = Math.min(charCount, capacity - position);
		while (charIndex < charCount) {
			value.getBytes(charIndex, charIndex + charsToWrite, buffer, position);
			charIndex += charsToWrite;
			position += charsToWrite;
			charsToWrite = Math.min(charCount - charIndex, capacity);
			if (require(charsToWrite)) buffer = this.buffer;
		}
	}

	// float

	/** Writes a 4 byte float. */
	public void writeFloat (float value) throws KryoException {
		writeInt(Float.floatToIntBits(value));
	}

	/** Writes a 1-5 byte float with reduced precision.
	 * @param optimizePositive If true, small positive numbers will be more efficient (1 byte) and small negative numbers will be
	 *           inefficient (5 bytes). */
	public int writeFloat (float value, float precision, boolean optimizePositive) throws KryoException {
		return writeInt((int)(value * precision), optimizePositive);
	}

	// short

	/** Writes a 2 byte short. */
	public void writeShort (int value) throws KryoException {
		require(2);
		buffer[position++] = (byte)(value >>> 8);
		buffer[position++] = (byte)value;
	}

	/** Writes a 1-3 byte short.
	 * @param optimizePositive If true, small positive numbers will be more efficient (1 byte) and small negative numbers will be
	 *           inefficient (3 bytes). */
	public int writeShort (int value, boolean optimizePositive) throws KryoException {
		if (optimizePositive) {
			if (value >= 0 && value <= 254) {
				require(1);
				buffer[position++] = (byte)value;
				return 1;
			}
			require(3);
			buffer[position++] = -1; // short positive
		} else {
			if (value >= -127 && value <= 127) {
				require(1);
				buffer[position++] = (byte)value;
				return 1;
			}
			require(3);
			buffer[position++] = -128; // short
		}
		buffer[position++] = (byte)(value >>> 8);
		buffer[position++] = (byte)value;
		return 3;
	}

	// long

	/** Writes an 8 byte long. */
	public void writeLong (long value) throws KryoException {
		require(8);
		byte[] buffer = this.buffer;
		buffer[position++] = (byte)(value >>> 56);
		buffer[position++] = (byte)(value >>> 48);
		buffer[position++] = (byte)(value >>> 40);
		buffer[position++] = (byte)(value >>> 32);
		buffer[position++] = (byte)(value >>> 24);
		buffer[position++] = (byte)(value >>> 16);
		buffer[position++] = (byte)(value >>> 8);
		buffer[position++] = (byte)value;
	}

	/** Writes a 1-9 byte long.
	 * @param optimizePositive If true, small positive numbers will be more efficient (1 byte) and small negative numbers will be
	 *           inefficient (9 bytes). */
	public int writeLong (long value, boolean optimizePositive) throws KryoException {
		if (!optimizePositive) value = (value << 1) ^ (value >> 63);
		if ((value & ~0x7F) == 0) {
			require(1);
			buffer[position++] = (byte)value;
			return 1;
		} else if ((value >>> 4 & ~0xFF) == 0) {
			require(2);
			buffer[position++] = (byte)(value & 0xF | 0x80); // take 1st 4 bits, set bit 8
			buffer[position++] = (byte)(value >>> 4);
			return 2;
		} else if ((value >>> 12 & ~0xFF) == 0) {
			require(3);
			byte[] buffer = this.buffer;
			int position = this.position;
			buffer[position++] = (byte)(value & 0xF | (1 << 4) | 0x80); // 1st 4 bits, set bits 5,6,7 to adtl bytes - 1, set bit 8
			buffer[position++] = (byte)(value >>> 4);
			buffer[position++] = (byte)(value >>> 12);
			this.position = position;
			return 3;
		} else if ((value >>> 20 & ~0xFF) == 0) {
			require(4);
			byte[] buffer = this.buffer;
			int position = this.position;
			buffer[position++] = (byte)(value & 0xF | (2 << 4) | 0x80);
			buffer[position++] = (byte)(value >>> 4);
			buffer[position++] = (byte)(value >>> 12);
			buffer[position++] = (byte)(value >>> 20);
			this.position = position;
			return 4;
		} else if ((value >>> 28 & ~0xFF) == 0) {
			require(5);
			byte[] buffer = this.buffer;
			int position = this.position;
			buffer[position++] = (byte)(value & 0xF | (3 << 4) | 0x80);
			buffer[position++] = (byte)(value >>> 4);
			buffer[position++] = (byte)(value >>> 12);
			buffer[position++] = (byte)(value >>> 20);
			buffer[position++] = (byte)(value >>> 28);
			this.position = position;
			return 5;
		} else if ((value >>> 36 & ~0xFF) == 0) {
			require(6);
			byte[] buffer = this.buffer;
			int position = this.position;
			buffer[position++] = (byte)(value & 0xF | (4 << 4) | 0x80);
			buffer[position++] = (byte)(value >>> 4);
			buffer[position++] = (byte)(value >>> 12);
			buffer[position++] = (byte)(value >>> 20);
			buffer[position++] = (byte)(value >>> 28);
			buffer[position++] = (byte)(value >>> 36);
			this.position = position;
			return 6;
		} else if ((value >>> 44 & ~0xFF) == 0) {
			require(7);
			byte[] buffer = this.buffer;
			int position = this.position;
			buffer[position++] = (byte)(value & 0xF | (5 << 4) | 0x80);
			buffer[position++] = (byte)(value >>> 4);
			buffer[position++] = (byte)(value >>> 12);
			buffer[position++] = (byte)(value >>> 20);
			buffer[position++] = (byte)(value >>> 28);
			buffer[position++] = (byte)(value >>> 36);
			buffer[position++] = (byte)(value >>> 44);
			this.position = position;
			return 7;
		} else if ((value >>> 52 & ~0xFF) == 0) {
			require(8);
			byte[] buffer = this.buffer;
			int position = this.position;
			buffer[position++] = (byte)(value & 0xF | (6 << 4) | 0x80);
			buffer[position++] = (byte)(value >>> 4);
			buffer[position++] = (byte)(value >>> 12);
			buffer[position++] = (byte)(value >>> 20);
			buffer[position++] = (byte)(value >>> 28);
			buffer[position++] = (byte)(value >>> 36);
			buffer[position++] = (byte)(value >>> 44);
			buffer[position++] = (byte)(value >>> 52);
			this.position = position;
			return 8;
		}
		require(9);
		byte[] buffer = this.buffer;
		int position = this.position;
		buffer[position++] = (byte)(value & 0xF | (7 << 4) | 0x80);
		buffer[position++] = (byte)(value >>> 4);
		buffer[position++] = (byte)(value >>> 12);
		buffer[position++] = (byte)(value >>> 20);
		buffer[position++] = (byte)(value >>> 28);
		buffer[position++] = (byte)(value >>> 36);
		buffer[position++] = (byte)(value >>> 44);
		buffer[position++] = (byte)(value >>> 52);
		buffer[position++] = (byte)(value >>> 60);
		this.position = position;
		return 9;
	}

	// boolean

	/** Writes a 1 byte boolean. */
	public void writeBoolean (boolean value) throws KryoException {
		require(1);
		buffer[position++] = (byte)(value ? 1 : 0);
	}

	// char

	/** Writes a 2 byte char. */
	public void writeChar (char value) throws KryoException {
		require(2);
		buffer[position++] = (byte)(value >>> 8);
		buffer[position++] = (byte)value;
	}

	// double

	/** Writes an 8 byte double. */
	public void writeDouble (double value) throws KryoException {
		writeLong(Double.doubleToLongBits(value));
	}

	/** Writes a 1-9 byte double with reduced precision.
	 * @param optimizePositive If true, small positive numbers will be more efficient (1 byte) and small negative numbers will be
	 *           inefficient (9 bytes). */
	public int writeDouble (double value, double precision, boolean optimizePositive) throws KryoException {
		return writeLong((long)(value * precision), optimizePositive);
	}

	/** Returns the number of bytes that would be written with {@link #writeShort(int)}. */
	static public int shortLength (int value, boolean optimizePositive) {
		if (optimizePositive) {
			if (value >= 0 && value <= 254) return 1;
		} else {
			if (value >= -127 && value <= 127) return 1;
		}
		return 3;
	}

	/** Returns the number of bytes that would be written with {@link #writeInt(int, boolean)}. */
	static public int intLength (int value, boolean optimizePositive) {
		if (!optimizePositive) value = (value << 1) ^ (value >> 31);
		if ((value & ~0x7F) == 0) return 1;
		if ((value >>> 5 & ~0xFF) == 0) return 2;
		if ((value >>> 13 & ~0xFF) == 0) return 3;
		if ((value >>> 21 & ~0xFF) == 0) return 4;
		return 5;
	}

	/** Returns the number of bytes that would be written with {@link #writeLong(long, boolean)}. */
	static public int longLength (long value, boolean optimizePositive) {
		if (!optimizePositive) value = (value << 1) ^ (value >> 63);
		if ((value & ~0x7F) == 0) return 1;
		if ((value >>> 4 & ~0xFF) == 0) return 2;
		if ((value >>> 12 & ~0xFF) == 0) return 3;
		if ((value >>> 20 & ~0xFF) == 0) return 4;
		if ((value >>> 28 & ~0xFF) == 0) return 5;
		if ((value >>> 36 & ~0xFF) == 0) return 6;
		if ((value >>> 44 & ~0xFF) == 0) return 7;
		if ((value >>> 52 & ~0xFF) == 0) return 8;
		return 9;
	}
}