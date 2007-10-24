/*
 The Broad Institute
 SOFTWARE COPYRIGHT NOTICE AGREEMENT
 This software and its documentation are copyright (2003-2008) by the
 Broad Institute/Massachusetts Institute of Technology. All rights are
 reserved.
 
 This software is supplied without any warranty or guaranteed support
 whatsoever. Neither the Broad Institute nor MIT can be responsible for its
 use, misuse, or functionality.
 */

/**
 *
 */
package org.genepattern.server.webapp.jsf;

import static org.genepattern.server.webapp.jsf.UIBeanHelper.getRequest;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.Vector;

import javax.faces.event.ActionEvent;
import javax.faces.model.SelectItem;

import org.apache.log4j.Logger;
import org.genepattern.server.util.PropertiesManager;
import org.genepattern.server.webapp.StartupServlet;

public class ServerSettingsBean {

    private static Logger log = Logger.getLogger("ServerSettingsBean.class");

    private Map<String, String[]> modes;

    private String[] clientModes = new String[] { "Local", "Any", "Specified" };

    private String currentClientMode = clientModes[0]; // default

    private String specifiedClientMode;

    private String currentMode; // Default

    private Properties settings;

    private List<KeyValuePair> customSettings;

    private Properties defaultSettings;

    private String newCSKey = "";

    private String newCSValue = "";

    private Calendar cal = Calendar.getInstance();

    private final String gpLogPath = "GpLogPath";

    private final String wsLogPath = "WsLogPath";

    /**
     * 
     */
    public ServerSettingsBean() {
	if (!AuthorizationHelper.adminServer()) {
	    throw new SecurityException("You don't have the required permissions to administer the server.");
	}

	if (modes == null) {
	    modes = new TreeMap<String, String[]>();
	    modes.put("Access", new String[] { "gp.allowed.clients" });
	    modes.put("Command Line Prefix", new String[] { "gp.allowed.clients" });
	    modes.put("File Purge", new String[] { "purgeJobsAfter", "purgeTime" });
	    modes.put("Java Flag", new String[] { "java_flags", "visualizer_java_flags", "r_flags" });
	    modes.put("Gene Pattern Log", null);
	    modes.put("Web Server Log", null);
	    modes.put("Repositories", new String[] { "ModuleRepositoryURL", "ModuleRepositoryURLs",
		    "SuiteRepositoryURL", "SuiteRepositoryURLs" });
	    modes.put("Proxy", new String[] { "http.proxyHost", "http.proxyPort", "http.proxyUser",
		    "http.proxyPassword", "ftp.proxyHost", "ftp.proxyPort", "ftp.proxyUser", "ftp.proxyPassword" });
	    modes.put("Database", new String[] { "database.vendor", "HSQL_port", "HSQL.class", "HSQL.args",
		    "HSQL.schema", "hibernate.connection.driver_class", "hibernate.connection.shutdown",
		    "hibernate.connection.url", "hibernate.connection.username", "hibernate.connection.password",
		    "hibernate.dialect", "hibernate.default_schema", "hibernate.connection.SetBigStringTryClob" });
	    // modes.put("LSID", new String[] { "lsid.authority", "lsid.show"
	    // }); // remove show LSID for 3.1 per bug 1654
	    modes.put("Programming Languages", new String[] { "perl", "java", "R" });

	    modes.put("Advanced", new String[] { "DefaultPatchRepositoryURL", "DefaultPatchURL", "patchQualifiers",
		    "patches", "ant", "resources", "index", "tasklib", "jobs", "tomcatCommonLib", "webappDir",
		    "log4j.appender.R.File", "pipeline.cp", "pipeline.main", "pipeline.vmargs", "pipeline.decorator",
		    "installedPatchLSIDs", "JavaGEInstallerURL", "num.threads" });
	    modes.put("Custom", null);
	    modes.put("Shut Down Server", null);
	}
	currentMode = (String) modes.keySet().toArray()[0];
	if (settings == null) {
	    try {
		settings = PropertiesManager.getGenePatternProperties();
	    } catch (IOException ioe) {
		log.error(ioe);
	    }
	}
	if (customSettings == null) {
	    try {
		Properties tmp = PropertiesManager.getCustomProperties();
		customSettings = new ArrayList<KeyValuePair>();
		for (Map.Entry entry : tmp.entrySet()) {
		    customSettings.add(new KeyValuePair((String) entry.getKey(), (String) entry.getValue()));
		}

	    } catch (IOException ioe) {
		log.error(ioe);
	    }
	}
	if (defaultSettings == null) {
	    try {
		defaultSettings = PropertiesManager.getDefaultProperties();
	    } catch (IOException ioe) {
		log.error(ioe);
	    }
	}
    }

