<!-- /*
The Broad Institute
SOFTWARE COPYRIGHT NOTICE AGREEMENT
This software and its documentation are copyright (2003-2006) by the
Broad Institute/Massachusetts Institute of Technology. All rights are
reserved.

This software is supplied without any warranty or guaranteed support
whatsoever. Neither the Broad Institute nor MIT can be responsible for its
use, misuse, or functionality.
*/ -->


<%@ page import="org.genepattern.server.genepattern.GenePatternAnalysisTask,
                 org.genepattern.server.webservice.server.local.LocalTaskIntegratorClient,
                 org.genepattern.server.webservice.server.local.LocalAnalysisClient,
                 org.genepattern.util.GPConstants,
                 org.genepattern.util.StringUtils,
                 org.genepattern.server.TaskUtil,
		     org.genepattern.webservice.OmnigeneException,
                 org.genepattern.webservice.ParameterFormatConverter,
                 org.genepattern.webservice.ParameterInfo,
                 org.genepattern.webservice.TaskInfo,
                 org.genepattern.webservice.JobInfo,
                 org.genepattern.webservice.TaskInfoAttributes,
                 javax.servlet.RequestDispatcher,
                 java.io.File,
                 java.net.URLEncoder,
		     java.net.URL,
                 java.util.HashMap"
         session="false" contentType="text/html" language="Java" %>
