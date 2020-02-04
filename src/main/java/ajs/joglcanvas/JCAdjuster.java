package ajs.joglcanvas;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Label;
import java.awt.Scrollbar;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;

import ajs.joglcanvas.JOGLImageCanvas.FloatCube;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.ScrollbarWithLabel;
import ij.plugin.frame.PlugInDialog;

public class JCAdjuster extends PlugInDialog implements AdjustmentListener {
	
	ImagePlus imp;
	JOGLImageCanvas jic;
	FloatCube c;
	private final static char[] cps=new char[] {'X','Y','Z','W','H','D'};

	public JCAdjuster(JOGLImageCanvas jic) {
		super("JC Color");
		this.imp=jic.getImage();
		this.jic=jic;
		c=jic.getCutPlanesCube();
		int[] whd=new int[] {imp.getWidth(),imp.getHeight(),imp.getNSlices()};
		int[] inits= new int[] {(int)(c.x*whd[0]),(int)(c.y*whd[1]),(int)(c.z*whd[2]),
				(int)(c.w*whd[0]),(int)(c.h*whd[1]),(int)(c.d*whd[2])};
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
			NumberScrollPanel nsp=new NumberScrollPanel(inits[i],0,whd[i>2?i-3:i],cps[i]);
			add(nsp, c);
			nsp.addAdjustmentListener(this);
			//nsp.setFocusable(false);
		}
		pack();
		show();
	}

	@Override
	public void adjustmentValueChanged(AdjustmentEvent e) {
		Object source=e.getSource();
		if(source instanceof NumberScrollPanel) {
			NumberScrollPanel nsp=(NumberScrollPanel)source;
			switch(nsp.getLabel()) {
			case 'X':
				c.x=(float)nsp.getValue()/imp.getWidth();
				break;
			case 'Y':
				c.y=(float)nsp.getValue()/imp.getHeight();
				break;
			case 'Z':
				c.z=(float)nsp.getValue()/imp.getNSlices();
				break;
			case 'W':
				c.w=(float)nsp.getValue()/imp.getWidth();
				break;
			case 'H':
				c.h=(float)nsp.getValue()/imp.getHeight();
				break;
			case 'D':
				c.d=(float)nsp.getValue()/imp.getNSlices();
				break;
			}
			jic.setCutPlanesCube(c);
		}

	}
	
	class NumberScrollPanel extends ScrollbarWithLabel implements ActionListener{
		
		private TextField textfield;
		private Scrollbar sb;
		private char label;
		
		public NumberScrollPanel(int val, int min, int max, char label) {
			super(null, val, max-min, min, max, label);
			Component[] comps=getComponents();
			for(Component comp:comps)if(comp instanceof Scrollbar)sb=(Scrollbar)comp;
			textfield=new TextField(""+val,3);
			textfield.addActionListener(this);
			add(textfield, BorderLayout.EAST);
			this.label=label;
		}
		
		public char getLabel() {return label;}
		
		public Dimension getPreferredSize() {
			Dimension dim = super.getPreferredSize();
			dim.width+=textfield.getPreferredSize().width+2;
			return dim;
		}
		
		@Override
		public void adjustmentValueChanged(AdjustmentEvent e) {
			super.adjustmentValueChanged(e);
			textfield.setText(""+e.getValue());
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			String text=textfield.getText();
			int val;
			try {
				val=Integer.parseInt(text);
				setValue(val);
				adjustmentValueChanged(new AdjustmentEvent(sb, AdjustmentEvent.ADJUSTMENT_VALUE_CHANGED, AdjustmentEvent.TRACK, val, false));
			}catch(Exception ex) {
				
			}
		}
	}

}