    /**
     * @return
     */
    public String getCurrentMode() {
	return currentMode;
    }

    /**
     * @param currentMode
     */
    public void setCurrentMode(String currentMode) {
	this.currentMode = currentMode;
    }

    /**
     * @return
     */
    public Object[] getModes() {
	return modes.keySet().toArray();
    }

    /**
     * Method to support live debugging of bean
     */
    public String getModesAsString() {
	String msg = "Server modes: ";
	for (Object m : getModes()) {
	    msg += m.toString() + " ";
	}
	return msg;
    }

    /**
     * @param evt
     */
    public void modeChanged(ActionEvent evt) {
	setCurrentMode(getRequest().getParameter("mode"));
    }

    /**
     * Return the properties object with the server settings. This should be lazy initialized, it might be called many
     * times but we only need to read the file once.
     * 
     */
    public Properties getSettings() {
	return settings;
    }

    /**
     * @return
     */
    public String getCurrentClientMode() {
	currentClientMode = (String) settings.get("gp.allowed.clients");

	if (!(clientModes[0].equals(currentClientMode) || clientModes[1].equals(currentClientMode))) {
	    currentClientMode = clientModes[2];
	}
	return currentClientMode;
    }

    /**
     * @param mode
     */
    public void setCurrentClientMode(String mode) {
	currentClientMode = mode;
	if (!clientModes[2].equals(currentClientMode)) {
	    settings.put("gp.allowed.clients", mode);
	    specifiedClientMode = null;
	}
    }

    /**
     * @return
     */
    public String getSpecifiedClientMode() {
	return specifiedClientMode;
    }

    /**
     * @param mode
     */
    public void setSpecifiedClientMode(String mode) {
	specifiedClientMode = mode;
    }

    /**
     * @return
     */
    public List getSpecifiedClientModes() {
	return getSelectItems("gp.allowed.clients");
    }

    /**
     * @param mode
     */
    public void setSpecifiedClientModes(List clientModes) {
	setSelectItems(clientModes, "gp.allowed.clients");
    }

    /**
     * @param event
     */
    public void addSpecifiedClientMode(ActionEvent event) {
	if (clientModes[2].equals(currentClientMode)) {
	    removeDomain(clientModes[0]);
	    removeDomain(clientModes[1]);
	    String allClientModes = (String) settings.get("gp.allowed.clients");
	    String[] result = allClientModes.split(",");
	    // avoid adding duplicated domains.
	    boolean exist = false;
	    for (int i = 0; i < result.length; i++) {
		if (result[i] != null && result[i].equals(specifiedClientMode)) {
		    exist = true;
		    break;
		}
	    }
	    if (!exist && allClientModes.length() > 0) {
		allClientModes = allClientModes.concat(",").concat(specifiedClientMode);
	    } else if (allClientModes.length() == 0) {
		allClientModes = specifiedClientMode;
	    }
	    settings.put("gp.allowed.clients", allClientModes);
	}
	saveSettings(event);
    }

    /**
     * @param event
     */
    public void removeSpecifiedClientMode(ActionEvent event) {
	event.getComponent();
	removeDomain(specifiedClientMode);
	saveSettings(event);
    }

    private void removeDomain(String mode) {
	String allClientModes = (String) settings.get("gp.allowed.clients");
	String[] result = allClientModes.split(",");
	StringBuffer newClientModes = new StringBuffer();
	for (int i = 0; i < result.length; i++) {
	    if (result[i] != null && !result[i].equals(mode)) {
		newClientModes.append(result[i]).append(",");
	    }
	}
	String newClientModesStr = (newClientModes.length() > 0) ? newClientModes.substring(0,
		newClientModes.length() - 1) : "";
	settings.put("gp.allowed.clients", newClientModesStr);
    }

    /**
     * @param logFile
     * @return
     * @throws IOException
     */
    public String getLog(File logFile) throws IOException {
	StringBuffer buf = new StringBuffer();
	BufferedReader br = null;

	try {

	    if (logFile != null && logFile.exists()) {
		br = new BufferedReader(new FileReader(logFile));
		String thisLine = "";

		while ((thisLine = br.readLine()) != null) { // while loop
		    // begins here
		    buf.append(thisLine).append("\n");
		} // end while
	    }
	} catch (IOException exc) {
	    log.error(exc);
	    System.exit(1);
	}
	return buf.toString();

    }

