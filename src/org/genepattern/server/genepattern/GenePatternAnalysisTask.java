package org.genepattern.server.genepattern;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import javax.servlet.ServletContext;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.genepattern.server.AnalysisServiceException;
import org.genepattern.server.dbloader.DBLoader;
import org.genepattern.server.ejb.AnalysisJobDataSource;
import org.genepattern.server.indexer.Indexer;
import org.genepattern.server.indexer.IndexerDaemon;
import org.genepattern.server.util.BeanReference;
import org.genepattern.util.IGPConstants;
import org.genepattern.util.LSID;
import org.genepattern.webservice.JobInfo;
import org.genepattern.webservice.JobStatus;
import org.genepattern.webservice.OmnigeneException;
import org.genepattern.webservice.ParameterInfo;
import org.genepattern.webservice.TaskInfo;
import org.genepattern.webservice.TaskInfoAttributes;

/**
 * Enables definition, execution, and sharing of AnalysisTasks using extensive
 * metadata descriptions and obviating programming effort by the task creator or
 * user. Like other Omnigene AnalysisTasks, this one has an onJob(JobInfo
 * jobInfo) method which executes an analysis task to completion (or error) and
 * returns results. Unlike all of the others, GenePatternAnalysisTask is not a
 * wrapper for a specific application. It is a wrapper to a user-defined task,
 * whose command line is defined in the metadata captured in a
 * TaskInfoAttributes. The rich metadata known about a task is almost entirely
 * stored in well-known entries in the task's TaskInfoAttributes HashMap.
 * 
 * <p>
 * A typical GenePattern command line will be something like this: <br>
 * <blockquote>perl foo.pl &lt;input_filename&gt; &lt;num_iter&gt;
 * &lt;max_attempts&gt; </blockquote> <br>
 * in which there are three substitutions to be made at invocation time. These
 * substitutions replace the &lt;bracketed variable names&gt; with the values
 * supplied by the caller. Some parameters have a prefix included, meaning that
 * when they are substituted, they are prefixed by some fixed text as well (eg.
 * <code>-F<i>filename</i></code>). By default parameters are mandatory,
 * however, the user, in defining the task parameters, may indicate that some
 * are optional, meaning that they may be replaced with empty strings at command
 * line substitution time rather than being rejected for execution.
 * 
 * <p>
 * There are <i>many </i> other supporting methods included in this class. Among
 * them:
 * <ul>
 * <li><b>Task definition </b></li>
 * <ul>
 * <li>A host of attributes for documenting tasks allows for categorization
 * when search for them to build a pipeline, for sharing them with others, for
 * [future] automated selection of most appropriate execution platform, etc.
 * </li>
 * <li>Validation at task definition time and task execution time of correct
 * and complete parameter definitions.</li>
 * <li>Storage of a task's associated files (scripts, DLLs, executables,
 * property files, etc) in isolation from other tasks</li>
 * <li>Ability to add and delete tasks without writing a new wrapper extending
 * AnalysisTask or a DBLoader. Built-in substitution variables allow the user to
 * create platform-independent command lines that will work on both Windows and
 * Unix.</li>
 * <li>Public and private task types, of which only a user's own private tasks
 * will appear in the task catalog they request</li>
 * </ul>
 * 
 * <li><b>Task execution </b></li>
 * <ul>
 * <li>Conversion of URLs (http://, ftp://) to local files and substition with
 * local filenames for task inputs.</li>
 * <li>Execution of each task in its own "sandbox" directory</li>
 * <li>Ability to stop a running task</li>
 * <li>Support for pipelining of tasks as a form of composite pseudo-task</li>
 * </ul>
 * 
 * <li><b>Task sharing/publication </b></li>
 * <ul>
 * <li>Ability to export all information about a task in the form of a zip file
 * </li>
 * <li>Ability to import a zip file containing a task definition, allowing
 * browsing and installation</li>
 * <li>Integration with stored tasks archived on SourceForge.net (browse,
 * download, install)</li>
 * </ul>
 * 
 * <li><b>Browser support </b></li>
 * <ul>
 * <li>Access to all of the above features (task definition, execution,
 * sharing) can be accomplished using a web browser</li>
 * </ul>
 * </ul>
 * 
 * @author Jim Lerner
 * @version 1.0
 * @see org.genepattern.server.AnalysisTask
 * @see org.genepattern.webservice.TaskInfoAttributes
 */

public class GenePatternAnalysisTask implements IGPConstants {

	/** used by log4j logging */
	static {
		String log4jConfiguration = System.getProperty("log4j.configuration");
		if (log4jConfiguration == null)
			log4jConfiguration = "/webapps/gp/WEB-INF/classes/log4j.properties";
		File l4jconf = new File(log4jConfiguration);

		//	System.out.println("GPAT static init: log4j.configuration=" +
		// log4jConfiguration + ", user.dir=" + System.getProperty("user.dir") +
		// ", l4jconf.length=" + l4jconf.length());
		if (l4jconf.exists())
			PropertyConfigurator.configure(log4jConfiguration);
	}

	private static Logger _cat = Logger
			.getLogger("edu.mit.wi.omnigene.service.analysis.genepattern.GenePatternAnalysisTask");

	/**
	 * location on server of taskLib directory where per-task support files are
	 * stored
	 */
	private static String taskLibDir = null;

	protected static final String CLASSPATH = "classpath";

	protected static final String OUTPUT_FILENAME = "output_filename";

	protected static final String ORIGINAL_PATH = "originalPath";

	public static final String TASK_NAME = "GenePatternAnalysisTask";

	/** milliseconds between polls for work to do when idle */
	protected static int POLL_INTERVAL = 1000;

	/**
	 * maximum number of concurrent tasks to run before next one will have to
	 * wait
	 */
	public static int NUM_THREADS = 20;

	/** hashtable of running jobs. key=jobID (as String), value=Process */
	protected static Hashtable htRunningJobs = new Hashtable();

	/** hashtable of running pipelines. key=jobID (as String), value=Process */
	protected static Hashtable htRunningPipelines = new Hashtable();

	/** mapping of LSIDs to taskLibDir directories */
	protected static Hashtable htTaskLibDir = new Hashtable();

	/** indicates whether version string has been displayed by init already */
	protected static boolean bAnnounced = false;

	/** use rename or copy for input files */
	protected boolean bCopyInputFiles = (System.getProperty("copyInputFiles") != null);

	/**
	 * Called by Omnigene Analysis engine to run a single analysis job, wait for
	 * completion, then report the results to the analysis_job database table.
	 * Running a job involves looking up the TaskInfo and TaskInfoAttributes for
	 * the job, validating and formatting a command line based on the formal and
	 * actual arguments to the task, downloading any input URLs to the local
	 * filesystem, executing the application, and then returning any of the
	 * output files from the sandbox directory where it ran to the analysis_job
	 * database (and ultimately to the caller).
	 * 
	 * @param o
	 *            JobInfo object
	 * @author Jim Lerner
	 */
	public void onJob(Object o) {

		JobInfo jobInfo = (JobInfo) o;
		TaskInfo taskInfo = null;
		File inFile;
		File outFile = null;
		String taskName = null;
		int i;
		int jobStatus = JobStatus.JOB_ERROR;
		String outDirName = getJobDir(Integer.toString(jobInfo.getJobNumber()));
      JobInfo parentJobInfo = null;
		try {
			/**
			 * make directory to hold input and output files
			 */
			File outDir = new File(outDirName);
			if (!outDir.exists()) {
				if (!outDir.mkdirs()) {
					_cat.error("onJob error making directory " + outDirName);
					throw new AnalysisServiceException(
							"Error creating output directory " + outDirName);
				}
			} else {
				// clean out existing directory
				File[] old = outDir.listFiles();
				for (i = 0; old != null && i < old.length; i++) {
					old[i].delete();
				}

			}

			AnalysisJobDataSource ds = getDS();
			taskInfo = ds.getTask(jobInfo.getTaskID());
			if (taskInfo == null) {
				throw new Exception("No such taskID (" + jobInfo.getTaskID()
						+ " for job " + jobInfo.getJobNumber());
			}
			taskName = taskInfo.getName();
			TaskInfoAttributes taskInfoAttributes = taskInfo
					.giveTaskInfoAttributes();
			if (taskInfoAttributes == null || taskInfoAttributes.size() == 0) {
				throw new Exception(taskName
						+ ": missing all TaskInfoAttributes!");
			}

			// check OS and CPU restrictions of TaskInfoAttributes against this
			// server
			validateCPU(taskInfoAttributes.get(CPU_TYPE)); // eg. "x86", "ppc",
														   // "alpha", "sparc"
			validateOS(taskInfoAttributes.get(OS)); // eg. "Windows", "linux",
													// "Mac OS X", "OSF1",
													// "Solaris"

			// get environment variables
			Hashtable env = getEnv();

			addTaskLibToPath(taskName, env, taskInfoAttributes.get(LSID));

			ParameterInfo[] params = jobInfo.getParameterInfoArray();
			Properties props = setupProps(taskName, jobInfo.getJobNumber(),
					jobInfo.getTaskID(), taskInfoAttributes, params, env,
					taskInfo.getParameterInfoArray(), jobInfo.getUserId());

			// move input files into temp directory
			String inputFilename = null;

			HashMap attrsActual = null;
			String mode;
			String fileType;
			String originalPath;
			long inputLastModified[] = new long[0];
			long inputLength[] = new long[0];

			if (params != null) {
				inputLastModified = new long[params.length];
				inputLength = new long[params.length];
				for (i = 0; i < params.length; i++) {
					attrsActual = params[i].getAttributes();
					fileType = (attrsActual != null ? (String) attrsActual
							.get(ParameterInfo.TYPE) : null);
					mode = (attrsActual != null ? (String) attrsActual
							.get(ParameterInfo.MODE) : null);
					originalPath = params[i].getValue();
					// allow parameter value substitutions within file input
					// parameters
					originalPath = substitute(originalPath, props, params);

					if (fileType != null
							&& fileType.equals(ParameterInfo.FILE_TYPE)
							&& mode != null
							&& !mode.equals(ParameterInfo.OUTPUT_MODE)) {
						_cat.debug("in: mode=" + mode + ", fileType="
								+ fileType + ", name=" + params[i].getValue()
								+ ", origValue=" + params[i].getValue());
						if (originalPath == null)
							throw new IOException(params[i].getName()
									+ " has not been assigned a filename");

						if (mode.equals("CACHED_IN"))
							originalPath = System.getProperty("jobs") + "/"
									+ originalPath;
						inFile = new File(originalPath);
						// TODO: strip Axisnnnnnaxis_ from name
						int j;
						String baseName = inFile.getName();
						j = baseName.indexOf("axis_");
						// strip off the AxisNNNNNaxis_ prefix
						if (baseName.indexOf("Axis") == 0 && j != -1) {
							baseName = baseName
									.substring(baseName.indexOf("_") + 1);
							_cat.debug("name without Axis is " + baseName);
						}
						outFile = new File(outDirName, baseName);

						// borrow input file and put it into the job's directory
						_cat.debug("borrowing " + inFile.getCanonicalPath()
								+ " to " + outFile.getCanonicalPath());

						if (!inFile.exists()
								|| (!outFile.exists() && (bCopyInputFiles ? !copyFile(
										inFile, outFile)
										: !rename(inFile, outFile, true)))) {
							throw new Exception("FAILURE: " + inFile.toString()
									+ " (exists " + inFile.exists()
									+ ") rename to " + outFile.toString()
									+ " (exists " + outFile.exists() + ")");
						} else {
							if (bCopyInputFiles)
								outFile.deleteOnExit(); // mark for delete, just
														// in case
							params[i].getAttributes().put(ORIGINAL_PATH,
									originalPath);
							params[i].setValue(outFile.getCanonicalPath());
							inputLastModified[i] = outFile.lastModified();
							inputLength[i] = outFile.length();
							_cat.debug("inherited input file "
									+ outFile.getCanonicalPath()
									+ " before run: length=" + inputLength[i]
									+ ", lastModified=" + inputLastModified[i]);
							// outFile.setReadOnly();
						}
					} else if (i >= taskInfo.getParameterInfoArray().length) {
						//_cat.debug("params[" + i + "]=" + params[i].getName()
						// + " has no formal defined");
					} else {
						// check formal parameters for a file input type that
						// was in fact sent as a string (ie. cached or http)

						// find the formal parameter corresponding to this
						// actual parameter
						ParameterInfo[] formals = taskInfo
								.getParameterInfoArray();
						HashMap attrFormals = null;
						fileType = null;
						mode = null;
						for (int formal = 0; formals != null
								&& formal < formals.length; formal++) {
							if (formals[formal].getName().equals(
									params[i].getName())) {
								attrFormals = formals[formal].getAttributes();
								fileType = (String) attrFormals
										.get(ParameterInfo.TYPE);
								mode = (String) attrFormals
										.get(ParameterInfo.MODE);
								break;
							}
						}
						// handle http files by downloading them and
						// substituting the downloaded filename for the URL in
						// the command line

						// TODO: handle other protocols: file: (which server?),
						// ftp:
						if (fileType != null
								&& fileType.equals(ParameterInfo.FILE_TYPE)
								&& mode != null
								&& !mode.equals(ParameterInfo.OUTPUT_MODE)
								&& originalPath != null
								&& (originalPath.startsWith("http://")
										|| originalPath.startsWith("https://")
										|| originalPath.startsWith("ftp:") || originalPath
										.startsWith("file:"))) {
							_cat.debug("in: mode=" + mode + ", fileType="
									+ fileType + ", name="
									+ params[i].getValue());

							// derive a filename that is as similar as
							// reasonable to the name of the page
							String baseName = originalPath
									.substring(originalPath.lastIndexOf("/") + 1);
							int j;
							j = baseName.lastIndexOf("?");
							if (j != -1 && j < baseName.length())
								baseName = baseName.substring(j + 1);
							j = baseName.lastIndexOf("&");
							if (j != -1 && j < baseName.length())
								baseName = baseName.substring(j + 1);
							j = baseName.lastIndexOf("=");
							if (j != -1 && j < baseName.length())
								baseName = baseName.substring(j + 1);
							j = baseName.indexOf("Axis");
							// strip off the AxisNNNNNaxis_ prefix
							if (j == 0)
								baseName = baseName.substring(baseName
										.indexOf("_") + 1);
							if (baseName.length() == 0)
								baseName = "indexPage";
							baseName = URLDecoder.decode(baseName, UTF8);

							outFile = new File(outDirName, baseName);
							_cat.info("downloading " + originalPath + " to "
									+ outFile.getAbsolutePath());
							outFile.deleteOnExit();
							FileOutputStream os = new FileOutputStream(outFile);
							InputStream is = new URL(originalPath).openStream();
							byte[] buf = new byte[100000];
							while ((j = is.read(buf, 0, buf.length)) > 0) {
								os.write(buf, 0, j);
							}
							is.close();
							os.close();
							params[i].getAttributes().put(ORIGINAL_PATH,
									originalPath);
							params[i].setValue(outFile.getCanonicalPath());
							inputLastModified[i] = outFile.lastModified();
							inputLength[i] = outFile.length();
							_cat.debug("inherited downloaded input file "
									+ outFile.getCanonicalPath()
									+ " before run: length=" + inputLength[i]
									+ ", lastModified=" + inputLastModified[i]);
							// outFile.setReadOnly();
						}
					}
				} // end for each parameter
			} // end if parameters not null

			// build the command line, replacing <variableName> with the same
			// name from the properties
			// (ParameterInfo[], System properties, environment variables, and
			// built-ins merged)

			// build props again, now that downloaded files are set
			props = setupProps(taskName, jobInfo.getJobNumber(), jobInfo
					.getTaskID(), taskInfoAttributes, params, env, taskInfo
					.getParameterInfoArray(), jobInfo.getUserId());

			StringBuffer stdout = new StringBuffer();
			StringBuffer stderr = new StringBuffer();

			// check that all parameters are used in the command line
			// and that all non-optional parameters that are cited actually
			// exist
			ParameterInfo[] formalParameters = taskInfo.getParameterInfoArray();
			Vector vProblems = validateParameters(props, taskName,
					taskInfoAttributes.get(COMMAND_LINE), params,
					formalParameters, true);

			String c = substitute(substitute(taskInfoAttributes
					.get(COMMAND_LINE), props, formalParameters), props,
					formalParameters);
			if (c == null || c.trim().length() == 0) {
				vProblems.add("Command line not defined");
			}

			String lsfPrefix = props.getProperty(COMMAND_PREFIX, null);
			if (lsfPrefix != null && lsfPrefix.length() > 0) {
				taskInfoAttributes.put(COMMAND_LINE, lsfPrefix + " "
						+ taskInfoAttributes.get(COMMAND_LINE));
			}

			// create an array of Strings for Runtime.exec to fix bug 55
			// (filenames in spaces cause invalid command line)
			String cmdLine = taskInfoAttributes.get(COMMAND_LINE);
			StringTokenizer stCommandLine;
			String[] commandTokens = null;
			String firstToken;
			String token;

			// TODO: handle quoted arguments within the command line (eg. echo
			// "<p1> <p2>" as a single token)

			// check that the user didn't quote the program name
			if (!cmdLine.startsWith("\"")) {
				// since we could have a definition like "<perl>=perl -Ifoo", we
				// need to double-tokenize the first token to extract just
				// "perl"
				stCommandLine = new StringTokenizer(cmdLine);
				firstToken = stCommandLine.nextToken();
				// now the command line contains the real first word (perl)
				// followed by the rest, ready for space-tokenizing
				cmdLine = substitute(firstToken, props, formalParameters)
						+ cmdLine.substring(firstToken.length());
				stCommandLine = new StringTokenizer(cmdLine);
				commandTokens = new String[stCommandLine.countTokens()];

				for (i = 0; stCommandLine.hasMoreTokens(); i++) {
					token = stCommandLine.nextToken();
					commandTokens[i] = substitute(token, props,
							formalParameters);
					if (commandTokens[i] == null) {
						String[] copy = new String[commandTokens.length - 1];
						System.arraycopy(commandTokens, 0, copy, 0, i);
						if ((i + 1) < commandTokens.length)
							System.arraycopy(commandTokens, i + 1, copy, i,
									commandTokens.length - i - 1);
						commandTokens = copy;
						i--;
					}
				}
			} else {
				// the user quoted the command, so it has to be handled
				// specially
				int endQuote = cmdLine.indexOf("\"", 1); // find the matching
														 // closing quote
				if (endQuote == -1) {
					vProblems.add("Missing closing quote on command line: "
							+ cmdLine);
				} else {
					firstToken = cmdLine.substring(1, endQuote);
					stCommandLine = new StringTokenizer(cmdLine
							.substring(endQuote + 1));
					commandTokens = new String[stCommandLine.countTokens() + 1];

					commandTokens[0] = substitute(firstToken, props,
							formalParameters);
					for (i = 1; stCommandLine.hasMoreTokens(); i++) {
						token = stCommandLine.nextToken();
						commandTokens[i] = substitute(token, props,
								formalParameters);
						// empty token?
						if (commandTokens[i] == null) {
							String[] copy = new String[commandTokens.length - 1];
							System.arraycopy(commandTokens, 0, copy, 0, i);
							if ((i + 1) < commandTokens.length)
								System.arraycopy(commandTokens, i + 1, copy, i,
										commandTokens.length - i - 1);
							commandTokens = copy;
							i--;
						}
					}
				}
			}

			// do the substitutions one more time to allow, for example,
			// p2=<p1>.res
			for (i = 1; i < commandTokens.length; i++) {
				commandTokens[i] = substitute(commandTokens[i], props,
						formalParameters);
				if (commandTokens[i] == null) {
					String[] copy = new String[commandTokens.length - 1];
					System.arraycopy(commandTokens, 0, copy, 0, i);
					if ((i + 1) < commandTokens.length)
						System.arraycopy(commandTokens, i + 1, copy, i,
								commandTokens.length - i - 1);
					commandTokens = copy;
					i--;
				}
			}

			StringBuffer commandLine = new StringBuffer(commandTokens[0]);
			for (i = 1; i < commandTokens.length; i++) {
				commandLine.append(" ");
				commandLine.append(commandTokens[i]);
			}

			if (vProblems.size() > 0) {
				stderr
						.append("Error validating input parameters, command line would be:\n"
								+ commandLine.toString() + "\n");
				for (Enumeration eProblems = vProblems.elements(); eProblems
						.hasMoreElements();) {
					stderr.append(eProblems.nextElement() + "\n");
				}
				/*
				 * for(i=0; params != null && i <params.length; i++){
				 * stderr.append(params[i].getName() + "=" +
				 * params[i].getValue() + "\n"); }
				 */
				jobStatus = JobStatus.JOB_ERROR;
			} else {
				// run the task and wait for completion.
				_cat.info(taskName + " command (job " + jobInfo.getJobNumber()
						+ "): " + commandLine.toString());
				//System.out.println(taskName + " command (job " +
				// jobInfo.getJobNumber() + "):\n" + commandLine.toString());
				try {
					runCommand(commandTokens, env, outDir, stdout, stderr,
							jobInfo);
					jobStatus = JobStatus.JOB_FINISHED;
					//System.out.println(taskName + " (" +
					// jobInfo.getJobNumber() + ") done.");
					_cat.info(taskName + " (" + jobInfo.getJobNumber()
							+ ") done.");
				} catch (Throwable t) {
					jobStatus = JobStatus.JOB_ERROR;
					//System.err.println(taskName + " (" +
					// jobInfo.getJobNumber() + ") done with error: " +
					// t.getMessage());
					_cat.error(taskName + " (" + jobInfo.getJobNumber()
							+ ") done with error: " + t.getMessage());
					t.printStackTrace();
					stderr.append(t.getMessage() + "\n\n");
				}
			}

			// move input files back into Axis attachments directory
			if (params != null) {
				for (i = 0; i < params.length; i++) {
					attrsActual = params[i].getAttributes();
					fileType = (attrsActual != null ? (String) attrsActual
							.get(ParameterInfo.TYPE) : null);
					mode = (attrsActual != null ? (String) attrsActual
							.get(ParameterInfo.MODE) : null);
					if (fileType != null
							&& fileType.equals(ParameterInfo.FILE_TYPE)
							&& mode != null
							&& !mode.equals(ParameterInfo.OUTPUT_MODE)) {
						// System.out.println("out: mode=" + mode + ",
						// fileType=" + fileType + ", name=" +
						// params[i].getValue());
						if (params[i].getValue() == null)
							throw new IOException(params[i].getName()
									+ " has no filename association");
						inFile = new File(params[i].getValue());
						originalPath = (String) params[i].getAttributes()
								.remove(ORIGINAL_PATH);
						_cat.debug(params[i].getName() + " original path='"
								+ originalPath + "'");
						if (originalPath == null || originalPath.length() == 0) {
							_cat.info(params[i].getName() + " original path='"
									+ originalPath + "'");
							continue;
						}
						outFile = new File(originalPath);
						// System.out.println("unborrowing " + inFile + " to " +
						// outFile);
						// un-borrow the input file, moving it from the job's
						// directory back to where it came from
						if (inFile.exists()
								&& !outFile.exists()
								&& (bCopyInputFiles ? !inFile.delete()
										: !rename(inFile, outFile, true))) {
							_cat.info("FAILURE: " + inFile.toString()
									+ " (exists " + inFile.exists()
									+ ") rename to " + outFile.toString()
									+ " (exists " + outFile.exists() + ")");
						} else {
							if (inputLastModified[i] != outFile.lastModified()
									|| inputLength[i] != outFile.length()) {
								_cat.debug("inherited input file "
										+ outFile.getCanonicalPath()
										+ " after run: length="
										+ inputLength[i] + ", lastModified="
										+ inputLastModified[i]);
								String errorMessage = "WARNING: "
										+ outFile.toString()
										+ " may have been overwritten during execution of task "
										+ taskName + ", job number "
										+ jobInfo.getJobNumber() + "\n";
								if (inputLastModified[i] != outFile
										.lastModified())
									errorMessage = errorMessage
											+ "original date: "
											+ new Date(inputLastModified[i])
											+ ", current date: "
											+ new Date(outFile.lastModified())
											+ " diff="
											+ (inputLastModified[i] - outFile
													.lastModified()) + "ms. \n";
								if (inputLength[i] != outFile.length())
									errorMessage = errorMessage
											+ "original size: "
											+ inputLength[i]
											+ ", current size: "
											+ outFile.length() + "\n";

								if (stderr.length() > 0)
									stderr.append("\n");
								stderr.append(errorMessage);
								//System.err.println(errorMessage);
								_cat.error(errorMessage);
							}
							params[i].setValue(originalPath);
						}
					} else {
						// TODO: what if the input file is also supposed to be
						// one of the outputs?
						originalPath = (String) params[i].getAttributes()
								.remove(ORIGINAL_PATH);
						if (originalPath != null
								&& (originalPath.startsWith("http://")
										|| originalPath.startsWith("https://")
										|| originalPath.startsWith("ftp:") || originalPath
										.startsWith("file:"))) {
							outFile = new File(params[i].getValue());
							_cat.debug("out: mode=" + mode + ", fileType="
									+ fileType + ", name="
									+ params[i].getValue());
							if (inputLastModified[i] != outFile.lastModified()
									|| inputLength[i] != outFile.length()) {
								_cat.debug("inherited input file "
										+ outFile.getCanonicalPath()
										+ " after run: length="
										+ inputLength[i] + ", lastModified="
										+ inputLastModified[i]);
								String errorMessage = "WARNING: "
										+ outFile.toString()
										+ " may have been overwritten during execution of task "
										+ taskName + ", job number "
										+ jobInfo.getJobNumber() + "\n";
								if (inputLastModified[i] != outFile
										.lastModified())
									errorMessage = errorMessage
											+ "original date: "
											+ new Date(inputLastModified[i])
											+ ", current date: "
											+ new Date(outFile.lastModified())
											+ "\n";
								if (inputLength[i] != outFile.length())
									errorMessage = errorMessage
											+ "original size: "
											+ inputLength[i]
											+ ", current size: "
											+ outFile.length() + "\n";
								if (stderr.length() > 0)
									stderr.append("\n");
								stderr.append(errorMessage);
								//System.err.println(errorMessage);
								_cat.error(errorMessage);
							}
							outFile.delete();
							params[i].setValue(originalPath);
							continue;
						}
					}
				} // end for each parameter
			} // end if parameters not null

			// reload jobInfo to pick up any output parameters were added by the
			// job explicitly (eg. pipelines)
			jobInfo = ds.getJobInfo(jobInfo.getJobNumber());

			// any files that are left in outDir are output files
			File[] outputFiles = new File(outDirName)
					.listFiles(new FilenameFilter() {
						public boolean accept(File dir, String name) {
							return !name.equals(STDERR) && !name.equals(STDOUT);
						}
					});

			// create a sorted list of files by lastModified() date
			Arrays.sort(outputFiles, new Comparator() {
				public int compare(Object o1, Object o2) {
					long f1Date = ((File) o1).lastModified();
					long f2Date = ((File) o2).lastModified();
					if (f1Date < f2Date)
						return -1;
					if (f1Date == f2Date)
						return 0;
					return 1;
				}
			});
         
         
         parentJobInfo = getDS().getParent(jobInfo.getJobNumber());

			for (i = 0; i < outputFiles.length; i++) {
				File f = outputFiles[i];
				_cat.debug("adding output file to output parameters "
						+ f.getName() + " from " + outDirName);
				addFileToOutputParameters(jobInfo, f.getName(), f.getName(), parentJobInfo);
			}

			if (stdout.length() > 0) {
				//System.out.println("adding stdout");
				outFile = writeStringToFile(outDirName, STDOUT, stdout
						.toString());
				addFileToOutputParameters(jobInfo, STDOUT, STDOUT, parentJobInfo);
			}

			if (stderr.length() > 0) {
				//System.out.println("adding stderr");
				outFile = writeStringToFile(outDirName, STDERR, stderr
						.toString());
				addFileToOutputParameters(jobInfo, STDERR, STDERR, parentJobInfo);
			}

			getDS().updateJob(jobInfo.getJobNumber(),
					jobInfo.getParameterInfo(), jobStatus);
         if(parentJobInfo!=null) {
            getDS().updateJob(parentJobInfo.getJobNumber(),
			      parentJobInfo.getParameterInfo(), ((Integer)JobStatus.STATUS_MAP.get(parentJobInfo.getStatus())).intValue());
         }
			if (outputFiles.length == 0 && stderr.length() == 0
					&& stdout.length() == 0) {
				//System.err.println("no output for " + taskName + " (job " +
				// jobInfo.getJobNumber() + ").");
				_cat.error("no output for " + taskName + " (job "
						+ jobInfo.getJobNumber() + ").");
			}
			IndexerDaemon.notifyJobComplete(jobInfo.getJobNumber());

		} catch (Throwable e) {
			//System.err.println(taskName + " error: " + e);
			_cat.error(taskName + " error: " + e);
			e.printStackTrace();
			try {
				outFile = writeStringToFile(outDirName, STDERR, e.getMessage()
						+ "\n\n");
				addFileToOutputParameters(jobInfo, STDERR, STDERR, parentJobInfo);
				getDS().updateJob(jobInfo.getJobNumber(),
						jobInfo.getParameterInfo(), JobStatus.JOB_ERROR);
            if(parentJobInfo!=null) {
               getDS().updateJob(parentJobInfo.getJobNumber(),
			         parentJobInfo.getParameterInfo(), ((Integer)JobStatus.STATUS_MAP.get(parentJobInfo.getStatus())).intValue());
            }
			} catch (Exception e2) {
				//System.err.println(taskName + " error: unable to update job
				// error status" +e2);
				_cat.error(taskName
						+ " error: unable to update job error status" + e2);
			}
			IndexerDaemon.notifyJobComplete(jobInfo.getJobNumber());
		}

	}

