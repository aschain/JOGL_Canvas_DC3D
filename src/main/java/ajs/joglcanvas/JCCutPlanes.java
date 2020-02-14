package ajs.joglcanvas;

import java.awt.Button;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Label;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;

import ajs.joglcanvas.JCAdjuster.NumberScrollPanel;
import ajs.joglcanvas.JOGLImageCanvas.FloatCube;

@SuppressWarnings("serial")
public class JCCutPlanes extends JCAdjuster {
	
	FloatCube c;
	private final static char[] cps=new char[] {'X','Y','Z','W','H','D'};
	private NumberScrollPanel[] nsps=new NumberScrollPanel[cps.length];

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
		for(int i=0;i<cps.length;i++) {
			c.gridy++;
			nsps[i]=new NumberScrollPanel(inits[i],((i>2)?1:0),whd[(i>2)?(i-3):i]+(i>2?1:0),cps[i],0);
			add(nsps[i], c);
			nsps[i].addAdjustmentListener(this);
			nsps[i].setFocusable(false);
		}
		c.gridy++;
		Button b=new Button("Reset");
		b.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				for(int i=0;i<3;i++) {
					nsps[i].setFloatValue(0);
					nsps[i+3].setFloatValue((i==0?imp.getWidth():(i==1?imp.getHeight():imp.getNSlices())));
				}
				update();
			}
		});
		add(b,c);
		pack();
		Container win=jic.icc.getParent();
		Point loc=win.getLocation();
		setLocation(new Point(loc.x+win.getSize().width+10,loc.y+5));
		show();
	}
	
	private void update() {
		int i=0;
		c.x=nsps[i++].getFloatValue();
		c.y=nsps[i++].getFloatValue();
		c.z=nsps[i++].getFloatValue();
		c.w=nsps[i++].getFloatValue();
		c.h=nsps[i++].getFloatValue();
		c.d=nsps[i++].getFloatValue();
		jic.setCutPlanesCube(c);
	}

	@Override
	public void adjustmentValueChanged(AdjustmentEvent e) {
		if(jic.icc==null || !jic.icc.isVisible())dispose();
		Object source=e.getSource();
		if(source instanceof NumberScrollPanel) {
			NumberScrollPanel nsp=(NumberScrollPanel)source;
			switch(nsp.getLabel()) {
			case 'X':
				if(nsp.getFloatValue()>=nsps[3].getFloatValue())nsps[3].setFloatValue(nsp.getFloatValue()+1f);
				break;
			case 'Y':
				if(nsp.getFloatValue()>=nsps[4].getFloatValue())nsps[4].setFloatValue(nsp.getFloatValue()+1f);
				break;
			case 'Z':
				if(nsp.getFloatValue()>=nsps[5].getFloatValue())nsps[5].setFloatValue(nsp.getFloatValue()+1f);
				break;
			case 'W':
				if(nsp.getFloatValue()<=nsps[0].getFloatValue())nsps[0].setFloatValue(nsp.getFloatValue()-1f);
				break;
			case 'H':
				if(nsp.getFloatValue()<=nsps[1].getFloatValue())nsps[1].setFloatValue(nsp.getFloatValue()-1f);
				break;
			case 'D':
				if(nsp.getFloatValue()<=nsps[2].getFloatValue())nsps[2].setFloatValue(nsp.getFloatValue()-1f);
				break;
			}
		}
		update();

	}
}