    /**
     * @return
     * @throws IOException
     */
    public String getWsLog() throws IOException {
	return getLog(getWsLogFile());

    }

    /**
     * @return
     * @throws IOException
     */
    public String getGpLog() throws IOException {
	return getLog(getGpLogFile());
    }

    /**
     * @return
     */
    public String getGpLogHeader() {
	File gpLog = getGpLogFile();
	return getLogHeader(gpLog, "Gene Pattern");
    }

    /**
     * @return
     */
    public String getWsLogHeader() {
	File wsLog = getWsLogFile();
	return getLogHeader(wsLog, "Web Server");
    }

    /**
     * @param logFile
     * @param name
     * @return
     */
    private String getLogHeader(File logFile, String name) {
	StringBuffer buf = new StringBuffer();
	if ((logFile == null || !logFile.exists())) {
	    buf.append("Log not found.");
	} else {
	    buf.append(name + " log file from ");
	    buf.append(UIBeanHelper.getRequest().getServerName() + " on ");
	    buf.append(cal.getTime());

	}
	return buf.toString();
    }

    /**
     * @return
     */
    private File getGpLogFile() {
	String logPath = settings.getProperty("log4j.appender.R.File");
	if (logPath == null || !new File(logPath).exists()) {
	    String newLogPath = settings.getProperty(gpLogPath);
	    if (newLogPath != null) {
		return new File(newLogPath);
	    }
	}
	return new File(logPath);
    }

    /**
     * @return
     */
    private File getWsLogFile() {
	String logPath = settings.getProperty("log4j.appender.All.File");
	if (logPath == null || !new File(logPath).exists()) {
	    String newLogPath = settings.getProperty(wsLogPath);
	    if (newLogPath != null) {
		return new File(newLogPath);
	    }
	}
	return new File(logPath);
    }

    /**
     * @param repositoryName
     * @return
     */
    private List<SelectItem> getSelectItems(String commaSeparatedValue) {
	String selectItems = (String) settings.get(commaSeparatedValue);
	if (selectItems == null) {
	    return Collections.EMPTY_LIST;
	}
	String[] result = selectItems.split(",");
	List<SelectItem> valuesLst = new ArrayList<SelectItem>();
	for (int i = 0; i < result.length; i++) {
	    valuesLst.add(new SelectItem(result[i]));
	}
	return valuesLst;
    }

    /**
     * @param mrURLs
     * @param repositoryName
     */
    private void setSelectItems(List values, String Name) {
	StringBuffer repositoryURLs = new StringBuffer();
	for (int i = 0; i < values.size(); i++) {
	    repositoryURLs.append(values.get(i)).append(",");
	}
	settings.put(Name, repositoryURLs.substring(0, repositoryURLs.length() - 1).toString());
    }

    /**
     * @param currentRepositoryName
     * @param repositoryNames
     */
    private void addRepositoryURL(String currentRepositoryName, String repositoryNames) {
	String currentRepositoryURL = (String) settings.get(currentRepositoryName);
	String repositoryURLs = (String) settings.get(repositoryNames);
	String[] result = repositoryURLs.split(",");
	boolean exist = false;
	for (int i = 0; i < result.length; i++) {
	    if (result[i] != null && result[i].equals(currentRepositoryURL)) {
		exist = true;
		break;
	    }
	}
	if (!exist) {
	    repositoryURLs = repositoryURLs.concat(",").concat(currentRepositoryURL);
	}
	settings.put(repositoryNames, repositoryURLs);

    }

    /**
     * @param currentRepositoryName
     * @param repositoryNames
     * @param defaultName
     */
    private void removeRepositoryURL(String currentRepositoryName, String repositoryNames, String defaultName) {
	String currentRepositoryURL = (String) settings.get(currentRepositoryName);
	String repositoryURLs = (String) settings.get(repositoryNames);
	String[] result = repositoryURLs.split(",");
	StringBuffer newRepositoryURLs = new StringBuffer(defaultName + ",");
	if (!defaultName.equals(currentRepositoryURL)) {
	    for (int i = 0; i < result.length; i++) {
		if (!defaultName.equals(result[i]) && result[i] != null && !result[i].equals(currentRepositoryURL)) {
		    newRepositoryURLs.append(result[i]).append(",");
		}
	    }
	    settings.put(repositoryNames, newRepositoryURLs.substring(0, newRepositoryURLs.length() - 1).toString());
	}
    }

