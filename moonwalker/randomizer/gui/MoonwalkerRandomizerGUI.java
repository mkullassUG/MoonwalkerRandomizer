/*
    Copyright (C) 2020 Micha³ Kullass

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package moonwalker.randomizer.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import moonwalker.core.utils.MoonwalkerMetadata;
import moonwalker.core.utils.OutOfSpaceException;
import moonwalker.core.utils.REV00Metadata;
import moonwalker.randomizer.core.Hashes;
import moonwalker.randomizer.core.MoonwalkerRandomizer;

public class MoonwalkerRandomizerGUI extends JFrame
{
	private final int seedLengthLimit = 96;
	
	private File srcRom;
	private File destRom;
	
	private JFileChooser srcRomDialog;
	private JFileChooser destRomDialog;
	
	private JLabel lStatus;
	private ScheduledThreadPoolExecutor statusUpdater;
	private ScheduledFuture<?> statusFuture;
	
	private ExecutorService randomizerThreadPool;
	
	private RomVersion romVer;
	private MoonwalkerRandomizer mRandomizer;
	private HashMap<String, Supplier<Boolean>> randomizerSettings;
	
	private Preferences prefs;

	private final static String VERSION = "0.7.2";
	private AboutDialog aboutDialog;
	
	public MoonwalkerRandomizerGUI()
	{
		setLocationByPlatform(true);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setLayout(new BorderLayout());
		setTitle("Moonwalker Randomizer v" + VERSION);
		
		prefs = Preferences.userNodeForPackage(MoonwalkerRandomizerGUI.class);
		
		lStatus = new JLabel(" ");
		statusUpdater = new ScheduledThreadPoolExecutor(1);
		
		randomizerThreadPool = Executors.newSingleThreadExecutor();
		
		char[] seedCharset = new char[]
		{
			'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 
			
			'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
			'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
			
			'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
			'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
			
			'-', '_'
		};
		
		HashMap<Character, Integer> charToCodeMap = new HashMap<>();
		for (int i = 0; i < seedCharset.length; i++)
			charToCodeMap.put(seedCharset[i], i);

		int bitsPerChar = (seedCharset.length == 0)?0:
			(32 - Integer.numberOfLeadingZeros(seedCharset.length - 1));
		
		Color infoStatusColor = new Color(0, 0, 192);
		Color successStatusColor = new Color(0, 208, 0);
		Color errorStatusColor = Color.RED;
		
		double statusDurationScale = 75;
		double statusBaseDuration = 200;
		
		JPanel northPanel = new JPanel();
		northPanel.setLayout(new BoxLayout(northPanel, BoxLayout.Y_AXIS));
		
		JPanel srcRomPanel = new JPanel();
		srcRomPanel.setLayout(new BoxLayout(srcRomPanel, BoxLayout.X_AXIS));
		JToggleButton bSrcHide = new JToggleButton("Hide");
		bSrcHide.setSelected(prefs.getBoolean("SourcePathShown", false));
		JLabel lSrcRom = new JLabel("Source ROM: "
				+ getFileLabelText(srcRom, bSrcHide.isSelected()));
		lSrcRom.setMinimumSize(new Dimension(0, lSrcRom.getMinimumSize().height));
		JButton bSrcSelect = new JButton("Select");
		
		srcRomPanel.add(Box.createHorizontalGlue());
		srcRomPanel.add(lSrcRom);
		srcRomPanel.add(Box.createHorizontalGlue());
		srcRomPanel.add(bSrcSelect);
		srcRomPanel.add(Box.createHorizontalStrut(5));
		srcRomPanel.add(bSrcHide);
		srcRomPanel.add(Box.createHorizontalStrut(5));
		
		JPanel destRomPanel = new JPanel();
		destRomPanel.setLayout(new BoxLayout(destRomPanel, BoxLayout.X_AXIS));
		JToggleButton bDestHide = new JToggleButton("Hide");
		bDestHide.setSelected(prefs.getBoolean("TargetPathShown", false));
		JLabel ldestRom = new JLabel("Destination file: "
				+ getFileLabelText(destRom, bDestHide.isSelected()));
		ldestRom.setMinimumSize(new Dimension(0, ldestRom.getMinimumSize().height));
		JButton bDestSelect = new JButton("Select");
		
		
		destRomPanel.add(Box.createHorizontalGlue());
		destRomPanel.add(ldestRom);
		destRomPanel.add(Box.createHorizontalGlue());
		destRomPanel.add(bDestSelect);
		destRomPanel.add(Box.createHorizontalStrut(5));
		destRomPanel.add(bDestHide);
		destRomPanel.add(Box.createHorizontalStrut(5));
		
		JPanel seedPanel = new JPanel();
		seedPanel.setLayout(new BoxLayout(seedPanel, BoxLayout.X_AXIS));
		JLabel lSeed = new JLabel("Seed:");
		JTextField tfSeed = new JTextField();
		((AbstractDocument) tfSeed.getDocument()).setDocumentFilter(new DocumentFilter()
		{
			@Override
			public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException
			{
				replace(fb, offset, 0, string, attr);
			}
			@Override
			public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException
			{
				int l = fb.getDocument().getLength();
				if (l - length < seedLengthLimit)
				{
					String s = filter(text);
					if (s.length() + l - length < seedLengthLimit)
						fb.replace(offset, length, s, attrs);
					else
						fb.replace(offset, length, s.substring(0, seedLengthLimit - l + length), attrs);
				}
			}
			
			private String filter(String s)
			{
				return s.codePoints()
					.filter(c -> 
						((c >= '0') && (c <= '9'))
						|| ((c >= 'a') && (c <= 'z'))
						|| ((c >= 'A') && (c <= 'Z'))
						|| (c == '-')
						|| (c == '_')
						|| (c == ' ')
					)
					.map(c -> (c == ' ')?'_':c)
					.collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
					.toString();
			}
		});
		
//		tfSeed.addActionListener(e ->
//		{
//			System.out.println(randomizerSettings.entrySet()
//				.stream()
//				.sorted((ent1, ent2) -> ent1.getKey().compareTo(ent2.getKey()))
//				.map(ent -> ent.getKey() + " \t\t" + ent.getValue().get())
//				.reduce("", (base, curr) -> base + curr + "\n"));
//		});
		
		Runnable randomizeSeed = () ->
		{
			StringBuilder sb = new StringBuilder();
			Random r = new Random();
			double len = r.nextGaussian() * 4 + 15;
			int l = (int) Math.max(Math.min((long) Math.ceil(Math.abs(len)), seedLengthLimit), 0);
			for (int i = 0; i < l; i++)
				sb.append(seedCharset[r.nextInt(64)]);
			tfSeed.setText(sb.toString());
		};
		
		JButton bRandomSeed = new JButton("Random");
		bRandomSeed.addActionListener(e ->
		{
			randomizeSeed.run();
			showStatus("Seed randomized", infoStatusColor, statusDurationScale, statusBaseDuration);
		});
		
		seedPanel.add(Box.createHorizontalStrut(5));
		seedPanel.add(lSeed);
		seedPanel.add(Box.createHorizontalStrut(5));
		seedPanel.add(tfSeed);
		seedPanel.add(Box.createHorizontalStrut(5));
		seedPanel.add(bRandomSeed);
		seedPanel.add(Box.createHorizontalStrut(5));
		
		northPanel.add(new JSeparator());
		northPanel.add(Box.createVerticalStrut(2));
		northPanel.add(srcRomPanel);
		northPanel.add(Box.createVerticalStrut(2));
		northPanel.add(destRomPanel);
		northPanel.add(Box.createVerticalStrut(2));
		northPanel.add(new JSeparator());
		northPanel.add(Box.createVerticalStrut(2));
		northPanel.add(seedPanel);
		northPanel.add(Box.createVerticalStrut(2));
		northPanel.add(new JSeparator());
		
		add(northPanel, BorderLayout.NORTH);
		
		JButton bRandomize = new JButton("Randomize");
		
		FileFilter binFileFilter = new FileFilter()
		{
			@Override
			public String getDescription()
			{
				return "Binary file (.bin)";
			}
			
			@Override
			public boolean accept(File f)
			{
				return f.isDirectory() || f.getName().toLowerCase().endsWith(".bin");
			}
		};
		
		bSrcSelect.addActionListener(e -> 
		{
			if (srcRomDialog == null)
			{
				try
				{
					srcRomDialog = new JFileChooser(prefs.get("SourceDialogPath", ""));
				}
				catch (Exception exc)
				{
					srcRomDialog = new JFileChooser();
				}
				srcRomDialog.setDialogTitle("Select a source ROM file");
				srcRomDialog.setFileFilter(binFileFilter);
				srcRomDialog.setAcceptAllFileFilterUsed(false);
			}
			if (srcRomDialog.showOpenDialog(MoonwalkerRandomizerGUI.this) == JFileChooser.APPROVE_OPTION)
			{
				File f = srcRomDialog.getSelectedFile();
				prefs.put("SourceDialogPath", f.getParent());
				
				if ((f != null) && f.equals(destRom))
					if (JOptionPane.showConfirmDialog(MoonwalkerRandomizerGUI.this,
							"The selected destination file is the same as the source ROM file. \n"
							+ "\n"
							+ "Rerandomizing a ROM is not recommended, as it may lead to artifacts such as \n"
							+ "randomization failure due to lack of free space in the ROM file or duplicate custom music. \n"
							+ "\n"
							+ "Are you sure you want to continue?",
							"Moonwalker Randomizer",
							JOptionPane.OK_CANCEL_OPTION,
							JOptionPane.WARNING_MESSAGE)
							!= JOptionPane.OK_OPTION)
						return;
				
				JPanel dialogPanel = createRomVersionPanel();
				
				if (JOptionPane.showConfirmDialog(MoonwalkerRandomizerGUI.this, dialogPanel,
						"Select the ROM version",
						JOptionPane.OK_CANCEL_OPTION,
						JOptionPane.PLAIN_MESSAGE
						) == JOptionPane.OK_OPTION)
				{
					srcRom = f;
					//TODO implement other versions
					romVer = RomVersion.REV00;
					lSrcRom.setText("Source ROM: " + getFileLabelText(srcRom, bSrcHide.isSelected()));
					showStatus("Source ROM file selected (REV00)", infoStatusColor, statusDurationScale, statusBaseDuration);
					bRandomize.setEnabled((srcRom != null) && (destRom != null));
				}
			}
		});
		bDestSelect.addActionListener(e ->
		{
			if (destRomDialog == null)
			{
				try
				{
					destRomDialog = new JFileChooser(prefs.get("TargetDialogPath", ""));
				}
				catch (Exception exc)
				{
					destRomDialog = new JFileChooser();
				}
				destRomDialog.setDialogTitle("Select a source ROM file");
				destRomDialog.setFileFilter(binFileFilter);
				destRomDialog.setAcceptAllFileFilterUsed(false);
			}
			if (destRomDialog.showOpenDialog(MoonwalkerRandomizerGUI.this) == JFileChooser.APPROVE_OPTION)
			{
				File f = destRomDialog.getSelectedFile();
				prefs.put("TargetDialogPath", f.getParent());
				
				if (f.equals(srcRom))
				{
					if (JOptionPane.showConfirmDialog(MoonwalkerRandomizerGUI.this,
							"The selected destination file is the same as the source ROM file. \n"
							+ "\n"
							+ "Rerandomizing a ROM is not recommended, as it may lead to artifacts such as \n"
							+ "randomization failure due to lack of free space in the ROM file or duplicate custom music. \n"
							+ "\n"
							+ "Are you sure you want to continue?",
							"Moonwalker Randomizer",
							JOptionPane.OK_CANCEL_OPTION,
							JOptionPane.WARNING_MESSAGE)
							!= JOptionPane.OK_OPTION)
						return;
				}
				else if (f.exists())
				{
					if (JOptionPane.showConfirmDialog(MoonwalkerRandomizerGUI.this,
							"The selected destination file already exists. "
							+ "Are you sure you want to continue?",
							"Moonwalker Randomizer",
							JOptionPane.OK_CANCEL_OPTION,
							JOptionPane.QUESTION_MESSAGE)
							!= JOptionPane.OK_OPTION)
						return;
				}
				
				destRom = f;
				ldestRom.setText("Destination file: " + getFileLabelText(destRom, bDestHide.isSelected()));
				showStatus("Destination file selected", infoStatusColor, statusDurationScale, statusBaseDuration);
				bRandomize.setEnabled((srcRom != null) && (destRom != null));
			}
		});
		
		bSrcHide.addActionListener(e ->
		{
			boolean srcPathShown = bSrcHide.isSelected();
			lSrcRom.setText("Source ROM: " + getFileLabelText(srcRom, srcPathShown));
			prefs.putBoolean("SourcePathShown", srcPathShown);
		});
		bDestHide.addActionListener(e ->
		{
			boolean targetPathShown = bDestHide.isSelected();
			ldestRom.setText("Destination file: " + getFileLabelText(destRom, targetPathShown));
			prefs.putBoolean("TargetPathShown", targetPathShown);
		});
		
		JPanel stageSettingsPanel = new JPanel(new BorderLayout());
		stageSettingsPanel.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createEmptyBorder(), "Stage settings"));
		
		DefaultMutableTreeNode root = new DefaultMutableTreeNode();
		
		randomizerSettings = createStageSettingsTree(root);
		
		Color baseColor = new Color(255, 255, 255);
		Color selectedColor = new Color(171, 171, 255);
		
		JTree tree = new JTree(root);
		DefaultTreeCellRenderer defaultRenderer = new DefaultTreeCellRenderer();
		defaultRenderer.setClosedIcon(null);
		defaultRenderer.setOpenIcon(null);
		tree.setCellRenderer((tree1, value, selected, expanded, leaf, row, hasFocus) ->
		{
			Component ret = null;
			if (value instanceof Component)
				ret = (Component) value;
			else if (value instanceof DefaultMutableTreeNode)
			{
				Object obj = ((DefaultMutableTreeNode) value).getUserObject();
				if (obj instanceof Component)
					ret = (Component) obj;
			}
			if (ret == null)
				ret = defaultRenderer.getTreeCellRendererComponent(
						tree, value, selected, expanded, leaf, row, hasFocus);
			
			ret.setBackground(hasFocus?selectedColor:baseColor);
			
			return ret;
		});
		tree.addMouseListener(new MouseAdapter()
		{
			public void mouseClicked(MouseEvent e)
			{
				if (e.getButton() == MouseEvent.BUTTON1)
				{
					TreePath tp = tree.getPathForLocation(e.getX(), e.getY());
					if (tp != null)
					{
						Object obj = tp.getLastPathComponent();
						DefaultMutableTreeNode node = null;
						JCheckBox ch = null;
						if (obj instanceof DefaultMutableTreeNode)
						{
							node = (DefaultMutableTreeNode) obj;
							obj = node.getUserObject();
							if (obj instanceof Component)
								ch = ((JCheckBox) obj);
						}
						if ((node != null) && (ch != null))
						{
							ch.setSelected(!ch.isSelected());
							((DefaultTreeModel) tree.getModel()).reload(node);
						}
					}
				}
			}
		});
		tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		tree.setRootVisible(false);
		tree.setShowsRootHandles(true);
		stageSettingsPanel.add(new JScrollPane(tree), BorderLayout.CENTER);
		
		JPanel globalSettingsPanel = new JPanel();
		globalSettingsPanel.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createEmptyBorder(), "Global settings"));
		globalSettingsPanel.setLayout(new BoxLayout(globalSettingsPanel, BoxLayout.Y_AXIS));
		
		JCheckBox cRandomizePositions = new JCheckBox("Randomize positions*");
		JCheckBox cRandomizeStageOrder = new JCheckBox("Randomize stage order (Coming soon)");
		JCheckBox cRandomizeBosses = new JCheckBox("Randomize bosses (Coming soon)");
		JCheckBox cRandomizeMusic = new JCheckBox("Randomize music");
		
		JRadioButton rbMusicShuffleOnly = new JRadioButton("Only shuffle standard music");
		JRadioButton rbMusicInsertOnly = new JRadioButton("Only insert custom music");
		JRadioButton rbMusicFull = new JRadioButton("Randomize fully");
		
		ButtonGroup bgMusic = new ButtonGroup();
		bgMusic.add(rbMusicShuffleOnly);
		bgMusic.add(rbMusicInsertOnly);
		bgMusic.add(rbMusicFull);
		
		cRandomizePositions.setSelected(true);
		cRandomizeStageOrder.setSelected(false);
		cRandomizeBosses.setSelected(false);
		cRandomizeMusic.setSelected(false);
		rbMusicFull.setSelected(true);
		
		cRandomizeStageOrder.setEnabled(false);
		cRandomizeBosses.setEnabled(false);
		rbMusicShuffleOnly.setEnabled(cRandomizeMusic.isSelected());
		rbMusicInsertOnly.setEnabled(cRandomizeMusic.isSelected());
		rbMusicFull.setEnabled(cRandomizeMusic.isSelected());
		
		cRandomizeMusic.addChangeListener(e ->
		{
			boolean enableMusicSettings = cRandomizeMusic.isSelected();
			rbMusicShuffleOnly.setEnabled(enableMusicSettings);
			rbMusicInsertOnly.setEnabled(enableMusicSettings);
			rbMusicFull.setEnabled(enableMusicSettings);
		});
		
		randomizerSettings.put("randomizePositions", () -> cRandomizePositions.isSelected());
		randomizerSettings.put("randomizeStageOrder", () -> cRandomizeStageOrder.isSelected());
		randomizerSettings.put("randomizeBosses", () -> cRandomizeBosses.isSelected());
		randomizerSettings.put("randomizeMusic", () -> cRandomizeMusic.isSelected());
		
		randomizerSettings.put("randomizeMusic.shuffleStandard",
				() -> (rbMusicShuffleOnly.isSelected() || rbMusicFull.isSelected()));
		randomizerSettings.put("randomizeMusic.insertCustom",
				() -> (rbMusicInsertOnly.isSelected() || rbMusicFull.isSelected()));
		
		randomizerSettings.put("replaceTitleText", () -> Boolean.TRUE);
		
		JLabel explLabel = new JLabel("*When enabled, uses stage settings. "
				+ "When disabled, ignores them.");
		
		globalSettingsPanel.add(leftJustified(0, cRandomizePositions));
		globalSettingsPanel.add(leftJustified(0, cRandomizeStageOrder));
		globalSettingsPanel.add(leftJustified(0, cRandomizeBosses));
		globalSettingsPanel.add(leftJustified(0, cRandomizeMusic));
		globalSettingsPanel.add(
				leftJustified(20, 
					verticalLayout(0,
						rbMusicShuffleOnly, rbMusicInsertOnly, rbMusicFull)));
		globalSettingsPanel.add(Box.createVerticalGlue());
		globalSettingsPanel.add(leftJustified(0, explLabel));
		
		stageSettingsPanel.setMinimumSize(new Dimension(20, 
				stageSettingsPanel.getMinimumSize().height));
		globalSettingsPanel.setMinimumSize(new Dimension(20, 
				globalSettingsPanel.getMinimumSize().height));
		
		JSplitPane centerPanel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true,
				stageSettingsPanel, globalSettingsPanel);
		centerPanel.setResizeWeight(0.4);
		
		add(centerPanel, BorderLayout.CENTER);
		
		JPanel southPanel = new JPanel();
		southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.Y_AXIS));
		
		JPanel southOptionPanel = new JPanel();
		southOptionPanel.setLayout(new BoxLayout(southOptionPanel, BoxLayout.X_AXIS));
		
		JButton bAbout = new JButton("About...");
		bAbout.addActionListener(e ->
		{
			if (aboutDialog == null)
				aboutDialog = new AboutDialog(VERSION, this);
			aboutDialog.show();
		});
		
		Runnable randomizeROM = () ->
		{
			try
			{
				long seed = computeSeed(tfSeed.getText().toCharArray(), charToCodeMap, bitsPerChar);
				
				byte[] rom = Files.readAllBytes(srcRom.toPath());
				MoonwalkerMetadata meta = new REV00Metadata(rom);
				if (mRandomizer == null)
					mRandomizer = new MoonwalkerRandomizer();
				
				mRandomizer.randomize(rom, randomizerSettings.entrySet()
							.stream()
							.collect(Collectors
								.toMap(e -> e.getKey(),
									e -> e.getValue().get())
							), meta, Hashes.murmur64(seed));
				
				try (FileOutputStream fos = new FileOutputStream(destRom))
				{
					fos.write(rom);
				}
				
				showStatus("ROM randomized successfully.", successStatusColor, statusDurationScale * 1.5, statusBaseDuration);
			}
			catch (Exception e)
			{
				e.printStackTrace();
				String msg;
				if (e instanceof IOException)
					msg = "An error occurred while reading or writing ROM data.\nMake sure the selected files are accessible and not currently in use.";
				else if (e instanceof OutOfSpaceException)
					msg = "The selected ROM file did not contain enough free space to finish randomization.";
				else
					msg = "The selected ROM file may be corrupted,\nor its version differs from expected. (Expected version: " + romVer + ")";
				CustomDialogs.showExceptionDialog(MoonwalkerRandomizerGUI.this, "Randomization failed - " + msg, "Error", e);
				showStatus("Randomization failed.", errorStatusColor, statusDurationScale * 2, statusBaseDuration);
			}
		};
		
		bRandomize.addActionListener(e ->
		{
			getGlassPane().setVisible(true);
			showStatus("Randomizing...", infoStatusColor, statusDurationScale, statusBaseDuration);
			repaint();
			try
			{
				randomizerThreadPool.submit(() ->
				{
					randomizeROM.run();
					getGlassPane().setVisible(false);
				});
			}
			catch (Exception ex)
			{
				ex.printStackTrace();
				getGlassPane().setVisible(false);
			}
		});
		Font randButtonFont = bRandomize.getFont();
		bRandomize.setFont(randButtonFont
				.deriveFont(randButtonFont.getSize() * 1.5f)
				.deriveFont(Font.BOLD));
		bRandomize.setForeground(new Color(0, 0, 128));
		bRandomize.setEnabled((srcRom != null) && (destRom != null));
		
		southOptionPanel.add(Box.createHorizontalStrut(5));
		southOptionPanel.add(bAbout);
		southOptionPanel.add(Box.createHorizontalGlue());
		southOptionPanel.add(lStatus);
		southOptionPanel.add(Box.createHorizontalGlue());
		southOptionPanel.add(bRandomize);
		southOptionPanel.add(Box.createHorizontalStrut(7));
		
		southOptionPanel.setAlignmentX(0);
		lStatus.setAlignmentX(0);
		
		southPanel.add(Box.createVerticalStrut(5));
		southPanel.add(southOptionPanel);
		southPanel.add(Box.createVerticalStrut(5));
		
		add(southPanel, BorderLayout.SOUTH);
		
		setGlassPane(createGlassPane());
		
		pack();
		setSize(800, 450);
		revalidate();
		centerPanel.setDividerLocation(0.45);
		
		addWindowListener(new WindowListener()
		{
			public void windowOpened(WindowEvent e)
			{}
			public void windowIconified(WindowEvent e)
			{}
			public void windowDeiconified(WindowEvent e)
			{}
			public void windowDeactivated(WindowEvent e)
			{}
			public void windowClosing(WindowEvent e)
			{
				if ((randomizerThreadPool != null) && !randomizerThreadPool.isShutdown())
					randomizerThreadPool.shutdown();
			}
			public void windowClosed(WindowEvent e)
			{}
			public void windowActivated(WindowEvent e)
			{}
		});
		
		randomizeSeed.run();
	}
	
	private static String limitString(String s, int limit)
	{
		if (s.length() > limit)
		{
			return s.substring(0, limit) + " ...";
		}
		return s;
	}
	private static HashMap<String, Supplier<Boolean>> createStageSettingsTree(DefaultMutableTreeNode root)
	{
		HashMap<String, Supplier<Boolean>> ret = new HashMap<>();
		
		String[] stageNames = 
		{
			"1", "2", "3", "4", "5"
		};
		String[][] roundNames = 
		{
			{"1", "2", "3"},
			{"1", "2", "3"},
			{"1", "2", "3"},
			{"1", "2", "3"},
			{"1", "2", "3"}
		};
		
		//TODO convert to making presentation name based on setting name
		String[][] settingPresentationNames = 
		{
			/*1-1*/
			{"Randomize kids (ground)", "Randomize kids (hidden)", 
					"Randomize gangsters", "Randomize billiard players",
					"Randomize ladies",  "Randomize cats", "Randomize chairs"},
			
			/*1-2*/
			{"Randomize kids (hidden)", "Randomize gangsters",
					"Randomize ladies"},
			
			/*1-3*/
			{"Randomize kids (ground)", "Randomize kids (hidden)", 
					"Randomize gangsters", "Randomize billiard players",
					"Randomize ladies",  "Randomize cats", "Randomize chairs"},
			
			/*2-1*/
			{"Randomize kids (ground)", "Randomize kids (hidden)",
					"Randomize street thugs", "Randomize dogs",
					"Randomize guards", "Randomize trash cans",
					"Randomize explosives"},
			
			/*2-2*/
			{"Randomize kids (ground)", "Randomize kids (hidden)",
					"Randomize street thugs", "Randomize dogs",
					"Randomize guards", "Randomize explosives"},
			
			/*2-3*/
			{"Randomize kids (ground)", "Randomize kids (hidden)",
					"Randomize street thugs", "Randomize dogs",
					"Randomize guards", "Randomize trash cans",
					"Randomize explosives"},
			
			/*3-1*/
			{"Randomize kids (ground)", "Randomize kids (hidden)",
					"Randomize zombies", "Randomize birds"},
			
			/*3-2*/
			{"Randomize kids (ground)", "Randomize kids (hidden)",
					"Randomize zombies", "Randomize birds"},
			
			/*3-3*/
			{"Randomize kids (ground)", "Randomize kids (hidden)",
					"Randomize zombies", "Randomize birds"},
			
			/*4-1*/
			{"Randomize caves", "Randomize kids (ground)", "Randomize rocks",
					"Randomize stalactites", "Randomize zombies",
					"Randomize spiders", "Randomize guards"},
			
			/*4-2*/
			{"Randomize caves", "Randomize rocks", "Randomize boulders",
					"Randomize stalactites", "Randomize zombies",
					"Randomize spiders", "Randomize guards"},
			
			/*4-3*/
			{"Randomize caves", "Randomize rocks", "Randomize stalactites",
					"Randomize zombies", "Randomize spiders",
					"Randomize guards"},
			
			/*5-1*/
			{"Randomize kids (ground)", "Randomize kids (hidden)",
					"Randomize guards", "Randomize explosives",
					"Randomize turrets"},
			
			/*5-2*/
			{"Randomize kids (ground)", "Randomize kids (hidden)",
					"Randomize guards", "Randomize explosives",
					"Randomize turrets"},
			
			/*5-3*/
			{"Randomize kids (ground)", "Randomize kids (hidden)",
					"Randomize guards", "Randomize explosives"}
		};
		
		String[][][] settingNames =
		{
			/*1-1*/
			{{"type:0xC"}, {"type:0x5"}, {"type:0x50", "proc:fixStage1Doors"}, {"type:0x62"}, {"type:0x61"},  {"type:0x55"}, {"type:0xE"}},
			
			/*1-2*/
			{{"type:0x5"}, {"type:0x50", "proc:fixStage1Doors"}, {"type:0x61"}},
			
			/*1-3*/
			{{"type:0xC"}, {"type:0x5"}, {"type:0x50", "proc:fixStage1Doors"}, {"type:0x62"}, {"type:0x61"}, {"type:0x55"}, {"type:0xE"}},
			
			/*2-1*/
			{{"type:0xC"}, {"type:0x5"}, {"type:0x57"}, {"type:0x5D"}, {"type:0x5A"}, {"type:0x1B"}, {"type:0x7D"}},
			
			/*2-2*/
			{{"type:0xC"}, {"type:0x5"}, {"type:0x57"}, {"type:0x5D"}, {"type:0x5A"}, {"type:0x7D"}},
			
			/*2-3*/
			{{"type:0xC"}, {"type:0x5"}, {"type:0x57"}, {"type:0x5D"}, {"type:0x5A"}, {"type:0x1B"}, {"type:0x7D"}},
			
			/*3-1*/
			{{"type:0xC"}, {"type:0x5"}, {"type:0x5F"}, {"type:0x65"}},
			
			/*3-2*/
			{{"type:0xC"}, {"type:0x5"}, {"type:0x5F"}, {"type:0x65"}},
			
			/*3-3*/
			{{"type:0xC"}, {"type:0x5"}, {"type:0x5F"}, {"type:0x65"}},
			
			/*4-1*/
			{{"proc:randomizeCaveData"}, {"type:0xC"}, {"type:0xE"}, {"type:0x2F"}, {"type:0x60"}, {"type:0x4C"}, {"type:0x59"}},
			
			/*4-2*/
			{{"proc:randomizeCaveData"}, {"type:0xE"}, {"type:0x30"}, {"type:0x2F"}, {"type:0x60"}, {"type:0x4C"}, {"type:0x59"}},
			
			/*4-3*/
			{{"proc:randomizeCaveData"}, {"type:0xE"}, {"type:0x2F"}, {"type:0x60"}, {"type:0x4C"}, {"type:0x59"}},
			
			/*5-1*/
			{{"type:0xC"}, {"type:0x5"}, {"type:0x5B"}, {"type:0x7D"}, {"type:0x25"}},
			
			/*5-2*/
			{{"type:0xC"}, {"type:0x5"}, {"type:0x5B"}, {"type:0x7D"}, {"type:0x25"}},
			
			/*5-3*/
			{{"type:0xC"}, {"type:0x5"}, {"type:0x5B"}, {"type:0x7D"}}
		};
		
		int ind = 0;
		for (int i = 0; i < stageNames.length; i++)
		{
			DefaultMutableTreeNode stageNode = new DefaultMutableTreeNode("Stage " + stageNames[i]);
			for (int i0 = 0; i0 < roundNames[i].length; i0++)
			{
				DefaultMutableTreeNode roundNode = new DefaultMutableTreeNode("Round "
						+ stageNames[i] + "-" + roundNames[i][i0]);
				for (int i1 = 0; i1 < settingPresentationNames[ind].length; i1++)
				{
					String sName = settingPresentationNames[ind][i1];
					JCheckBox ch = new JCheckBox(sName);
					ch.setSelected(true);
					DefaultMutableTreeNode node = new DefaultMutableTreeNode(ch);
					
					roundNode.add(node);
					
					String settingPrefix = stageNames[i] + "-" + roundNames[i][i0] + ".";
					String[] settingNameArr = settingNames[ind][i1];
					for (String str: settingNameArr)
					{
						String settingName = settingPrefix;
						if (str.startsWith("type:"))
							settingName = "randomizePositions." + settingName + str;
						else if (str.startsWith("proc:"))
							settingName = "executeProcedures." + settingName + str;
						ret.put(settingName, () -> ch.isSelected());
					}
				}
				stageNode.add(roundNode);
				ind++;
			}
			root.add(stageNode);
		}
		
		return ret;
	}
	
	private static long computeSeed(char[] chArr, HashMap<Character, Integer> charToCodeap, int bitsPerChar)
	{
		int[] codeArr = new int[chArr.length];
		for (int i = 0; i < chArr.length; i++)
			codeArr[i] = charToCodeap.getOrDefault(chArr[i], 0);
		
		long seed = codeArr.length;
		int shift = 0;
		for (int i = 0; i < codeArr.length; i++)
		{
			int code = codeArr[i];
			long val = Long.rotateLeft(code, shift);
			seed ^= val;
			shift += bitsPerChar;
			shift %= Long.SIZE;
		}
		
		return seed;
	}
	private void showStatus(String status, Color color, double durationPerCharacter, double baseDuration)
	{
		showStatus(status, color, (int) (baseDuration + (status.length() * durationPerCharacter)));
	}
	private void showStatus(String status, Color color, int duration)
	{
		statusUpdater.submit(() ->
		{
			swingInvokeAndWait(() ->
			{
				synchronized (lStatus)
				{
					if (statusFuture != null)
						statusFuture.cancel(false);
					lStatus.setText(status);
					lStatus.setForeground(color);
					statusFuture = statusUpdater.schedule(() ->
					{
						synchronized (lStatus)
						{
							swingInvokeAndWait(() ->
							{
								lStatus.setText(" ");
								lStatus.setForeground(Color.BLACK);
								statusFuture = null;
							}, Exception::printStackTrace);
						}
					}, duration, TimeUnit.MILLISECONDS);
				}
			}, Exception::printStackTrace);
		});
	}
	private static void swingInvokeAndWait(Runnable run, Consumer<Exception> exceptionHandler)
	{
		try
		{
			SwingUtilities.invokeAndWait(run);
		}
		catch (Exception e)
		{
			exceptionHandler.accept(e);
		}
	}
	private static String getFileLabelText(File f, boolean hide)
	{
		if (hide)
			return (f == null)?"[not selected, hidden]":"[hidden]";
		return (f == null)?"[not selected]":f.getAbsolutePath();
	}
	private JPanel createRomVersionPanel()
	{
		//TODO add parameter of type List<..., Supplier<Boolean>> for button state access
		JPanel dialogPanel = new JPanel();
		dialogPanel.setLayout(new BoxLayout(dialogPanel, BoxLayout.Y_AXIS));
		
		JRadioButton bREV00 = new JRadioButton("REV00");
		JRadioButton bREV01 = new JRadioButton("REV01");
		JRadioButton bPrototype = new JRadioButton("Prototype");
		JRadioButton bDetect = new JRadioButton("Detect automatically");
		JRadioButton bCustom = new JRadioButton("Custom...");
		
		bREV01.setEnabled(false);
		bPrototype.setEnabled(false);
		bDetect.setEnabled(false);
		bCustom.setEnabled(false);
		
		ButtonGroup buGroup = new ButtonGroup();
		buGroup.add(bREV00);
		buGroup.add(bREV01);
		buGroup.add(bPrototype);
		buGroup.add(bDetect);
		buGroup.add(bCustom);
		
		dialogPanel.add(bREV00);
		dialogPanel.add(bREV01);
		dialogPanel.add(bPrototype);
		dialogPanel.add(bDetect);
		dialogPanel.add(bCustom);
		
		bREV00.setSelected(true);
		
		return dialogPanel;
	}
	private static JPanel createGlassPane()
	{
		JPanel ret = new JPanel()
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				super.paintComponent(g);
				Color origC = g.getColor();
				g.setColor(getBackground());
				g.fillRect(0, 0, getWidth(), getHeight());
				g.setColor(origC);
			}
		};

		ret.setOpaque(false);
		ret.setBackground(new Color(0, 0, 0, 64));
		ret.addMouseListener(new MouseAdapter()
		{});
		ret.addMouseMotionListener(new MouseMotionAdapter()
		{});
		ret.addKeyListener(new KeyListener()
		{
			@Override
			public void keyTyped(KeyEvent e)
			{
				e.consume();
			}
			@Override
			public void keyReleased(KeyEvent e)
			{
				e.consume();
			}
			@Override
			public void keyPressed(KeyEvent e)
			{
				e.consume();
			}
		});
		ret.setFocusTraversalKeysEnabled(false);
		ret.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		
		return ret;
	}
	private static JPanel leftJustified(int leftMargin, Component... comp)
	{
		JPanel ret = new JPanel();
		ret.setLayout(new BoxLayout(ret, BoxLayout.X_AXIS));
		if (leftMargin > 0)
			ret.add(Box.createHorizontalStrut(leftMargin));
		
		for (int i = 0; i < comp.length; i++)
			ret.add(comp[i]);

		ret.add(Box.createHorizontalGlue());
		ret.setMaximumSize(new Dimension(ret.getMaximumSize().width, ret.getPreferredSize().height));
		
		return ret;
	}
	private static JPanel verticalLayout(int internalMargin, Component... comp)
	{
		JPanel ret = new JPanel();
		ret.setLayout(new BoxLayout(ret, BoxLayout.Y_AXIS));
		if (internalMargin > 0)
		{
			for (int i = 0; i < comp.length; i++)
			{
				ret.add(comp[i]);
				if (i < (comp.length - 1))
					ret.add(Box.createVerticalStrut(internalMargin));
			}
		}
		else
		{
			for (int i = 0; i < comp.length; i++)
				ret.add(comp[i]);
		}
		return ret;
	}
	
	public static void main(String[] args)
	{
		try
		{
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			UIManager.getDefaults().entrySet().forEach((t) ->
			{
				if (t.getKey() instanceof String)
				{
					if (((String) t.getKey()).endsWith(".focus"))
					{
						t.setValue(new Color(0, 0, 0, 0));
					}
				}
			});
			JFrame.setDefaultLookAndFeelDecorated(false);
		}
		catch (Exception e)
		{}
		
		try
		{
			Class.forName("java.util.function.IntFunction");
			ArrayList.class.getMethod("toArray", IntFunction.class);
		}
		catch (Exception e)
		{
			if (!(e instanceof SecurityException))
			{
				int res = JOptionPane.showOptionDialog(null,
						"This program requires Java version 11 or higher. "
						+ "Please download the appropriate version before proceeding.\n"
						+ "If you have already installed Java 11 and are still "
						+ "getting this error, make sure your operating system is using "
						+ "the right Java version to open this program.\n\n"
						+ "Current Java version: " + System.getProperty("java.version"),
						"Error", JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null,
						new String[] {"Proceed anyway", "Exit"}, "Exit");
				
				if (res != JOptionPane.OK_OPTION)
					System.exit(0);
			}
		}
		
		SwingUtilities.invokeLater(() ->
		{
			try
			{
				MoonwalkerRandomizerGUI gui = new MoonwalkerRandomizerGUI();
				gui.setVisible(true);
			}
			catch (Exception e)
			{
				e.printStackTrace();
				CustomDialogs.showExceptionDialog(null,
						limitString("Cannot launch application. Reason: "
								+ e.getMessage() + " (" + e.getClass().getTypeName() + ")"
								, 75),
						"Error", e);
			}
		});
	}
	
	private static enum RomVersion
	{
		REV00,
		REV01,
		PROTOTYPE,
		CUSTOM
	}
	
	private static class CustomDialogs
	{
		public static void showExceptionDialog(Component parent, String message, String title, Exception exc)
		{
			JPanel content = new JPanel(new BorderLayout());
			JLabel msgLabel = new JLabel((message == null)?null:(message.startsWith("<html>")?message:("<html>" + message.replace("\n", "<br>") + "</html>")));
			content.add(msgLabel, BorderLayout.NORTH);
			
			JPanel centerPanel = new JPanel();
			centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
			
			JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
			JButton button = new JButton("\u25BC");
			button.setMargin(new Insets(0, 4, 0, 4));
			buttonPanel.add(button);
			JLabel buttonLabel = new JLabel("Show details");
			buttonPanel.add(buttonLabel);
			buttonPanel.setMaximumSize(new Dimension(buttonPanel.getMaximumSize().width, buttonPanel.getPreferredSize().height));
			
			centerPanel.add(buttonPanel);
			JTextArea ta = new JTextArea();
			ta.setEditable(false);
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			if (exc != null)
				exc.printStackTrace(pw);
			else
				pw.print((String) null);
			ta.setText(sw.toString());
			pw.close();
			JScrollPane sTa = new JScrollPane(ta);
			sTa.setPreferredSize(new Dimension(sTa.getPreferredSize().width, (int) Math.min(sTa.getPreferredSize().height * 1.5, 115)));
			sTa.setMaximumSize(new Dimension(sTa.getMaximumSize().width, (int) Math.min(sTa.getMaximumSize().height * 1.5, 115)));
			sTa.setVisible(false);
			button.addActionListener(e ->
			{
				if (sTa.isVisible())
				{
					sTa.setVisible(false);
					button.setText("\u25BC");
					buttonLabel.setText("Show details");
				}
				else
				{
					sTa.setVisible(true);
					button.setText("\u25B2");
					buttonLabel.setText("Hide details");
				}
				SwingUtilities.getWindowAncestor(content).pack();
			});
			centerPanel.add(sTa);
			
			content.add(centerPanel, BorderLayout.CENTER);
			JOptionPane.showMessageDialog(parent, content, title, JOptionPane.ERROR_MESSAGE);
		}
	}
}
