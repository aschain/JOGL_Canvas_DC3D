package ajs.joglcanvas;

import java.awt.Component;
import java.awt.Insets;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager;

public class JICLayout implements LayoutManager {

	@Override
	public void addLayoutComponent(String name, Component comp) {}

	@Override
	public void removeLayoutComponent(Component comp) {}

	@Override
	public Dimension preferredLayoutSize(Container parent) {
		if(parent.getComponentCount()<1)return null;
		Component m=parent.getComponents()[0];
		return getDim(parent, m.getWidth(),m.getHeight());
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
		m.setSize(ms.width, ms.height);
		m.setLocation(ins.left,ins.top);
	}

}
