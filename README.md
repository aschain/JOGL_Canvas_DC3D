# JOGL_Canvas_DC3D
This is a plugin for ImageJ/Fiji (ImageJ1) that uses OpenGL and a graphics card to display images rather than java's canvas. It replaces the ImageCanvas's java Canvas with a [JOGL](http://jogamp.org/jogl/www/) opengl GLCanvas instead.

This has two major benefits:

## Deep Color --  Higher bit depths than 8 bits/color if your monitor supports it.

Most monitors are capable of 8 bits of color depth per channel (256 shades of color, 16.7 million colors). That's 24 bits for Red, Green, and Blue, or 32 bits for Red, Green, Blue, Alpha (transparency), all together. Newer monitors are capable of higher bits per color, a common one is 10 bits for RGB (1024 shades of color per channel, 1.07 billion colors, sometimes called 10 bit or 30 bit display), and 2 bits for alpha (for 32 bits). Other monitors can go up to 12 or 16 bits per color channel.
The GLCanvas can display to the monitor in these higher bits. Java represents each color with one byte (256 shades), but GLCanvas can use a short (65536), float (billions), or the more specific 10-bit packed int (1024 shades/color, 4 shades for alpha).

## 3D --  JOGL makes it easier to render in 3D.

Because OpenGL and graphics cards are geared towards 3D rendering, I also implemented a fairly simple 3d renderer for image stacks.  Image stacks are rendered in 3d directly on the canvas, and you can zoom, and rotate but not translate or move the "camera".

