package com.xebialabs.deployit.ci;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.jvnet.localizer.Localizable;
import org.kohsuke.stapler.DataBoundConstructor;
import com.google.common.base.Strings;

import com.xebialabs.deployit.ci.dar.RemoteLookup;
import com.xebialabs.deployit.ci.util.DeployitTypes;
import com.xebialabs.deployit.ci.util.FileFinder;
import com.xebialabs.deployit.ci.util.JenkinsDeploymentListener;
import com.xebialabs.deployit.engine.packager.content.DarMember;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.util.IOException2;
import hudson.util.ListBoxModel;

import static com.xebialabs.deployit.ci.util.ListBoxModels.of;
import static java.lang.String.format;

public class ArtifactView extends DeployableView {

    public String location;

    @DataBoundConstructor
    public ArtifactView(String type, String name, String location, String tags, List<NameValuePair> properties) {
        super(type, name, tags, properties);
        this.location = location;
    }

    @Override
    public DarMember newDarMember(DeployitTypes deployitTypes, FilePath workspace, EnvVars envVars, JenkinsDeploymentListener listener) {
        final DarMember deployable = super.newDarMember(deployitTypes, workspace, envVars, listener);
        String resolvedLocation = getResolvedLocation(envVars);
        try {
            final File file = findFileFromPattern(resolvedLocation, workspace, listener);
            deployable.setLocation(file);
            return deployable;
        } catch (IOException e) {
            throw new RuntimeException(String.format("Unable to find artifact for deployable '%s' in '%s'", getName(), resolvedLocation), e);
        }
    }

    private String getResolvedLocation(EnvVars envVars) {
        if (Strings.emptyToNull(location) == null) {
            throw new RuntimeException(String.format("No location specified for '%s' of type '%s'", getName(), getType()));
        }
        return envVars.expand(location);
    }

    static File findFileFromPattern(String pattern, FilePath workspace, JenkinsDeploymentListener listener) throws IOException {
        listener.info(String.format("Searching for '%s' in '%s'", pattern, workspace));
        FileFinder fileFinder = new FileFinder(pattern);
        List<String> fileNames;
        try {
            fileNames = workspace.act(fileFinder);
        } catch (InterruptedException exception) {
            throw new IOException(format("Interrupted while searching for '%s' in '%s'", pattern, workspace), exception);
        }
        listener.info("Found file(s): " + fileNames);
        if (fileNames.size() > 1) {
            final Localizable localizable = Messages._DeployitNotifier_TooManyFilesMatchingPattern();
            listener.error(localizable);
            throw new RuntimeException(String.valueOf(localizable));
        } else if (fileNames.size() == 0) {
            final Localizable localizable = Messages._DeployitNotifier_noArtifactsFound(pattern);
            listener.error(localizable);
            throw new RuntimeException(String.valueOf(localizable));
        }
        // so we use only the first found
        final String artifactPath = fileNames.get(0);
        return fetchFile(artifactPath, workspace);
    }

    private static File fetchFile(String artifactPath, FilePath workspace) throws IOException {
        try {
            return workspace.getChannel().call(new RemoteLookup(artifactPath, workspace.getRemote()));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Extension
    public static final class DescriptorImpl extends DeployableViewDescriptor {

        @Override
        public String getDisplayName() {
            return "Artifact";
        }

        public ListBoxModel doFillTypeItems() {
            return of(getDeployitDescriptor().getAllArtifactTypes());
        }
    }

}
