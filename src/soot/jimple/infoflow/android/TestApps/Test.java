/*******************************************************************************
 * Copyright (c) 2012 Secure Software Engineering Group at EC SPRIDE.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors: Christian Fritz, Steven Arzt, Siegfried Rasthofer, Eric
 * Bodden, and others.
 ******************************************************************************/
package soot.jimple.infoflow.android.TestApps;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import soot.SootMethod;
import soot.Unit;
import soot.jimple.infoflow.AbstractInfoflowProblem.PathTrackingMethod;
import soot.jimple.infoflow.InfoflowResults;
import soot.jimple.infoflow.InfoflowResults.SinkInfo;
import soot.jimple.infoflow.InfoflowResults.SourceInfo;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.handlers.ResultsAvailableHandler;
import soot.jimple.toolkits.ide.icfg.BiDiInterproceduralCFG;

public class Test {
	
	private static final class MyResultsAvailableHandler implements
			ResultsAvailableHandler {
		private final BufferedWriter wr;

		private MyResultsAvailableHandler() {
			this.wr = null;
		}

		private MyResultsAvailableHandler(BufferedWriter wr) {
			this.wr = wr;
		}

		@Override
		public void onResultsAvailable(
				BiDiInterproceduralCFG<Unit, SootMethod> cfg,
				InfoflowResults results) {
			// Dump the results
			if (results == null) {
				print("No results found.");
			}
			else {
				for (SinkInfo sink : results.getResults().keySet()) {
					print("Found a flow to sink " + sink + ", from the following sources:");
					for (SourceInfo source : results.getResults().get(sink)) {
						print("\t- " + source.getSource() + " (in "
								+ cfg.getMethodOf(source.getContext()).getSignature()  + ")");
						if (source.getPath() != null && !source.getPath().isEmpty())
							print("\t\ton Path " + source.getPath());
					}
				}
			}
		}

		private void print(String string) {
			try {
				System.out.println(string);
				if (wr != null)
					wr.write(string + "\n");
			}
			catch (IOException ex) {
				// ignore
			}
		}
	}
	
	static String command;
	static boolean generate = false;
	
	private static int timeout = -1;
	private static int sysTimeout = -1;
	
	private static boolean DEBUG = false;

	/**
	 * @param args[0] = path to apk-file
	 * @param args[1] = path to android-dir (path/android-platforms/)
	 */
	public static void main(final String[] args) throws IOException, InterruptedException {
		if (args.length < 2) {
			printUsage();	
			return;
		}
		
		//start with cleanup:
		File outputDir = new File("JimpleOutput");
		if (outputDir.isDirectory()){
			boolean success = true;
			for(File f : outputDir.listFiles()){
				success = success && f.delete();
			}
			if(!success){
				System.err.println("Cleanup of output directory "+ outputDir + " failed!");
			}
			outputDir.delete();
		}
		
		// Parse additional command-line arguments
		parseAdditionalOptions(args);
		if (!validateAdditionalOptions())
			return;
		
		List<String> apkFiles = new ArrayList<String>();
		File apkFile = new File(args[0]);
		if (apkFile.isDirectory()) {
			String[] dirFiles = apkFile.list(new FilenameFilter() {
			
				@Override
				public boolean accept(File dir, String name) {
					return (name.endsWith(".apk"));
				}
			
			});
			for (String s : dirFiles)
				apkFiles.add(s);
		}
		else
			apkFiles.add(args[0]);

		for (final String fileName : apkFiles) {
			final String fullFilePath;
			
			// Directory handling
			if (apkFiles.size() > 1) {
				fullFilePath = args[0] + File.separator + fileName;
				System.out.println("Analyzing file " + fullFilePath + "...");
				File flagFile = new File("_Run_" + fileName);
				if (flagFile.exists())
					continue;
				flagFile.createNewFile();
			}
			else
				fullFilePath = fileName;

			// Run the analysis
			if (timeout > 0)
				runAnalysisTimeout(fullFilePath, args[1]);
			else if (sysTimeout > 0)
				runAnalysisSysTimeout(fullFilePath, args[1]);
			else
				runAnalysis(fullFilePath, args[1]);

			System.gc();
		}
	}


	private static void parseAdditionalOptions(String[] args) {
		int i = 2;
		while (i < args.length) {
			if (args[i].equalsIgnoreCase("--timeout")) {
				timeout = Integer.valueOf(args[i+1]);
				i += 2;
			}
			else if (args[i].equalsIgnoreCase("--systimeout")) {
				sysTimeout = Integer.valueOf(args[i+1]);
				i += 2;
			}
			else
				i++;
		}
	}
	
	private static boolean validateAdditionalOptions() {
		if (timeout > 0 && sysTimeout > 0) {
			System.err.println("Timeout and system timeout cannot be used together");
			return false;
		}
		return true;
	}
	
