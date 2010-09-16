package org.genepattern.server.executor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;
//import org.genepattern.server.AnalysisTask;
import org.genepattern.server.JobInfoManager;
import org.genepattern.server.database.HibernateUtil;
import org.genepattern.server.domain.AnalysisJob;
import org.genepattern.server.domain.JobStatus;
import org.genepattern.server.webservice.server.dao.AnalysisDAO;
import org.genepattern.webservice.JobInfo;
import org.hibernate.Query;
import org.hibernate.Session;

/**
 * Default implementation of the CommandManager interface.
 * Note, there is special-handling for pipeline execution. There is one, and only one, CommandExecutor for pipelines.
 * By default this is set implicitly (no need for anything in the job_configuration file) with the following private class variables:
 *     {@link #DEFAULT_PIPELINE_EXEC_ID}
 *     {@link #DEFAULT_PIPELINE_EXEC_CLASS}
 * 
 * To override the default, set a 'pipeline.executor' in the 'default.properties' section of the job configuration file.
 * This is an id to an executor which must be defined in the 'executors' section of the configuration file.
 * For example:
<code>
default.properties:
    executor: RuntimeExec
    pipeline.executor: PipelineExec

executors:
    # simple declaration, of the form <id>:<classname>
    RuntimeExec: org.genepattern.server.executor.RuntimeCommandExecutor
    # explicitly declare pipeline executor
    PipelineExec: org.genepattern.server.executor.pipeline.CustomPipelineExecutor
</code>
 * 
 * @author pcarr
 */
public class BasicCommandManager implements CommandManager {
    private static Logger log = Logger.getLogger(BasicCommandManager.class);
    
    private AnalysisJobScheduler analysisTaskScheduler = null;
    
    public void startAnalysisService() { 
        log.info("starting analysis service...");
        handleJobsOnServerStartup();
        
        if (analysisTaskScheduler == null) {
            analysisTaskScheduler = new AnalysisJobScheduler();
        }
        analysisTaskScheduler.startQueue();
        log.info("...analysis service started!");
    }
    
    public void shutdownAnalysisService() {
        if (analysisTaskScheduler != null) {
            analysisTaskScheduler.stopQueue();
        }
        log.info("shutting down analysis service...done!");
    }

    //TODO: use paged results to handle large number of 'Processing' jobs
    private void handleJobsOnServerStartup() {
        log.info("handling 'DISPATCHING' and 'PROCESSING' jobs on server startup ...");
        List<MyJobInfoWrapper> runningJobs = getRunningJobs();

        for(MyJobInfoWrapper jobInfoWrapper : runningJobs) {
            JobInfo jobInfo = jobInfoWrapper.jobInfo;
            String jobId = ""+jobInfo.getJobNumber();

            //boolean isPipeline = jobInfoWrapper.isPipeline;
            //boolean isInPipeline = jobInfoWrapper.isInPipeline;
            //if (isPipeline) {
            //    setJobStatus(jobInfo, JobStatus.JOB_ERROR);
            //}
            //else if (isInPipeline) {
            //    setJobStatus(jobInfo, JobStatus.JOB_ERROR);
            //}
            //else {
            try {
                String curStatus = jobInfo.getStatus();
                CommandExecutor cmdExec = CommandManagerFactory.getCommandManager().getCommandExecutor(jobInfo);
                int updatedStatusId = cmdExec.handleRunningJob(jobInfo);
                
                if (statusChanged(curStatus, updatedStatusId)) {
                    setJobStatus(jobInfo, updatedStatusId);
                }
                //TODO: if necessary notify the parent pipeline, which would be necessary when the status is changed to 'error' or 'finished'
            }
            catch (CommandExecutorNotFoundException e) {
                log.error("error getting command executor for job #"+jobId, e);
                setJobStatusToError(jobInfo);
            }
            catch (Exception e) {
                log.error("error handling running job on server startup for job #"+jobId, e);
                setJobStatusToError(jobInfo);
            }
        }
        log.info("... done handling 'DISPATCHING' and 'PROCESSING' jobs on server startup.");
    }
    
    private static boolean statusChanged(String origStatus, int updatedStatusId) {
        int origStatusId = JobStatus.STATUS_MAP.get(origStatus);
        return origStatusId != updatedStatusId;
    }
    
    private void setJobStatusToError(JobInfo jobInfo) {
        setJobStatus(jobInfo, JobStatus.JOB_ERROR);
    }
    
    private void setJobStatus(JobInfo jobInfo, int jobStatus) {
        String jobId = ""+jobInfo.getJobNumber();
        try {
            AnalysisDAO dao = new AnalysisDAO();
            dao.updateJobStatus(jobInfo.getJobNumber(), jobStatus);
            HibernateUtil.commitTransaction();
        }
        catch (Exception e) {
            HibernateUtil.rollbackTransaction();
            log.error("Unable to set status to "+jobStatus+" for job #"+jobId);
        }
        finally {
            HibernateUtil.closeCurrentSession();
        } 
    }

