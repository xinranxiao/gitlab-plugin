package com.dabsquared.gitlabjenkins.connection;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.AbstractIdCredentialsListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.dabsquared.gitlabjenkins.gitlab.GitLabClientBuilder;
import com.dabsquared.gitlabjenkins.gitlab.api.GitLabApi;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Robin Müller
 */
@Extension
public class GitLabConnectionConfig extends GlobalConfiguration {

    private List<GitLabConnection> connections = new ArrayList<>();
    private transient Map<String, GitLabConnection> connectionMap = new HashMap<>();
    private transient Map<String, GitLabApi> clients = new HashMap<>();

    public GitLabConnectionConfig() {
        load();
        refreshConnectionMap();
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        connections = req.bindJSONToList(GitLabConnection.class, json.get("connections"));
        refreshConnectionMap();
        clients.clear();
        save();
        return super.configure(req, json);
    }

    public List<GitLabConnection> getConnections() {
        return connections;
    }

    public void addConnection(GitLabConnection connection) {
        connections.add(connection);
        connectionMap.put(connection.getName(), connection);
    }

    public GitLabApi getClient(String connectionName) {
        if (!clients.containsKey(connectionName) && connectionMap.containsKey(connectionName)) {
            clients.put(connectionName, GitLabClientBuilder.buildClient(connectionMap.get(connectionName)));
        }
        return clients.get(connectionName);
    }

    public FormValidation doCheckName(@QueryParameter String id, @QueryParameter String value) {
        if (value == null || value.isEmpty()) {
            return FormValidation.error(Messages.name_required());
        } else if (connectionMap.containsKey(value) && !connectionMap.get(value).toString().equals(id)) {
            return FormValidation.error(Messages.name_exists(value));
        } else {
            return FormValidation.ok();
        }
    }

    public FormValidation doCheckUrl(@QueryParameter String value) {
        if (value == null || value.isEmpty()) {
            return FormValidation.error(Messages.url_required());
        } else {
            return FormValidation.ok();
        }
    }

    // TODO check why this gets called twice on page load once with the correct id and once with an empty string
    public FormValidation doCheckApiTokenId(@QueryParameter String value) {
        if (value == null || value.isEmpty()) {
            return FormValidation.error(Messages.apiToken_required());
        } else {
            return FormValidation.ok();
        }
    }

    public FormValidation doTestConnection(@QueryParameter String url, @QueryParameter String apiTokenId, @QueryParameter boolean ignoreCertificateErrors) {
        try {
            GitLabClientBuilder.buildClient(url, apiTokenId, ignoreCertificateErrors).headCurrentUser();
            return FormValidation.ok(Messages.connection_success());
        } catch (WebApplicationException e) {
            return FormValidation.error(Messages.connection_error(e.getMessage()));
        } catch (ProcessingException e) {
            return FormValidation.error(Messages.connection_error(e.getCause().getMessage()));
        }
    }

    public ListBoxModel doFillApiTokenIdItems(@QueryParameter String name) {
        if (Jenkins.getInstance().hasPermission(Item.CONFIGURE)) {
            AbstractIdCredentialsListBoxModel<StandardListBoxModel, StandardCredentials> options = new StandardListBoxModel()
                .withEmptySelection()
                .withMatching(
                    new GitLabCredentialMatcher(),
                    CredentialsProvider.lookupCredentials(
                        StandardCredentials.class, Jenkins.getInstance(), ACL.SYSTEM, new ArrayList<DomainRequirement>()
                    )
                );
            if (name != null && connectionMap.containsKey(name)) {
                String apiTokenId = connectionMap.get(name).getApiTokenId();
                for (ListBoxModel.Option option : options) {
                    if (option.value.equals(apiTokenId)) {
                        option.selected = true;
                    }
                }
            }
            return options;
        }

        return new StandardListBoxModel();
    }

    private void refreshConnectionMap() {
        connectionMap.clear();
        for (GitLabConnection connection : connections) {
            connectionMap.put(connection.getName(), connection);
        }
    }

    private static class GitLabCredentialMatcher implements CredentialsMatcher {
        @Override
        public boolean matches(@NonNull Credentials credentials) {
            return credentials instanceof StringCredentials;
        }
    }
}
