# JOGL_Canvas_DC3D
This is a plugin for ImageJ/Fiji (ImageJ1) that uses OpenGL and with graphics card accelleration to display images rather than java's AWT canvas. It replaces the ImageCanvas's java Canvas with a [JOGL](http://jogamp.org/jogl/www/) opengl GLCanvas instead.

This has two major benefits:

### Deep Color or HDR High Dynamic Range --  Higher bit depths than 8 bits/color if your monitor supports it.

Most monitors are capable of 8 bits of color depth per channel (256 shades of color, 16.7 million colors). That's 24 bits for Red, Green, and Blue, or 32 bits for Red, Green, Blue, Alpha (transparency), all together. Newer monitors are capable of higher bits per color, a common one is 10 bits for RGB (1024 shades of color per channel, 1.07 billion colors, sometimes called 10 bit or 30 bit display), and 2 bits for alpha (for 32 bits). Other monitors can go up to 12 or 16 bits per color channel.
The GLCanvas can display to the monitor in these higher bits. Java represents each color with one byte (256 shades), but GLCanvas can use a short (65536), float (billions), or the more specific 10-bit packed int (1024 shades/color, 4 shades for alpha).

### 3D --  JOGL makes it easier to render in 3D.

Because OpenGL and graphics cards are geared towards 3D rendering, I also implemented a fairly simple 3d renderer for image stacks.  Image stacks are rendered in 3d directly on the canvas, and you can zoom, pan, translate, and rotate.

## Installation
Copy [JOGL_Canvas_DC3D](https://github.com/aschain/JOGL_Canvas_DC3D/releases/) to the plugins folder in your imagej1 / fiji directory. You must have the JOGL jars installed.  They will be installed if you have the fiji Java-8 update site activated. If you can run **Plugins -> Utilities -> Debugging -> Test Java3D** then you are ok.

## How to use
The first time you run, the preferences will pop up (or go to *Plugins -> JOGL Canvas DC3D -> JOGL Canvas Prefs*).  You must set The display pixel depths (8,8,8,8 for normal monitors, 10,10,10,2 for 10 bit, etc).  You can either replace the current window's ImageCanvas with the GLCanvas (*Plugins -> JOGL Canvas DC3D -> Convert to JOGL Canvas*) or create another window that is a mirror of the image window, but with the GLCanvas (*Plugins -> JOGL Canvas DC3D -> Open JOGL Canvas Mirror*). Additionally, in the preferences, there is a setting to add shortcuts to these plugins to the ImageJ popup menu, which pops up when you right-click on any image.  Once you have opened a JOGL Canvas window, right clicking in the window opens a JOGL_Canvas-specific popup menu (see below).  If you have a high-bit monitor and want to test whether an image can actually display in deep-color, choose "open test image" at the bottom of the preferences.  This image is a gradual ramp of 4096 shades of gray, and it opens initally with the default imagej canvas. You should see banding about every 60 pixels.  Convert to GLCanvas or open a mirror (with a 10bit+ display), and there should be no banding.

_Note:_ JOGL_Canvas cannot use LUTs that are more complex than a single color (Red, Green, Blue, Yellow, Gray...). This is because the LUTs are 8-bit and couldn't be used for 10-bit or higher display.  The plugin takes the color that corresponds to the brightest pixel and assumes a solid ramp LUT from that.  It will adjust for changes in max/min like normal ImageJ.

## JOGL Canvas Popup menus

|3d Options Submenu| What it does|
|------------------|--------|
|Turn 3d on/off| Displays a 3d rendering of the image (if >1 slice)|
|Rendering| MAX - all voxels are transparent, ALPHA - bright voxels are less transparent|
|Set Undersampling| If not enough memory for 3d, undersampling can take every 2nd,4th etc. pixel|
|3D Pixel type| Select pixel display memory type for 3d (defaults to byte, but you can use short if you really need to see the 3d render in deep color as well, but it will probably be quite slow)|
|Update 3d Image| The 3d image does not update when changes are made to the original image until you select this|
|Reset 3d view| Resets the 3D angle changes that have been made, so the view is from the top down|
|Adjust Cut Planes, etc| Opens a Dialog box that controls cut planes, contrast/gamma, or rotation/translation/zoom|
|Stereoscopic 3D| Creates a stereo image to view with google cardboard (side-by-side 3D), colored glasses, or using a 3d-capable monitor|
|Save image or movie| Runs the *JOGL Cavnas Recorder* plugin, allowing screen caps of the 3D render|

|Other Options| What it does|
|------------------|--------|
|Resizable Mirror| If a JOGLCanvas Mirror, allows you to resize instead of locking to the original's size|
|Fullscreen| Puts the canvas into a GL fullscreen mode, press Esc to exit|
|JOGL Canvas Preferences| Opens the preferences|
|Revert to Normal Window| Switches back to ImageCanvas or closes the mirror window|

|ImageJ| Opens the original ImageJ popup menu|
|--------------|------------|



## 3D Rendering
-- When in 3D mode, for the mirror window, clicking and dragging will spin the volume around its midpoint in x and y.  For the converted ImageWindow, you must control-click (command for mac) to spin the volume.  Hold down alt (option) to spin the volume around the z-axis. Shift-click to reset the 3d view. You can zoom in and out with "+" and "-" keys like with normal ImageJ windows, and pan by holding down the space bar.

-- If you update the image (cut/paste, etc.) it will not update the 3D render right away, you will need to press the "update" button that appears, or right click and select 3D Options - Update 3d Image.

-- ROIs will appear as you draw them in the 3D volume, with the z determined by the current set slice. When adjusting ROIs, XY-coordinates when clicking on the screen are based on the original display, not the rotated 3d display (like you were clicking on the original image, not the 3d one). If you set multiple ROIs in the overlay, they will all be visible in the 3d render, in their correct z-position.

-- In ALPHA rendering mode, voxel's are less transparent (higher alpha) based on how bright the pixel is. Each pixel's alpha is calculated as the maximum of the red, green and blue channels.  

-- 3D rendered images cannot be directly saved.  You must capture them into normal image first. I have included a simple plugin to do this: (*Plugins -> JOGL Canvas DC3D -> JOGL Canvas Recorder*). To use it, open/select a JOGL Canvas-converted image or mirror, then open the *JOGL Canvas Recorder*, and hit *Start Recording*.  If you want only one image, just hit *Stop*. If you want to record a movie while rotating/zooming/panning the render, do those actions, and then hit *Stop*.
