package io.github.fvarrui.javapackager.maven;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import io.github.fvarrui.javapackager.model.FileAssociation;
import io.github.fvarrui.javapackager.model.Manifest;
import io.github.fvarrui.javapackager.model.Platform;
import io.github.fvarrui.javapackager.model.Scripts;
import io.github.fvarrui.javapackager.model.WindowsConfig;
import io.github.fvarrui.javapackager.packagers.Context;
import io.github.fvarrui.javapackager.packagers.PackagerFactory;
import io.github.fvarrui.javapackager.packagers.WindowsPackager;

/**
 * Create Windows application mojo  
 */
@Mojo(name = "create-windows-app", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution = ResolutionScope.RUNTIME)
public class CreateWindowsAppMojo extends AbstractMojo {
	
	// maven components
	
	@Parameter(defaultValue = "${project}", readonly = true)
	private MavenProject mavenProject;

	@Parameter(defaultValue = "${session}", readonly = true)
	private MavenSession mavenSession;

	@Component
	private BuildPluginManager pluginManager;
	
	// plugin parameters
	
	/**
	 * Output directory.
	 */
	@Parameter(defaultValue = "${project.build.directory}", property = "outputDirectory", required = false)
	private File outputDirectory;

	/**
	 * Path to project license file.
	 */
	@Parameter(property = "licenseFile", required = false)
	private File licenseFile;

	/**
	 * Path to the app icon file (PNG, ICO or ICNS).
	 */
	@Parameter(property = "iconFile", required = false)
	private File iconFile;

	/**
	 * Full path to your app main class.
	 */
	@Parameter(defaultValue = "${exec.mainClass}", property = "mainClass", required = true)
	private String mainClass;

	/**
	 * App name.
	 */
	@Parameter(defaultValue = "${project.name}", property = "name", required = false)
	private String name;

	/**
	 * App name to show.
	 */
	@Parameter(defaultValue = "${project.name}", property = "displayName", required = false)
	private String displayName;

	/**
	 * Project version.
	 */
	@Parameter(defaultValue = "${project.version}", property = "version", required = false)
	private String version;

	/**
	 * Project description.
	 */
	@Parameter(defaultValue = "${project.description}", property = "description", required = false)
	private String description;

	/**
	 * App website URL.
	 */
	@Parameter(defaultValue = "${project.url}", property = "url", required = false)
	private String url;

	/**
	 * App will run as administrator (with elevated privileges).
	 */
	@Parameter(defaultValue = "false", property = "administratorRequired", required = false)
	private Boolean administratorRequired;

	/**
	 * Organization name.
	 */
	@Parameter(defaultValue = "${project.organization.name}", property = "organizationName", required = false)
	private String organizationName;

	/**
	 * Organization website URL.
	 */
	@Parameter(defaultValue = "${project.organization.url}", property = "organizationUrl", required = false)
	private String organizationUrl;

	/**
	 * Organization email.
	 */
	@Parameter(defaultValue = "", property = "organizationEmail", required = false)
	private String organizationEmail;

	/**
	 * Embeds a customized JRE with the app.
	 */
	@Parameter(defaultValue = "false", property = "bundleJre", required = false)
	private Boolean bundleJre;
	
	/**
	 * Generates a customized JRE, including only identified or specified modules. Otherwise, all modules will be included.
	 */
	@Parameter(defaultValue = "true", property = "customizedJre", required = false)
	private Boolean customizedJre;

	/**
	 * Path to JRE folder. If specified, it will bundle this JRE with the app, and won't generate a customized JRE. For Java 8 version or least.
	 */
	@Parameter(property = "jrePath", required = false)
	private File jrePath;

	/**
	 * Path to JDK folder. If specified, it will use this JDK modules to generate a customized JRE. Allows generating JREs for different platforms.
	 */
	@Parameter(property = "jdkPath", required = false)
	private File jdkPath;

	/**
	 * Additional files and folders to include in the bundled app.
	 */
	@Parameter(property = "additionalResources", required = false)
	private List<File> additionalResources;

	/**
	 * Defines modules to customize the bundled JRE. Don't use jdeps to get module dependencies.
	 */
	@Parameter(property = "modules", required = false)
	private List<String> modules;

	/**
	 * Additional modules to the ones identified by jdeps or the specified with modules property.
	 */
	@Parameter(property = "additionalModules", required = false)
	private List<String> additionalModules;