	private static void runAnalysisTimeout(final String fileName, final String androidJar) {
		FutureTask<InfoflowResults> task = new FutureTask<InfoflowResults>(new Callable<InfoflowResults>() {

			@Override
			public InfoflowResults call() throws Exception {
				final long beforeRun = System.nanoTime();
				
				final SetupApplication app = new SetupApplication(androidJar, fileName);
				if (new File("../soot-infoflow/EasyTaintWrapperSource.txt").exists())
					app.setTaintWrapperFile("../soot-infoflow/EasyTaintWrapperSource.txt");
				else
					app.setTaintWrapperFile("EasyTaintWrapperSource.txt");
				app.calculateSourcesSinksEntrypoints("SourcesAndSinks.txt");
				app.setPathTracking(PathTrackingMethod.ForwardTracking);
				
				if (DEBUG) {
					app.printEntrypoints();
					app.printSinks();
					app.printSources();
				}
				
				final BufferedWriter wr = new BufferedWriter(new FileWriter("_out_" + new File(fileName).getName() + ".txt"));
				try {
					System.out.println("Running data flow analysis...");
					final InfoflowResults res = app.runInfoflow(new MyResultsAvailableHandler(wr));
					System.out.println("Data flow analysis done.");

					System.out.println("Analysis has run for " + (System.nanoTime() - beforeRun) / 1E9 + " seconds");
					wr.write("Analysis has run for " + (System.nanoTime() - beforeRun) / 1E9 + " seconds\n");
					
					wr.flush();
					return res;
				}
				finally {
					if (wr != null)
						wr.close();
				}
			}
			
		});
		ExecutorService executor = Executors.newFixedThreadPool(1);
		executor.execute(task);
		
		try {
			System.out.println("Running infoflow task...");
			task.get(timeout, TimeUnit.MINUTES);
		} catch (ExecutionException e) {
			System.err.println("Infoflow computation failed: " + e.getMessage());
			e.printStackTrace();
		} catch (TimeoutException e) {
			System.err.println("Infoflow computation timed out: " + e.getMessage());
			e.printStackTrace();
		} catch (InterruptedException e) {
			System.err.println("Infoflow computation interrupted: " + e.getMessage());
			e.printStackTrace();
		}
		
		// Make sure to remove leftovers
		executor.shutdown();		
	}

	private static void runAnalysisSysTimeout(final String fileName, final String androidJar) {
		String classpath = System.getProperty("java.class.path");
		String javaHome = System.getProperty("java.home");
		String executable = "/usr/bin/timeout";
		String[] command = new String[] { executable,
				"-s", "KILL",
				sysTimeout + "m",
				javaHome + "/bin/java",
				"-cp", classpath,
				"soot.jimple.infoflow.android.TestApps.Test",
				fileName,
				androidJar };
		System.out.println("Running command: " + executable + " " + command);
		try {
			ProcessBuilder pb = new ProcessBuilder(command);
			pb.redirectOutput(new File("_out_" + new File(fileName).getName() + ".txt"));
			pb.redirectError(new File("err_" + new File(fileName).getName() + ".txt"));
			Process proc = pb.start();
			proc.waitFor();
		} catch (IOException ex) {
			System.err.println("Could not execute timeout command: " + ex.getMessage());
			ex.printStackTrace();
		} catch (InterruptedException ex) {
			System.err.println("Process was interrupted: " + ex.getMessage());
			ex.printStackTrace();
		}
	}

	private static void runAnalysis(final String fileName, final String androidJar) {
		try {
			final long beforeRun = System.nanoTime();
				
			final SetupApplication app = new SetupApplication(androidJar, fileName);
			if (new File("../soot-infoflow/EasyTaintWrapperSource.txt").exists())
				app.setTaintWrapperFile("../soot-infoflow/EasyTaintWrapperSource.txt");
			else
				app.setTaintWrapperFile("EasyTaintWrapperSource.txt");
			app.calculateSourcesSinksEntrypoints("SourcesAndSinks.txt");
			app.setPathTracking(PathTrackingMethod.ForwardTracking);
				
			if (DEBUG) {
				app.printEntrypoints();
				app.printSinks();
				app.printSources();
			}
				
			System.out.println("Running data flow analysis...");
			app.runInfoflow(new MyResultsAvailableHandler());
			System.out.println("Analysis has run for " + (System.nanoTime() - beforeRun) / 1E9 + " seconds");
		} catch (IOException ex) {
			System.err.println("Could not read file: " + ex.getMessage());
			ex.printStackTrace();
		}
	}

	private static void printUsage() {
		System.out.println("FlowDroid (c) Secure Software Engineering Group @ EC SPRIDE");
		System.out.println();
		System.out.println("Incorrect arguments: [0] = apk-file, [1] = android-jar-directory");
		System.out.println("Optional further parameters:");
		System.out.println("\t--TIMEOUT n Time out after n seconds");
		System.out.println("\t--SYSTIMEOUT n Hard time out (kill process) after n seconds, Unix only");
	}

}
