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
import java.awt.Desktop;
import java.awt.Window;
import java.awt.Dialog.ModalityType;
import java.net.URI;
import java.awt.Dimension;
import java.awt.Font;
import java.util.Scanner;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import moonwalker.core.MoonwalkerCore;

public class AboutDialog
{
	private JDialog dialog;
	
	public AboutDialog(String mwrVersion, Window owner)
	{
		dialog = new JDialog(owner, ModalityType.DOCUMENT_MODAL);
		dialog.setTitle("Moonwalker Randomizer");
		dialog.setLayout(new BorderLayout());
		dialog.setMinimumSize(new Dimension(250, 125));
		
		JPanel centerPanel = new JPanel();
		centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
		
		centerPanel.add(new JLabel("Moonwalker Randomizer v" + mwrVersion + " by MKull"));
		centerPanel.add(Box.createVerticalStrut(5));
		centerPanel.add(new JLabel("    Programming by MKull"));
		centerPanel.add(Box.createVerticalStrut(5));
		centerPanel.add(new JLabel("    Rom analysis by MKull"));
		centerPanel.add(Box.createVerticalStrut(5));
		centerPanel.add(new JLabel("    Stage mapping by Kamil20506, Eddward, MKull"));
		centerPanel.add(Box.createVerticalStrut(5));
		centerPanel.add(new JLabel("Moonwalker Core library v" + MoonwalkerCore.VERSION + " by MKull"));
		centerPanel.add(Box.createVerticalStrut(5));
		
		dialog.add(wrapInJPanel(centerPanel, 10), BorderLayout.CENTER);
		
		JPanel southPanel = new JPanel();
		southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.X_AXIS));
		
		JDialog licenseDialog = new JDialog(dialog, ModalityType.DOCUMENT_MODAL);
		licenseDialog.setLayout(new BorderLayout());
		licenseDialog.setMinimumSize(new Dimension(300, 200));
		
		JTextArea taLicense = new JTextArea();
		taLicense.setEditable(false);
		try (Scanner sc = new Scanner(getClass().getResourceAsStream("/LICENSE")))
		{
			StringBuilder sb = new StringBuilder();
			while (sc.hasNextLine())
			{
				sb.append(sc.nextLine());
				sb.append("\n");
			}
			taLicense.setText(sb.toString());
			
			try
			{
				Font f = taLicense.getFont();
				taLicense.setFont(f.deriveFont(f.getSize2D() * 1.25f));
			}
			catch (Exception e)
			{}
		}
		catch (Exception e)
		{
			e.printStackTrace();
			taLicense.setForeground(Color.RED);
			taLicense.setText("Failed to load license content.\nThis program is licensed under GNU AGPLv3.\nPlease visit https://www.gnu.org/licenses/");
		}
		licenseDialog.add(new JScrollPane(taLicense), BorderLayout.CENTER);
		
		JPanel southLicensePanel = new JPanel();
		JButton bCloseLicense = new JButton("Close");
		bCloseLicense.addActionListener(e -> licenseDialog.dispose());
		southLicensePanel.add(bCloseLicense);
		licenseDialog.add(southLicensePanel, BorderLayout.SOUTH);
		
		licenseDialog.setSize(400, 600);
		
		JButton bLicence = new JButton("License");
		JButton bGithub = new JButton("Github");
		JButton bClose = new JButton("Close");
		bLicence.addActionListener(e -> 
		{
			licenseDialog.setLocationRelativeTo(dialog);
			licenseDialog.setVisible(true);
		});
		bGithub.addActionListener(e -> 
		{
			try
			{
				Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
				if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE))
					desktop.browse(new URI("https://github.com/mkullassUG/MoonwalkerRandomizer"));
			}
			catch (Exception exc)
			{
				exc.printStackTrace();
			}
		});
		bClose.addActionListener(e -> dialog.dispose());
		
		southPanel.add(Box.createHorizontalStrut(5));
		southPanel.add(bLicence);
		southPanel.add(Box.createHorizontalStrut(5));
		southPanel.add(bGithub);
		southPanel.add(Box.createHorizontalGlue());
		southPanel.add(bClose);
		southPanel.add(Box.createHorizontalStrut(5));
		
		dialog.add(wrapInJPanel(southPanel, 5), BorderLayout.SOUTH);
		
		dialog.pack();
		dialog.setLocationRelativeTo(owner);
	}
	
	public void show()
	{
		dialog.setVisible(true);
	}
	
	private JPanel wrapInJPanel(Component comp, int margin)
	{
		JPanel yWrapper = new JPanel();
		yWrapper.setLayout(new BoxLayout(yWrapper, BoxLayout.Y_AXIS));
		JPanel xWrapper = new JPanel();
		xWrapper.setLayout(new BoxLayout(xWrapper, BoxLayout.X_AXIS));
		
		xWrapper.add(Box.createHorizontalStrut(margin));
		xWrapper.add(comp);
		xWrapper.add(Box.createHorizontalStrut(margin));
		
		yWrapper.add(Box.createVerticalStrut(margin));
		yWrapper.add(xWrapper);
		yWrapper.add(Box.createVerticalStrut(margin));
		
		return yWrapper;
	}
}
