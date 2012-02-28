package com.marklogic.maven;

import static com.marklogic.xcc.ContentFactory.newContent;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.shared.model.fileset.util.FileSetManager;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.PlexusConfigurationException;

import com.marklogic.xcc.AdhocQuery;
import com.marklogic.xcc.Content;
import com.marklogic.xcc.ContentCapability;
import com.marklogic.xcc.ContentCreateOptions;
import com.marklogic.xcc.ContentPermission;
import com.marklogic.xcc.DocumentFormat;
import com.marklogic.xcc.Session;
import com.marklogic.xcc.exceptions.RequestException;

/**
 * @author Bob Browning <bob.browning@pressassociation.com>
 */
public abstract class AbstractInstallMojo extends AbstractDeploymentMojo {

	protected static final String ACTION_INSTALL_CONTENT = "install-content";
	protected static final String ACTION_INSTALL_CPF = "install-cpf";
	protected static final String ACTION_INSTALL_DATABASES = "install-databases";
	protected static final String ACTION_INSTALL_TRIGGERS = "install-triggers";
	protected static final String ACTION_INSTALL_SERVERS = "install-servers";

	protected void installContent() throws MojoExecutionException {
		executeAction(ACTION_INSTALL_CONTENT);

		if (getCurrentEnvironment().getResources() != null) {
			/*
			 * Install content resources from maven project
			 */
			installResources(getCurrentEnvironment().getResources());
		}
	}

	protected void installCPF() throws MojoExecutionException {
		if (getCurrentEnvironment().getPipelineResources() != null) {
			/*
			 * Install pipeline resources from maven project
			 */
			installPipeline(getCurrentEnvironment().getPipelineResources());
		}

		executeAction(ACTION_INSTALL_CPF);
	}

	protected void installDatabases() throws MojoExecutionException {
		executeAction(ACTION_INSTALL_DATABASES);
	}

	protected void installTriggers() throws MojoExecutionException {
		executeAction(ACTION_INSTALL_TRIGGERS);
	}

	protected void installServers() throws MojoExecutionException {
		executeAction(ACTION_INSTALL_SERVERS);
	}

	private void installPipeline(ResourceFileSet[] resources) {
		try {
			FileSetManager manager = new FileSetManager();

			for (ResourceFileSet resource : resources) {
				final String targetDatabase = getCurrentEnvironment()
						.getApplicationName() + "-" + resource.getDatabase();
				getLog().info(" -- ".concat(targetDatabase).concat(" -- "));

				/*
				 * Get connection to database for uploading content
				 */
				Session session = getSession(targetDatabase);

				AdhocQuery query = session
						.newAdhocQuery("(::)\n"
								+ "xquery version \"1.0-ml\";\n"
								+ "import module namespace p = \"http://marklogic.com/cpf/pipelines\" at \"/MarkLogic/cpf/pipelines.xqy\";\n"
								+ "declare variable $file as xs:string external; \n"
								+ "p:insert( xdmp:unquote($file)/* )\n"
								+ "(::)");

				for (String f : manager.getIncludedFiles(resource)) {
					File sourceFile = new File(resource.getDirectory(), f);
					getLog().info(
							String.format("Loading pipeline configuration %s",
									sourceFile.getPath()));
					try {
						query.setNewStringVariable("file",
								getFileAsString(sourceFile));
						session.submitRequest(query);
					} catch (IOException e) {
						getLog().error(
								"Failed to read pipeline file ".concat(f), e);
					} catch (RequestException e) {
						getLog().error(
								"Failed to insert pipeline file ".concat(f), e);
					}
				}
			}
		} finally {
			for (Map.Entry<String, Session> e : sessions.entrySet()) {
				e.getValue().close();
			}
			sessions = new HashMap<String, Session>();
		}
	}

