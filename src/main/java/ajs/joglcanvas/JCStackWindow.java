package ajs.joglcanvas;

import java.awt.Component;

import ij.ImagePlus;
import ij.gui.StackWindow;

public class JCStackWindow extends StackWindow {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public JCStackWindow(ImagePlus imp, JOGLImageCanvas dcic) {
		super(imp,dcic);
        Component[] wincs=getComponents();
        for(int i=0;i<wincs.length;i++)remove(wincs[i]);
        for(int i=0;i<wincs.length;i++) {
        		if(wincs[i] instanceof JOGLImageCanvas) {add(dcic.icc);}
        		else {add(wincs[i]);}
        }
		repaint();
	}
	
	public JCStackWindow(ImagePlus imp) {
		this(imp,new JOGLImageCanvas(imp, false));
	}
	
	public void setTitle(String title) {
		super.setTitle(title+" (JOGL Canvas)");
	}
	
	public void updateTitle(String adder) {
		if(adder.isEmpty())setTitle(imp.getTitle());
		else super.setTitle(imp.getTitle()+" (JOGL Canvas) ("+adder+")");
	}
		
}
