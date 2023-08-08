### HDR - High Dynamic Range for colors
Most monitors are capable of 8 bits of depth per color (256 shades of color, 16.7 million colors).

However, newer monitors are capable of higher bits per color, a common one is 10 bits for RGB (1024 shades of color per channel, 1.07 billion colors, sometimes called 10 bit or 30 bit display, HDR, or HDR10). Other monitors can go up to 12 or 16 bits per color channel. More bits means smoother transistions between shades of colors.

Java, by itself, unfortunately, cannot make use of higher bit monitors. However the JOGL integration in JOGLCanvas makes this possible!

### Bit info
Eight bits per color means 24 bits total for Red, Green, and Blue, or 32 bits for Red, Green, Blue, Alpha (transparency), and therefore can be represented with 4 bytes. 10 bits per color is often accompanied by a reduced 2 bits for alpha, for 32 bits total, which allows the higher color bit depth in the same 4 bytes of data. The GLCanvas can display to the monitor with the regular 8 bit 4 bytes, the packed 10 bit 4 bytes, or it can use even higher amounts of bytes for 12- or 16- bit displays.

### How to use
The first time you run, the preferences will pop up (or go to *Plugins -> JOGL Canvas DC3D -> JOGL Canvas Prefs*).  You must set The display pixel depths (8,8,8,8 for normal monitors, 10,10,10,2 for 10 bit, etc).  

You can either replace the current window's ImageCanvas with the GLCanvas (*Plugins -> JOGL Canvas DC3D -> Convert to JOGL Canvas*) or create another window that is a mirror of the image window, but with the GLCanvas (*Plugins -> JOGL Canvas DC3D -> Open JOGL Canvas Mirror*).  If you have a high-bit monitor and want to test whether it can actually display an image in high-bit color, choose "open test image" at the bottom of the preferences.  This image is a gradual ramp of 4096 shades of gray, and it opens initally with the default imagej canvas. You should see similar/identical banding about every 60 pixels in the 8-bit row and all higher bits.  Convert to GLCanvas or open a mirror (with a 10bit+ display), and there should be no banding for the 10-bit row (or higher if you have such a display).

### LUTs
JOGL_Canvas cannot use LUTs that are more complex than a single color (Red, Green, Blue, Yellow, Gray...). This is because the ImageJ built-in LUTs are 8-bit and can't be used for 10-bit or higher display.  The plugin takes the color that corresponds to the brightest pixel and assumes a solid ramp LUT from that.  It will make adjustments for changes in max/min like normal ImageJ.

### Troubleshooting
If the test image still shows banding:  
1. Make sure the monitor settings are for higher color bits.  For example, open Nvidia Control Panel, select Change Resolution, select "Use NVIDIA color settings" and set "Output color depth" to "10bpc", and "Output dynamic range" to "Full"
2. Sometimes stereoscopic active stereo does not work with HDR, so turn off "Enable active stereo" in JOGL Canvas Prefs
3. Try rebooting?
