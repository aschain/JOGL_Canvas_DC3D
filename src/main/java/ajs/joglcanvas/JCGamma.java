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
public class JCGamma extends JCAdjuster {
	
	private final static char[] cps=new char[] {'1','2','3','4','5','6'};
	NumberScrollPanel[] nsps;

	public JCGamma(JOGLImageCanvas jic) {
		super("Gamma", jic);
		float[] inits=jic.getGamma();
		if(inits==null)inits=new float[] {1f,1f,1f,1f,1f,1f};
		setLayout(new GridBagLayout());
		GridBagConstraints c= new GridBagConstraints();
		c.gridx=0;
		c.gridy=0;
		c.fill=GridBagConstraints.HORIZONTAL;
		c.anchor=GridBagConstraints.CENTER;
		c.insets=new Insets(5,5,5,5);
		add(new Label("Gamma for Channels:"),c);
		nsps=new NumberScrollPanel[imp.getNChannels()];
		for(int i=0;i<imp.getNChannels();i++) {
			c.gridy++;
			nsps[i]=new NumberScrollPanel(inits[i],5,500,cps[i],2);
			add(nsps[i], c);
			nsps[i].addAdjustmentListener(this);
			//nsp.setFocusable(false);
		}
		pack();
		Container win=jic.icc.getParent();
		Point loc=win.getLocation();
		setLocation(new Point(loc.x+win.getSize().width+10,loc.y+5));
		show();
	}

	@Override
	public void adjustmentValueChanged(AdjustmentEvent e) {
		if(jic.icc==null)dispose();
		Object source=e.getSource();
		if(source instanceof NumberScrollPanel) {
			float[] gamma=new float[nsps.length];
			for(int i=0;i<nsps.length;i++) {
				gamma[i]=(float)nsps[i].getValue()/(float)Math.pow(10, nsps[i].getExp());
			}
			jic.setGamma(gamma);
		}

	}
}
