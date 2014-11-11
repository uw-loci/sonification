/*
 * #%L
 * An ImageJ plugin for listening to your images.
 * %%
 * Copyright (C) 2014 Board of Regents of the University of
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
import ij.IJ;
import ij.ImageJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.WindowConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;



public class Sonification extends JFrame implements FileFilter, ServerListener,
	Constants, PlugIn
{

	// -- Constants --

	private static final SynthDefNameComp SYNTH_DEF_NAME_COMP =
		new SynthDefNameComp();

	private static final String[] TABLE_NAMES = { "JCollider" };

	private static final boolean DEBUG = false;

	// -- Fields --

	private final SynthDefTable[] defTables = new SynthDefTable[1];
	private SynthDefTable selectedTable = null;
	
	private List<SynthDef> MemoryDefCollection = new ArrayList<SynthDef>(); 
	
	
	private Synth synth = null;
	private Server server = null;
	private NodeWatcher nw = null;
	private Group grpAll;

	private final Sonification enc_this = this;

	public Sonification() {
		super("Sonification");
	}

	// -- Sonification methods --

	public void initialize() {
		
		final Box b = Box.createHorizontalBox();
		final Box b2 = Box.createHorizontalBox();
		final Container cp = getContentPane();
		final JTextField ggAppPath = new JTextField(32);
		final String fs = File.separator;
		JScrollPane ggScroll;
		JLabel lb;
		JFrame spf = null;

//		for (int i = 0; i < 1; i++) {
//			defTables[i] = new SynthDefTable(TABLE_NAMES[i]);
//			ggScroll = new JScrollPane(defTables[i]);
//			b.add(ggScroll);
//			defTables[i].getSelectionModel().addListSelectionListener(
//				new TableSelListener(i));
////			if (i == 1) {
////				ggScroll.setTransferHandler(new SynthDefFileTransferHandler(1));
////				ggScroll.setToolTipText("Drop SynthDef Files from the Finder here");
////			}
//		}

		try {
			cp.setLayout(new BorderLayout());
			cp.add(b, BorderLayout.CENTER);

			server = new Server("localhost");
			createDefs();

			final File f =
				findFile(JCollider.isWindows ? "scsynth.exe" : "scsynth", new String[] {
					fs + "Applications" + fs + "SuperCollider" + fs +
						"SuperCollider.app" + fs + "Contents" + fs + "Resources",
					fs + "Applications" + fs + "SC3",
					fs + "usr" + fs + "local" + fs + "bin", fs + "usr" + fs + "bin",
					"C:\\Program Files\\SC3", "C:\\Program Files\\SuperCollider_f" });
			if (f != null) Server.setProgram(f.getAbsolutePath());

			ggAppPath.setText(Server.getProgram());
			ggAppPath.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(final ActionEvent e) {
					Server.setProgram(ggAppPath.getText());
				}
			});
			lb = new JLabel("Server App Path :");
			lb.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 4));
			cp.add(b2, BorderLayout.NORTH);
			cp.add(createButtons(), BorderLayout.SOUTH);

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
		}
		catch (final IOException e1) {
			JOptionPane.showMessageDialog(this, "Failed to create a server :\n" +
				e1.getClass().getName() + e1.getLocalizedMessage(), this.getTitle(),
				JOptionPane.ERROR_MESSAGE);
		}

		JCollider.setDeepFont(cp, ServerPanel.fntGUI);

		addWindowListener(new WindowAdapter() {

				
			@Override
			public void windowClosing(final WindowEvent e) {
				if (nw != null) {
					nw.dispose();
					nw = null;
				}
				if (server != null) {
					try {
						if (server.didWeBootTheServer()) server.quitAndWait();
						else if (grpAll != null) grpAll.free();
						server = null;
					}
					catch (final IOException e1) {
						reportError(e1);
					}
				}

				if (DEBUG) IJ.log("disposing server window: " + e.getWindow());
				e.getWindow().setVisible(false);
				e.getWindow().dispose();
			}
		});

		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

		if (spf != null) setLocation(spf.getX() + spf.getWidth() + 24, spf.getY());
		setSize(512, 512);
		setVisible(true);
		toFront();
	}

	// -- PlugIn methods --

	@Override
	public void run(final String arg) {
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				initialize();
			}
		});

	}

	// -- FileFilter methods --

	@Override
	public boolean accept(final File f) {
		try {
			return SynthDef.isDefFile(f);
		}
		catch (final IOException e1) {
			return false;
		}
	}

	// -- ServerListener methods --

	@Override
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

	// -- Main method --

	public static void main(final String args[]) {
		System.out.println("Path=" + System.getenv("PATH"));
		new ImageJ();
		
		new Sonification().run("");
	    //GenericDialog gd = new GenericDialog("New Image");
	    //.addStringField("Title: ", title);
	    //gd.addNumericField("Width: ", width, 0);
	    //gd.addNumericField("Height: ", height, 0);
	    //gd.showDialog();
	}

	// -- Helper methods --

	private JComponent createButtons() {
		final Box b = Box.createHorizontalBox();
		JButton but;

		but = new JButton(new ActionPlay());
		but.setToolTipText("Play Selected SynthDef");
		b.add(but);
		but = new JButton(new ActionStop());
		but.setToolTipText("Stop All Synths");
		b.add(but);
		but = new JButton(new ActionTune());
		b.add(but);

		return b;
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
			MemoryDefCollection.add(harmonicAMTest());
			//defTables[0].addDefs(collDefs);
		}
		catch (final IOException e1) {
			reportError(e1);
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
	

//	private static SynthDef jSampleAndHoldLiquid() {
//		final GraphElem clockRate =
//			UGen.kr("MouseX", UGen.ir(1), UGen.ir(200), UGen.ir(1));
//		final GraphElem clockTime = UGen.kr("reciprocal", clockRate);
//		final GraphElem clock = UGen.kr("Impulse", clockRate, UGen.ir(0.4f));
//		final GraphElem centerFreq =
//			UGen.kr("MouseY", UGen.ir(100), UGen.ir(8000), UGen.ir(1));
//		final GraphElem freq =
//			UGen.kr("Latch", UGen.kr("MulAdd", UGen.kr("WhiteNoise"), UGen.kr("*",
//				centerFreq, UGen.ir(0.5f)), centerFreq), clock);
//		final GraphElem panPos = UGen.kr("Latch", UGen.kr("WhiteNoise"), clock);
//
//		final GraphElem f =
//			UGen
//				.ar("*", UGen.ar("SinOsc", freq), UGen.kr("Decay2", clock, UGen.kr("*",
//					UGen.ir(0.1f), clockTime), UGen.kr("*", UGen.ir(0.9f), clockTime)));
//		final GraphElem g = UGen.ar("Pan2", f, panPos);
//		final GraphElem h =
//			UGen.ar("CombN", g, UGen.ir(0.3f), UGen.ir(0.3f), UGen.ir(2));
//		return new SynthDef("JSampleAndHoldLiquid", UGen.ar("Out", UGen.ir(0), h));
//	}
	
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
			
			controlz[i] = Control.kr(new String[] {"ch"+i}, new float[] {0.05f});
			channelz[i] = controlz[i].getChannel(0);
			
			combowave = (combowave==null) ? 
					UGen.ar("*", UGen.ar("SinOsc", UGen.ir(num)), channelz[i]) : 
						UGen.ar("+", combowave, UGen.ar("*", UGen.ar("SinOsc", UGen.ir(num)), channelz[i]));
			i++;
			
			//combowave = UGen.ar("+", combowave, UGen.ar("*", UGen.ar("SinOsc", UGen.ir(num)), UGen.ir(0.05f)));
			
		}
		
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
		
		return new SynthDef("harmonicTest", UGen.ar("Out",UGen.ir(0),ch1stereo));
		
	}
	
	private static SynthDef harmonicFMTest() {
		
		
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
		
		return new SynthDef("harmonicTest", UGen.ar("Out",UGen.ir(0),ch1stereo));
		
	}	
	

	// -- Helper classes --

	private static class SynthDefTable extends JTable {

		private final SynthDefTableModel tm;

		private SynthDefTable(final String name) {
			super();
			tm = new SynthDefTableModel(name);
			setModel(tm);
			getColumnModel().getColumn(0).setPreferredWidth(128);
			setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		}

		private void addDefs(final List<? extends SynthDef> defs) {
			tm.addDefs(defs);
		}

		private List<SynthDef> getDefs() {
			return tm.getDefs();
		}
	}

	private static class SynthDefTableModel extends AbstractTableModel {

		private final List<SynthDef> collDefs = new ArrayList<SynthDef>();
		private final String name;

		private SynthDefTableModel(final String name) {
			super();
			this.name = name;
		}

		@Override
		public String getColumnName(final int col) {
			return name;
		}

		@Override
		public int getRowCount() {
			return collDefs.size();
		}

		@Override
		public int getColumnCount() {
			return 1;
		}

		@Override
		public Object getValueAt(final int row, final int column) {
			if (row < collDefs.size()) {
				return (collDefs.get(row)).getName();
			}
			return null;
		}

		private void addDefs(final List<? extends SynthDef> defs) {
			if (defs.isEmpty()) return;

			final int startRow = collDefs.size();
			collDefs.addAll(defs);
			fireTableRowsInserted(startRow, collDefs.size() - 1);
		}

		private List<SynthDef> getDefs() {
			return new ArrayList<SynthDef>(collDefs);
		}
	}
	
	private class ActionTune extends AbstractAction {
		
		private ActionTune() {
			super("Tune");
		}
		
		@Override
		public void actionPerformed(final ActionEvent e) {
			
			//SynthDef tunedef = MemoryDefCollection.get(0);
			//Synth tunesynth;
			
			
			if(synth !=null && server!=null) {
				try {
					OSCBundle bndl = new OSCBundle(System.currentTimeMillis());
					bndl.addPacket(synth.setMsg("ch0",0.5f));
					server.sendBundle(bndl);
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
//			if(tunedef != null && grpAll != null && server!= null)
//				try {
//					tunesynth = Synth.basicNew(tunedef.getName(), server);
//					
//					//def.send(server, MemoryDefCollection.)
//				}
//				catch(IOException e2) {
//					System.err.println(e2);
//					
//				}
		}
	}

	private class ActionPlay extends AbstractAction {

		private ActionPlay() {
			super("Play");
		}

		@Override
		public void actionPerformed(final ActionEvent e) {
			//if (selectedTable == null) return;

			//final SynthDef def = defTables[0].getDefs().get(0);
			final SynthDef def = MemoryDefCollection.get(0);
			

			if (def != null && grpAll != null && server != null) {
				try {
					synth = Synth.basicNew(def.getName(), server);
					if (nw != null) nw.register(synth);
					server.sendMsg(synth.newMsg(grpAll));
				}
				catch (final IOException e1) {
					JCollider.displayError(enc_this, e1, "Play");
				}
			}
		}
	}

	private class ActionStop extends AbstractAction {

		private ActionStop() {
			super("Stop All");
		}

		@Override
		public void actionPerformed(final ActionEvent e) {
			if (grpAll != null) {
				try {
					grpAll.freeAll();
				}
				catch (final IOException e1) {
					JCollider.displayError(enc_this, e1, "Stop");
				}
			}
		}
	}

	private class TableSelListener implements ListSelectionListener {

		int idx;

		private TableSelListener(final int idx) {
			this.idx = idx;
		}

		@Override
		public void valueChanged(final ListSelectionEvent e) {
			if (defTables[idx].getSelectedRowCount() > 0) {
				selectedTable = defTables[idx];
				for (int i = 0; i < defTables.length; i++) {
					if ((i != idx) && defTables[i].getSelectedRowCount() > 0) {
						defTables[i].clearSelection();
					}
				}
			}
		}
	}

	private static class SynthDefNameComp implements Comparator<SynthDef> {

		private SynthDefNameComp() { /* empty */}

		@Override
		public int compare(final SynthDef def1, final SynthDef def2) {
			return def1.getName().compareTo(def2.getName());
		}
	}

	private class SynthDefFileTransferHandler extends TransferHandler {

		private final int idx;

		private SynthDefFileTransferHandler(final int idx) {
			this.idx = idx;
		}

		/**
		 * Overridden to import a Pathname if it is available.
		 */
		@Override
		public boolean importData(final JComponent c, final Transferable t) {
			final Object o;
			final List<SynthDef> collDefs;
			File f;
			SynthDef[] defs;

			try {
				if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
					o = t.getTransferData(DataFlavor.javaFileListFlavor);
					if (o instanceof List) {
						@SuppressWarnings("unchecked")
						final List<File> fileList = (List<File>) o;
						collDefs = new ArrayList<SynthDef>();
						for (int i = 0; i < fileList.size(); i++) {
							f = fileList.get(i);
							try {
								if (SynthDef.isDefFile(f)) {
									defs = SynthDef.readDefFile(f);
									for (int j = 0; j < defs.length; j++) {
										collDefs.add(defs[j]);
									}
								}
								else {
									System.err.println("Not a synth def file : " + f.getName());
								}
							}
							catch (final IOException e1) {
								JCollider.displayError(enc_this, e1, "Drop File");
							}
						}
						if (!collDefs.isEmpty()) {
							Collections.sort(collDefs, SYNTH_DEF_NAME_COMP);
							defTables[idx].addDefs(collDefs);
							return true;
						}
					}
				}
			}
			catch (final UnsupportedFlavorException e1) { /* ignored */}
			catch (final IOException e2) {
				JCollider.displayError(enc_this, e2, "Drop File");
			}

			return false;
		}

		@Override
		public boolean canImport(final JComponent c, final DataFlavor[] flavors) {
			for (int i = 0; i < flavors.length; i++) {
				if (flavors[i].equals(DataFlavor.javaFileListFlavor)) return true;
			}
			return false;
		}
	} // class PathTransferHandler

}
