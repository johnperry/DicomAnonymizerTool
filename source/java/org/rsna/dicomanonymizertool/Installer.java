/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.dicomanonymizertool;

import org.rsna.installer.SimpleInstaller;

/**
 * The DicomAnonymizerTool program installer, consisting of just a
 * main method that instantiates a SimpleInstaller.
 */
public class Installer {

	static String windowTitle = "DicomAnonymizerTool Installer";
	static String programName = "DicomAnonymizerTool";
	static String introString = "<p><b>DicomAnonymizerTool</b> is a command-line tool for "
								+ "invoking the DicomAnonymizer on a file.</p>";

	public static void main(String args[]) {
		new SimpleInstaller(windowTitle,programName,introString);
	}
}
