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
import org.rsna.ctp.stdstages.anonymizer.dicom.*;

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
			System.out.println("Usage: java -jar DicomAnonymizerTool {parameters}");
			System.out.println("where:");
			System.out.println("  -in {inputfile} specifies the file to be anonymized");
			System.out.println("  -out {outputfile} specifies the file in which to store the anonymized file.");
			System.out.println("       If -out is missing, the anonymized file is named {inputfile}-an.");
			System.out.println("       If {outputfile} is missing, the anonymized file overwrites {inputfile}");
			System.out.println("  -da {scriptfile} specifies the anonymizer script.");
			System.out.println("       If -da is missing, element anonymization is not performed.");
			System.out.println("       If {scriptfile} is missing, the default script is used.");
			System.out.println("  -lut {lookuptablefile} specifies the anonymizer lookup table properties file.");
			System.out.println("       If -lut is missing, the default lookup table is used.");
			System.out.println("  -dpa {pixelscriptfile} specifies the pixel anonymizer script file.");
			System.out.println("       If -dpa is missing, pixel anonymization is not performed.");
			System.out.println("       If {pixelscriptfile} is missing, the default pixel script is used.");
			System.exit(0);
		}
		
		Hashtable<String,String> argsTable = new Hashtable<String,String>();
		String switchName = null;
		for (String arg : args) {
			if (arg.startsWith("-")) {
				switchName = arg;
				argsTable.put(switchName, "");
			}
			else {
				if (switchName != null) {
					argsTable.put(switchName, arg);
					switchName = null;
				}
			}
		}

		String path = argsTable.get("-in");
		if (path == null) {
			System.out.println("Input file was not specified.");
			System.exit(0);
		}
		File inFile = new File(path);
		if (!inFile.exists()) {
			System.out.println("Input file ("+path+") does not exist.");
			System.exit(0);
		}
		
		path = argsTable.get("-out");
		File outFile = inFile;
		if (path == null) {
			File f = new File(inFile.getAbsolutePath());
			String name = f.getName();
			if (name.toLowerCase().endsWith(".dcm")) {
				name = name.substring(0, name.length()-4) 
						+ "-an" 
						+ name.substring(name.length()-4);
			}
			else name += "-an";
			outFile = new File(f.getParentFile(), name);
		}
		else if (!path.equals("")) {
			outFile = new File(path);
		}
		
		File daScriptFile = new File("dicom-anonymizer.script");
		path = argsTable.get("-da");
		if (path == null) daScriptFile = null;
		else if (!path.equals("")) {
			daScriptFile = new File(path);
		}
		
		File lookupTableFile = new File("lookup-table.properties");
		path = argsTable.get("-lut");
		if ((path != null) && !path.equals("")) {
			lookupTableFile = new File(path);
		}
		
		File dpaScriptFile = new File("dicom-pixel-anonymizer.script");
		path = argsTable.get("-dpa");
		if (path == null) dpaScriptFile = null;
		else if (!path.equals("")) {
			dpaScriptFile = new File(path);
		}
		
		DicomObject dob = null;
		boolean isImage = false;
		try {
			dob = new DicomObject(inFile);
			isImage = dob.isImage();
		}
		catch (Exception ex) {
			System.out.println(inFile + " is not a DicomObject.");
			System.exit(0);
		}
		
		try {
			System.out.println("Anonymizing "+inFile);
			
			//Run the DICOMPixelAnonymizer first before the elements used
			//in signature matching are modified by the DicomAnonymizer.
			if (dpaScriptFile != null) {
				if (isImage) {
					File file = outFile;
					PixelScript pixelScript = new PixelScript(dpaScriptFile);
					if (pixelScript != null) {
						Signature signature = pixelScript.getMatchingSignature(dob);
						if (signature != null) {
							Regions regions = signature.regions;
							if ((regions != null) && (regions.size() > 0)) {
								AnonymizerStatus status = DICOMPixelAnonymizer.anonymize(inFile, outFile, regions, true, false);
								System.out.println("...The DICOMPixelAnonymizer returned "+status.getStatus()+".");
								if (status.isOK()) inFile = outFile;
								else {
									System.out.println("...Aborting the process");
									System.exit(0);
								}
							}
						}
						else System.out.println("...No matching signature found for pixel anonymization.");
					}
				}
				else System.out.println("...Pixel anonymization skipped - not an image.");
			}
			
			//Now run the DICOMAnonymizer
			if (daScriptFile != null) {
				DAScript daScript = DAScript.getInstance(daScriptFile);
				Properties daScriptProps = daScript.toProperties();
				Properties lutProps = LookupTable.getProperties(lookupTableFile);
				IntegerTable intTable = null;
				AnonymizerStatus status =
							DICOMAnonymizer.anonymize(inFile, outFile, daScriptProps, lutProps, intTable, false, false);
				System.out.println("...The DICOMAnonymizer returned "+status.getStatus()+".");
				if (!status.isOK()) {
					System.out.println("...Aborting the process");
					System.exit(0);
				}
			}
			System.out.println("...Anonymized file: "+outFile);
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
