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

import ij.*;
import ij.process.*;
import ij.gui.*;
//import java.awt.*;
import ij.plugin.*;
import ij.plugin.frame.*;
import javax.swing.WindowConstants;

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

import java.util.Random;
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
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;

import de.sciss.jcollider.*;
import de.sciss.jcollider.gui.*;

public class Sonification
extends JFrame
implements FileFilter, ServerListener, Constants, PlugIn
{
	public static Font	fntGUI	= ServerPanel.fntGUI;

	protected final SynthDefTable[] defTables = new SynthDefTable[ 1 ];
	protected SynthDefTable selectedTable	= null;
	
	protected static final Comparator synthDefNameComp = new SynthDefNameComp();
	
	protected Server		server	= null;
	protected NodeWatcher	nw		= null;
	protected Group			grpAll;
	
	//private static final String[] tableNames = { "JCollider", "Drop Zone" };
	private static final String[] tableNames = {"JCollider"};

	protected final Sonification enc_this	= this;
	
	public Sonification()
	{
		super( "Sonification" );
	}

	public void initialize()
	{
		final Box			b			= Box.createHorizontalBox();
		final Box			b2			= Box.createHorizontalBox();
		final Container		cp			= getContentPane();
		final JTextField	ggAppPath	= new JTextField( 32 );
		final String		fs			= File.separator;
		JScrollPane			ggScroll;
		JLabel				lb;
		JFrame				spf			= null;
		
		for( int i = 0; i < 1; i++ ) {
			defTables[ i ]	= new SynthDefTable( tableNames[ i ]);
			ggScroll		= new JScrollPane( defTables[ i ]);
			b.add( ggScroll );
			defTables[ i ].getSelectionModel().addListSelectionListener( new TableSelListener( i ));
			if( i == 1 ) {
				ggScroll.setTransferHandler( new SynthDefFileTransferHandler( 1 ));
				ggScroll.setToolTipText( "Drop SynthDef Files from the Finder here" );
			}
		}
		
//		defTables[0].setRowSelectionInterval(0,0);

		try {
			cp.setLayout( new BorderLayout() );
			cp.add( b, BorderLayout.CENTER );

			server = new Server( "localhost" );
//			loadDefs();
			createDefs();

			File f = findFile( JCollider.isWindows ? "scsynth.exe" : "scsynth", new String[] {
				fs + "Applications" + fs + "SuperCollider" + fs + "SuperCollider.app" + fs + "Contents" + fs + "Resources",
				fs + "Applications" + fs + "SC3",
				fs + "usr" + fs + "local" + fs + "bin",
				fs + "usr" + fs + "bin",
				"C:\\Program Files\\SC3",
				"C:\\Program Files\\SuperCollider_f"
			});
//			if( (f == null) && JCollider.isMacOS ) {
//				try {
//					f = MRJAdapter.findApplication( "SCjm" );
//					if( f != null ) f = new File( f.getParentFile(), "scsynth" );
//				}
//				catch( IOException e1 ) {}
//			}
			if( f != null ) Server.setProgram( f.getAbsolutePath() );

			ggAppPath.setText( Server.getProgram() );
			ggAppPath.addActionListener( new ActionListener() {
				public void actionPerformed( ActionEvent e )
				{
					Server.setProgram( ggAppPath.getText() );
				}
			});
			lb = new JLabel( "Server App Path :" );
			lb.setBorder( BorderFactory.createEmptyBorder( 2, 6, 2, 4 ));
			//b2.add( lb );
			//b2.add( ggAppPath );
			cp.add( b2, BorderLayout.NORTH );
			cp.add( createButtons(), BorderLayout.SOUTH );

			server.addListener( this );
			try {
				server.start();
				server.startAliveThread();
			}
			catch( IOException e1 ) { /* ignored */ }
//			if( server.isRunning() ) initServer();
			spf = ServerPanel.makeWindow( server, ServerPanel.MIMIC | ServerPanel.CONSOLE | ServerPanel.DUMP );
			spf.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		}
		catch( IOException e1 ) {
			JOptionPane.showMessageDialog( this, "Failed to create a server :\n" + e1.getClass().getName() +
				e1.getLocalizedMessage(), this.getTitle(), JOptionPane.ERROR_MESSAGE );
		}

		JCollider.setDeepFont( cp, fntGUI );

		addWindowListener( new WindowAdapter() {
			public void windowClosing( WindowEvent e )
			{
				if( nw != null ) {
					nw.dispose();
					nw = null;
				}
				if( server != null ) {
					try {
						if( server.didWeBootTheServer() ) server.quitAndWait();
						else if( grpAll != null ) grpAll.free();
						server = null;
					}
					catch( IOException e1 ) {
						reportError( e1 );
					}
				}

				IJ.log("disposing server window: " + e.getWindow());
				e.getWindow().setVisible( false );
				e.getWindow().dispose();
				//System.exit( 0 );
			}
		});
		
		setDefaultCloseOperation( DO_NOTHING_ON_CLOSE );
		
//		pack();
		if( spf != null ) setLocation( spf.getX() + spf.getWidth() + 24, spf.getY() );
		setSize( 512, 512 );
		setVisible( true );
		toFront();
//		defTables[0].setRowSelectionInterval(0,0);
	}
	
//	private void loadDefs()
//	{
//		final File[]			defFiles	= new File( "synthdefs" ).listFiles( this );
//		SynthDef[]				defs;
//		final List	collDefs	= new ArrayList();
//	
//		for( int i = 0; i < defFiles.length; i++ ) {
//			try {
//				defs = SynthDef.readDefFile( defFiles[ i ]);
//				for( int j = 0; j < defs.length; j++ ) {
//					collDefs.add( defs[ j ]);
//				}
//			}
//			catch( IOException e1 ) {
//				System.err.println( defFiles[ i ].getName() + " : " + e1.getClass().getName() +
//					" : " + e1.getLocalizedMessage() );
//			}
//		}
//		
//		Collections.sort( collDefs, synthDefNameComp );
//		defTables[ 0 ].addDefs( collDefs );
//	}
	
	private JComponent createButtons()
	{
		final Box	b	= Box.createHorizontalBox();
		JButton		but;
		
		but	= new JButton( new ActionPlay() );
		but.setToolTipText( "Play Selected SynthDef" );
		b.add( but );
		but = new JButton( new ActionStop() );
		but.setToolTipText( "Stop All Synths" );
		b.add( but );
		//but = new JButton( new ActionDiagram() );
		//but.setToolTipText( "Open Diagram For Selected SynthDef" );
		//b.add( but );
		//but = new JButton( new ActionDump() );
		//but.setToolTipText( "Dump Selected SynthDef To The System Console" );
		//b.add( but );
		//but = new JButton( new ActionSynthDefApiEx() );
		//but.setToolTipText( "Demo code from SynthDef API doc" );
		//b.add( but );
		//but = new JButton( new ActionNodeTree() );
		//but.setToolTipText( "View a Tree of all Nodes" );
		//b.add( but );
		
		return b;
	}
	
	public void run(String arg) {
		//ImagePlus imp = IJ.getImage();
		//IJ.run(imp, "Invert", "");
		//IJ.wait(1000);
		//IJ.run(imp, "Invert", "");

		SwingUtilities.invokeLater( new Runnable() {
			
			public void run()
			{
				initialize();				
			}
		});

		
	}
	
	private void createDefs()
	{

		IJ.log("Creating Defs");
		//IJ.handleException(new Exception("stacktrace"));
		try {
//			UGenInfo.readDefinitions();
			UGenInfo.readBinaryDefinitions();

			final List collDefs = SonificationDefs.create();
			Collections.sort( collDefs, synthDefNameComp );
//			defTables[ 1 ].addDefs( collDefs );
			defTables[ 0 ].addDefs( collDefs );
		}
		catch( IOException e1 ) {

			IJ.handleException(e1);
			//e1.printStackTrace();
//			reportError( e1 );
		}
	}

	private void initServer()
	throws IOException
	{
		sendDefs();
		if( !server.didWeBootTheServer() ) {
			server.initTree();
			server.notify( true );
		}
//		if( nw != null ) nw.dispose();
		nw		= NodeWatcher.newFrom( server );
		grpAll	= Group.basicNew( server );
		nw.register( server.getDefaultGroup() );
		nw.register( grpAll );
		server.sendMsg( grpAll.newMsg() );
	}

	private void sendDefs()
	{
		List	defs;
		SynthDef		def;
	
		for( int i = 0; i < defTables.length; i++ ) {
			defs = defTables[ i ].getDefs();
			for( int j = 0; j < defs.size(); j++ ) {
				def = (SynthDef) defs.get( j );
				try {
					def.send( server );
				}
				catch( IOException e1 ) {
					System.err.println( "Sending Def " + def.getName() + " : " +
						e1.getClass().getName() + " : " + e1.getLocalizedMessage() );
				}
			}
		}
	}

	private static File findFile( String fileName, String[] folders )
	{
		File f;
	
		for( int i = 0; i < folders.length; i++ ) {
			f = new File( folders[ i ], fileName );
			if( f.exists() ) return f;
		}
		return null;
	}

    public static void main( String args[] )
	{
    	System.out.println("Path="+System.getenv("PATH"));
    	new ImageJ();
    	new Sonification().run("");
    	
	}

	protected static void reportError( Exception e ) {
		System.err.println( e.getClass().getName() + " : " + e.getLocalizedMessage() );
	}

// ------------- ServerListener interface -------------

	public void serverAction( ServerEvent e )
	{
		switch( e.getID() ) {
		case ServerEvent.RUNNING:
			try {
				initServer();
			}
			catch( IOException e1 ) {
				reportError( e1 );
			}
			break;
		
		case ServerEvent.STOPPED:
			// re-run alive thread
			final javax.swing.Timer t = new javax.swing.Timer( 1000, new ActionListener() {
				public void actionPerformed( ActionEvent e )
				{
					try {
						if( server != null ) server.startAliveThread();
					}
					catch( IOException e1 ) {
						reportError( e1 );
					}
				}
			});
			t.setRepeats( false );
			t.start();
			break;
		
		default:
			break;
		}
	}

// ------------- FileFilter interface -------------

	public boolean accept( File f )
	{
		try {
			return SynthDef.isDefFile( f );
		}
		catch( IOException e1 ) {
			return false;
		}
	}
	
// ------------- internal classes -------------
	
	private abstract static class SonificationDefs
	{
		private static java.util.List create()
		
		{
			IJ.log("Creating Defs Part 2");
			final java.util.List result = new ArrayList();
			final Random rnd = new Random(System.currentTimeMillis());
			SynthDef def;
			GraphElem f,g,h;
			
			{
				GraphElem	clockRate	= UGen.kr( "MouseX", UGen.ir( 1 ), UGen.ir( 200 ), UGen.ir( 1 ));
				GraphElem	clockTime	= UGen.kr( "reciprocal", clockRate );
				GraphElem	clock		= UGen.kr( "Impulse", clockRate, UGen.ir( 0.4f ));
				GraphElem	centerFreq	= UGen.kr( "MouseY", UGen.ir( 100 ), UGen.ir( 8000 ), UGen.ir( 1 ));
				GraphElem	freq		= UGen.kr( "Latch", UGen.kr( "MulAdd", UGen.kr( "WhiteNoise" ),
											UGen.kr( "*", centerFreq, UGen.ir( 0.5f )), centerFreq ), clock );
				GraphElem	panPos		= UGen.kr( "Latch", UGen.kr( "WhiteNoise" ), clock );

				f	= UGen.ar( "*", UGen.ar( "SinOsc", freq ), UGen.kr( "Decay2", clock,
						UGen.kr( "*", UGen.ir( 0.1f ), clockTime ), UGen.kr( "*", UGen.ir( 0.9f ), clockTime )));
				g	= UGen.ar( "Pan2", f, panPos );
				h	= UGen.ar( "CombN", g, UGen.ir( 0.3f ), UGen.ir( 0.3f ), UGen.ir( 2 ));
				def = new SynthDef( "JSampleAndHoldLiquid", UGen.ar( "Out", UGen.ir( 0 ), h ));
				result.add( def );
			}
			
			return result;
		}
		
		
	}
	
	private static class SynthDefTable
	extends JTable
	{
		private final SynthDefTableModel tm;
	
		protected SynthDefTable( String name )
		{
			super();
			tm = new SynthDefTableModel( name );
			setModel( tm );
			getColumnModel().getColumn( 0 ).setPreferredWidth( 128 );
			setSelectionMode( ListSelectionModel.SINGLE_SELECTION );
		}
		
//		private void addDef( SynthDef def )
//		{
//			tm.addDef( def );
//		}

		protected void addDefs( List defs )
		{
			tm.addDefs( defs );
		}
		
		protected SynthDef getSelectedDef()
		{
			final int row = getSelectedRow();
			if( row >= 0 ) return tm.getDef( row );
			else return null;
		}

		protected List getDefs()
		{
			return tm.getDefs();
		}
	}
	
	private static class SynthDefTableModel
	extends AbstractTableModel
	{
		private final List collDefs = new ArrayList();
		private final String name;

		protected SynthDefTableModel( String name )
		{
			super();
			this.name = name;
		}
		
		public String getColumnName( int col )
		{
			return name;
		}

		public int getRowCount()
		{
			return collDefs.size();
		}
		
		public int getColumnCount()
		{
			return 1;
		}
		
		public Object getValueAt( int row, int column )
		{
			if( row < collDefs.size() ) {
				return ((SynthDef) collDefs.get( row )).getName();
			} else {
				return null;
			}
		}

//		private void addDef( SynthDef def )
//		{
//			collDefs.add( def );
//			fireTableRowsInserted( collDefs.size() - 1, collDefs.size() - 1 );
//		}

		protected void addDefs( List defs )
		{
			if( defs.isEmpty() ) return;
		
			final int startRow = collDefs.size();
			collDefs.addAll( defs );
			fireTableRowsInserted( startRow, collDefs.size() - 1 );
		}

		protected SynthDef getDef( int idx )
		{
			return (SynthDef) collDefs.get( idx );
		}

//		private int getNumDefs()
//		{
//			return collDefs.size();
//		}

		protected List getDefs()
		{
			return new ArrayList( collDefs );
		}
	}
	
	private class ActionPlay
	extends AbstractAction
	{
		protected ActionPlay()
		{
			super( "Play" );			
		}
	
		public void actionPerformed( ActionEvent e )
		{
			if( selectedTable == null ) return;
			
			final SynthDef	def		=  (SynthDef)defTables[0].getDefs().get(0);   	//selectedTable.getSelectedDef();
			final Synth		synth;
			
			if( (def != null) && (grpAll != null) && (server != null) ) {
				try {
					synth	= Synth.basicNew( def.getName(), server );
					if( nw != null ) nw.register( synth );
					server.sendMsg( synth.newMsg( grpAll ));
				}
				catch( IOException e1 ) {
					JCollider.displayError( enc_this, e1, "Play" );
				}
			}
		}
	}

	private class ActionStop
	extends AbstractAction
	{
		protected ActionStop()
		{
			super( "Stop All" );
		}
	
		public void actionPerformed( ActionEvent e )
		{
			if( grpAll != null ) {
				try {
					grpAll.freeAll();
				}
				catch( IOException e1 ) {
					JCollider.displayError( enc_this, e1, "Stop" );
				}
			}
		}
	}
	
	private class TableSelListener
	implements ListSelectionListener
	{
		int idx;
	
		protected TableSelListener( int idx )
		{
			this.idx	= idx;
		}

		public void valueChanged( ListSelectionEvent e )
		{
			if( defTables[ idx ].getSelectedRowCount() > 0 ) {
				selectedTable = defTables[ idx ];
				for( int i = 0; i < defTables.length; i++ ) {
					if( (i != idx) && defTables[ i ].getSelectedRowCount() > 0 ) {
						defTables[ i ].clearSelection();
					}
				}
			}
		}
	}

//	private class ActionDiagram
//	extends AbstractAction
//	{
//		protected ActionDiagram()
//		{
//			super( "Def Diagram" );			
//		}
//	
//		public void actionPerformed( ActionEvent e )
//		{
//			if( selectedTable == null ) return;
//			
//			final SynthDef def = selectedTable.getSelectedDef();
//			if( def != null ) {
//				new SynthDefDiagram( def );
//			}
//		}
//	}
	
//	private class ActionDump
//	extends AbstractAction
//	{
//		protected ActionDump()
//		{
//			super( "Def Dump" );			
//		}
//	
//		public void actionPerformed( ActionEvent e )
//		{
//			if( selectedTable == null ) return;
//			
//			final SynthDef def = selectedTable.getSelectedDef();
//			if( def != null ) {
//				def.printOn( System.out );
//			}
//		}
//	}

//	private class ActionSynthDefApiEx
//	extends AbstractAction
//	{
//		protected ActionSynthDefApiEx()
//		{
//			super( "API Ex" );
//		}
//	
//		public void actionPerformed( ActionEvent e )
//		{
//			SonificationDefs.synthDefApiExample( server );	// doesn't inform nodewatcher though
//		}
//	}

//	private class ActionNodeTree
//	extends AbstractAction
//	{
//		protected ActionNodeTree()
//		{
//			super( "Node Tree" );			
//		}
//	
//		public void actionPerformed( ActionEvent e )
//		{
//			if( (server == null) || (nw == null) || (grpAll == null) ) return;
//		
//			final NodeTreePanel		ntp			= new NodeTreePanel( nw, grpAll );
//			final JFrame			treeFrame	= ntp.makeWindow();
//			
//			treeFrame.addWindowListener( new WindowAdapter() {
//				public void windowClosing( WindowEvent e )
//				{
//					treeFrame.setVisible( false );
//					treeFrame.dispose();
//					ntp.dispose();
//				}
//			});
//		}
//	}

	private static class SynthDefNameComp
	implements Comparator
	{
		protected SynthDefNameComp() { /* empty */ }
		
		public int compare( Object def1, Object def2 )
		{
			return( ((SynthDef) def1).getName().compareTo( ((SynthDef) def2).getName() ));
		}
	}

	private class SynthDefFileTransferHandler
	extends TransferHandler
	{
		private final int idx;
	
		protected SynthDefFileTransferHandler( int idx )
		{
			this.idx = idx;
		}

		/**
		 * Overridden to import a Pathname if it is available.
		 */
		public boolean importData( JComponent c, Transferable t )
		{
			final Object			o;
			final List				fileList;
			final List				collDefs;
			File					f;
			SynthDef[]				defs;
		
			try {
				if( t.isDataFlavorSupported( DataFlavor.javaFileListFlavor )) {
					o =  t.getTransferData( DataFlavor.javaFileListFlavor );
					if( o instanceof List ) {
						fileList	= (List) o;
						collDefs	= new ArrayList();
						for( int i = 0; i < fileList.size(); i++ ) {
							f = (File) fileList.get( i );
							try {
								if( SynthDef.isDefFile( f )) {
									defs = SynthDef.readDefFile( f );
									for( int j = 0; j < defs.length; j++ ) {
										collDefs.add( defs[ j ]);
									}
								} else {
									System.err.println( "Not a synth def file : " + f.getName() );
								}
							}
							catch( IOException e1 ) {
								JCollider.displayError( enc_this, e1, "Drop File" );
							}
						}
						if( !collDefs.isEmpty() ) {
							Collections.sort( collDefs, synthDefNameComp );
							defTables[ idx ].addDefs( collDefs );
							return true;
						}
					}
				}
			}
			catch( UnsupportedFlavorException e1 ) { /* ignored */ }
			catch( IOException e2 ) {
				JCollider.displayError( enc_this, e2, "Drop File" );
			}

			return false;
		}
		
		public boolean canImport( JComponent c, DataFlavor[] flavors )
		{
			for( int i = 0; i < flavors.length; i++ ) {
				if( flavors[i].equals( DataFlavor.javaFileListFlavor )) return true;
			}
			return false;
		}
	} // class PathTransferHandler
}