	protected static boolean validateCPU(String expected) throws Exception {
		String actual = System.getProperty("os.arch");
		// eg. "x86", "i386", "ppc", "alpha", "sparc"

		if (expected.equals(""))
			return true;
		if (expected.equals(ANY))
			return true;
		if (expected.equalsIgnoreCase(actual))
			return true;

		String intelEnding = "86"; // x86, i386, i586, etc.
		if (expected.endsWith(intelEnding) && actual.endsWith(intelEnding))
			return true;

		if (System.getProperty(COMMAND_PREFIX, null) != null)
			return true; // don't validate for LSF

		throw new Exception("Cannot run on this platform.  Task requires a "
				+ expected + " CPU, but this is a " + actual);
	}

	protected static boolean validateOS(String expected) throws Exception {
		String actual = System.getProperty("os.name");
		// eg. "Windows XP", "Linux", "Mac OS X", "OSF1"

		if (expected.equals(""))
			return true;
		if (expected.equals(ANY))
			return true;
		if (expected.equalsIgnoreCase(actual))
			return true;

		String MicrosoftBeginning = "Windows"; // Windows XP, Windows ME,
											   // Windows XP, Windows 2000, etc.
		if (expected.startsWith(MicrosoftBeginning)
				&& actual.startsWith(MicrosoftBeginning))
			return true;

		if (System.getProperty(COMMAND_PREFIX, null) != null)
			return true; // don't validate for LSF

		throw new Exception("Cannot run on this platform.  Task requires a "
				+ expected + " operating system, but this server is running "
				+ actual);
	}

	/**
	 * Performs substitutions of parameters within the commandLine string where
	 * there is a &lt;variable&gt; whose substitution value is defined as a key
	 * by that name in props. If the parameters is one which has a "prefix",
	 * that prefix is prepended to the substitution value as the substitution is
	 * made. For example, if the prefix is "-f " and the parameter "/foo/bar" is
	 * supplied, the ultimate substitution will be "-f /foo/bar".
	 * 
	 * @author Jim Lerner
	 * @param commandLine
	 *            command line with just variable names rather than values
	 * @param props
	 *            Properties object containing name/value pairs for parameter
	 *            substitution in the command line
	 * @param params
	 *            ParameterInfo[] describing whether each parameter has a prefix
	 *            defined.
	 * @return String command line with all substitutions made
	 */

	public String substitute(String commandLine, Properties props,
			ParameterInfo[] params) {
		if (commandLine == null)
			return null;
		int start = 0, end = 0, blank;
		String varName = null;
		String replacement = null;
		boolean isOptional = true;
		// create a hashtable of parameters keyed on name for attribute lookup
		Hashtable htParams = new Hashtable();
		for (int i = 0; params != null && i < params.length; i++) {
			htParams.put(params[i].getName(), params[i]);
		}
		ParameterInfo p = null;
		StringBuffer newString = new StringBuffer(commandLine);
		while (start < newString.length()
				&& (start = newString.toString().indexOf(LEFT_DELIMITER, start)) != -1) {
			start += LEFT_DELIMITER.length();
			end = newString.toString().indexOf(RIGHT_DELIMITER, start);
			if (end == -1) {
				_cat.error("Missing " + RIGHT_DELIMITER + " delimiter in "
						+ commandLine);
				break; // no right delimiter means no substitution
			}
			blank = newString.toString().indexOf(" ", start);
			if (blank != -1 && blank < end) {
				// if there's a space in the name, then it's a redirection of
				// stdin
				start = blank + 1;
				continue;
			}
			varName = newString.substring(start, end);
			replacement = props.getProperty(varName);
			if (replacement == null) {
				// don't sweat inability to substitute for optional parameters.
				// They've already been validated by this point.
				_cat.info("no substitution available for parameter " + varName);
				// System.out.println(props);
				//			replacement = LEFT_DELIMITER + varName + RIGHT_DELIMITER;
				replacement = "";
			}
			if (varName.equals("resources")) {
				// special treatment: make this an absolute path so that
				// pipeline jobs running in their own directories see the right
				// path
				replacement = new File(replacement).getAbsolutePath();
			}
			if (replacement.length() == 0)
				_cat.debug("GPAT.substitute: replaced " + varName
						+ " with empty string");
			p = (ParameterInfo) htParams.get(varName);
			if (p != null) {
				HashMap hmAttributes = p.getAttributes();
				if (hmAttributes != null) {
					if (hmAttributes
							.get(PARAM_INFO_OPTIONAL[PARAM_INFO_NAME_OFFSET]) == null) {
						isOptional = false;
					}
					String optionalPrefix = (String) hmAttributes
							.get(PARAM_INFO_PREFIX[PARAM_INFO_NAME_OFFSET]);
					if (replacement.length() > 0 && optionalPrefix != null && optionalPrefix.length() > 0) {
						replacement = optionalPrefix + replacement;
					}
				}
			}
			if (replacement.indexOf("Program Files") != -1)
				replacement = replace(replacement, "Program Files", "Progra~1");

			newString = newString.replace(start - LEFT_DELIMITER.length(), end
					+ RIGHT_DELIMITER.length(), replacement);
			start = start + replacement.length() - LEFT_DELIMITER.length();
		}
		if (newString.length() == 0 && isOptional) {
			return null;
		}
		return newString.toString();
	}

	/**
	 * Deletes a task, by name, from the Omnigene task_master database.
	 * 
	 * @param name
	 *            name of task to delete
	 * @author Jim Lerner
	 */
	public static void deleteTask(String lsid) throws OmnigeneException,
			RemoteException {
		String username = null;
		TaskInfo ti = GenePatternAnalysisTask.getTaskInfo(lsid, username);
		File libdir = null;
		try {
			libdir = new File(getTaskLibDir(ti.getName(), (String) ti
					.getTaskInfoAttributes().get(LSID), username));
		} catch (Exception e) {
			// ignore
		}
		GenePatternTaskDBLoader loader = new GenePatternTaskDBLoader(lsid,
				null, null, null, username, 0);
		int formerID = loader.getTaskIDByName(lsid, username);
		loader.run(GenePatternTaskDBLoader.DELETE);
		try {
			// remove taskLib directory for this task
			if (libdir != null)
				libdir.delete();
			// delete all searchable indexes for this task
			Indexer.deleteTask(formerID);
		} catch (Exception ioe) {
			System.err.println(ioe
					+ " while deleting taskLib and search index for task "
					+ ti.getName());
		}
	}

