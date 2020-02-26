package ajs.joglcanvas;

import java.awt.Button;
import java.awt.Checkbox;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Label;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import ajs.joglcanvas.JOGLImageCanvas.CutPlanesCube;

@SuppressWarnings("serial")
public class JCCutPlanes extends JCAdjuster {
	
	int[] c=new int[6];
	private final static char[] cps=new char[] {'X','Y','Z','W','H','D'};
	private NumberScrollPanel[] nsps=new NumberScrollPanel[cps.length];

	public JCCutPlanes(JOGLImageCanvas jic) {
		super("Cut Planes", jic);
		CutPlanesCube cpfc=jic.getCutPlanesCube();
		int[] whd=new int[] {imp.getWidth(),imp.getHeight(),imp.getNSlices()};
		this.c= new int[] {cpfc.x(),cpfc.y(),cpfc.z(),cpfc.w(),cpfc.h(),cpfc.d()};
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
			nsps[i]=new NumberScrollPanel(this.c[i],((i>2)?1:0),whd[(i>2)?(i-3):i]+(i>2?1:0),cps[i],0);
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
		c.gridx++;
		Checkbox cb=new Checkbox("Apply to Rois");
		cb.setState(cpfc.applyToRoi);
		cb.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				jic.setCutPlanesApplyToRoi(e.getStateChange()==ItemEvent.SELECTED);
			}
		});
		add(cb,c);
		pack();
		setToDefaultLocation();
		show();
	}
	
	private void update() {
		int i=0;
		c[i]=(int)nsps[i++].getFloatValue();
		c[i]=(int)nsps[i++].getFloatValue();
		c[i]=(int)nsps[i++].getFloatValue();
		c[i]=(int)nsps[i++].getFloatValue();
		c[i]=(int)nsps[i++].getFloatValue();
		c[i]=(int)nsps[i].getFloatValue();
		jic.updateCutPlanesCube(c);
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