    private List<MyJobInfoWrapper> getRunningJobs() {
        try {
            AnalysisDAO dao = new AnalysisDAO();
            int numRunningJobs = -1;
            return getRunningJobs(dao, numRunningJobs);
        }
        catch (Throwable t) {
            log.error("error getting list of running jobs from the server", t);
            return new ArrayList<MyJobInfoWrapper>();
        }
        finally {
            HibernateUtil.closeCurrentSession();
        }
    }
    
    //private int getNumRunningJobs(Session session) { 
    //    final String hql = 
    //        "select count(*) from org.genepattern.server.domain.AnalysisJob "+
    //        " where jobStatus.statusId = :statusId and deleted = false ";
    //    
    //    Query query = session.createQuery(hql);
    //    query.setInteger("statusId", JobStatus.JOB_PROCESSING);
    //    Object rval = query.uniqueResult();
    //    return AnalysisDAO.getCount(rval);
    //}

    private static List<MyJobInfoWrapper> getRunningJobs(AnalysisDAO dao, int maxJobCount) {
        List<MyJobInfoWrapper> runningJobs = new ArrayList<MyJobInfoWrapper>();
        Session session = HibernateUtil.getSession();
        //final String hql = "from org.genepattern.server.domain.AnalysisJob where ( jobStatus.statusId = :statusId or jobStatus.statusId = :dispatchingStatusId ) and deleted = false order by submittedDate ";
        final String hql = "from org.genepattern.server.domain.AnalysisJob where jobStatus.statusId = :statusId and deleted = false order by submittedDate ";
        Query query = session.createQuery(hql);
        if (maxJobCount > 0) {
            query.setMaxResults(maxJobCount);
        }
        query.setInteger("statusId", JobStatus.JOB_PROCESSING);
        //query.setInteger("dispatchingStatusId", JobStatus.JOB_DISPATCHING);
        List<AnalysisJob> jobList = query.list();
        for(AnalysisJob aJob : jobList) {
            JobInfo singleJobInfo = new JobInfo(aJob);
            
            final boolean closeDbSession = false;
            boolean isPipeline = JobInfoManager.isPipeline(singleJobInfo, closeDbSession);
            boolean isInPipeline = false;
            if (!isPipeline) {
                //find out if it is a top-level job
                JobInfo parentJobInfo = dao.getParent(singleJobInfo.getJobNumber());
                isInPipeline = parentJobInfo != null;
            }
            MyJobInfoWrapper m = new MyJobInfoWrapper();
            m.jobInfo = singleJobInfo;
            m.isPipeline = isPipeline;
            m.isInPipeline = isInPipeline;
            runningJobs.add(m);
        }
        return runningJobs;
    }
    
    private static class MyJobInfoWrapper {
        JobInfo jobInfo;
        boolean isPipeline;
        boolean isInPipeline;
    }

    private CommandManagerProperties configProperties = new CommandManagerProperties();
    public CommandManagerProperties getConfigProperties() {
        return configProperties;
    }
    public void setConfigProperties(CommandManagerProperties configProperties) {
        this.configProperties = configProperties;
    }
    
    //map cmdExecId - commandExecutor
    private LinkedHashMap<String,CommandExecutor> cmdExecutorsMap = new LinkedHashMap<String,CommandExecutor>();
    //hold a single executor for all pipelines
    private static final String DEFAULT_PIPELINE_EXEC_ID = "org.genepattern.server.executor.PipelineExecutor";
    
    public void addCommandExecutor(String id, CommandExecutor cmdExecutor) throws Exception {
        if (cmdExecutorsMap.containsKey(id)) {
            throw new Exception("duplicate id: "+id);
        }
        cmdExecutorsMap.put(id, cmdExecutor);
    }
    
    private String pipelineExecId = DEFAULT_PIPELINE_EXEC_ID;
    public void setPipelineExecutor(String id) {
        this.pipelineExecId = id;
    }
    
    public CommandExecutor getCommandExecutorById(String cmdExecutorId) {
        return cmdExecutorsMap.get(cmdExecutorId);
    }

