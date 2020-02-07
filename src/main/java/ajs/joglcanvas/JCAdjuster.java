package ajs.joglcanvas;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Event;
import java.awt.Scrollbar;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

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
		private float div;
		
		public NumberScrollPanel(float val, float min, float max, char label, int exp) {
			super(null, (int)(val*Math.pow(10, exp)), 1, (int)(min*Math.pow(10, exp)), (int)(max*Math.pow(10, exp)), label);
			Component[] comps=getComponents();
			for(Component comp:comps)if(comp instanceof Scrollbar)sb=(Scrollbar)comp;
			textfield=new TextField(String.format("%."+exp+"f",val),4);
			textfield.addActionListener(this);
			textfield.addFocusListener(new FocusListener() {
				@Override
				public void focusGained(FocusEvent e) {
					textfield.selectAll();
				}
				@Override
				public void focusLost(FocusEvent e) {textAdjustment();}
			});
			add(textfield, BorderLayout.EAST);
			this.label=label;
			this.exp=exp;
			div=(float)Math.pow(10, exp);
		}
		
		public char getLabel() {return label;}
		
		public Dimension getPreferredSize() {
			Dimension dim = super.getPreferredSize();
			dim.width+=textfield.getPreferredSize().width+50;
			return dim;
		}
		
		public void setFloatValue(float val) {
			super.setValue((int)(val*div));
			textfield.setText(String.format("%."+exp+"f",val));
		}
		
		@Override
		public void setValue(int val) {
			super.setValue(val);
			textfield.setText(String.format("%."+exp+"f",(float)val/div));
		}
		
		
		public float getFloatValue() {
			return (float)getValue()/div;
		}
		
		public float getFloatIncrement() {
			return 1f/div;
		}
		
		public boolean getValueIsAdjusting() {
			return sb.getValueIsAdjusting();
		}
		
		@Override
		public void adjustmentValueChanged(AdjustmentEvent e) {
			super.adjustmentValueChanged(e);
			textfield.setText(String.format("%."+exp+"f", (float)e.getValue()/div));
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			textAdjustment();
		}
		
		public void textAdjustment() {
			String text=textfield.getText();
			int val;
			try {
				val=(int)(Float.parseFloat(text)*div);
				setValue(val);
				adjustmentValueChanged(new AdjustmentEvent(sb, AdjustmentEvent.ADJUSTMENT_VALUE_CHANGED, AdjustmentEvent.TRACK, val, false));
			}catch(Exception ex) {
				
			}
		}
	}

}
