/*
 * Copyright 2011 DTO Solutions, Inc. (http://dtosolutions.com)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/*
* JschNodeExecutor.java
* 
* User: Greg Schueler <a href="mailto:greg@dtosolutions.com">greg@dtosolutions.com</a>
* Created: 3/21/11 4:46 PM
* 
*/
package com.dtolabs.rundeck.core.execution.impl.jsch;

import com.dtolabs.rundeck.core.Constants;
import com.dtolabs.rundeck.core.common.Framework;
import com.dtolabs.rundeck.core.common.FrameworkProject;
import com.dtolabs.rundeck.core.common.INodeEntry;
import com.dtolabs.rundeck.core.execution.ExecutionContext;
import com.dtolabs.rundeck.core.execution.ExecutionException;
import com.dtolabs.rundeck.core.execution.ExecutionListener;
import com.dtolabs.rundeck.core.execution.impl.common.AntSupport;
import com.dtolabs.rundeck.core.execution.service.NodeExecutor;
import com.dtolabs.rundeck.core.execution.service.NodeExecutorResult;
import com.dtolabs.rundeck.core.plugins.configuration.Describable;
import com.dtolabs.rundeck.core.plugins.configuration.Description;
import com.dtolabs.rundeck.core.plugins.configuration.Property;
import com.dtolabs.rundeck.core.tasks.net.ExtSSHExec;
import com.dtolabs.rundeck.core.tasks.net.SSHTaskBuilder;
import com.jcraft.jsch.JSchException;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;

import java.text.MessageFormat;
import java.util.*;

/**
 * JschNodeExecutor is ...
 *
 * @author Greg Schueler <a href="mailto:greg@dtosolutions.com">greg@dtosolutions.com</a>
 */
public class JschNodeExecutor implements NodeExecutor, Describable {
    public static final String SERVICE_PROVIDER_TYPE = "jsch-ssh";
    public static final String FWK_PROP_AUTH_CANCEL_MSG = "framework.messages.error.ssh.authcancel";
    public static final String FWK_PROP_AUTH_CANCEL_MSG_DEFAULT =
        "Authentication failure connecting to node: \"{0}\". Make sure your resource definitions and credentials are up to date.";
    public static final String NODE_ATTR_SSH_KEYPATH = "ssh-keypath";

    public static final String PROJ_PROP_PREFIX = "project.";
    public static final String FWK_PROP_PREFIX = "framework.";

    public static final String FWK_PROP_SSH_KEYPATH = FWK_PROP_PREFIX + NODE_ATTR_SSH_KEYPATH;
    public static final String PROJ_PROP_SSH_KEYPATH = PROJ_PROP_PREFIX + NODE_ATTR_SSH_KEYPATH;

    public static final String NODE_ATTR_SSH_AUTHENTICATION = "ssh-authentication";
    public static final String NODE_ATTR_SSH_PASSWORD_OPTION = "ssh-password-option";


    public static final String FWK_PROP_SSH_AUTHENTICATION = FWK_PROP_PREFIX + NODE_ATTR_SSH_AUTHENTICATION;
    public static final String PROJ_PROP_SSH_AUTHENTICATION = PROJ_PROP_PREFIX + NODE_ATTR_SSH_AUTHENTICATION;
    private Framework framework;

    public JschNodeExecutor(final Framework framework) {
        this.framework = framework;
    }

    static final List<Property> CONFIG_PROPERTIES = new ArrayList<Property>();
    static final Map<String, String> CONFIG_MAPPING;

    public static final String CONFIG_KEYPATH = "keypath";
    public static final String CONFIG_AUTHENTICATION = "authentication";

    static {
//        CONFIG_PROPERTIES.add(PropertyUtil.string(CONFIG_KEYPATH, "SSH Keypath",
//            "Path to a private SSH Key file, for use with SSH and SCP. Can be overridden by node attribute \""
//            + NODE_ATTR_SSH_KEYPATH + "\".", true, null));

        final Map<String, String> mapping = new HashMap<String, String>();
        mapping.put(CONFIG_KEYPATH, PROJ_PROP_SSH_KEYPATH);
        mapping.put(CONFIG_AUTHENTICATION, PROJ_PROP_SSH_AUTHENTICATION);
        CONFIG_MAPPING = Collections.unmodifiableMap(mapping);
    }


    static final Description DESC = new Description() {
        public String getName() {
            return SERVICE_PROVIDER_TYPE;
        }

        public String getTitle() {
            return "SSH";
        }

        public String getDescription() {
            return "Executes a command on a remote node via SSH.";
        }

        public List<Property> getProperties() {
            return CONFIG_PROPERTIES;
        }

        public Map<String, String> getPropertiesMapping() {
            return CONFIG_MAPPING;
        }
    };

    public Description getDescription() {
        return DESC;
    }