	/**
	 * Provides a TreeMap, sorted by case-insensitive task name, of all of the
	 * tasks registered in the task_master table that are handled by the
	 * GenePatternAnalysisTask class.
	 * 
	 * @return TreeMap whose key is task name, and whose value is a TaskInfo
	 *         object (with nested TaskInfoAttributes and ParameterInfo[]).
	 * @author Jim Lerner
	 */
	public static Collection getTasks() throws OmnigeneException,
			RemoteException {
		return getTasks(null);
	}

	public static AnalysisJobDataSource getDS() throws OmnigeneException {
		AnalysisJobDataSource ds;
		try {
			ds = BeanReference.getAnalysisJobDataSourceEJB();
			return ds;
		} catch (Exception e) {
			throw new OmnigeneException(
					"Unable to find analysisJobDataSource: " + e.getMessage());
		}
	}

	/**
	 * getTasks for a specific userID returns a TreeMap of all of the
	 * GenePatternAnalysisTask-supported tasks that are visible to a particular
	 * userID. Tasks are presented in case-insensitive alphabetical order.
	 * 
	 * @param userID
	 *            userID controlling which private tasks will be returned. All
	 *            public tasks are also returned, and are interleaved
	 *            alphabetically with the private tasks.
	 * @return TreeMap whose key is task name, and whose value is a TaskInfo
	 *         object (with nested TaskInfoAttributes and ParameterInfo[]).
	 * @author Jim Lerner
	 */

	public static List getTasksSorted(String userID) throws OmnigeneException,
			RemoteException {
		List vTasks = getTasks(userID); // get vector of TaskInfos
		if (vTasks != null) {
			Collections.sort(vTasks, new Comparator() {
				// case-insensitive compare on task name, then LSID
				public int compare(Object o1, Object o2) {
					TaskInfo t1 = (TaskInfo) o1;
					TaskInfo t2 = (TaskInfo) o2;
					int c = t1.getName().compareToIgnoreCase(t2.getName());
					if (c == 0) {
						String lsid1 = t1.giveTaskInfoAttributes().get(LSID);
						String lsid2 = t2.giveTaskInfoAttributes().get(LSID);
						if (lsid1 == null)
							return 1;
						if (lsid2 == null)
							return -1;
						return -lsid1.compareToIgnoreCase(lsid2);
					}
					return c;
				}
			});
		}
		return vTasks;
	}

	public static List getTasks(String userID) throws OmnigeneException,
			RemoteException {
		Vector vTasks = getDS().getTasks(userID); // get vector of TaskInfos
		return vTasks;
	}

	/**
	 * For a given taskName, look up the TaskInfo object in the database and
	 * return it to the caller. TODO: involve userID in the search!
	 * 
	 * @param taskName
	 *            name of the task to locate
	 * @return TaskInfo complete description of the task (including nested
	 *         TaskInfoAttributes and ParameterInfo[]).
	 * @author Jim Lerner
	 *  
	 */
	public static TaskInfo getTaskInfo(String taskName, String username)
			throws OmnigeneException {
		TaskInfo taskInfo = null;
		try {
			int taskID = -1;
			AnalysisJobDataSource ds = getDS();
			try {
				// search for an existing task with the same name
				GenePatternTaskDBLoader loader = new GenePatternTaskDBLoader(
						taskName, null, null, null, username, 0);
				taskID = loader.getTaskIDByName(taskName, username);
				if (taskID != -1) {
					taskInfo = ds.getTask(taskID);
				}
			} catch (OmnigeneException e) {
				//this is a new task, no taskID exists
				// do nothing
				throw new OmnigeneException("no such task: " + taskName
						+ " for user " + username);
			} catch (RemoteException re) {
				throw new OmnigeneException("Unable to load the " + taskName
						+ " task: " + re.getMessage());
			}
		} catch (Exception e) {
			throw new OmnigeneException(e.getMessage() + " in getTaskInfo("
					+ taskName + ", " + username + ")");
		}
		return taskInfo;
	}

	/**
	 * Locates the directory where the a particular task's files are stored. It
	 * is one level below $omnigene.conf/taskLib. TODO: involve userID in this,
	 * so that there is no conflict among same-named private tasks. Creates the
	 * directory if it doesn't already exist.
	 * 
	 * @param taskName
	 *            name of task to look up
	 * @return directory name on server where taskName support files are stored
	 * @throws Exception
	 *             if genepattern.properties System property not defined
	 * @author Jim Lerner
	 */

	public static String getTaskLibDir(String lsid) throws Exception,
			MalformedURLException {
		LSID l = new LSID(lsid);
		if (l.getAuthority().equals("") || l.getIdentifier().equals("")
				|| !l.hasVersion()) {
			throw new MalformedURLException("invalid LSID");
		}
		return getTaskLibDir(null, lsid, null);
	}

	/**
	 * Locates the directory where the a particular task's files are stored. It
	 * is one level below $omnigene.conf/taskLib. TODO: involve userID in this,
	 * so that there is no conflict among same-named private tasks. Creates the
	 * directory if it doesn't already exist.
	 * 
	 * @param taskName
	 *            name of task to look up
	 * @return directory name on server where taskName support files are stored
	 * @throws Exception
	 *             if genepattern.properties System property not defined
	 * @author Jim Lerner
	 */
	public static String getTaskLibDir(String taskName, String sLSID,
			String username) throws Exception {
		String ret = null;
		if (sLSID != null) {
			ret = (String) htTaskLibDir.get(sLSID);
			if (ret != null)
				return ret;
		}

		try {
			File f = null;
			if (taskLibDir == null) {
				taskLibDir = System.getProperty("genepattern.properties");
				if (taskLibDir == null)
					throw new Exception(
							"GenePatternAnalysisTask.getTaskLibDir: genepattern.properties environment variable not set");
				f = new File(taskLibDir).getParentFile();
				f = new File(f, "taskLib");
				taskLibDir = f.getCanonicalPath();
			}
			LSID lsid = null;
			TaskInfo taskInfo = null;
			if (sLSID != null && sLSID.length() > 0) {
				try {
					lsid = new LSID(sLSID);
					//System.out.println("getTaskLibDir: using lsid " + sLSID +
					// " for task name " + taskName);
					if (taskName == null || taskInfo == null) {
						// lookup task name for this LSID
						taskInfo = getTaskInfo(lsid.toString(), username);
						if (taskInfo != null) {
							taskName = taskInfo.getName();
							if (username == null)
								username = taskInfo.getUserId();
						}
					}
				} catch (MalformedURLException mue) {
					_cat.info("getTaskLibDir: bad sLSID " + sLSID);
				}
			}
			if (lsid == null && taskName != null) {
				try {
					lsid = new LSID(taskName);
					_cat.debug("getTaskLibDir: using lsid from taskName "
							+ taskName);
					// lookup task name for this LSID
					taskInfo = getTaskInfo(lsid.toString(), username);
					if (taskInfo == null)
						throw new Exception("can't getTaskInfo from "
								+ lsid.toString());
					taskName = taskInfo.getName();
					if (username == null)
						username = taskInfo.getUserId();
				} catch (MalformedURLException mue2) {
					// neither LSID nor taskName is an actual LSID. So use the
					// taskName without an LSID
					_cat.info("getTaskLibDir: using taskName " + taskName);
				}
			}
			String dirName = makeDirName(lsid, taskName, taskInfo);
			f = new File(taskLibDir, dirName);
			f.mkdirs();
			ret = f.getCanonicalPath();
			if (lsid != null) {
				htTaskLibDir.put(lsid, ret);
			}
			return ret;
		} catch (Exception e) {
			//e.printStackTrace();
			throw e;
		}
	}

	/**
	 * Locates the directory where the a particular task's files are stored. It
	 * is one level below $omnigene.conf/taskLib. TODO: involve userID in this,
	 * so that there is no conflict among same-named private tasks. Creates the
	 * directory if it doesn't already exist.
	 * 
	 * @param taskName
	 *            name of task to look up
	 * @return directory name on server where taskName support files are stored
	 * @throws Exception
	 *             if genepattern.properties System property not defined
	 * @author Jim Lerner
	 */
	public static String getTaskLibDir(TaskInfo taskInfo) throws Exception {
		File f = null;
		if (taskLibDir == null) {
			taskLibDir = System.getProperty("genepattern.properties");
			if (taskLibDir == null)
				throw new Exception(
						"GenePatternAnalysisTask.getTaskLibDir: genepattern.properties environment variable not set");
			f = new File(taskLibDir).getParentFile();
			f = new File(f, "taskLib");
			taskLibDir = f.getCanonicalPath();
		}
		String taskName = taskInfo.getName();
		TaskInfoAttributes tia = taskInfo.giveTaskInfoAttributes();
		LSID lsid = null;
		try {
			lsid = new LSID(tia.get(IGPConstants.LSID));
		} catch (MalformedURLException mue) {
			// ignore -- not an LSID
		} catch (Exception e2) {
		}
		String dirName = makeDirName(lsid, taskName, taskInfo);
		f = new File(taskLibDir, dirName);
		f.mkdirs();
		return f.getCanonicalPath();
	}

	protected static String makeDirName(LSID lsid, String taskName,
			TaskInfo taskInfo) {
		String dirName;
		int MAX_DIR_LENGTH = 255; // Mac OS X directory name limit
		String version;
		String invariantPart = (taskInfo != null ? ("" + taskInfo.getID())
				: Integer.toString(Math.abs(taskName.hashCode()), 36)); // [a-z,0-9];
		if (lsid != null) {
			//invariantPart = lsid.getAuthority() + "-" + lsid.getNamespace() +
			// "-" + lsid.getIdentifier();
			version = lsid.getVersion();
			if (version.equals("")) {
				//invariantPart = "" + Math.random() + "-" + Math.random() ;
				version = "tmp";
			}
			// String hashBase36 =
			// Integer.toString(Math.abs(invariantPart.hashCode()), 36); //
			// [a-z,0-9]
		} else {
			//try { throw new Exception("no LSID given"); } catch (Exception e)
			// { System.out.println(e.getMessage()); e.printStackTrace(); }
			dirName = taskName;
			version = "1";
		}
		dirName = "." + version + "." + invariantPart; // hashBase36;
		dirName = taskName.substring(0, Math.min(MAX_DIR_LENGTH
				- dirName.length(), taskName.length()))
				+ dirName;
		//	System.out.println("makeDirName: " + dirName + " from task " +
		// (taskInfo != null ? ("" + taskInfo.getID()) : "[no taskInfo]") + ",
		// LSID " + lsid);
		//	if (taskInfo == null) try { throw new Exception("where am I?"); }
		// catch (Exception e) { e.printStackTrace(); }
		return dirName;
	}

	/**
	 * Given a task name and a Hashtable of environment variables, find the path
	 * in the environment and add the named task's directory to the path,
	 * supporting enhanced transparency of execution in the GenePattern
	 * environment for scripts and applications. TODO: add userID to the search
	 * for the task.
	 * 
	 * @param taskName
	 *            name of the task whose <libdir>should be added to the path
	 * @param envVariables
	 *            Hashtable of environment variables (one of which should be the
	 *            path!)
	 * @throws Exception
	 *             if genepattern.properties System property not defined
	 * @author Jim Lerner
	 */
	protected void addTaskLibToPath(String taskName, Hashtable envVariables,
			String sLSID) throws Exception {
		boolean isWindows = System.getProperty("os.name").startsWith("Windows");
		String pathKey = "path";
		String path = (String) envVariables.get(pathKey);
		if (path == null) {
			pathKey = "PATH";
			path = (String) envVariables.get(pathKey);
		}
		String taskDir = getTaskLibDir(taskName, sLSID, null);

		if (isWindows) {
			// Windows
			path = path + System.getProperty("path.separator") + taskDir;
			envVariables.put(pathKey, path);
		} else {
			// Unix shell syntax for path
			if (path.charAt(0) == '(') {
				path = path.substring(0, path.length() - 1) + " " + taskDir
						+ ")";
			} else {
				path = path.substring(0, path.length() - 1) + " " + taskDir;
			}
			envVariables.put(pathKey, path);
		}
		//_cat.debug("path for " + taskName + " set to " + path);
	}

	/**
	 * Fill returned Properties with everything that the user can get a
	 * substitution for, including all System.getProperties() properties plus
	 * all of the actual ParameterInfo name/value pairs.
	 * 
	 * <p>
	 * Each input file gets additional entries for the directory (INPUT_PATH)
	 * the file name (just filename, no path) aka INPUT_FILE, and the base name
	 * (no path, no extension), aka INPUT_BASENAME. These are considered helper
	 * parameters which can be used in command line substitutions.
	 * 
	 * <p>
	 * Other properties added to the command line substitution environment are:
	 * <ul>
	 * <li>NAME (task name)</li>
	 * <li>JOB_ID (job number when executing)</li>
	 * <li>TASK_ID (task ID number from task_master table)</li>
	 * <li>&lt;JAVA&gt; fully qualified filename to Java VM running the
	 * GenePatternAnalysis engine</li>
	 * <li>LIBDIR directory containing the task's support files (post-fixed by
	 * a path separator for convenience of task writer)</li>
	 * </ul>
	 * 
	 * <p>
	 * Called by onJob() to create actual run-time parameter lookup, and by
	 * validateInputs() for both task save-time and task run-time parameter
	 * validation.
	 * <p>
	 * 
	 * @param taskName
	 *            name of task to be run
	 * @param jobNumber
	 *            job number of job to be run
	 * @param taskID
	 *            task ID of job to be run
	 * @param taskInfoAttributes
	 *            TaskInfoAttributes metadata of job to be run
	 * @param parms
	 *            actual parameters to substitute for job to be run
	 * @param env
	 *            Hashtable of environment variables values
	 * @param formalParameters
	 *            ParameterInfo[] of formal parameter definitions, used to
	 *            determine which parameters are input files (therefore needing
	 *            additional attributes added to substitution table)
	 * @author Jim Lerner
	 * @return Properties Properties object with all substitution name/value
	 *         pairs defined
	 */
	public Properties setupProps(String taskName, int jobNumber, int taskID,
			TaskInfoAttributes taskInfoAttributes, ParameterInfo[] actuals,
			Hashtable env, ParameterInfo[] formalParameters, String userID)
			throws Exception {

		Properties props = new Properties();
		try {
			// copy environment variables into props
			String key = null;
			String value = null;
			Enumeration eVariables = null;
			for (eVariables = System.getProperties().propertyNames(); eVariables
					.hasMoreElements();) {
				key = (String) eVariables.nextElement();
				value = System.getProperty(key, "");
				props.put(key, value);
			}
			for (eVariables = env.keys(); eVariables.hasMoreElements();) {
				key = (String) eVariables.nextElement();
				value = (String) env.get(key);
				if (value == null)
					value = "";
				props.put(key, value);
			}

			props.put(NAME, taskName);
			props.put(JOB_ID, Integer.toString(jobNumber));
			props.put(TASK_ID, Integer.toString(taskID));
			props.put(USERID, "" + userID);
			String sLSID = taskInfoAttributes.get(LSID);
			props.put(LSID, sLSID);

			// as a convenience to the user, create a <libdir> property which is
			// where DLLs, JARs, EXEs, etc. are dumped to when adding tasks
			String taskLibDir = (taskID != -1 ? new File(getTaskLibDir(
					taskName, sLSID, userID)).getPath()
					+ System.getProperty("file.separator") : "taskLibDir");
			props.put(LIBDIR, taskLibDir);

			// as a convenience to the user, create a <java> property which will
			// invoke java programs without requiring java.exe on the path
			if (System.getProperty(JAVA, null) == null)
				props.put(JAVA, System.getProperty("java.home")
						+ System.getProperty("file.separator") + "bin"
						+ System.getProperty("file.separator") + "java");
			else
				props.put(JAVA, System.getProperty(JAVA)
						+ System.getProperty("file.separator") + "bin"
						+ System.getProperty("file.separator") + "java");

			// add Perl if it isn't already defined
			if (props.getProperty(PERL, null) == null) {
				props.put(PERL, new File(props.getProperty("user.dir"))
						.getParentFile().getAbsolutePath()
						+ System.getProperty("file.separator")
						+ "perl"
						+ System.getProperty("file.separator")
						+ "bin"
						+ System.getProperty("file.separator") + "perl");
			}
			//  File GenePatternPM = new File(props.get(TOMCAT) + File.separator
			// + ".." + File.separator + "resources");
			String perl = (String) props.get(PERL); // + " -I" +
													// GenePatternPM.getCanonicalPath();
			props.put(PERL, perl);

			// add R if it isn't already defined
			if (props.getProperty(R, null) == null) {
				props.put(R, new File(props.getProperty("user.dir"))
						.getParentFile().getAbsolutePath()
						+ System.getProperty("file.separator")
						+ "R"
						+ System.getProperty("file.separator")
						+ "bin"
						+ System.getProperty("file.separator") + "R");
				props.put(R.toLowerCase(), new File(props
						.getProperty("user.dir")).getParentFile()
						.getAbsolutePath()
						+ System.getProperty("file.separator")
						+ "R"
						+ System.getProperty("file.separator")
						+ "bin"
						+ System.getProperty("file.separator") + "R");
			}
			// BUG: this is NOT R_HOME! This is R_HOME/bin/R
			props.put(R_HOME, props.getProperty(R));

			// R should be <java> <java_flags> -cp <libdir> -DR_HOME=<R> RunR
			// <job_id>
			props.put(R, substitute(LEFT_DELIMITER + JAVA + RIGHT_DELIMITER
					+ " " + LEFT_DELIMITER + "java_flags" + RIGHT_DELIMITER
					+ " -cp ../../webapps/gp/WEB-INF/classes -DR_HOME="
					+ LEFT_DELIMITER + "R_HOME" + RIGHT_DELIMITER + " -D"
					+ IGPConstants.USERID + "=" + LEFT_DELIMITER + USERID
					+ RIGHT_DELIMITER + " RunR " + LEFT_DELIMITER + JOB_ID
					+ RIGHT_DELIMITER, props, null));

			// populate props with the input parameters so that they can be
			// looked up by name
			if (actuals != null) {
				for (int i = 0; i < actuals.length; i++) {
					value = actuals[i].getValue();
					if (value == null)
						value = "";
					props.put(actuals[i].getName(), value);
				}
			}

			String inputFilename = null;
			String inputParamName = null;
			String outDirName = getJobDir(Integer.toString(jobNumber));
			new File(outDirName).mkdirs();

			int j;
			// find input filenames, create _path, _file, and _basename props
			// for each
			if (actuals != null)
				for (int i = 0; i < actuals.length; i++) {
					for (int f = 0; f < formalParameters.length; f++) {
						if (actuals[i].getName().equals(
								formalParameters[f].getName())) {
							if (formalParameters[f].isInputFile()) {
								inputFilename = actuals[i].getValue();
								if (inputFilename == null
										|| inputFilename.length() == 0)
									continue;
								inputParamName = actuals[i].getName();
								File inFile = new File(outDirName, new File(
										inputFilename).getName());
								props.put(inputParamName, inFile.getName());
								props.put(inputParamName + INPUT_PATH,
										new String(outDirName));
								String baseName = inFile.getName();
								if (baseName.startsWith("Axis")) {
									// strip off the AxisNNNNNaxis_ prefix
									j = baseName.indexOf("axis_");
									if (j != -1) {
										baseName = baseName.substring(j + 5);
									}
								}
								props.put(inputParamName + INPUT_FILE,
										new String(baseName)); // filename
															   // without path
								j = baseName.lastIndexOf(".");
								if (j != -1) {
									props.put(inputParamName + INPUT_EXTENSION,
											new String(baseName
													.substring(j + 1))); // filename
																		 // extension
									baseName = baseName.substring(0, j);
								} else {
									props.put(inputParamName + INPUT_EXTENSION,
											""); // filename extension
								}
								if (inputFilename.startsWith("http:")
										|| inputFilename.startsWith("https:")
										|| inputFilename.startsWith("ftp:")) {
									j = baseName.lastIndexOf("?");
									if (j != -1)
										baseName = baseName.substring(j + 1);
									j = baseName.lastIndexOf("&");
									if (j != -1)
										baseName = baseName.substring(j + 1);
									j = baseName.lastIndexOf("=");
									if (j != -1)
										baseName = baseName.substring(j + 1);
								}
								props.put(inputParamName + INPUT_BASENAME,
										new String(baseName)); // filename
															   // without path
															   // or extension
							}
							break;
						}
					}
				}
			return props;

		} catch (NullPointerException npe) {
			_cat.error(npe + " in setupProps.  Currently have:\n" + props);
			throw npe;
		}
	}