    /**
     * @return
     */
    public List getModuleRepositoryURLs() {
	addRepositoryURL("ModuleRepositoryURL", "ModuleRepositoryURLs");
	return getSelectItems("ModuleRepositoryURLs");
    }

    /**
     * @param mrURLs
     */
    public void setModuleRepositoryURLs(List mrURLs) {
	setSelectItems(mrURLs, "ModuleRepositoryURLs");
    }

    /**
     * @return
     */
    public void addModuleRepositoryURL(ActionEvent event) {
	addRepositoryURL("ModuleRepositoryURL", "ModuleRepositoryURLs");
	saveSettings(null);
    }

    /**
     * @return
     */
    public void removeModuleRepositoryURL(ActionEvent event) {
	String defaultModuleRepositoryURL = (String) defaultSettings.get("DefaultModuleRepositoryURL");
	removeRepositoryURL("ModuleRepositoryURL", "ModuleRepositoryURLs", defaultModuleRepositoryURL);
	settings.put("ModuleRepositoryURL", defaultModuleRepositoryURL);
    }

    /**
     * @return
     */
    public List getSuiteRepositoryURLs() {
	addRepositoryURL("SuiteRepositoryURL", "SuiteRepositoryURLs");
	return getSelectItems("SuiteRepositoryURLs");
    }

    /**
     * @param mrURLs
     */
    public void setSuiteRepositoryURLs(List mrURLs) {
	setSelectItems(mrURLs, "SuiteRepositoryURLs");
    }

    /**
     * @return
     */
    public void addSuiteRepositoryURL(ActionEvent event) {
	addRepositoryURL("SuiteRepositoryURL", "SuiteRepositoryURLs");
	saveSettings(null);
    }

    /**
     * @return
     */
    public void removeSuiteRepositoryURL(ActionEvent event) {
	String defaultSuiteRepositoryURL = (String) defaultSettings.get("DefaultSuiteRepositoryURL");
	removeRepositoryURL("SuiteRepositoryURL", "SuiteRepositoryURLs", defaultSuiteRepositoryURL);
	settings.put("SuiteRepositoryURL", defaultSuiteRepositoryURL);
    }

    /**
     * @return
     */
    public void setProxyHost(String host) {
	settings.put("http.proxyHost", host);
	settings.put("ftp.proxyHost", host);
    }

    public String getProxyHost() {
	return (String) settings.get("http.proxyHost");
    }

    public void setProxyPort(String port) {
	settings.put("http.proxyPort", port);
	settings.put("ftp.proxyPort", port);
    }

    public String getProxyPort() {
	return (String) settings.get("http.proxyPort");
    }

    public void setProxyUser(String user) {
	settings.put("http.proxyUser", user);
	settings.put("ftp.proxyUser", user);
    }

    public String getProxyUser() {
	return (String) settings.get("http.proxyUser");
    }

    /**
     * @return
     */
    public String getProxyPassword() {
	return (String) System.getProperty("http.proxyPassword");
    }

    /**
     * @param password
     */
    public void setProxyPassword(String password) {
	System.setProperty("http.proxyPassword", password);
	System.setProperty("ftp.proxyPassword", password);
    }

    /**
     * @return
     */
    public String getDb() {
	String db = (String) settings.get("database.vendor");
	return db;
    }

    /**
     * @param dbName
     */
    public void setDb(String dbName) {
	settings.put("database.vendor", dbName);
    }

    /**
     * @return
     */
    public String getClobRadio() {
	String value = (String) settings.get("hibernate.connection.SetBigStringTryClob");
	return (value == null) ? "" : value;
    }

    /**
     * @param mode
     */
    public void setClobRadio(String mode) {
	if (mode != null && (mode.equals("true") || mode.equals("false"))) {
	    settings.put("hibernate.connection.SetBigStringTryClob", mode);
	}
    }

    public String getDefaultSchema() {
	String value = (String) settings.get("hibernate.default_schema");
	return (value == null) ? "" : value;
    }

    public void setDefaultSchema(String defaultSchema) {
	if (defaultSchema != null && !defaultSchema.equals("")) {
	    settings.put("hibernate.default_schema", defaultSchema);
	}
    }

    public String getHibernatePassword() {
	String value = (String) settings.get("hibernate.connection.password");
	return (value == null) ? "" : value;
    }

