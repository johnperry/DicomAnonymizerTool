/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.dicomanonymizertool;

import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.apache.log4j.*;
import org.rsna.ctp.objects.*;
import org.rsna.ctp.stdstages.anonymizer.*;
import org.rsna.ctp.stdstages.anonymizer.dicom.*;
import org.rsna.util.FileUtil;

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
			System.out.println("Usage: java -jar DAT {parameters}");
			System.out.println("where:");
			System.out.println("  -in {input} specifies the file or directory to be anonymized");
			System.out.println("       If {input} is a directory, all files in it and its subdirectories are processed.");
			System.out.println("  -out {output} specifies the file or directory in which to store the anonymized file or files.");
			System.out.println("       If -out is missing and -in specifies a file, the anonymized file is named {input}-an.");
			System.out.println("       If -out is missing and -in specifies a directory, an output directory named {input}-an is created.");
			System.out.println("       If {output} is missing and -in specifies a file, the anonymized file overwrites {input}");
			System.out.println("       If {output} is present and -in specifies a file, the anonymized file is named {output}");
			System.out.println("       If {output} is present and -in specifies a directory, an output directory named {output} is created.");
			System.out.println("  -da {scriptfile} specifies the anonymizer script.");
			System.out.println("       If -da is missing, element anonymization is not performed.");
			System.out.println("       If {scriptfile} is missing, the default script is used.");
			System.out.println("  -lut {lookuptablefile} specifies the anonymizer lookup table properties file.");
			System.out.println("       If -lut is missing, the default lookup table is used.");
			System.out.println("  -dpa {pixelscriptfile} specifies the pixel anonymizer script file.");
			System.out.println("       If -dpa is missing, pixel anonymization is not performed.");
			System.out.println("       If {pixelscriptfile} is missing, the default pixel script is used.");
			System.out.println("  -test specifies that the pixel anonymizer is to blank regions in mid-gray.");
 			System.out.println("");
			checkConfig();
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
			System.out.println("Input path was not specified.");
			System.exit(0);
		}
		File inFile = new File(path);
		if (!inFile.exists()) {
			System.out.println("Input path ("+path+") does not exist.");
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
		if (inFile.isDirectory()) {
			if (outFile.exists() && outFile.isFile()) {
				System.out.println("Output path ("+path+") exists but it is not a directory.");
				System.exit(0);
			}				
			outFile.mkdirs();
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
		
		boolean testmode = (argsTable.get("-test") != null);
		boolean setBIRElement = true;
		
		DicomAnonymizerTool anonymizer =
			new DicomAnonymizerTool(
				daScriptFile, lookupTableFile, dpaScriptFile, setBIRElement, testmode);
		anonymizer.anonymize(inFile, outFile);
		System.out.println("Done.");
	}
	
	public File daScriptFile;
	public File lookupTableFile;
	public File dpaScriptFile;
	public boolean setBIRElement;
	public boolean testmode;

	public DicomAnonymizerTool(
			File daScriptFile, 
			File lookupTableFile, 
			File dpaScriptFile, 
			boolean setBIRElement, 
			boolean testmode) {
				
		this.daScriptFile = daScriptFile;
		this.lookupTableFile = lookupTableFile;
		this.dpaScriptFile = dpaScriptFile;
		this.setBIRElement = setBIRElement;
		this.testmode = testmode;
	}
	
	public void anonymize(File inFile, File outFile) { 
		if (inFile.isFile()) {
			process(inFile, outFile);			
		}
		else {
			File[] files = inFile.listFiles();
			for (File file : files) {
				if (file.isDirectory()) {
					File dir = new File(outFile, file.getName());
					dir.mkdirs();
					anonymize(file, dir);
				}
				else {
					anonymize(file, new File(outFile, file.getName()));
				}
			}
		}
	}
	
	public void process(File inFile, File outFile) { 
		DicomObject dob = null;
		boolean isImage = false;
		try {
			dob = new DicomObject(inFile);
			isImage = dob.isImage();
		}
		catch (Exception ex) {
			System.out.println("Skipping non-DICOM file: "+inFile);
			return;
		}
		
		try {
			System.out.println("Anonymizing "+inFile);
			boolean ok = false;
			
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
								AnonymizerStatus status = DICOMPixelAnonymizer.anonymize(inFile, outFile, regions, setBIRElement, testmode);
								System.out.println("...The DICOMPixelAnonymizer returned "+status.getStatus()+".");
								if (status.isOK()) {
									inFile = outFile;
									ok = true;
								}
								else {
									System.out.println("...Aborting the processing of this file");
									return;
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
				if (status.isOK()) {
					ok = true;
				}
				else {
					System.out.println("...Aborting the processing of this file");
					return;
				}
			}
			if (ok) System.out.println("...Anonymized file: "+outFile);
		}
		catch (Exception ex) {
			ex.printStackTrace();
			System.exit(0);
		}
    }

	private static void checkConfig() {
		String osName = System.getProperty("os.name");
		String dataModel = System.getProperty("sun.arch.data.model");
		String javaHome = System.getProperty("java.home");
		String javaVersion = System.getProperty("java.version");
		File javaHomeDir = new File(javaHome);
		File binDir = new File(javaHomeDir, "bin");
		File extDir = new File( new File(javaHomeDir, "lib"), "ext");
		File clib = FileUtil.getFile(extDir, "clibwrapper_jiio", ".jar");
		File jai = FileUtil.getFile(extDir, "jai_imageio", ".jar");
		File clibjiio = FileUtil.getFile(binDir, "clib_jiio", ".dll");
		File clibjiiosse2 = FileUtil.getFile(binDir, "clib_jiio_sse2", ".dll");
		File clibjiioutil = FileUtil.getFile(binDir, "clib_jiio_util", ".dll");

		boolean is32bits = dataModel.equals("32");
		boolean isWindows = osName.toLowerCase().contains("windows");
		boolean hasImageIOTools = (clib != null) && (jai != null);
		boolean hasDLLs = (clibjiio != null) && (clibjiiosse2 != null) && (clibjiioutil != null);

		String imageIOVersion = null;
		if (hasImageIOTools) {
			Hashtable<String,String> jaiManifest = getManifestAttributes(jai);
			imageIOVersion  = jaiManifest.get("Implementation-Version");
		}
		
		System.out.println("Configuration:");
		System.out.println("os.name:               "+osName);
		System.out.println("java.version:          "+javaVersion);
		System.out.println("sun.arch.data.model:   "+dataModel);
		System.out.println("java.home:             "+javaHome);
		System.out.println("java.home directory:   "+javaHomeDir);
		System.out.println("clib:                  "+handleNull(clib));
		System.out.println("jai:                   "+handleNull(jai));
		System.out.println("jiio:                  "+handleNull(clibjiio));
		System.out.println("jiio sse2:             "+handleNull(clibjiiosse2));
		System.out.println("jiio util:             "+handleNull(clibjiioutil));
		System.out.println("ImageIO Tools version: "+imageIOVersion);
		System.out.println("");

		if (isWindows) {
			if (!is32bits) {
				System.out.println("This "+osName+" system has a "+dataModel+" bit Java.");
				System.out.println("It must be 32 bits if pixel anonymization is requested.");
			}
			if (!hasImageIOTools) {
				System.out.println("This Java does not have the ImageIOTools installed.");
				System.out.println("The ImageIOTools are required only for pixel anonymization.");
			}
			else {
				if (!imageIOVersion.equals("1.1")) {
					System.out.println("The ImageIOTools version is "+imageIOVersion+".");
					System.out.println("Version 1.1 or later is required for pixel anonymization.");
				}
				if (!hasDLLs) System.out.println("This Java does not have the ImageIOTools native code extensions installed.");
			}
		}
	}
	
	private static String handleNull(File file) {
		if (file == null) return "null";
		return file.getAbsolutePath();
	}
	
	private static Hashtable<String,String> getManifestAttributes(File jarFile) {
		Hashtable<String,String> h = new Hashtable<String,String>();
		JarFile jar = null;
		try {
			jar = new JarFile(jarFile);
			Manifest manifest = jar.getManifest();
			Attributes attrs = manifest.getMainAttributes();
			Iterator it = attrs.keySet().iterator();
			while (it.hasNext()) {
				String key = it.next().toString();
				h.put(key, attrs.getValue(key));
			}
		}
		catch (Exception ex) { }
		FileUtil.close(jar);
		return h;
	}

}
