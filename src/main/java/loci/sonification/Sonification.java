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
import de.sciss.jcollider.UGenInfo;
import de.sciss.jcollider.gui.ServerPanel;
import ij.IJ;
import ij.ImageJ;
import ij.plugin.PlugIn;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Font;
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
import java.util.Random;

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

	public static Font fntGUI = ServerPanel.fntGUI;

	protected final SynthDefTable[] defTables = new SynthDefTable[1];
	protected SynthDefTable selectedTable = null;

	protected static final SynthDefNameComp synthDefNameComp =
		new SynthDefNameComp();

	protected Server server = null;
	protected NodeWatcher nw = null;
	protected Group grpAll;

	private static final String[] tableNames = { "JCollider" };

	protected final Sonification enc_this = this;

	public Sonification() {
		super("Sonification");
	}

	public void initialize() {
		final Box b = Box.createHorizontalBox();
		final Box b2 = Box.createHorizontalBox();
		final Container cp = getContentPane();
		final JTextField ggAppPath = new JTextField(32);
		final String fs = File.separator;
		JScrollPane ggScroll;
		JLabel lb;
		JFrame spf = null;

		for (int i = 0; i < 1; i++) {
			defTables[i] = new SynthDefTable(tableNames[i]);
			ggScroll = new JScrollPane(defTables[i]);
			b.add(ggScroll);
			defTables[i].getSelectionModel().addListSelectionListener(
				new TableSelListener(i));
			if (i == 1) {
				ggScroll.setTransferHandler(new SynthDefFileTransferHandler(1));
				ggScroll.setToolTipText("Drop SynthDef Files from the Finder here");
			}
		}

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

		JCollider.setDeepFont(cp, fntGUI);

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

				IJ.log("disposing server window: " + e.getWindow());
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

	private JComponent createButtons() {
		final Box b = Box.createHorizontalBox();
		JButton but;

		but = new JButton(new ActionPlay());
		but.setToolTipText("Play Selected SynthDef");
		b.add(but);
		but = new JButton(new ActionStop());
		but.setToolTipText("Stop All Synths");
		b.add(but);

		return b;
	}

	@Override
	public void run(final String arg) {
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				initialize();
			}
		});

	}

	private void createDefs() {

		IJ.log("Creating Defs");
		try {
			UGenInfo.readBinaryDefinitions();

			final List<SynthDef> collDefs = SonificationDefs.create();
			Collections.sort(collDefs, synthDefNameComp);
			defTables[0].addDefs(collDefs);
		}
		catch (final IOException e1) {
			IJ.handleException(e1);
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
		List<SynthDef> defs;
		SynthDef def;

		for (int i = 0; i < defTables.length; i++) {
			defs = defTables[i].getDefs();
			for (int j = 0; j < defs.size(); j++) {
				def = defs.get(j);
				try {
					def.send(server);
				}
				catch (final IOException e1) {
					System.err.println("Sending Def " + def.getName() + " : " +
						e1.getClass().getName() + " : " + e1.getLocalizedMessage());
				}
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

	public static void main(final String args[]) {
		System.out.println("Path=" + System.getenv("PATH"));
		new ImageJ();
		new Sonification().run("");

	}

	protected static void reportError(final Exception e) {
		System.err
			.println(e.getClass().getName() + " : " + e.getLocalizedMessage());
	}

// ------------- ServerListener interface -------------

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
						public void actionPerformed(final ActionEvent e) {
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

// ------------- FileFilter interface -------------

	@Override
	public boolean accept(final File f) {
		try {
			return SynthDef.isDefFile(f);
		}
		catch (final IOException e1) {
			return false;
		}
	}

// ------------- internal classes -------------

	private abstract static class SonificationDefs {

		private static List<SynthDef> create()

		{
			IJ.log("Creating Defs Part 2");
			final List<SynthDef> result = new ArrayList<SynthDef>();
			final Random rnd = new Random(System.currentTimeMillis());
			SynthDef def;
			GraphElem f, g, h;

			{
				final GraphElem clockRate =
					UGen.kr("MouseX", UGen.ir(1), UGen.ir(200), UGen.ir(1));
				final GraphElem clockTime = UGen.kr("reciprocal", clockRate);
				final GraphElem clock = UGen.kr("Impulse", clockRate, UGen.ir(0.4f));
				final GraphElem centerFreq =
					UGen.kr("MouseY", UGen.ir(100), UGen.ir(8000), UGen.ir(1));
				final GraphElem freq =
					UGen.kr("Latch", UGen.kr("MulAdd", UGen.kr("WhiteNoise"), UGen.kr(
						"*", centerFreq, UGen.ir(0.5f)), centerFreq), clock);
				final GraphElem panPos = UGen.kr("Latch", UGen.kr("WhiteNoise"), clock);

				f =
					UGen.ar("*", UGen.ar("SinOsc", freq), UGen.kr("Decay2", clock, UGen
						.kr("*", UGen.ir(0.1f), clockTime), UGen.kr("*", UGen.ir(0.9f),
						clockTime)));
				g = UGen.ar("Pan2", f, panPos);
				h = UGen.ar("CombN", g, UGen.ir(0.3f), UGen.ir(0.3f), UGen.ir(2));
				def =
					new SynthDef("JSampleAndHoldLiquid", UGen.ar("Out", UGen.ir(0), h));
				result.add(def);
			}

			return result;
		}

	}

	private static class SynthDefTable extends JTable {

		private final SynthDefTableModel tm;

		protected SynthDefTable(final String name) {
			super();
			tm = new SynthDefTableModel(name);
			setModel(tm);
			getColumnModel().getColumn(0).setPreferredWidth(128);
			setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		}

		protected void addDefs(final List<? extends SynthDef> defs) {
			tm.addDefs(defs);
		}

		protected SynthDef getSelectedDef() {
			final int row = getSelectedRow();
			if (row >= 0) return tm.getDef(row);
			else return null;
		}

		protected List<SynthDef> getDefs() {
			return tm.getDefs();
		}
	}

	private static class SynthDefTableModel extends AbstractTableModel {

		private final List<SynthDef> collDefs = new ArrayList<SynthDef>();
		private final String name;

		protected SynthDefTableModel(final String name) {
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
			else {
				return null;
			}
		}

		protected void addDefs(final List<? extends SynthDef> defs) {
			if (defs.isEmpty()) return;

			final int startRow = collDefs.size();
			collDefs.addAll(defs);
			fireTableRowsInserted(startRow, collDefs.size() - 1);
		}

		protected SynthDef getDef(final int idx) {
			return collDefs.get(idx);
		}

		protected List<SynthDef> getDefs() {
			return new ArrayList<SynthDef>(collDefs);
		}
	}

	private class ActionPlay extends AbstractAction {

		protected ActionPlay() {
			super("Play");
		}

		@Override
		public void actionPerformed(final ActionEvent e) {
			if (selectedTable == null) return;

			final SynthDef def = defTables[0].getDefs().get(0); // selectedTable.getSelectedDef();
			final Synth synth;

			if ((def != null) && (grpAll != null) && (server != null)) {
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

		protected ActionStop() {
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

		protected TableSelListener(final int idx) {
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

		protected SynthDefNameComp() { /* empty */}

		@Override
		public int compare(final SynthDef def1, final SynthDef def2) {
			return def1.getName().compareTo(def2.getName());
		}
	}

	private class SynthDefFileTransferHandler extends TransferHandler {

		private final int idx;

		protected SynthDefFileTransferHandler(final int idx) {
			this.idx = idx;
		}

		/**
		 * Overridden to import a Pathname if it is available.
		 */
		@Override
		public boolean importData(final JComponent c, final Transferable t) {
			final Object o;
			final List<File> fileList;
			final List<SynthDef> collDefs;
			File f;
			SynthDef[] defs;

			try {
				if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
					o = t.getTransferData(DataFlavor.javaFileListFlavor);
					if (o instanceof List) {
						fileList = (List<File>) o;
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
							Collections.sort(collDefs, synthDefNameComp);
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
