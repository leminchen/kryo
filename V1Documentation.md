Note this documentation is for an old version of Kryo which has been retired to "branches" in SVN. See the [home page](http://code.google.com/p/kryo) for the latest documentaiton.

Please use the [Kryo discussion group](http://groups.google.com/group/kryo-users) for support.

## Overview ##

Kryo is a fast and efficient object graph serialization framework for Java. The goals of the project are speed, efficiency, and an easy to use API. The project is useful any time objects need to be persisted, whether to a file, database, or over the network.

Kryo is competitive with other serialization projects. See V1Benchmarks for charts and more.

If you are planning to use Kryo for network communication, the [KryoNet](http://code.google.com/p/kryonet/) project may prove useful.


## Quickstart ##

Jumping way ahead to show how the library is used:

```
Kryo kryo = new Kryo();
kryo.register(SomeClass.class);
// ...
SomeClass someObject = new SomeClass(...);
kryo.writeObject(buffer, someObject);
// ...
SomeClass someObject = kryo.readObject(buffer, SomeClass.class);
```

First the classes to serialize are registered, then objects can be written and read. No interfaces, mapping files, or other actions beyond registration are needed to serialize objects.

The rest of this document details how this works and advanced usage of the library.


## Serializers ##

The Serializer interface defines methods to read and write objects to and from bytes. Serializer implementations are included for all types of objects: primitives, arrays, Strings, enums, collections, maps, beans, POJOs, and more.

The Serializer methods writeObject/readObject are used to read and write objects that may be null. First a byte is written to indicate if the object is null, then the object's bytes are written.

If it is known that an object is not null, the writeObjectData/readObjectData methods can be used. These are the methods that actually do the work of converting the object to and from bytes.

Serializers do their work with java.nio.ByteBuffers:

```
ByteBuffer buffer = ByteBuffer.allocateDirect(256);
StringSerializer stringSerializer = new StringSerializer();
stringSerializer.writeObjectData(buffer, "some text");
buffer.flip();
String text = stringSerializer.readObjectData(buffer, String.class);
```

Most simple serializers provide static methods:

```
StringSerializer.put(buffer, "some text");
buffer.flip();
String text = StringSerializer.get(buffer);
```

More complex serializers allow extra information to be provided so that the number of bytes output can be reduced:

```
LinkedList linkedList = new LinkedList();
linkedList.add("thishitis");
linkedList.add("bananas");
// ...
CollectionSerializer serializer = new CollectionSerializer(kryo);
serializer.setElementClass(String.class);
serializer.setElementsCanBeNull(false);
serializer.writeObject(buffer, linkedList);
// ...
LinkedList linkedList = serializer.readObject(buffer, LinkedList.class);
```

In this example, the CollectionSerializer is told that every element in the LinkedList is a String, and that none of the elements are ever going to be null. This allows the serializer to be more efficient. In this case, 2 to 3 bytes are saved per element in the list.

## Automatic serialization ##

Manually using serializers is useful, but tedious. The Kryo instance can be used to automatically read and write objects in a few different ways.

The writeObject/readObject and writeObjectData/readObjectData methods work the same as the Serializer methods:

```
// Used when the object may be null:
kryo.writeObject(buffer, someObject);
// ...
SomeClass someObject = kryo.readObject(buffer, SomeClass.class);

// Used when the object is known not to be null:
kryo.writeObjectData(buffer, someObject);
// ...
SomeClass someObject = kryo.readObjectData(buffer, SomeClass.class);
```

Additional methods are provided for when the class of the object being written is not known when it is deserialized:

```
// Used when the class is not known at deserialization time (the object may be null):
kryo.writeClassAndObject(buffer, someObject);
// ...
Object object = kryo.readClassAndObject(buffer);
if (object instanceof SomeClass) {
   // ...
}
```


## Registration ##

For Kryo to automatically serialize objects, it must know what serializer to use. Classes to be serialized can be registered with the Kryo instance and a Serializer can be specified:

```
Kryo kryo = new Kryo();
kryo.register(HashMap.class, new MapSerializer(kryo));
kryo.register(SomeClass.class, new FieldSerializer(kryo, SomeClass.class));
```

If register is called with no serializer, one is automatically chosen by `Kryo.newSerializer`, according to this table:

| **Type** | **Serializer** |
|:---------|:---------------|
| array    | ArraySerializer |
| Enum     | EnumSerializer |
| Collection | CollectionSerializer |
| Map      | MapSerializer  |
| CustomSerialization | CustomSerializer |
| any other class | FieldSerializer |

Most classes will use FieldSerializer. This is convenient because it does not require classes to implement an interface to be serialized. FieldSerializer does direct assignment to the object's fields. If the fields are public, protected, or default access (package private), bytecode generation is used for maximum speed (see [ReflectASM](http://code.google.com/p/reflectasm/)). For private fields, setAccessible and cached reflection is used, which is still quite fast.

FieldSerializer can handle any acyclic object graph. For graphs with many of the same object or with circular references, ReferenceFieldSerializer can be used.

Additional serializers are available in a separate project on github, [kryo-serializers](https://github.com/magro/kryo-serializers). This project contains many serializers that may not make sense to include in the core of Kryo. It also has a KryoReflectionFactorySupport class that uses
Sun's ReflectionFactory to create new instances of classes without a default constructor (tied to Sun JVMs of course). This is the same extralinguistic mechanism to create objects that is used by the built-in Java serialization.

## Registered IDs ##

When a class is registered, it is assigned an ordinal number. This integer is used in the serialized bytes to identify what class to instantiate when the object is deserialized. By using an integer, a class can be represented very efficiently, usually with just a byte. The downside is that the ordinals must be identical when the class is deserialized. To do this, the exact same classes must be registered in the exact same order when the object is deserialized.

By default, all primitives (including wrappers) and java.lang.String are registered. Any other class, including JDK classes like ArrayList and even arrays such as `String[]` or `int[]` must be registered to receive an ordinal:

```
Kryo kryo = new Kryo();
kryo.register(ArrayList.class);
kryo.register(String[].class);
kryo.register(int[].class);
kryo.register(int[][].class);
```


## Unregistered classes ##

If it is known up front what classes need to be serialized, registering the classes is ideal. However, in some cases the classes to serialize are not known until it is time to perform the serialization. Kryo has a setting to handle this case:

```
Kryo kryo = new Kryo();
kryo.setRegistrationOptional(true);
// ...
kryo.writeObject(buffer, someObject);
```

When `setRegistrationOptional` is true, registered classes are still written as an integer. However, unregistered classes are written as a String, using the name of the class. This is much less efficient, but can't always be avoided.

The `Kryo.setSerializer` method can be used to set the serializer for an unregistered class. However, this must be called any time before an instance of the class is serialized **or** deserialized. Otherwise, `Kryo.newSerializer` will determine the serializer to use. This method can be overridden if needed. Note that a class can be annotated with DefaultSerializer to customize the serializer returned by `newSerializer`.


## Custom serializers ##

While the serializers shipped with Kryo can serialize any object, sometimes it makes sense to write your own. For example, FieldSerializer can't be used for java.awt.Color because it has no public zero argument constructor. Even if it did, the class has a number of non-transient fields which would be serialized unnecessarily. In this case, it is much more efficient to write a custom serializer:

```
kryo.register(Color.class, new Serializer() {
	public void writeObjectData (ByteBuffer buffer, Object object) {
		buffer.putInt(((Color)object).getRGB());
	}
	public Color readObjectData (ByteBuffer buffer, Class type) {
		return new Color(buffer.getInt());
	}
});
```

SimpleSerializer can be used in simple cases for slightly cleaner code:

```
kryo.register(Color.class, new SimpleSerializer<Color>() {
	public void write (ByteBuffer buffer, Color color) {
		buffer.putInt(color.getRGB());
	}
	public Color read (ByteBuffer buffer) {
		return new Color(buffer.getInt());
	}
});
```

Either of the above serializes a java.awt.Color instance using just one integer (4 bytes).

Another way to manually serialize objects is to implement the CustomSerialization interface. This allows an object to define for itself how it is written to and from bytes:

```
class SomeClass implements CustomSerialization {			
	public int value;
	public String name;

	public void readObjectData (Kryo kryo, ByteBuffer buffer) {
		StringSerializer.put(buffer, name);
		IntSerializer.put(buffer, value, true);
	}

	public void writeObjectData (Kryo kryo, ByteBuffer buffer) {
		name = StringSerializer.get(buffer);
		value = IntSerializer.get(buffer, true);
	}
}
// ...
kryo.writeObject(buffer, someObject);
// ...
SomeClass someObject = kryo.readObject(buffer, SomeClass.class);
```

It is common during custom serialization to make use of various serializers in addition to writing to the ByteBuffer directly.

## Java's built-in serialization ##

You may note that java.awt.Color implements Serializable, however Java's built-in serialization is very inefficient and is not recommended. That said, it can still be used in this way:

```
kryo.register(Color.class, new SerializableSerializer());
```

This serializes java.awt.Color using 170 bytes (versus only 4 bytes as shown above).


## Byte arrays and streams ##

ObjectBuffer is a convenience class that can be used to write objects to and from byte arrays and streams. Often it is desirable to work with these rather than a java.nio.ByteBuffer.

```
ObjectBuffer buffer = new ObjectBuffer(kryo);
buffer.writeObject(new FileOutputStream("object.bin"), someObject);
someObject = buffer.readObject(new FileInputStream("object.bin"), SomeClass.class);
byte[] bytes = buffer.writeObject(someObject);
someObject = buffer.readObject(bytes);
```

Internally ObjectBuffer uses its own buffer, so it cannot be shared by multiple threads.

ObjectBuffer provides [constructors](http://kryo.googlecode.com/svn/api/v1/com/esotericsoftware/kryo/ObjectBuffer.html#ObjectBuffer(com.esotericsoftware.kryo.Kryo,%20int,%20int)) to control the initial and maximum size of the internal buffer. The buffer must be large enough to contain all the bytes for the object graph being serialized or deserialized. The size of the buffer is doubled until it exceeds the maximum size, in which case a SerializationException is thrown.


## Compression ##

Serialized objects can be compressed or otherwise encoded. The Compressor class wraps a serializer and can modify the bytes after they are serialized and before they are deserialized. The compressor is specified instead of the serializer when the class is registered:

```
kryo.register(SomeObject.class, new DeflateCompressor(new FieldSerializer(kryo, SomeObject.class)));
```

In this example, the data will be compressed and decompressed with the [deflate algorithm](http://en.wikipedia.org/wiki/Deflate).

Kryo comes with base compressor classes to deal with the serialized data as a byte buffer, byte array, or stream.

Kryo also comes with DeltaCompressor, which caches bytes for objects that were serialized for a specific receiver. Subsequent serialization for the same receiver results only in bytes that describe the delta from the last serialization. This can greatly reduce the number of bytes needed to serialize an object that seldom changes dramatically.


## Context ##

Serializers do not directly keep state about a particular serialization. This allows serializer instances to be used concurrently by multiple threads. Some serializers (eg, most compressors) do need some to keep some state, such as a temporary buffer. To do so, Kryo provides the Context class:

```
public void writeObjectData (ByteBuffer buffer, Object object) {
	Context context = Kryo.getContext();
	ByteBuffer tempBuffer = context.getBuffer(2048);
	// Use temporary buffer to perform serialization.
}
```

The `Kryo.getContext` method provides a thread local Context instance. Context has convenience methods for obtaining temporary buffers. This allows serializers to use a temporary buffer in a thread safe manner and to share temporary buffers to avoid unnecessary memory allocation. Context also allows arbitrary data to be stored:

```
public void writeObjectData (ByteBuffer buffer, Object object) {
	Context context = Kryo.getContext();
	Cipher encrypt = (Cipher)context.get(this, "encryptCipher");
	if (encrypt == null) {
		encrypt = Cipher.getInstance("Blowfish");
		encrypt.init(Cipher.ENCRYPT_MODE, keySpec);
		context.put(this, "encryptCipher", encrypt);
	}
	encrypt.doFinal(inputBuffer, outputBuffer);
}
```

By keeping state in the context, the serializer remains thread safe. If the Cipher were simply stored in a private field, two threads could be inside of the Cipher's `doFinal` method, which would corrupt the Cipher's state and cause a catastrophe.

When writing serializers for single threaded usage of Kryo, there is no need to use contexts. In a multithreaded environment, such as servlets, any state needed during serialization should be stored in a context. All serializers provided by Kryo and the Kryo class itself are thread safe.

## Remote Entities ##

Some serializers are interested in the entity that will consume a serialized object or the entity that produced a serialized object. For example, DeltaCompressor is used with the [KryoNet](http://code.google.com/p/kryonet/) networking library to only send bytes that differ from the last send. The serializer needs to know what client is going to receive the bytes so it can know the basis for the delta. This can be handled by setting the remote entity ID on the context before serialization:

```
Context context = Kryo.getContext();
context.setRemoteEntityID(client.getID());
kryo.writeClassAndObject(buffer, object);
```

DeltaCompressor uses the last bytes sent to the specified remote entity ID to perform the delta.

In addition to the remote entity ID, Context's `put` and `get` methods allow any contextual data to be provided to serializers.

## Logging ##

Kryo makes use of the low overhead, lightweight [MinLog logging library](http://code.google.com/p/minlog/). The logging level can be set in this way:

```
Log.set(LEVEL_TRACE);
```

Kryo does minimal logging at INFO and above levels. DEBUG is good to use during development. TRACE is good to use when debugging a specific problem, but outputs too much information to leave on all the time.

MinLog supports a fixed logging level, which will remove logging statements below that level. For maximum efficiency, Kryo can be compiled with a fixed logging level MinLog JAR. See [MinLog](http://code.google.com/p/minlog/) for more information.