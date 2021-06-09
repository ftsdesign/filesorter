package biz.ftsdesign.filesorter;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.*;
import java.io.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.logging.Logger;

import javax.swing.*;

public class Main {
	private static Logger log = Logger.getLogger(Main.class.getCanonicalName());
	private static final String NAME = "File Sorter";

	private static File[] sourceDirs;
	private static File targetDir;
	private static boolean dryRun = false;
	private static boolean deleteSource = true;
	private static boolean postProcess = true;
	private static boolean resetTimestamp = true;
	private static boolean closeOnSuccess = true;
	private static JButton buttonProcess;
	private static JTextField postProcCommandField;
	private static JTextField timestampField;

	static {
		System.setProperty("java.util.logging.SimpleFormatter.format", 
				"%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%1$tL %1$tZ %3$s %4$s: %5$s%6$s%n");
	}
	
	public static void main(String[] args) throws IOException {
		log.info("Started");
		final JFrame frame = new JFrame(NAME);
		frame.setMinimumSize(new Dimension(500, 100));
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.setLayout(new GridBagLayout());
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				doExit();
			}
		});
		Insets insets = new Insets(4, 4, 4, 4);
		int row = 0;
		
		final JLabel labelSourceDir = new JLabel("");
		final JLabel labelTargetDir = new JLabel("");
		buttonProcess = new JButton("Process");
		buttonProcess.setEnabled(false);
		buttonProcess.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				try {
					doSort();
				} catch (Exception e) {
					String msg = e.getMessage() != null ? e.getMessage() : e.toString();
					log.severe(msg);
					e.printStackTrace();
					JOptionPane.showMessageDialog(frame, msg, "Error", JOptionPane.ERROR_MESSAGE);
				}
			}
		});

		JButton buttonChooseSource = new JButton("Choose source dir...");
		buttonChooseSource.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JFileChooser fileChooserSource = new JFileChooser();
				fileChooserSource.setDialogTitle("Choose source dir");
				fileChooserSource.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				fileChooserSource.setMultiSelectionEnabled(true);
				int result = fileChooserSource.showOpenDialog(frame);
				if (result == JFileChooser.APPROVE_OPTION) {
					sourceDirs = fileChooserSource.getSelectedFiles();
					if (sourceDirs != null && sourceDirs.length > 0) {
						StringBuilder sb = new StringBuilder();
						if (sourceDirs.length > 1) {
							sb.append(sourceDirs.length).append(" dirs [");
							for (int i = 0; i < sourceDirs.length; i++) {
								sb.append(sourceDirs[i].getName());
								if (i < sourceDirs.length - 1) {
									sb.append("; ");
								}
							}
							sb.append("]");
						} else {
							sb.append(sourceDirs[0].getAbsolutePath());
						}
						labelSourceDir.setText(sb.toString());
						labelSourceDir.setToolTipText(sb.toString());
						updateProcessButtonState();
					}
				}
			}
		});
		frame.getContentPane().add(buttonChooseSource, new GridBagConstraints(0, row, 1, 1, 0, 0, 
				GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, insets, 0, 0));
		labelSourceDir.setMinimumSize(new Dimension(200, 10));
		frame.getContentPane().add(labelSourceDir, new GridBagConstraints(1, row, 1, 1, 10, 0, 
				GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, insets, 0, 0));
		row++;
		
		JButton buttonChooseTarget = new JButton("Choose target dir...");
		buttonChooseTarget.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JFileChooser fileChooserTarget = new JFileChooser();
				fileChooserTarget.setDialogTitle("Choose target dir");
				fileChooserTarget.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				fileChooserTarget.setMultiSelectionEnabled(false);
				int result = fileChooserTarget.showOpenDialog(frame);
				if (result == JFileChooser.APPROVE_OPTION) {
					targetDir = fileChooserTarget.getSelectedFile();
					if (targetDir != null) {
						labelTargetDir.setText(targetDir.getName());
						updateProcessButtonState();
					}
				}
			}
		});
		frame.getContentPane().add(buttonChooseTarget, new GridBagConstraints(0, row, 1, 1, 0, 0, 
				GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, insets, 0, 0));
		labelTargetDir.setMinimumSize(new Dimension(200, 10));
		frame.getContentPane().add(labelTargetDir, new GridBagConstraints(1, row, 1, 1, 10, 0, 
				GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, insets, 0, 0));
		row++;
		
		final JCheckBox checkBoxPostProcess = new JCheckBox("Post-process cmd ($" + FileSorterEngine.VAR_FILE_NAME + " for file)");
		checkBoxPostProcess.setSelected(postProcess);
		checkBoxPostProcess.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				postProcess = checkBoxPostProcess.isSelected();
				postProcCommandField.setEnabled(postProcess);
			}
		});
		frame.getContentPane().add(checkBoxPostProcess, new GridBagConstraints(0, row, 1, 1, 0, 0, 
				GridBagConstraints.WEST, GridBagConstraints.NONE, insets, 0, 0));
		
		postProcCommandField = new JTextField(FileSorterEngine.SAMPLE_CMD);
		postProcCommandField.setToolTipText("Use $" + FileSorterEngine.VAR_FILE_NAME + " for filename");
		postProcCommandField.setEnabled(postProcess);
		frame.getContentPane().add(postProcCommandField, new GridBagConstraints(1, row, 1, 1, 0, 0, 
				GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, insets, 0, 0));
		row++;
		
		final JCheckBox checkBoxSetTimestamp = new JCheckBox("Reset timestamp");
		checkBoxSetTimestamp.setSelected(resetTimestamp);
		checkBoxSetTimestamp.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				resetTimestamp = checkBoxSetTimestamp.isSelected();
				timestampField.setEnabled(resetTimestamp);
			}
		});
		frame.getContentPane().add(checkBoxSetTimestamp, new GridBagConstraints(0, row, 1, 1, 0, 0, 
				GridBagConstraints.WEST, GridBagConstraints.NONE, insets, 0, 0));
		
		timestampField = new JTextField(Instant.EPOCH.toString());
		timestampField.setEnabled(resetTimestamp);
		frame.getContentPane().add(timestampField, new GridBagConstraints(1, row, 1, 1, 0, 0, 
				GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, insets, 0, 0));
		row++;
		
		final JCheckBox checkBoxDeleteSource = new JCheckBox("Delete source");
		checkBoxDeleteSource.setSelected(deleteSource);
		checkBoxDeleteSource.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				deleteSource = checkBoxDeleteSource.isSelected();
			}
		});
		frame.getContentPane().add(checkBoxDeleteSource, new GridBagConstraints(0, row, 2, 1, 0, 0, 
				GridBagConstraints.WEST, GridBagConstraints.NONE, insets, 0, 0));
		row++;
		
		final JCheckBox checkBoxDryRun = new JCheckBox("Test mode (no changes will be made)");
		checkBoxDryRun.setSelected(dryRun);
		checkBoxDryRun.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				dryRun = checkBoxDryRun.isSelected();
			}
		});
		frame.getContentPane().add(checkBoxDryRun, new GridBagConstraints(0, row, 2, 1, 0, 0, 
				GridBagConstraints.WEST, GridBagConstraints.NONE, insets, 0, 0));
		row++;
		
		final JCheckBox checkBoxCloseOnSuccess = new JCheckBox("Close on success");
		checkBoxCloseOnSuccess.setSelected(closeOnSuccess);
		checkBoxCloseOnSuccess.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				closeOnSuccess = checkBoxCloseOnSuccess.isSelected();
			}
		});
		frame.getContentPane().add(checkBoxCloseOnSuccess, new GridBagConstraints(0, row, 2, 1, 0, 0, 
				GridBagConstraints.WEST, GridBagConstraints.NONE, insets, 0, 0));
		row++;
		
		frame.getContentPane().add(buttonProcess, new GridBagConstraints(0, row, 2, 1, 0, 0, 
				GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, insets, 0, 0));
		row++;

		frame.pack();
		frame.setVisible(true);
	}
	
	private static void updateProcessButtonState() {
		buttonProcess.setEnabled(sourceDirs != null && sourceDirs.length > 0 && targetDir != null);
	}
	
	private static void doExit() {
		log.info("Closed by user action");
		System.exit(0);
	}
	
	private static void doSort() throws Exception {
		FileSorterEngine fileSorter = new FileSorterEngine(Arrays.asList(sourceDirs), targetDir);
		fileSorter.setPostProcess(postProcess);
		if (postProcess) {
			fileSorter.setPostProcessCommand(postProcCommandField.getText());
		}
		fileSorter.setResetTimestamp(resetTimestamp);
		if (resetTimestamp) {
			fileSorter.setTimestamp(Instant.parse(timestampField.getText()));
		}
		fileSorter.process(deleteSource, dryRun);
		if (closeOnSuccess) {
			doExit();
		}
	}
}
