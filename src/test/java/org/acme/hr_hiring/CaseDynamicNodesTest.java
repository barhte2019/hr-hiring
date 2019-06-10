package org.acme.hr_hiring;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import javax.persistence.EntityManagerFactory;

import org.drools.compiler.kie.builder.impl.InternalKieModule;
import org.jbpm.casemgmt.api.CaseNotFoundException;
import org.jbpm.casemgmt.api.CaseRuntimeDataService;
import org.jbpm.casemgmt.api.CaseService;
import org.jbpm.casemgmt.api.auth.AuthorizationManager;
import org.jbpm.casemgmt.api.dynamic.TaskSpecification;
import org.jbpm.casemgmt.api.generator.CaseIdGenerator;
import org.jbpm.casemgmt.api.model.CaseStatus;
import org.jbpm.casemgmt.api.model.instance.CaseFileInstance;
import org.jbpm.casemgmt.api.model.instance.CaseInstance;
import org.jbpm.casemgmt.impl.AuthorizationManagerImpl;
import org.jbpm.casemgmt.impl.CaseRuntimeDataServiceImpl;
import org.jbpm.casemgmt.impl.CaseServiceImpl;
import org.jbpm.casemgmt.impl.event.CaseConfigurationDeploymentListener;
import org.jbpm.casemgmt.impl.generator.TableCaseIdGenerator;
import org.jbpm.casemgmt.impl.marshalling.CaseMarshallerFactory;
import org.jbpm.kie.services.impl.FormManagerServiceImpl;
import org.jbpm.kie.services.impl.KModuleDeploymentService;
import org.jbpm.kie.services.impl.KModuleDeploymentUnit;
import org.jbpm.kie.services.impl.ProcessServiceImpl;
import org.jbpm.kie.services.impl.RuntimeDataServiceImpl;
import org.jbpm.kie.services.impl.bpmn2.BPMN2DataServiceImpl;
import org.jbpm.kie.services.impl.query.QueryServiceImpl;
import org.jbpm.kie.services.impl.security.DeploymentRolesManager;
import org.jbpm.runtime.manager.impl.RuntimeManagerFactoryImpl;
import org.jbpm.runtime.manager.impl.jpa.EntityManagerFactoryManager;
import org.jbpm.services.api.DefinitionService;
import org.jbpm.services.api.DeploymentService;
import org.jbpm.services.api.ProcessService;
import org.jbpm.services.api.RuntimeDataService;
import org.jbpm.services.api.model.DeploymentUnit;
import org.jbpm.services.api.model.ProcessDefinition;
import org.jbpm.services.api.model.ProcessInstanceDesc;
import org.jbpm.services.api.model.UserTaskInstanceDesc;
import org.jbpm.services.api.query.QueryService;
import org.jbpm.services.task.HumanTaskConfigurator;
import org.jbpm.services.task.HumanTaskServiceFactory;
import org.jbpm.services.task.audit.TaskAuditServiceFactory;
import org.jbpm.services.task.impl.model.GroupImpl;
import org.jbpm.shared.services.impl.TransactionalCommandService;
import org.jbpm.test.services.TestIdentityProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.builder.ReleaseId;
import org.kie.api.builder.model.KieBaseModel;
import org.kie.api.builder.model.KieModuleModel;
import org.kie.api.builder.model.KieSessionModel;
import org.kie.api.conf.EqualityBehaviorOption;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.runtime.conf.ClockTypeOption;
import org.kie.api.runtime.query.QueryContext;
import org.kie.api.task.TaskService;
import org.kie.api.task.UserGroupCallback;
import org.kie.api.task.model.OrganizationalEntity;
import org.kie.api.task.model.Status;
import org.kie.api.task.model.TaskSummary;
import org.kie.internal.io.ResourceFactory;
import org.kie.internal.query.QueryFilter;
import org.kie.internal.runtime.conf.DeploymentDescriptor;
import org.kie.internal.runtime.conf.DeploymentDescriptorBuilder;
import org.kie.internal.runtime.conf.NamedObjectModel;
import org.kie.internal.runtime.conf.ObjectModel;
import org.kie.internal.runtime.conf.RuntimeStrategy;
import org.kie.internal.runtime.manager.deploy.DeploymentDescriptorImpl;
import org.kie.scanner.KieMavenRepository;
import org.kie.test.util.db.DataSourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.kie.scanner.KieMavenRepository.getKieMavenRepository;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CaseDynamicNodesTest {
    private static final Logger logger = LoggerFactory.getLogger(CaseDynamicNodesTest.class);
    private boolean configured = false;

    private CaseService caseService;

    private EntityManagerFactory emf;

    private TestIdentityProvider identityProvider;
    private CaseIdGenerator caseIdGenerator;
    private CaseRuntimeDataService caseRuntimeDataService;
    private RuntimeDataService runtimeDataService;
    private DeploymentService deploymentService;
    private DefinitionService bpmn2Service;
    private ProcessService processService;
    private AuthorizationManager authorizationManager;
    private QueryService queryService;
    private TaskService taskService;

    private static final String ARTIFACT_ID = "hr-hiring";
    private static final String GROUP_ID = "org.acme";
    private static final String VERSION = "1.0.0";

    private KModuleDeploymentUnit deploymentUnit;

    private List<String> listenerMvelDefinitions = new ArrayList<String>();

    @Before
    public void setUp() throws Exception {
        if(configured) return;
        configured = true;
        configureServices();

        KieServices ks = KieServices.Factory.get();
        ReleaseId releaseId = ks.newReleaseId(GROUP_ID, ARTIFACT_ID, VERSION);
        List<String> processes = getProcessDefinitionFiles();

        InternalKieModule kJar1 = createKieJar(ks, releaseId, processes);
        File pom = new File("target/kmodule", "pom.xml");
        pom.getParentFile().mkdirs();

        FileOutputStream fs = new FileOutputStream(pom);
        fs.write(getPom(releaseId).getBytes());
        fs.close();

        KieMavenRepository repository = getKieMavenRepository();
        repository.deployArtifact(releaseId, kJar1, pom);

        // use user name who is part of the case roles assignment
        // so (s)he will be authorized to access case instance
        identityProvider.setName("bob");
        String rolesArr[] = { "admin" };
        List<String> roles = new ArrayList<String>(Arrays.asList(rolesArr));
        identityProvider.setRoles(roles);

        prepareDeploymentUnit();
    }

    @Test
    public void testEndToEndEmptyCase() {
        Map<String, Object> data = new HashMap<>();
        // data.put("continue", false);
        String caseId = startCase(data);

        // Get the tasks.
        List<Long> taskIds = getTasksInCase(caseId, 1, 1);

        UserTaskInstanceDesc task = runtimeDataService.getTaskById(taskIds.get(0));
        assertEquals("Additional Notes on Rejection", task.getName());
        assertEquals(Status.Ready.toString(), task.getStatus());

        taskService.claim(taskIds.get(0), "test");
        List<Status> qStatusList = new ArrayList<>();
        qStatusList.add(Status.Reserved);
        List<TaskSummary> qTasks = taskService.getTasksOwnedByStatus("test", qStatusList, null);
        assertNotNull(qTasks);
        assertEquals(1, qTasks.size());
        assertEquals("test", qTasks.get(0).getActualOwnerId());
        assertEquals(Status.Reserved, qTasks.get(0).getStatus());

        taskService.start(taskIds.get(0), "test");
        qTasks = taskService.getTasksOwnedByStatus("test", qStatusList, null);
        assertEquals(0, qTasks.size());
        qStatusList.clear();
        qStatusList.add(Status.InProgress);
        qTasks = taskService.getTasksOwnedByStatus("test", qStatusList, null);
        assertNotNull(qTasks);
        assertEquals(1, qTasks.size());
        assertEquals("test", qTasks.get(0).getActualOwnerId());
        assertEquals(Status.InProgress, qTasks.get(0).getStatus());

        taskService.complete(taskIds.get(0), "test", data);
        qStatusList.add(Status.Ready);
        qStatusList.add(Status.Reserved);
        qTasks = taskService.getTasksOwnedByStatus("test", qStatusList, null);
        assertEquals(0, qTasks.size());
        

        List<ProcessInstanceDesc> processInstances = caseRuntimeDataService.getProcessInstancesForCase(caseId, new QueryFilter())
                .stream().collect(Collectors.toList());

        assertEquals(1, processInstances.size());
        assertNull(processInstances.get(0).getActiveTasks());
        // TODO: Case and Process instance are active, eventhough there are not active tasks.
        // assertCaseInstanceActive(caseId);
        // 2 = COMPLETED
        // assertEquals(2, processInstances.get(0).getState().intValue());
    }

    @Test
    public void testAddDynamicTaskToExternalApplication() {
        Map<String, Object> data = new HashMap<>();
        data.put("internalApplication", false);
        String caseId = startCase(data);

        // Get the tasks.
        List<Long> taskIds = getTasksInCase(caseId, 1, 1);

        UserTaskInstanceDesc task = runtimeDataService.getTaskById(taskIds.get(0));
        assertEquals("TA Application Review", task.getName());
        assertEquals(Status.Ready.toString(), task.getStatus());

        TaskSpecification taskSpecification = caseService.newHumanTaskSpec("Other Task (dynamic)", "description", "the_actor", "the_group", null);
        caseService.addDynamicTask(caseId, taskSpecification);

        taskIds = getTasksInCase(caseId, 1, 2);
        assertTrue("dynamic task not found", taskIds.stream().map(i -> {return runtimeDataService.getTaskById(i);}).anyMatch(p -> {return "Other Task (dynamic)".equals(p.getName());}));
    }

    @After
    public void tearDown() {
        List<CaseStatus> caseStatuses = Collections.singletonList(CaseStatus.OPEN);
        caseRuntimeDataService.getCaseInstances(caseStatuses, new QueryContext(0, Integer.MAX_VALUE))
                .forEach(caseInstance -> caseService.cancelCase(caseInstance.getCaseId()));
        
        identityProvider.reset();
        identityProvider.setRoles(new ArrayList<>());

        cleanupSingletonSessionId();

        if(deploymentUnit != null) {
            deploymentService.undeploy(deploymentUnit);
            deploymentUnit = null;
        }

        if (emf != null) {
            emf.close();
        }
        EntityManagerFactoryManager.get().clear();

    }

    private static void cleanupSingletonSessionId() {
        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        if (tempDir.exists()) {

            String[] jbpmSerFiles = tempDir.list(new FilenameFilter() {

                @Override
                public boolean accept(File dir, String name) {

                    return name.endsWith("-jbpmSessionId.ser");
                }
            });
            for (String file : jbpmSerFiles) {
                logger.debug("Temp dir to be removed {} file {}", tempDir, file);
                new File(tempDir, file).delete();
            }
        }
    }

    private String startCase(Map<String, Object> data) {
        List<ProcessDefinition> processDefs = runtimeDataService.getProcessesByDeploymentId("org.acme:hr-hiring:1.0.0", null)
                .stream().collect(Collectors.toList());
        assertNotNull(processDefs);
        Map<String, OrganizationalEntity> roleAssignments = new HashMap<>();
        roleAssignments.put("talent-acquisition", new GroupImpl("admin"));

        CaseFileInstance cfi = caseService.newCaseFileInstance("org.acme:hr-hiring:1.0.0",
                "hr-hiring.hiring-case-definition", data, roleAssignments);
        String caseId = caseService.startCase("org.acme:hr-hiring:1.0.0", "hr-hiring.hiring-case-definition", cfi);
        assertNotNull(caseId);
        assertCaseInstanceActive(caseId);
        return caseId;
    }

    private List<Long> getTasksInCase(String caseId, int expectedProcessInstances, int expectedTasks) {
        // Get the tasks.
        List<ProcessInstanceDesc> processInstances = caseRuntimeDataService.getProcessInstancesForCase(caseId, new QueryFilter())
                .stream().collect(Collectors.toList());

        assertEquals(expectedProcessInstances, processInstances.size());
        List<Long> taskIds = new ArrayList<>();
        processInstances.forEach(pid -> {taskIds.addAll(runtimeDataService.getTasksByProcessInstanceId(pid.getId()));});
        assertEquals(expectedTasks, taskIds.size());
        return taskIds;
    }

    private void assertCaseInstanceActive(String caseId) {
        try {
            CaseInstance caseInstance = caseService.getCaseInstance(caseId);
            assertNotNull(caseInstance);
            assertTrue("Case instance is not active", caseInstance.getStatus().equals(CaseStatus.OPEN.getId()));
        } catch (CaseNotFoundException ex) {
            fail("Case instance is not active");
        }
    }

    protected void assertTask(TaskSummary task, String actor, String name, Status status) {
        assertNotNull(task);
        assertEquals(name, task.getName());
        assertEquals(actor, task.getActualOwnerId());
        assertEquals(status, task.getStatus());
    }

    private void configureServices() {
        buildDatasource();
        // EMF required for case ID generator
        emf = EntityManagerFactoryManager.get().getOrCreate("org.jbpm.domain");
        identityProvider = new TestIdentityProvider();
        authorizationManager = new AuthorizationManagerImpl(identityProvider, new TransactionalCommandService(emf));
        // Case ID generator required for case service
        caseIdGenerator = new TableCaseIdGenerator(new TransactionalCommandService(emf));

        // build definition service
        bpmn2Service = new BPMN2DataServiceImpl();

        DeploymentRolesManager deploymentRolesManager = new DeploymentRolesManager();

        queryService = new QueryServiceImpl();
        ((QueryServiceImpl) queryService).setIdentityProvider(identityProvider);
        ((QueryServiceImpl) queryService).setCommandService(new TransactionalCommandService(emf));
        ((QueryServiceImpl) queryService).init();

        // build deployment service
        deploymentService = new KModuleDeploymentService();
        ((KModuleDeploymentService) deploymentService).setBpmn2Service(bpmn2Service);
        ((KModuleDeploymentService) deploymentService).setEmf(emf);
        ((KModuleDeploymentService) deploymentService).setIdentityProvider(identityProvider);
        ((KModuleDeploymentService) deploymentService).setManagerFactory(new RuntimeManagerFactoryImpl());
        ((KModuleDeploymentService) deploymentService).setFormManagerService(new FormManagerServiceImpl());

        HumanTaskConfigurator humanTaskConfigurator = HumanTaskServiceFactory.newTaskServiceConfigurator();
        humanTaskConfigurator.entityManagerFactory(emf);
        // TODO: Define restrictive roles access
        humanTaskConfigurator.userGroupCallback(new UserGroupCallback() {

            @Override
            public List<String> getGroupsForUser(String userId) {
                String[] groups = { "Administrators" };
                return Arrays.asList(groups);
            }

            @Override
            public boolean existsUser(String userId) {
                return true;
            }

            @Override
            public boolean existsGroup(String groupId) {
                return true;
            }
        });
        taskService = humanTaskConfigurator.getTaskService();

        // build runtime data service
        runtimeDataService = new RuntimeDataServiceImpl();
        ((RuntimeDataServiceImpl) runtimeDataService).setCommandService(new TransactionalCommandService(emf));
        ((RuntimeDataServiceImpl) runtimeDataService).setIdentityProvider(identityProvider);
        ((RuntimeDataServiceImpl) runtimeDataService).setTaskService(taskService);
        ((RuntimeDataServiceImpl) runtimeDataService).setDeploymentRolesManager(deploymentRolesManager);
        ((RuntimeDataServiceImpl) runtimeDataService).setTaskAuditService(TaskAuditServiceFactory.newTaskAuditServiceConfigurator().setTaskService(taskService).getTaskAuditService());
        ((KModuleDeploymentService) deploymentService).setRuntimeDataService(runtimeDataService);

        // build process service
        processService = new ProcessServiceImpl();
        ((ProcessServiceImpl) processService).setDataService(runtimeDataService);
        ((ProcessServiceImpl) processService).setDeploymentService(deploymentService);

        // build case runtime data service
        caseRuntimeDataService = new CaseRuntimeDataServiceImpl();
        ((CaseRuntimeDataServiceImpl) caseRuntimeDataService).setCaseIdGenerator(caseIdGenerator);
        ((CaseRuntimeDataServiceImpl) caseRuntimeDataService).setRuntimeDataService(runtimeDataService);
        ((CaseRuntimeDataServiceImpl) caseRuntimeDataService).setCommandService(new TransactionalCommandService(emf));
        ((CaseRuntimeDataServiceImpl) caseRuntimeDataService).setIdentityProvider(identityProvider);
        ((CaseRuntimeDataServiceImpl) caseRuntimeDataService).setDeploymentRolesManager(deploymentRolesManager);

        // build case service
        caseService = new CaseServiceImpl();
        ((CaseServiceImpl) caseService).setCaseIdGenerator(caseIdGenerator);
        ((CaseServiceImpl) caseService).setCaseRuntimeDataService(caseRuntimeDataService);
        ((CaseServiceImpl) caseService).setProcessService(processService);
        ((CaseServiceImpl) caseService).setDeploymentService(deploymentService);
        ((CaseServiceImpl) caseService).setRuntimeDataService(runtimeDataService);
        ((CaseServiceImpl) caseService).setCommandService(new TransactionalCommandService(emf));
        ((CaseServiceImpl) caseService).setAuthorizationManager(authorizationManager);
        ((CaseServiceImpl) caseService).setIdentityProvider(identityProvider);

        CaseConfigurationDeploymentListener configurationListener = new CaseConfigurationDeploymentListener(identityProvider);

        // set runtime data service as listener on deployment service
        ((KModuleDeploymentService) deploymentService).addListener((RuntimeDataServiceImpl) runtimeDataService);
        ((KModuleDeploymentService) deploymentService).addListener((BPMN2DataServiceImpl) bpmn2Service);
        ((KModuleDeploymentService) deploymentService).addListener((QueryServiceImpl) queryService);
        ((KModuleDeploymentService) deploymentService).addListener((CaseRuntimeDataServiceImpl) caseRuntimeDataService);
        ((KModuleDeploymentService) deploymentService).addListener(configurationListener);

    }

    private void buildDatasource() {
        Properties driverProperties = new Properties();
        driverProperties.put("user", "sa");
        driverProperties.put("password", "sa");
        driverProperties.put("url", "jdbc:h2:mem:mydb");
        driverProperties.put("driverClassName", "org.h2.Driver");
        driverProperties.put("className", "org.h2.jdbcx.JdbcDataSource");

        DataSourceFactory.setupPoolingDataSource("jdbc/testDS1", driverProperties);
    }

    private List<String> getProcessDefinitionFiles() {
        List<String> processes = new ArrayList<String>();
        processes.add("org/acme/hr_hiring/hiring-case-definition.bpmn");
        return processes;
    }

    private InternalKieModule createKieJar(KieServices ks, ReleaseId releaseId, List<String> resources) {
        return createKieJar(ks, releaseId, resources, null);
    }

    private InternalKieModule createKieJar(KieServices ks, ReleaseId releaseId, List<String> resources, Map<String, String> extraResources) {

        KieFileSystem kfs = createKieFileSystemWithKProject(ks);
        kfs.writePomXML(getPom(releaseId));

        DeploymentDescriptor customDescriptor = createDeploymentDescriptor();

        if (extraResources == null) {
            extraResources = new HashMap<String, String>();
        }
        extraResources.put("src/main/resources/" + DeploymentDescriptor.META_INF_LOCATION, customDescriptor.toXml());

        for (String resource : resources) {
            kfs.write("src/main/resources/KBase-test/" + resource, ResourceFactory.newClassPathResource(resource));
        }
        if (extraResources != null) {
            for (Map.Entry<String, String> entry : extraResources.entrySet()) {
                kfs.write(entry.getKey(), ResourceFactory.newByteArrayResource(entry.getValue().getBytes()));
            }
        }

        KieBuilder kieBuilder = ks.newKieBuilder(kfs);
        if (!kieBuilder.buildAll().getResults().getMessages().isEmpty()) {
            for (Message message : kieBuilder.buildAll().getResults().getMessages()) {
                logger.error("Error Message: ({}) {}", message.getPath(), message.getText());
            }
            throw new RuntimeException("There are errors builing the package, please check your knowledge assets!");
        }

        return (InternalKieModule) kieBuilder.getKieModule();
    }

    private KieFileSystem createKieFileSystemWithKProject(KieServices ks) {
        KieModuleModel kproj = ks.newKieModuleModel();

        KieBaseModel kieBaseModel1 = kproj.newKieBaseModel("KBase-test").setDefault(true).addPackage("*").setEqualsBehavior(EqualityBehaviorOption.EQUALITY).setEventProcessingMode(EventProcessingOption.STREAM);

        KieSessionModel ksessionModel = kieBaseModel1.newKieSessionModel("ksession-test");

        ksessionModel.setDefault(true).setType(KieSessionModel.KieSessionType.STATEFUL).setClockType(ClockTypeOption.get("realtime"));

        ksessionModel.newWorkItemHandlerModel("Log", "new org.jbpm.process.instance.impl.demo.SystemOutWorkItemHandler()");
        ksessionModel.newWorkItemHandlerModel("Service Task", "new org.jbpm.bpmn2.handler.ServiceTaskHandler(\"name\")");

        KieFileSystem kfs = ks.newKieFileSystem();
        kfs.writeKModuleXML(kproj.toXML());
        return kfs;
    }

    private String getPom(ReleaseId releaseId, ReleaseId... dependencies) {
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" + "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd\">\n" + "  <modelVersion>4.0.0</modelVersion>\n" + "\n" + "  <groupId>" + releaseId.getGroupId() + "</groupId>\n" + "  <artifactId>" + releaseId.getArtifactId() + "</artifactId>\n" + "  <version>" + releaseId.getVersion() + "</version>\n" + "\n";
        //ReleaseId cmiRid = ks.newReleaseId("org.jbpm", "jbpm-case-mgmt-impl", "7.18.0.Final-redhat-00004");
        pom += "<dependencies>\n";
        pom += "<dependency>\n";
        pom += "  <groupId>org.jbpm</groupId>\n";
        pom += "  <artifactId>jbpm-case-mgmt-impl</artifactId>\n";
        pom += "  <version>7.18.0.Final-redhat-00004</version>\n";
        pom += "</dependency>\n";
        if (dependencies != null && dependencies.length > 0) {
            for (ReleaseId dep : dependencies) {
                pom += "<dependency>\n";
                pom += "  <groupId>" + dep.getGroupId() + "</groupId>\n";
                pom += "  <artifactId>" + dep.getArtifactId() + "</artifactId>\n";
                pom += "  <version>" + dep.getVersion() + "</version>\n";
                pom += "</dependency>\n";
            }
        }
        pom += "</dependencies>\n";
        pom += "</project>";
        return pom;
    }

    private DeploymentDescriptor createDeploymentDescriptor() {
        //add this listener by default
        //listenerMvelDefinitions.add("new org.jbpm.casemgmt.impl.util.TrackingCaseEventListener()");

        DeploymentDescriptor customDescriptor = new DeploymentDescriptorImpl("org.jbpm.domain");
        DeploymentDescriptorBuilder ddBuilder = customDescriptor.getBuilder()
                .runtimeStrategy(RuntimeStrategy.PER_CASE)
                .addMarshalingStrategy(new ObjectModel("mvel", CaseMarshallerFactory.builder().withDoc().toString()))
                .addWorkItemHandler(new NamedObjectModel("mvel", "StartCaseInstance", "new org.jbpm.casemgmt.impl.wih.StartCaseWorkItemHandler(ksession)"));

        listenerMvelDefinitions.forEach(
                listenerDefinition -> ddBuilder.addEventListener(new ObjectModel("mvel", listenerDefinition))
        );

        getProcessListeners().forEach(
                listener -> ddBuilder.addEventListener(listener)
        );
        
        getWorkItemHandlers().forEach(
               listener -> ddBuilder.addWorkItemHandler(listener)
        );


        return customDescriptor;
    }

    private List<ObjectModel> getProcessListeners() {
        return new ArrayList<>();
    }
    
    private List<NamedObjectModel> getWorkItemHandlers() {
        return new ArrayList<>();
    }

    private DeploymentUnit prepareDeploymentUnit() {
        assertNotNull(deploymentService);
        deploymentUnit = new KModuleDeploymentUnit(GROUP_ID, ARTIFACT_ID, VERSION);

        final DeploymentDescriptor descriptor = new DeploymentDescriptorImpl();
        descriptor.getBuilder().addEventListener(new NamedObjectModel(
                "mvel",
                "processIdentity",
                "new org.jbpm.kie.services.impl.IdentityProviderAwareProcessListener(ksession)"
        ));
        deploymentUnit.setDeploymentDescriptor(descriptor);
        deploymentUnit.setStrategy(RuntimeStrategy.PER_CASE);

        deploymentService.deploy(deploymentUnit);
        return deploymentUnit;
    }
}