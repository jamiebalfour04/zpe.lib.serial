import com.fazecast.jSerialComm.SerialPort;
import jamiebalfour.zpe.core.YASSByteCodes;
import jamiebalfour.zpe.core.ZPEFunction;
import jamiebalfour.zpe.core.ZPERuntimeEnvironment;
import jamiebalfour.zpe.core.ZPEStructure;
import jamiebalfour.zpe.interfaces.ZPECustomFunction;
import jamiebalfour.zpe.interfaces.ZPELibrary;
import jamiebalfour.zpe.interfaces.ZPEType;
import jamiebalfour.zpe.types.ZPEList;

import java.util.HashMap;
import java.util.Map;

public class Plugin implements ZPELibrary {

  @Override
  public Map<String, ZPECustomFunction> getFunctions() {
    Map<String, ZPECustomFunction> m = new HashMap<>();
    m.put("list_serial_ports", new ListSerialPorts());
    return m;
  }

  @Override
  public Map<String, Class<? extends ZPEStructure>> getObjects() {
    return null;
  }

  @Override
  public boolean supportsWindows() {
    return true;
  }

  @Override
  public boolean supportsMacOs() {
    return true;
  }

  @Override
  public boolean supportsLinux() {
    return true;
  }

  @Override
  public String getName() {
    return "libSerial";
  }

  @Override
  public String getVersionInfo() {
    return "1.0";
  }

  public static class ListSerialPorts implements jamiebalfour.zpe.interfaces.ZPECustomFunction {

    @Override
    public String getManualEntry() {
      return "Returns a list of all serial ports available on the system.";
    }

    @Override
    public String getManualHeader() {
      return "list_serial_ports()";
    }

    @Override
    public int getMinimumParameters() {
      return 0;
    }

    @Override
    public String[] getParameterNames() {
      return new String[0];
    }

    @Override
    public ZPEType MainMethod(HashMap<String, Object> hashMap, ZPERuntimeEnvironment zpeRuntimeEnvironment, ZPEFunction zpeFunction) {
      SerialPort[] ports = SerialPort.getCommPorts();
      ZPEList output = new ZPEList();
      for (SerialPort p : ports) {
        //Add all ports to the list
        ZPESerialPort port = new ZPESerialPort(zpeRuntimeEnvironment, zpeFunction, "ZPESerialPort");
        port.p = p;
        output.add(port);
      }

      return output;
    }

    @Override
    public int getRequiredPermissionLevel() {
      return 0;
    }

    @Override
    public byte getReturnType() {
      return YASSByteCodes.LIST;
    }

  }
}