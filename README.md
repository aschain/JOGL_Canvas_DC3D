# JOGL_Canvas_DC3D
This is a plugin for ImageJ/Fiji (ImageJ1) that uses OpenGL with its graphics card accelleration to display images rather than java's AWT canvas. It replaces the ImageCanvas's java Canvas with a [JOGL](http://jogamp.org/jogl/www/) opengl GLCanvas instead.

This has two major benefits:

### 3D --  In-line 3D rendering of volumes

Image stacks are rendered in 3D directly on the canvas, and you can zoom, pan, translate, and rotate. You can also make selections and it will display all selections in the overlay from different slices where appropriate. You can render in an external window (mirror) or within the ImagePlus canvas.

### Deep Color or HDR High Dynamic Range --  Higher bit depths than 8 bits/color if your monitor supports it.
[More info here](https://github.com/aschain/JOGL_Canvas_DC3D/blob/master/HDR.md)

## Installation
Copy [JOGL_Canvas_DC3D](https://github.com/aschain/JOGL_Canvas_DC3D/releases/) to the plugins folder in your imagej1 / fiji directory. You must have the JOGL jars installed.  They will be installed if you have the fiji Java-8 update site activated. If you can run **Plugins -> Utilities -> Debugging -> Test Java3D** then you are ok.

## How to use
Open the image or image stack you would like to switch to JOGL Canvas. Open the plugins menu and select ```Plugins -> JOGL Canvas DC3D -> Convert to JOGL Canvas``` to convert the canvas to JOGL Canvas, or ```Plugins -> JOGL Canvas DC3D -> Open JOGL Canvas Mirror``` to open the JOGL Canvas in an external window.  The mirror is especially helpful if you would like to have your 3D rendering next to the 2D stack. Additionally, in the preferences, there is a setting to add shortcuts to these plugins to the ImageJ popup menu, which pops up when you right-click on any image.  

Once the JOGL Canvas is up, right clicking in the window opens a JOGL_Canvas-specific popup menu (see below). 

When 3D is on, right click and then drag to rotate the view. Regular clicks will perform normal ImageJ selection tasks, etc., as if on the 2d image, based on the current z-position.

## JOGL Canvas Popup menus

|3d Options Submenu| What it does|
|------------------|--------|
|Turn 3d on/off| Displays a 3d rendering of the image (if >1 slice)|
|Rendering| MAX - all voxels are transparent, ALPHA - bright voxels are less transparent|
|Set Undersampling| If not enough memory for 3d, undersampling can take every 2nd,4th etc. pixel|
|3D Pixel type| Select pixel display memory type for 3d (defaults to byte, but you can use short if you really need to see the 3d render in deep color as well, but it will probably be quite slow)|
|Update 3d Image| If the 3d image does not update when changes are made to the original image, select this|
|Reset 3d view| Resets the 3D angle changes that have been made, so the view is from the top down|
|Adjustments| Opens a Dialog boxes that controls cut planes, contrast/gamma, or rotation/translation/zoom|
|Stereoscopic 3D| Creates a stereo image to view with google cardboard (side-by-side 3D), colored glasses, or using a 3d-capable monitor|
|Save image or movie| Runs the *JOGL Cavnas Recorder* plugin, allowing screen caps of the 3D render|

|Other Options| What it does|
|------------------|--------|
|Fullscreen| Puts the canvas into a GL fullscreen mode, press Esc to exit|
|Resizable Mirror| If a JOGLCanvas Mirror, allows you to resize instead of locking to the original's size|
|Toggle Left Click 3D Control| If a JOGLCanvas Mirror, allows use of left-click to rotate the view|
|JOGL Canvas Preferences| Opens the preferences|
|Revert to Normal Window| Switches back to ImageCanvas or closes the mirror window|

|ImageJ| Opens the original ImageJ popup menu|
|--------------|------------|



## 3D Rendering
-- When in 3D mode, right click and drag will spin the volume around its midpoint along the x- and y-axis.  Hold down alt (option) to spin the volume around the z-axis. You can zoom in and out with "+" and "-" keys like with normal ImageJ windows, and pan by holding down the space bar.

-- If you update the image (cut/paste, etc.) it sometimes may not update the 3D render right away, you may need to select the "update 3d Image" menu item.

-- ROIs will appear as you draw them in the 3D volume, with the z determined by the current set slice. When adjusting ROIs, XY-coordinates when clicking on the screen are based on the original display, not the rotated 3d display (like you were clicking on the original image, not the 3d one). If you set multiple ROIs in the overlay, they will all be visible in the 3d render, in their correct z-position.

-- In ALPHA rendering mode, voxel's are less transparent (higher alpha) based on how bright the pixel is. Each pixel's alpha is calculated as the maximum of the red, green and blue channels.  

-- 3D rendered images cannot be directly saved (File-save will not save the rendered image).  You must capture them into a normal image first. I have included a simple plugin to do this: (*Plugins -> JOGL Canvas DC3D -> JOGL Canvas Recorder*). To use it, open/select a JOGL Canvas-converted image or mirror, then open the *JOGL Canvas Recorder*, and hit *Start Recording*.  If you want only one image, just hit *Stop*. If you want to record a movie while rotating/zooming/panning the render, do those actions, and then hit *Stop*.
