package ajs.joglcanvas;

import ij.IJ;
import ij.ImageListener;
import ij.ImagePlus;

public class JOGLCanvasService implements ImageListener {

	public JOGLCanvasService() {
		
	}

	
	public void imageOpened(ImagePlus imp) {
		do{IJ.wait(500);}while(!imp.isVisible());
		JCP.convertToJOGLCanvas(imp);
	}

	public void imageClosed(ImagePlus imp) {
	}

	public void imageUpdated(ImagePlus imp) {
	}

}