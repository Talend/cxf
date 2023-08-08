/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cxf.ext.logging.osgi;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.cxf.ext.logging.LoggingFeature;
import org.apache.cxf.feature.AbstractFeature;
import org.apache.cxf.feature.Feature;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Activator implements BundleActivator {
    private static final Logger LOG = LoggerFactory.getLogger(Activator.class);
    private static final String CONFIG_PID = "org.apache.cxf.features.logging";

    @Override
    public void start(final BundleContext bundleContext) {
        Dictionary<String, String> properties = new Hashtable<>();
        properties.put(Constants.SERVICE_PID, CONFIG_PID);
        bundleContext.registerService(ManagedService.class.getName(), new ConfigUpdater(bundleContext), properties);
    }

    @Override
    public void stop(BundleContext context) throws Exception {

    }
    public static void configureLoggingFeature(LoggingFeature loggingFeature) {
        Bundle bundle = FrameworkUtil.getBundle(Activator.class);
        if (bundle != null) {
            try {
                BundleContext bundleContext = bundle.getBundleContext();
                ConfigurationAdmin configAdmin = (ConfigurationAdmin) bundleContext.
                        getService(bundleContext.getServiceReference(ConfigurationAdmin.class.getName()));
                Configuration configF = configAdmin.getConfiguration(CONFIG_PID);
                if (configF != null) {
                    Dictionary<String, Object> config = configF.getProperties();
                    updated(config, loggingFeature);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static final class ConfigUpdater implements ManagedService {
        private BundleContext bundleContext;
        private ServiceRegistration<?> serviceReg;
        private ServiceRegistration<?> intentReg;
        private LoggingFeature logging;

        ConfigUpdater(BundleContext bundleContext) {
            this.logging = new LoggingFeature();
            this.bundleContext = bundleContext;
        }

        @Override
        public void updated(Dictionary config) throws ConfigurationException {
            boolean enabled = Activator.updated(config, logging);
            if (enabled) {
                if (serviceReg == null) {
                    Dictionary<String, Object> properties = new Hashtable<>();
                    properties.put("name", "logging");
                    serviceReg = bundleContext.registerService(Feature.class.getName(), logging, properties);
                }
            } else {
                if (serviceReg != null) {
                    serviceReg.unregister();
                    serviceReg = null;
                }
            }
            if (intentReg == null) {
                Dictionary<String, Object> properties = new Hashtable<>();
                properties.put("org.apache.cxf.dosgi.IntentName", "logging");
                bundleContext.registerService(AbstractFeature.class.getName(), logging, properties);
            }
        }

    }

    @SuppressWarnings("rawtypes")
    private static Set<String> getTrimmedSet(Dictionary config, String propertyKey) {
        return new HashSet<>(
                Arrays.stream(String.valueOf(getValue(config, propertyKey, "")).split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toSet()));
    }

    @SuppressWarnings("rawtypes")
    private static String getValue(Dictionary config, String key, String defaultValue) {
        if (config == null) {
            return defaultValue;
        }
        String value = (String)config.get(key);
        return value != null ? value : defaultValue;
    }

    @SuppressWarnings("rawtypes")
    private static boolean updated(Dictionary config, LoggingFeature logging) {
        boolean enabled = Boolean.valueOf(getValue(config, "enabled", "false"));
        LOG.info("CXF message logging feature " + (enabled ? "enabled" : "disabled"));
        Integer limit = Integer.valueOf(getValue(config, "limit", "65536"));
        Boolean pretty = Boolean.valueOf(getValue(config, "pretty", "false"));
        Boolean verbose = Boolean.valueOf(getValue(config, "verbose", "true"));
        Long inMemThreshold = Long.valueOf(getValue(config, "inMemThresHold", "-1"));
        Boolean logMultipart = Boolean.valueOf(getValue(config, "logMultipart", "true"));
        Boolean logBinary = Boolean.valueOf(getValue(config, "logBinary", "false"));
        Set<String> sensitiveElementNames = getTrimmedSet(config, "sensitiveElementNames");
        Set<String> sensitiveProtocolHeaderNames = getTrimmedSet(config, "sensitiveProtocolHeaderNames");

        if (limit != null) {
            logging.setLimit(limit);
        }
        if (inMemThreshold != null) {
            logging.setInMemThreshold(inMemThreshold);
        }
        if (pretty != null) {
            logging.setPrettyLogging(pretty);
        }

        if (verbose != null) {
            logging.setVerbose(verbose);
        }
        if (logMultipart != null) {
            logging.setLogMultipart(logMultipart);
        }
        if (logBinary != null) {
            logging.setLogBinary(logBinary);
        }

        logging.setSensitiveElementNames(sensitiveElementNames);
        logging.setSensitiveProtocolHeaderNames(sensitiveProtocolHeaderNames);

        return enabled;
    }

}
