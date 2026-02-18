<h1>zpe.lib.serial</h1>

<p>
  This is the official Serial Port plugin for ZPE.
</p>

This library was among the first I developed for ZPE, but it fell into disrepair.
<p align="center">
  <img src="https://www.computerhope.com/jargon/s/serial-port.jpg" alt="A serial port" width="300">
</p>

<p>
  The plugin provides support for listing, opening and writing to serial ports.
</p>

<h2>Installation</h2>

<p>
  Place <strong>zpe.lib.serial.jar</strong> in your ZPE native-plugins folder and restart ZPE.
</p>

<p>
  You can also download with the ZULE Package Manager by using:
</p>
<p>
  <code>zpe --zule install zpe.lib.serial.jar</code>
</p>

<h2>Documentation</h2>

<p>
  Full documentation, examples and API reference are available here:
</p>

<p>
  <a href="https://www.jamiebalfour.scot/projects/zpe/documentation/plugins/zpe.lib.serial/" target="_blank">
    View the complete documentation
  </a>
</p>

<h2>Example</h2>

<pre>

ports = list_serial_ports()

for (p in ports)
    print(p.get_name())
end for

port = ports[0]
port.open()
port.write()
</pre>

<h2>Notes</h2>

<ul>
  <li>Uses jSerialComm internally.</li>
  <li>Requires permission level 3 for port operations.</li>
</ul>