    public NodeExecutorResult executeCommand(final ExecutionContext context, final String[] command,
                                             final INodeEntry node) throws
        ExecutionException {
        if (null == node.getHostname() || null == node.extractHostname()) {
            throw new ExecutionException(
                "Hostname must be set to connect to remote node '" + node.getNodename() + "'");
        }
        if (null == node.extractUserName()) {
            throw new ExecutionException(
                "Username must be set to connect to remote node '" + node.getNodename() + "'");
        }


        final ExecutionListener listener = context.getExecutionListener();
        final Project project = new Project();
        AntSupport.addAntBuildListener(listener, project);

        boolean success = false;
        final ExtSSHExec sshexec;
        //perform jsch sssh command
        final NodeSSHConnectionInfo nodeAuthentication = new NodeSSHConnectionInfo(node, framework,
            context);
        final int timeout = nodeAuthentication.getSSHTimeout();
        try {

            sshexec = SSHTaskBuilder.build(node, command, project, context.getDataContext(),
                nodeAuthentication, context.getLoglevel());
        } catch (SSHTaskBuilder.BuilderException e) {
            throw new ExecutionException(e);
        }
        String errormsg = null;
        try {
            sshexec.execute();
            success = true;
        } catch (BuildException e) {
            if (e.getMessage().contains("Timeout period exceeded, connection dropped")) {
                errormsg =
                    "Failed execution for node: " + node.getNodename() + ": Execution Timeout period exceeded (after "
                    + timeout + "ms), connection dropped";
            } else if (null != e.getCause() && e.getCause() instanceof JSchException && (
                e.getCause().getMessage().contains("timeout:") || e.getCause().getMessage().contains(
                    "SocketTimeoutException") || e.getCause().getMessage().contains(
                    "java.net.ConnectException: Operation timed out"))) {
                errormsg = "Failed execution for node: " + node.getNodename() + ": Connection Timeout (after " + timeout
                           + "ms): " + e.getMessage();
            } else if (null != e.getCause() && e.getCause() instanceof JSchException && e.getCause().getMessage()
                .contains("Auth cancel")) {
                String msgformat = FWK_PROP_AUTH_CANCEL_MSG_DEFAULT;
                if (framework.getPropertyLookup().hasProperty(FWK_PROP_AUTH_CANCEL_MSG)) {
                    msgformat = framework.getProperty(FWK_PROP_AUTH_CANCEL_MSG);
                }
                errormsg = MessageFormat.format(msgformat, node.getNodename(),
                    e.getMessage());
            } else {
                errormsg = e.getMessage();
            }
            context.getExecutionListener().log(0, errormsg);
        }
        final int resultCode = sshexec.getExitStatus();
        final boolean status = success;
        final String resultmsg = null != errormsg ? errormsg : null;

        return new NodeExecutorResult() {
            public int getResultCode() {
                return resultCode;
            }

            public boolean isSuccess() {
                return status;
            }

            @Override
            public String toString() {
                return "[jsch-ssh] result was " + (isSuccess() ? "success" : "failure") + ", resultcode: "
                       + getResultCode() + (null != resultmsg ? ": " + resultmsg : "");

            }
        };
    }


    final static class NodeSSHConnectionInfo implements SSHTaskBuilder.SSHConnectionInfo {
        final INodeEntry node;
        final Framework framework;
        final ExecutionContext context;
        FrameworkProject frameworkProject;

        NodeSSHConnectionInfo(final INodeEntry node, final Framework framework, final ExecutionContext context) {

            this.node = node;
            this.framework = framework;
            this.context = context;
            this.frameworkProject = framework.getFrameworkProjectMgr().getFrameworkProject(
                context.getFrameworkProject());
        }

        public SSHTaskBuilder.AuthenticationType getAuthenticationType() {
            if (null != node.getAttributes().get(NODE_ATTR_SSH_AUTHENTICATION)) {
                return SSHTaskBuilder.AuthenticationType.valueOf(node.getAttributes().get(
                    NODE_ATTR_SSH_AUTHENTICATION));
            }

            if (frameworkProject.hasProperty(PROJ_PROP_SSH_AUTHENTICATION)) {
                return SSHTaskBuilder.AuthenticationType.valueOf(frameworkProject.getProperty(
                    PROJ_PROP_SSH_AUTHENTICATION));
            } else if (framework.hasProperty(FWK_PROP_SSH_AUTHENTICATION)) {
                return SSHTaskBuilder.AuthenticationType.valueOf(framework.getProperty(FWK_PROP_SSH_AUTHENTICATION));
            } else {
                return SSHTaskBuilder.AuthenticationType.privateKey;
            }
        }

        public String getPrivateKeyfilePath() {
            if (null != node.getAttributes().get(NODE_ATTR_SSH_KEYPATH)) {
                return node.getAttributes().get(NODE_ATTR_SSH_KEYPATH);
            }

            if (frameworkProject.hasProperty(PROJ_PROP_SSH_KEYPATH)) {
                return frameworkProject.getProperty(PROJ_PROP_SSH_KEYPATH);
            } else if (framework.hasProperty(FWK_PROP_SSH_KEYPATH)) {
                return framework.getProperty(FWK_PROP_SSH_KEYPATH);
            } else {
                //return default framework level
                return framework.getProperty(Constants.SSH_KEYPATH_PROP);
            }
        }

        private String evaluateOption(final String optionName) {
            final Map<String, String> option = context.getDataContext().get("option");
            if (null != option) {
                return option.get(optionName);
            } else {
                return null;
            }
        }


        public String getPassword() {
            if (null != node.getAttributes().get(NODE_ATTR_SSH_PASSWORD_OPTION)) {
                return evaluateOption(node.getAttributes().get(NODE_ATTR_SSH_PASSWORD_OPTION));
            } else {
                return null;
            }
        }

        public int getSSHTimeout() {
            int timeout = 0;
            if (framework.getPropertyLookup().hasProperty(Constants.SSH_TIMEOUT_PROP)) {
                final String val = framework.getProperty(Constants.SSH_TIMEOUT_PROP);
                try {
                    timeout = Integer.parseInt(val);
                } catch (NumberFormatException e) {
                }
            }
            return timeout;
        }

        public String getUsername() {
            if (null != node.getUsername() || node.containsUserName()) {
                return node.extractUserName();
            } else if (frameworkProject.hasProperty("project.ssh.user")
                       && !"".equals(frameworkProject.getProperty("project.ssh.user").trim())) {
                return frameworkProject.getProperty("project.ssh.user").trim();
            } else {
                return framework.getProperty(Constants.SSH_USER_PROP);
            }
        }
    }


}
