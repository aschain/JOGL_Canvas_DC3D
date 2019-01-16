# DeepColorWindow
Plugin for ImageJ/Fiji using opengl to display images on 10bit or higher monitors

Most monitors are capable of 8 bits of color depth per channel (256 shades of color, 16.7 million colors). That's 24 bits for Red, Green, and Blue, or 32 bits for Red, Green, Blue, Alpha (transparency), all together. Newer monitors are capable of higher bits per color, often 10 bits for RGB (1024 shades of color per channel, 1.07 billion colors, sometimes called 10 bit or 30 bit display), and 2 bits for alpha (for 32 bits). They may even go up to 12 or 16 bits per color channel.

This plugin for Fiji or ImageJ1 replaces ImageCanvas with a JOGL opengl GLCanvas which allows the higher bit color depth. Instead of an 8bit byte rendering of the image for display, all color channels are converted to 32 bit floats.  The ROI, zoom indicator, etc., are drawn via a dummy blank image and mapped onto the render. Overlay doesn't seem to be working correctly but everything else seems to work on my system. 

Because I already have a JOGL GLCanvas, probably it wouldn't be that hard to implement some easy 3d rendering?
