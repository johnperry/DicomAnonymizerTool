/*---------------------------------------------------------------
*  Copyright 2015 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.dicomanonymizertool;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.apache.log4j.*;
import org.rsna.ctp.Configuration;
import org.rsna.ctp.objects.*;
import org.rsna.ctp.plugin.Plugin;
import org.rsna.ctp.stdstages.anonymizer.*;
import org.rsna.ctp.stdstages.anonymizer.dicom.*;
import org.rsna.util.ClasspathUtil;
import org.rsna.util.FileUtil;
import org.rsna.util.ImageIOTools;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The DicomAnonymizerTool program provides a command-line
 * tool for invoking the DicomAnonymizer.
 */
public class DicomAnonymizerTool {
	
	static Logger logger = null;

	static final String JPEGBaseline = "1.2.840.10008.1.2.4.50";
	static final String JPEGLossLess = "1.2.840.10008.1.2.4.70";
	static final String DEFUALT_OUTPUT_FILE_FORMAT = "%PatientName%-%Modality%%StudyID%-%StudyDescription%-%StudyDate%/%SeriesNumber%_%SeriesDescription%-%InstanceNumber%.dcm";

	/**
	 * The main method to start the program.
	 * @param args the list of arguments from the command line.
	 */
    public static void main(String args[]) {
		File logProps = new File("log4j.properties");
		String propsPath = logProps.getAbsolutePath();
		if (!logProps.exists()) {
			System.out.println("Logger configuration file: "+propsPath);
			System.out.println("Logger configuration file not found.");
		}
		PropertyConfigurator.configure(propsPath);
		logger = Logger.getLogger(DicomAnonymizerTool.class);
		Logger.getRootLogger().setLevel(Level.INFO);

		/*
		Logger.getRootLogger().addAppender(
				new ConsoleAppender(
					new PatternLayout("%d{HH:mm:ss} %-5p [%c{1}] %m%n")));
		*/
		
		if (args.length == 0) {
			System.out.println("Usage: java -jar DAT.jar {parameters}");
			System.out.println("where:");
			System.out.println("  -in {input} specifies the file or directory to be anonymized");
			System.out.println("       If {input} is a directory, all files in it and its subdirectories are processed.");
			System.out.println("  -outPattern {pattern} specifies the pattern for the name of the anonymized file or files and path.");
			System.out.println(String.format("  	  If no value for -outPattern is specified, it uses default - %s",DEFUALT_OUTPUT_FILE_FORMAT));
			System.out.println("  	  If both -outPattern and -out {output} are specified, and -in is a file, the -out {output} takes the precedence");
			System.out.println("  -out {output} specifies the file or directory in which to store the anonymized file or files.");
			System.out.println("       If -out is missing and -in specifies a file, the anonymized file is named {input}-an.");
			System.out.println("       If -out is missing and -in specifies a directory, an output directory named {input}-an is created.");
			System.out.println("       If {output} is missing and -in specifies a file, the anonymized file overwrites {input}");
			System.out.println("       If {output} is present and -in specifies a file, the anonymized file is named {output}");
			System.out.println("       If {output} is present and -in specifies a directory, an output directory named {output} is created.");
			System.out.println("  -f {scriptfile} specifies the filter script.");
			System.out.println("       If -f is missing, all files are accepted.");
			System.out.println("       If {scriptfile} is missing, the default script is used.");
			System.out.println("  -da {scriptfile} specifies the anonymizer script.");
			System.out.println("       If -da is missing, element anonymization is not performed.");
			System.out.println("       If {scriptfile} is missing, the default script is used.");
			System.out.println("  -p{param} \"{value}\" specifies a value for the specified parameter.");
			System.out.println("       {value} must be encapsulated in quotes.");
			System.out.println("  -e{element} \"{script}\" specifies a script for the specified element.");
			System.out.println("       {element} may be specified as a DICOM keyword, e.g. -ePatientName.");
			System.out.println("       {element} may be specified as (group,element), e.g. -e(0010,0010).");
			System.out.println("       {element} may be specified as [group,element], e.g. -e[0010,0010].");
			System.out.println("       {element} may be specified as groupelement, e.g. -e00100010.");
			System.out.println("       {script} must be encapsulated in quotes.");
			System.out.println("  -lut {lookuptablefile} specifies the anonymizer lookup table properties file.");
			System.out.println("       If -lut is missing, the default lookup table is used.");
			System.out.println("  -dpa {pixelscriptfile} specifies the pixel anonymizer script file.");
			System.out.println("       If -dpa is missing, pixel anonymization is not performed.");
			System.out.println("       If {pixelscriptfile} is missing, the default pixel script is used.");
			System.out.println("  -dec specifies that the image is to be decompressed if the pixel anonymizer requires it.");
			System.out.println("  -rec specifies that the image is to be recompressed after pixel anonymization if it was decompressed.");
			System.out.println("  -test specifies that the pixel anonymizer is to blank regions in mid-gray.");
			System.out.println("  -check {frame} specifies that the anonymized image is to be tested to ensure that the images load.");
			System.out.println("       If -check is missing, no frame checking is done.");
			System.out.println("       If {frame} is missing, only the last frame is checked.");
			System.out.println("       If {frame} is specified as first, only the first frame is checked.");
			System.out.println("       If {frame} is specified as last, only the last frame is checked.");
			System.out.println("       If {frame} is specified as all, all frames are checked.");
 			System.out.println("  -n {threads} specifies the number of parallel threads used for processing.");
 			System.out.println("  -v specifies verbose output");
 			System.out.println("");
			checkConfig();
			System.exit(0);
		}
		
		Hashtable<String,String> argsTable = new Hashtable<String,String>();
		String switchName = null;
		for (String arg : args) {
			if (arg.startsWith("-") && !arg.matches("^-[0-9]+(\\.[0-9]*)?$")) {
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
		
		if (argsTable.containsKey("-debug")) {
			System.out.println("Parameters:");
			for (String key : argsTable.keySet()) {
				String value = argsTable.get(key);
				System.out.print("    " + key + ": ");
				if (value == null) System.out.println("null");
				else System.out.println("\""+value+"\"");
			}
			System.exit(0);
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
		String outFileNameFormat = argsTable.containsKey("-outPattern") ? 
			argsTable.get("-outPattern") != null && !argsTable.get("-outPattern").isEmpty() ? argsTable.get("-outPattern"): DEFUALT_OUTPUT_FILE_FORMAT : null;
		boolean useOutputFileNameFormat = argsTable.containsKey("-out") 
			&& argsTable.get("-out") != null ? false : true;
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
			if (outFileNameFormat == null)			
				outFile.mkdirs();
		}
		
		File filterScriptFile = new File("dicom-filter.script");
		path = argsTable.get("-f");
		if (path == null) filterScriptFile = null;
		else if (!path.equals("")) {
			File f = new File(path);
			if (f.exists()) filterScriptFile = f;
		}
		
		File daScriptFile = new File("dicom-anonymizer.script");
		path = argsTable.get("-da");
		if (path == null) daScriptFile = null;
		else if (!path.equals("")) {
			daScriptFile = new File(path);
		}
		if (daScriptFile != null) {
			DAScript daScript = DAScript.getInstance(daScriptFile);
			Properties daProps = daScript.toProperties();
			for (String key : argsTable.keySet()) {
				if (key.startsWith("-e")) {
					String value = argsTable.get(key);
					String name = key.substring(2).trim();
					int tag = DicomObject.getElementTag(name);
					int group = (tag & 0xffff0000) >> 16;
					int elem = tag & 0xffff;
					String propKey = String.format("set.[%04x,%04x]",group,elem);
					daProps.setProperty(propKey, value);
				}
				else if (key.startsWith("-p")) {
					String value = argsTable.get(key);
					String name = key.substring(2).trim();
					String propKey = "param." + name;
					daProps.setProperty(propKey, value);
				}
			}
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
		
		boolean decompress = (argsTable.containsKey("-dec"));
		boolean recompress = (argsTable.containsKey("-rec"));
		
		String check = argsTable.get("-check");
		
		boolean testmode = (argsTable.containsKey("-test"));
		int maxThreads = 1;
		try { maxThreads = Integer.parseInt(argsTable.get("-n")); }
		catch (Exception ex) { }
		if (maxThreads < 1) maxThreads = 1;
		boolean verbose = (argsTable.get("-v") != null);
		boolean setBIRElement = true;
		
		DicomAnonymizerTool anonymizer =
			new DicomAnonymizerTool(
				filterScriptFile,
				daScriptFile, lookupTableFile, 
				dpaScriptFile, decompress, recompress, setBIRElement, testmode, 
				check, 
				maxThreads, 
				verbose);
		anonymizer.go(inFile, outFile, outFileNameFormat, useOutputFileNameFormat);
	}
	
	public File filterScriptFile;
	public File daScriptFile;
	public File lookupTableFile;
	public File dpaScriptFile;
	public boolean decompress;
	public boolean recompress;
	public boolean setBIRElement;
	public boolean testmode;
	public String check;
	public String filterScript = null;
	public int maxThreads;
	public boolean verbose = false;
	final ThreadPoolExecutor execSvc;
	final LinkedBlockingQueue<Runnable> queue;
	long startTime = 0;
	boolean allQueued = false;

	public DicomAnonymizerTool(
			File filterScriptFile, 
			File daScriptFile, 
			File lookupTableFile, 
			File dpaScriptFile, 
			boolean decompress,
			boolean recompress,
			boolean setBIRElement, 
			boolean testmode,
			String check,
			int maxThreads,
			boolean verbose) {
				
		this.filterScriptFile = filterScriptFile;
		this.daScriptFile = daScriptFile;
		this.lookupTableFile = lookupTableFile;
		this.dpaScriptFile = dpaScriptFile;
		this.decompress = decompress;
		this.recompress = recompress;
		this.setBIRElement = setBIRElement;
		this.testmode = testmode;
		this.check = check;
		this.maxThreads = maxThreads;
		this.verbose = verbose;
		
		//If there is a config.xml file, load the CTP configuration
		File configFile = new File("config.xml");
		if (configFile.exists()) {
			//Note: the next line is commented out to make the program work in Java9
			//and later as well as Java7 and 8. This requires all jars to be on the 
			//DAT.jar manifest's class path, which includes several dummy jar names
			//(extension1.jar, extension2.jar, etc.) to allow plugins to be added
			//for AnonymizerExtensions without having to rebuild DAT.
			//ClasspathUtil.addJARs( new File( System.getProperty("user.dir") ) );
			
			//Load the configuration to register any plugins.
			Configuration config = Configuration.load();
			for (Plugin plugin : config.getPlugins()) {
				plugin.start();
			}
		}		

		queue = new LinkedBlockingQueue<Runnable>();
		execSvc = new ThreadPoolExecutor( maxThreads, maxThreads, 0L, TimeUnit.MILLISECONDS, queue );
	}
	
	public void go(File inFile, File outFile, String outFileFormat, boolean useOutputFileNameFormat) {
		startTime = System.currentTimeMillis();
		// If the inFile is a file and if outFile is supplied, we will use it
		anonymize(inFile, outFile, outFileFormat, useOutputFileNameFormat);
		allQueued = true;
	}
	
	public void anonymize(File inFile, File outFile, String outFileFormat, boolean useOutputFileNameFormat) { 
		if (inFile.isFile()) {
			outFileFormat = useOutputFileNameFormat ? outFileFormat : null;
			execSvc.execute( new Processor(inFile, outFile, outFileFormat, this) );
		}
		else {
			useOutputFileNameFormat = true;
			File[] files = inFile.listFiles();
			for (File file : files) {
				if (file.isDirectory()) {
					File dir = new File(outFile, file.getName());
					dir.mkdirs();
					anonymize(file, dir, outFileFormat, useOutputFileNameFormat);
				}
				else {
					anonymize(file, new File(outFile, file.getName()), outFileFormat, useOutputFileNameFormat);
				}
			}
		}
	}
	
	public synchronized void notify(String s) {
		System.out.print(s);
		if (allQueued && (execSvc.getActiveCount() <= 1) && (queue.size() == 0)) {
			long endTime = System.currentTimeMillis();
			double elapsedTime = ((double)(endTime - startTime))/1000.;
			System.out.println(String.format("----\nElapsed time: %.3f",elapsedTime));
			System.exit(0);
		}
	}
	
	class Processor extends Thread {
		File inFile;
		File outFile;
		String outFileFormat;
		DicomAnonymizerTool parent;
		
		public Processor(File inFile, File outFile, String outFileFormat, DicomAnonymizerTool parent) {
			super();
			this.inFile = inFile;
			this.outFile = outFile;
			this.parent = parent;
			this.outFileFormat = outFileFormat;
		}
	
		public void run() {
			StringBuffer sb = new StringBuffer();
			DicomObject dob = null;
			boolean isImage = false;
			try {
				dob = new DicomObject(inFile);
				isImage = dob.isImage();
			}
			catch (Exception ex) {
				if (verbose) {
					sb.append("----\nThread: "+Thread.currentThread().getName()+": ");
					sb.append("Skipping non-DICOM file: "+inFile+"\n");
				}
				parent.notify(sb.toString());
				return;
			}
			
			if (filterScriptFile != null) {
				if (filterScript == null) {
					filterScript = FileUtil.getText(filterScriptFile);
				}
				if (!dob.matches(filterScript)) {
					if (verbose) {
						sb.append("----\nThread: "+Thread.currentThread().getName()+": ");
						sb.append("Skipping non-matching DICOM file: "+inFile+"\n");
					}
					parent.notify(sb.toString());
					return;
				}
			}					

			sb.append("----\nThread: "+Thread.currentThread().getName()+": ");
			try {
				sb.append("Anonymizing "+inFile+"\n");
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
									boolean decompressed = false;
									if (decompress && 
											dob.isEncapsulated() && 
												!dob.getTransferSyntaxUID().equals(JPEGBaseline)) {
										if (DICOMDecompressor.decompress(inFile, outFile).isOK()) {
											dob = new DicomObject(outFile);
											decompressed = true;
										}
										else {
											outFile.delete();
											sb.append("Decompression failure.\n");
										}
									}
									
									AnonymizerStatus status = 
										DICOMPixelAnonymizer.anonymize(dob.getFile(), outFile, regions, setBIRElement, testmode);
									if (verbose || !status.isOK()) sb.append("   The DICOMPixelAnonymizer returned "+status.getStatus()+".\n");
									
									if (status.isOK()) {
										if (decompressed && recompress) {
											Transcoder transcoder = new Transcoder();
											transcoder.setTransferSyntax(JPEGLossLess);
											transcoder.transcode(outFile, outFile);
										}
										inFile = outFile;
										ok = true;
									}
									else {
										sb.append("   Aborting the processing of this file.\n");
										parent.notify(sb.toString());
										return;
									}
								}
							}
							else if (verbose) sb.append("   No matching signature found for pixel anonymization.\n");
						}
					}
					else if (verbose) sb.append("   Pixel anonymization skipped - not an image.\n");
				}

				//Now run the DICOMAnonymizer
				if (daScriptFile != null) {
					DAScript daScript = DAScript.getInstance(daScriptFile);
					Properties daScriptProps = daScript.toProperties();
					Properties lutProps = LookupTable.getProperties(lookupTableFile);
					IntegerTable intTable = null;
					System.out.println("Output file names will have fomrat : " + outFileFormat);
					AnonymizerStatus status =
								DICOMAnonymizer.anonymize(inFile, outFile, daScriptProps, lutProps, intTable, false, false, outFileFormat);
					if (verbose || !status.isOK()) sb.append("   The DICOMAnonymizer returned "+status.getStatus()+".\n");
					if (status.isOK()) {
						ok = true;
					}
					else {
						sb.append("   Aborting the processing of this file\n");
						parent.notify(sb.toString());
					}
				}
				if (ok) {
					sb.append("   Anonymized file: "+outFile+"\n");
					if (check != null) {
						try {
							dob = new DicomObject(outFile);
							if (dob.isImage()) {
								int numberOfFrames = dob.getNumberOfFrames();
								if (numberOfFrames == 0) numberOfFrames++;
								BufferedImage img = null;
								if (check.equals("all")) {
									for (int k=0; k<numberOfFrames; k++) {
										img = dob.getBufferedImage(k, false);
									}
								}
								else {
									if (check.equals("") || check.equals("last")) {
										img = dob.getBufferedImage(numberOfFrames - 1, false);
									}
									else if (check.equals("first")) {
										img = dob.getBufferedImage(0, false);
									}
								}
								if (verbose && (img != null)) {
									sb.append("   Frame checking succeeded.\n");
								}
							}
						}
						catch (Exception ex) { 
							sb.append("   Frame checking failed.");
						}
					}
				}
				else sb.append("   Anonymization failed.\n");
			}
			catch (Exception ex) {
				ex.printStackTrace();
				System.exit(0);
			}
			parent.notify(sb.toString());
		}
	}

	//**************************************************************************
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
		
		if (!hasImageIOTools) {
			File userDir = new File(System.getProperty("user.dir"));
			clib = FileUtil.getFile(userDir, "clibwrapper_jiio", ".jar");
			jai = FileUtil.getFile(userDir, "jai_imageio", ".jar");
			hasImageIOTools = (clib != null) && (jai != null);
		}

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
		System.out.println("clib:                  "+handleNull(clib));
		System.out.println("jai:                   "+handleNull(jai));
		System.out.println("jiio:                  "+handleNull(clibjiio));
		System.out.println("jiio sse2:             "+handleNull(clibjiiosse2));
		System.out.println("jiio util:             "+handleNull(clibjiioutil));
		System.out.println("ImageIO Tools version: "+imageIOVersion);
		System.out.println("");

		if (isWindows && !hasDLLs) {
			System.out.println("This Java does not have the ImageIOTools native code extensions installed.\n");
		}
		
		System.out.println("Available codecs:\n"+ImageIOTools.listAvailableCodecs());
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
