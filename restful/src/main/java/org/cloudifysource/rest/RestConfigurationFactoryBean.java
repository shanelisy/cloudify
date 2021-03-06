/*******************************************************************************
 * Copyright (c) 2013 GigaSpaces Technologies Ltd. All rights reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *******************************************************************************/
package org.cloudifysource.rest;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import org.cloudifysource.dsl.cloud.Cloud;
import org.cloudifysource.dsl.cloud.compute.CloudCompute;
import org.cloudifysource.dsl.cloud.compute.ComputeTemplate;
import org.cloudifysource.dsl.internal.CloudifyConstants;
import org.cloudifysource.dsl.internal.ComputeTemplatesReader;
import org.cloudifysource.dsl.internal.DSLException;
import org.cloudifysource.dsl.internal.ServiceReader;
import org.cloudifysource.dsl.internal.packaging.CloudConfigurationHolder;
import org.openspaces.admin.Admin;
import org.openspaces.core.GigaSpace;
import org.openspaces.core.context.GigaSpaceContext;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 *
 * @author yael
 * @since 2.6.0
 *
 */
@Component
public class RestConfigurationFactoryBean implements FactoryBean<RestConfiguration> {

    private static final Logger logger = Logger.getLogger(RestConfigurationFactoryBean.class.getName());

    private RestConfiguration config;

    @GigaSpaceContext(name = "gigaSpace")
    private GigaSpace gigaSpace;

    @Autowired(required = true)
    protected Admin admin;

    @Override
    public RestConfiguration getObject() throws Exception {
        config = new RestConfiguration();
        initRestConfiguration();
        return config;
    }

    /**
     * Initialize all needed fields in RestConfiguration.
     */
    public void initRestConfiguration() {
        logger.info("Initializing service controller cloud configuration");
        Cloud cloud = readCloud();
        config.setAdmin(admin);
        if (cloud != null) {
        	config.setCloud(cloud);
            initCloudTemplates();
            CloudCompute cloudCompute = cloud.getCloudCompute();
            if (cloudCompute.getTemplates().isEmpty()) {
                throw new IllegalArgumentException("No templates defined in cloud configuration!");
            }
            String defaultTemplateName = cloudCompute.getTemplates().keySet().iterator().next();
            logger.info("Setting default template name to: " + defaultTemplateName
                    + ". This template will be used for services that do not specify an explicit template");
            config.setDefaultTemplateName(defaultTemplateName);
            String managementTemplateName = cloud.getConfiguration().getManagementMachineTemplate();
            config.setManagementTemplateName(managementTemplateName);
            config.setManagementTemplate(cloudCompute.getTemplates().get(managementTemplateName));
            config.setTemporaryFolderPath(CloudifyConstants.REST_FOLDER);
        } else {
            logger.info("Service Controller is running in local cloud mode");
        }
    }

    private Cloud readCloud() {
        logger.info("Loading cloud configuration");

        CloudConfigurationHolder cloudConfigurationHolder = getCloudConfigurationFromManagementSpace();
        logger.info("Cloud Configuration: " + cloudConfigurationHolder);
        final String cloudConfigurationFilePath = cloudConfigurationHolder.getCloudConfigurationFilePath();
        if (cloudConfigurationFilePath == null) {
            // must be local cloud or azure
            return null;
        }
        Cloud cloud = null;
        try {
            final File cloudConfigurationFile = new File(cloudConfigurationFilePath);
            File cloudConfigurationDir = cloudConfigurationFile.getParentFile();
            cloud = ServiceReader.readCloud(cloudConfigurationFile);
            config.setCloudConfigurationDir(cloudConfigurationDir);
            config.setCloudConfigurationHolder(cloudConfigurationHolder);
        } catch (final DSLException e) {
            throw new IllegalArgumentException(
                    "Failed to read cloud configuration file: " + cloudConfigurationHolder
                            + ". Error was: " + e.getMessage(), e);
        } catch (final IOException e) {
            throw new IllegalArgumentException(
                    "Failed to read cloud configuration file: " + cloudConfigurationHolder
                            + ". Error was: " + e.getMessage(), e);
        }
        logger.info("Successfully loaded cloud configuration file from management space");
        return cloud;
    }

    private void initCloudTemplates() {
        final File additionalTemplatesFolder = new File(config.getCloudConfigurationDir(),
                CloudifyConstants.ADDITIONAL_TEMPLATES_FOLDER_NAME);
        logger.info("initCloudTemplates - Adding templates from folder: "
                + additionalTemplatesFolder.getAbsolutePath());
        if (!additionalTemplatesFolder.exists()) {
            logger.info("initCloudTemplates - no templates to add from folder: "
                    + additionalTemplatesFolder.getAbsolutePath());
            return;
        }
        File[] listFiles = additionalTemplatesFolder.listFiles();
        ComputeTemplatesReader reader = new ComputeTemplatesReader();
        List<ComputeTemplate> addedTemplates = reader.addAdditionalTemplates(config.getCloud(), listFiles);
        logger.info("initCloudTemplates - Added the following templates: " + addedTemplates);
        config.getLastTemplateFileNum().addAndGet(listFiles.length);

    }

    private CloudConfigurationHolder getCloudConfigurationFromManagementSpace() {
        logger.info("Waiting for cloud configuration to become available in management space");
        final CloudConfigurationHolder config = gigaSpace.read(
                new CloudConfigurationHolder(), 1000 * 60);
        if (config == null) {

            final String msg = "Could not find the expected Cloud Configuration Holder in Management space!";
            logger.severe(msg);
            throw new IllegalStateException(msg);
        }
        return config;
    }

    @Override
    public Class<?> getObjectType() {
        return RestConfiguration.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

}