	/**
	 * Spawns a separate process to execute the requested analysis task. It
	 * copies the stdout and stderr output streams to StringBuffers so that they
	 * can be returned to the invoker. The stdin input stream is closed
	 * immediately after execution in order to ensure that the running task has
	 * no misconceptions about being able to read anything from it. runCommand
	 * maintains entries in the htRunningJobs Hashtable whose keys are jobIDs
	 * and whose values are running Process objects. This allows Processes to be
	 * stopped by jobID.
	 * 
	 * <p>
	 * Please read about the BUG in the runCommand comments related to a race
	 * condition in the closure of the stdin stream after forking the process.
	 * 
	 * @param commandLine
	 *            String representation of the command line to run with all
	 *            substitutions for parameters made.
	 * @param env
	 *            Hashtable of environment name/value pairs. Used to provide the
	 *            environment to the exec method, including the modified PATH
	 *            value.
	 * @param runDir
	 *            The directory in which to start the process running (it will
	 *            be a temporary directory with only input files in it).
	 * @param stdout
	 *            StringBuffer to capture stdout output from the running process
	 * @param stderr
	 *            StringBuffer to capture stderr output from the running process
	 * @param jobInfo
	 *            JobInfo object for this instance
	 * @author Jim Lerner
	 */
	protected void runCommand(String commandLine[], Hashtable env, File runDir,
			final StringBuffer stdout, final StringBuffer stderr,
			JobInfo jobInfo) throws IOException, InterruptedException,
			Throwable {
		Process process = null;
		String jobID = null;
		try {
			env.remove("SHELLOPTS"); // readonly variable in tcsh and bash, not
									 // critical anyway
			String[] envp = hashTableToStringArray(env);

			// spawn the command
			process = Runtime.getRuntime().exec(commandLine, envp, runDir);

			// BUG: there is race condition during a tiny time window between
			// the exec and the close
			// (the lines above and below this comment) during which it is
			// possible for an application
			// to imagine that there might be useful input coming from stdin.
			// This seemed to be
			// the case for Perl 5.0.1 on Wilkins, and might be a problem in
			// other applications as well.

			process.getOutputStream().close(); // there is no stdin to feed to
											   // the program. So if it asks,
											   // let it see EOF!
			jobID = "" + jobInfo.getJobNumber();
			htRunningJobs.put(jobID, process);

			// create threads to read from the command's stdout and stderr
			// streams
			Thread outputReader = streamToStringBuffer(
					process.getInputStream(), stdout, stderr);
			Thread errorReader = streamToStringBuffer(process.getErrorStream(),
					stderr, stderr);

			// drain the output and error streams
			outputReader.start();
			errorReader.start();

			// wait for all output before attempting to send it back to the
			// client
			outputReader.join();
			errorReader.join();

			// the process will be dead by now
			process.waitFor();

			// TODO: cleanup input file(s)
		} catch (Throwable t) {
			_cat.error(t + " in runCommand, reporting to stderr");
			stderr.append(t.toString() + "\n\n");
			//throw t;
		} finally {
			if (jobID != null) {
				htRunningJobs.remove(jobID);
			}
		}
	}

	/**
	 * takes a filename, "short name" of a file, and JobInfo object and adds the
	 * descriptor of the file to the JobInfo as an output file.
	 * 
	 * @param jobInfo
	 *            JobInfo object that will hold output file descriptor
	 * @param fileName
	 *            full name of the file on the server
	 * @param label
	 *            "short name of the file", ie. the basename without the
	 *            directory
    * @param parentJobInfo the parent job of the given jobInfo or <tt>null</tt> if no parent exists
	 * @author Jim Lerner
	 *  
	 */
	protected void addFileToOutputParameters(JobInfo jobInfo, String fileName,
			String label, JobInfo parentJobInfo) {
		fileName = jobInfo.getJobNumber() + "/" + fileName;
		// try { _cat.debug("addFileToOutputParameters: job " +
		// jobInfo.getJobNumber() + ", file: " + new
		// File(fileName).getCanonicalPath() + " as " + label); } catch
		// (IOException ioe) {}
		ParameterInfo paramOut = new ParameterInfo(label, fileName, "");
		paramOut.setAsOutputFile();
		jobInfo.addParameterInfo(paramOut);
      if(parentJobInfo!=null) {
         parentJobInfo.addParameterInfo(paramOut);
      }
	}

	/**
	 * takes a jobID and a Hashtable in which the jobID is putatively listed,
	 * and attempts to terminate the job. Note that Process.destroy() is not
	 * always successful. If a process cannot be killed without a "kill -9", it
	 * seems not to die from a Process.destroy() either.
	 * 
	 * @param jobID
	 *            JobInfo jobID number
	 * @param htWhere
	 *            Hashtable in which the job was listed when it was invoked
	 * @return true if the job was found, false if not listed (already deleted)
	 * @author Jim Lerner
	 *  
	 */
	protected static boolean terminateJob(String jobID, Hashtable htWhere) {
		Process p = (Process) htWhere.get(jobID);
		if (p != null) {
			p.destroy();
		}
		return (p != null);
	}

	/**
	 * checks that all task parameters are used in the command line and that all
	 * parameters that are cited actually exist. Optional parameters need not be
	 * cited in the command line. Parameter names that match a list of reserved
	 * names are also called out.
	 * 
	 * @param props
	 *            Properties containing environment variables
	 * @param taskName
	 *            name of task that is being checked. Used in error messages.
	 * @param commandLine
	 *            command line for task execution prior to parameter
	 *            substitutions
	 * @param actualParams
	 *            array of ParameterInfo objects for actual parameter values
	 * @param formalParams
	 *            array of ParameterInfo objects for formal parameter values
	 *            (used for optional determination)
	 * @param enforceOptionalNonBlank
	 *            boolean determining whether to complain if non-optional
	 *            parameters are not supplied (true for run-time, false for
	 *            design-time)
	 * @return Vector of error messages (zero length if no problems found)
	 * @author Jim Lerner
	 *  
	 */
	protected Vector validateParameters(Properties props, String taskName,
			String commandLine, ParameterInfo[] actualParams,
			ParameterInfo[] formalParams, boolean enforceOptionalNonBlank) {
		Vector vProblems = new Vector();
		String name;
		boolean runtimeValidation = (actualParams != formalParams);

		// validate R-safe task name
		if (!isRSafe(taskName)) {
			vProblems
					.add(taskName
							+ " is not a legal task name.  It must contain only letters, digits, and periods, and may not begin with a period or digit.\n It must not be a reserved keyword in R ('if', 'else', 'repeat', 'while', 'function', 'for', 'in', 'next', 'break', 'true', 'false', 'null', 'na', 'inf', 'nan').");
		}

		if (commandLine.trim().length() == 0) {
			vProblems.add("Command line not defined");
		}

		// check that each parameter is cited in either the command line or the
		// output filename pattern
		if (actualParams != null) {
			Vector paramNames = new Vector();
			next_parameter: for (int actual = 0; actual < actualParams.length; actual++) {
				name = LEFT_DELIMITER + actualParams[actual].getName()
						+ RIGHT_DELIMITER;
				if (paramNames.contains(actualParams[actual].getName())) {
					vProblems
							.add(taskName
									+ ": "
									+ actualParams[actual].getName()
									+ " has been declared as a parameter more than once"
									+ " for task "
									+ props.get(IGPConstants.LSID));
				}
				paramNames.add(actualParams[actual].getName());
				/*
				 * if (!isRSafe(actualParams[actual].getName())) {
				 * vProblems.add(actualParams[actual].getName() + " is not a
				 * legal parameter name. It must contain only letters, digits,
				 * and periods, and may not begin with a period or digit" + "
				 * for task " + props.get(IGPConstants.LSID)); }
				 */
				for (int j = 0; j < UNREQUIRED_PARAMETER_NAMES.length; j++) {
					if (name.equals(UNREQUIRED_PARAMETER_NAMES[j])) {
						continue next_parameter;
					}
				}
				HashMap hmAttributes = null;
				boolean foundFormal = false;
				int formal;
				for (formal = 0; formal < formalParams.length; formal++) {
					if (formalParams[formal].getName().equals(
							actualParams[actual].getName())) {
						hmAttributes = formalParams[formal].getAttributes();
						foundFormal = true;
						break;
					}
				}

				if (!foundFormal) {
					vProblems.add(taskName + ": supplied parameter " + name
							+ " is not part of the definition for task "
							+ props.get(IGPConstants.LSID));
					continue;
				}

				// for non-optional parameters, make sure they are mentioned in
				// the command line
				if (hmAttributes == null
						|| hmAttributes
								.get(PARAM_INFO_OPTIONAL[PARAM_INFO_NAME_OFFSET]) == null
						|| ((String) hmAttributes
								.get(PARAM_INFO_OPTIONAL[PARAM_INFO_NAME_OFFSET]))
								.length() == 0) {
					if (commandLine.indexOf(name) == -1) {
						vProblems.add(taskName + ": non-optional parameter "
								+ name + " is not cited in the command line"
								+ " for task " + props.get(IGPConstants.LSID));
					} else if (enforceOptionalNonBlank
							&& (actualParams[actual].getValue() == null || actualParams[actual]
									.getValue().length() == 0)
							&& formalParams[formal].getValue().length() == 0) {
						vProblems.add(taskName + ": non-optional parameter "
								+ name + " is blank for task "
								+ props.get(IGPConstants.LSID));
					}
				}
				// check that parameter is not named the same as a predefined
				// parameter
				for (int j = 0; j < RESERVED_PARAMETER_NAMES.length; j++) {
					if (actualParams[actual].getName().equalsIgnoreCase(
							RESERVED_PARAMETER_NAMES[j])) {
						vProblems
								.add(taskName
										+ ": parameter "
										+ name
										+ " is a reserved name and cannot be used as a parameter name for task "
										+ props.get(IGPConstants.LSID));
					}
				}

				// if the parameter is part of a choice list, verify that the
				// default is on the list
				String dflt = (String) hmAttributes
						.get(PARAM_INFO_DEFAULT_VALUE[PARAM_INFO_NAME_OFFSET]);
				String actualValue = actualParams[actual].getValue();
				String choices = formalParams[formal].getValue();
				String[] stChoices = formalParams[formal]
						.getChoices(PARAM_INFO_CHOICE_DELIMITER);
				if (dflt != null
						&& dflt.length() > 0
						&& formalParams[formal]
								.hasChoices(PARAM_INFO_CHOICE_DELIMITER)) {
					boolean foundDefault = false;
					boolean foundActual = false;
					for (int iChoice = 0; iChoice < stChoices.length; iChoice++) {
						String entry = stChoices[iChoice];
						StringTokenizer stChoiceEntry = new StringTokenizer(
								entry, PARAM_INFO_TYPE_SEPARATOR);
						String sLHS = "";
						String sRHS = "";
						if (stChoiceEntry.hasMoreTokens()) {
							sLHS = stChoiceEntry.nextToken();
						}
						if (stChoiceEntry.hasMoreTokens()) {
							sRHS = stChoiceEntry.nextToken();
						}
						if (sLHS.equals(dflt) || sRHS.equals(dflt)) {
							foundDefault = true;
							break;
						}
					}
					if (!foundDefault) {
						vProblems.add("Default value '" + dflt
								+ "' for parameter " + name
								+ " was not found in the choice list '"
								+ choices + "'" + " for task "
								+ props.get(IGPConstants.LSID));
					}
				}

				// check for valid choice selection
				if (runtimeValidation
						&& formalParams[formal]
								.hasChoices(PARAM_INFO_CHOICE_DELIMITER)) {
					boolean foundActual = false;
					for (int iChoice = 0; iChoice < stChoices.length; iChoice++) {
						String entry = stChoices[iChoice];
						StringTokenizer stChoiceEntry = new StringTokenizer(
								entry, PARAM_INFO_TYPE_SEPARATOR);
						String sLHS = "";
						String sRHS = "";
						if (stChoiceEntry.hasMoreTokens()) {
							sLHS = stChoiceEntry.nextToken();
						}
						if (stChoiceEntry.hasMoreTokens()) {
							sRHS = stChoiceEntry.nextToken();
						}
						if (sLHS.equals(actualValue)
								|| sRHS.equals(actualValue)) {
							foundActual = true;
							break;
						}
					}
					if (!foundActual) {
						vProblems.add("Value '" + actualValue
								+ "' for parameter " + name
								+ " was not found in the choice list '"
								+ choices + "'" + " for task "
								+ props.get(IGPConstants.LSID));
					}
				}
			}
		}

		// check that each substitution variable listed in the command line is
		// actually in props
		vProblems = validateSubstitutions(props, taskName, commandLine,
				"command line", vProblems, formalParams);
		return vProblems;
	}

	/**
	 * checks that each substition variable listed in the task command line
	 * actually exists in the ParameterInfo array for the task.
	 * 
	 * @param props
	 *            Properties object containing substitution variable name/value
	 *            pairs
	 * @param taskName
	 *            name of task to be validated (used in error messages)
	 * @param commandLine
	 *            command line to be validated
	 * @param source
	 *            identifier for what is being checked (command line) for use in
	 *            error messages
	 * @param vProblems
	 *            Vector of problems already found, to be appended with new
	 *            problems and returned from this method
	 * @param formalParams
	 *            ParameterInfo array of formal parameter definitions (used for
	 *            optional determination)
	 * @return Vector of error messages (vProblems with new errors appended)
	 * @author Jim Lerner
	 */
	protected Vector validateSubstitutions(Properties props, String taskName,
			String commandLine, String source, Vector vProblems,
			ParameterInfo[] formalParams) {
		// check that each substitution variable listed in the command line is
		// actually in props
		int start = 0;
		int end;
		int blank;
		String varName;
		while (start < commandLine.length()
				&& (start = commandLine.indexOf(LEFT_DELIMITER, start)) != -1) {
			end = commandLine.indexOf(RIGHT_DELIMITER, start);
			if (end == -1)
				break;
			blank = commandLine.indexOf(" ", start) + 1;
			if (blank != 0 && blank < end) {
				// if there's a space in the name, then it's a redirection of
				// stdin
				start = blank;
				continue;
			}
			varName = commandLine.substring(start + LEFT_DELIMITER.length(),
					end);
			if (!varName.endsWith(INPUT_PATH)) {
				if (!props.containsKey(varName)) {
					boolean isOptional = false;
					for (int i = 0; i < formalParams.length; i++) {
						if (!formalParams[i].getName().equals(varName))
							continue;
						HashMap hmAttributes = formalParams[i].getAttributes();
						if (hmAttributes != null
								&& hmAttributes
										.get(PARAM_INFO_OPTIONAL[PARAM_INFO_NAME_OFFSET]) != null
								&& ((String) hmAttributes
										.get(PARAM_INFO_OPTIONAL[PARAM_INFO_NAME_OFFSET]))
										.length() != 0) {
							isOptional = true;
						}
						break;
					}
					if (!isOptional) {
						vProblems.add(taskName
								+ ": no substitution available for "
								+ LEFT_DELIMITER + varName + RIGHT_DELIMITER
								+ " in " + source + " " + commandLine
								+ " for task " + props.get(IGPConstants.LSID));
					}
				}
			}
			start = end + RIGHT_DELIMITER.length();
		}
		return vProblems;
	}