<%

    response.setHeader("Cache-Control", "no-store"); // HTTP 1.1 cache control
    response.setHeader("Pragma", "no-cache");         // HTTP 1.0 cache control
    response.setDateHeader("Expires", 0);
    String agent = request.getHeader("USER-AGENT");
    String iFrameWidth = "width=\"100%\"";
    if (agent.indexOf("Safari") >= 0) {
        iFrameWidth = "width=\"250\"";
    }
    String taskName = request.getParameter(GPConstants.NAME);
    String reloadJobNo = request.getParameter("reloadJob");
    String username = request.getParameter(GPConstants.USERID);
    if (username == null || username.length() == 0) {
        username = (String) request.getAttribute("userID");
    }
    JobInfo reloadJob = null;
    if (reloadJobNo != null) {
	LocalAnalysisClient ac = new LocalAnalysisClient(username);
	reloadJob = ac.getJob(Integer.parseInt(reloadJobNo));
    }


    if (taskName == null || taskName.length() == 0) {
%>
<html>
<head>
    <link href="skin/stylesheet.css" rel="stylesheet" type="text/css">
    <link href="css/style.css" rel="stylesheet" type="text/css">
    <link rel="SHORTCUT ICON" href="favicon.ico">
    <title>run GenePattern task</title>
</head>
<body>
<jsp:include page="navbar.jsp"/>
<div id="content" class="content">
<table width="100%" border="0" cellspacing="0" cellpadding="0">
	
	<tr>
		<td valign="top" class="maincontent" id="maincontent">

Must specify task name.<br>

<jsp:include page="footer.jsp"/>

</body>
</html>
<%
        return;
    }
   
    boolean bNoEnvelope = (request.getParameter("noEnvelope") != null);
    TaskInfo taskInfo = null;
    try {
        String lsid = request.getParameter(GPConstants.LSID);
        if (lsid == null) {
            lsid = taskName;
        }
        taskInfo = GenePatternAnalysisTask.getTaskInfo(lsid, username);
    } catch (OmnigeneException oe) {
    }
    TaskInfoAttributes tia = taskInfo.giveTaskInfoAttributes();
    ParameterInfo[] parameterInfoArray = null;
    try {
        parameterInfoArray = new ParameterFormatConverter().getParameterInfoArray(taskInfo.getParameterInfo());
        if (parameterInfoArray == null) {
            parameterInfoArray = new ParameterInfo[0];
        }
    } catch (OmnigeneException oe) {
    }
    String taskType = tia.get("taskType");
    boolean isVisualizer = "visualizer".equalsIgnoreCase(taskType);
    boolean isPipeline = "pipeline".equalsIgnoreCase(taskType);
    String formAction = "runTaskPipeline.jsp";
    if (isVisualizer) {
        formAction = "preRunVisualizer.jsp";
    } else if (isPipeline) {
        formAction = "runPromptingPipeline.jsp";
        int numParams = parameterInfoArray.length;
        if (numParams == 0) {
            try {
                RequestDispatcher rd = request.getRequestDispatcher("runPipeline.jsp");
                rd.forward(request, response);
                return;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    if (!bNoEnvelope) { %>
<html>
<head>
    <link href="skin/stylesheet.css" rel="stylesheet" type="text/css">
    <link rel="SHORTCUT ICON" href="favicon.ico">
    <title>run <%= taskInfo != null ? taskInfo.getName() : "GenePattern task" %>
    </title>
    <style>
        .heading {
            font-family: Arial, Helvetica, sans-serif;
            background-color: #0E0166;
            color: white;
            font-size: 12pt;
            font-weight: 800;
            text-align: center;
        }

        .majorCell {
            border-width: 2;
            font-size: 10pt;
        }

        .button {
            width: 50;
        }

        .wideButton {
            width: 100;
        }

        .wideBoldButton {
            width: 100;
            font-weight: bold;
            color: red
        }

        td {
            padding-left: 5;
        }
    </style>
</head>
<body>
<jsp:include page="navbar.jsp"/>

<% } %>

<%
    if (taskInfo == null) {
%>
<script language="javascript">
    alert('No such task <%= taskName %>');
</script>
No such task <%= taskName %><br>
<% if (!bNoEnvelope) { %>
<jsp:include page="footer.jsp"/>

</body>
</html>
<% } %>
<%
        return;
    }
    taskName = taskInfo.getName();

//XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX

%>
<script language="javascript">
    function resetValues() {
        // alert('Resetting');
        window.location = 'runTask.jsp?<%= GPConstants.NAME %>=<%= taskName %>'
    }
    function formvalidation(form) {
        var requiredParams = new Array();
    <%
     int count = 0;
     for (int i = 0; i < parameterInfoArray.length; i++) {
         ParameterInfo pi = parameterInfoArray[i];
         HashMap pia = pi.getAttributes();
         boolean isOptional = ((String)pia.get(GPConstants.PARAM_INFO_OPTIONAL[GPConstants.PARAM_INFO_NAME_OFFSET])).length() > 0;
         if(!isOptional) {
             out.println("requiredParams[" + count + "] = \"" + pi.getName() + "\";");
             count++;
         }
     }
     %>
        var error = false;
        var missingParams = "<br/>";
        for (param in requiredParams) {
            var formElementName = requiredParams[param];
            var e = form[formElementName];
            var type = e.type;
            if (type == "file" || type == "text" || type == "textarea") {
                var value = e.value;
                if (value == null || value == "") {
                    error = true;
                    var name = e.name;
                    name = name.replace(".", " ");
                    missingParams += "<br/>" + "<b>" + name + "</b>";
                }
            }
        }
        d = document.getElementById("errorMessageDiv");
        t = document.getElementById("errorMessage");
        if (error) {
            d.style.display = "inline";
            t.innerHTML =
            "<font color=\"red\" size=\"+1\">The task could not be run. The following required parameters need to have values provided:</font>" +
            missingParams;
        } else {
            d.style.display = "hidden";
        }
        return !error;
    }
</script>


<table cols="2">

<tr>
<td valign='top' height='100%'>
    <iframe frameborder="0" scrolling="yes" marginwidth="1" src="getRecentJobs.jsp" <%=iFrameWidth%> height="500"
            name="iframe" id="iframeid">
        No &lt;iframes&gt; support :(
    </iframe>


</td>
<td valign='top'>
<%
    int veridx = ((String) tia.get(GPConstants.LSID)).lastIndexOf(":");
    String taskLsidVersion = ((String) tia.get(GPConstants.LSID)).substring(veridx + 1);

%>

<table>
    <tr>
        <td><b><font size="+1"><%= taskName %>
        </font></b> version <%= taskLsidVersion%>
        </td>
        <%
            if (taskName != null) {
                LocalTaskIntegratorClient taskIntegratorClient = new LocalTaskIntegratorClient(username, out);
                File[] docFiles = taskIntegratorClient.getDocFiles(taskInfo);
                boolean hasDoc = docFiles != null && docFiles.length > 0;
                if (hasDoc) {
        %>
        <td align="right"><b>Documentation:</b><%
            for (int i = 0; i < docFiles.length; i++) { %>
            <a href="getTaskDoc.jsp?<%= GPConstants.NAME %>=<%= StringUtils.htmlEncode(request.getParameter(GPConstants.NAME)) %>&file=<%= URLEncoder.encode(docFiles[i].getName()) %>"
               target="new"><%= StringUtils.htmlEncode(docFiles[i].getName()) %>
            </a><%
            }
        %></td>
    </tr>
    <%
            }
        }

// XXXXXXXXXXXXXXXXXXXXXXXXX

    %>
    <div id="errorMessageDiv" style="display:none;">
        <p id="errorMessage">></p>
    </div>
</table>


<form name="pipeline" action="<%=formAction%>" method="post" ENCTYPE="multipart/form-data"
      onsubmit="return formvalidation(this);">
<input type="hidden" name="taskName" value="<%= taskName %>">
<input type="hidden" name="taskLSID" value="<%= tia.get(GPConstants.LSID) %>">
<input type="hidden" name="<%= GPConstants.USERID %>" value="<%= username %>">
<input type="hidden" name="taskName" value="<%= taskName %>">

<table valign="top">


<%
    int numParams = parameterInfoArray.length;
    if (numParams > 0) { %>
<tr>
    <td align='left' colspan='2'><b>&nbsp;&nbsp;</b></td>
</tr>
<% } else { %>
<tr>
    <td align='left' colspan='2'><i>has no input parameters</i></td>
</tr>
<% }
    String prefix = "";
    HashMap reloadParamMap = new HashMap();
    if (reloadJob != null){
		ParameterInfo[] reloadParams = reloadJob.getParameterInfoArray();
		for (int i=0; i < reloadParams.length; i++){
			reloadParamMap.put(reloadParams[i].getName(), reloadParams[i].getValue());
		}
    }

    for (int param = 0; param < parameterInfoArray.length; param++) {
        out.flush();
        ParameterInfo pi = parameterInfoArray[param];
        HashMap pia = pi.getAttributes();
        String[] choices = null;
        String[] stChoices = pi.getChoices(GPConstants.PARAM_INFO_CHOICE_DELIMITER);
        String val = pi.getValue();
        boolean isOptional = ((String) pia.get(GPConstants.PARAM_INFO_OPTIONAL[GPConstants.PARAM_INFO_NAME_OFFSET]))
                .length() > 0;
        String defaultValue = (request.getParameter(pi.getName()) != null ? request.getParameter(pi.getName()) :
                (String) pia.get(GPConstants.PARAM_INFO_DEFAULT_VALUE[0]));
        if (defaultValue != null) {
            defaultValue = defaultValue.trim();
        }
	  if (reloadJob != null){
		defaultValue = (String)reloadParamMap.get(pi.getName());
		
		// convert reloaded files to URLs
	//	 File f = new File(defaultValue);
	//	 boolean fileExists = f.exists();
	//	if (fileExists){
	//		defaultValue="file://" + StringUtils..htmlEncode(f.getCanonicalPath());
	//		String urlBase = request.getRequestURL().toString();
	//		String ending = request.getServletPath();
	//		urlBase = urlBase.substring(0, urlBase.indexOf(ending));
	//		defaultValue= urlBase +"/getInputFile.jsp?file="+StringUtils.htmlEncode(f.getName());	
	//	}
		
		System.out.println("DV=" + defaultValue);
	  }
        String description = pi.getDescription();
	  String displayDesc =  (String)pia.get("altDescription");
	  if (displayDesc != null) description = displayDesc;


	// use the alternative name if provided.  this is to allow pipelines to rename input parameters in the UI
	// while still maintining the chain of where the param is to go in the real pipeline name
	// i.e. I am too lazy to rewrite all the pipeline code to add this feature (JTL)
	  String displayName =  (String)pia.get("altName");
	  if (displayName == null) displayName = pi.getName();
	  
	  if (pi.getAttributes() != null && pi.getAttributes().containsKey(pi.TYPE)) {
			String type = (String)pi.getAttributes().get(pi.TYPE);
			System.out.println("T2=" + type);
		}
%>

<tr>
<td align="right" valign="top">
    <nobr><%= !isOptional ? "<b>" : "" %><%= displayName.replace('.', ' ') %>:<%=
        !isOptional ? "<span style=\"font-size: medium;\"> *</span></b>" : "" %>
    </nobr>
</td>
<td valign="top" align='left'>

    <% 		if (pi.isInputFile()) { 

			String urlStr = defaultValue;

			if (defaultValue != null){
				if (defaultValue.trim().length() > 0){

				File f = new File(defaultValue);
				String axisName = f.getName();
				boolean fileExists = f.exists();
				boolean isURL=false;
				if (fileExists){
					String urlBase = request.getRequestURL().toString();
					String ending = request.getServletPath();
					urlBase = urlBase.substring(0, urlBase.indexOf(ending));
					urlStr= urlBase +"/getInputFile.jsp?file="+StringUtils.htmlEncode(axisName);
					
				} else {
					try {// see if a URL was passed in
						URL url = new URL(defaultValue);
						isURL = true;
					} catch (Exception e){
						e.printStackTrace();
					}

				}
				String name = axisName;						
				int idx = axisName.lastIndexOf("att_");
				if (idx > 0) {
					name = axisName.substring(idx+4);
				}
			%>
			
		<input	
				name="<%= pi.getName() %>" 
				value="<%= urlStr %>"
				size="60" 
				readOnly="true"
				class="little"/>

			<%
				}

			}



%>

    <input type="file"
           name="<%= pi.getName() %>"
           size="60"
           onchange="this.form['shadow<%= pi.getName() %>'].value=this.value;"
           onblur="javascript:if (this.value.length > 0) { this.form['shadow<%= pi.getName() %>'].value=this.value; }"
           ondrop="this.form['shadow<%= pi.getName() %>'].value=this.value;"
           class="little">
    <input name="shadow<%= pi.getName() %>"
           type="hidden"
           value="<%= defaultValue == null ? "" : defaultValue %>"
           readonly
           size="90"
           tabindex="-1"
           class="shadow"
           style="{ border-style: none; font-style: italic; font-size:9pt; background-color: transparent }">

    <%







       if (description.length() > 0 && !description.equals(pi.getName().replace('.',' '))) {
           out.println("<br>" + StringUtils.htmlEncode(description));
       }
   } else if (pi.isOutputFile()) {
   } else if (pi.isPassword()){
%>
	   <table align="left">
	    <tr>
	        <td valign="top">
	            <input type="password" name="<%= pi.getName() %>" value="<%=  defaultValue %>">
	        </td>
	        <%
	            if (description.length() > 0) { %>
	        <td valign="top"><%= StringUtils.htmlEncode(description) %>
	        </td>
	        <% } %>
	    </tr>
	</table>
	<%    
	   
   } else if (stChoices.length < 2) {

    %>
<table align="left">
    <tr>
        <td valign="top">
            <input name="<%= pi.getName() %>" value="<%=  defaultValue %>">
        </td>
        <%
            if (description.length() > 0) { %>
        <td valign="top"><%= StringUtils.htmlEncode(description) %>
        </td>
        <% } %>
    </tr>
</table>
<%		} else { %>
<table align="left">
    <tr>
        <td valign="top">
            <select name="<%= pi.getName() %>">
                <%
                    String display = null;
                    String option = null;
                    String choice;
                    for (int iChoice = 0; iChoice < stChoices.length; iChoice++) {
                        choice = stChoices[iChoice];
                        int c = choice.indexOf(GPConstants.PARAM_INFO_TYPE_SEPARATOR);
                        if (c == -1) {
                            display = choice;
                            option = choice;
                        } else {
                            option = choice.substring(0, c);
                            display = choice.substring(c + 1);
                        }
                        display = display.trim();
                        option = option.trim();
                %>
                <option value="<%= option %>"<%=
                    defaultValue.equals(option) || defaultValue.equals(display) ? " selected" : "" %>><%=
                    display %>
                </option>
                <% } %>
            </select>
        </td>
        <td valign="top"><%= StringUtils.htmlEncode(description) %>
        </td>
    </tr>
</table>
<%




        }
        out.println("</td></tr>");
    }




%>
<tr>
    <td></td>
    <td><input type="submit" name="cmd" value="run"><input type="button" name="reset" value="reset"
                                                           onClick="resetValues()"> <input type="button"
                                                                                           value="help"
                                                                                           onclick="window.open('getTaskDoc.jsp?<%= GPConstants.NAME %>=<%= taskName %>', '_new')">
    </td>
</tr>

</table>
</form>


<% if (!bNoEnvelope) { %>
</td>
</tr>
</table>
<jsp:include page="footer.jsp"/>

</body>
</html>
<% } %>