    public void setHibernatePassword(String hibernatePassword) {
	if (hibernatePassword != null && !hibernatePassword.equals("")) {
	    settings.put("hibernate.connection.password", hibernatePassword);
	}
    }

    /**
     * @return
     */
    public String getLsidShowRadio() {
	String value = (String) settings.get("lsid.show");
	return (value == null) ? "" : (value.equals("1") ? "true" : "false");
    }

    /**
     * @param mode
     */
    public void setLsidShowRadio(String mode) {
	if (mode != null) {
	    if (mode.equals("true")) {
		settings.put("lsid.show", "1");
	    } else {
		settings.put("lsid.show", "0");
	    }
	}
    }

    /**
     * @return
     */
    public String getShutDownRadio() {
	String value = (String) settings.get("hibernate.connection.shutdown");
	return (value == null) ? "" : value;
    }

    /**
     * @param mode
     */
    public void setShutDownRadio(String mode) {
	if (mode != null && (mode.equals("true") || mode.equals("false"))) {
	    settings.put("hibernate.connection.shutdown", mode);
	}
    }

    /**
     * @return
     */
    public String shutDownServer() {
	System.exit(1);
	return null;
    }

    /**
     * @return
     */
    public String getNewCSKey() {
	return newCSKey;
    }

    /**
     * @return
     */
    public String getNewCSValue() {
	return newCSValue;
    }

    /**
     * @param key
     */
    public void setNewCSKey(String key) {
	newCSKey = key;
    }

    /**
     * @param value
     */
    public void setNewCSValue(String value) {
	newCSValue = value;
    }

    /**
     * @param event
     */
    public void saveNewCustomSetting(ActionEvent event) {
	if (newCSKey != "" && newCSValue != "") {
	    customSettings.add(new KeyValuePair(newCSKey, newCSValue));
	    PropertiesManager.storeChangesToCustomProperties(customSettings);
	}
    }

    /**
     * @param event
     */
    public void deleteCustomSetting(ActionEvent event) {
	String keyToRemove = getKey();
	for (KeyValuePair element : customSettings) {
	    if (element.getKey().equals(keyToRemove)) {
		customSettings.remove(element);
		break;
	    }
	}
    }

    /**
     * @return
     */
    public List<KeyValuePair> getCustomSettings() {
	return customSettings;
    }

    /**
     * @return
     */
    private String getKey() {
	Map params = UIBeanHelper.getExternalContext().getRequestParameterMap();
	String key = (String) params.get("key");
	return key;
    }

    /**
     * Save the settings back to the file. Trigger by the "submit" button on the page.
     * 
     * @return
     */
    public void saveSettings(ActionEvent event) {
	UIBeanHelper.setInfoMessage("Property successfully updated");
	PropertiesManager.storeChanges(settings);
	PropertiesManager.storeChangesToCustomProperties(customSettings);
    }

    /**
     * @param event
     */
    public void restore(ActionEvent event) {
	String[] propertyKeys = modes.get(currentMode);
	String subtype = currentMode.equals("Repositories") ? (String) event.getComponent().getAttributes().get(
		"subtype") : "";

	if (propertyKeys != null) {
	    String defaultValue;
	    Vector<String> keysToRemove = new Vector<String>();
	    for (String propertyKey : propertyKeys) {
		if (subtype.equals("Module") && !propertyKey.contains(subtype)) {
		    continue;
		} else if (subtype.equals("Suite") && !propertyKey.contains(subtype)) {
		    continue;
		}
		defaultValue = (String) defaultSettings.get(propertyKey);
		if (defaultValue != null) {
		    settings.put(propertyKey, defaultValue);
		} else {
		    settings.remove(propertyKey);
		    keysToRemove.add(propertyKey);
		}
	    }
	    if (keysToRemove.size() > 0) {
		PropertiesManager.removeProperties(keysToRemove);
	    }
	    this.saveSettings(event);
	}
    }

    public String getPurgeJobsAfter() {
	return (String) settings.get("purgeJobsAfter");
    }

    public void setPurgeJobsAfter(String purgeJobsAfter) {
	settings.setProperty("purgeJobsAfter", purgeJobsAfter);
    }

    public String getPurgeTime() {
	return (String) settings.get("purgeTime");
    }

    public void setPurgeTime(String purgeTime) {
	settings.setProperty("purgeTime", purgeTime);
    }

    public void savePurgeSettings(ActionEvent event) {
	saveSettings(event);
	StartupServlet.startJobPurger();
    }

}