	/**
	 * takes a taskInfoAttributes and ParameterInfo array for a new task and
	 * validates that the input parameters are all accounted for. It returns a
	 * Vector of error messages to the caller (zero length if all okay).
	 * 
	 * @param taskname
	 *            name of task (used in error messages)
	 * @param tia
	 *            TaskInfoAttributes (HashMap) containing command line
	 * @param params
	 *            ParameterInfo array of formal parameter definitions
	 * @return Vector of error messages from validation of inputs
	 * @author Jim Lerner
	 *  
	 */
	public static Vector validateInputs(String taskName,
			TaskInfoAttributes tia, ParameterInfo[] params) {
		GenePatternAnalysisTask gp = new GenePatternAnalysisTask();
		Vector vProblems = null;
		try {
			Properties props = gp.setupProps(taskName, 0, -1, tia, params,
					GenePatternAnalysisTask.getEnv(), params, null);
			vProblems = gp.validateParameters(props, taskName, tia
					.get(COMMAND_LINE), params, params, false);
		} catch (Exception e) {
			vProblems = new Vector();
			vProblems.add(e.toString() + " while validating inputs for "
					+ tia.get(IGPConstants.LSID));
			e.printStackTrace();
		}
		return vProblems;
	}

	/**
	 * Determine whether a proposed method or identifier name is a legal
	 * identifier. Although there are many possible standards, the R language
	 * defines what seems to be both a strict and reasonable definition, and has
	 * the added bonus of making R scripts work properly.
	 * 
	 * According to the R language reference manual:
	 * 
	 * Identifiers consist of a sequence of letters, digits and the period
	 * (�.�). They must not start with a digit, nor with a period followed by a
	 * digit. The definition of a letter depends on the current locale: the
	 * precise set of characters allowed is given by the C expression
	 * (isalnum(c) || c==�.�) and will include accented letters in many Western
	 * European locales.
	 * 
	 * @param varName
	 *            proposed variable name
	 * @return boolean if the proposed name is R-legal
	 * @author Jim Lerner
	 *  
	 */
	public static boolean isRSafe(String varName) {
		// anything but letters, digits, and period is an invalid R identifier
		// that must be quoted
		String validCharacters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789.";
		String[] reservedNames = new String[] { "if", "else", "repeat",
				"while", "function", "for", "in", "next", "break", "true",
				"false", "null", "na", "inf", "nan" };
		boolean isReservedName = false;
		for (int i = 0; i < reservedNames.length; i++) {
			if (varName.equals(reservedNames[i]))
				isReservedName = true;
		}
		StringTokenizer stVarName = new StringTokenizer(varName,
				validCharacters);
		boolean ret = varName.length() > 0 && // the name is not empty
				stVarName.countTokens() == 0 && // it consists of only letters,
												// digits, and periods
				varName.charAt(0) != '.' && // it doesn't begin with a period
				!Character.isDigit(varName.charAt(0)) && // it doesn't begin
														 // with a digit
				!isReservedName; // it isn't a reserved name
		return ret;
	}

	/**
	 * encapsulate an invalid R identifier name in quotes if necessary
	 * 
	 * @param varName
	 *            variable name
	 * @return variable name, quoted if necessary
	 * @author Jim Lerner
	 *  
	 */
	public static String rEncode(String varName) {
		// anything but letters, digits, and period is an invalid R identifier
		// that must be quoted
		if (isRSafe(varName)) {
			return varName;
		} else {
			return "\"" + replace(varName, "\"", "\\\"") + "\"";
		}
	}

	/**
	 * marshalls all of the attributes which make up a task (name, description,
	 * TaskInfoAttributes, ParameterInfo[]), validates that they will ostensibly
	 * work (parameter substitutions all accounted for), and creates a new or
	 * updated task database entry (via a DBLoader invocation). If there are
	 * validation errors, the task is not created and the error message(s) are
	 * returned to the caller. Otherwise (all okay), null is returned.
	 * 
	 * @param name
	 *            task name
	 * @param description
	 *            description of task
	 * @param params
	 *            ParameterInfo[] of formal parameters for the task
	 * @param taskInfoAttributes
	 *            GenePattern TaskInfoAttributes describing metadata for the
	 *            task
	 * @throws OmnigeneException
	 *             if DBLoader is unhappy when connecting to Omnigene
	 * @throws RemoteException
	 *             if DBLoader is unhappy when connecting to Omnigene
	 * @return Vector of String error messages if there was an error validating
	 *         the command line and input parameters, otherwise null to indicate
	 *         success
	 * @see #installTask(String, String, int)
	 * @author Jim Lerner
	 *  
	 */
	protected static Vector installTask(String name, String description, ParameterInfo[] params,
			TaskInfoAttributes taskInfoAttributes, String username,
			int access_id) throws OmnigeneException, RemoteException {
		String originalUsername = username;
		Vector vProblems = GenePatternAnalysisTask.validateInputs(name,
				taskInfoAttributes, params);
		if (vProblems.size() > 0)
			return vProblems;

		//System.out.println("GPAT.installTask: installing " + name + " with
		// LSID " + taskInfoAttributes.get(LSID));

		// privacy is stored both in the task_master table as a field, and in
		// the taskInfoAttributes
		taskInfoAttributes.put(PRIVACY, access_id == ACCESS_PRIVATE ? PRIVATE
				: PUBLIC);
		if (access_id == ACCESS_PRIVATE) {
			taskInfoAttributes.put(USERID, username);
		}

		String lsid = taskInfoAttributes.get(LSID);
		if (lsid == null || lsid.equals("")) {
			//System.out.println("installTask: creating new LSID");
			lsid = LSIDManager.getInstance().createNewID().toString();
			taskInfoAttributes.put(LSID, lsid);
		}

		// TODO: if the task is a pipeline, generate the serialized model right
		// now too
		GenePatternTaskDBLoader loader = new GenePatternTaskDBLoader(name,
				description, params, taskInfoAttributes.encode(),
				username, access_id);

		int formerID = loader.getTaskIDByName(lsid, originalUsername);
		boolean isNew = (formerID == -1);
		if (!isNew) {

			try {
				// delete the search engine indexes for this task so that it
				// will be reindexed
				_cat.debug("installTask: deleting index for previous task ID "
						+ formerID);
				Indexer.deleteTask(formerID);
				_cat.debug("installTask: deleted index");
			} catch (Exception ioe) {
				_cat.info(ioe + " while deleting search index for task " + name
						+ " during update");

				System.err.println(ioe
						+ " while deleting search index for task " + name
						+ " during update");
			}
		}
		loader.run(isNew ? GenePatternTaskDBLoader.CREATE
				: GenePatternTaskDBLoader.UPDATE);
		IndexerDaemon.notifyTaskUpdate(loader.getTaskIDByName(
				LSID != null ? lsid : name, username));
		return null;
	}

	/**
	 * use installTask but first manage the LSID. if it has one, keep it
	 * unchanged. If not, create a new one to be used when creating a new task
	 * or installing from a zip file
	 */
	public static String installNewTask(String name, String description, ParameterInfo[] params,
			TaskInfoAttributes taskInfoAttributes, String username,
			int access_id) throws OmnigeneException, RemoteException,
			TaskInstallationException {
		LSID taskLSID = null;
		String requestedLSID = taskInfoAttributes.get(LSID);
		if (requestedLSID != null && requestedLSID.length() > 0) {
			try {
				taskLSID = new LSID(requestedLSID);
			} catch (MalformedURLException mue) {
				mue.printStackTrace();
				// XXX what to do here? Create a new one from scratch!
			}
		}

		LSIDManager lsidManager = LSIDManager.getInstance();
		if (taskLSID == null) {
			//System.out.println("installNewTask: creating new LSID");
			taskLSID = lsidManager.createNewID();
		} else {
			taskLSID = lsidManager.getNextIDVersion(requestedLSID);
		}
		taskInfoAttributes.put(IGPConstants.LSID, taskLSID.toString());
		//System.out.println("GPAT.installNewTask: new LSID=" +
		// taskLSID.toString());

		Vector probs = installTask(name, description, params,
				taskInfoAttributes, username, access_id);
		if ((probs != null) && (probs.size() > 0)) {
			throw new TaskInstallationException(probs);
		}
		return taskLSID.toString();
	}

	/**
	 * use installTask but first manage LSID, If it has a local one, update the
	 * version. If it has an external LSID create a new one. Used when modifying
	 * an existing task in an editor
	 */
	public static String updateTask(String name, String description, ParameterInfo[] params,
			TaskInfoAttributes taskInfoAttributes, String username,
			int access_id) throws OmnigeneException, RemoteException,
			TaskInstallationException {
		LSID taskLSID = null;
		LSIDManager mgr = LSIDManager.getInstance();
		try {
			//System.out.println("updateTask: old LSID=" +
			// taskInfoAttributes.get(LSID));
			taskLSID = new LSID(taskInfoAttributes.get(LSID));
		} catch (MalformedURLException mue) {
			mue.printStackTrace();
			// XXX what to do here?
			System.err.println("updateTask: " + mue);
		}

		if (taskLSID == null) { // old task from 1.1 or earlier
			taskLSID = mgr.createNewID();
			//System.out.println("updateTask: creating new ID: " +
			// taskLSID.toString());
			taskInfoAttributes.put(LSID, taskLSID.toString());
		} else if (mgr.getAuthority().equalsIgnoreCase(taskLSID.getAuthority())) {
			//System.out.println("updateTask: getting next version for " +
			// taskLSID);
			try {
				taskLSID = mgr.getNextIDVersion(taskLSID);
			} catch (MalformedURLException mue) {
				Vector vProblem = new Vector();
				vProblem.add(mue.getMessage());
				throw new TaskInstallationException(vProblem);
			}
			//System.out.println("updateTask: next version for existing ID=" +
			// taskLSID.toString());
			taskInfoAttributes.put(IGPConstants.LSID, taskLSID.toString());
		} else {
			//System.out.println("updateTask: got authority " +
			// taskLSID.getAuthority() + " but expected " + mgr.getAuthority());
			// modifying someone elses task. Give it a new LSID here
			String provenance = taskInfoAttributes
					.get(IGPConstants.LSID_PROVENANCE);
			provenance = provenance + "  " + taskLSID.toString();
			taskInfoAttributes.put(IGPConstants.LSID_PROVENANCE, provenance);

			taskLSID = mgr.createNewID();
			//System.out.println("updateTask: creating new ID for someone
			// else's provenance: " + taskLSID.toString());
			taskInfoAttributes.put(LSID, taskLSID.toString());
		}

		Vector probs = installTask(name, description, params,
				taskInfoAttributes, username, access_id);

		if ((probs != null) && (probs.size() > 0)) {
			throw new TaskInstallationException(probs);
		}
		return taskLSID.toString();
	}

	public static boolean taskExists(String taskName, String user)
			throws OmnigeneException {

		TaskInfo existingTaskInfo = null;
		try {
			existingTaskInfo = GenePatternAnalysisTask.getTaskInfo(taskName,
					user);
		} catch (OmnigeneException oe) {
			// ignore
		}
		return (existingTaskInfo != null);
	}