    //implement the CommandExecutorMapper interface
    public CommandExecutor getCommandExecutor(JobInfo jobInfo) throws CommandExecutorNotFoundException {
        CommandExecutor cmdExec = null;
        
        //special case for pipelines ...
        boolean isPipeline = JobInfoManager.isPipeline(jobInfo);
        if (isPipeline) {
            log.debug("job "+jobInfo.getJobNumber()+" is a pipeline");
            return getPipelineExecutor();
        }
        //TODO: special case for visualizers ... if a job is a visualizer ignore it
        //boolean isVisualizer = JobInfoManager.isVisualizer(jobInfo);
        //if (isVisualizer) {
        //    
        //}

        //initialize to default executor
        String cmdExecId = this.configProperties.getCommandExecutorId(jobInfo);
        if (cmdExecId == null) {
            log.info("no commandExecutorId found for job, use the first one from the list.");
            cmdExecId = getFirstCmdExecId();
        }
        
        cmdExec = cmdExecutorsMap.get(cmdExecId);
        if (cmdExec == null) {
            String errorMessage = "no CommandExecutor found for job: ";
            if (jobInfo != null) {
                errorMessage += (jobInfo.getJobNumber()+". "+jobInfo.getTaskName());
            }
            else {
                errorMessage += "null";
            }
            throw new CommandExecutorNotFoundException(errorMessage);
        }
        return cmdExec;
    }

    private String getFirstCmdExecId() {
        String firstKey = cmdExecutorsMap.keySet().iterator().next();
        return firstKey;
    }
    
    public Properties getCommandProperties(JobInfo jobInfo) {
        return this.configProperties.getCommandProperties(jobInfo);
    }

    public Map<String, CommandExecutor> getCommandExecutorsMap() {
        return Collections.unmodifiableMap(cmdExecutorsMap);
    }

    /**
     * Call this at system startup to initialize the list of CommandExecutorService instances.
     * Don't start the pipeline executor until all of the other executors have started.
     */
    public void startCommandExecutors() { 
        //need this first to identify which executor in the list is the pipeline executor
        initPipelineExecutor();

        for(String cmdExecId : cmdExecutorsMap.keySet()) {
            if (cmdExecId.equals(pipelineExecId)) {
                //don't start the pipeline executor
            }
            else {
                CommandExecutor cmdExec = cmdExecutorsMap.get(cmdExecId);
                if (cmdExec == null) {
                    log.error("null CommandExecutor for cmdExecId: '"+cmdExecId+"'");
                }
                else {
                    try {
                        cmdExec.start();
                    }
                    catch (Throwable t) {
                        log.error("Error starting CommandExecutor, for class: "+cmdExec.getClass().getCanonicalName()+": "+t.getLocalizedMessage(), t);
                    }
                }
            }
        }
        
        //start pipeline executor
        getPipelineExecutor().start();
    }
    
    /**
     * Call this at system shutdown to stop the list of running CommandExecutorService instances.
     * 
     * Implementation note: pipeline execution is treated as a special-case. First, terminate all running pipelines, then terminate any remaining jobs.
     */
    public void stopCommandExecutors() {
        CommandExecutor pipelineExec = getPipelineExecutor();
        if (pipelineExec != null) {
            try {
                pipelineExec.stop();
            }
            catch (Throwable t) {
                log.error("Error stopping PipelineExecutor", t);
            }
        }
        
        for(String cmdExecId : cmdExecutorsMap.keySet()) {
            CommandExecutor cmdExec = cmdExecutorsMap.get(cmdExecId);
            try {
                if (cmdExec != pipelineExec) {
                    cmdExec.stop();
                }
            }
            catch (Throwable t) {
                log.error("Error stopping CommandExecutorService, for class: "+cmdExec.getClass().getCanonicalName()+": "+t.getLocalizedMessage(), t);
            }
        }
    }
    
    private synchronized void initPipelineExecutor() {
        log.debug("initializing pipeline executor");
        String defaultPipelineExecId = this.configProperties.getTop().getDefaultProperty("pipeline.executor");
        if (defaultPipelineExecId == null) {
            defaultPipelineExecId = DEFAULT_PIPELINE_EXEC_ID;
        }
        this.pipelineExecId = defaultPipelineExecId;
        CommandExecutor pipelineExec = cmdExecutorsMap.get(pipelineExecId);
        if (pipelineExec == null) {
            //initialize to default setting
            pipelineExec = new PipelineExecutor();
            cmdExecutorsMap.put(pipelineExecId, pipelineExec);
        }
        log.info("pipeline.executor, id="+pipelineExecId);
        log.info("pipeline.executor, class="+pipelineExec.getClass().getCanonicalName());
    }
    
    private CommandExecutor getPipelineExecutor() {
        CommandExecutor pipelineExec = cmdExecutorsMap.get(pipelineExecId);
        if (pipelineExec == null) {
            log.error("Pipeline configuration error: no CommandExecutor exists with id="+pipelineExecId);
            //hard-coded to default pipeline execution in spite of config file error
            pipelineExec = new PipelineExecutor();
            cmdExecutorsMap.put(pipelineExecId, pipelineExec);
        }
        return pipelineExec;
    }
    
    public void wakeupJobQueue() {
        if (analysisTaskScheduler == null) {
            log.error("analysisTaskScheduler is null");
        }
        analysisTaskScheduler.wakeupJobQueue();
    }
}
