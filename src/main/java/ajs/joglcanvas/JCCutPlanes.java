package ajs.joglcanvas;

import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Label;
import java.awt.Point;
import java.awt.event.AdjustmentEvent;

import ajs.joglcanvas.JOGLImageCanvas.FloatCube;

@SuppressWarnings("serial")
public class JCCutPlanes extends JCAdjuster {
	
	FloatCube c;
	private final static char[] cps=new char[] {'X','Y','Z','W','H','D'};

	public JCCutPlanes(JOGLImageCanvas jic) {
		super("Cut Planes", jic);
		c=jic.getCutPlanesCube();
		int[] whd=new int[] {imp.getWidth(),imp.getHeight(),imp.getNSlices()};
		int[] inits= new int[] {(int)c.x,(int)c.y,(int)c.z,(int)c.w,(int)c.h,(int)c.d};
		setLayout(new GridBagLayout());
		GridBagConstraints c= new GridBagConstraints();
		c.gridx=0;
		c.gridy=0;
		c.fill=GridBagConstraints.HORIZONTAL;
		c.anchor=GridBagConstraints.CENTER;
		c.insets=new Insets(5,5,5,5);
		add(new Label("Cut Planes"),c);
		for(int i=0;i<6;i++) {
			c.gridy++;
			NumberScrollPanel nsp=new NumberScrollPanel(inits[i],((i>2)?1:0),whd[(i>2)?(i-3):i]+(i>2?1:0),cps[i],0);
			add(nsp, c);
			nsp.addAdjustmentListener(this);
			nsp.setFocusable(false);
		}
		pack();
		Container win=jic.icc.getParent();
		Point loc=win.getLocation();
		setLocation(new Point(loc.x+win.getSize().width+10,loc.y+5));
		show();
	}

	@Override
	public void adjustmentValueChanged(AdjustmentEvent e) {
		if(jic.icc==null || !jic.icc.isVisible())dispose();
		Object source=e.getSource();
		if(source instanceof NumberScrollPanel) {
			NumberScrollPanel nsp=(NumberScrollPanel)source;
			switch(nsp.getLabel()) {
			case 'X':
				c.x=(float)nsp.getValue();
				break;
			case 'Y':
				c.y=(float)nsp.getValue();
				break;
			case 'Z':
				c.z=(float)nsp.getValue();
				break;
			case 'W':
				c.w=(float)nsp.getValue();
				break;
			case 'H':
				c.h=(float)nsp.getValue();
				break;
			case 'D':
				c.d=(float)nsp.getValue();
				break;
			}
			jic.setCutPlanesCube(c);
		}

	}
}