    /**
	 * Additional arguments to provide to the JVM (for example <tt>-Xmx2G</tt>).
	 */	
	@Parameter(property = "vmArgs", required = false)
	private List<String> vmArgs;
	
	/**
	 * Provide your own runnable .jar (for example, a shaded .jar) instead of letting this plugin create one via
	 * the <tt>maven-jar-plugin</tt>.
	 */
    @Parameter(property = "runnableJar", required = false)
    private File runnableJar;

    /**
     * Whether or not to copy dependencies into the bundle. Generally, you will only disable this if you specified
     * a <tt>runnableJar</tt> with all dependencies shaded into the .jar itself. 
     */
    @Parameter(defaultValue = "true", property = "copyDependencies", required = true)
    private Boolean copyDependencies;
    
	/**
	 * Bundled JRE directory name
	 */
	@Parameter(defaultValue = "jre", property = "jreDirectoryName", required = false)
	private String jreDirectoryName;

	/**
	 * Windows specific config
	 */
	@Parameter(property = "winConfig", required = false)
	private WindowsConfig winConfig;

	/**
	 * Extra properties for customized Velocity templates, accesible through '$this.extra' map. 
	 */
	@Parameter(required = false)
	private Map<String, String> extra;
	
	/**
	 * Uses app resources folder as default working directory.
	 */
	@Parameter(defaultValue = "true", property = "useResourcesAsWorkingDir", required = false)
	private boolean useResourcesAsWorkingDir;
	
	/**
	 * Assets directory
	 */
	@Parameter(defaultValue = "${project.basedir}/assets", property = "assetsDir", required = false)
	private File assetsDir;
	
	/**
	 * Classpath
	 */
	@Parameter(property = "classpath", required = false)
	private String classpath;

	/**
	 * JRE min version
	 */
	@Parameter(property = "jreMinVersion", required = false)
	private String jreMinVersion;
	
	/**
	 * Additional JAR manifest entries  
	 */
	@Parameter(required = false)
	private Manifest manifest;
	
	/**
	 * Additional module paths
	 */
	@Parameter(property = "additionalModulePaths", required = false)
	private List<File> additionalModulePaths;
	
	/**
	 * Packaging JDK
	 */
	@Parameter(defaultValue = "${java.home}", property = "packagingJdk", required = false)
	private File packagingJdk;
	
	/**
	 * Additional module paths
	 */
	@Parameter(property = "fileAssociations", required = false)
	private List<FileAssociation> fileAssociations;
	
	/**
	 * Windows bootstrap script
	 */
	@Parameter(property = "bootstrap", required = false)
	private File bootstrap;
	
	public void execute() throws MojoExecutionException {
		
		Context.setContext(
				new MavenContext(
						executionEnvironment(mavenProject, mavenSession, pluginManager), 
						getLog()
						)
				);
		
		try {

			WindowsPackager packager = (WindowsPackager) PackagerFactory.createPackager(Platform.windows) 				
						.additionalModules(additionalModules)
						.additionalModulePaths(additionalModulePaths)
						.additionalResources(additionalResources)
						.administratorRequired(administratorRequired)
						.assetsDir(assetsDir)
						.bundleJre(bundleJre)
						.classpath(classpath)
						.copyDependencies(copyDependencies)
						.customizedJre(customizedJre)
						.description(description)
						.displayName(displayName)
						.extra(extra)
						.fileAssociations(fileAssociations)
						.iconFile(iconFile)
						.jdkPath(jdkPath)
						.jreDirectoryName(jreDirectoryName)
						.jreMinVersion(jreMinVersion)
						.jrePath(jrePath)
						.licenseFile(licenseFile)
						.mainClass(mainClass)
						.manifest(manifest)
						.modules(modules)
						.name(defaultIfBlank(name, Context.getMavenContext().getEnv().getMavenProject().getArtifactId()))
						.organizationEmail(organizationEmail)
						.organizationName(organizationName)
						.organizationUrl(organizationUrl)
						.outputDirectory(outputDirectory)
						.packagingJdk(packagingJdk)
						.runnableJar(runnableJar)
						.scripts(new Scripts(bootstrap))
						.useResourcesAsWorkingDir(useResourcesAsWorkingDir)
						.url(url)
						.version(version)
						.vmArgs(vmArgs)
						.winConfig(winConfig);
			
			// generate app, installers and bundles
			packager.createApp();
			
		} catch (Exception e) {

			throw new MojoExecutionException(e.getMessage(), e);
			
		}
		

	}

	
}
