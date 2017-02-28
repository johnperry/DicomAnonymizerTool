/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.dicomanonymizertool;

import java.io.*;
import java.util.*;
import org.apache.log4j.*;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.stdstages.anonymizer.*;
import org.rsna.ctp.stdstages.anonymizer.dicom.DAScript;
import org.rsna.ctp.stdstages.anonymizer.dicom.DICOMAnonymizer;

/**
 * The DicomAnonymizerTool program provides a command-line
 * tool for invoking the DicomAnonymizer.
 */
public class DicomAnonymizerTool {

	/**
	 * The main method to start the program.
	 * @param args the list of arguments from the command line.
	 */
    public static void main(String args[]) {
		Logger.getRootLogger().addAppender(
				new ConsoleAppender(
					new PatternLayout("%d{HH:mm:ss} %-5p [%c{1}] %m%n")));
		Logger.getRootLogger().setLevel(Level.INFO);
		
		if (args.length == 0) {
			System.out.println("Usage: java -jar DicomAnonymizerTool {inputfile} {outputfile} {scriptfile} {lookuptablefile}");
			System.out.println("where:");
			System.out.println("{inputfile} is the file to be anonymized");
			System.out.println("{outputfile} is the file in which to store the anonymized file.");
			System.out.println("   If {outputfile} is \'*\', the anonymized file overwrites {inputfile}.");
			System.out.println("{scriptfile} is the anonymizer script.");
			System.out.println("   If {scriptfile} is missing, the default script is used.");
			System.out.println("{lookuptablefile} is the anonymizer lookup table properties file.");
			System.out.println("   If {lookuptablefile} is missing, the default lookup table is used.");
			System.exit(0);
		}
		
		File inFile = new File(args[0]);
		if (!inFile.exists()) {
			System.out.println("Input file ("+args[0]+") does not exist.");
			System.exit(0);
		}
		
		File outFile = new File(args[0]);
		if (args.length > 1) {
			if (!args[1].equals("*")) {
				outFile = new File(args[1]);
			}
		}
		else {
			System.out.println("Output file was not specified.");
		}
		
		File scriptFile = new File("dicom-anonymizer.script");
		if (args.length > 2) {
			scriptFile = new File(args[2]);
		}
		
		File lookupTableFile = new File("lookup-table.properties");
		if (args.length > 3) {
			lookupTableFile = new File(args[3]);
		}
		
		try {
			DicomObject dob = new DicomObject(inFile);
		}
		catch (Exception ex) {
			System.out.println(inFile + " is not a DicomObject.");
		}
		
		try {
			DAScript dascript = DAScript.getInstance(scriptFile);
			Properties script = dascript.toProperties();
			Properties lookup = LookupTable.getProperties(lookupTableFile);
			IntegerTable intTable = null;
			AnonymizerStatus status =
						DICOMAnonymizer.anonymize(inFile, outFile, script, lookup, intTable, false, false);
			System.out.println("The DicomAnonymizer returned "+status.getStatus()+".");
		}
		catch (Exception ex) {
			ex.printStackTrace();
		}		
    }

	/**
	 * Class constructor; empty program main class.
	 */
    public DicomAnonymizerTool() { }
}