	/**
	 * returns the userID value extracted from an HTTP cookie. If the user is
	 * unidentified, this method sends a redirect to the browser to request the
	 * user to login, and the login page will then redirect the user back to the
	 * original page with a valid userID now known.
	 * 
	 * @param request
	 *            HttpServletRequest containing a cookie with userID (if they
	 *            are logged in)
	 * @param response
	 *            HttpServletResponse which is used to redirect the browser if
	 *            the user is not logged in yet
	 * @return String userID after login
	 * @author Jim Lerner
	 *  
	 */
	public static String getUserID(HttpServletRequest request, HttpServletResponse response) {
		String userID = null;
		if (request.getAttribute(USER_LOGGED_OFF) != null) {
			return userID;
		}
		Cookie[] cookies = request.getCookies();
		if (cookies != null) {
			for (int i = 0; i < cookies.length; i++) {
				if (cookies[i].getName().equals(USERID)) {
					userID = cookies[i].getValue();
					if (userID.length() > 0) {
						break;
					}
				}
			}
		}

		if ((userID == null || userID.length() == 0)
				&& request.getParameter(USERID) != null) {
			userID = request.getParameter(USERID);
		}

		if ((userID == null || userID.length() == 0) && response != null) {
			// redirect to the fully-qualified host name to make sure that the
			// one cookie that we are allowed to write is useful
			try {
				String fqHostName = InetAddress.getLocalHost()
						.getCanonicalHostName();
				if (fqHostName.equals("localhost"))
					fqHostName = "127.0.0.1";
				String serverName = request.getServerName();
				if (!fqHostName.equalsIgnoreCase(serverName)) {
					String URL = request.getRequestURI();
					if (request.getQueryString() != null)
						URL = URL + ("?" + request.getQueryString());
					String fqAddress = "http://" + fqHostName + ":"
							+ request.getServerPort() + "/gp/login.jsp?origin="
							+ URLEncoder.encode(URL, UTF8);
					response.sendRedirect(fqAddress);
					return null;
				}
				response.sendRedirect("login.jsp");
				return null;
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}
		if (userID != null) {
			// strip surrounding quotes, if they exist
			if (userID.startsWith("\"")) {
				userID = userID.substring(1, userID.length() - 1);
				try {
					userID = URLDecoder.decode(userID, UTF8);
				} catch (UnsupportedEncodingException uee) { /* ignore */
				}
			}
		}
		return userID;
	}

	/**
	 * takes a job number and returns the directory where output files from that
	 * job are/will be stored. <b>This routine depends on having the System
	 * property java.io.tmpdir set the same for both the Tomcat and JBoss
	 * instantiations. </b>
	 * 
	 * @param jobNumber
	 *            the job number whose storage directory is being sought
	 * @return String directory name on server of this job's files
	 * @author Jim Lerner
	 *  
	 */
	public static String getJobDir(String jobNumber) {
		String tmpDir = System.getProperty("jobs");
		if (!tmpDir.endsWith(File.separator))
			tmpDir = tmpDir + "/";
		tmpDir = tmpDir + jobNumber;
		return tmpDir;
	}

	// SourceForge support:

	/**
	 * returns a TreeMap of downloadable GenePattern tasks in the repository at
	 * SourceForge.net Each task in the "genepattern" project and with a ".zip"
	 * file extension is returned. The TreeMap keys are in the format " <name>,
	 * <size><date>", and the values are URL hrefs to each task, ready to
	 * download.
	 * 
	 * @author Jim Lerner
	 * @see #getSourceForgeTasks(String, String)
	 * @return TreeMap of task description/URL pairs. See
	 *         getSourceForgeTasks(projectName, fileType) for more information.
	 * @throws IOException
	 *             if an error occurs while communicating with SourceForge
	 */
	public static TreeMap getSourceForgeTasks() throws IOException {
		return getSourceForgeTasks("genepattern", ".zip");
	}

	/**
	 * returns a TreeMap of downloadable GenePattern tasks in the repository at
	 * SourceForge.net Each task in the named project and with a matching file
	 * extension is returned. The TreeMap keys are in the format " <name>,
	 * <size><date>", and the values are URL hrefs to each task, ready to
	 * download. This routine basically screen-scrapes the SourceForge website
	 * to dig up this information and returns it in a pseudo-structured format.
	 * It isn't pretty, but it does work. Unfortunately, SourceForge is fairly
	 * slow to render the underlying page.
	 * 
	 * @param projectName
	 *            name of the SourceForge project (eg. "genepattern")
	 * @param fileType
	 *            filename extension of interest (eg. ".zip")
	 * @author Jim Lerner
	 * @throws IOException
	 *             if an error occurs while communicating with SourceForge
	 */
	public static TreeMap getSourceForgeTasks(String projectName,
			String fileType) throws IOException {
		TreeMap tmOut = new TreeMap(String.CASE_INSENSITIVE_ORDER);
		// TODO: check out the RSS XML feed at
		// http://sourceforge.net/export/rss2_projfiles.php?project=genepattern
		String sourceForgeURL = "http://sourceforge.net/project/showfiles.php?group_id=72311";
		String finalURL = "http://twtelecom.dl.sourceforge.net/sourceforge/"
				+ projectName + "/";
		String BEGIN_FILE_LIST = "Below is a list of all files of the project";
		String END_FILE_LIST = "Project Totals:";
		String START_NAME = "<h3>";
		String END_NAME = "</h3>";
		String START_FILEDATE = "<b>";
		String END_FILEDATE = "</b>";
		String HREF_START = "<A HREF=\"http://prdownloads.sourceforge.net/"
				+ projectName + "/";
		String DOWNLOAD = "?download";
		String HREF_END = DOWNLOAD + "\">";
		String START_SIZE = "</A></TD><TD align=\"right\">";
		String END_SIZE = "</TD>";
		String START_ENTRY = "<TR>";
		String END_ENTRY = "</TR>";
		String sPage = null;
		int start, end;

		StringBuffer sbFilePage = new StringBuffer(30000);
		try {
			BufferedReader is = new BufferedReader(new InputStreamReader(
					new URL(sourceForgeURL).openStream()));
			while (is.ready()) {
				sbFilePage.append(is.readLine());
			}
			is.close();
		} catch (IOException ioe) {
			throw new IOException(ioe + " while accessing " + sourceForgeURL);
		}
		sPage = sbFilePage.toString();
		start = sPage.indexOf(BEGIN_FILE_LIST);
		end = sPage.indexOf(END_FILE_LIST, start);
		sPage = sPage.substring(start, end);
		start = sPage.indexOf(START_ENTRY, start);
		start = 0;
		String href = null;
		String item = null;
		String itemData = "";
		String uploadDate = "";
		String fileSize = null;
		for (start = sPage.indexOf(START_NAME, start); start != -1; start = sPage
				.indexOf(START_NAME, start)) {
			end = sPage.indexOf(END_ENTRY, start) + END_ENTRY.length();
			end = sPage.indexOf(END_ENTRY, end) + END_ENTRY.length();

			end = sPage.indexOf(END_NAME, start);
			item = sPage.substring(start + START_NAME.length(), end);
			start = end + END_NAME.length();

			end = sPage.indexOf(START_FILEDATE, start);
			if (end == -1) {
				// no releases for this file
				start = sPage.indexOf(END_ENTRY, start) + END_ENTRY.length();
				continue;
			}
			start = end;
			end = sPage.indexOf(END_FILEDATE, start);
			uploadDate = sPage.substring(start + START_FILEDATE.length(), end);

			start = sPage.indexOf(HREF_START, end + END_FILEDATE.length());
			if (start == -1) {
				continue;
			}
			end = sPage.indexOf(HREF_END, start);
			if (end == -1) {
				_cat.error("couldn't find end of HREF starting at "
						+ sPage.substring(start) + " for " + item);
				break;
			}
			href = sPage.substring(start + HREF_START.length(), end);
			start = end + HREF_END.length();

			start = sPage.indexOf(START_SIZE, start);
			end = sPage.indexOf(END_SIZE, start + START_SIZE.length());
			fileSize = sPage.substring(start + START_SIZE.length(), end);
			start = end + END_SIZE.length();

			start = sPage.indexOf(END_ENTRY, start) + END_ENTRY.length();

			if (fileType != null && !href.endsWith(fileType)) {
				// not a downloadable task file
				continue;
			}
			href = finalURL + href;

			tmOut.put(item + "," + uploadDate + "  " + fileSize, href);
		}
		return tmOut;
	}

	// zip file support:

	/**
	 * inspects a GenePattern-packaged task in a zip file and returns the name
	 * of the task contained therein
	 * 
	 * @param zipFilename
	 *            filename of zip file containing a GenePattern task
	 * @return name of task in zip file
	 * @author Jim Lerner
	 * @throws IOException
	 *             if an error occurs opening the zip file (eg. file not found)
	 *  
	 */
	public static String getTaskNameFromZipFile(String zipFilename)
			throws IOException {
		Properties props = getPropsFromZipFile(zipFilename);
		return props.getProperty(NAME);
	}

	/**
	 * opens a GenePattern-packaged task and returns a Properties object
	 * containing all of the TaskInfo, TaskInfoAttributes, and ParameterInfo[]
	 * data for the task.
	 * 
	 * @param zipFilename
	 *            filename of the GenePattern task zip file
	 * @return Properties object containing key/value pairs for all of the
	 *         TaskInfo, TaskInfoAttributes, and ParameterInfo[]
	 * @throws IOException
	 *             if an error occurs opening the zip file
	 * @author Jim Lerner
	 *  
	 */
	public static Properties getPropsFromZipFile(String zipFilename)
			throws IOException {
		if (!zipFilename.endsWith(".zip"))
			throw new IOException(zipFilename + " is not a zip file");
		ZipFile zipFile = new ZipFile(zipFilename);
		ZipEntry manifestEntry = zipFile
				.getEntry(IGPConstants.MANIFEST_FILENAME);
		if (manifestEntry == null) {
			zipFile.close();
			throw new IOException(
					zipFilename
							+ " is missing a GenePattern manifest file.  It probably isn't a GenePattern task package.");
		}
		Properties props = new Properties();
		try {
			props.load(zipFile.getInputStream(manifestEntry));
		} catch (IOException ioe) {
			throw new IOException(
					zipFilename
							+ " is probably not a GenePattern zip file.  The manifest file cannot be loaded.  "
							+ ioe.getMessage());
		} finally {
			zipFile.close();
		}
		return props;
	}

	/**
	 * opens a GenePattern-packaged task in the form of a remote URL and returns
	 * a Properties object containing all of the TaskInfo, TaskInfoAttributes,
	 * and ParameterInfo[] data for the task.
	 * 
	 * @param zipURL
	 *            URL of the GenePattern task zip file
	 * @return Properties object containing key/value pairs for all of the
	 *         TaskInfo, TaskInfoAttributes, and ParameterInfo[]
	 * @throws Exception
	 *             if an error occurs accessing the URL (no such host, no such
	 *             URL, not a zip file, etc.)
	 * @author Jim Lerner
	 *  
	 */
	public static Properties getPropsFromZipURL(String zipURL) throws Exception {
		try {
			URL url = new URL(zipURL);
			URLConnection conn = url.openConnection();
			if (conn == null)
				_cat.error("null conn in getPropsFromZipURL");
			InputStream is = conn.getInputStream();
			if (is == null)
				_cat.error("null is in getPropsFromZipURL");
			ZipInputStream zis = new ZipInputStream(is);
			ZipEntry zipEntry = null;
			Properties props = null;
			while (true) {
				try {
					zipEntry = zis.getNextEntry();
					if (zipEntry == null)
						break;
				} catch (ZipException ze) {
					break; // EOF
				}
				if (zipEntry.getName().equals(IGPConstants.MANIFEST_FILENAME)) {
					long manifestSize = zipEntry.getSize();
					if (manifestSize == -1)
						manifestSize = 10000;
					byte b[] = new byte[(int) manifestSize];
					int numRead = zis.read(b, 0, (int) manifestSize);
					props = new Properties();
					props.load(new ByteArrayInputStream(b, 0, numRead));
					props.setProperty("size", "" + conn.getContentLength());
					props.setProperty("created", "" + conn.getLastModified());
					break;
				}
			}
			zis.close();
			is.close();
			return props;
		} catch (Exception e) {
			_cat.error(e + " in getPropsFromZipURL while reading " + zipURL);
			throw e;
		}
	}

	/**
	 * accepts the filename of a GenePattern-packaged task in the form of a zip
	 * file, unpacks it, and installs the task in the Omnigene task database.
	 * Any taskLib entries (files such as scripts, DLLs, properties, etc.) from
	 * the zip file are installed in the appropriate taskLib directory.
	 * 
	 * @param zipFilename
	 *            filename of zip file containing task to install
	 * @return Vector of String error messages if unsuccessful, null if okay
	 * @see #installTask(String, String, String, ParameterInfo[],
	 *      TaskInfoAttributes, username, access_id)
	 * @author Jim Lerner
	 *  
	 */
	public static String installNewTask(String zipFilename, String username,
			int access_id, boolean recursive) throws TaskInstallationException {
		Vector vProblems = new Vector();
		int i;
		ZipFile zipFile = null;
		InputStream is = null;
		File outFile = null;
		FileOutputStream os = null;
		String taskName = zipFilename;
		String lsid = null;
		try {
			String name;
			try {
				zipFile = new ZipFile(zipFilename);
			} catch (IOException ioe) {
				throw new Exception("Couldn't open " + zipFilename + ": "
						+ ioe.getMessage());
			}
			ZipEntry manifestEntry = zipFile.getEntry(MANIFEST_FILENAME);
			ZipEntry zipEntry = null;
			long fileLength = 0;
			int numRead = 0;
			if (manifestEntry == null) {
				// is it a zip of zips?
				for (Enumeration eEntries = zipFile.entries(); eEntries
						.hasMoreElements();) {
					zipEntry = (ZipEntry) eEntries.nextElement();
					if (zipEntry.getName().endsWith(".zip")) {
						continue;
					}
					throw new Exception(MANIFEST_FILENAME
							+ " file not found in " + zipFilename);
				}
				// if we get here, the zip file contains only other zip files
				// recursively install them
				String firstLSID = null;
				for (Enumeration eEntries = zipFile.entries(); eEntries
						.hasMoreElements();) {
					zipEntry = (ZipEntry) eEntries.nextElement();
					is = zipFile.getInputStream(zipEntry);
					outFile = new File(System.getProperty("java.io.tmpdir"),
							zipEntry.getName());
					outFile.deleteOnExit();
					os = new FileOutputStream(outFile);
					fileLength = zipEntry.getSize();
					numRead = 0;
					byte[] buf = new byte[100000];
					while ((i = is.read(buf, 0, buf.length)) > 0) {
						os.write(buf, 0, i);
						numRead += i;
					}
					os.close();
					os = null;
					outFile.setLastModified(zipEntry.getTime());
					if (numRead != fileLength) {
						vProblems.add("only read " + numRead + " of "
								+ fileLength + " bytes in " + zipFilename
								+ "'s " + zipEntry.getName());
					}
					is.close();
					_cat.info("installing " + outFile.getAbsolutePath());
					lsid = installNewTask(outFile.getAbsolutePath(), username,
							access_id);
					_cat.info("installed " + lsid);
					if (firstLSID == null)
						firstLSID = lsid;
					outFile.delete();
					if (!recursive)
						break; // only install the top level (first entry)
				}
				return firstLSID;
			}
			Properties props = new Properties();
			props.load(zipFile.getInputStream(manifestEntry));
			taskName = (String) props.remove(NAME);
			lsid = (String) props.get(LSID);
			LSID l = new LSID(lsid); //; throw MalformedURLException if this is
									 // a bad LSID
			if (taskName == null || taskName.length() == 0) {
				vProblems.add("Missing task name in manifest in "
						+ new File(zipFilename).getName());
				throw new TaskInstallationException(vProblems); // abandon ship!
			}
			String taskDescription = (String) props.remove(DESCRIPTION);

			// ParameterInfo entries consist of name/value/description triplets,
			// of which the value and description are optional
			// It is assumed that the names are p[1-n]_name, p[1-n]_value, and
			// p[1-n]_description
			// and that the numbering runs consecutively. When there is no
			// p[m]_name value, then there are m-1 ParameterInfos

			// count ParameterInfo entries
			int numParameterInfos = 0;
			String value;
			String description;

			Vector vParams = new Vector();
			ParameterInfo pi = null;
			for (i = 1; i <= MAX_PARAMETERS; i++) {
				name = (String) props.remove("p" + i + "_name");
				if (name == null)
					continue;
				if (name == null || name.length() == 0)
					throw new Exception("missing parameter name for " + "p" + i
							+ "_name");
				value = (String) props.remove("p" + i + "_value");
				if (value == null)
					value = "";
				description = (String) props.remove("p" + i + "_description");
				if (description == null)
					description = "";
				pi = new ParameterInfo(name, value, description);
				HashMap attributes = new HashMap();
				for (int attribute = 0; attribute < PARAM_INFO_ATTRIBUTES.length; attribute++) {
					name = (String) PARAM_INFO_ATTRIBUTES[attribute][0];
					value = (String) props.remove("p" + i + "_" + name);
					if (value != null) {
						attributes.put(name, value);
					}
					if (name.equals(PARAM_INFO_TYPE[0]) && value != null
							&& value.equals(PARAM_INFO_TYPE_INPUT_FILE)) {
						attributes.put(ParameterInfo.MODE,
								ParameterInfo.INPUT_MODE);
						attributes.put(ParameterInfo.TYPE,
								ParameterInfo.FILE_TYPE);
					}
				}

				for (Enumeration p = props.propertyNames(); p.hasMoreElements();) {
					name = (String) p.nextElement();
					if (!name.startsWith("p" + i + "_"))
						continue;
					value = (String) props.remove(name);
					//_cat.debug("installTask: " + taskName + ": parameter " +
					// name + "=" + value);
					name = name.substring(name.indexOf("_") + 1);
					attributes.put(name, value);
				}

				if (attributes.size() > 0) {
					pi.setAttributes(attributes);
				}
				vParams.add(pi);
			}
			ParameterInfo[] params = new ParameterInfo[vParams.size()];
			vParams.copyInto(params);

			// all remaining properties are assumed to be TaskInfoAttributes
			TaskInfoAttributes tia = new TaskInfoAttributes();
			for (Enumeration eProps = props.propertyNames(); eProps
					.hasMoreElements();) {
				name = (String) eProps.nextElement();
				value = props.getProperty(name);
				tia.put(name, value);
			}

			//System.out.println("installTask (zip): username=" + username + ",
			// access_id=" + access_id + ", tia.owner=" + tia.get(USERID) + ",
			// tia.privacy=" + tia.get(PRIVACY));
			if (vProblems.size() == 0) {
				_cat.info("installing " + taskName + " into database");
				vProblems = GenePatternAnalysisTask.installTask(taskName,
						taskDescription, params, tia, username, access_id);
				// get the newly assigned LSID
				lsid = (String) tia.get(IGPConstants.LSID);

				// extract files from zip file
				String taskDir = GenePatternAnalysisTask
						.getTaskLibDir((String) tia.get(IGPConstants.LSID));
				File dir = new File(taskDir);

				// if there are any existing files from a previous installation
				// of this task,
				// clean them out so there is no interference
				File[] fileList = dir.listFiles();
				for (i = 0; i < fileList.length; i++) {
					fileList[i].delete();
				}

				String folder = null;
				for (Enumeration eEntries = zipFile.entries(); eEntries
						.hasMoreElements();) {
					zipEntry = (ZipEntry) eEntries.nextElement();
					if (zipEntry.getName().equals(MANIFEST_FILENAME)) {
						continue;
					}
					is = zipFile.getInputStream(zipEntry);
					name = zipEntry.getName();
					if (zipEntry.isDirectory() || name.indexOf("/") != -1
							|| name.indexOf("\\") != -1) {
						// TODO: mkdirs()
						_cat
								.warn("installTask: skipping hierarchically-entered name: "
										+ name);
						continue;
					}

					// copy attachments to the taskLib BEFORE installing the
					// task, so that there is no time window when
					// the task is installed in Omnigene's database but the
					// files aren't decoded and so the task can't yet
					// be properly invoked

					// TODO: allow names to have paths, so long as they are
					// below the current point and not above or a peer
					// strip absolute or ../relative path names from zip entry
					// name so that they dump into the tasklib directory only
					i = name.lastIndexOf("/");
					if (i != -1)
						name = name.substring(i + 1);
					i = name.lastIndexOf("\\");
					if (i != -1)
						name = name.substring(i + 1);

					try {
						// TODO: support directory structure within zip file
						outFile = new File(taskDir, name);
						if (outFile.exists()) {
							File oldVersion = new File(taskDir, name + ".old");
							_cat.warn("replacing " + name + " ("
									+ outFile.length() + " bytes) in "
									+ taskDir + ".  Renaming old one to "
									+ oldVersion.getName());
							oldVersion.delete(); // delete the previous .old
												 // file
							boolean renamed = rename(outFile, oldVersion, true);
							if (!renamed)
								_cat.error("failed to rename "
										+ outFile.getCanonicalPath() + " to "
										+ oldVersion.getCanonicalPath());
						}
						os = new FileOutputStream(outFile);
						fileLength = zipEntry.getSize();
						numRead = 0;
						byte[] buf = new byte[100000];
						while ((i = is.read(buf, 0, buf.length)) > 0) {
							os.write(buf, 0, i);
							numRead += i;
						}
						os.close();
						os = null;
						outFile.setLastModified(zipEntry.getTime());
						if (numRead != fileLength) {
							vProblems.add("only read " + numRead + " of "
									+ fileLength + " bytes in " + zipFilename
									+ "'s " + zipEntry.getName());
						}
					} catch (IOException ioe) {
						String msg = "error unzipping file " + name + " from "
								+ zipFilename + ": " + ioe.getMessage();
						vProblems.add(msg);
					}
					is.close();
					if (os != null) {
						os.close();
						os = null;
					}
				}
			}
		} catch (Exception e) {
			_cat.error(e);
			e.printStackTrace();
			vProblems.add(e.getMessage() + " while installing task");
		} finally {
			try {
				if (zipFile != null)
					zipFile.close();
			} catch (IOException ioe) {
			}
		}
		if ((vProblems != null) && (vProblems.size() > 0)) {
			for (Enumeration eProblems = vProblems.elements(); eProblems
					.hasMoreElements();) {
				_cat.error(eProblems.nextElement());
			}
			throw new TaskInstallationException(vProblems);
		}
		//_cat.debug("installTask: done.");
		return lsid;
	}

	public static String installNewTask(String zipFilename, String username,
			int access_id) throws TaskInstallationException {
		return installNewTask(zipFilename, username, access_id, true);
	}

	/**
	 * downloads a file from a URL and returns the path to the local file to the
	 * caller.
	 * 
	 * @param zipURL
	 *            String URL of file to download
	 * @return String filename of temporary downloaded file on server
	 * @author Jim Lerner
	 * @throws IOException
	 *             if any problems occured in accessing the remote file or
	 *             storing it locally
	 *  
	 */
	public static String downloadTask(String zipURL) throws IOException {
		File zipFile = File.createTempFile("gpz", ".zip");
		zipFile.deleteOnExit();
		FileOutputStream os = new FileOutputStream(zipFile);
		InputStream is = new URL(zipURL).openStream();
		byte[] buf = new byte[30000];
		int i;
		while ((i = is.read(buf, 0, buf.length)) > 0) {
			os.write(buf, 0, i);
		}
		is.close();
		os.close();
		return zipFile.getPath();
	}

	/**
	 * return a Vector of TaskInfos of the contents of zip-of-zips file
	 */
	public static Vector getZipOfZipsTaskInfos(File zipf) throws Exception {
		Vector vTaskInfos = new Vector();
		ZipFile zipFile = new ZipFile(zipf);
		InputStream is = null;
		for (Enumeration eEntries = zipFile.entries(); eEntries
				.hasMoreElements();) {
			ZipEntry zipEntry = (ZipEntry) eEntries.nextElement();
			if (!zipEntry.getName().endsWith(".zip")) {
				throw new Exception("not a GenePattern zip-of-zips file");
			}
			is = zipFile.getInputStream(zipEntry);
			// there is no way to create a ZipFile from an input stream, so
			// every file within the
			// stream must be extracted before it can be processed
			File outFile = new File(System.getProperty("java.io.tmpdir"),
					zipEntry.getName());
			outFile.deleteOnExit();
			OutputStream os = new FileOutputStream(outFile);
			long fileLength = zipEntry.getSize();
			long numRead = 0;
			byte[] buf = new byte[100000];
			int i;
			while ((i = is.read(buf, 0, buf.length)) > 0) {
				os.write(buf, 0, i);
				numRead += i;
			}
			os.close();
			os = null;
			if (numRead != fileLength) {
				throw new Exception("only read " + numRead + " of "
						+ fileLength + " bytes in " + zipf.getName() + "'s "
						+ zipEntry.getName());
			}
			is.close();
			Properties props = new Properties();
			ZipFile subZipFile = new ZipFile(outFile);
			ZipEntry manifestEntry = subZipFile
					.getEntry(IGPConstants.MANIFEST_FILENAME);
			props.load(subZipFile.getInputStream(manifestEntry));
			subZipFile.close();
			outFile.delete();

			TaskInfo ti = new TaskInfo();
			ti.setName((String) props.remove(NAME));
			ti.setDescription((String) props.remove(DESCRIPTION));

			// ParameterInfo entries consist of name/value/description triplets,
			// of which the value and description are optional
			// It is assumed that the names are p[1-n]_name, p[1-n]_value, and
			// p[1-n]_description
			// and that the numbering runs consecutively. When there is no
			// p[m]_name value, then there are m-1 ParameterInfos

			// count ParameterInfo entries
			int numParameterInfos = 0;
			String name;
			String value;
			String description;

			Vector vParams = new Vector();
			ParameterInfo pi = null;
			for (i = 1; i <= MAX_PARAMETERS; i++) {
				name = (String) props.remove("p" + i + "_name");
				if (name == null)
					continue;
				if (name == null || name.length() == 0)
					throw new Exception("missing parameter name for " + "p" + i
							+ "_name");
				value = (String) props.remove("p" + i + "_value");
				if (value == null)
					value = "";
				description = (String) props.remove("p" + i + "_description");
				if (description == null)
					description = "";
				pi = new ParameterInfo(name, value, description);
				HashMap attributes = new HashMap();
				for (int attribute = 0; attribute < PARAM_INFO_ATTRIBUTES.length; attribute++) {
					name = (String) PARAM_INFO_ATTRIBUTES[attribute][0];
					value = (String) props.remove("p" + i + "_" + name);
					if (value != null) {
						attributes.put(name, value);
					}
					if (name.equals(PARAM_INFO_TYPE[0]) && value != null
							&& value.equals(PARAM_INFO_TYPE_INPUT_FILE)) {
						attributes.put(ParameterInfo.MODE,
								ParameterInfo.INPUT_MODE);
						attributes.put(ParameterInfo.TYPE,
								ParameterInfo.FILE_TYPE);
					}
				}

				for (Enumeration p = props.propertyNames(); p.hasMoreElements();) {
					name = (String) p.nextElement();
					if (!name.startsWith("p" + i + "_"))
						continue;
					value = (String) props.remove(name);
					//_cat.debug("installTask: " + taskName + ": parameter " +
					// name + "=" + value);
					name = name.substring(name.indexOf("_") + 1);
					attributes.put(name, value);
				}

				if (attributes.size() > 0) {
					pi.setAttributes(attributes);
				}
				vParams.add(pi);
			}
			ParameterInfo[] params = new ParameterInfo[vParams.size()];
			ti.setParameterInfoArray((ParameterInfo[]) vParams
					.toArray(new ParameterInfo[0]));

			// all remaining properties are assumed to be TaskInfoAttributes
			TaskInfoAttributes tia = new TaskInfoAttributes();
			for (Enumeration eProps = props.propertyNames(); eProps
					.hasMoreElements();) {
				name = (String) eProps.nextElement();
				value = props.getProperty(name);
				tia.put(name, value);
			}
			ti.setTaskInfoAttributes(tia);
			vTaskInfos.add(ti);
		}
		return vTaskInfos;
	}

	// pipeline support:

	/**
	 * accepts a jobID and Process object, logging them in the
	 * htRunningPipelines Hashtable. When the pipeline terminates, they will be
	 * removed from the Hashtable by terminateJob.
	 * 
	 * @see #terminateJob(String, Hashtable)
	 * @see #terminatePipeline(String)
	 * @param jobID
	 *            job ID number
	 * @param p
	 *            Process object for running R pipeline
	 * @author Jim Lerner
	 */
	public static void startPipeline(String jobID, Process p) {
		htRunningPipelines.put(jobID, p);
	}

	/**
	 * Creates an Omnigene database entry in the analysis_job table. Unlike
	 * other entries, this one is not dispatchable to any known analysis task
	 * because it has a bogus taskID. Since it is a pipeline, it is actually
	 * being invoked by a separate process (not GenePatternAnalysisTask), but is
	 * using the rest of the infrastructure to get input files, store output
	 * files, and retrieve status and result files.
	 * 
	 * @see #startPipeline(String, Process)
	 * @see #terminatePipeline(String)
	 * @author Jim Lerner
	 * @param userID
	 *            user who owns this pipeline data instance
	 * @param parameterInfo
	 *            ParameterInfo array containing pipeline data file output
	 *            entries
	 * @throws OmnigeneException
	 *             if thrown by Omnigene
	 * @throws RemoteException
	 *             if thrown by Omnigene
	 */
	public static JobInfo createPipelineJob(String userID,
			String parameter_info, String pipelineName, String lsid)
			throws OmnigeneException, RemoteException {
		JobInfo jobInfo = getDS()
				.createTemporaryPipeline(
						userID, parameter_info, pipelineName, lsid);
		return jobInfo;
	}
	
	public static JobInfo createVisualizerJob(String userID,
			String parameter_info, String visualizerName, String lsid)
			throws OmnigeneException, RemoteException {
		JobInfo jobInfo = getDS()
				.createVisualizerJobRecord(
						userID, parameter_info, visualizerName, lsid);
		return jobInfo;
	}
	/**
	 * Changes the JobStatus of a pipeline job, and appends zero or more output
	 * parameters (output filenames) to the JobInfo ParameterInfo array for
	 * eventual return to the invoker. This routine is actually invoked from
	 * updatePipelineStatus.jsp. The jobStatus constants are those defined in
	 * edu.mit.wi.omnigene.framework.analysis.JobStatus
	 * 
	 * @param jobNumber
	 *            jobID of the pipeline whose status is to be updated
	 * @param jobStatus
	 *            new status (eg. JobStatus.PROCESSING, JobStatus.DONE, etc.)
	 * @param additionalParams
	 *            array of ParameterInfo objects which represent additional
	 *            output parameters from the pipeline job
	 * @throws OmnigeneException
	 *             if thrown by Omnigene
	 * @throws RemoteException
	 *             if thrown by Omnigene
	 * @see org.genepattern.webservice.JobStatus
	 * @author Jim Lerner
	 */
	public static void updatePipelineStatus(int jobNumber, int jobStatus,
			ParameterInfo[] additionalParams) throws OmnigeneException,
			RemoteException {
		AnalysisJobDataSource ds = getDS();
		JobInfo jobInfo = ds.getJobInfo(jobNumber);
		if (additionalParams != null) {
			for (int i = 0; i < additionalParams.length; i++) {
				jobInfo.addParameterInfo(additionalParams[i]);
			}
		}

		if (jobStatus < JobStatus.JOB_NOT_STARTED)
			jobStatus = ((Integer) JobStatus.STATUS_MAP
					.get(jobInfo.getStatus())).intValue();
		ds.updateJob(jobNumber, jobInfo.getParameterInfo(), jobStatus);
	}

	/**
	 * Changes the JobStatus of a pipeline job, and appends zero or one output
	 * parameters (output filenames) to the jobs's JobInfo ParameterInfo array
	 * for eventual return to the invoker. This routine is actually invoked from
	 * updatePipelineStatus.jsp. The jobStatus constants are those defined in
	 * edu.mit.wi.omnigene.framework.analysis.JobStatus
	 * 
	 * @param jobNumber
	 *            jobID of the pipeline whose status is to be updated
	 * @param jobStatus
	 *            new status (eg. JobStatus.PROCESSING, JobStatus.DONE, etc.)
	 * @param name
	 *            optional [short] name of filename parameter, ie. without
	 *            directory information
	 * @param additionalFilename
	 *            optional filename of output file for this job
	 * @throws OmnigeneException
	 *             if thrown by Omnigene
	 * @throws RemoteException
	 *             if thrown by Omnigene
	 * @see org.genepattern.webservice.JobStatus
	 * @author Jim Lerner
	 */
	public static void updatePipelineStatus(int jobNumber, int jobStatus,
			String name, String additionalFilename) throws OmnigeneException,
			RemoteException {
		if (name != null && additionalFilename != null) {
			ParameterInfo additionalParam = new ParameterInfo();
			additionalParam.setAsOutputFile();
			additionalParam.setName(name);
			additionalParam.setValue(additionalFilename);
			updatePipelineStatus(jobNumber, jobStatus,
					new ParameterInfo[] { additionalParam });
		} else {
			updatePipelineStatus(jobNumber, jobStatus, null);
		}
	}

	/**
	 * accepts a jobID and attempts to terminate the running pipeline process.
	 * Pipelines are notable only in that they are sometimes run not as Omnigene
	 * tasks, but as R code that runs through each task serially. The running R
	 * process itself is the "pipeline", although it isn't strictly speaking a
	 * task. When the pipeline is run as a task, it is not treated as a pipeline
	 * in this code. The pipeline behavior only occurs when run via
	 * runPipeline.jsp, allowing intermediate results of the task to appear,
	 * which would not happen if it were run as a task (all or none for output).
	 * 
	 * @param jobID
	 *            JobInfo jobNumber
	 * @return Process of the pipeline if running, else null
	 * @author Jim Lerner
	 */
	public static Process terminatePipeline(String jobID) {
		Process p = (Process) htRunningPipelines.remove(jobID);
		if (p != null) {
			p.destroy();
		} else {
			p = (Process) htRunningJobs.get(jobID);
			if (p != null) {
				p.destroy();
			}
		}
		return p;
	}

	public static void terminateAll(String message) {
		_cat.warn(message);
		String jobID;
		Enumeration eJobs;
		int numTerminated = 0;

		for (eJobs = htRunningPipelines.keys(); eJobs.hasMoreElements();) {
			jobID = (String) eJobs.nextElement();
			_cat.warn("Terminating job " + jobID);
			Process p = terminatePipeline(jobID);
			if (p != null) {
				try {
					updatePipelineStatus(Integer.parseInt(jobID),
							JobStatus.JOB_ERROR, null);
				} catch (Exception e) { /* ignore */
				}
			}
			numTerminated++;
		}
		for (eJobs = htRunningJobs.keys(); eJobs.hasMoreElements();) {
			jobID = (String) eJobs.nextElement();
			_cat.warn("Terminating job " + jobID);
			terminateJob(jobID, htRunningJobs);
			numTerminated++;
		}
		if (numTerminated > 0) {
			// let the processes terminate, clean up, and record their outputs
			// in the database
			Thread.yield();
		}
	}

	// utility methods:

	/**
	 * Here's a tricky/nasty way of getting the environment variables despite
	 * System.getenv() being deprecated. TODO: find a better (no-deprecated)
	 * method of retrieving environment variables in platform-independent
	 * fashion. The environment is used <b>almost </b> as is, except that the
	 * directory of the task's files is added to the path to make execution work
	 * transparently. This is equivalent to the <libdir>substitution variable.
	 * Some of the applications will be expecting to find their support files on
	 * the path or in the same directory, and this manipulation makes it
	 * transparent to them.
	 * 
	 * <p>
	 * Implementation: spawn a process that performs either a "sh -c set" (on
	 * Unix) or "cmd /c set" on Windows.
	 * 
	 * @author Jim Lerner
	 * @return Hashtable of environment variable name/value pairs
	 *  
	 */
	public static Hashtable getEnv() {
		Hashtable envVariables = new Hashtable();
		int i;
		String key;
		String value;
		boolean isWindows = System.getProperty("os.name").startsWith("Windows");

		try {
			Process getenv = Runtime.getRuntime().exec(
					isWindows ? "cmd /c set" : "sh -c set");
			BufferedReader in = new BufferedReader(new InputStreamReader(getenv
					.getInputStream()));
			String line;
			while ((line = in.readLine()) != null) {
				i = line.indexOf("=");
				if (i == -1) {
					continue;
				}
				key = line.substring(0, i);
				value = line.substring(i + 1);
				envVariables.put(key, value);
			}
			in.close();
		} catch (IOException ioe) {
			_cat.error(ioe);
		}
		return envVariables;
	}

	/**
	 * Creates a new Thread which blocks on reads to an InputStream, appends
	 * their output to the StringBuffer. The thread terminates upon EOF from the
	 * InputStream.
	 * 
	 * @param is
	 *            InputStream to read from
	 * @param sbOut
	 *            StringBuffer to copy InputStream contents to
	 * @param sbErr
	 *            StringBuffer to report IOExceptions to
	 * @throws IOException
	 *             if unable to open InputStream
	 * @author Jim Lerner
	 */
	protected Thread streamToStringBuffer(final InputStream is,
			final StringBuffer sbOut, final StringBuffer sbErr)
			throws IOException {
		// create thread to read from the a process' output or error stream
		return new Thread(new Runnable() {
			public void run() {
				BufferedReader in = new BufferedReader(
						new InputStreamReader(is));
				String line;
				// copy stdout to StringBuffer (eventually to stream) for client

				try {
					while ((line = in.readLine()) != null) {
						//		                _cat.debug(line);
						sbOut.append(line);
						sbOut.append("\n");
					}
				} catch (IOException ioe) {
					sbErr.append(ioe + " while reading from process stream");
				}
			}
		});
	}

	/**
	 * writes a string to a file
	 * 
	 * @param dirName
	 *            directory in which to create the file
	 * @param filename
	 *            filename within the directory
	 * @param outputString
	 *            String to write to file
	 * @return File that was written
	 * @author Jim Lerner
	 *  
	 */
	protected File writeStringToFile(String dirName, String filename,
			String outputString) {
		File outFile = null;
		try {
			outFile = new File(dirName, filename);
			FileWriter fw = new FileWriter(outFile);
			fw.write(outputString != null ? outputString : "");
			fw.close();
		} catch (NullPointerException npe) {
			_cat.error(getClass().getName() + ": writeStringToFile(" + dirName
					+ ", " + filename + ", " + outputString + "): "
					+ npe.getMessage());
			npe.printStackTrace();
		} catch (IOException ioe) {
			_cat.error(getClass().getName() + ": writeStringToFile(" + dirName
					+ ", " + filename + ", " + outputString + "): "
					+ ioe.getMessage());
			ioe.printStackTrace();
		} finally {
			if (true)
				return outFile;
		}
		return outFile;
	}

	/**
	 * Utility function to convert a HashTable to a String[]. Used because the
	 * Runtime.exec() method requires a String[] of environment variables, which
	 * stem from a Hashtable.
	 * 
	 * @param htEntries
	 *            input Hashtable
	 * @return String[] array of String of name=value elements from input
	 *         Hashtable
	 * @author Jim Lerner
	 */
	public String[] hashTableToStringArray(Hashtable htEntries) {
		String[] envp = new String[htEntries.size()];
		int i = 0;
		String key = null;
		for (Enumeration eVariables = htEntries.keys(); eVariables
				.hasMoreElements();) {
			key = (String) eVariables.nextElement();
			envp[i++] = key + "=" + (String) htEntries.get(key);
		}
		return envp;
	}

	/**
	 * replace all instances of "find" in "original" string and substitute
	 * "replace" for them
	 * 
	 * @param original
	 *            String before replacements are made
	 * @param find
	 *            String to search for
	 * @param replace
	 *            String to replace the sought string with
	 * @return String String with all replacements made
	 * @author Jim Lerner
	 */
	public static final String replace(String original, String find,
			String replace) {
		StringBuffer res = new StringBuffer();
		int idx = 0;
		int i = 0;
		while (true) {
			i = idx;
			idx = original.indexOf(find, idx);
			if (idx == -1) {
				res.append(original.substring(i));
				break;
			} else {
				res.append(original.substring(i, idx));
				res.append(replace);
				idx += find.length();
			}
		}
		return res.toString();
	}

	/**
	 * renames a file, even across filesystems. If the underlying Java rename()
	 * fails because the source and destination are not on the same filesystem,
	 * this method performs a copy instead.
	 * 
	 * @param from
	 *            File which is to be renamed
	 * @param to
	 *            File which will be the new name
	 * @param deleteIfCopied
	 *            boolean indicating whether to delete the source file if it was
	 *            copied to a different filesystem
	 * @return true if the rename was accomplished
	 * @author Jim Lerner
	 *  
	 */

	public static boolean rename(File from, File to, boolean deleteIfCopied) {
		//try { _cat.debug("renaming " + from.getCanonicalPath() + " to " +
		// to.getCanonicalPath()); } catch (IOException ioe) { }
		if (!from.exists()) {
			_cat.error(from.toString() + " doesn't exist for rename");
			return false;
		}
		if (!to.getParentFile().exists()) {
			_cat.info(to.getParent() + " directory does not exist");
			to.getParentFile().mkdirs();
		}
		if (from.equals(to))
			return true;
		if (to.exists()) {
			_cat.info(to.toString() + " already exists for rename");
			if (!from.equals(to))
				to.delete();
		}

		for (int retries = 1; retries < 20; retries++) {
			if (from.equals(to) || from.renameTo(to)) {
				return true;
			}
			_cat
					.info("GenePatternAnalysisTask.rename: sleeping before retrying rename from "
							+ from.toString() + " to " + to.toString());
			// sleep and retry in case Indexer is busy with this file right now
			try {
				Thread.sleep(100 * retries);
			} catch (InterruptedException ie) {
			}
		}

		try {
			_cat.info("Have to copy, renameTo failed: "
					+ from.getCanonicalPath() + " -> " + to.getCanonicalPath());
		} catch (IOException ioe) {
		}
		// if can't rename, then copy to destination and delete original
		if (copyFile(from, to)) {
			if (deleteIfCopied) {
				if (!from.delete()) {
					_cat.info("Unable to delete source of copy/rename: "
							+ from.toString());
				}
			}
			return true;
		} else {
			return false;
		}
	}

	public static boolean copyFile(File from, File to) {
		FileInputStream is = null;
		FileOutputStream os = null;
		try {
			if (to.exists())
				to.delete();
			is = new FileInputStream(from);
			os = new FileOutputStream(to);
			byte[] buf = new byte[100000];
			int i;
			while ((i = is.read(buf, 0, buf.length)) > 0) {
				os.write(buf, 0, i);
			}
			is.close();
			is = null;
			os.close();
			os = null;
			to.setLastModified(from.lastModified());
			return true;
		} catch (Exception e) {
			_cat.error("Error copying " + from.getAbsolutePath() + " to "
					+ to.getAbsolutePath() + ": " + e.getMessage());
			try {
				if (is != null)
					is.close();
				if (os != null)
					os.close();
			} catch (IOException ioe) {
			}
			return false;
		}
	}

	/**
	 * escapes characters that have an HTML entity representation. It uses a
	 * quick string -> array mapping to avoid creating thousands of temporary
	 * objects.
	 * 
	 * @param nonHTMLsrc
	 *            String containing the text to make HTML-safe
	 * @return String containing new copy of string with ENTITIES escaped
	 */
	public static final String htmlEncode(String nonHTMLsrc) {
		if (nonHTMLsrc == null)
			return "";
		StringBuffer res = new StringBuffer();
		int l = nonHTMLsrc.length();
		int idx;
		char c;
		for (int i = 0; i < l; i++) {
			c = nonHTMLsrc.charAt(i);
			idx = entityMap.indexOf(c);
			if (idx == -1) {
				res.append(c);
			} else {
				res.append(quickEntities[idx]);
			}
		}
		return res.toString();
	}

	/**
	 * static lookup table for htmlEncode method
	 * 
	 * @see #htmlEncode(String)
	 *  
	 */
	private static final String[][] ENTITIES = {
	/* We probably don't want to filter regular ASCII chars so we leave them out */
	{ "&", "amp" }, { "<", "lt" }, { ">", "gt" }, { "\"", "quot" },

	{ "\u0083", "#131" }, { "\u0084", "#132" }, { "\u0085", "#133" },
			{ "\u0086", "#134" }, { "\u0087", "#135" }, { "\u0089", "#137" },
			{ "\u008A", "#138" }, { "\u008B", "#139" }, { "\u008C", "#140" },
			{ "\u0091", "#145" }, { "\u0092", "#146" }, { "\u0093", "#147" },
			{ "\u0094", "#148" }, { "\u0095", "#149" }, { "\u0096", "#150" },
			{ "\u0097", "#151" }, { "\u0099", "#153" }, { "\u009A", "#154" },
			{ "\u009B", "#155" }, { "\u009C", "#156" }, { "\u009F", "#159" },

			{ "\u00A0", "nbsp" }, { "\u00A1", "iexcl" }, { "\u00A2", "cent" },
			{ "\u00A3", "pound" }, { "\u00A4", "curren" }, { "\u00A5", "yen" },
			{ "\u00A6", "brvbar" }, { "\u00A7", "sect" }, { "\u00A8", "uml" },
			{ "\u00A9", "copy" }, { "\u00AA", "ordf" }, { "\u00AB", "laquo" },
			{ "\u00AC", "not" }, { "\u00AD", "shy" }, { "\u00AE", "reg" },
			{ "\u00AF", "macr" }, { "\u00B0", "deg" }, { "\u00B1", "plusmn" },
			{ "\u00B2", "sup2" }, { "\u00B3", "sup3" },

			{ "\u00B4", "acute" }, { "\u00B5", "micro" }, { "\u00B6", "para" },
			{ "\u00B7", "middot" }, { "\u00B8", "cedil" },
			{ "\u00B9", "sup1" }, { "\u00BA", "ordm" }, { "\u00BB", "raquo" },
			{ "\u00BC", "frac14" }, { "\u00BD", "frac12" },
			{ "\u00BE", "frac34" }, { "\u00BF", "iquest" },

			{ "\u00C0", "Agrave" }, { "\u00C1", "Aacute" },
			{ "\u00C2", "Acirc" }, { "\u00C3", "Atilde" },
			{ "\u00C4", "Auml" }, { "\u00C5", "Aring" }, { "\u00C6", "AElig" },
			{ "\u00C7", "Ccedil" }, { "\u00C8", "Egrave" },
			{ "\u00C9", "Eacute" }, { "\u00CA", "Ecirc" },
			{ "\u00CB", "Euml" }, { "\u00CC", "Igrave" },
			{ "\u00CD", "Iacute" }, { "\u00CE", "Icirc" },
			{ "\u00CF", "Iuml" },

			{ "\u00D0", "ETH" }, { "\u00D1", "Ntilde" },
			{ "\u00D2", "Ograve" }, { "\u00D3", "Oacute" },
			{ "\u00D4", "Ocirc" }, { "\u00D5", "Otilde" },
			{ "\u00D6", "Ouml" }, { "\u00D7", "times" },
			{ "\u00D8", "Oslash" }, { "\u00D9", "Ugrave" },
			{ "\u00DA", "Uacute" }, { "\u00DB", "Ucirc" },
			{ "\u00DC", "Uuml" }, { "\u00DD", "Yacute" },
			{ "\u00DE", "THORN" }, { "\u00DF", "szlig" },

			{ "\u00E0", "agrave" }, { "\u00E1", "aacute" },
			{ "\u00E2", "acirc" }, { "\u00E3", "atilde" },
			{ "\u00E4", "auml" }, { "\u00E5", "aring" }, { "\u00E6", "aelig" },
			{ "\u00E7", "ccedil" }, { "\u00E8", "egrave" },
			{ "\u00E9", "eacute" }, { "\u00EA", "ecirc" },
			{ "\u00EB", "euml" }, { "\u00EC", "igrave" },
			{ "\u00ED", "iacute" }, { "\u00EE", "icirc" },
			{ "\u00EF", "iuml" },

			{ "\u00F0", "eth" }, { "\u00F1", "ntilde" },
			{ "\u00F2", "ograve" }, { "\u00F3", "oacute" },
			{ "\u00F4", "ocirc" }, { "\u00F5", "otilde" },
			{ "\u00F6", "ouml" }, { "\u00F7", "divid" },
			{ "\u00F8", "oslash" }, { "\u00F9", "ugrave" },
			{ "\u00FA", "uacute" }, { "\u00FB", "ucirc" },
			{ "\u00FC", "uuml" }, { "\u00FD", "yacute" },
			{ "\u00FE", "thorn" }, { "\u00FF", "yuml" }, { "\u0080", "euro" } };

	private static String entityMap;

	private static String[] quickEntities;

	static {
		// Initialize some local mappings to speed it all up
		int l = ENTITIES.length;
		StringBuffer temp = new StringBuffer();

		quickEntities = new String[l];
		for (int i = 0; i < l; i++) {
			temp.append(ENTITIES[i][0]);
			quickEntities[i] = "&" + ENTITIES[i][1] + ";";
		}
		entityMap = temp.toString();
	}

	public static void main(String args[]) {
		try {

			if (args.length == 2 && args[0].equals("deleteTask")) {
				String lsid = args[1];
				GenePatternAnalysisTask.deleteTask(lsid);
			} else if (args.length == 0) {
				GenePatternAnalysisTask.test();
				GenePatternAnalysisTask.installNewTask("c:/temp/echo.zip",
						"jlerner@broad.mit.edu", 1);
			} else {
				System.err
						.println("GenePatternAnalysisTask: Don't know what input arguments mean");
			}
		} catch (Exception e) {
			_cat.error(e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Test method for the GenePatternAnalysisTask class. Currently tests
	 * installation of several tasks.
	 * 
	 * @throws OmnigeneException
	 * @throws RemoteException
	 * @author Jim Lerner
	 *  
	 */
	private static void test() throws OmnigeneException, RemoteException {
		/*
		 * select 'new TaskInfo("' || task_name || '","' || description || '","' ||
		 * classname || '",\n"' || parameter_info || '",\nnew
		 * TaskInfoAttributes("' || commandline || '",\n"' ||
		 * '",null,null,null,null,null,"Java"))' from task_master;
		 */

		Vector vProblems;
		Enumeration eProblems;
		TaskInfoAttributes tia = new TaskInfoAttributes();

		tia.clear();
		tia.put(COMMAND_LINE, "cmd /c copy <input_filename> <output_pattern>");
		tia.put(OS, "Windows NT");
		vProblems = installTask(
				"echo",
				"echo input",
				new ParameterInfo[] { /* no input parameters */}, tia,
				"jlerner@broad.mit.edu", 1);
		if (vProblems != null) {
			for (eProblems = vProblems.elements(); eProblems.hasMoreElements();) {
				_cat.error(eProblems.nextElement());
			}
		}

		tia.clear();
		tia
				.put(
						COMMAND_LINE,
						"<java> -cp <libdir>TransposeFilter.jar edu.mit.wi.gp.executers.RunTransposePreprocess <input_filename>");
		tia.put(LANGUAGE, "Java");
		tia.put(TASK_TYPE, "filter");
		tia.put(JVM_LEVEL, "1.3");
		vProblems = installTask(
				"Transpose",
				"transpose a res or gct file",
				new ParameterInfo[] { /* no input parameters */}, tia,
				"jlerner@broad.mit.edu", 1);
		if (vProblems != null) {
			for (eProblems = vProblems.elements(); eProblems.hasMoreElements();) {
				_cat.error(eProblems.nextElement());
			}
		}

		tia.clear();
		tia
				.put(
						COMMAND_LINE,
						"<java> -cp <libdir>ExcldRowsFilter.jar edu.mit.wi.gp.executers.RunExcludeRowsPreprocess <input_filename> <low> <high> <min_fold> <min_difference>");
		tia.put(LANGUAGE, "Java");
		tia.put(TASK_TYPE, "filter");
		tia.put(JVM_LEVEL, "1.3");
		vProblems = installTask(
				"ExcludeRows",
				"exclude rows from a res or gct file",
				new ParameterInfo[] {
						new ParameterInfo("low", null, "low"),
						new ParameterInfo("high", null, "high"),
						new ParameterInfo("min_fold", null, "minimum fold"),
						new ParameterInfo("min_difference", null,
								"minimum difference") }, tia,
				"jlerner@broad.mit.edu", 1);
		if (vProblems != null) {
			for (eProblems = vProblems.elements(); eProblems.hasMoreElements();) {
				_cat.error(eProblems.nextElement());
			}
		}

		tia.clear();
		tia
				.put(
						COMMAND_LINE,
						"<java> -cp <libdir>ThresholdFilter.jar edu.mit.wi.gp.executers.RunThresholdPreprocess <input_filename> <min> <max>");
		tia.put(LANGUAGE, "Java");
		tia.put(TASK_TYPE, "filter");
		tia.put(JVM_LEVEL, "1.3");
		vProblems = installTask(
				"Threshold",
				"threshold a res or gct file",
				new ParameterInfo[] {
						new ParameterInfo("min", null, "minimum"),
						new ParameterInfo("max", null, "maximum") }, tia,
				"jlerner@broad.mit.edu", 1);
		if (vProblems != null) {
			for (eProblems = vProblems.elements(); eProblems.hasMoreElements();) {
				_cat.error(eProblems.nextElement());
			}
		}

		tia.clear();
		tia
				.put(
						COMMAND_LINE,
						"<java> -cp <libdir>gp.jar;<libdir>trove.jar;<libdir>openide.jar edu.mit.wi.gp.ui.pinkogram.BpogPanel <input_path> <input_basename>");
		tia.put(LANGUAGE, "Java");
		tia.put(TASK_TYPE, "visualizer");
		tia.put(JVM_LEVEL, "1.3");
		vProblems = installTask(
				"BluePinkOGram",
				"display a BPOG of a RES or GCT file",
				new ParameterInfo[] { /* no input parameters */}, tia,
				"jlerner@broad.mit.edu", 1);
		if (vProblems != null) {
			for (eProblems = vProblems.elements(); eProblems.hasMoreElements();) {
				_cat.error(eProblems.nextElement());
			}
		}

	}

	// really boring stuff: constructors and concrete methods overriding
	// abstract AnalysisTask methods:

	// series of constructors which add default values for important input
	// parameters

	public GenePatternAnalysisTask() {
		if (System.getProperty("GenePatternVersion") == null) {
			// System properties are already loaded by StartupServlet
			File propFile = new File(System
					.getProperty("genepattern.properties"),
					"genepattern.properties");
			FileInputStream fis = null;
			try {
				fis = new FileInputStream(propFile);
				System.getProperties().load(fis);
			} catch (IOException ioe) {
				_cat.error(propFile.getName() + " cannot be loaded.  "
						+ ioe.getMessage());
			} finally {
				try {
					if (fis != null)
						fis.close();
				} catch (IOException ioe) {
				}
			}
		}
		/*
		 * System.out.println("GPAT.init:"); TreeMap tmProps = new
		 * TreeMap(System.getProperties()); for (Iterator iProps =
		 * tmProps.keySet().iterator(); iProps.hasNext(); ) { String propName =
		 * (String)iProps.next(); String propValue =
		 * (String)tmProps.get(propName); System.out.println(propName + "=" +
		 * propValue); }
		 */

		String pathNames[] = new String[] { PERL, JAVA, R, TOMCAT };
		String oldName;
		String newName;
		for (int i = 0; i < pathNames.length; i++) {
			oldName = System.getProperty(pathNames[i]);
			if (oldName == null)
				continue;
			try {
				newName = new File(oldName).getCanonicalPath();
				System.setProperty(pathNames[i], newName);
			} catch (IOException ioe) {
				_cat.error("GenePattern init: " + ioe
						+ " while getting canonical path for " + oldName);
			}
		}
		// dump System properties, sorted and untruncated
		TreeMap props = new TreeMap(String.CASE_INSENSITIVE_ORDER);
		props.putAll(System.getProperties());
		for (Iterator itProps = props.keySet().iterator(); itProps.hasNext();) {
			String name = (String) itProps.next();
			String value = (String) props.get(name);
			//		_cat.info(name + "=" + value);
		}
		if (!bAnnounced) {
			_cat.info("GenePattern version " + props.get("GenePatternVersion")
					+ " build " + props.get("tag") + " loaded");
			bAnnounced = true;
		}
	}

	public static void announceReady() {
		GenePatternAnalysisTask gpat = new GenePatternAnalysisTask();
		_cat.info("GenePattern server version "
				+ System.getProperty("GenePatternVersion") + " is ready.");
	}

	/**
	 * loads the request into queue
	 * 
	 * @return Vector of JobInfo
	 * @author Raj Kuttan
	 */
	public Vector getWaitingJobs() {
		Vector jobVector = null;
		try {
			jobVector = getDS()
					.getWaitingJob(NUM_THREADS);
		} catch (Exception e) {
			_cat.error(getClass().getName() + ": getWaitingJobs "
					+ e.getMessage());
			jobVector = new Vector();
		}
		return jobVector;
	}

	/** return boolean indicating whether a filename represents a code file */
	public static boolean isCodeFile(String filename) {
		return hasEnding(filename, "code");
	}

	/**
	 * return boolean indicating whether a filename represents a documentation
	 * file
	 */
	public static boolean isDocFile(String filename) {
		return hasEnding(filename, "doc");
	}

	/** return boolean indicating whether a filename represents a binary file */
	public static boolean isBinaryFile(String filename) {
		return hasEnding(filename, "binary");
	}

	/**
	 * return boolean indicating whether a filename represents a file type (as
	 * found in System.getProperties(files.{code,doc,binary}))
	 */
	protected static boolean hasEnding(String filename, String fileType) {
		String endings = System.getProperty("files." + fileType, "");
		Vector vEndings = csvToVector(endings.toLowerCase());
		boolean ret = false;
		filename = new File(filename).getName().toLowerCase();
		int lastDot = filename.lastIndexOf(".");
		if (lastDot == -1) {
			ret = vEndings.contains("");
		} else {
			ret = vEndings.contains(filename.substring(lastDot + 1));
		}
		return ret;
	}

	/** convert a CSV list into a Vector */
	protected static Vector csvToVector(String csv) {
		StringTokenizer stEntries = new StringTokenizer(csv, ",; ");
		Vector vEntries = new Vector();
		while (stEntries.hasMoreTokens()) {
			vEntries.add(stEntries.nextToken());
		}
		return vEntries;
	}

	// implements FilenameFilter, but static
	public static boolean accept(File dir, String name) {
		return isDocFile(name);
	}

	public static Properties loadGenePatternProperties(
			ServletContext application, String filename) throws IOException {
		return appendProperties(application, filename, new Properties());
	}

	public static Properties appendProperties(ServletContext application,
			String filename, Properties props) throws IOException {
		// append build.properties to the genepattern properties
		return appendProperties((String) application
				.getAttribute("genepattern.properties"), filename, props);
	}

	public static Properties appendProperties(String propsDir, String filename,
			Properties props) throws IOException {
		// append build.properties to the genepattern properties
		File propFile = new File(propsDir + File.separatorChar + filename);
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(propFile);
			props.load(fis);
		} catch (IOException ioe) {
			throw new IOException(propFile.getAbsolutePath()
					+ " cannot be loaded, reason: " + ioe.getMessage());
		} finally {
			try {
				if (fis != null)
					fis.close();
				fis = null;
			} catch (IOException ioe) {
			}
		}
		return props;
	}

	/* TODO: put all of this stuff in database and look it up when requested */

	// LHS is what is presented to user, RHS is what java System.getProperty()
	// returns
	public static String[] getCPUTypes() {
		return new String[] { ANY, "Alpha=alpha", "Intel=x86", "PowerPC=ppc",
				"Sparc=sparc" };
	}

	// LHS=show to user, RHS=what System.getProperty("os.name") returns
	public static String[] getOSTypes() {
		return new String[] { ANY, "Linux=linux", "MacOS=Mac OS X",
				"Solaris=solaris", "Tru64=OSF1", "Windows=Windows" };
	}

	public static String[] getTaskTypes() {
		return new String[] { "", "Clustering", "Gene List Selection",
				"Image Creator", "Method", IGPConstants.TASK_TYPE_PIPELINE,
				"Prediction", "Preprocess & Utilities", "Projection",
				"Statistical Methods", "Sequence Analysis",
				TASK_TYPE_VISUALIZER };
	}

	public static String[] getLanguages() {
		return new String[] { ANY, "C", "C++", "Java", "Perl", "Python", "R" };
	}

} // end GenePatternAnalysisTask class

/**
 * The GenePatternTaskDBLoader dynamically creates Omnigene TASK_MASTER table
 * entries for new or modified GenePatternAnalysisTasks. Each task has a name,
 * description, array of
 * ParameterInfo declarations, and an XML-encoded form of TaskInfoAttributes.
 * These are all persisted in the Omnigene database and recalled when a task is
 * going to be invoked.
 * 
 * @author Jim Lerner
 * @see org.genepattern.server.dbloader.DBLoader;
 *  
 */

class GenePatternTaskDBLoader extends DBLoader {
	public void setup() {
	}

	public GenePatternTaskDBLoader(String name, String description,
			ParameterInfo[] params,
			String taskInfoAttributes, String username, int access_id) {
		this._name = name;
		this._taskDescription = description;
		this._params = params;
		this._taskInfoAttributes = taskInfoAttributes;
		this.access_id = access_id;
		this.user_id = username;
	}

	public void updateTaskInfoAttributes(String taskInfoAttributes) {
		this._taskInfoAttributes = taskInfoAttributes;

	}

}