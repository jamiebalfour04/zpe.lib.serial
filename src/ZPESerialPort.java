import com.fazecast.jSerialComm.SerialPort;
import jamiebalfour.zpe.core.*;
import jamiebalfour.zpe.core.exceptions.ZPERuntimeException;
import jamiebalfour.zpe.core.interfaces.*;
import jamiebalfour.zpe.core.types.*;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;

/** Java implementation of the same SerialPort object exposed by the ZPEX build. */
public class ZPESerialPort extends ZPEStructure {
  private static final long serialVersionUID = -2658403322308184479L;
  SerialPort p;

  public ZPESerialPort(ZPERuntimeEnvironment runtime, ZPEPropertyWrapper parent, String name) {
    super(runtime, parent, name);
    addPropertyByName("system_name");
    addPropertyByName("name");
    addPropertyByName("baud_rate");
    addNativeMethod("get_name", method("get_name", new String[0], new String[0], YASSByteCodes.STRING_TYPE,
        parameters -> new ZPEString(p.getDescriptivePortName())));
    addNativeMethod("is_open", method("is_open", new String[0], new String[0], YASSByteCodes.BOOLEAN_TYPE,
        parameters -> new ZPEBoolean(p.isOpen())));
    addNativeMethod("open", method("open", new String[0], new String[0], YASSByteCodes.BOOLEAN_TYPE,
        parameters -> new ZPEBoolean(p.openPort())));
    addNativeMethod("close", method("close", new String[0], new String[0], YASSByteCodes.BOOLEAN_TYPE,
        parameters -> new ZPEBoolean(p.closePort())));
    addNativeMethod("bytes_available", method("bytes_available", new String[0], new String[0], YASSByteCodes.NUMBER_TYPE,
        parameters -> new ZPENumber(p.bytesAvailable())));
    addNativeMethod("write", method("write", new String[]{"text"}, new String[]{"string"}, YASSByteCodes.NUMBER_TYPE,
        parameters -> {
          ZPEType supplied = parameters.get("text");
          byte[] bytes = (supplied == null ? "" : supplied.toString()).getBytes(StandardCharsets.UTF_8);
          return new ZPENumber(p.writeBytes(bytes, bytes.length));
        }));
    addNativeMethod("read", method("read", new String[]{"maximum_bytes"}, new String[]{"number"}, YASSByteCodes.STRING_TYPE,
        parameters -> {
          int available = Math.max(0, p.bytesAvailable());
          ZPEType supplied = parameters.get("maximum_bytes");
          int maximum = supplied instanceof ZPENumber ? ((ZPENumber) supplied).currentValue().intValue() : available;
          byte[] bytes = new byte[Math.min(available, Math.max(0, maximum))];
          int count = bytes.length == 0 ? 0 : p.readBytes(bytes, bytes.length);
          return new ZPEString(new String(bytes, 0, Math.max(0, count), StandardCharsets.UTF_8));
        }));
  }

  @Override public ZPEType getVariable(String name) {
    if (p != null) {
      switch (name) {
        case "system_name": return new ZPEString(p.getSystemPortName());
        case "name": return new ZPEString(p.getDescriptivePortName());
        case "baud_rate": return new ZPENumber(p.getBaudRate());
      }
    }
    return super.getVariable(name);
  }

  @Override public void setProperty(String name, ZPEType value) throws ZPERuntimeException {
    if ("baud_rate".equals(name)) {
      if (!(value instanceof ZPENumber)) throw new ZPERuntimeException("SerialPort.baud_rate must be a number.");
      p.setBaudRate(((ZPENumber) value).currentValue().intValue());
    } else if ("system_name".equals(name) || "name".equals(name)) {
      throw new ZPERuntimeException("Property '" + name + "' is read-only.");
    }
    super.setProperty(name, value);
  }

  private interface Body { ZPEType run(HashMap<String,ZPEType> parameters); }
  private static ZPEObjectNativeMethod method(String name,String[] parameterNames,String[] parameterTypes,byte returnType,Body body){
    return new ZPEObjectNativeMethod(){
      public ZPEType run(HashMap<String,ZPEType> parameters,ZPEObject parent){return body.run(parameters);}
      public String[] getParameterNames(){return parameterNames;}
      public String[] getParameterTypes(){return parameterTypes;}
      public int getRequiredPermissionLevel(){return 3;}
      public String getName(){return name;}
      public byte[] returnTypes(){return new byte[]{returnType};}
    };
  }
}
