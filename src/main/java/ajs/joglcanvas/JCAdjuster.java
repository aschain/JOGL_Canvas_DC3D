package ajs.joglcanvas;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Label;
import java.awt.Point;
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

public abstract class JCAdjuster extends PlugInDialog implements AdjustmentListener {
	
	ImagePlus imp;
	JOGLImageCanvas jic;

	public JCAdjuster(String title, JOGLImageCanvas jic) {
		super(title);
		this.imp=jic.getImage();
		this.jic=jic;
	}

	@Override
	public void adjustmentValueChanged(AdjustmentEvent e) {
		if(jic.icc==null)dispose();
	}
	
	@SuppressWarnings("serial")
	class NumberScrollPanel extends ScrollbarWithLabel implements ActionListener{
		
		private TextField textfield;
		private Scrollbar sb;
		private char label;
		private int exp;
		
		public NumberScrollPanel(int val, int min, int max, char label, int exp) {
			super(null, val, 1, min, max, label);
			Component[] comps=getComponents();
			for(Component comp:comps)if(comp instanceof Scrollbar)sb=(Scrollbar)comp;
			textfield=new TextField(""+val,4);
			textfield.addActionListener(this);
			add(textfield, BorderLayout.EAST);
			this.label=label;
			this.exp=exp;
		}
		
		public char getLabel() {return label;}
		public int getExp() {return exp;}
		
		public Dimension getPreferredSize() {
			Dimension dim = super.getPreferredSize();
			dim.width+=textfield.getPreferredSize().width+50;
			return dim;
		}
		
		@Override
		public void adjustmentValueChanged(AdjustmentEvent e) {
			super.adjustmentValueChanged(e);
			textfield.setText(String.format("%."+exp+"f", (float)e.getValue()/(float)Math.pow(10, exp)));
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
