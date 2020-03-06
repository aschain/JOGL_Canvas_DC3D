package ajs.joglcanvas;

import java.awt.Component;
import java.awt.Insets;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager;

public class JICLayout implements LayoutManager {
	
	final private JOGLImageCanvas jic;
	
	public JICLayout(JOGLImageCanvas ic) {this.jic=ic;}

	@Override
	public void addLayoutComponent(String name, Component comp) {}

	@Override
	public void removeLayoutComponent(Component comp) {}

	@Override
	public Dimension preferredLayoutSize(Container parent) {
		return getDim(parent, (int)((double)jic.icc.getWidth()/jic.dpimag+0.5),(int)((double)jic.icc.getHeight()/jic.dpimag+0.5));
	}
	
	private Dimension getDim(Container parent, int width, int height) {
		Insets ins=parent.getInsets();
		return new Dimension(ins.left+ins.right+width,ins.top+ins.bottom+height);
	}

	@Override
	public Dimension minimumLayoutSize(Container parent) {
		return getDim(parent,10,10);
	}

	@Override
	public void layoutContainer(Container parent) {
		if(parent.getComponentCount()<1)return;
		Insets ins=parent.getInsets();
		Component m=parent.getComponents()[0];
		Dimension s=parent.getSize();
		Dimension ms=new Dimension(s.width-ins.left-ins.right-1, s.height-ins.top-ins.bottom-1);
		if(jic.dpimag!=1.0) {
			ms.width=(int)((double)ms.width*jic.dpimag+0.5);
			ms.height=(int)((double)ms.height*jic.dpimag+0.5);
		}
		m.setSize(ms.width, ms.height);
		m.setLocation(ins.left+1,ins.top);
	}

}
