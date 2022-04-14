package ajs.joglcanvas;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.StackWindow;

public class JCStackWindow extends StackWindow {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public JCStackWindow(ImagePlus imp, JOGLImageCanvas jic) {
		super(imp,jic);
		Container c=this;
		if(c instanceof JFrame)c=((JFrame)c).getContentPane();
        Component[] wincs=c.getComponents();
        
        for(int i=0;i<wincs.length;i++) {
        		if(wincs[i] instanceof JOGLImageCanvas) {c.remove(i); c.add(jic.icc, i);}
        }
        addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				jic.glw.destroy();
			}
		});
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
