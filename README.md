# zpe.lib.serial

The official serial-port plugin for ZPE and ZPEX, using
[jSerialComm](https://fazecast.github.io/jSerialComm/) internally.

The project uses jSerialComm 2.11.4, including its native Apple Silicon
library. Older releases such as 2.5.3 contain only Intel macOS binaries and
cannot be loaded by an ARM64 ZPEX plugin.

## ZPE installation

Build the existing JAR and install it into `plugins`. This retains the
classic `list_serial_ports()` API for the JVM version of ZPE.

## ZPEX native plugin

The repository also contains an independent GraalVM adapter. It compiles the
Java serial implementation and jSerialComm into a platform-specific shared
library, without embedding or linking the ZPE runtime itself.

Set `GRAAL_HOME` to a GraalVM containing Native Image, then run:

```console
./build-native.sh
```

If jSerialComm is stored elsewhere, set `JSERIALCOMM_JAR` to its full path.
The output is written beneath `build/native`. Install the resulting `.dylib`,
`.so` or `.dll` with:

```console
zpe --plugins add build/native/zpe.lib.serial.dylib
```

The native plugin exposes:

- `SerialManager`: `refresh()`, `port_count()` and `get_port(index)`;
- `SerialPort`: readable `system_name` and `name` properties;
- writable `baud_rate`;
- `open()`, `close()`, `bytes_available()`, `write(text)` and
  `read(maximum_bytes)` methods, plus the legacy `get_name()` and `is_open()`
  methods.

See `examples/native_serial.yas` for a discovery example. Hardware access may
still require the appropriate operating-system permissions.

Both builds are imported with `import "zpe.lib.serial"`. ZPE searches
`plugins` for `zpe.lib.serial.jar`; ZPEX searches only `native-plugins` for
`zpe.lib.serial.dylib` (or the matching Linux/Windows filename).

## Distribution package

Every push runs the GitHub Actions workflow and produces `zpe.lib.serial.zip`.
The archive contains the JVM plugin and the macOS ARM64, Windows x64 and Linux
x64 ZPEX plugins. The workflow uploads the archive as a build artifact and
deploys it with rsync when the `SFTP_DESTINATION_SERIAL` repository secret is
configured.

The native library is specific to an operating system and CPU architecture.
Build and distribute one copy for each supported target.

The Native Image build includes explicit JNI reachability metadata for
`SerialPort`. jSerialComm's native code locates that class, its constructor and
its fields through JNI at runtime; Native Image would otherwise remove the
lookup metadata even though the Java class itself remains reachable.
