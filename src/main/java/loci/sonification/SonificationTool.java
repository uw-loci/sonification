/*
 * #%L
 * Sonification plugin for listening to your images.
 * %%
 * Copyright (C) 2014 - 2017 Board of Regents of the University of
 * Wisconsin-Madison.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

package loci.sonification;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Toolbar;
import ij.plugin.tool.PlugInTool;
import ij.process.ImageProcessor;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import de.sciss.jcollider.Constants;
import de.sciss.jcollider.Control;
import de.sciss.jcollider.GraphElem;
import de.sciss.jcollider.Group;
import de.sciss.jcollider.JCollider;
import de.sciss.jcollider.NodeWatcher;
import de.sciss.jcollider.Server;
import de.sciss.jcollider.ServerEvent;
import de.sciss.jcollider.ServerListener;
import de.sciss.jcollider.Synth;
import de.sciss.jcollider.SynthDef;
import de.sciss.jcollider.UGen;
import de.sciss.jcollider.UGenChannel;
import de.sciss.jcollider.UGenInfo;
import de.sciss.jcollider.gui.ServerPanel;
import de.sciss.net.OSCBundle;


public class SonificationTool extends PlugInTool implements ServerListener, Constants
{
	private static final boolean DEBUG = false;
	
	private List<SynthDef> MemoryDefCollection = new ArrayList<SynthDef>(); 
	
	private float[] harmonicfreqlist = {300f, 600f, 900f, 1200f, 1500f, 1800f, 2100f, 2400f, 2700f, 3000f, 3300f, 3600f, 3900f, 4200f, 4500f, 4800f};
	private static float[] harmonicstacked = {300f, 600f, 900f, 1200f, 1500f, 1800f, 2100f, 2400f, 300f, 600f, 900f, 1200f, 1500f, 1800f, 2100f, 2400f};
	float[] modpartials = {0.25f, 0.5f, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 11f, 12f, 13f, 14f}; 
	
	
	private Synth synth = null;
	private Server server = null;
	private NodeWatcher nw = null;
	private Group grpAll;
	
	//private boolean isInit = false;
	
	private final SonificationTool enc_this = this;

	@Override
	public void mousePressed(ImagePlus imp, MouseEvent e) {
		
		//if (!isInit)
			//ensureBooted();
		ImageStack stack = imp.getStack();
		ImageProcessor[] pixelstack = new ImageProcessor[imp.getNChannels()];
		
		for(int c = 0; c<imp.getNChannels(); c++) {
			
			int z = imp.getSlice();
			int t = imp.getFrame();
			int i = imp.getStackIndex(c+1,z,t);
			
			
			
			pixelstack[c] = stack.getProcessor(i);
			
		}
		StringBuilder sb = new StringBuilder();
		float[] intensitystack = new float[imp.getNChannels()]; 
		
		
		//normal pixel sonification behavior
		for(int c = 0; c<imp.getNChannels(); c++){
			
			
			
			sb.append(pixelstack[c].getf(e.getX(),e.getY())+" ");
			intensitystack[c] = pixelstack[c].getf(e.getX(),e.getY());
		}
		
		startSound(intensitystack);
		
		
		
		//calibration path playing behavior:
//		float[][] mypath = new float[imp.getDimensions()[0]][imp.getNChannels()];
//		
//		int pixelcount = 0;
//		
//		for(float[] chan : mypath) {
//			
//			
//			
//			for (int c=0; c<chan.length; c++) {
//				//IJ.log(pixelcount + "");
//				chan[c] = pixelstack[c].getf(55,pixelcount);
//				
//			}
//			pixelcount++;
//		}
//		
//		startSound(mypath);
		
		//IJ.log(imp.getDimensions()[0]+","+imp.getDimensions()[1]);
		e.consume();
		
		
		
		
	}

	@Override
	public void mouseReleased(ImagePlus imp, MouseEvent e) {
		stopSound();
		e.consume();
	}

	@Override
	public void mouseDragged(ImagePlus imp, MouseEvent e) {
		
		
		if(e.getX()>0 && e.getX()<imp.getDimensions()[0] && e.getY()>0 && e.getY()<imp.getDimensions()[1])
		{
			try {
			
				
				
				
			ImageStack stack = imp.getStack();
			ImageProcessor[] pixelstack = new ImageProcessor[imp.getNChannels()];
			
			for(int c = 0; c<imp.getNChannels(); c++) {
				
				int z = imp.getSlice();
				int t = imp.getFrame();
				int i = imp.getStackIndex(c+1,z,t);
				
				pixelstack[c] = stack.getProcessor(i);
				
			}
			StringBuilder sb = new StringBuilder();
			float[] intensitystack = new float[imp.getNChannels()]; 
			for(int c = 0; c<imp.getNChannels(); c++){
				
				
				
				sb.append("ch"+(c+1)+": "+pixelstack[c].getf(e.getX(),e.getY())+",");
				intensitystack[c] = pixelstack[c].getf(e.getX(),e.getY());
			}
			playTone(intensitystack);
			sb.deleteCharAt(sb.length()-1);
			IJ.log(sb.toString());		
			//final int[] pixel = imp.getPixel(e.getX(), e.getY());
			//playTone(pixel);
			e.consume();
			}
			catch (Exception exc) {
				IJ.handleException(exc);
			}
		}
		else
			stopSound();
	}
	
	// -- Main method --

	public static void main(final String args[]) {
		new ImageJ();
		new SonificationTool().run("");
		
		
	}
	
	
	@Override
	public void run(final String arg) {
		Toolbar.addPlugInTool(this);
		ensureBooted();
	}
//	@Override
//	public void run(final String arg) {
//		SwingUtilities.invokeLater(new Runnable() {
//
//			@Override
//			public void run() {
//				ensureBooted();
//			}
//		});
//
//	}

	// -- Helper methods --

	private void ensureBooted() {
		// TODO Auto-generated method stub
		
		try {
			server = new Server("localhost");
			createDefs();
			
			final String fs = File.separator;
			JFrame spf = null;
			JFrame mygui = null;
			

			final File f =
				findFile(JCollider.isWindows ? "scsynth.exe" : "scsynth", new String[] {
					fs + "Applications" + fs + "SuperCollider" + fs +
						"SuperCollider.app" + fs + "Contents" + fs + "Resources",
					fs + "Applications" + fs + "SC3",
					fs + "usr" + fs + "local" + fs + "bin", fs + "usr" + fs + "bin",
					"C:\\Program Files\\SC3", "C:\\Program Files\\SuperCollider_f" });
			if (f != null) Server.setProgram(f.getAbsolutePath());

//			ggAppPath.setText(Server.getProgram());
//			ggAppPath.addActionListener(new ActionListener() {
//
//				@Override
//				public void actionPerformed(final ActionEvent e) {
//					Server.setProgram(ggAppPath.getText());
//				}
//			});
			
			
			
			server.addListener(this);
			try {
				server.start();
				server.startAliveThread();
			}
			catch (final IOException e1) { /* ignored */}
			spf =
				ServerPanel.makeWindow(server, ServerPanel.MIMIC | ServerPanel.CONSOLE |
					ServerPanel.DUMP);
			spf.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
			
			//mygui = 
		}
		catch(final IOException e1) {
//			JOptionPane.showMessageDialog(this, "Failed to create a server :\n" +
//					e1.getClass().getName() + e1.getLocalizedMessage(), "",
//					JOptionPane.ERROR_MESSAGE);
			
			IJ.log("server creation failed");
			}
		
		
	}

	private void startSound(float [] pixel) {
		// TODO Auto-generated method stub
		final SynthDef def = MemoryDefCollection.get(0);
		

		if (def != null && grpAll != null && server != null) {
			try {
				synth = Synth.basicNew(def.getName(), server);
				if (nw != null) nw.register(synth);
				server.sendMsg(synth.newMsg(grpAll));
				playTone(pixel);
			}
			catch (final IOException e1) {
				//JCollider.displayError(enc_this, e1, "Play");
				IJ.log("error starting sound");
			}
		}
		
	}
	
	private void startSound(float [][] pixelpath) {
		// TODO Auto-generated method stub
		final SynthDef def = MemoryDefCollection.get(0);
		

		if (def != null && grpAll != null && server != null) {
			try {
				synth = Synth.basicNew(def.getName(), server);
				if (nw != null) nw.register(synth);
				server.sendMsg(synth.newMsg(grpAll));
				playPath(pixelpath);
			}
			catch (final IOException e1) {
				//JCollider.displayError(enc_this, e1, "Play");
				IJ.log("error starting sound");
			}
		}
		
	}

	private void stopSound() {
		// TODO Auto-generated method stub
		if (grpAll != null) {
			try {
				grpAll.freeAll();
			}
			catch (final IOException e1) {
				//JCollider.displayError(enc_this, e1, "Stop");
				IJ.log("error stopping sound");
			}
		}
		
	}

	private void playTone(float[] pixel) {
		//IJ.log("playing a tone: " + pixel[0]);
		// TODO Auto-generated method stub
		if(synth !=null && server!=null) {
			try {
				
				String control[] = new String[pixel.length];
				String volcontrol[] = new String[pixel.length];
				float values[] = new float[pixel.length];
				float volvalues[] = new float[pixel.length];
				
				//TODO: add all control messages into an array and intensity values in another array to create before making the bundle (prevent late messages)
				
				
				
				for(int i=0;i<pixel.length;i++) {
					
					
					control[i] = "ch"+i;
					
					/*HDR intensity mapping (64 bit)*/
					//bndl.addPacket(synth.setMsg("ch"+i, (pixel[i]-353)/10708));
					
					/*8 bit intensity mapping*/
					//bndl.addPacket(synth.setMsg("ch"+i, pixel[i]/255));
					
					//values[i] =  pixel[i]/255;
					
					/*FM specific mapping*/
					//bndl.addPacket(synth.setMsg("ch"+i, ((pixel[i]/255)*150) + harmonicfreqlist[i]));
					
					

					
					//values[i] = ((pixel[i]/255)*150) + harmonicfreqlist[i];
					
					/*FM2 mapping*/
					//bndl.addPacket(synth.setMsg("ch"+i, pixel[i]/255));
					
					
					/*FM2 HDR*/
					//bndl.addPacket(synth.setMsg("ch"+i, (pixel[i]-353)/10708));
					
					/*FM3 */
					//bndl.addPacket(synth.setMsg("ch"+i, ((pixel[i]/255)*5) + harmonicfreqlist[i]));
					
					//values[i] = (pixel[i]/255)*5 + harmonicfreqlist[i];
					
					/*FM3 HDR */
					//bndl.addPacket(synth.setMsg("ch"+i, (((pixel[i]-353)/10708)*5) + harmonicfreqlist[i]));
					
					/*FM4 */
					//bndl.addPacket(synth.setMsg("ch"+i, pixel[i]/255));
					
					/*FM4 HDR*/
					//bndl.addPacket(synth.setMsg("ch"+i, (pixel[i]-353)/10708));
					
					/*FM5 */
					
//					if(i<8) {
//						
//						bndl.addPacket(synth.setMsg("ch"+i, ((pixel[i]/255)*150) + harmonicstacked[i]));
//						
//						bndl.addPacket(synth.setMsg("ch"+(i+7), ((pixel[i]/255)*150) + harmonicstacked[i]));
//						
//					}
//					else {
//						bndl.addPacket(synth.setMsg("ch"+i, ((pixel[i]/255)*5) + harmonicstacked[i]));
//					}
					
					
					/*FM5 HDR*/
					
					/*FM6*/
					
					values[i] = ((pixel[i]/255)*350) + harmonicfreqlist[i];
					
					/*TRUEFM*/
					
					//values[i] = ((pixel[i]/255)*150) + (440f*modpartials[i]);
					

				}
				
				for (int i=0; i<pixel.length; i++) {
					volcontrol[i] = "chv"+i;
					volvalues[i] = pixel[i]/255;
				}
				
//				for (int i =pixel.length-1;i<pixel.length*2;i++) {
//					control[i] = "chv" + (i-(pixel.length-1));
//					values[i] = pixel[i-(pixel.length-1)]/255;
//				}
//				bndl.addPacket(synth.setMsg("ch0",0.5f));
//				bndl.addPacket(synth.setMsg("", value));
				
				OSCBundle bndl = new OSCBundle(System.currentTimeMillis());
				
				bndl.addPacket(synth.setMsg(control,values));
				bndl.addPacket(synth.setMsg(volcontrol,volvalues));
				server.sendBundle(bndl);
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
		
	}
	
	private void playPath(float[][] pixelpath) {
		
		OSCBundle bndl;
		
		//IJ.log(pixelpath.length + "");
		
		if(synth !=null && server!=null) {
			
			IJ.log("hello");
			//try {
				long time = System.currentTimeMillis();
				
				
				for (int i =0; i <4500;i++) {
					//each pixel is 10ms
					try {
						float[] pixel = pixelpath[(i)/10];
						String control[] = new String[pixel.length];
						float values[] = new float[pixel.length];
						
						bndl = new OSCBundle(time + i);
						
						for (int x = 0; x<pixel.length; x++) {
							
							
							control[x] = "ch"+x;
							
							//8-bit mapping;
							values[x] = pixel[x]/255;
						}
						IJ.log(Arrays.toString(values));
						bndl.addPacket(synth.setMsg(control,values));
						server.sendBundle(bndl);
					}
					catch (Exception e) {
						IJ.log(e.getMessage());
					}
					
				
				}
				
				//synth.fre
				
				
				
				//stopSound();
				
	            bndl = new OSCBundle( time + 5500 );
	            bndl.addPacket( grpAll.freeAllMsg() );
	            try {
					server.sendBundle( bndl );
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				
//				for(float[] pixel : pixelpath) {
//					
//
//					
//
//				}


				
			//} catch (IOException e1) {
				// TODO Auto-generated catch block
				// e1.printStackTrace();
			//}
		}
	}
	
	public void serverAction(final ServerEvent e) {
		switch (e.getID()) {
			case ServerEvent.RUNNING:
				try {
					initServer();
				}
				catch (final IOException e1) {
					reportError(e1);
				}
				break;

			case ServerEvent.STOPPED:
				// re-run alive thread
				final javax.swing.Timer t =
					new javax.swing.Timer(1000, new ActionListener() {

						@Override
						public void actionPerformed(final ActionEvent evt) {
							try {
								if (server != null) server.startAliveThread();
							}
							catch (final IOException e1) {
								reportError(e1);
							}
						}
					});
				t.setRepeats(false);
				t.start();
				break;

			default:
				break;
		}
	}

	private void initServer() throws IOException {
		sendDefs();
		if (!server.didWeBootTheServer()) {
			server.initTree();
			server.notify(true);
		}
		nw = NodeWatcher.newFrom(server);
		grpAll = Group.basicNew(server);
		nw.register(server.getDefaultGroup());
		nw.register(grpAll);
		server.sendMsg(grpAll.newMsg());
	}
	
	private void createDefs() {
		if (DEBUG) IJ.log("Creating Defs");
		try {
			UGenInfo.readBinaryDefinitions();

			//final List<SynthDef> collDefs = new ArrayList<SynthDef>();
			//collDefs.add(jSampleAndHoldLiquid());
			
			
			//Collections.sort(collDefs, SYNTH_DEF_NAME_COMP);
			
			
			//MemoryDefCollection.add(jSampleAndHoldLiquid());
			//MemoryDefCollection.add(sinSummationTest());
			//MemoryDefCollection.add(harmonicAMTest());
			//MemoryDefCollection.add(harmonicFMTest());
			//MemoryDefCollection.add(harmonicFMTest2());
			//MemoryDefCollection.add(harmonicFMTest3());
			//MemoryDefCollection.add(harmonicFMTest4());
			//MemoryDefCollection.add(harmonicFMTest5());
			//MemoryDefCollection.add(phantomTest());
			//MemoryDefCollection.add(harmonicFMTest3modified());
			//MemoryDefCollection.add(harmonicFMTest2modified());
			//MemoryDefCollection.add(trueFM1());
			MemoryDefCollection.add(harmonicFMTest6());
			//defTables[0].addDefs(collDefs);
		}
		catch (final IOException e1) {
			reportError(e1);
		}
	}
	
	private void sendDefs() {
		
//		List<SynthDef> defs;
//		SynthDef def;

//		for (int i = 0; i < defTables.length; i++) 
//		{
//			defs = defTables[i].getDefs();
//			for (int j = 0; j < defs.size(); j++) {
//				def = defs.get(j);
//				try {
//					def.send(server);
//				}
//				catch (final IOException e1) {
//					System.err.println("Sending Def " + def.getName() + " : " +
//						e1.getClass().getName() + " : " + e1.getLocalizedMessage());
//				}
//			}
//		}
		System.out.println("Memory list size:" + MemoryDefCollection.size());
		
		for(SynthDef t : MemoryDefCollection) {
			try {
				t.send(server);
			}
			catch (final IOException e1){
				System.err.println("Sending Def " + t.getName() + " : " + e1.getClass().getName() + " : " + e1.getLocalizedMessage());
			}
		}
	}
	
	private static SynthDef sinSummationTest() {
		
		Control[] controlz = new Control[15];
		UGenChannel[] channelz = new UGenChannel[15];
		
		//OSCBundle x
		
		//14 higher notes
		float[] freqlist = {440f, 466.16f, 493.88f, 523.25f, 554.37f, 587.33f, 622.25f, 659.26f, 698.46f, 739.99f, 783.99f, 830.61f, 880f, 932.33f, 987.77f};
		//Base note A440
		GraphElem combowave = null; 
		//= UGen.ar("SinOsc", UGen.ir(440));
		int i = 0;
		for (float num : freqlist) {
			
			controlz[i] = Control.kr(new String[] {"ch"+i}, new float[] {0.0f});
			channelz[i] = controlz[i].getChannel(0);
			
			combowave = (combowave==null) ? 
					UGen.ar("*", UGen.ar("SinOsc", UGen.ir(num)), channelz[i]) : 
						UGen.ar("+", combowave, UGen.ar("*", UGen.ar("SinOsc", UGen.ir(num)), channelz[i]));
			i++;
			
			//combowave = UGen.ar("*",combowave,UGen.ir(0.75f));
			
			//combowave = UGen.ar("+", combowave, UGen.ar("*", UGen.ar("SinOsc", UGen.ir(num)), UGen.ir(0.05f)));
			
		}
		combowave = UGen.ar("*",combowave,UGen.ir(0.5f));
		
		//final GraphElem ch1 = UGen.ar("SinOsc", UGen.ir(440));
		

		//final GraphElem ch2 = UGen.ar("SinOsc", UGen.ir(466.16f));
		
		//final GraphElem combo = UGen.ar("+", ch1, ch2);
		final GraphElem ch1stereo = UGen.array(combowave, combowave);
		
		
		/*GraphElem f	= UGen.ar( "*", UGen.ar( "LFPulse", 
				UGen.kr( "MulAdd", UGen.kr( "FSinOsc", UGen.ir( 0.05f ), UGen.ir( 0 )), UGen.ir( 80 ), UGen.ir( 160 )),
					UGen.ir( 0 ), UGen.ir( 0.4f )), UGen.ir( 0.05f ));*/
		
//		GraphElem f	= UGen.ar( "RLPF", UGen.ar("SinOsc", UGen.ir(440)), UGen.kr( "MulAdd",
//						UGen.kr( "FSinOsc", UGen.array( UGen.ir( 0.6f ), UGen.ir( 0.7f )), UGen.ir( 0 )), UGen.ir( 3600 ), UGen.ir( 4000 )),
//							UGen.ir( 0.2f ));	
		
		/*UGen.ar( "*", UGen.ar( "LFPulse", 
				UGen.kr( "MulAdd", UGen.kr( "FSinOsc", UGen.ir( 0.05f ), UGen.ir( 0 )), UGen.ir( 80 ), UGen.ir( 160 )),
					UGen.ir( 0 ), UGen.ir( 0.4f )), UGen.ir( 0.05f ))*/
		
			//def = new SynthDef( "JPulseModulation", UGen.ar( "Out", UGen.ir( 0 ), f ));
		
		return new SynthDef("sinSummationTest", UGen.ar("Out",UGen.ir(0),ch1stereo));
	}
	
	private static SynthDef harmonicAMTest() {
		
		
		Control[] controlz = new Control[15];
		UGenChannel[] channelz = new UGenChannel[15];
		
		float[] freqlist = {300f, 600f, 900f, 1200f, 1500f, 1800f, 2100f, 2400f, 2700f, 3000f, 3300f, 3600f, 3900f, 4200f, 4500f};
		//Base note A440
		GraphElem combowave = null; 
		//= UGen.ar("SinOsc", UGen.ir(440));
		int i = 0;
		for (float num : freqlist) {
			
			controlz[i] = Control.kr(new String[] {"ch"+i}, new float[] {0.05f});
			channelz[i] = controlz[i].getChannel(0);
			
			combowave = (combowave==null) ? 
					UGen.ar("*", UGen.ar("SinOsc", UGen.ir(num)), channelz[i]) : 
						UGen.ar("+", combowave, UGen.ar("*", UGen.ar("SinOsc", UGen.ir(num)), channelz[i]));
			i++;
			
			//combowave = UGen.ar("+", combowave, UGen.ar("*", UGen.ar("SinOsc", UGen.ir(num)), UGen.ir(0.05f)));
			
		}
		
		final GraphElem ch1stereo = UGen.array(combowave, combowave);
		
		return new SynthDef("harmonicAMTest", UGen.ar("Out",UGen.ir(0),ch1stereo));
		
	}
	
	private static SynthDef phantomTest() {
		
		
		float[] freqlist = {300f, 600f, 900f, 1200f, 1500f};
		
		GraphElem combowave = null;
		
		Control[] controlz = new Control[5];
		UGenChannel[] channelz = new UGenChannel[5];
		
		int i = 0;
		for (float num : freqlist) {
			
			controlz[i] = Control.kr(new String[] {"ch"+i}, new float[] {0.05f});
			channelz[i] = controlz[i].getChannel(0);
			
			combowave = (combowave==null) ? 
					UGen.ar("*", UGen.ar("SinOsc", UGen.ir(num)), channelz[i]) : 
						UGen.ar("+", combowave, UGen.ar("*", UGen.ar("SinOsc", UGen.ir(num)), channelz[i]));
			i++;
			
			//combowave = UGen.ar("+", combowave, UGen.ar("*", UGen.ar("SinOsc", UGen.ir(num)), UGen.ir(0.05f)));
			
		}

		
		final GraphElem ch1stereo = UGen.array(combowave, combowave);
		
		return new SynthDef("harmonicTest", UGen.ar("Out",UGen.ir(0),ch1stereo));		
	}
	
	private static SynthDef harmonicFMTest() {
		
		
		Control[] controlz = new Control[15];
		Control[] volcontrolz = new Control[15];
		UGenChannel[] channelz = new UGenChannel[15];
		UGenChannel[] volchannelz = new UGenChannel[15];
		
		float[] freqlist = {300f, 600f, 900f, 1200f, 1500f, 1800f, 2100f, 2400f, 2700f, 3000f, 3300f, 3600f, 3900f, 4200f, 4500f};
		//Base note A440
		GraphElem combowave = null; 
		//= UGen.ar("SinOsc", UGen.ir(440));
		int i = 0;
		for (float num : freqlist) {
			
			controlz[i] = Control.kr(new String[] {"ch"+i}, new float[] {num});
			volcontrolz[i] = Control.kr(new String[] {"chv"+i}, new float[] {0.0f});
			channelz[i] = controlz[i].getChannel(0);
			volchannelz[i] = volcontrolz[i].getChannel(0);
			
			combowave = (combowave==null) ? 
					UGen.ar("*", UGen.ar("SinOsc", channelz[i]), volchannelz[i]) : 
						UGen.ar("+", combowave, UGen.ar("*", UGen.ar("SinOsc", channelz[i]), volchannelz[i]));
			i++;
			
			//combowave = UGen.ar("+", combowave, UGen.ar("*", UGen.ar("SinOsc", UGen.ir(num)), UGen.ir(0.05f)));
			
		}
		
		final GraphElem ch1stereo = UGen.array(combowave, combowave);
		
		return new SynthDef("harmonicTest", UGen.ar("Out",UGen.ir(0),ch1stereo));
		
	}
	
	
	//vary intensity of beat notes (reference tone + beat notes)
	private static SynthDef harmonicFMTest2() {
		
		
		Control[] controlz = new Control[16];
		UGenChannel[] channelz = new UGenChannel[16];
		
		float[] freqlist = {300f, 600f, 900f, 1200f, 1500f, 1800f, 2100f, 2400f, 2700f, 3000f, 3300f, 3600f, 3900f, 4200f, 4500f, 4800f};
		float[] beatnotes = {302f, 602f, 902f, 1202f, 1502f, 1802f, 2102f, 2402f, 2702f, 3002f, 3302f, 3602f, 3902f, 4202f, 4502f, 4802f};
		//Base note A440
		GraphElem combowave = null; 
		//= UGen.ar("SinOsc", UGen.ir(440));
		int i = 0;
		for (float num : freqlist) {
			
			controlz[i] = Control.kr(new String[] {"ch"+i}, new float[] {num});
			channelz[i] = controlz[i].getChannel(0);
			
			
			//reference tone
			combowave = (combowave==null) ? 
					UGen.ar("SinOsc", UGen.ir(num), UGen.ir(0.2f)) : 
						UGen.ar("+", combowave,  UGen.ar("SinOsc", UGen.ir(num), UGen.ir(0.2f)));
					
					
			//beat note with control channel		
			combowave = UGen.ar("+", combowave, UGen.ar("*", UGen.ar("SinOsc", UGen.ir(beatnotes[i])), channelz[i]));
			
//			combowave = (combowave==null) ? 
//					UGen.ar("*", UGen.ar("SinOsc", channelz[i]), UGen.ir(0.1f)) : 
//						UGen.ar("+", combowave, UGen.ar("*", UGen.ar("SinOsc", channelz[i]), UGen.ir(0.1f)));
			i++;
			
			//combowave = UGen.ar("+", combowave, UGen.ar("*", UGen.ar("SinOsc", UGen.ir(num)), UGen.ir(0.05f)));
			
		}
		
		final GraphElem ch1stereo = UGen.array(combowave, combowave);
		
		return new SynthDef("harmonicTest", UGen.ar("Out",UGen.ir(0),ch1stereo));
		
	}
	
	
	private static SynthDef harmonicFMTest2modified() {
		
		
		Control[] controlz = new Control[16];
		Control[] volcontrolz = new Control[16];
		UGenChannel[] channelz = new UGenChannel[16];
		UGenChannel[] volchannelz = new UGenChannel[16];
		
		float[] freqlist = {300f, 600f, 900f, 1200f, 1500f, 1800f, 2100f, 2400f, 2700f, 3000f, 3300f, 3600f, 3900f, 4200f, 4500f, 4800f};
		float[] beatnotes = {302f, 602f, 902f, 1202f, 1502f, 1802f, 2102f, 2402f, 2702f, 3002f, 3302f, 3602f, 3902f, 4202f, 4502f, 4802f};
		//Base note A440
		GraphElem combowave = null; 
		//= UGen.ar("SinOsc", UGen.ir(440));
		int i = 0;
		for (float num : freqlist) {
			
			controlz[i] = Control.kr(new String[] {"ch"+i}, new float[] {num});
			volcontrolz[i] = Control.kr(new String[] {"chv"+i}, new float[] {0.0f});
			channelz[i] = controlz[i].getChannel(0);
			volchannelz[i] = volcontrolz[i].getChannel(0);
			
			
			//reference tone
			combowave = (combowave==null) ? 
					UGen.ar("SinOsc", UGen.ir(num), channelz[i]) : 
						UGen.ar("+", combowave,  UGen.ar("SinOsc", UGen.ir(num), channelz[i]));
					
					
			//beat note with control channel		
			combowave = UGen.ar("+", combowave, UGen.ar("*", UGen.ar("SinOsc", UGen.ir(beatnotes[i])), channelz[i]));
			
//			combowave = (combowave==null) ? 
//					UGen.ar("*", UGen.ar("SinOsc", channelz[i]), UGen.ir(0.1f)) : 
//						UGen.ar("+", combowave, UGen.ar("*", UGen.ar("SinOsc", channelz[i]), UGen.ir(0.1f)));
			i++;
			
			//combowave = UGen.ar("+", combowave, UGen.ar("*", UGen.ar("SinOsc", UGen.ir(num)), UGen.ir(0.05f)));
			
		}
		
		final GraphElem ch1stereo = UGen.array(combowave, combowave);
		
		return new SynthDef("harmonicTest", UGen.ar("Out",UGen.ir(0),ch1stereo));
		
	}	
	//vary distance of beat notes (reference tone + beat notes)
	private static SynthDef harmonicFMTest3() {
		
		
		Control[] controlz = new Control[16];
		UGenChannel[] channelz = new UGenChannel[16];
		
		float[] freqlist = {300f, 600f, 900f, 1200f, 1500f, 1800f, 2100f, 2400f, 2700f, 3000f, 3300f, 3600f, 3900f, 4200f, 4500f};
		//float[] beatnotes = {301f, 601f, 901f, 1201f, 1501f, 1801f, 2101f, 2401f, 2701f, 3001f, 3301f, 3600f, 3901f, 4201f, 4501f};
		
		//Base note A440
		GraphElem combowave = null; 
		//= UGen.ar("SinOsc", UGen.ir(440));
		int i = 0;
		for (float num : freqlist) {
			
			controlz[i] = Control.kr(new String[] {"ch"+i}, new float[] {num});
			channelz[i] = controlz[i].getChannel(0);
			
			//reference tone
			combowave = (combowave==null) ? 
					UGen.ar("SinOsc", UGen.ir(num), UGen.ir(0.1f)) : 
						UGen.ar("+", combowave,  UGen.ar("SinOsc", UGen.ir(num), UGen.ir(0.1f)));
					
					
			//beat note with control channel		
			combowave = UGen.ar("+", combowave, UGen.ar("SinOsc", channelz[i], UGen.ir(0.2f)));
			
			
			i++;
			
			//combowave = UGen.ar("+", combowave, UGen.ar("*", UGen.ar("SinOsc", UGen.ir(num)), UGen.ir(0.05f)));
			
		}
		
		combowave = UGen.ar("*",combowave,UGen.ir(0.5f));
		
		final GraphElem ch1stereo = UGen.array(combowave, combowave);
		
		return new SynthDef("harmonicTest", UGen.ar("Out",UGen.ir(0),ch1stereo));
		
	}

	private static SynthDef harmonicFMTest3modified() {
		
		
		Control[] controlz = new Control[16];
		Control[] volcontrolz = new Control[16];
		UGenChannel[] channelz = new UGenChannel[16];
		UGenChannel[] volchannelz = new UGenChannel[16];
		
		float[] freqlist = {300f, 600f, 900f, 1200f, 1500f, 1800f, 2100f, 2400f, 2700f, 3000f, 3300f, 3600f, 3900f, 4200f, 4500f};
		//float[] beatnotes = {301f, 601f, 901f, 1201f, 1501f, 1801f, 2101f, 2401f, 2701f, 3001f, 3301f, 3600f, 3901f, 4201f, 4501f};
		
		//Base note A440
		GraphElem combowave = null; 
		//= UGen.ar("SinOsc", UGen.ir(440));
		int i = 0;
		for (float num : freqlist) {
			
			controlz[i] = Control.kr(new String[] {"ch"+i}, new float[] {num});
			volcontrolz[i] = Control.kr(new String[] {"chv"+i}, new float[] {0.0f});
			channelz[i] = controlz[i].getChannel(0);
			volchannelz[i] = volcontrolz[i].getChannel(0);
			
			//reference tone
			combowave = (combowave==null) ? 
					UGen.ar("SinOsc", UGen.ir(num), UGen.ir(0.05f)) : 
						UGen.ar("+", combowave,  UGen.ar("SinOsc", UGen.ir(num), UGen.ir(0.05f)));
					
					
			//beat note with control channel		
			combowave = UGen.ar("+", combowave, UGen.ar("SinOsc", channelz[i], volchannelz[i]));
			
			
			i++;
			
			//combowave = UGen.ar("+", combowave, UGen.ar("*", UGen.ar("SinOsc", UGen.ir(num)), UGen.ir(0.05f)));
			
		}
		
		combowave = UGen.ar("*",combowave,UGen.ir(0.5f));
		
		final GraphElem ch1stereo = UGen.array(combowave, combowave);
		
		return new SynthDef("harmonicTest", UGen.ar("Out",UGen.ir(0),ch1stereo));
		
	}	
	
	
	
	//vary intensity of beat notes (stacked pair)
	private static SynthDef harmonicFMTest4() {
		
		
		Control[] controlz = new Control[16];
		UGenChannel[] channelz = new UGenChannel[16];
		
		float[] freqlist = {300f, 600f, 900f, 1200f, 1500f, 1800f, 2100f, 2400f
						, 301f, 601f, 901f, 1201f, 1501f, 1801f, 2101f, 2401f};
		//float[] beatnotes = {301f, 601f, 901f, 1201f, 1501f, 1801f, 2101f, 2401f, 2701f, 3001f, 3301f, 3600f, 3901f, 4201f, 4501f};
		//Base note A440
		GraphElem combowave = null; 
		//= UGen.ar("SinOsc", UGen.ir(440));
		int i = 0;
		for (float num : freqlist) {
			
			controlz[i] = Control.kr(new String[] {"ch"+i}, new float[] {num});
			channelz[i] = controlz[i].getChannel(0);
			
			combowave = (combowave==null) ? 
					UGen.ar("*", UGen.ar("SinOsc", UGen.ir(num)), channelz[i]) : 
						UGen.ar("+", combowave, UGen.ar("*", UGen.ar("SinOsc", UGen.ir(num)), channelz[i]));
			
//			combowave = (combowave==null) ? 
//					UGen.ar("*", UGen.ar("SinOsc", channelz[i]), UGen.ir(0.1f)) : 
//						UGen.ar("+", combowave, UGen.ar("*", UGen.ar("SinOsc", channelz[i]), UGen.ir(0.1f)));
					i++;
					
					//combowave = UGen.ar("+", combowave, UGen.ar("*", UGen.ar("SinOsc", UGen.ir(num)), UGen.ir(0.05f)));
					
		}
		
		combowave = UGen.ar("*",combowave,UGen.ir(0.5f));
		
		final GraphElem ch1stereo = UGen.array(combowave, combowave);
		
		
		
		return new SynthDef("harmonicTest", UGen.ar("Out",UGen.ir(0),ch1stereo));
		
	}
	
	//vary distance of beat notes (stacked pair)
	private static SynthDef harmonicFMTest5() {
		
		
		Control[] controlz = new Control[15];
		UGenChannel[] channelz = new UGenChannel[15];
		
		float[] freqlist = {300f, 600f, 900f, 1200f, 1500f, 1800f, 2100f, 2400f, 2700f, 3000f, 3300f, 3600f, 3900f, 4200f, 4500f};
		//float[] beatnotes = {301f, 601f, 901f, 1201f, 1501f, 1801f, 2101f, 2401f, 2701f, 3001f, 3301f, 3600f, 3901f, 4201f, 4501f};
		//Base note A440
		GraphElem combowave = null; 
		//= UGen.ar("SinOsc", UGen.ir(440));
		int i = 0;
		for (float num : freqlist) {
			
			controlz[i] = Control.kr(new String[] {"ch"+i}, new float[] {harmonicstacked[i]});
			channelz[i] = controlz[i].getChannel(0);
			
			combowave = (combowave==null) ? 
					UGen.ar("*", UGen.ar("SinOsc", channelz[i]), UGen.ir(0.1f)) : 
						UGen.ar("+", combowave, UGen.ar("*", UGen.ar("SinOsc", channelz[i]), UGen.ir(0.1f)));
			i++;
					
					//combowave = UGen.ar("+", combowave, UGen.ar("*", UGen.ar("SinOsc", UGen.ir(num)), UGen.ir(0.05f)));
					
		}
		
		final GraphElem ch1stereo = UGen.array(combowave, combowave);
		
		return new SynthDef("harmonicTest", UGen.ar("Out",UGen.ir(0),ch1stereo));
		
	}
	
	private static SynthDef harmonicFMTest6() {
		
		Control[] controlz = new Control[16];
		Control[] volcontrolz = new Control[16];
		UGenChannel[] channelz = new UGenChannel[16];
		UGenChannel[] volchannelz = new UGenChannel[16];
		
		//float[] freqlist = {300f, 600f, 900f, 1200f, 1500f, 1800f, 2100f, 2400f, 2700f, 3000f, 3300f, 3600f, 3900f, 4200f, 4500f};
		float[] freqlist = {300f, 900f, 1500f, 1800f, 2400f, 3000f, 3600f, 4200f, 4800f, 5400f, 6000f, 6600f, 7200f, 7800f, 8400f};
		//float[] beatnotes = {301f, 601f, 901f, 1201f, 1501f, 1801f, 2101f, 2401f, 2701f, 3001f, 3301f, 3600f, 3901f, 4201f, 4501f};
		
		//Base note A440
		GraphElem combowave = null; 
		//= UGen.ar("SinOsc", UGen.ir(440));
		int i = 0;
		for (float num : freqlist) {
			
			controlz[i] = Control.kr(new String[] {"ch"+i}, new float[] {num});
			volcontrolz[i] = Control.kr(new String[] {"chv"+i}, new float[] {0.0f});
			channelz[i] = controlz[i].getChannel(0);
			volchannelz[i] = volcontrolz[i].getChannel(0);
			
			combowave = (combowave==null) ? 
					UGen.ar("*", UGen.ar("SinOsc", channelz[i]), volchannelz[i]) : 
						UGen.ar("+", combowave, UGen.ar("*", UGen.ar("SinOsc", channelz[i]), volchannelz[i]));
			
			
			i++;
			
			//combowave = UGen.ar("+", combowave, UGen.ar("*", UGen.ar("SinOsc", UGen.ir(num)), UGen.ir(0.05f)));
			
		}
		
		combowave = UGen.ar("*",combowave,UGen.ir(0.5f));
		
		final GraphElem ch1stereo = UGen.array(combowave, combowave);
		
		return new SynthDef("harmonicTest", UGen.ar("Out",UGen.ir(0),ch1stereo));
		
	}
	
	
	private static SynthDef trueFM1() {
		
		Control[] controlz = new Control[16];
		UGenChannel[] channelz = new UGenChannel[16];		
		
		float basefreq = 440.0f;
		
		//float[] modpartials = {110f, 220f, 440f, 880f, 1320f, 1760f, 2200f, 2640f, 3080f, 3520f, 3960f, 4400f, 4840f, 5280f, 5720f, 6160f};
		
		float[] modpartials = {0.25f, 0.5f, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 11f, 12f, 13f, 14f}; 
		
		//float[] carpartials = {};
		GraphElem combowave = null;
		
		for (int i = 0; i< modpartials.length; i++) {
			
			controlz[i] = Control.kr(new String[] {"ch"+i}, new float[] {basefreq*modpartials[i]});
			channelz[i] = controlz[i].getChannel(0);
			
			GraphElem mod = UGen.ar("SinOsc",channelz[i], UGen.ir(0.1f));
			
			GraphElem car = UGen.ar("SinOsc",UGen.ar("+",UGen.ir(basefreq),mod), UGen.ir(0.1f));
			
			//GraphElem car = UGen.ar("SinOsc",mod, UGen.ir(0.1f));
			
			combowave = (combowave==null) ?
					car :
					UGen.ar("+",combowave,car);
					
		}

		combowave = UGen.ar("*",combowave,UGen.ir(0.5f));
		
		final GraphElem ch1stereo = UGen.array(combowave,combowave);
		
		return new SynthDef("trueFM1", UGen.ar("Out",UGen.ir(0),ch1stereo));
	}
	
	
	
	private static File findFile(final String fileName, final String[] folders) {
		File f;

		for (int i = 0; i < folders.length; i++) {
			f = new File(folders[i], fileName);
			if (f.exists()) return f;
		}
		return null;
	}

	private static void reportError(final Exception e) {
		IJ.handleException(e);
	}	


}
