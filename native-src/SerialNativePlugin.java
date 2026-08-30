import com.fazecast.jSerialComm.SerialPort;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.word.WordFactory;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** GraalVM shared-library adapter for ZPEX. It deliberately has no dependency on ZPE. */
public final class SerialNativePlugin {
  private static final ObjectHandles HANDLES = ObjectHandles.getGlobal();
  private static final Pattern STRING_VALUE = Pattern.compile("\\\"value\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
  private static final Pattern NUMBER_VALUE = Pattern.compile("\\\"value\\\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)");
  private static CCharPointer descriptor = WordFactory.nullPointer();

  private static final String DESCRIPTOR = "{"
      + "\"abiVersion\":1,\"name\":\"libSerial\",\"version\":\"2.0\",\"functions\":["
      + "{\"name\":\"list_serial_ports\",\"parameters\":[],\"returnType\":\"list\"}],\"objects\":["
      + "{\"name\":\"SerialManager\",\"constructorParameters\":[],\"properties\":[],\"methods\":["
      + "{\"name\":\"refresh\",\"parameters\":[],\"returnType\":\"number\"},"
      + "{\"name\":\"port_count\",\"parameters\":[],\"returnType\":\"number\"},"
      + "{\"name\":\"get_port\",\"parameters\":[{\"name\":\"index\",\"type\":\"number\"}],\"returnType\":{\"type\":\"object\",\"objectType\":\"SerialPort\"}}]},"
      + "{\"name\":\"SerialPort\",\"constructorParameters\":[{\"name\":\"system_name\",\"type\":\"string\"}],\"properties\":["
      + "{\"name\":\"system_name\",\"type\":\"string\",\"readable\":true,\"writable\":false},"
      + "{\"name\":\"name\",\"type\":\"string\",\"readable\":true,\"writable\":false},"
      + "{\"name\":\"baud_rate\",\"type\":\"number\",\"readable\":true,\"writable\":true}],\"methods\":["
      + "{\"name\":\"get_name\",\"parameters\":[],\"returnType\":\"string\"},"
      + "{\"name\":\"is_open\",\"parameters\":[],\"returnType\":\"boolean\"},"
      + "{\"name\":\"open\",\"parameters\":[],\"returnType\":\"boolean\"},"
      + "{\"name\":\"close\",\"parameters\":[],\"returnType\":\"boolean\"},"
      + "{\"name\":\"bytes_available\",\"parameters\":[],\"returnType\":\"number\"},"
      + "{\"name\":\"write\",\"parameters\":[{\"name\":\"text\",\"type\":\"string\"}],\"returnType\":\"number\"},"
      + "{\"name\":\"read\",\"parameters\":[{\"name\":\"maximum_bytes\",\"type\":\"number\",\"optional\":true}],\"returnType\":\"string\"}]}]}";

  /** Required by some GraalVM development builds even when producing a shared library. */
  public static void main(String[] arguments) { }

  private static final class Manager {
    SerialPort[] ports = SerialPort.getCommPorts();
    int refresh() { ports = SerialPort.getCommPorts(); return ports.length; }
  }

  private static CCharPointer cString(String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    CCharPointer out = UnmanagedMemory.malloc(bytes.length + 1);
    for (int i = 0; i < bytes.length; i++) out.write(i, bytes[i]);
    out.write(bytes.length, (byte) 0);
    return out;
  }

  private static String javaString(CCharPointer value) {
    if (value.isNull()) return "";
    int length = 0;
    while (value.read(length) != 0) length++;
    byte[] bytes = new byte[length];
    for (int i = 0; i < length; i++) bytes[i] = value.read(i);
    return new String(bytes, StandardCharsets.UTF_8);
  }

  private static String escape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
  }

  private static CCharPointer value(String type, String jsonValue) {
    return cString("{\"kind\":\"value\",\"value\":{\"type\":\"" + type + "\",\"value\":" + jsonValue + "}}");
  }

  private static CCharPointer stringValue(String value) { return value("string", "\"" + escape(value) + "\""); }
  private static CCharPointer numberValue(long value) { return value("number", Long.toString(value)); }
  private static CCharPointer booleanValue(boolean value) { return value("boolean", Boolean.toString(value)); }
  private static CCharPointer objectValue(String type, long handle) { return cString("{\"kind\":\"object\",\"objectType\":\"" + type + "\",\"handle\":" + handle + "}"); }

  private static String firstString(String json) {
    Matcher matcher = STRING_VALUE.matcher(json);
    if (!matcher.find()) throw new IllegalArgumentException("A string argument is required.");
    return matcher.group(1).replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\");
  }

  private static long firstNumber(String json, long defaultValue) {
    Matcher matcher = NUMBER_VALUE.matcher(json);
    return matcher.find() ? (long) Double.parseDouble(matcher.group(1)) : defaultValue;
  }

  private static Object object(long handle) { return HANDLES.get(WordFactory.pointer(handle)); }
  private static long retain(Object value) { return HANDLES.create(value).rawValue(); }
  private static void fail(CCharPointerPointer error, Throwable throwable) {
    if (error.isNonNull()) error.write(cString(throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage()));
  }

  @CEntryPoint(name = "zpe_graal_plugin_abi_version")
  public static int abiVersion(IsolateThread thread) { return 1; }

  @CEntryPoint(name = "zpe_graal_plugin_descriptor")
  public static CCharPointer descriptor(IsolateThread thread) {
    if (descriptor.isNull()) descriptor = cString(DESCRIPTOR);
    return descriptor;
  }