	private void installResources(ResourceFileSet[] resources) {
		try {
			FileSetManager manager = new FileSetManager();

			for (ResourceFileSet resource : resources) {
				final String targetDatabase = getCurrentEnvironment()
						.getApplicationName() + "-" + resource.getDatabase();
				getLog().info(" -- ".concat(targetDatabase).concat(" -- "));

				/*
				 * Get connection to database for uploading content
				 */
				Session session = getSession(targetDatabase);

				ContentCreateOptions options = null;
				options = new ContentCreateOptions();

				String[] collections = resource.getCollections();
				if (collections != null && collections.length > 0) {
					options.setCollections(collections);
				}

				Permission[] permissions = resource.getPermissions();
				if (permissions != null && permissions.length > 0) {
					ContentPermission[] contentPermissions = new ContentPermission[permissions.length];
					int i = 0;
					for (Permission permission : permissions) {
						String capability = permission.getCapability();
						ContentCapability contentCapability = null;

						if (capability.equals("execute"))
							contentCapability = ContentCapability.EXECUTE;
						else if (capability.equals("read"))
							contentCapability = ContentCapability.READ;
						else if (capability.equals("insert"))
							contentCapability = ContentCapability.INSERT;
						else if (capability.equals("update"))
							contentCapability = ContentCapability.UPDATE;
						else
							throw new RuntimeException(
									"Unknown permission capability for role '"
											+ permission.getRole() + "' : "
											+ capability);

						ContentPermission contentPermission = new ContentPermission(
								contentCapability, permission.getRole());
						contentPermissions[i++] = contentPermission;
					}
					options.setPermissions(contentPermissions);
				}

				String format = resource.getFormat();
				if (format != null && format.length() > 0) {
					if (format.equals("xml"))
						options.setFormat(DocumentFormat.XML);
					else if (format.equals("text"))
						options.setFormat(DocumentFormat.TEXT);
					else if (format.equals("binary"))
						options.setFormat(DocumentFormat.BINARY);
					else if (format.equals("none"))
						options.setFormat(DocumentFormat.NONE);
					else
						throw new RuntimeException("Unknown format: " + format);
				}

				for (String f : manager.getIncludedFiles(resource)) {
					File sourceFile = new File(resource.getDirectory(), f);
					File destinationFile = new File(
							resource.getOutputDirectory(), f);

					String destinationPath = destinationFile.getPath().replace(
							File.separatorChar, '/');
					getLog().info(
							String.format("Deploying %s to %s",
									sourceFile.getPath(), destinationPath));
					try {

						Content c = newContent(destinationPath,
								getFileAsString(sourceFile), options);
						session.insertContent(c);
					} catch (IOException e) {
						getLog().error(
								"Failed to read content file ".concat(f), e);
					} catch (RequestException e) {
						getLog().error(
								"Failed to insert content file ".concat(f), e);
					}
				}
			}
		} finally {
			for (Map.Entry<String, Session> e : sessions.entrySet()) {
				e.getValue().close();
			}
			sessions = new HashMap<String, Session>();
		}
	}
	
	 protected void invokeModules() 
	    {
	    	PlexusConfiguration invokes = getCurrentEnvironment().getModuleInvokes();
	    	
	    	if (invokes == null || invokes.getChildCount() == 0)
	    		return;
	    	
			String appName = getCurrentEnvironment().getApplicationName();

			for (PlexusConfiguration config : invokes.getChildren() )
	    	{
				try
				{
					String modulePath = config.getChild("module").getValue();
					String serverName = config.getChild("server").getValue();
					
					PlexusConfiguration server = getServer(serverName);
			
					String database = appName + "-" + server.getAttribute("database");
					int port = Integer.parseInt( server.getAttribute("port") );

					getLog().info("-------------------------------------------------------------------- ");
					getLog().info("Invoking module on " + database);
					getLog().info("-------------------------------------------------------------------- ");
					getLog().info("Connecting to : " + host + " : " + port );
					getLog().info("  Module Path : " + modulePath);
					
					Session session = getXccSession(database, port);
					session.submitRequest( session.newModuleInvoke(modulePath) );
				} 
				catch (PlexusConfigurationException e)
				{
					e.printStackTrace();
				} 
				catch (RequestException e)
				{
					e.printStackTrace();
				}
	    	}
	    }

}
