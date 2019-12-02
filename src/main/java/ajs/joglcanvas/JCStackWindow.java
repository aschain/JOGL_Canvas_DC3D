package ajs.joglcanvas;

import java.awt.Component;
import java.awt.Container;
import javax.swing.JFrame;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.StackWindow;

public class JCStackWindow extends StackWindow {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public JCStackWindow(ImagePlus imp, JOGLImageCanvas dcic) {
		super(imp,dcic);
		Container c=this;
		if(c instanceof JFrame)c=((JFrame)c).getContentPane();
        Component[] wincs=c.getComponents();
        
        for(int i=0;i<wincs.length;i++) {
        		if(wincs[i] instanceof JOGLImageCanvas) {c.remove(i); c.add(dcic.icc, i);}
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
