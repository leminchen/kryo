If the version of Kryo's dependencies conflict with those in your project, the following Maven XML will create a "shaded" JAR. This renames the packages for all Kryo and dependency classes.

```
<dependency>
       <groupId>com.esotericsoftware.kryo</groupId>
       <artifactId>kryo</artifactId>
       <version>2.14</version>
       <classifier>shaded</classifier>
       <exclusions>
               <exclusion>
                       <groupId>com.esotericsoftware.reflectasm</groupId>
                       <artifactId>reflectasm</artifactId>
               </exclusion>
               <exclusion>
                       <groupId>com.esotericsoftware.minlog</groupId>
                       <artifactId>minlog</artifactId>
               </exclusion>
               <exclusion>
                       <groupId>org.objenesis</groupId>
                       <artifactId>objenesis</artifactId>
               </exclusion>
       </exclusions>
</dependency>
```