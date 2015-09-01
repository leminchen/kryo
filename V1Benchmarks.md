The results below were obtained using the [thrift-protobuf-compare project](http://code.google.com/p/thrift-protobuf-compare/). There you will find the source for the benchmarks used to generate the charts displayed here. The project also has a [benchmarking page](http://code.google.com/p/thrift-protobuf-compare/wiki/Benchmarking) that compares 20+ Java serialization libraries, including Kryo.

Besides the benchmark project linked above, a project called [memcached-session-manager](http://code.google.com/p/memcached-session-manager/) has done some [additional benchmarking](http://code.google.com/p/memcached-session-manager/wiki/SerializationStrategyBenchmark).

## Protobuf ##

Google's [protobuf project](http://code.google.com/p/protobuf/) beats out most other Java serialization libraries, so is a good baseline to compare against:

<table><tr>
<td><img src='http://kryo.googlecode.com/svn/wiki/benchmarks/images/size-proto.png' /></td>
<td><img src='http://kryo.googlecode.com/svn/wiki/benchmarks/images/rtt-proto.png' /></td>
</tr></table>

The bars labeled "kryo" represent out of the box serialization. The classes are registered with no optimizations.

The bars labeled "kryo optimized" represent the classes registered with optimizations such as letting Kryo know which fields will never be null and what type of elements will be in a list.

The bars labeled "kryo compressed" represent the same as "kryo optimized" but with [deflate compression](http://en.wikipedia.org/wiki/DEFLATE_%28algorithm%29). The compression has a small performance hit to decode and a large performance hit to encode, but may make sense when space or bandwidth is a concern. The round trip time with compression is shown below.

Although Kryo is doing everything at runtime with no schema and protobuf uses precompiled classes generated from a schema, Kryo puts up a fight. The two projects are basically tied in serialization size and protobuf just squeaks ahead in serialization speed.

## Protobuf differences ##

There are other differences between the projects that should be considered.

Protobuf requires a `.proto` file to be written that describes the data structures. With Kryo, the classes to be serialized only need to be registered at runtime.

The `.proto` file is compiled into Java, C++, or Python code. Third party add-ons exist to use protobuf with many other languages. Kryo is designed only to be compatible with Java and provides no interoperability with other languages.

The protobuf compiler produces builders and immutable message classes. From the protobuf documentation:
> _Protocol buffer classes are basically dumb data holders (like structs in C++); they don't make good first class citizens in an object model. If you want to add richer behavior to a generated class, the best way to do this is to wrap the generated protocol buffer class in an application-specific class._

With Kryo, instances of any class can be serialized, even classes from third parties where you do not control the source.

Protobuf supports limited changing of the `.proto` data structure definition without breaking compatibility with previously serialized objects or previously generated message classes. With Kryo, the class definition during deserialization must be identical to when the class was serialized. In the future Kryo may support optional forward and/or backward compatibility.

## Java serialization ##

Java's built-in serialization is slow, inefficient, and has many well-known problems (see Effective Java, by Josh Bloch pp. 213).

![http://kryo.googlecode.com/svn/wiki/benchmarks/images/size-java.png](http://kryo.googlecode.com/svn/wiki/benchmarks/images/size-java.png)

![http://kryo.googlecode.com/svn/wiki/benchmarks/images/rtt-java.png](http://kryo.googlecode.com/svn/wiki/benchmarks/images/rtt-java.png)