  @CEntryPoint(name = "zpe_graal_plugin_create")
  public static long create(IsolateThread thread, CCharPointer type, CCharPointer arguments, CCharPointerPointer error) {
    try {
      String name = javaString(type);
      if ("SerialManager".equals(name)) return retain(new Manager());
      if ("SerialPort".equals(name)) return retain(SerialPort.getCommPort(firstString(javaString(arguments))));
      throw new IllegalArgumentException("Unknown serial object '" + name + "'.");
    } catch (Throwable throwable) { fail(error, throwable); return 0; }
  }

  @CEntryPoint(name = "zpe_graal_plugin_invoke")
  public static CCharPointer invoke(IsolateThread thread, long handle, CCharPointer type, CCharPointer method,
                                    CCharPointer arguments, CCharPointerPointer error) {
    try {
      String objectType = javaString(type), name = javaString(method), args = javaString(arguments);
      if ("SerialManager".equals(objectType)) {
        Manager manager = (Manager) object(handle);
        if ("refresh".equals(name)) return numberValue(manager.refresh());
        if ("port_count".equals(name)) return numberValue(manager.ports.length);
        if ("get_port".equals(name)) {
          int index = (int) firstNumber(args, -1);
          if (index < 0 || index >= manager.ports.length) throw new IndexOutOfBoundsException("Serial port index " + index + " is out of range.");
          return objectValue("SerialPort", retain(manager.ports[index]));
        }
      } else if ("SerialPort".equals(objectType)) {
        SerialPort port = (SerialPort) object(handle);
        if ("get_name".equals(name)) return stringValue(port.getDescriptivePortName());
        if ("is_open".equals(name)) return booleanValue(port.isOpen());
        if ("open".equals(name)) return booleanValue(port.openPort());
        if ("close".equals(name)) return booleanValue(port.closePort());
        if ("bytes_available".equals(name)) return numberValue(port.bytesAvailable());
        if ("write".equals(name)) {
          byte[] bytes = firstString(args).getBytes(StandardCharsets.UTF_8);
          return numberValue(port.writeBytes(bytes, bytes.length));
        }
        if ("read".equals(name)) {
          int available = Math.max(0, port.bytesAvailable());
          int maximum = (int) firstNumber(args, available);
          byte[] bytes = new byte[Math.min(available, Math.max(0, maximum))];
          int count = bytes.length == 0 ? 0 : port.readBytes(bytes, bytes.length);
          return stringValue(new String(bytes, 0, Math.max(0, count), StandardCharsets.UTF_8));
        }
      }
      throw new IllegalArgumentException("Unknown method '" + name + "' on " + objectType + ".");
    } catch (Throwable throwable) { fail(error, throwable); return WordFactory.nullPointer(); }
  }

  @CEntryPoint(name = "zpe_graal_plugin_invoke_function")
  public static CCharPointer invokeFunction(IsolateThread thread,CCharPointer function,CCharPointer arguments,CCharPointerPointer error){
    try{
      String name=javaString(function);
      if(!"list_serial_ports".equals(name))throw new IllegalArgumentException("Unknown function '"+name+"'.");
      SerialPort[] ports=SerialPort.getCommPorts();StringBuilder json=new StringBuilder("{\"kind\":\"value\",\"value\":{\"type\":\"list\",\"value\":[");
      for(int i=0;i<ports.length;i++){if(i>0)json.append(',');json.append("{\"type\":\"object\",\"objectType\":\"SerialPort\",\"handle\":").append(retain(ports[i])).append('}');}
      return cString(json.append("]}}").toString());
    }catch(Throwable throwable){fail(error,throwable);return WordFactory.nullPointer();}
  }

  @CEntryPoint(name = "zpe_graal_plugin_get_property")
  public static CCharPointer getProperty(IsolateThread thread, long handle, CCharPointer type, CCharPointer property,
                                         CCharPointerPointer error) {
    try {
      if (!"SerialPort".equals(javaString(type))) throw new IllegalArgumentException("This object has no properties.");
      SerialPort port = (SerialPort) object(handle);
      switch (javaString(property)) {
        case "system_name": return stringValue(port.getSystemPortName());
        case "name": return stringValue(port.getDescriptivePortName());
        case "open": return booleanValue(port.isOpen());
        case "baud_rate": return numberValue(port.getBaudRate());
        default: throw new IllegalArgumentException("Unknown serial-port property.");
      }
    } catch (Throwable throwable) { fail(error, throwable); return WordFactory.nullPointer(); }
  }

  @CEntryPoint(name = "zpe_graal_plugin_set_property")
  public static int setProperty(IsolateThread thread, long handle, CCharPointer type, CCharPointer property,
                                CCharPointer value, CCharPointerPointer error) {
    try {
      if (!"SerialPort".equals(javaString(type)) || !"baud_rate".equals(javaString(property)))
        throw new IllegalArgumentException("Only SerialPort.baud_rate is writable.");
      ((SerialPort) object(handle)).setBaudRate((int) firstNumber(javaString(value), 9600));
      return 0;
    } catch (Throwable throwable) { fail(error, throwable); return 1; }
  }

  @CEntryPoint(name = "zpe_graal_plugin_destroy")
  public static void destroy(IsolateThread thread, long handle, CCharPointer type) {
    ObjectHandle objectHandle = WordFactory.pointer(handle);
    Object value = HANDLES.get(objectHandle);
    if (value instanceof SerialPort && ((SerialPort) value).isOpen()) ((SerialPort) value).closePort();
    HANDLES.destroy(objectHandle);
  }

  @CEntryPoint(name = "zpe_graal_plugin_free_string")
  public static void freeString(IsolateThread thread, CCharPointer value) { if (value.isNonNull()) UnmanagedMemory.free(value); }
}
